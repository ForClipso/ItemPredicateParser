package me.blvckbytes.storage_query.translation;

import org.bukkit.Translatable;
import org.jetbrains.annotations.NotNull;

public class ConjunctionKey implements Translatable {

  public static final ConjunctionKey INSTANCE = new ConjunctionKey();

  private ConjunctionKey() {}

  @Override
  public @NotNull String getTranslationKey() {
    return "custom.storage-query.conjunction-key";
  }
}
