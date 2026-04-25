package org.springframework.rewrite.parser.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;

public class MavenProjectAnalyzer {
   private final MavenProjectSorter mavenProjectSorter;
   private final MavenProjectFactory mavenProjectFactory;

   public MavenProjectAnalyzer(MavenProjectSorter mavenProjectSorter, MavenProjectFactory mavenProjectFactory) {
      this.mavenProjectSorter = mavenProjectSorter;
      this.mavenProjectFactory = mavenProjectFactory;
   }

   public List<MavenProject> getBuildProjects(Path baseDir, List<Resource> resources) {
      List<MavenProject> allMavenProjects = this.mavenProjectFactory.create(baseDir, resources);
      List<MavenProject> mavenProjects = this.mavenProjectSorter.sort(baseDir, allMavenProjects);
      return this.map(baseDir, resources, mavenProjects);
   }

   private List<MavenProject> map(Path baseDir, List<Resource> resources, List<MavenProject> sortedModels) {
      List<MavenProject> mavenProjects = new ArrayList<>();
      sortedModels.stream().filter(Objects::nonNull).forEach(mavenProject -> {
         String projectDir = LinuxWindowsPathUnifier.unifiedPathString(baseDir.resolve(mavenProject.getModuleDir()).normalize());
         List<Resource> filteredResources = resources.stream().filter(r -> LinuxWindowsPathUnifier.unifiedPathString(r).startsWith(projectDir)).toList();
         mavenProjects.add(mavenProject);
      });
      List<MavenProject> collected = new ArrayList<>(mavenProjects);
      collected.remove(0);
      mavenProjects.get(0).setReactorProjects(collected);
      return mavenProjects;
   }
}
