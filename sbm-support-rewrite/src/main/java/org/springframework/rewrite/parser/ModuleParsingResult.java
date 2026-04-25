package org.springframework.rewrite.parser;

import java.util.ArrayList;
import java.util.List;
import org.openrewrite.SourceFile;
import org.springframework.rewrite.parser.maven.MavenProject;

public record ModuleParsingResult(
   MavenProject currentProject,
   SourceSetParsingResult mainSourcesParsingResult,
   SourceSetParsingResult testSourcesParsingResult,
   List<SourceFile> resourceFilesList
) {
   public List<? extends SourceFile> sourceFiles() {
      List<SourceFile> allSourceFiles = new ArrayList<>();
      allSourceFiles.addAll(this.mainSourcesParsingResult.sourceFiles());
      allSourceFiles.addAll(this.testSourcesParsingResult.sourceFiles());
      allSourceFiles.addAll(this.resourceFilesList);
      return allSourceFiles;
   }
}
