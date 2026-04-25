package org.springframework.rewrite.resource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;

public class ProjectResourceSet {
   private final List<RewriteSourceFileHolder<? extends SourceFile>> projectResources = new ArrayList<>();
   private final ExecutionContext executionContext;
   private final RewriteMigrationResultMerger migrationResultMerger;

   public ProjectResourceSet(
      List<RewriteSourceFileHolder<? extends SourceFile>> projectResources,
      ExecutionContext executionContext,
      RewriteMigrationResultMerger migrationResultMerger
   ) {
      this.executionContext = executionContext;
      this.migrationResultMerger = migrationResultMerger;
      this.projectResources.addAll(projectResources);
   }

   public List<RewriteSourceFileHolder<? extends SourceFile>> list() {
      return this.stream().toList();
   }

   public Stream<RewriteSourceFileHolder<? extends SourceFile>> stream() {
      return this.projectResources.stream().filter(r -> r != null && !r.isDeleted());
   }

   public ProjectResource get(int index) {
      return this.list().get(index);
   }

   public void add(RewriteSourceFileHolder<? extends SourceFile> newResource) {
      this.projectResources.add(newResource);
   }

   public void replace(int index, RewriteSourceFileHolder<? extends SourceFile> newResource) {
      this.projectResources.set(index, newResource);
   }

   public void replace(Path path, RewriteSourceFileHolder<? extends SourceFile> newResource) {
      int index = this.indexOf(path);
      this.projectResources.set(index, newResource);
   }

   public int size() {
      return this.projectResources.size();
   }

   public int indexOf(Path absolutePath) {
      return this.projectResources.stream().map(ProjectResource::getAbsolutePath).collect(Collectors.toList()).indexOf(absolutePath);
   }

   public void apply(Recipe... recipes) {
      InMemoryLargeSourceSet largeSourceSet = new InMemoryLargeSourceSet(
         this.projectResources.stream().map(RewriteSourceFileHolder::getSourceFile).filter(SourceFile.class::isInstance).map(SourceFile.class::cast).toList()
      );
      List<Result> results = (new Recipe() {
         public String getDisplayName() {
            return "Run a list of recipes";
         }

         public String getDescription() {
            return this.getDisplayName();
         }

         public List<Recipe> getRecipeList() {
            return Arrays.asList(recipes);
         }
      }).run(largeSourceSet, this.executionContext).getChangeset().getAllResults();
      this.migrationResultMerger.mergeResults(this, results);
   }

   void clearDeletedResources() {
      Iterator<RewriteSourceFileHolder<? extends SourceFile>> iterator = this.projectResources.iterator();

      while (iterator.hasNext()) {
         RewriteSourceFileHolder<? extends SourceFile> current = iterator.next();
         if (current.isDeleted()) {
            iterator.remove();
         }
      }
   }

   public Stream<RewriteSourceFileHolder<? extends SourceFile>> streamIncludingDeleted() {
      return this.projectResources.stream();
   }

   private Optional<RewriteSourceFileHolder<? extends SourceFile>> findResourceByPath(Path sourcePath) {
      return this.projectResources.stream().filter(pr -> pr.getSourcePath().toString().equals(sourcePath.toString())).findFirst();
   }
}
