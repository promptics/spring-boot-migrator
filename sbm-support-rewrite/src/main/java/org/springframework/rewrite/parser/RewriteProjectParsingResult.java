package org.springframework.rewrite.parser;

import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;

public record RewriteProjectParsingResult(List<SourceFile> sourceFiles, ExecutionContext executionContext) {
}
