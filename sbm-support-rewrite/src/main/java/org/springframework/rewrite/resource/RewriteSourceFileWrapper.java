package org.springframework.rewrite.resource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.openrewrite.SourceFile;

public class RewriteSourceFileWrapper {
   public List<RewriteSourceFileHolder<? extends SourceFile>> wrapRewriteSourceFiles(Path absoluteProjectDir, List<SourceFile> parsedByRewrite) {
      return parsedByRewrite.stream().map(sf -> this.wrapRewriteSourceFile(absoluteProjectDir, sf)).collect(Collectors.toList());
   }

   private RewriteSourceFileHolder<?> wrapRewriteSourceFile(Path absoluteProjectDir, SourceFile sourceFile) {
      return new RewriteSourceFileHolder<>(absoluteProjectDir, sourceFile);
   }
}
