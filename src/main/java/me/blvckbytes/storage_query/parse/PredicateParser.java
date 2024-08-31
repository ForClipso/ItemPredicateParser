package me.blvckbytes.storage_query.parse;

import me.blvckbytes.storage_query.token.*;
import me.blvckbytes.storage_query.predicate.*;
import me.blvckbytes.storage_query.translation.*;
import org.bukkit.Material;
import org.bukkit.Translatable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PredicateParser {

  @FunctionalInterface
  private interface BinaryNodeConstructor {
    ItemPredicate call(Token token, TranslatedTranslatable translatable, ItemPredicate lhs, ItemPredicate rhs);
  }

  @FunctionalInterface
  private interface UnaryNodeConstructor {
    ItemPredicate call(Token token, TranslatedTranslatable translatable, ItemPredicate operand);
  }

  private final TranslationRegistry translationRegistry;
  private final TranslatedTranslatable conjunctionTranslation;
  private final ArrayList<Token> tokens;
  private final Map<Token, TranslatedTranslatable> resolveCache;
  private final boolean allowMissingClosingParentheses;

  public PredicateParser(
    TranslationRegistry translationRegistry,
    List<Token> tokens,
    boolean allowMissingClosingParentheses
  ) {
    this.translationRegistry = translationRegistry;
    this.tokens = new ArrayList<>(tokens);
    this.allowMissingClosingParentheses = allowMissingClosingParentheses;

    this.conjunctionTranslation = this.translationRegistry.lookup(ConjunctionKey.INSTANCE);
    this.resolveCache = new HashMap<>();

    if (this.conjunctionTranslation == null)
      throw new IllegalStateException("Expected the registry to know about the conjunction translation");
  }

  public @Nullable ItemPredicate parseAst() {
    var result = parseDisjunctionNode();

    while (!tokens.isEmpty()) {
      var nextExpression = parseDisjunctionNode();

      if (nextExpression == null)
        break;

      // Consecutive predicates are implicitly joined by AND
      result = new ConjunctionNode(null, conjunctionTranslation, result, nextExpression, true);
    }

    return result;
  }

  private @Nullable ItemPredicate parseConjunctionNode() {
    return parseBinaryNode(this::parseNegationNode, ConjunctionKey.class, ConjunctionNode::new);
  }

  private @Nullable ItemPredicate parseDisjunctionNode() {
    return parseBinaryNode(this::parseConjunctionNode, DisjunctionKey.class, DisjunctionNode::new);
  }

  private @Nullable ItemPredicate parseBinaryNode(
    Supplier<ItemPredicate> parser,
    Class<? extends Translatable> operatorType,
    BinaryNodeConstructor constructor
  ) {
    var result = parser.get();

    if (result == null)
      return null;

    while (!tokens.isEmpty()) {
      var token = tokens.getFirst();
      var translated = resolveTranslated(token);

      if (translated == null || !operatorType.isInstance(translated.translatable()))
        break;

      tokens.removeFirst();

      var rhs = parser.get();

      if (rhs == null)
        throw new ArgumentParseException(token.commandArgumentIndex(), ParseConflict.EXPECTED_EXPRESSION_AFTER_OPERATOR);

      result = constructor.call(token, translated, result, rhs);
    }

    return result;
  }

  private @Nullable ItemPredicate parseNegationNode() {
    return parseUnaryNode(this::parseExactNode, NegationKey.class, NegationNode::new);
  }

  private @Nullable ItemPredicate parseExactNode() {
    return parseUnaryNode(this::parseParenthesesNode, ExactKey.class, ExactNode::new);
  }

  private @Nullable ItemPredicate parseUnaryNode(
    Supplier<ItemPredicate> parser,
    Class<? extends Translatable> operatorType,
    UnaryNodeConstructor constructor
  ) {
    if (tokens.isEmpty())
      return null;

    var token = tokens.getFirst();
    var translated = resolveTranslated(token);

    if (translated == null || !operatorType.isInstance(translated.translatable()))
      return parser.get();

    tokens.removeFirst();

    var operand = parser.get();

    if (operand == null)
      throw new ArgumentParseException(token.commandArgumentIndex(), ParseConflict.EXPECTED_EXPRESSION_AFTER_OPERATOR);

    return constructor.call(token, translated, operand);
  }

  private @Nullable ItemPredicate parseParenthesesNode() {
    if (tokens.isEmpty())
      return null;

    var token = tokens.getFirst();

    if (!(token instanceof ParenthesisToken openingToken))
      return parseItemPredicate();

    if (!openingToken.isOpening())
      throw new ArgumentParseException(token.commandArgumentIndex(), ParseConflict.EXPECTED_OPENING_PARENTHESIS);

    tokens.removeFirst();

    // This check provides better user-experience, as an empty pair of parentheses would yield the following behavior:
    // The ( enters a new ParenthesesNode, which re-climbs the precedence ladder.
    // The next invocation will expect ( but gets ) and thus throws a parentheses-mismatch.
    // This way, it becomes clear that the content within the pair is what's actually missing.
    if (!tokens.isEmpty() && tokens.getFirst() instanceof ParenthesisToken parenthesisToken && !parenthesisToken.isOpening())
      throw new ArgumentParseException(token.commandArgumentIndex(), ParseConflict.EXPECTED_SEARCH_PATTERN);

    // Invoke the lowest precedence parser
    var inner = parseDisjunctionNode();

    if (inner == null)
      throw new ArgumentParseException(token.commandArgumentIndex(), ParseConflict.EXPECTED_SEARCH_PATTERN);

    // Takes care of parentheses which immediately followed the next higher precedence inside the current
    // parentheses - example: (dia-ches (unbr)). The ( of (unbr) would be left by the predicate parser,
    // and so the parentheses parser needs to pick it up and implicitly add conjunctions to inner
    while (!tokens.isEmpty()) {
      if (!((tokens.getFirst() instanceof ParenthesisToken nextToken) && nextToken.isOpening()))
        break;

      inner = new ConjunctionNode(null, conjunctionTranslation, inner, parseParenthesesNode(), true);
    }

    if (tokens.isEmpty()) {
      if (allowMissingClosingParentheses)
        return new ParenthesesNode(inner);

      throw new ArgumentParseException(token.commandArgumentIndex(), ParseConflict.EXPECTED_CLOSING_PARENTHESIS);
    }

    token = tokens.getFirst();

    if (!(token instanceof ParenthesisToken closingToken) || closingToken.isOpening()) {
      if (allowMissingClosingParentheses) {
        while (!tokens.isEmpty()) {
          // Found corresponding closing parenthesis
          if (tokens.getFirst() instanceof ParenthesisToken nextToken && !nextToken.isOpening()) {
            tokens.removeFirst();
            break;
          }

          // Re-climb the precedence ladder
          // This way, missing closing parentheses will be added to the very end, which makes it
          // possible to actually get the desired result, until closing-parens are added in while typing
          inner = new ConjunctionNode(null, conjunctionTranslation, inner, parseDisjunctionNode(), true);
        }

        return new ParenthesesNode(inner);
      }

      throw new ArgumentParseException(token.commandArgumentIndex(), ParseConflict.EXPECTED_CLOSING_PARENTHESIS);
    }

    tokens.removeFirst();

    return new ParenthesesNode(inner);
  }

  private @Nullable TranslatedTranslatable resolveTranslated(Token token) {
    if (!(token instanceof UnquotedStringToken stringToken))
      return null;

    return resolveCache.computeIfAbsent(token, tk -> {
      var searchResult = translationRegistry.search(token.commandArgumentIndex(), stringToken.value());

      if (searchResult.isWildcardPresent())
        return null;

      return getShortestMatch(searchResult.result());
    });
  }

  private @Nullable ItemPredicate parseItemPredicate() {
    var predicates = new ArrayList<ItemPredicate>();

    while (!tokens.isEmpty()) {
      var currentToken = tokens.getFirst();

      if (currentToken instanceof QuotedStringToken textSearch) {
        predicates.add(new TextSearchPredicate(textSearch));
        tokens.removeFirst();
        continue;
      }

      if (currentToken instanceof ParenthesisToken)
        break;

      if (!(currentToken instanceof UnquotedStringToken translationSearch))
        throw new ArgumentParseException(currentToken.commandArgumentIndex(), ParseConflict.EXPECTED_SEARCH_PATTERN);

      var searchString = translationSearch.value();

      if (searchString.isEmpty()) {
        tokens.removeFirst();
        continue;
      }

      var searchResult = translationRegistry.search(currentToken.commandArgumentIndex(), searchString);

      var searchResultEntries = searchResult.result();

      // Wildcards may only apply to materials, not only because that's the only place where they make sense, but
      // because otherwise, predicate-ambiguity would arise.
      if (searchResult.isWildcardPresent()) {
        var materials = new ArrayList<Material>();

        for (var resultEntry : searchResultEntries) {
          if (resultEntry.translatable() instanceof Material material)
            materials.add(material);
        }

        if (materials.isEmpty())
          throw new ArgumentParseException(currentToken.commandArgumentIndex(), ParseConflict.NO_SEARCH_MATCH);

        predicates.add(new MaterialPredicate(translationSearch, null, materials));
        tokens.removeFirst();
        continue;
      }

      var shortestMatch = getShortestMatch(searchResultEntries);

      if (shortestMatch == null)
        throw new ArgumentParseException(currentToken.commandArgumentIndex(), ParseConflict.NO_SEARCH_MATCH);

      if (shortestMatch.translatable() instanceof Material predicateMaterial) {
        predicates.add(new MaterialPredicate(translationSearch, shortestMatch, List.of(predicateMaterial)));
        tokens.removeFirst();
        continue;
      }

      if (shortestMatch.translatable() instanceof Enchantment predicateEnchantment) {
        tokens.removeFirst();

        IntegerToken enchantmentLevel = tryConsumeIntegerArgument(tokens);
        throwOnTimeNotation(enchantmentLevel);

        predicates.add(new EnchantmentPredicate(currentToken, shortestMatch, predicateEnchantment, enchantmentLevel));
        continue;
      }

      if (shortestMatch.translatable() instanceof PotionEffectType predicatePotionEffect) {
        tokens.removeFirst();

        IntegerToken potionEffectAmplifier = tryConsumeIntegerArgument(tokens);
        throwOnTimeNotation(potionEffectAmplifier);

        IntegerToken potionEffectDuration = tryConsumeIntegerArgument(tokens);

        predicates.add(new PotionEffectPredicate(currentToken, shortestMatch, predicatePotionEffect, potionEffectAmplifier, potionEffectDuration));
        continue;
      }

      if (shortestMatch.translatable() instanceof DeteriorationKey) {
        tokens.removeFirst();

        IntegerToken deteriorationPercentageMin = tryConsumeIntegerArgument(tokens);
        throwOnTimeNotation(deteriorationPercentageMin);
        throwOnNonEqualsComparison(deteriorationPercentageMin);

        IntegerToken deteriorationPercentageMax = tryConsumeIntegerArgument(tokens);
        throwOnTimeNotation(deteriorationPercentageMax);
        throwOnNonEqualsComparison(deteriorationPercentageMax);

        predicates.add(new DeteriorationPredicate(currentToken, shortestMatch, deteriorationPercentageMin, deteriorationPercentageMax));
        continue;
      }

      if (shortestMatch.translatable() instanceof AmountKey) {
        tokens.removeFirst();

        IntegerToken amount = tryConsumeIntegerArgument(tokens);
        throwOnTimeNotation(amount);

        if (amount == null || amount.value() == null)
          throw new ArgumentParseException(currentToken.commandArgumentIndex(), ParseConflict.EXPECTED_FOLLOWING_INTEGER);

        predicates.add(new AmountPredicate(currentToken, shortestMatch, amount));
        continue;
      }

      break;
    }

    if (predicates.isEmpty())
      return null;

    ItemPredicate result = predicates.removeFirst();

    // Consecutive predicates are implicitly joined by AND
    while (!predicates.isEmpty())
      result = new ConjunctionNode(null, conjunctionTranslation, result, predicates.removeFirst(), true);

    return result;
  }

  private static void throwOnNonEqualsComparison(@Nullable IntegerToken token) {
    if (token == null)
      return;

    if (token.comparisonMode() == ComparisonMode.EQUALS)
      return;

    throw new ArgumentParseException(token.commandArgumentIndex(), ParseConflict.DOES_NOT_ACCEPT_NON_EQUALS_COMPARISON);
  }

  private static void throwOnTimeNotation(@Nullable IntegerToken token) {
    if (token == null)
      return;

    if (!token.wasTimeNotation())
      return;

    throw new ArgumentParseException(token.commandArgumentIndex(), ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
  }

  private static @Nullable IntegerToken tryConsumeIntegerArgument(List<Token> tokens) {
    IntegerToken integerToken = null;

    if (!tokens.isEmpty()) {
      var nextToken = tokens.getFirst();

      if (nextToken instanceof IntegerToken argument) {
        integerToken = argument;
        tokens.removeFirst();
      }
    }

    return integerToken;
  }

  private static @Nullable TranslatedTranslatable getShortestMatch(List<TranslatedTranslatable> matches) {
    // TODO: This should account for alphabetical sorting order too, not just length, to
    //       handle contains-&-length "collision"

    if (matches.isEmpty())
      return null;

    var numberOfMatches = matches.size();

    if (numberOfMatches == 1)
      return matches.getFirst();

    var shortestMatchLength = Integer.MAX_VALUE;
    var shortestMatchIndex = 0;

    for (var matchIndex = 0; matchIndex < numberOfMatches; ++matchIndex) {
      var currentLength = matches.get(matchIndex).translation().length();

      if (currentLength < shortestMatchLength) {
        shortestMatchLength = currentLength;
        shortestMatchIndex = matchIndex;
      }
    }

    return matches.get(shortestMatchIndex);
  }
}
