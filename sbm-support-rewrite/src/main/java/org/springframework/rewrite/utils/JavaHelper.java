package org.springframework.rewrite.utils;

public class JavaHelper {
   public static String lowercaseFirstChar(String name) {
      return Character.toLowerCase(name.charAt(0)) + name.substring(1);
   }

   public static String uppercaseFirstChar(String name) {
      return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
   }
}
