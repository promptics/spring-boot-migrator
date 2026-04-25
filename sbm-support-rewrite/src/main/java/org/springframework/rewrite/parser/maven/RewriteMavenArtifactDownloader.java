package org.springframework.rewrite.parser.maven;

import java.util.function.Consumer;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.ipc.http.HttpSender;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.maven.cache.MavenArtifactCache;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;

public class RewriteMavenArtifactDownloader extends MavenArtifactDownloader {
   public RewriteMavenArtifactDownloader(MavenArtifactCache mavenArtifactCache, @Nullable MavenSettings mavenSettings, Consumer<Throwable> onError) {
      super(mavenArtifactCache, mavenSettings, onError);
   }

   public RewriteMavenArtifactDownloader(
      MavenArtifactCache mavenArtifactCache, @Nullable MavenSettings settings, HttpSender httpSender, Consumer<Throwable> onError
   ) {
      super(mavenArtifactCache, settings, httpSender, onError);
   }
}
