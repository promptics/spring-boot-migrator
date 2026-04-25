package org.springframework.rewrite.parser.maven;

import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;
import org.springframework.core.io.Resource;

public class MavenProjectFactory {
   private final MavenArtifactDownloader artifactDownloader;

   public MavenProjectFactory(MavenArtifactDownloader artifactDownloader) {
      this.artifactDownloader = artifactDownloader;
   }

   public List<MavenProject> create(Path baseDir, List<Resource> projectResources) {
      List<Resource> allPomFiles = MavenBuildFileFilter.filterBuildFiles(projectResources);
      if (allPomFiles.isEmpty()) {
         throw new IllegalArgumentException("The provided resources did not contain any 'pom.xml' file.");
      } else {
         return allPomFiles.stream().map(pf -> this.create(baseDir, pf, projectResources)).toList();
      }
   }

   @NotNull
   public MavenProject create(Path baseDir, Resource pomFile, List<Resource> projectResources) {
      return new MavenProject(baseDir, pomFile, this.artifactDownloader, projectResources);
   }
}
