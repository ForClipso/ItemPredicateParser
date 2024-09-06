package me.blvckbytes.item_predicate_parser.translation;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.io.FileUtils;
import org.bukkit.Registry;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class LanguageRegistry implements ILanguageRegistry {

  private final AssetIndex assetIndex;
  private final File languagesFolder;
  private final Logger logger;

  private final Map<TranslationLanguage, TranslationRegistry> registryByLanguage;

  public LanguageRegistry(Plugin plugin) throws Exception {
    this.registryByLanguage = new HashMap<>();
    this.logger = plugin.getLogger();
    this.assetIndex = new AssetIndex();
    this.languagesFolder = Paths.get(plugin.getDataFolder().getAbsolutePath(), "languages", assetIndex.serverVersion).toFile();

    if (!this.languagesFolder.isDirectory()) {
      if (!this.languagesFolder.mkdirs())
        throw new IllegalStateException("Could not create directory " + this.languagesFolder);

      logger.info("Created folder to house language files");
    }
  }

  private String downloadLanguageFileContents(TranslationLanguage language) throws Exception {
    // They don't seem to carry this entry in the index; we need to
    // unzip the client.jar in order to access it - what a headache.
    if (language == TranslationLanguage.ENGLISH_US)
      return this.assetIndex.getClientEmbeddedLanguageFileContents();

    var fileUrl = this.assetIndex.getLanguageFileUrl(language.assetFileName);

    if (fileUrl == null)
      throw new IllegalStateException("Could not look up URL for " + language.assetFileName);

    return this.assetIndex.makePlainTextGetRequest(fileUrl);
  }

  private JsonObject accessOrDownloadLanguageFile(TranslationLanguage language, boolean overwrite) throws Exception {
    var localFile = new File(this.languagesFolder, language.assetFileName);

    if (overwrite && localFile.exists()) {
      if (!localFile.delete())
        throw new IllegalStateException("Could not delete existing language file " + localFile);
    }

    String contents;

    if (!localFile.exists()) {
      logger.info("Downloading language-file " + language.assetFileName);
      contents = downloadLanguageFileContents(language);
      FileUtils.writeStringToFile(localFile, contents, StandardCharsets.UTF_8);
    } else {
      contents = FileUtils.readFileToString(localFile, StandardCharsets.UTF_8);
    }

    return assetIndex.parseJson(contents);
  }

  @Override
  public @Nullable TranslationRegistry getTranslationRegistry(TranslationLanguage language) {
    return registryByLanguage.get(language);
  }

  public void initializeRegistry(TranslationLanguage language) throws Exception {
    JsonObject languageFile;

    try {
      languageFile = accessOrDownloadLanguageFile(language, false);
    } catch (JsonSyntaxException e1) {
      try {
        languageFile = accessOrDownloadLanguageFile(language, true);
      } catch (JsonSyntaxException e2) {
        throw new IllegalStateException("Could not successfully parse language-file " + language.assetFileName);
      }
    }

    language.customTranslations.apply(languageFile);

    TranslationRegistry registry = new TranslationRegistry(languageFile, logger);
    registry.initialize(makeSources(language.collisionPrefixes));
    registryByLanguage.put(language, registry);
  }

  private List<TranslatableSource> makeSources(CollisionPrefixes collisionPrefixes) {
    var result = new ArrayList<TranslatableSource>();

    result.add(new TranslatableSource(Registry.ENCHANTMENT, collisionPrefixes.forEnchantments()));
    result.add(new TranslatableSource(Registry.EFFECT, collisionPrefixes.forEffects()));
    result.add(new TranslatableSource(Registry.MATERIAL, collisionPrefixes.forMaterials()));

    result.add(new TranslatableSource(List.of(
      DeteriorationKey.INSTANCE,
      NegationKey.INSTANCE,
      DisjunctionKey.INSTANCE,
      ConjunctionKey.INSTANCE,
      ExactKey.INSTANCE,
      AmountKey.INSTANCE
    ), ""));

    return result;
  }
}
