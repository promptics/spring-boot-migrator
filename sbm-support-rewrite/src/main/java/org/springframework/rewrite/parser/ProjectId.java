package org.springframework.rewrite.parser;

import java.util.Objects;

public record ProjectId(String groupId, String artifactId) {
   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         ProjectId projectId = (ProjectId)o;
         return Objects.equals(this.groupId, projectId.groupId) && Objects.equals(this.artifactId, projectId.artifactId);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.groupId, this.artifactId);
   }

   @Override
   public String toString() {
      return this.groupId + ":" + this.artifactId;
   }
}
