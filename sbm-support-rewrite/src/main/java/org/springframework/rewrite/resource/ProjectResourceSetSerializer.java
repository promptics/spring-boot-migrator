package org.springframework.rewrite.resource;

public class ProjectResourceSetSerializer {
   private final ProjectResourceSerializer resourceSerializer;

   public ProjectResourceSetSerializer(ProjectResourceSerializer resourceSerializer) {
      this.resourceSerializer = resourceSerializer;
   }

   public void writeChanges(ProjectResourceSet projectResourceSet) {
      projectResourceSet.streamIncludingDeleted().forEach(this.resourceSerializer::writeChanges);
      projectResourceSet.clearDeletedResources();
   }
}
