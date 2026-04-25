package org.springframework.rewrite.resource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.springframework.rewrite.resource.finder.AbsolutePathResourceFinder;

public class RewriteMigrationResultMerger {
   private final RewriteSourceFileWrapper surceFileWrapper;

   public RewriteMigrationResultMerger(RewriteSourceFileWrapper surceFileWrapper) {
      this.surceFileWrapper = surceFileWrapper;
   }

   public void mergeResults(ProjectResourceSet resourceSet, List<Result> results) {
      results.forEach(result -> {
         SourceFile after = result.getAfter();
         SourceFile before = result.getBefore();
         if (after == null) {
            this.handleDeleted(resourceSet, before);
         } else if (before == null) {
            this.handleAdded(resourceSet, after);
         } else {
            this.handleModified(resourceSet, after);
         }
      });
   }

   private void handleDeleted(ProjectResourceSet resourceSet, SourceFile before) {
      Path path = resourceSet.list().get(0).getAbsoluteProjectDir().resolve(before.getSourcePath());
      Optional<RewriteSourceFileHolder<? extends SourceFile>> match = new AbsolutePathResourceFinder(path).apply(resourceSet);
      match.get().delete();
   }

   private void handleAdded(ProjectResourceSet resourceSet, SourceFile after) {
      RewriteSourceFileHolder<? extends SourceFile> modifiableProjectResource = this.surceFileWrapper
         .wrapRewriteSourceFiles(resourceSet.list().get(0).getAbsoluteProjectDir(), List.of(after))
         .get(0);
      resourceSet.add(modifiableProjectResource);
   }

   private void handleModified(ProjectResourceSet resourceSet, SourceFile after) {
      Path absoluteProjectDir = resourceSet.list().get(0).getAbsoluteProjectDir();
      Path resolve = absoluteProjectDir.resolve(after.getSourcePath());
      Optional<RewriteSourceFileHolder<? extends SourceFile>> modifiedResource = new AbsolutePathResourceFinder(resolve).apply(resourceSet);
      if (modifiedResource.isEmpty()) {
         throw new IllegalStateException("Could not find resource matching path '%s'".formatted(resolve));
      } else {
         this.replaceWrappedResource(modifiedResource.get(), after);
      }
   }

   private <T extends SourceFile> void replaceWrappedResource(RewriteSourceFileHolder<T> resource, SourceFile r) {
      Class<? extends SourceFile> type = resource.getType();
      resource.replaceWith(type.cast(r));
   }
}
