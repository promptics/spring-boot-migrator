# AGENTS

This repository is a multi-module Maven project for Spring Boot Migrator (SBM).
Use these guidelines when making changes in this repository.

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

