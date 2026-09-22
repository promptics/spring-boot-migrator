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

Note that `build-sbm-legacy.yml` triggers on pushes to any branch and builds
the full reactor. While the split is in progress the reactor is deliberately
pruned, so that workflow will fail on the intermediate branches. Its trigger
needs adjusting before the split PRs are opened.

## Open items

- [ ] Split the Lombok bump out of the `sbm-support-boot` commit into PR 1.
- [ ] Repeat the cherry-pick + `test-compile` check for PRs 2-11.
- [ ] Decide between the eleven-, nine- and eight-PR variants.
- [ ] Adjust the `build-sbm-legacy.yml` trigger so intermediate branches do
      not fail on the pruned reactor.
- [ ] Confirm which companion changes in the parser launcher have to land
      before PR 1 and PR 7 (see issue #7).
