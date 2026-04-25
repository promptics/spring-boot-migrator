package org.springframework.rewrite.parser;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
   prefix = "spring.rewrite"
)
public class SpringRewriteProperties {
   private boolean skipMavenParsing = false;
   private boolean pomCacheEnabled = false;
   private String pomCacheDirectory = Path.of(System.getProperty("user.home")).resolve(".rewrite-cache").toAbsolutePath().normalize().toString();
   private Set<String> plainTextMasks = Set.of("**.txt");
   private int sizeThresholdMb = 10;
   private boolean runPerSubmodule = false;
   private List<String> activeProfiles = List.of("default");
   private Set<String> ignoredPathPatterns = Set.of(
      "**/target/**", "target/**", "**/.idea/**", ".idea/**", ".mvn/**", "**/.mvn/**", "**.git/**", "**/lib/**", "**/.gitignore"
   );
   private boolean failOnInvalidActiveRecipes = true;
   private boolean parseAdditionalResources = true;
   private boolean logCompilationWarningsAndErrors = false;

   public boolean isSkipMavenParsing() {
      return this.skipMavenParsing;
   }

   public void setSkipMavenParsing(boolean skipMavenParsing) {
      this.skipMavenParsing = skipMavenParsing;
   }

   public boolean isPomCacheEnabled() {
      return this.pomCacheEnabled;
   }

   public void setPomCacheEnabled(boolean pomCacheEnabled) {
      this.pomCacheEnabled = pomCacheEnabled;
   }

   public String getPomCacheDirectory() {
      return this.pomCacheDirectory;
   }

   public void setPomCacheDirectory(String pomCacheDirectory) {
      this.pomCacheDirectory = pomCacheDirectory;
   }

   public Set<String> getPlainTextMasks() {
      return this.plainTextMasks;
   }

   public void setPlainTextMasks(Set<String> plainTextMasks) {
      this.plainTextMasks = plainTextMasks;
   }

   public int getSizeThresholdMb() {
      return this.sizeThresholdMb;
   }

   public void setSizeThresholdMb(int sizeThresholdMb) {
      this.sizeThresholdMb = sizeThresholdMb;
   }

   public boolean isRunPerSubmodule() {
      return this.runPerSubmodule;
   }

   public void setRunPerSubmodule(boolean runPerSubmodule) {
      this.runPerSubmodule = runPerSubmodule;
   }

   public List<String> getActiveProfiles() {
      return this.activeProfiles;
   }

   public void setActiveProfiles(List<String> activeProfiles) {
      this.activeProfiles = activeProfiles;
   }

   public Set<String> getIgnoredPathPatterns() {
      return this.ignoredPathPatterns;
   }

   public void setIgnoredPathPatterns(Set<String> ignoredPathPatterns) {
      this.ignoredPathPatterns = ignoredPathPatterns;
   }

   public boolean isFailOnInvalidActiveRecipes() {
      return this.failOnInvalidActiveRecipes;
   }

   public void setFailOnInvalidActiveRecipes(boolean failOnInvalidActiveRecipes) {
      this.failOnInvalidActiveRecipes = failOnInvalidActiveRecipes;
   }

   public boolean isParseAdditionalResources() {
      return this.parseAdditionalResources;
   }

   public void setParseAdditionalResources(boolean parseAdditionalResources) {
      this.parseAdditionalResources = parseAdditionalResources;
   }

   public boolean isLogCompilationWarningsAndErrors() {
      return this.logCompilationWarningsAndErrors;
   }

   public void setLogCompilationWarningsAndErrors(boolean logCompilationWarningsAndErrors) {
      this.logCompilationWarningsAndErrors = logCompilationWarningsAndErrors;
   }
}
