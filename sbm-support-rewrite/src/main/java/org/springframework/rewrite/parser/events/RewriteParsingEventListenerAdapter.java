package org.springframework.rewrite.parser.events;

import org.openrewrite.SourceFile;
import org.openrewrite.Parser.Input;
import org.openrewrite.tree.ParsingEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

public class RewriteParsingEventListenerAdapter implements ParsingEventListener {
   private static final Logger LOGGER = LoggerFactory.getLogger(RewriteParsingEventListenerAdapter.class);
   private final ApplicationEventPublisher eventPublisher;

   public RewriteParsingEventListenerAdapter(ApplicationEventPublisher eventPublisher) {
      this.eventPublisher = eventPublisher;
   }

   public void intermediateMessage(String stateMessage) {
      this.eventPublisher.publishEvent(new IntermediateParsingEvent(stateMessage));
   }

   public void startedParsing(Input input) {
      this.eventPublisher.publishEvent(new StartedParsingResourceEvent(input));
   }

   public void parsed(Input input, SourceFile sourceFile) {
      LOGGER.debug("Parsed %s to %s".formatted(input.getPath(), sourceFile.getSourcePath()));
      this.eventPublisher.publishEvent(new FinishedParsingResourceEvent(input, sourceFile));
   }
}
