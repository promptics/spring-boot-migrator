package org.springframework.rewrite.parser.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;
import org.apache.maven.model.Build;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Prerequisites;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;
import org.openrewrite.xml.tree.Xml.Document;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parser.ProjectId;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;
import org.springframework.rewrite.utils.ResourceUtil;

public class MavenProject {
   private final Path projectRoot;
   private final MavenProject.MavenBuildFile buildFile;
   private List<MavenProject> reactorProjects = new ArrayList<>();
   private final List<MavenProject> dependentProjects = new ArrayList<>();
   private final List<MavenProject> dependencyProjects = new ArrayList<>();
   private final List<MavenProject> moduleProjects = new ArrayList<>();
   private final MavenArtifactDownloader rewriteMavenArtifactDownloader;
   private final List<Resource> resources;
   private ProjectId projectId;

   public MavenProject(Path baseDir, Resource rootPom, MavenArtifactDownloader rewriteMavenArtifactDownloader, List<Resource> resources) {
      this(baseDir, rootPom, List.of(), rewriteMavenArtifactDownloader, resources);
   }

   public MavenProject(
      Path baseDir, Resource pomFile, List<MavenProject> dependsOnModels, MavenArtifactDownloader rewriteMavenArtifactDownloader, List<Resource> resources
   ) {
      this.projectRoot = baseDir;
      this.buildFile = new MavenProject.MavenBuildFile(pomFile);
      if (dependsOnModels != null) {
         this.dependentProjects.addAll(dependsOnModels);
      }

      this.rewriteMavenArtifactDownloader = rewriteMavenArtifactDownloader;
      this.resources = resources;
      this.projectId = new ProjectId(this.getGroupId(), this.getArtifactId());
   }

   public List<MavenProject> getDependentProjects() {
      return this.dependentProjects;
   }

   public void setDependencyProjects(List<MavenProject> dependencyProjects) {
      this.dependencyProjects.clear();
      this.dependencyProjects.addAll(dependencyProjects);
   }

   public List<MavenProject> getDependencyProjects() {
      return this.dependencyProjects;
   }

   public File getFile() {
      return this.buildFile.getPath().toFile();
   }

   public MavenProject.MavenBuildFile getBuildFile() {
      return this.buildFile;
   }

   public Path getBasedir() {
      return this.buildFile == null ? null : this.buildFile.getPath().getParent();
   }

   public void setReactorProjects(List<MavenProject> collected) {
      this.reactorProjects = collected;
   }

   public List<MavenProject> getCollectedProjects() {
      return this.reactorProjects;
   }

   public Path getModulePath() {
      return this.projectRoot.resolve(this.getModuleDir());
   }

   public Path getModuleDir() {
      if (this.getBasedir() == null) {
         return null;
      } else {
         return "pom.xml".equals(LinuxWindowsPathUnifier.relativize(this.projectRoot, this.buildFile.getPath()).toString())
            ? Path.of("")
            : LinuxWindowsPathUnifier.relativize(this.projectRoot, this.buildFile.getPath()).getParent();
      }
   }

   public String getGroupIdAndArtifactId() {
      return this.buildFile.getGroupIdAndArtifactId();
   }

   public Path getPomFilePath() {
      return this.buildFile.getPath();
   }

   public Plugin getPlugin(String s) {
      return this.buildFile.getBuild() == null ? null : (Plugin)this.buildFile.getBuild().getPluginsAsMap().get(s);
   }

   public Properties getProperties() {
      return this.buildFile.getProperties();
   }

   public MavenRuntimeInformation getMavenRuntimeInformation() {
      return new MavenRuntimeInformation();
   }

   public String getName() {
      return this.buildFile.getName();
   }

   public String getGroupId() {
      return this.buildFile.getGroupId() == null ? this.buildFile.getParent().getGroupId() : this.buildFile.getGroupId();
   }

   public String getArtifactId() {
      return this.buildFile.getArtifactId();
   }

   public String getVersion() {
      return this.buildFile.getVersion() == null ? this.buildFile.getParent().getVersion() : this.buildFile.getVersion();
   }

   @Override
   public String toString() {
      String groupId = this.buildFile.getGroupId() == null ? this.buildFile.getParent().getGroupId() : this.buildFile.getGroupId();
      return groupId + ":" + this.buildFile.getArtifactId();
   }

   public String getBuildDirectory() {
      String s = this.buildFile.getBuild() != null ? this.buildFile.getBuild().getDirectory() : null;
      return s == null ? this.buildFile.getPath().getParent().resolve("target").toAbsolutePath().normalize().toString() : s;
   }

   public String getSourceDirectory() {
      String s = this.buildFile.getBuild() != null ? this.buildFile.getBuild().getSourceDirectory() : null;
      return s == null ? this.buildFile.getPath().getParent().resolve("src/main/java").toAbsolutePath().normalize().toString() : s;
   }

   public List<Path> getCompileClasspathElements() {
      Scope scope = Scope.Compile;
      return this.getClasspathElements(scope);
   }

   public List<Path> getTestClasspathElements() {
      return this.getClasspathElements(Scope.Test);
   }

   @NotNull
   private List<Path> getClasspathElements(Scope scope) {
      Document pomSourceFile = this.getSourceFile();
      return this.getClasspathJars(scope, pomSourceFile);
   }

   @NotNull
   private List<Path> getClasspathJars(Scope scope, Document pomSourceFile) {
      MavenArtifactDownloader downloader = this.rewriteMavenArtifactDownloader;
      return getClasspathJars(scope, pomSourceFile, downloader);
   }

   @NotNull
   public static List<Path> getClasspathJars(Scope scope, Document pomSourceFile, MavenArtifactDownloader downloader) {
      MavenResolutionResult pom = (MavenResolutionResult)pomSourceFile.getMarkers().findFirst(MavenResolutionResult.class).get();
      List<ResolvedDependency> resolvedDependencies = (List<ResolvedDependency>)pom.getDependencies().get(scope);
      return (List<Path>)(resolvedDependencies != null
         ? resolvedDependencies.stream()
            .filter(rd -> rd.getRepository() != null)
            .map(rd -> downloader.downloadArtifact(rd))
            .filter(Objects::nonNull)
            .distinct()
            .toList()
         : new ArrayList<>());
   }

   public String getTestSourceDirectory() {
      String s = this.buildFile.getBuild() != null ? this.buildFile.getBuild().getSourceDirectory() : null;
      return s == null ? this.buildFile.getPath().getParent().resolve("src/test/java").toAbsolutePath().normalize().toString() : s;
   }

   public void setSourceFile(Document sourceFile) {
      this.buildFile.setSourceFile(sourceFile);
   }

   private static List<Resource> listJavaSources(List<Resource> resources, Path sourceDirectory) {
      return resources.stream().filter(whenIn(sourceDirectory)).filter(whenFileNameEndsWithJava()).toList();
   }

   @NotNull
   private static Predicate<Resource> whenFileNameEndsWithJava() {
      return p -> ResourceUtil.getPath(p).getFileName().toString().endsWith(".java");
   }

   @NotNull
   private static Predicate<Resource> whenIn(Path sourceDirectory) {
      return r -> {
         String resourcePath = LinuxWindowsPathUnifier.unifiedPathString(r);
         String sourceDirectoryPath = LinuxWindowsPathUnifier.unifiedPathString(sourceDirectory);
         return resourcePath.startsWith(sourceDirectoryPath);
      };
   }

   public List<Resource> getJavaSourcesInTarget() {
      return listJavaSources(this.getResources(), this.getBasedir().resolve(this.getBuildDirectory()));
   }

   private List<Resource> getResources() {
      return this.resources;
   }

   public List<Resource> getMainJavaSources() {
      Path sourceDir = this.getProjectRoot().resolve(this.getModuleDir()).resolve("src/main/java");
      return listJavaSources(this.resources, sourceDir);
   }

   public List<Resource> getTestJavaSources() {
      return listJavaSources(this.resources, this.getProjectRoot().resolve(this.getModuleDir()).resolve("src/test/java"));
   }

   public ProjectId getProjectId() {
      return this.projectId;
   }

   public Object getProjectEncoding() {
      return this.buildFile.getProperties().get("project.build.sourceEncoding");
   }

   public Path getProjectRoot() {
      return this.projectRoot;
   }

   @Deprecated
   public Resource getPomFile() {
      return this.buildFile.getPomFileResource();
   }

   public Document getSourceFile() {
      return this.buildFile.getSourceFile();
   }

   public boolean dependsOn(MavenProject model) {
      return this.dependentProjects.stream().anyMatch(m -> m.getGroupId().equals(model.getGroupId()) && m.getArtifactId().equals(model.getArtifactId()));
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         MavenProject that = (MavenProject)o;
         return Objects.equals(this.buildFile, that.buildFile);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.buildFile);
   }

   public class MavenBuildFile extends Model {
      private final Resource pomFileResource;
      @Deprecated
      private final Resource resource;
      private Document sourceFile;
      private final Model delegate;
      private static final MavenXpp3Reader XPP_3_READER = new MavenXpp3Reader();

      public MavenBuildFile(Resource pomFileResource) {
         this.pomFileResource = pomFileResource;
         this.assertPomFile(pomFileResource);
         this.resource = pomFileResource;

         try {
            this.delegate = XPP_3_READER.read(ResourceUtil.getInputStream(this.resource));
            this.delegate.setPomFile(this.resource.getFile());
            List<Dependency> dependencies = this.delegate.getDependencies();
            dependencies.forEach(d -> {
            });
         } catch (IOException var4) {
            throw new RuntimeException(var4);
         } catch (XmlPullParserException var5) {
            throw new RuntimeException(var5);
         }
      }

      private void assertPomFile(Resource resource) {
         if (!LinuxWindowsPathUnifier.unifiedPathString(resource).endsWith("pom.xml")) {
            throw new IllegalArgumentException("Provided resource '%s' is not a pom.xml file.".formatted(ResourceUtil.getPath(resource)));
         }
      }

      public void setSourceFile(Document sourceFile) {
         this.sourceFile = sourceFile;
      }

      public Document getSourceFile() {
         return this.sourceFile;
      }

      public String getContent() {
         return ResourceUtil.getContent(this.pomFileResource);
      }

      public Path getPath() {
         return ResourceUtil.getPath(this.pomFileResource);
      }

      public Resource getPomFileResource() {
         return this.pomFileResource;
      }

      public String toString() {
         return (this.delegate.getGroupId() == null ? this.delegate.getParent().getGroupId() : this.delegate.getGroupId())
            + ":"
            + this.delegate.getArtifactId();
      }

      public String getArtifactId() {
         return this.delegate.getArtifactId();
      }

      public Build getBuild() {
         return this.delegate.getBuild();
      }

      public String getChildProjectUrlInheritAppendPath() {
         return this.delegate.getChildProjectUrlInheritAppendPath();
      }

      public CiManagement getCiManagement() {
         return this.delegate.getCiManagement();
      }

      public List<Contributor> getContributors() {
         return this.delegate.getContributors();
      }

      public String getDescription() {
         return this.delegate.getDescription();
      }

      public List<Developer> getDevelopers() {
         return this.delegate.getDevelopers();
      }

      public String getGroupId() {
         return this.delegate.getGroupId();
      }

      public String getInceptionYear() {
         return this.delegate.getInceptionYear();
      }

      public IssueManagement getIssueManagement() {
         return this.delegate.getIssueManagement();
      }

      public List<License> getLicenses() {
         return this.delegate.getLicenses();
      }

      public List<MailingList> getMailingLists() {
         return this.delegate.getMailingLists();
      }

      public String getModelEncoding() {
         return this.delegate.getModelEncoding();
      }

      public String getModelVersion() {
         return this.delegate.getModelVersion();
      }

      public String getName() {
         String name = this.delegate.getName();
         if (name == null) {
            name = this.delegate.getArtifactId();
         }

         return name;
      }

      public Organization getOrganization() {
         return this.delegate.getOrganization();
      }

      public String getPackaging() {
         return this.delegate.getPackaging();
      }

      public Parent getParent() {
         return this.delegate.getParent();
      }

      public Prerequisites getPrerequisites() {
         return this.delegate.getPrerequisites();
      }

      public List<Profile> getProfiles() {
         return this.delegate.getProfiles();
      }

      public Scm getScm() {
         return this.delegate.getScm();
      }

      public String getUrl() {
         return this.delegate.getUrl();
      }

      public String getVersion() {
         return this.delegate.getVersion();
      }

      public File getPomFile() {
         return this.delegate.getPomFile();
      }

      public File getProjectDirectory() {
         return this.delegate.getPomFile().toPath().getParent().toFile();
      }

      public String getId() {
         return this.delegate.getId();
      }

      public List<Dependency> getDependencies() {
         return this.delegate.getDependencies();
      }

      public DependencyManagement getDependencyManagement() {
         return this.delegate.getDependencyManagement();
      }

      public DistributionManagement getDistributionManagement() {
         return this.delegate.getDistributionManagement();
      }

      public InputLocation getLocation(Object key) {
         return this.delegate.getLocation(key);
      }

      public List<String> getModules() {
         return this.delegate.getModules();
      }

      public List<Repository> getPluginRepositories() {
         return this.delegate.getPluginRepositories();
      }

      public Properties getProperties() {
         return this.delegate.getProperties();
      }

      public Reporting getReporting() {
         return this.delegate.getReporting();
      }

      public Object getReports() {
         return this.delegate.getReports();
      }

      public List<Repository> getRepositories() {
         return this.delegate.getRepositories();
      }

      public String getGroupIdAndArtifactId() {
         return this.getGroupId() + ":" + this.getArtifactId();
      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            MavenProject.MavenBuildFile that = (MavenProject.MavenBuildFile)o;
            Path thisPath = ResourceUtil.getPath(this.pomFileResource);
            Path thatPath = ResourceUtil.getPath(that.pomFileResource);
            return Objects.equals(thisPath, thatPath);
         } else {
            return false;
         }
      }

      public int hashCode() {
         return Objects.hash(this.pomFileResource);
      }
   }
}
