package org.springframework.rewrite.plugin.shared;

public class MemorySettings {
   private final String min;
   private final String max;

   public MemorySettings(String min, String max) {
      this.min = min;
      this.max = max;
   }

   public static MemorySettings of(String min, String max) {
      return new MemorySettings(min, max);
   }

   public static MemorySettings noop() {
      return new MemorySettings(null, null);
   }

   public String getMin() {
      return this.min;
   }

   public String getMax() {
      return this.max;
   }
}
