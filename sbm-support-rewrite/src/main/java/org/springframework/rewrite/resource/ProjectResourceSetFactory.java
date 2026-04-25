package org.springframework.rewrite.resource;

import java.nio.file.Path;
import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;

public class ProjectResourceSetFactory {
   private final RewriteMigrationResultMerger rewriteMigrationResultMerger;
   private final RewriteSourceFileWrapper sourceFileWrapper;
   private final ExecutionContext executionContext;

   public ProjectResourceSetFactory(
      RewriteMigrationResultMerger rewriteMigrationResultMerger, RewriteSourceFileWrapper sourceFileWrapper, ExecutionContext executionContext
   ) {
      this.rewriteMigrationResultMerger = rewriteMigrationResultMerger;
      this.sourceFileWrapper = sourceFileWrapper;
      this.executionContext = executionContext;
   }

   public ProjectResourceSet create(Path baseDir, List<SourceFile> sourceFiles) {
      baseDir = baseDir.toAbsolutePath().normalize();
      List<RewriteSourceFileHolder<? extends SourceFile>> rewriteSourceFileHolders = this.sourceFileWrapper.wrapRewriteSourceFiles(baseDir, sourceFiles);
      return this.createFromSourceFileHolders(rewriteSourceFileHolders);
   }

   public ProjectResourceSet createFromSourceFileHolders(List<RewriteSourceFileHolder<? extends SourceFile>> rewriteSourceFileHolders) {
      return new ProjectResourceSet(rewriteSourceFileHolders, this.executionContext, this.rewriteMigrationResultMerger);
   }
}
