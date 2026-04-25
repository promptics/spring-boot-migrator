package org.springframework.rewrite.parser.maven;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.utils.ResourceUtil;

class MavenModelReader {
   public Model readModel(Resource mavenPomFile) {
      try {
         return new MavenXpp3Reader().read(ResourceUtil.getInputStream(mavenPomFile));
      } catch (XmlPullParserException | IOException var4) {
         Path path = ResourceUtil.getPath(mavenPomFile);
         throw new RuntimeException("Could not read Maven model from resource '%s'".formatted(path.toString()), var4);
      }
   }
}
