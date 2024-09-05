package me.blvckbytes.item_predicate_parser.predicate;

import me.blvckbytes.item_predicate_parser.token.Token;
import me.blvckbytes.item_predicate_parser.translation.TranslatedTranslatable;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record MaterialPredicate(
  Token token,
  @Nullable TranslatedTranslatable translatedTranslatable,
  List<Material> materials
) implements ItemPredicate {

  @Override
  public boolean test(PredicateState state) {
    for (var material : materials) {
      if (material.equals(state.item.getType()))
        return true;
    }
    return false;
  }

  @Override
  public String stringify(boolean useTokens) {
    if (translatedTranslatable != null && !useTokens)
      return translatedTranslatable.normalizedTranslation;

    return token.stringify();
  }
}
