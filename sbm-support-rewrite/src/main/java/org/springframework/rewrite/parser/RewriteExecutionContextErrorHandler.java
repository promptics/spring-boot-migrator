package org.springframework.rewrite.parser;

import java.util.function.Consumer;
import org.openrewrite.java.JavaParsingException;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.internal.MavenParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RewriteExecutionContextErrorHandler implements Consumer<Throwable> {
   private static final Logger LOGGER = LoggerFactory.getLogger(RewriteExecutionContextErrorHandler.class);
   private final RewriteExecutionContextErrorHandler.ThrowExceptionSwitch throwExceptionSwitch;

   RewriteExecutionContextErrorHandler(RewriteExecutionContextErrorHandler.ThrowExceptionSwitch throwExceptionSwitch) {
      this.throwExceptionSwitch = throwExceptionSwitch;
   }

   public void accept(Throwable t) {
      if (t instanceof MavenParsingException) {
         LOGGER.warn(t.getMessage());
      } else if (t instanceof MavenDownloadingException) {
         LOGGER.warn(t.getMessage());
      } else {
         if (!(t instanceof JavaParsingException)) {
            throw new RuntimeException(t.getMessage(), t);
         }

         if (t.getMessage().equals("Failed symbol entering or attribution")) {
            throw new RuntimeException("This could be a broken jar. Activate logging on WARN level for 'org.openrewrite' might reveal more information.", t);
         }
      }
   }

   public static class ThrowExceptionSwitch {
      private boolean throwExceptions = true;
   }
}
