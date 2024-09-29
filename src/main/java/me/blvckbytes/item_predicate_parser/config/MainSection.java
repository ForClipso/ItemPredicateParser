package me.blvckbytes.item_predicate_parser.config;

import me.blvckbytes.bbconfigmapper.sections.AConfigSection;
import me.blvckbytes.bukkitevaluable.BukkitEvaluable;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MainSection extends AConfigSection {

  public @Nullable BukkitEvaluable expandedPreview;
  public BukkitEvaluable maxCompletionsCount;
  public @Nullable BukkitEvaluable maxCompletionsExceeded;
  public @Nullable BukkitEvaluable inputNonHighlightPrefix;
  public @Nullable BukkitEvaluable inputHighlightPrefix;
  public Map<String, BukkitEvaluable> parseConflicts;

  public MainSection(EvaluationEnvironmentBuilder baseEnvironment) {
    super(baseEnvironment);

    maxCompletionsCount = BukkitEvaluable.of(30);
  }
}