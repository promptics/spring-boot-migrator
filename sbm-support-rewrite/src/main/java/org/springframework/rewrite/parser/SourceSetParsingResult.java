package org.springframework.rewrite.parser;

import java.util.List;
import org.openrewrite.SourceFile;
import org.openrewrite.java.internal.JavaTypeCache;
import org.openrewrite.java.tree.JavaType.FullyQualified;

public record SourceSetParsingResult(List<SourceFile> sourceFiles, List<FullyQualified> classpath, JavaTypeCache typeCache) {
}
