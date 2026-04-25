package org.springframework.rewrite.boot.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.rewrite.parser.RewriteParserConfiguration;

@Deprecated(
   forRemoval = true
)
@AutoConfiguration
@Import({RecipeDiscoveryConfiguration.class, RewriteParserConfiguration.class, ProjectResourceSetConfiguration.class})
public class SpringRewriteCommonsConfiguration {
}
