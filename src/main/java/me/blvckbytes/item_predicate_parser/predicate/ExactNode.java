package me.blvckbytes.item_predicate_parser.predicate;

import me.blvckbytes.item_predicate_parser.token.Token;
import me.blvckbytes.item_predicate_parser.translation.TranslatedLangKeyed;
import org.jetbrains.annotations.Nullable;

public record ExactNode(
  Token token,
  TranslatedLangKeyed<?> translatedLangKeyed,
  ItemPredicate operand
) implements ItemPredicate {

  @Override
  public @Nullable ItemPredicate testForFailure(PredicateState state) {
    var exactState = state.copyAndEnterExact();
    ItemPredicate failure;

    // The predicates themselves weren't satisfied
    if ((failure = operand.testForFailure(exactState)) != null)
        return failure;

    // There have been remaining, unmatched properties - exact-mode failed
    if (exactState.hasRemains())
      return this;

    return null;
  }

  @Override
  public String stringify(boolean useTokens) {
    if (useTokens)
      return token.stringify() + " " + operand.stringify(true);

    return translatedLangKeyed.normalizedPrefixedTranslation + " " + operand.stringify(false);
  }
}
