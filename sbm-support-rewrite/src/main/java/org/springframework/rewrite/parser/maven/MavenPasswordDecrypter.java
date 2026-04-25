package org.springframework.rewrite.parser.maven;

import java.nio.file.Path;
import java.util.List;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.maven.MavenSettings.Server;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipherException;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

class MavenPasswordDecrypter {
   private final SecDispatcher secDispatcher;

   public MavenPasswordDecrypter() {
      try {
         this.secDispatcher = new DefaultSecDispatcher(new DefaultPlexusCipher());
      } catch (Exception var2) {
         throw new RuntimeException(var2);
      }
   }

   public void decryptMavenServerPasswords(MavenSettings mavenSettings, Path mavenSecuritySettingsFile) {
      System.setProperty("settings.security", mavenSecuritySettingsFile.toString());
      if (mavenSettings.getServers() != null && mavenSettings.getServers().getServers() != null) {
         List<Server> servers = mavenSettings.getServers().getServers();

         for (int i = 0; i < servers.size(); i++) {
            Server server = servers.get(i);
            if (server.getPassword() != null) {
               Server serverWithDecodedPw = this.decryptPassword(server);
               servers.set(i, serverWithDecodedPw);
            }
         }
      }
   }

   private Server decryptPassword(Server server) {
      try {
         String decryptionResult = this.secDispatcher.decrypt(server.getPassword());
         return server.withPassword(decryptionResult);
      } catch (SecDispatcherException var3) {
         throw new RuntimeException(var3);
      }
   }
}
