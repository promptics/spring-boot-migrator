package org.springframework.rewrite.utils;

import java.util.Comparator;
import java.util.Map;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

public class OsAgnosticPathMatcher implements PathMatcher {
   private PathMatcher pathMatcher = new AntPathMatcher();
   private LinuxWindowsPathUnifier pathUnifier = new LinuxWindowsPathUnifier();

   public boolean isPattern(String s) {
      return this.pathMatcher.isPattern(s);
   }

   public boolean match(String pattern, String path) {
      path = this.unifyPath(path);
      return this.pathMatcher.match(pattern, path);
   }

   private String unifyPath(String path) {
      return LinuxWindowsPathUnifier.unifiedPathString(path);
   }

   public boolean matchStart(String pattern, String path) {
      path = this.unifyPath(path);
      return this.pathMatcher.matchStart(pattern, path);
   }

   public String extractPathWithinPattern(String pattern, String path) {
      path = this.unifyPath(path);
      return this.pathMatcher.extractPathWithinPattern(pattern, path);
   }

   public Map<String, String> extractUriTemplateVariables(String pattern, String path) {
      path = this.unifyPath(path);
      return this.pathMatcher.extractUriTemplateVariables(pattern, path);
   }

   public Comparator<String> getPatternComparator(String path) {
      path = this.unifyPath(path);
      return this.pathMatcher.getPatternComparator(path);
   }

   public String combine(String pattern1, String pattern2) {
      return this.pathMatcher.combine(pattern1, pattern2);
   }
}
