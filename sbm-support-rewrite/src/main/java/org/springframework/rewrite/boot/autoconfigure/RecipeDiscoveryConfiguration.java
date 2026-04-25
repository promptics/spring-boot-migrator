package org.springframework.rewrite.boot.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.rewrite.RewriteRecipeDiscovery;
import org.springframework.rewrite.parser.RewriteParserConfiguration;
import org.springframework.rewrite.parser.SpringRewriteProperties;

@AutoConfiguration(
   after = {RewriteParserConfiguration.class}
)
@Import({RewriteParserConfiguration.class})
@EnableConfigurationProperties({SpringRewriteProperties.class})
public class RecipeDiscoveryConfiguration {
   @Bean
   RewriteRecipeDiscovery rewriteRecipeDiscovery(SpringRewriteProperties springRewriteProperties) {
      return new RewriteRecipeDiscovery(springRewriteProperties);
   }
}
