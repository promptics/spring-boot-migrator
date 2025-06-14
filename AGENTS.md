# AGENTS

This repository is a multi-module Maven project for Spring Boot Migrator (SBM).
Use these guidelines when making changes in this repository.

## Module Overview
The top-level `pom.xml` lists the main Maven modules:

```
applications/spring-shell
applications/spring-boot-upgrade
components/openrewrite-spring-recipes
components/sbm-recipes-boot-upgrade
components/sbm-recipes-spring-cloud
components/sbm-recipes-spring-framework
components/sbm-recipes-jee-to-boot
components/sbm-recipes-mule-to-boot
components/sbm-support-boot
components/sbm-support-weblogic
components/sbm-support-jee
components/sbm-openrewrite
components/sbm-core
components/test-helper
components/recipe-test-support
components/sbm-utils
```

Additional standalone projects include:

- `sbm-support-rewrite`
- `sbm-gradle-tooling-model` (Gradle `model`, `plugin` and `parser` submodules)
- Eclipse plug-in under `ide-integrations`.

See the repository README for more details on each module.

## Build and Test
- Requires **JDK 17** and Maven.
- To build the entire project and run tests use:
  ```bash
  mvn clean install
  ```
- Integration tests need Docker and a configured `M2_HOME` environment variable.
  You can skip them with `-DskipITs`.
- To skip all tests use `-DskipTests`.

## Coding Conventions
- Each new `.java` file must include the Apache License header and a Javadoc
  class comment with an `@author` tag.
- Follow the commit message guidelines from
  [CONTRIBUTING.adoc](CONTRIBUTING.adoc).

## Pull Requests
- Ensure the project builds (`mvn clean install`) before opening a PR.
- Provide unit tests for new functionality when possible.

