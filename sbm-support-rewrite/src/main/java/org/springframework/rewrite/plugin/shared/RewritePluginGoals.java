package org.springframework.rewrite.plugin.shared;

public enum RewritePluginGoals {
   RUN("run", "rewriteRun"),
   RUN_NO_FORK("runNoFork", "rewriteRun"),
   DRY_RUN("dryRun", "rewriteDryRun"),
   DRY_RUN_NO_FORK("dryRunNoFork", "rewriteDryRun"),
   DISCOVER("discover", "rewriteDiscover"),
   CYCLONEDX("cyclonedx", null);

   private final String maven;
   private final String gradle;

   private RewritePluginGoals(String maven, String gradle) {
      this.maven = maven;
      this.gradle = gradle;
   }

   public String getMaven() {
      return this.maven;
   }

   public String getGradle() {
      return this.gradle;
   }
}
