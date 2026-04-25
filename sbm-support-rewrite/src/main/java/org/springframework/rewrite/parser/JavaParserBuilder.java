package org.springframework.rewrite.parser;

import java.nio.charset.Charset;
import java.util.Collection;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaParser.Builder;
import org.openrewrite.java.internal.JavaTypeCache;

public class JavaParserBuilder extends Builder {
   private Builder delegate = JavaParser.fromJavaVersion();

   public JavaParser build() {
      return this.delegate.build();
   }

   public Builder charset(Charset charset) {
      return this.delegate.charset(charset);
   }

   public Builder classpath(Collection classpath) {
      return this.delegate.classpath(classpath);
   }

   public Builder classpath(String... classpath) {
      return this.delegate.classpath(classpath);
   }

   public Builder classpathFromResources(ExecutionContext ctx, String... classpath) {
      return this.delegate.classpathFromResources(ctx, classpath);
   }

   public Builder getDelegate() {
      return this.delegate;
   }

   public Builder classpath(byte[]... classpath) {
      return this.delegate.classpath(classpath);
   }

   public Builder logCompilationWarningsAndErrors(boolean logCompilationWarningsAndErrors) {
      return this.delegate.logCompilationWarningsAndErrors(logCompilationWarningsAndErrors);
   }

   public Builder typeCache(JavaTypeCache javaTypeCache) {
      return this.delegate.typeCache(javaTypeCache);
   }

   public Builder dependsOn(Collection collection) {
      return this.delegate.dependsOn(collection);
   }

   public Builder dependsOn(String... inputsAsStrings) {
      return this.delegate.dependsOn(inputsAsStrings);
   }

   public Builder styles(Iterable iterable) {
      return this.delegate.styles(iterable);
   }

   public String getDslName() {
      return this.delegate.getDslName();
   }
}
