package me.blvckbytes.item_predicate_parser.predicate;

import me.blvckbytes.item_predicate_parser.token.IntegerToken;
import me.blvckbytes.item_predicate_parser.token.Token;
import me.blvckbytes.item_predicate_parser.translation.TranslatedTranslatable;

public record AmountPredicate(
  Token token,
  TranslatedTranslatable translatedTranslatable,
  IntegerToken amountArgument
) implements ItemPredicate {

  @Override
  public boolean test(PredicateState state) {
    return amountArgument.matches(state.item.getAmount());
  }

  @Override
  public String stringify(boolean useTokens) {
    if (useTokens)
      return token.stringify() + " " + amountArgument.stringify();

    return translatedTranslatable.normalizedName + " " + amountArgument.stringify();
  }
}