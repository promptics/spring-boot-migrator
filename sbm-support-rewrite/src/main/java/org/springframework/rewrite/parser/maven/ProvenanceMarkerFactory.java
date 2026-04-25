package org.springframework.rewrite.parser.maven;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openrewrite.marker.Marker;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parser.ParserContext;
import org.springframework.rewrite.utils.ResourceUtil;

public class ProvenanceMarkerFactory {
   private final MavenProvenanceMarkerFactory markerFactory;

   public ProvenanceMarkerFactory(MavenProvenanceMarkerFactory markerFactory) {
      this.markerFactory = markerFactory;
   }

   public Map<Path, List<Marker>> generateProvenanceMarkers(Path baseDir, ParserContext parserContext) {
      Map<Path, List<Marker>> result = new HashMap<>();
      parserContext.getSortedProjects().forEach(mavenProject -> {
         List<Marker> markers = this.markerFactory.generateProvenance(baseDir, mavenProject);
         Resource resource = parserContext.getMatchingBuildFileResource(mavenProject);
         Path path = ResourceUtil.getPath(resource);
         result.put(path, markers);
      });
      return result;
   }
}
