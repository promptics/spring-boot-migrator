package org.springframework.rewrite.resource;

public abstract class BaseProjectResource implements InternalProjectResource {
   protected boolean isChanged = false;
   private boolean isDeleted = false;

   @Override
   public boolean hasChanges() {
      return this.isChanged;
   }

   @Override
   public void resetHasChanges() {
      this.isChanged = false;
   }

   @Override
   public void markAsChanged() {
      this.isChanged = true;
   }

   @Override
   public void delete() {
      this.isDeleted = true;
      this.markAsChanged();
   }

   @Override
   public boolean isDeleted() {
      return this.isDeleted;
   }
}
