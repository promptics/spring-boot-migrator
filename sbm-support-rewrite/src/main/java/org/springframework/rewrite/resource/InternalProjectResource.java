package org.springframework.rewrite.resource;

public interface InternalProjectResource extends ProjectResource {
   void resetHasChanges();

   void markAsChanged();

   boolean hasChanges();

   @Override
   boolean isDeleted();
}
