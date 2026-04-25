package org.springframework.rewrite.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.style.Autodetect;
import org.openrewrite.java.style.Autodetect.Detector;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.Marker;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.xml.tree.Xml.Document;

public class StyleDetector {
   public List<SourceFile> sourcesWithAutoDetectedStyles(Stream<SourceFile> sourceFiles) {
      Detector javaDetector = Autodetect.detector();
      org.openrewrite.xml.style.Autodetect.Detector xmlDetector = org.openrewrite.xml.style.Autodetect.detector();
      List<SourceFile> sourceFileList = sourceFiles.peek(javaDetector::sample).peek(xmlDetector::sample).toList();
      Map<Class<? extends Tree>, NamedStyles> stylesByType = new HashMap<>();
      stylesByType.put(JavaSourceFile.class, javaDetector.build());
      stylesByType.put(Document.class, xmlDetector.build());
      return ListUtils.map(sourceFileList, this.applyAutodetectedStyle(stylesByType));
   }

   private UnaryOperator<SourceFile> applyAutodetectedStyle(Map<Class<? extends Tree>, NamedStyles> stylesByType) {
      return before -> {
         for (Entry<Class<? extends Tree>, NamedStyles> styleTypeEntry : stylesByType.entrySet()) {
            if (styleTypeEntry.getKey().isAssignableFrom(before.getClass())) {
               before = (SourceFile)before.withMarkers(before.getMarkers().add((Marker)styleTypeEntry.getValue()));
            }
         }

         return before;
      };
   }
}
