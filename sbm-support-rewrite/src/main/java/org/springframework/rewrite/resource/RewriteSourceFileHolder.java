package org.springframework.rewrite.resource;

import java.nio.file.Path;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.lang.Nullable;

public class RewriteSourceFileHolder<T extends SourceFile> extends BaseProjectResource implements InternalProjectResource {
   private T sourceFile;
   private final Path absoluteProjectDir;

   public RewriteSourceFileHolder(Path absoluteProjectDir, T sourceFile) {
      this.absoluteProjectDir = absoluteProjectDir;
      this.sourceFile = sourceFile;
      if (!this.absoluteProjectDir.isAbsolute()) {
         throw new IllegalArgumentException(String.format("Given projectDir '%s' is not absolute.", absoluteProjectDir));
      }
   }

   public Path getAbsoluteProjectDir() {
      return this.absoluteProjectDir;
   }

   @Override
   public String print() {
      try {
         return this.sourceFile.printAll();
      } catch (Exception var2) {
         throw new RuntimeException("Exception while printing '%s'".formatted(this.sourceFile.getSourcePath()), var2);
      }
   }

   @Override
   public Path getSourcePath() {
      return this.sourceFile.getSourcePath();
   }

   @Override
   public Path getAbsolutePath() {
      return this.absoluteProjectDir.resolve(this.getSourcePath()).normalize().toAbsolutePath();
   }

   @Override
   public void moveTo(Path newPath) {
      if (newPath.isAbsolute()) {
         newPath = this.absoluteProjectDir.relativize(newPath);
      }

      if (this.absoluteProjectDir.resolve(newPath).toFile().isDirectory()) {
         newPath = newPath.resolve(this.getAbsolutePath().getFileName());
      }

      this.sourceFile = (T)this.sourceFile.withSourcePath(newPath);
      this.markChanged();
   }

   public T getSourceFile() {
      return this.sourceFile;
   }

   public void replaceWith(@Nullable SourceFile fixedSourceFile) {
      if (this.sourceFile != null && fixedSourceFile != null && !this.sourceFile.printAll().equals(fixedSourceFile.printAll())) {
         this.markChanged();
      }

      this.sourceFile = (T)fixedSourceFile;
   }

   public void markChanged() {
      this.isChanged = true;
   }

   public Class<? extends SourceFile> getType() {
      return (Class<? extends SourceFile>)this.getSourceFile().getClass();
   }

   @Override
   public String toString() {
      return this.getAbsolutePath().toString();
   }
}
