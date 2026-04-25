package org.springframework.rewrite.parser.maven;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.openrewrite.marker.Marker;

public class ClasspathDependencies implements Marker {
   private List<Path> dependencies;
   private final UUID id;

   public ClasspathDependencies(List<Path> dependencies) {
      this.dependencies = dependencies;
      this.id = UUID.randomUUID();
   }

   private ClasspathDependencies(UUID id, List<Path> dependencies) {
      this.id = id;
      this.dependencies = dependencies;
   }

   public void setDependencies(List<Path> dependencies) {
      this.dependencies = dependencies;
   }

   public List<Path> getDependencies() {
      return this.dependencies;
   }

   public UUID getId() {
      return this.id;
   }

   public ClasspathDependencies withId(UUID id) {
      return new ClasspathDependencies(id, this.dependencies);
   }
}
