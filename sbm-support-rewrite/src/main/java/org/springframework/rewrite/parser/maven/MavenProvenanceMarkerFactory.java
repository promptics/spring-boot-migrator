package org.springframework.rewrite.parser.maven;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.openrewrite.Tree;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.marker.JavaProject.Publication;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.marker.GitProvenance;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.OperatingSystemProvenance;
import org.openrewrite.marker.BuildTool.Type;
import org.openrewrite.marker.ci.BuildEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenProvenanceMarkerFactory {
   private static final Logger LOGGER = LoggerFactory.getLogger(MavenProvenanceMarkerFactory.class);

   public List<Marker> generateProvenance(Path baseDir, MavenProject mavenProject) {
      MavenRuntimeInformation runtime = mavenProject.getMavenRuntimeInformation();
      BuildTool buildTool = new BuildTool(Tree.randomId(), Type.Maven, runtime.getMavenVersion());
      String javaRuntimeVersion = System.getProperty("java.specification.version");
      String javaVendor = System.getProperty("java.vm.vendor");
      String sourceCompatibility = null;
      String targetCompatibility = null;
      Plugin compilerPlugin = mavenProject.getPlugin("org.apache.maven.plugins:maven-compiler-plugin");
      if (compilerPlugin != null && compilerPlugin.getConfiguration() instanceof Xpp3Dom) {
         Xpp3Dom dom = (Xpp3Dom)compilerPlugin.getConfiguration();
         Xpp3Dom release = dom.getChild("release");
         if (release != null && StringUtils.isNotEmpty(release.getValue()) && !release.getValue().contains("${")) {
            sourceCompatibility = release.getValue();
            targetCompatibility = release.getValue();
         } else {
            Xpp3Dom source = dom.getChild("source");
            if (source != null && StringUtils.isNotEmpty(source.getValue()) && !source.getValue().contains("${")) {
               sourceCompatibility = source.getValue();
            }

            Xpp3Dom target = dom.getChild("target");
            if (target != null && StringUtils.isNotEmpty(target.getValue()) && !target.getValue().contains("${")) {
               targetCompatibility = target.getValue();
            }
         }
      }

      if (sourceCompatibility == null || targetCompatibility == null) {
         String propertiesReleaseCompatibility = (String)mavenProject.getProperties().get("maven.compiler.release");
         if (propertiesReleaseCompatibility != null) {
            sourceCompatibility = propertiesReleaseCompatibility;
            targetCompatibility = propertiesReleaseCompatibility;
         } else {
            String propertiesSourceCompatibility = (String)mavenProject.getProperties().get("maven.compiler.source");
            if (sourceCompatibility == null && propertiesSourceCompatibility != null) {
               sourceCompatibility = propertiesSourceCompatibility;
            }

            String propertiesTargetCompatibility = (String)mavenProject.getProperties().get("maven.compiler.target");
            if (targetCompatibility == null && propertiesTargetCompatibility != null) {
               targetCompatibility = propertiesTargetCompatibility;
            }
         }
      }

      if (sourceCompatibility == null) {
         sourceCompatibility = javaRuntimeVersion;
      }

      if (targetCompatibility == null) {
         targetCompatibility = sourceCompatibility;
      }

      BuildEnvironment buildEnvironment = BuildEnvironment.build(System::getenv);
      return Stream.of(
            buildEnvironment,
            this.gitProvenance(baseDir, buildEnvironment),
            OperatingSystemProvenance.current(),
            buildTool,
            new JavaVersion(Tree.randomId(), javaRuntimeVersion, javaVendor, sourceCompatibility, targetCompatibility),
            new JavaProject(
               Tree.randomId(), mavenProject.getName(), new Publication(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion())
            )
         )
         .filter(Objects::nonNull)
         .toList();
   }

   @Nullable
   private GitProvenance gitProvenance(Path baseDir, @Nullable BuildEnvironment buildEnvironment) {
      try {
         return GitProvenance.fromProjectDirectory(baseDir, buildEnvironment);
      } catch (Exception var4) {
         LOGGER.debug("Unable to determine git provenance", var4);
         return null;
      }
   }
}
