package org.springframework.rewrite.parser.events;

import java.util.List;
import org.openrewrite.SourceFile;

public record SuccessfullyParsedProjectEvent(List<SourceFile> sourceFiles) {
}
