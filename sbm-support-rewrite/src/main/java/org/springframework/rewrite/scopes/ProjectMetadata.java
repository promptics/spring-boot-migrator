package org.springframework.rewrite.scopes;

import org.openrewrite.maven.MavenSettings;

public class ProjectMetadata {
   private String metadata;
   private MavenSettings mavenSettings;

   public String getMetadata() {
      return this.metadata;
   }

   public void setMetadata(String metadata) {
      this.metadata = metadata;
   }

   public MavenSettings getMavenSettings() {
      return this.mavenSettings;
   }

   public void setMavenSettings(MavenSettings mavenSettings) {
      this.mavenSettings = mavenSettings;
   }
}
