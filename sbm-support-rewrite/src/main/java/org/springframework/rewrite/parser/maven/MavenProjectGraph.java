package org.springframework.rewrite.parser.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.rewrite.parser.ProjectId;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;

public class MavenProjectGraph {
   private static final String POM_XML = "pom.xml";
   private Map<ProjectId, MavenProject> gaToMavenProjectMap = new HashMap<>();

   public Map<MavenProject, Set<MavenProject>> from(Path baseDir, List<MavenProject> allMavenProjects) {
      this.initGaToMavenProjectMap(allMavenProjects);
      MavenProject rootProject = this.findRootProject(baseDir, allMavenProjects);
      Map<MavenProject, Set<MavenProject>> dag = new HashMap<>();
      this.buildDependencyGraph(rootProject, allMavenProjects, dag);
      this.enrichGraphWithDependencies(dag);
      return dag;
   }

   private void enrichGraphWithDependencies(Map<MavenProject, Set<MavenProject>> dag) {
      dag.keySet().stream().forEach(curProject -> {
         List<MavenProject> dependencyProjects = new ArrayList<>();
         curProject.getBuildFile().getDependencies().forEach(d -> {
            ProjectId projectId = new ProjectId(d.getGroupId(), d.getArtifactId());
            if (this.gaToMavenProjectMap.containsKey(projectId)) {
               MavenProject dependencyMavenProject = this.gaToMavenProjectMap.get(projectId);
               dependencyProjects.add(dependencyMavenProject);
            }
         });
         curProject.setDependencyProjects(dependencyProjects);
      });
      dag.keySet().stream().forEach(curProject -> {
         Set<MavenProject> dependentProjects = dag.get(curProject);
         curProject.getBuildFile().getDependencies().stream().forEach(dependency -> {
            ProjectId projectId = new ProjectId(dependency.getGroupId(), dependency.getArtifactId());
            if (this.gaToMavenProjectMap.containsKey(projectId)) {
               MavenProject dependantProject = this.gaToMavenProjectMap.get(projectId);
               if (dag.containsKey(dependantProject)) {
                  dependentProjects.add(dependantProject);
               }
            }
         });
      });
   }

   private void initGaToMavenProjectMap(List<MavenProject> allPomFiles) {
      this.gaToMavenProjectMap = new HashMap<>();
      allPomFiles.stream().forEach(mp -> this.gaToMavenProjectMap.putIfAbsent(new ProjectId(mp.getGroupId(), mp.getArtifactId()), mp));
   }

   private void buildDependencyGraph(MavenProject currentProject, List<MavenProject> reactorProjects, Map<MavenProject, Set<MavenProject>> dag) {
      if (isSingleModuleProject(reactorProjects)) {
         logDependentProject(currentProject, dag, null);
      } else {
         if (isMultiModuleProject(currentProject)) {
            currentProject.getBuildFile().getModules().stream().map(moduleName -> {
               Path modulePath = currentProject.getModulePath().resolve(moduleName).normalize();
               return reactorProjects.stream().filter(p -> LinuxWindowsPathUnifier.pathEquals(p.getModulePath(), modulePath)).findFirst().get();
            }).forEach(childProject -> {
               logDependentProject(childProject, dag, currentProject);
               this.buildDependencyGraph(childProject, reactorProjects, dag);
            });
         }

         dag.computeIfAbsent(currentProject, __ -> new HashSet<>());
      }
   }

   private static boolean isMultiModuleProject(MavenProject currentProject) {
      return hasPomPackaging(currentProject);
   }

   private static boolean isSingleModuleProject(List<MavenProject> reactorProjects) {
      return reactorProjects.size() == 1;
   }

   private static void logDependentProject(MavenProject dependingProject, Map<MavenProject, Set<MavenProject>> dag, MavenProject dependantProject) {
      Set<MavenProject> mavenProjects = dag.computeIfAbsent(dependingProject, __ -> new HashSet<>());
      if (dependantProject != null) {
         mavenProjects.add(dependantProject);
      }
   }

   private MavenProject findRootProject(Path baseDir, List<MavenProject> reactorProjects) {
      return reactorProjects.stream().filter(p -> LinuxWindowsPathUnifier.pathEquals(p.getBuildFile().getPath(), baseDir.resolve("pom.xml"))).findFirst().get();
   }

   private static boolean hasPomPackaging(MavenProject curMavenProject) {
      return "pom".equals(curMavenProject.getBuildFile().getPackaging());
   }
}
