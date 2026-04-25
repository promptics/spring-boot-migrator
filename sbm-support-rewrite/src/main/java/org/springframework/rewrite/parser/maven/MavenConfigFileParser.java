package org.springframework.rewrite.parser.maven;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.maven.cli.CleanArgument;
import org.jetbrains.annotations.NotNull;

class MavenConfigFileParser {
   private static final char ALTERNATE_POM_FILE = 'f';
   private static final char BATCH_MODE = 'B';
   private static final char SET_USER_PROPERTY = 'D';
   private static final char OFFLINE = 'o';
   private static final char QUIET = 'q';
   private static final char DEBUG = 'X';
   private static final char ERRORS = 'e';
   private static final char HELP = 'h';
   private static final char VERSION = 'v';
   private static final char SHOW_VERSION = 'V';
   private static final char NON_RECURSIVE = 'N';
   private static final char UPDATE_SNAPSHOTS = 'U';
   private static final char ACTIVATE_PROFILES = 'P';
   private static final String SUPRESS_SNAPSHOT_UPDATES = "nsu";
   private static final char CHECKSUM_FAILURE_POLICY = 'C';
   private static final char CHECKSUM_WARNING_POLICY = 'c';
   private static final char ALTERNATE_USER_SETTINGS = 's';
   private static final String ALTERNATE_GLOBAL_SETTINGS = "gs";
   private static final char ALTERNATE_USER_TOOLCHAINS = 't';
   private static final String ALTERNATE_GLOBAL_TOOLCHAINS = "gt";
   private static final String FAIL_FAST = "ff";
   private static final String FAIL_AT_END = "fae";
   private static final String FAIL_NEVER = "fn";
   private static final String RESUME_FROM = "rf";
   private static final String PROJECT_LIST = "pl";
   private static final String ALSO_MAKE = "am";
   private static final String ALSO_MAKE_DEPENDENTS = "amd";
   private static final String LOG_FILE = "l";
   private static final String ENCRYPT_MASTER_PASSWORD = "emp";
   private static final String ENCRYPT_PASSWORD = "ep";
   private static final String THREADS = "T";
   private static final String BUILDER = "b";
   private static final String NO_TRANSFER_PROGRESS = "ntp";
   private static final String COLOR = "color";
   private static final String MVN_MAVEN_CONFIG = ".mvn/maven.config";

   public List<String> getActivatedProfiles(Path baseDir) {
      File configFile = baseDir.resolve(".mvn/maven.config").toFile();
      if (configFile.isFile()) {
         try {
            List var5;
            try (Stream<String> lines = Files.lines(configFile.toPath(), Charset.defaultCharset())) {
               String[] args = readFile(lines);
               var5 = this.parse(args)
                  .stream()
                  .filter(o -> String.valueOf('P').equals(o.getOpt()))
                  .<String>map(Option::getValue)
                  .map(v -> v.split(","))
                  .flatMap(Arrays::stream)
                  .map(String::trim)
                  .toList();
            }

            return var5;
         } catch (IOException var8) {
            throw new RuntimeException(var8);
         }
      } else {
         return List.of();
      }
   }

   public Map<String, String> getUserProperties(Path baseDir) {
      File configFile = baseDir.resolve(".mvn/maven.config").toFile();
      if (configFile.isFile()) {
         try {
            Map var5;
            try (Stream<String> lines = Files.lines(configFile.toPath(), Charset.defaultCharset())) {
               String[] args = readFile(lines);
               var5 = this.parse(args)
                  .stream()
                  .filter(o -> String.valueOf('D').equals(o.getOpt()))
                  .<String>map(Option::getValue)
                  .filter(v -> v.contains("="))
                  .map(v -> v.split("="))
                  .collect(Collectors.toMap(a -> (String)a[0], a -> (String)a[1]));
            }

            return var5;
         } catch (IOException var8) {
            throw new RuntimeException(var8);
         }
      } else {
         return Map.of();
      }
   }

   @NotNull
   private static String[] readFile(Stream<String> lines) {
      return lines.filter(arg -> !arg.isEmpty() && !arg.startsWith("#")).toArray(String[]::new);
   }

   public List<Option> parse(String[] args) {
      Options options = new Options();
      options.addOption(Option.builder(Character.toString('h')).longOpt("help").desc("Display help information").build());
      options.addOption(
         Option.builder(Character.toString('f')).longOpt("file").hasArg().desc("Force the use of an alternate POM file (or directory with pom.xml)").build()
      );
      options.addOption(Option.builder(Character.toString('D')).longOpt("define").hasArg().desc("Define a user property").build());
      options.addOption(Option.builder(Character.toString('o')).longOpt("offline").desc("Work offline").build());
      options.addOption(Option.builder(Character.toString('v')).longOpt("version").desc("Display version information").build());
      options.addOption(Option.builder(Character.toString('q')).longOpt("quiet").desc("Quiet output - only show errors").build());
      options.addOption(Option.builder(Character.toString('X')).longOpt("debug").desc("Produce execution debug output").build());
      options.addOption(Option.builder(Character.toString('e')).longOpt("errors").desc("Produce execution error messages").build());
      options.addOption(Option.builder(Character.toString('N')).longOpt("non-recursive").desc("Do not recurse into sub-projects").build());
      options.addOption(
         Option.builder(Character.toString('U'))
            .longOpt("update-snapshots")
            .desc("Forces a check for missing releases and updated snapshots on remote repositories")
            .build()
      );
      options.addOption(
         Option.builder(Character.toString('P')).longOpt("activate-profiles").desc("Comma-delimited list of profiles to activate").hasArg().build()
      );
      options.addOption(
         Option.builder(Character.toString('B')).longOpt("batch-mode").desc("Run in non-interactive (batch) mode (disables output color)").build()
      );
      options.addOption(Option.builder("nsu").longOpt("no-snapshot-updates").desc("Suppress SNAPSHOT updates").build());
      options.addOption(Option.builder(Character.toString('C')).longOpt("strict-checksums").desc("Fail the build if checksums don't match").build());
      options.addOption(Option.builder(Character.toString('c')).longOpt("lax-checksums").desc("Warn if checksums don't match").build());
      options.addOption(Option.builder(Character.toString('s')).longOpt("settings").desc("Alternate path for the user settings file").hasArg().build());
      options.addOption(Option.builder("gs").longOpt("global-settings").desc("Alternate path for the global settings file").hasArg().build());
      options.addOption(Option.builder(Character.toString('t')).longOpt("toolchains").desc("Alternate path for the user toolchains file").hasArg().build());
      options.addOption(Option.builder("gt").longOpt("global-toolchains").desc("Alternate path for the global toolchains file").hasArg().build());
      options.addOption(Option.builder("ff").longOpt("fail-fast").desc("Stop at first failure in reactorized builds").build());
      options.addOption(Option.builder("fae").longOpt("fail-at-end").desc("Only fail the build afterwards; allow all non-impacted builds to continue").build());
      options.addOption(Option.builder("fn").longOpt("fail-never").desc("NEVER fail the build, regardless of project result").build());
      options.addOption(Option.builder("rf").longOpt("resume-from").hasArg().desc("Resume reactor from specified project").build());
      options.addOption(
         Option.builder("pl")
            .longOpt("projects")
            .desc(
               "Comma-delimited list of specified reactor projects to build instead of all projects. A project can be specified by [groupId]:artifactId or by its relative path"
            )
            .hasArg()
            .build()
      );
      options.addOption(Option.builder("am").longOpt("also-make").desc("If project list is specified, also build projects required by the list").build());
      options.addOption(
         Option.builder("amd")
            .longOpt("also-make-dependents")
            .desc("If project list is specified, also build projects that depend on projects on the list")
            .build()
      );
      options.addOption(Option.builder("l").longOpt("log-file").hasArg().desc("Log file where all build output will go (disables output color)").build());
      options.addOption(Option.builder(Character.toString('V')).longOpt("show-version").desc("Display version information WITHOUT stopping build").build());
      options.addOption(Option.builder("emp").longOpt("encrypt-master-password").hasArg().optionalArg(true).desc("Encrypt master security password").build());
      options.addOption(Option.builder("ep").longOpt("encrypt-password").hasArg().optionalArg(true).desc("Encrypt server password").build());
      options.addOption(
         Option.builder("T").longOpt("threads").hasArg().desc("Thread count, for instance 4 (int) or 2C/2.5C (int/float) where C is core multiplied").build()
      );
      options.addOption(Option.builder("b").longOpt("builder").hasArg().desc("The id of the build strategy to use").build());
      options.addOption(Option.builder("ntp").longOpt("no-transfer-progress").desc("Do not display transfer progress when downloading or uploading").build());
      options.addOption(Option.builder("npr").longOpt("no-plugin-registry").desc("Ineffective, only kept for backward compatibility").build());
      options.addOption(Option.builder("cpu").longOpt("check-plugin-updates").desc("Ineffective, only kept for backward compatibility").build());
      options.addOption(Option.builder("up").longOpt("update-plugins").desc("Ineffective, only kept for backward compatibility").build());
      options.addOption(Option.builder("npu").longOpt("no-plugin-updates").desc("Ineffective, only kept for backward compatibility").build());
      options.addOption(
         Option.builder("llr").longOpt("legacy-local-repository").desc("UNSUPPORTED: Use of this option will make Maven invocation fail.").build()
      );
      options.addOption(
         Option.builder()
            .longOpt("color")
            .hasArg()
            .optionalArg(true)
            .desc("Defines the color mode of the output. Supported are 'auto', 'always', 'never'.")
            .build()
      );
      String[] cleanArgs = CleanArgument.cleanArgs(args);
      CommandLineParser parser = new GnuParser();

      try {
         CommandLine parsedOptions = parser.parse(options, cleanArgs);
         return Arrays.asList(parsedOptions.getOptions());
      } catch (ParseException var6) {
         throw new RuntimeException(var6);
      }
   }
}
