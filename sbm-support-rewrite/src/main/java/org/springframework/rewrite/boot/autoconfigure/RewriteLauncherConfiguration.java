package org.springframework.rewrite.boot.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Stub of the upstream RewriteLauncherConfiguration. The original {@code RewriteRecipeLauncher}
 * bean depends on a Gradle plugin invoker that is not available in this internal copy.
 * SBM uses this class only as a Spring config marker (via {@code @Import}); imports of
 * its dependent configurations are kept so {@code @SpringBootTest(classes = {...})} continues
 * to wire the parser and resource-set beans.
 */
@AutoConfiguration(
   after = {ProjectResourceSetConfiguration.class, RecipeDiscoveryConfiguration.class}
)
@Import({ProjectResourceSetConfiguration.class, RecipeDiscoveryConfiguration.class})
public class RewriteLauncherConfiguration {
}
