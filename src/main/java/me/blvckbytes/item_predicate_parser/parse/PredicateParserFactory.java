package me.blvckbytes.item_predicate_parser.parse;

import me.blvckbytes.item_predicate_parser.token.Token;
import me.blvckbytes.item_predicate_parser.translation.ConjunctionKey;
import me.blvckbytes.item_predicate_parser.translation.TranslatedTranslatable;
import me.blvckbytes.item_predicate_parser.translation.TranslationRegistry;

import java.util.ArrayList;

public class PredicateParserFactory {

  public final TranslationRegistry registry;
  private final TranslatedTranslatable conjunctionTranslation;

  public PredicateParserFactory(TranslationRegistry registry) {
    this.registry = registry;
    this.conjunctionTranslation = this.registry.lookup(ConjunctionKey.INSTANCE);

    if (this.conjunctionTranslation == null)
      throw new IllegalStateException("Expected the registry to know about the conjunction translation");
  }

  public PredicateParser create(
    ArrayList<Token> tokens,
    boolean allowMissingClosingParentheses
  ) {
    return new PredicateParser(registry, conjunctionTranslation, tokens, allowMissingClosingParentheses);
  }
}