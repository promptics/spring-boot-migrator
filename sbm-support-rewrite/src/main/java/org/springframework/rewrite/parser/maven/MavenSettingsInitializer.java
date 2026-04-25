package org.springframework.rewrite.parser.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.maven.MavenSettings.ActiveProfiles;
import org.openrewrite.maven.MavenSettings.Mirrors;
import org.openrewrite.maven.MavenSettings.Profile;
import org.openrewrite.maven.MavenSettings.Profiles;
import org.openrewrite.maven.MavenSettings.Servers;
import org.openrewrite.maven.internal.RawRepositories;
import org.openrewrite.maven.tree.MavenRepository;
import org.springframework.rewrite.scopes.ProjectMetadata;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;
import org.springframework.stereotype.Component;

@Component
public class MavenSettingsInitializer {
   private final ExecutionContext executionContext;
   private final ProjectMetadata projectMetadata;
   private final MavenPasswordDecrypter passwordDecrypter;

   public MavenSettingsInitializer(ExecutionContext executionContext, ProjectMetadata projectMetadata) {
      this.executionContext = executionContext;
      this.projectMetadata = projectMetadata;
      this.passwordDecrypter = new MavenPasswordDecrypter();
   }

   public void initializeMavenSettings() {
      Path userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
      String m2RepoPath = userHome.resolve(".m2/repository").toAbsolutePath().normalize() + "/";
      String unifiedM2RepoPath = LinuxWindowsPathUnifier.unifiedPathString(m2RepoPath);
      String repo = "file://" + unifiedM2RepoPath;
      Path mavenSettingsFile = userHome.resolve(".m2/settings.xml");
      Path mavenSecuritySettingsFile = userHome.resolve(".m2/settings-security.xml");
      MavenRepository mavenRepository = new MavenRepository("local", repo, null, null, true, null, null, null, null);
      Profile defaultProfile = new Profile("default", null, new RawRepositories());
      Profiles profiles = new Profiles(List.of(defaultProfile));
      ActiveProfiles activeProfiles = new ActiveProfiles(List.of("default"));
      Mirrors mirrors = new Mirrors();
      Servers servers = new Servers();
      // OR 8.80: MavenSettings(String, MavenRepository, Profiles, ActiveProfiles, Mirrors, Servers, Proxies)
      MavenSettings mavenSettings = new MavenSettings(repo, mavenRepository, profiles, activeProfiles, mirrors, servers, null);
      MavenExecutionContextView mavenExecutionContextView = MavenExecutionContextView.view(this.executionContext);
      if (Files.exists(mavenSettingsFile)) {
         mavenSettings = mavenSettings.merge(MavenSettings.parse(mavenSettingsFile, mavenExecutionContextView));
         if (mavenSecuritySettingsFile.toFile().exists()) {
            this.passwordDecrypter.decryptMavenServerPasswords(mavenSettings, mavenSecuritySettingsFile);
         }
      }

      mavenExecutionContextView.setMavenSettings(mavenSettings, new String[0]);
      this.projectMetadata.setMavenSettings(mavenSettings);
   }
}
