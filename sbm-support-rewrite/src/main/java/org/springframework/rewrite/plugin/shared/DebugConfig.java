package org.springframework.rewrite.plugin.shared;

public class DebugConfig {
   private int port = 5005;
   private boolean suspend = true;
   private boolean isDebugEnabled = true;

   public static DebugConfig from(int port, boolean suspend) {
      return new DebugConfig(port, suspend, true);
   }

   public static DebugConfig fromDefault() {
      return new DebugConfig(5005, false, true);
   }

   public static DebugConfig disabled() {
      return new DebugConfig(5005, false, false);
   }

   private DebugConfig(int port, boolean suspend, boolean isDebugEnabled) {
      this.port = port;
      this.suspend = suspend;
      this.isDebugEnabled = isDebugEnabled;
   }

   public int getPort() {
      return this.port;
   }

   public boolean isSuspend() {
      return this.suspend;
   }

   public char isSuspendEnabled() {
      return (char)(this.suspend ? 'y' : 'n');
   }

   public boolean isDebugEnabled() {
      return this.isDebugEnabled;
   }
}
