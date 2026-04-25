package org.springframework.rewrite.utils;

import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

public class LinuxWindowsPathUnifier {
   public static Path relativize(Path subpath, Path path) {
      LinuxWindowsPathUnifier linuxWindowsPathUnifier = new LinuxWindowsPathUnifier();
      String unifiedAbsoluteRootPath = unifiedPathString(subpath);
      String pathUnified = unifiedPathString(path);
      return Path.of(unifiedAbsoluteRootPath).relativize(Path.of(pathUnified));
   }

   public static String unifiedPathString(Path path) {
      return unifiedPathString(path.toString());
   }

   public static Path unifiedPath(Path path) {
      return Path.of(unifiedPathString(path));
   }

   public static String unifiedPathString(Resource r) {
      return unifiedPathString(ResourceUtil.getPath(r));
   }

   public static String unifiedPathString(String path) {
      path = StringUtils.cleanPath(path);
      if (isWindows()) {
         path = transformToLinuxPath(path);
      }

      return path;
   }

   public static Path unifiedPath(String path) {
      return Path.of(unifiedPathString(path));
   }

   static boolean isWindows() {
      return System.getProperty("os.name").contains("Windows");
   }

   private static String transformToLinuxPath(String path) {
      return path.replaceAll("^[\\w]+:\\/?", "/");
   }

   public static boolean pathEquals(Resource r, Path path) {
      return unifiedPathString(ResourceUtil.getPath(r)).equals(unifiedPathString(path.normalize()));
   }

   public static boolean pathEquals(Path basedir, String parentPomPath) {
      return unifiedPathString(basedir).equals(parentPomPath);
   }

   public static boolean pathEquals(Path path1, Path path2) {
      return unifiedPathString(path1).equals(unifiedPathString(path2));
   }

   public static boolean pathStartsWith(Resource r, Path path) {
      return ResourceUtil.getPath(r).toString().startsWith(unifiedPathString(path));
   }
}
