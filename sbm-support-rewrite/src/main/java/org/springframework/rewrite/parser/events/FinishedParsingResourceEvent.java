package org.springframework.rewrite.parser.events;

import org.openrewrite.SourceFile;
import org.openrewrite.Parser.Input;

public record FinishedParsingResourceEvent(Input input, SourceFile sourceFile) {
}
