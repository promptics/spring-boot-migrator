package org.springframework.rewrite.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.Resource;

public class ResourceUtil {
   public static Path getPath(Resource resource) {
      try {
         return resource.getFile().toPath();
      } catch (IOException var2) {
         throw new RuntimeException(var2);
      }
   }

   public static InputStream getInputStream(Resource resource) {
      try {
         return resource.getInputStream();
      } catch (IOException var2) {
         throw new RuntimeException(var2);
      }
   }

   public static void write(Path basePath, List<Resource> resources) {
      try {
         Files.createDirectories(basePath);
         resources.stream().forEach(r -> persistResource(basePath, r));
      } catch (IOException var3) {
         throw new RuntimeException(var3);
      }
   }

   private static void persistResource(Path basePath, Resource r) {
      Path resourcePath = getPath(r);
      if (resourcePath.isAbsolute()) {
         Path e = resourcePath.relativize(basePath);
      } else {
         resourcePath = basePath.resolve(resourcePath).toAbsolutePath().normalize();
      }

      if (!resourcePath.toFile().exists()) {
         try {
            if (!resourcePath.getParent().toFile().exists()) {
               Files.createDirectories(resourcePath.getParent());
            }

            Files.writeString(resourcePath, getContent(r));
         } catch (IOException var4) {
            throw new RuntimeException(var4);
         }
      }
   }

   public static String getContent(Resource r) {
      try {
         return new String(getInputStream(r).readAllBytes());
      } catch (IOException var2) {
         throw new RuntimeException(var2);
      }
   }

   public static long contentLength(Resource resource) {
      try {
         return resource.contentLength();
      } catch (IOException var2) {
         throw new RuntimeException(var2);
      }
   }
}
