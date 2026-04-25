package org.springframework.rewrite.parser;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.openrewrite.xml.tree.Xml.Document;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parser.maven.MavenProject;
import org.springframework.rewrite.utils.ResourceUtil;

public class ParserContext {
   private final Path baseDir;
   private final List<Resource> resources;
   private final List<MavenProject> sortedProjects;
   private Map<Path, Document> pathDocumentMap;

   public ParserContext(Path baseDir, List<Resource> resources, List<MavenProject> sortedProjects) {
      this.baseDir = baseDir;
      this.resources = resources;
      this.sortedProjects = sortedProjects;
   }

   public List<Resource> getResources() {
      return this.resources;
   }

   public List<MavenProject> getSortedProjects() {
      return this.sortedProjects;
   }

   public List<String> getActiveProfiles() {
      return List.of("default");
   }

   public Resource getMatchingBuildFileResource(MavenProject pom) {
      return this.resources
         .stream()
         .filter(r -> ResourceUtil.getPath(r).toString().equals(pom.getPomFilePath().toString()))
         .findFirst()
         .orElseThrow(
            () -> new IllegalStateException(
                  "Could not find a resource in the list of resources that matches the path of MavenProject '%s'".formatted(pom.getPomFile().toString())
               )
         );
   }

   public List<Resource> getBuildFileResources() {
      return this.sortedProjects.stream().map(p -> p.getPomFile()).toList();
   }

   public Document getXmlDocument(Path path) {
      return this.pathDocumentMap.get(path);
   }

   public void setParsedBuildFiles(List<Document> xmlDocuments) {
      this.pathDocumentMap = xmlDocuments.stream()
         .peek(doc -> this.addSourceFileToModel(this.baseDir, this.getSortedProjects(), doc))
         .collect(Collectors.toMap(doc -> this.baseDir.resolve(doc.getSourcePath()), doc -> (Document)doc));
   }

   public List<Document> getSortedBuildFileDocuments() {
      return this.getSortedProjects().stream().map(p -> this.pathDocumentMap.get(p.getFile().toPath())).toList();
   }

   private void addSourceFileToModel(Path baseDir, List<MavenProject> sortedProjectsList, Document s) {
      sortedProjectsList.stream()
         .filter(p -> ResourceUtil.getPath(p.getPomFile()).toString().equals(baseDir.resolve(s.getSourcePath()).toString()))
         .forEach(p -> p.setSourceFile(s));
   }
}
