package org.springframework.rewrite.parser.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class MavenProjectSorter {
   private static final String POM_XML = "pom.xml";
   private final MavenProjectGraph mavenProjectGraph;

   public MavenProjectSorter(MavenProjectGraph mavenProjectGraph) {
      this.mavenProjectGraph = mavenProjectGraph;
   }

   public List<MavenProject> sort(Path baseDir, List<MavenProject> allPomFiles) {
      Map<MavenProject, Set<MavenProject>> graph = this.mavenProjectGraph.from(baseDir, allPomFiles);
      List<MavenProject> buildOrder = new ArrayList<>();
      Map<MavenProject, Set<MavenProject>> dependingProjects = new HashMap<>();
      graph.keySet()
         .forEach(
            mavenProject -> graph.entrySet()
                  .stream()
                  .peek(e -> dependingProjects.computeIfAbsent(mavenProject, __ -> new HashSet<>()))
                  .filter(e -> e.getValue().contains(mavenProject))
                  .forEach(e -> dependingProjects.get(mavenProject).add(e.getKey()))
         );
      Map<MavenProject, Integer> inDegree = dependingProjects.keySet().stream().collect(Collectors.toMap(k -> (MavenProject)k, __ -> 0));
      dependingProjects.entrySet().stream().flatMap(e -> e.getValue().stream()).forEach(d -> inDegree.put(d, inDegree.get(d) + 1));
      Queue<MavenProject> sources = new PriorityQueue<>(Comparator.comparing(p -> p.getBuildFile().getArtifactId()));

      for (Entry<MavenProject, Integer> entry : inDegree.entrySet()) {
         if (entry.getValue() == 0) {
            sources.add(entry.getKey());
         }
      }

      while (!sources.isEmpty()) {
         MavenProject project = sources.poll();
         buildOrder.add(project);

         for (MavenProject child : new ArrayList<>(dependingProjects.get(project))) {
            inDegree.put(child, inDegree.get(child) - 1);
            if (inDegree.get(child) == 0) {
               sources.add(child);
            }
         }
      }

      if (buildOrder.size() != inDegree.size()) {
         throw new RuntimeException("Cycle detected Maven projects");
      } else {
         return buildOrder;
      }
   }
}
