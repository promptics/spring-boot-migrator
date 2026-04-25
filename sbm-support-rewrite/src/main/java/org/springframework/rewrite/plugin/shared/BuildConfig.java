package org.springframework.rewrite.plugin.shared;

public class BuildConfig {
   private boolean skipTests = false;
   private MemorySettings memorySettings;

   BuildConfig(boolean skipTests, MemorySettings memorySettings) {
      this.skipTests = skipTests;
      this.memorySettings = memorySettings;
   }

   private BuildConfig(boolean skipTests) {
      this.skipTests = skipTests;
   }

   public static BuildConfig skipTests() {
      return new BuildConfig(true);
   }

   public static BuildConfig defaultConfig() {
      return new BuildConfig(false);
   }

   public static BuildConfig.Builder builder() {
      return new BuildConfig.Builder();
   }

   public static BuildConfig fromDefault() {
      return new BuildConfig(true, MemorySettings.noop());
   }

   public boolean isSkipTests() {
      return this.skipTests;
   }

   public MemorySettings getMemorySettings() {
      return this.memorySettings;
   }

   public boolean hasMemorySettings() {
      return this.memorySettings != null && this.memorySettings.getMin() != null;
   }

   public static class Builder {
      private boolean skipTests;
      private MemorySettings memorySettings = MemorySettings.of("256M", "1024M");

      public BuildConfig.Builder skipTests(boolean b) {
         this.skipTests = b;
         return this;
      }

      public BuildConfig build() {
         return new BuildConfig(this.skipTests, this.memorySettings);
      }

      public BuildConfig.Builder withMemory(String min, String max) {
         this.memorySettings = MemorySettings.of(min, max);
         return this;
      }
   }
}
