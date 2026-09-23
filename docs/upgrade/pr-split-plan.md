# OpenRewrite 8.80.1 Upgrade — PR Split Plan

Working document. Describes how the OpenRewrite 8.13.4 → 8.80.1 upgrade can be
split into independently reviewable pull requests, and records what was
verified about each split.

Related: issues #5 (upgrade tracker), #6, #7, #11, #13.

## Why split

The upgrade currently exists as a stack of branches in which a single change
set bumps OpenRewrite and re-activates eight modules at once. That is hard to
review and hard to bisect. The commits inside it are already organised in
per-module blocks, so the split follows lines that already exist.

## Module activation order

The upgrade proceeds one module at a time. Each module is re-activated in the
root `pom.xml` only once its tests pass. This order is the one the work
actually followed:

```
sbm-openrewrite   ┐ always active — OR API adaptation lives here
sbm-core          ┘
sbm-support-boot
sbm-support-jee
sbm-support-weblogic
sbm-recipes-jee-to-boot
sbm-recipes-spring-cloud
sbm-recipes-boot-upgrade
sbm-recipes-spring-framework
sbm-recipes-mule-to-boot
jaxrs-recipes
```

## Proposed PRs

Eleven PRs: one baseline, nine module activations, one trailing cleanup.
Each stacks on the previous.

| # | PR | Scope |
|---|----|-------|
| 1 | OR 8.80.1 baseline | Version bump, downstream modules deactivated, all `sbm-openrewrite` / `sbm-core` API adaptation, test-infra changes, cross-module tests deferred to #13 |
| 2 | Activate `sbm-support-boot` | Multi-module compiler-plugin fixtures, `freemarker.template.Configuration` default bean, sibling-module classpath skip |
| 3 | Activate `sbm-support-jee` | Re-activate, ArchUnit `ExecutionContext` exception-list alignment |
| 4 | Activate `sbm-support-weblogic` | `@Transactional` fixture literal, re-activate |
| 5 | Activate `sbm-recipes-jee-to-boot` | OR API call sites, `ExcludeDependency` applied individually, `Module.search` resource wrapping, fixture drift |
| 6 | Activate `sbm-recipes-spring-cloud` | Defensive `ClasspathDependencies` lookup, `target/`-rooted fixture scanning |
| 7 | Activate `sbm-recipes-boot-upgrade` | `MethodMatcher` accessors, `DatabaseDriverGaeFinder` classpath scan, fixture migration to `TestProjectContext` |
| 8 | Activate `sbm-recipes-spring-framework` | Dependency version pins, relocated launcher imports, `Scope.Compile` classpath |
| 9 | Activate `sbm-recipes-mule-to-boot` | Quark-wrapped `.raml` handling, dynamically-added sources missing `ClasspathDependencies`, fixture drift |
| 10 | Activate `jaxrs-recipes` | Parent-pom inheritance (drops a conflicting BOM import), ctor arities, `SbmAdapterRecipe` input/output partitioning |
| 11 | Cleanup | Re-enable tests that pass on 8.80.1, refine remaining `@Disabled` rationales |

PR 1 is the only large one. Every downstream module depends on the OpenRewrite
API surface adapted there, so it cannot be made smaller without leaving the
reactor uncompilable.

### Smaller variants

If eleven is too many, the natural collapses are:

- Merge PRs 3 and 4 into PR 2 — both are two-commit changes with no
  cross-module dependencies. Gives nine PRs.
- Additionally fold PR 11 into PR 1 — the re-enabled tests belong to fixtures
  PR 1 already touches. Gives eight PRs.

## Build verification

PR 1's contents were cherry-picked onto the branch point and compiled to check
the claim that the baseline builds on its own.

```
mvn -DskipTests -fae -Dspring-javaformat.skip=true clean test-compile
```

Result: `BUILD SUCCESS` across the four active modules
(`spring-boot-migrator`, `test-helper`, `sbm-openrewrite`, `sbm-core`,
`recipe-test-support`).

### Finding: the Lombok bump must travel with PR 1

Lombok 1.18.24 → 1.18.34 is currently bundled into a commit in the
`sbm-support-boot` block, where its own message describes it as a side effect.
It is not optional and not local to that module: without it,
`sbm-openrewrite` fails to compile under JDK 21 with

```
java.lang.NoSuchFieldError: Class com.sun.tools.javac.tree.JCTree$JCImport
does not have member field 'com.sun.tools.javac.tree.JCTree qualid'
```

even though `sbm-support-boot` is deactivated. The bump has to be split out
and placed in PR 1.

### What was not verified

- `mvn test` was not run for the split — only `test-compile`. Compiling
  cleanly does not imply the tests pass.
- PRs 2-11 were not individually cherry-picked and compiled. Each carries the
  same class of risk PR 1 did: a cross-cutting `sbm-core` change that an
  earlier module silently depends on. The check below should be repeated per
  PR before it is opened.

## Verification procedure

For each PR in the split:

```bash
git checkout -b verify-prN <base>
git cherry-pick <range>

# fast smoke test (~15s on the four-module reactor)
mvn -DskipTests -Dspring-javaformat.skip=true clean test-compile

# full signal
mvn -Dspring-javaformat.skip=true test
```

Some tests depend on the host Git configuration. `gpg.format=ssh` in a
user's `.gitconfig` is rejected by JGit and fails
`GitSupportTest#addAllAndCommit`,
`PreconditionVerifierIntegrationTest#allChecksSucceed` and
`MigrateToSpringCloudConfigServerIntegrationTest#recipeTest`. These are
environmental, not regressions. Either run on a host without that setting or
exclude them:

```bash
mvn -Dspring-javaformat.skip=true \
    -Dtest='!GitSupportTest#addAllAndCommit,!PreconditionVerifierIntegrationTest#allChecksSucceed,!MigrateToSpringCloudConfigServerIntegrationTest#recipeTest' \
    -Dsurefire.failIfNoSpecifiedTests=false \
    test
```

## Running CI steps locally

The GitHub Actions workflows are thin Maven wrappers and can be reproduced
without a runner.

```bash
# .github/workflows/build-sbm-legacy.yml
./mvnw --batch-mode clean package

# .github/workflows/build-sbm-support-rewrite.yml
( cd sbm-support-rewrite && mvn --batch-mode clean package )

# .github/workflows/check-license-headers.yml
mvn license:check
```

The per-module walk used by the revamp workflow mirrors the activation order
above and is the closest analogue to the split:

```bash
mvn --batch-mode -Dspring-javaformat.skip=true clean
for m in spring-boot-migrator test-helper sbm-openrewrite; do
  mvn --batch-mode -Dspring-javaformat.skip=true install --projects ":$m"
done
for m in sbm-core recipe-test-support sbm-support-boot sbm-support-jee \
         sbm-support-weblogic sbm-recipes-jee-to-boot sbm-recipes-spring-cloud \
         sbm-recipes-boot-upgrade sbm-recipes-spring-framework \
         sbm-recipes-mule-to-boot jaxrs-recipes; do
  mvn --batch-mode -DskipTests -Dspring-javaformat.skip=true install --projects ":$m"
done
```

### The `build-sbm-legacy.yml` workflow does not currently run

Every run of this workflow has failed, on every branch, for months — 44 of 44
at the time of writing, including commits that only touch documentation. The
runs complete in 0-25 seconds, where a full reactor build takes roughly 26
minutes, so none of them reach Maven at all.

The failure is in the `actions/setup-java@v2` step:

```
##[error]Cache service responded with 400
```

`setup-java@v2` is deprecated, targets Node 20, and is being forced onto
Node 24 by the runner. Its `cache: maven` integration no longer negotiates
successfully with the Actions cache service. `actions/checkout@v3` in the same
workflow is deprecated for the same reason.

Three separate things need fixing before the split PRs can rely on CI:

- Upgrade the deprecated actions, so the workflow reaches the build step at
  all. This is independent of the upgrade work and affects every branch in the
  repository.
- Stop two tests in `sbm-recipes-boot-upgrade` resolving a dependency over the
  network (see below).
- Adjust the trigger. The workflow builds the full reactor on pushes to any
  branch. While the split is in progress the reactor is deliberately pruned,
  so it would fail on the intermediate branches even once the other two are
  fixed.

### What the build actually does once it runs

The first item was applied on a branch to find out
(`actions/checkout@v3` → `@v7`, `actions/setup-java@v2` → `@v6`). `Setup Java`
then passes in about seven seconds and Maven runs for the first time. The
result, on `main` at OpenRewrite 7.35.0:

```
spring-boot-migrator ............ SUCCESS [  4.417 s]
test-helper .................... SUCCESS [ 15.027 s]
sbm-openrewrite ................ SUCCESS [01:46 min]
sbm-utils ...................... SUCCESS [  0.117 s]
sbm-core ....................... SUCCESS [02:14 min]
recipe-test-support ............ SUCCESS [  0.682 s]
sbm-support-boot ............... SUCCESS [ 54.925 s]
sbm-recipes-spring-framework ... SUCCESS [ 13.999 s]
sbm-support-jee ................ SUCCESS [ 16.597 s]
sbm-recipes-jee-to-boot ........ SUCCESS [01:50 min]
sbm-recipes-mule-to-boot ....... SUCCESS [01:46 min]
sbm-recipes-spring-cloud ....... SUCCESS [ 24.682 s]
openrewrite-spring-recipes ..... SUCCESS [ 12.297 s]
sbm-support-weblogic ........... SUCCESS [ 12.511 s]
sbm-recipes-boot-upgrade ....... FAILURE [03:18 min]
spring-shell ................... SKIPPED
spring-boot-upgrade ............ SKIPPED
```

Fourteen of sixteen modules pass. The failing module fails on two tests out
of 238, both in `ApacheSolrRepositoryBeanFinderTest`:

```
UncheckedIO Failed to parse pom
  at org.openrewrite.maven.internal.RawPom.parse
  at org.openrewrite.maven.internal.MavenPomDownloader.download
  at ResolvedPom$Resolver.resolveParentPropertiesAndRepositoriesRecursively
  ...
  at com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser._nextToken
```

Both tests call
`withBuildFileHavingDependencies("org.springframework.data:spring-data-solr:4.3.15")`,
which makes OpenRewrite resolve that artifact's parent-pom chain over the
network during a unit test. The artifact is not missing — its pom returns
HTTP 200. What comes back for some request in the parent chain is not a pom at
all. The underlying parser error names the content:

```
com.fasterxml.jackson.core.JsonParseException:
  Unexpected close tag </head>; expected </link>.  at [row,col]: [20,6]
```

`</head>` and `</link>` mean OpenRewrite was handed an **HTML document** and
asked to deserialise it as a pom. A direct request for the same artifact from
a different network returned a plain-text notice rather than HTML:

```
Your ip has exceeded rate limits. Find out more here https://central.sonatype.org/faq/429-error/
```

Both are Maven Central declining to serve the pom, in different formats for
different clients. The common factor is that `RawPom.parse` has no guard
against a non-XML response and fails deep inside Jackson rather than
reporting a refused download.

This reproduces identically on re-run, and on two separate branches.
GitHub-hosted runners share outbound IP ranges and a full reactor build makes
many requests to Central, so this is the normal condition there rather than an
occasional one. These two tests should be expected to fail consistently in CI
until they stop resolving over the network — a local fixture pom, or dropping
the live dependency if the finder under test only needs the type on the
classpath.

### Outcome

With the actions upgraded and the two Solr tests no longer resolving over the
network, the workflow reaches `BUILD SUCCESS` for the first time:

```
spring-boot-migrator ............ SUCCESS [  4.016 s]
test-helper .................... SUCCESS [ 17.624 s]
sbm-openrewrite ................ SUCCESS [01:42 min]
sbm-utils ...................... SUCCESS [  0.139 s]
sbm-core ....................... SUCCESS [02:23 min]
recipe-test-support ............ SUCCESS [  0.724 s]
sbm-support-boot ............... SUCCESS [ 57.830 s]
sbm-recipes-spring-framework ... SUCCESS [ 13.747 s]
sbm-support-jee ................ SUCCESS [ 16.130 s]
sbm-recipes-jee-to-boot ........ SUCCESS [01:56 min]
sbm-recipes-mule-to-boot ....... SUCCESS [01:52 min]
sbm-recipes-spring-cloud ....... SUCCESS [ 26.431 s]
openrewrite-spring-recipes ..... SUCCESS [ 12.216 s]
sbm-support-weblogic ........... SUCCESS [ 11.975 s]
sbm-recipes-boot-upgrade ....... SUCCESS [03:22 min]
spring-shell ................... SUCCESS [ 19.412 s]
spring-boot-upgrade ............ SUCCESS [  6.871 s]

BUILD SUCCESS — 14:26 min
```

Seventeen modules, not the fifteen seen earlier: `spring-shell` and
`spring-boot-upgrade` had been `SKIPPED` in every run because the reactor
aborted at `sbm-recipes-boot-upgrade` before reaching them. Both pass.

The Maven cache also works again — the post-job step stores `~/.m2` and the
JDK, which `setup-java@v2` could not do. Later runs should be faster than
14:26.

### Consequences for this plan

- The baseline is better than the branch history suggests. The whole reactor
  is green on `main` at OpenRewrite 7.35.0 before any upgrade work begins.
- The split assumed CI would validate each step. Until now it could not have:
  every check was red regardless of content.
- Each of the eleven PRs can now get a real verdict, so the per-PR
  verification in this document is worth running.

### A wider fragility, not addressed

Running `sbm-recipes-boot-upgrade` on a network with degraded access to Maven
Central produces **13** errors rather than two — `PagingAndSortingHelperTest`,
`CommonsMultipartResolverHelperTest`, `ConstructorBindingReportSectionTest`,
`ChangeJavaxPackagesToJakartaTest`, `UpgradeBomTo30Test` and
`Boot_24_25_UpdateDependenciesRecipeTest` all fail the same way. All of them
pass on a hosted runner.

So the live-Central dependency is not specific to the Solr tests; it runs
through this module's unit suite, and Solr was only the case that tipped over
first under CI's conditions. The suite is green but not hermetic, and that
will resurface whenever Central throttles harder or a runner's network is
slower.

## Open items

- [ ] Split the Lombok bump out of the `sbm-support-boot` commit into PR 1.
- [ ] Repeat the cherry-pick + `test-compile` check for PRs 2-11.
- [ ] Decide between the eleven-, nine- and eight-PR variants.
- [x] Upgrade the deprecated actions in `build-sbm-legacy.yml` and
      `build-sbm-support-rewrite.yml` so the workflow reaches its build step.
- [x] Stop `ApacheSolrRepositoryBeanFinderTest` resolving
      `spring-data-solr:4.3.15` over the network, so the one failing module
      can pass.
- [ ] Adjust the `build-sbm-legacy.yml` trigger so intermediate branches do
      not fail on the pruned reactor.
- [ ] Decide whether the remaining live-Central dependencies in
      `sbm-recipes-boot-upgrade`'s tests are worth removing, given the suite
      passes on a hosted runner but not on a slower or throttled network.
- [ ] Confirm which companion changes in the parser launcher have to land
      before PR 1 and PR 7 (see issue #7).
