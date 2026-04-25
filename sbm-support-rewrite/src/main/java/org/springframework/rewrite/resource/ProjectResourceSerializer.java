package org.springframework.rewrite.resource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProjectResourceSerializer {
   public void writeChanges(InternalProjectResource projectResource) {
      if (projectResource != null && projectResource.hasChanges()) {
         Path absolutePath = projectResource.getAbsolutePath();
         if (projectResource.isDeleted()) {
            try {
               if (Files.exists(absolutePath)) {
                  Files.delete(absolutePath);
               }
            } catch (IOException var8) {
               throw new RuntimeException("Can't delete file [" + absolutePath + "]", var8);
            }
         } else {
            try {
               Files.createDirectories(absolutePath.getParent());
            } catch (IOException var7) {
               throw new RuntimeException(var7);
            }

            try (BufferedWriter sourceFileWriter = Files.newBufferedWriter(absolutePath)) {
               String newSource = projectResource.print();
               sourceFileWriter.write(newSource);
               projectResource.resetHasChanges();
            } catch (IOException var10) {
               throw new RuntimeException("Can't write back changes in [" + absolutePath + "]", var10);
            }
         }
      }
   }
}
