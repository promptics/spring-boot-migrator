package org.springframework.rewrite.parser.maven;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.utils.ResourceUtil;

class MavenBuildFileFilter {
   private static final String POM_XML = "pom.xml";

   @NotNull
   static List<Resource> filterBuildFiles(List<Resource> resources) {
      return resources.stream().filter(r -> ResourceUtil.getPath(r).getFileName().toString().equals("pom.xml")).toList();
   }
}
