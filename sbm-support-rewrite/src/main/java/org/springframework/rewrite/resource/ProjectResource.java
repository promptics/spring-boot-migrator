package org.springframework.rewrite.resource;

import java.nio.file.Path;

public interface ProjectResource {
   String print();

   Path getSourcePath();

   Path getAbsolutePath();

   void delete();

   boolean isDeleted();

   void moveTo(Path newPath);
}
