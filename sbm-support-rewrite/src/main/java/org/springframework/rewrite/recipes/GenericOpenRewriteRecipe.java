package org.springframework.rewrite.recipes;

import java.util.function.Supplier;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

public class GenericOpenRewriteRecipe<V extends TreeVisitor<?, ExecutionContext>> extends Recipe {
   private final Supplier<V> visitorSupplier;
   private final String description;

   public GenericOpenRewriteRecipe() {
      this.description = null;
      this.visitorSupplier = null;
   }

   public GenericOpenRewriteRecipe(String description, Supplier<V> visitor) {
      this.visitorSupplier = visitor;
      this.description = description;
   }

   public GenericOpenRewriteRecipe(Supplier<V> visitor) {
      this("Executing visitor %s".formatted(visitor.get().getClass()), visitor);
   }

   public TreeVisitor<?, ExecutionContext> getVisitor() {
      return this.visitorSupplier.get();
   }

   public String getDisplayName() {
      return this.visitorSupplier != null ? this.visitorSupplier.get().getClass().getSimpleName() : "???";
   }

   public String getDescription() {
      return this.description;
   }
}
