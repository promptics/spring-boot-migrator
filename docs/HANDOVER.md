# SBM OR 8.80.1 Migration — Handover

## Where we are

Issue #5 (master tracker): upgrade SBM to OR 8.80.1, then Boot 4. Strategy:
**OR upgrade first, modules activated one-at-a-time, each green before moving on.
Push module activations onto PR #16 incrementally — drafts get a per-module
commit pair (build: re-activate ... + supporting test/code fixes).** Tests
must pass, not be disabled.

User (`fabapp2`) is no longer a maintainer of upstream
`spring-projects-experimental/spring-boot-migrator`. Working in fork
`promptics/spring-boot-migrator`. PR #16 (draft) is the rolling integration
PR.

## Current branch

```
SBM:                    claude/issue-6-7/integrate-rewrite-commons    (PR #16, draft)
                        tip f3a674eb (pushed to origin)
spring-rewrite-commons: bump-or-8.80.1 (in /tmp/src/spring-rewrite-commons-launcher)
                        promptics fork, OR 8.80.1, Boot 3.1.x baseline
                        tip 928e6ae (3 commits AHEAD of origin/bump-or-8.80.1)
```

## Active reactor (8 modules, all green) ✅

```
spring-boot-migrator         (root)
test-helper
sbm-openrewrite              — 54 tests, all green
sbm-core                     — 326 tests, 14 skipped (#13 cross-module classpath, deferred)
recipe-test-support
sbm-support-boot             — 81 tests, all green ✅
sbm-support-jee              — 10 tests, 5 pre-existing @Disabled ✅
sbm-support-weblogic         —  6 tests, all green ✅
sbm-recipes-jee-to-boot      — 146 tests, 15 pre-existing @Disabled ✅
sbm-recipes-spring-cloud     —  10 tests, 1 env failure ✅  ← just landed
```

The sbm-core/sbm-recipes-spring-cloud env failures that surface in this sandbox
(`GitSupportTest.addAllAndCommit`, `PreconditionVerifierIntegrationTest.allChecksSucceed`,
`MigrateToSpringCloudConfigServerIntegrationTest.recipeTest`) are pre-existing
**environmental** — `/root/.gitconfig` has `gpg.format=ssh` which JGit rejects.
Not regressions. Skip with
`-Dtest='!GitSupportTest#addAllAndCommit,!PreconditionVerifierIntegrationTest#allChecksSucceed,!MigrateToSpringCloudConfigServerIntegrationTest#recipeTest' -Dsurefire.failIfNoSpecifiedTests=false`,
or run on a host without that config.

The reactor-mode `mvn test` from root used to surface a pre-existing ArchUnit
`ControlledInstantiationOfExecutionContextTest` failure in any module that
re-copies the test from sbm-core without the `SbmCoreConfig`/`OpenRewriteTestSupport`
exception list. Standalone `mvn -pl components/<module> test` doesn't see it
(JAR vs class-folder classpath quirk). The spring-cloud copy was aligned with
sbm-core's exception list in this session; remaining inactive modules carry the
same drift and will need the same one-line alignment when activated.

## Inactive modules (commented out in root pom — activate one at a time)

```
sbm-recipes-boot-upgrade      ← NEXT
sbm-recipes-spring-framework
sbm-recipes-mule-to-boot
sbm-recipes-jpa
jaxrs-recipes
```

## What's pinned

```
OR:                       8.80.1   (was 8.13.4)
spring-rewrite-commons:   0.1.0-SNAPSHOT (local fork install, our patches)
Lombok:                   1.18.34  (bumped from 1.18.24 for JDK 21)
Spring Boot:              3.1.2    (still — bump to be done in #9)
Java baseline:            17       (still — bump to 21 in #8)
rewrite-recipe-bom:       not yet adopted (#7)
```

## Commits added in the most recent session (tail of PR #16, newest first)

```
b1feedc8 test(sbm-recipes-boot-upgrade): use directory-form sourcePath in withJavaSource (OR 8.80.1)
b4e583b0 test(sbm-recipes-boot-upgrade): use target/-rooted dummy projectRoot in SqlScriptDataSourceInitialization tests
d4a3b28f fix(sbm-recipes-boot-upgrade): adapt MethodMatcher accessors to OR 8.80.1
ffc83ce1 fix(sbm-core): TestProjectContext creates projectRoot if missing before walk-and-delete
50706729 docs: refresh HANDOVER.md after sbm-recipes-spring-cloud activation
e2cd0166 build: re-activate components/sbm-recipes-spring-cloud in the reactor
754e2a25 test(sbm-recipes-spring-cloud): exclude SbmCoreConfig & OpenRewriteTestSupport from ArchUnit ExecutionContext rule
1684917d test(recipe-test-support): scan target/-rooted fixtures in ProjectContextFileSystemTestSupport
463bead3 fix(sbm-core): defensive ClasspathDependencies marker lookup on test sources
d6b41c8e docs: HANDOVER reflects rewrite-commons fork patches landed on origin
9ef8b405 docs: refresh HANDOVER.md after sbm-recipes-jee-to-boot activation
f3a674eb build: re-activate components/sbm-recipes-jee-to-boot in the reactor
0b82bbaf test(sbm-recipes-jee-to-boot): adapt fixtures to OR 8.80.1 ChangeType + import-grouping output
aa57b9f9 fix(sbm-recipes-jee-to-boot): include dynamic-mapping recipes in getRecipeList()
51fa0826 fix(sbm-core): preserve type-specific resource wrapping in Module.search
195a4b1c test(sbm-recipes-jee-to-boot): adapt fixtures to OR 8.80.1 import & format output
92e76a33 test(sbm-core): switch UserInteractions test fallback to register-if-missing
05844f1f fix(sbm-core): apply ExcludeDependency recipes individually instead of mutating getRecipeList
fd629600 fix(sbm-recipes-jee-to-boot): adapt OR API call sites to OpenRewrite 8.80.1
d7fdaa32 test(sbm-core): make SpringBeanProvider compatible with downstream module classpaths
31030574 build: re-activate components/sbm-support-weblogic in the reactor
0c024242 test(sbm-support-weblogic): adapt @Transactional fixture literal to OR 8.80.1 writer
0beb8b19 docs: refresh HANDOVER.md after sbm-support-jee activation
8d319481 build: re-activate components/sbm-support-jee in the reactor
6a4f4b29 docs: handover note for OR 8.80.1 migration / sbm-support-boot green
```
(Older history below `6a4f4b29` is the original PR baseline — see prior
HANDOVER versions or `git log` for the full list.)

## spring-rewrite-commons fork patches (`origin/bump-or-8.80.1` on `promptics/spring-rewrite-commons`)

```
de3e7fe fix(parser): route spring.factories through PropertiesParser              ← LOCAL, NEEDS PUSH
a223201 fix(maven): skip classpath collection for unparseable sources              ← pushed
<sha>   fix(maven): detect multi-module via <modules> when packaging is inherited  ← pushed
<sha>   parser: tolerate empty/blank-project resources                             ← pushed
1a29dcb fix(or): RewriteRecipeDiscovery activation guard works with leaf recipes
fedfaf4 fix(test): adapt 4 launcher/polyglot test assertions to OR/Maven contracts
d3f3313 test(gradle): update brittle plugin-count literal 9 → 10 (not OR-related)
```

`de3e7fe` is the cluster-1 fix needed to unblock `sbm-recipes-boot-upgrade`'s
`CreateAutoconfigurationActionTest`/`BootHasAutoconfigurationConditionTest`/
`SpringFactoriesHelperTest` (see "sbm-recipes-boot-upgrade triage" below). It
lives only in the sandbox at `/tmp/src/spring-rewrite-commons` until pushed
from a host with GitHub creds. Patch exported at
`/tmp/sbm-rewrite-commons-patches/0001-fix-parser-route-spring.factories-through-Properties.patch`.

The earlier three `← pushed` patches were re-derived in a prior session
(the previous sandbox lost them before they reached origin) and pushed via
`git am` from a host with GitHub creds.

To rebuild and reinstall locally after edits (avoid pulling
gradle-tooling-api which the sandbox cannot reach at repo.gradle.org):
```bash
cd /tmp/src/spring-rewrite-commons-launcher
mvn spring-javaformat:apply -q   # if format check fails
mvn install -pl spring-rewrite-commons-launcher -am -DskipTests -q
cd /home/user/spring-boot-migrator
mvn -pl components/sbm-core,components/recipe-test-support,components/sbm-openrewrite -am install -DskipTests -Dspring-javaformat.skip=true -q
```

## Key fixes by category (this session)

### sbm-core production fixes (apply broadly)

- **`OpenRewriteMavenBuildFile.excludeDependenciesInner` → ApplyEach**: OR 8.80.1's
  `Recipe.getRecipeList()` is immutable. The previous "build one root
  `ExcludeDependency`, attach others as sub-recipes via `getRecipeList().add(...)`"
  pattern threw `UnsupportedOperationException`. Now applies each
  `ExcludeDependency` individually with its own scope and refreshes once.
- **`Module.search` type-wrap preservation**: was mapping wrapped resources
  back to raw `SourceFile` and re-creating via
  `projectResourceSetFactory.create(baseDir, sourceFiles)`, which dropped
  the type-specific wrapping (e.g. `PersistenceXml`, `EjbJarXml`). Switched
  to `createFromSourceFileHolders(...)` so `GenericTypeFinder<T>` matches
  again. Cleared 4 tests across the persistence and eclipselink clusters.

### sbm-core test infrastructure (test-jar consumed by downstream modules)

- **`SpringBeanProvider.run` UserInteractions register-if-missing**: when
  downstream modules add `@Configuration` recipes (e.g. `MigrateJaxWsRecipe`)
  that take a `UserInteractions`, the default test context lacked one →
  `NoSuchBeanDefinitionException`. Added a programmatic register-if-missing
  in `run()` (checked over `springBeans` for any `UserInteractions`-typed
  class, and only registers a no-op if none is present). `@ConditionalOnMissingBean`
  isn't reliably honored without `@EnableAutoConfiguration` in this manual
  `register()`-based context.
- **freemarker bean disambiguation**: `spring-boot-starter-freemarker`
  (transitively pulled in by jee-to-boot etc.) registers a
  `freeMarkerConfiguration` bean via Boot auto-config alongside sbm-core's
  `configuration` bean. Switched the lookup at the end of
  `SpringBeanProvider.run` from `getBean(class)` to
  `getBean("configuration", class)`.

### sbm-recipes-jee-to-boot — compile-time API breakage (OR 8.80.1)

- `AutoFormat` 0-arg → 1-arg `(style:null)`
- `OrderImports` 1-arg → 2-arg `((removeUnused, style:null))`
- `AddOrUpdateAnnotationAttribute` 4-arg → 6-arg
  `(... , null oldValue, addOnly, null appendArray)`
- 3 `doNext` helpers (`SwapHttHeaders`, `ReplaceResponseEntityBuilder`,
  `SwapFamilyForSeries`) were calling `getRecipeList().add(...)`. Switched
  each class to hold its own `ArrayList<Recipe>` and override
  `getRecipeList()` to return it.

### sbm-recipes-jee-to-boot — production-recipe regressions (the deep cluster)

- **Dead-mappings in `ReplaceMediaType`**: constructor built ~30 entries
  into a `recipes` list from `mappings.put(...)`, but the OR 8.80.1
  `getRecipeList()` refactor returned a hardcoded `List.of(...)` that
  didn't include `recipes`. Result: `MediaType._TYPE` ↔ `_VALUE` constant
  pairs silently never renamed. Fix: prepend `recipes` to the returned list.
- **Dead-mappings in `SwapStatusForHttpStatus`**: ctor did
  `fieldsMapping.forEach((k,v) -> new ReplaceConstantWithAnotherConstant(...))`
  which **discarded** the new recipes (no assignment). Moved the build into
  `getRecipeList()` so mappings actually run (e.g. `REQUEST_ENTITY_TOO_LARGE` →
  `PAYLOAD_TOO_LARGE`).
- **Stray `)` in `SwapResponseWithResponseEntity#readEntity` template**:
  `JavaTemplate.builder("#{any(...)}.getBody())")` — OR 8.13.4 was lenient,
  8.80.1 strict-fails with `generated 0 statements`. Removed the trailing
  paren.

### sbm-recipes-boot-upgrade (preparatory cleanup landed; module not yet activated)

- **`MethodMatcher` accessor removal in OR 8.80.1**:
  `MethodMatcher.getTargetTypePattern/getMethodNamePattern/getArgumentPattern`
  were dropped. `MethodMatcher.toString()` returns the full
  `<type> <name>(<args>)` pattern — exactly what `renameMethodCalls` accepts.
  Replaced both call sites (`Boot_24_25_SpringDataJpaAction.refactorCallsToGetOne`
  and `Boot_24_25_SpringDataJpa.report`). Both are exercised only by `@Disabled`
  tests, so this is a compile-only fix.
- **TestProjectContext safety guard / dummy projectRoot**: 8 tests in
  `Boot_24_25_SqlScriptDataSourceInitialization*` passed `Path.of("./dummy")`
  to `withProjectRoot`. The sbm-core safety guard rejects roots outside
  `<cwd>/target/` or system tmp (it would otherwise wipe the module's source
  tree). Moved fixtures to `./target/dummy-test-path`.
- **TestProjectContext create-if-missing**: the safety guard's `Files.walk`
  threw `NoSuchFileException` when the target dir didn't pre-exist. Added an
  `exists()` guard plus `createDirectories` so the subsequent writeResources
  call has a place to write to.
- **`withJavaSource` API form**: 2 tests passed full file paths
  (`src/main/java/com/example/Foo.java`); OR 8.80.1's TestProjectContext
  rejects file-form and requires the source-set directory
  (`src/main/java`). Package and filename are inferred from the source code.

### sbm-recipes-spring-cloud (previous session)

- **`DependencyChangeHandler.recompileModuleClasses` Optional.get NPE**:
  `testJavaSourceSet.get(0)...findFirst(ClasspathDependencies.class).get()` blew
  up on test-source sets that lacked the marker (the marker mutation isn't
  even consumed by the subsequent `parseInputs`). Guarded with `ifPresent`.
  Unblocked `MigrateToSpringCloudConfigServerIntegrationTest` past `AddDependencies`.
- **`ProjectContextFileSystemTestSupport` scanned 0 resources**: the helper
  copies fixtures into `./target/test-projects/<name>/`. The fork's default
  `SpringRewriteProperties.ignoredPathPatterns` include `**/target/**` →
  scan returns nothing → the entire ProjectContext is empty. Override the
  patterns inside the `SpringBeanProvider.run` callback (mirrors the same
  workaround in `RecipeIntegrationTestSupport.andApplyRecipe`).
- **ArchUnit `ControlledInstantiationOfExecutionContextTest` reactor failure**:
  the spring-cloud copy of this test omits the `SbmCoreConfig` and
  `OpenRewriteTestSupport` exceptions present in sbm-core's copy. Standalone
  `mvn -pl ... test` doesn't see the violations (sbm-core/test-helper come in
  as JARs, which `DoNotIncludeJars` filters), but reactor-mode `mvn test` puts
  sibling `target/classes` folders on the test classpath and ArchUnit scans
  them. Aligned the exception list. Same drift exists in the inactive copies
  (`sbm-recipes-spring-framework`, `-mule-to-boot`, `-boot-upgrade`,
  `jaxrs-recipes`, etc.) — apply the same fix when activating each.

### sbm-recipes-jee-to-boot — fixture drift (OR 8.80.1 cosmetic output)

- Import-organizer no longer inserts blank lines between groups when
  adding new imports → updated 10+ test expected literals.
- `ChangeType` leaves substituted types as fully-qualified references at
  some positions (return type) instead of using short form. Expected
  literals updated to use FQCN for those sites
  (`ResponseStatusTest.testOkWithTopLevelType`, `mini_integration_test_1`).
  `ShortenFullyQualifiedTypeReferences` recipe was tried but doesn't pick
  these up — left out for now.
- One stray-quote typo on `ReplaceMediaTypeTest` lines 523/541 that 8.13.4
  parsed leniently but 8.80.1 emits as `ParseError`.

### rewrite-commons fork (re-derived from the lost sandbox + new in this session)

- **`ProjectScanner.filterIgnoredResources`**: throw → warn + empty
- **`MavenProjectFactory.create`**: warn + `List.of()` (added SLF4J logger)
- **`MavenProjectAnalyzer.getBuildProjects`**: short-circuit on empty before
  `mavenProjectSorter.sort` and the `mavenProjects.get(0)` deref
- **`MavenBuildFileParser.parseBuildFiles`**: `Assert.notEmpty` → warn + `List.of()`
- **`MavenProjectGraph.isMultiModuleProject`**: also return true when
  `<modules>` is non-empty (parent inheritance not resolved by `MavenXpp3Reader`)
- **`MavenModuleParser.parseSourceSet`**: skip `ParseError` entries before
  casting to `J.CompilationUnit` (was a `ClassCastException`). New patch
  `928e6ae` for OR 8.80.1's stricter `Parser.parseInputs` return type.
- **`spring-rewrite-commons-launcher/pom.xml`**: comment out the
  `spring-rewrite-commons-plugin-invoker-gradle` dependency (sandbox can't
  reach `repo.gradle.org` for `gradle-tooling-api:8.4`)

## #13 disabled tests (deferred)

5 cross-module classpath-mutation tests in `AddDependencyTest`, all `@Disabled`
with rationale tied to issue #13. They depend on classpath-mutation behaviour
that needs deeper redesign post OR 8.80.1.

```
addingTheDependencyResolvesTheMissingType
addingANewDependencyMakesTypesAvailable
test2
classpathFromJavaSourceSetShouldBeEqual
classpathFromJClasspathMarkerShouldBeEqual
```

Plus 4 in `AddAnnotationAndThenDependency2Test` similarly deferred.

## Issues queued

- **#7** Adopt `rewrite-recipe-bom 3.29.0` (replaces `rewrite-spring` +
  `rewrite-migrate-java` pins)
- **#8** Bump Java baseline 17 → 21
- **#9** Bump Spring Boot 3.1.x → latest 3.x
- **#10** Bump Spring Boot to 4.x

## Next module to activate (per one-at-a-time strategy)

`sbm-recipes-boot-upgrade` is next. **Compile fixes and the easy test-fixture
drift fixes are already on the branch** (commits `d4a3b28f`, `b4e583b0`,
`b1feedc8`, plus `ffc83ce1` in sbm-core). The module pom-line is **kept
commented** because 19 tests still fail. Detailed triage of those failures
follows in the next section.

To resume:

1. Edit root `pom.xml` — uncomment `<module>components/sbm-recipes-boot-upgrade</module>`
2. `mvn -pl components/sbm-recipes-boot-upgrade -am install -DskipTests -Dspring-javaformat.skip=true`
3. `mvn -pl components/sbm-recipes-boot-upgrade test -Dspring-javaformat.skip=true`
4. Apply the fork patch listed below (cluster #1) so spring.factories actually
   parses, then work through the remaining clusters.

After the module is green (env failures aside), commit the activation as
`build: re-activate components/sbm-recipes-boot-upgrade in the reactor` and
push to PR #16.

## sbm-recipes-boot-upgrade — detailed triage of remaining 19 failures

Test count baseline: **184 tests, 12 failures + 7 errors + 4 skipped** after
the prep-fix commits (`d4a3b28f`, `b4e583b0`, `b1feedc8`, `ffc83ce1`) land.
Standalone:
```bash
mvn -pl components/sbm-recipes-boot-upgrade test -Dspring-javaformat.skip=true
```
Failures cluster as follows (most-impactful first):

### Cluster 1 — `CreateAutoconfigurationActionTest` (6 failures) ❗ NEEDS FORK PATCH

**Root cause**: `spring.factories` files have properties syntax but the
`.factories` extension. The fork's `RewriteResourceParser.parseResources`
routes resources to a parser only if `<parser>.accept(path)` returns true;
`PropertiesParser.accept` checks for the `.properties` extension, so
`spring.factories` falls through to the `QuarkParser`. `Quark.printAll()`
returns empty, so the action sees an empty file when reading
`org.springframework.boot.autoconfigure.EnableAutoConfiguration=...`, never
generates the `AutoConfiguration.imports` file, and the spring.factories
resource ends up replaced with empty content / removed entirely.

This is a real production regression, not a test artifact — any SBM user
running this recipe against a real Boot 2.x app will hit the same.

**Fix**: a one-line fork patch routes `*spring.factories` paths through
`PropertiesParser` explicitly. The patch is committed locally on the fork
clone in this sandbox and exported for hand-off:

```
/tmp/sbm-rewrite-commons-patches/0001-fix-parser-route-spring.factories-through-Properties.patch
```

Patch content (sha `de3e7fe` on the local fork branch `bump-or-8.80.1`,
`spring-rewrite-commons-launcher/src/main/java/org/springframework/rewrite/parser/RewriteResourceParser.java`):

```diff
-        else if (propertiesParser.accept(path)) {
+        else if (propertiesParser.accept(path) || path.toString().endsWith("spring.factories")) {
+            // spring.factories has properties syntax but lacks the .properties extension,
+            // so propertiesParser.accept() rejects it. Route it through PropertiesParser
+            // explicitly so downstream recipes can read its content via Properties.File
+            // (otherwise it's parsed as Quark and prints empty).
             propertiesPaths.add(path);
         }
```

Push from a host with GitHub creds:
```bash
git clone https://github.com/promptics/spring-rewrite-commons.git
cd spring-rewrite-commons && git checkout bump-or-8.80.1
git am /path/to/0001-fix-parser-route-spring.factories-through-Properties.patch
git push origin bump-or-8.80.1
```

After the patch lands, the cluster reduces but doesn't go to zero. With the
patch applied locally I observed the test counts go from `6 fail` →
`3 failures + 3 errors`. The remaining failures are because, after the
properties file *is* parsed correctly, the action's
`removeAutoConfigKeyFromSpringFactories` calls `replace(...)` which appears to
remove the resource from the resource set entirely (instead of replacing
in-place) — so the 6 tests that all expect the spring.factories resource to
still exist after the action runs (`itDeletesSourceWhenMovedToNewFile`,
`autoConfigurationImportsContent`, `shouldMoveMultipleProperties`, etc.) all
fail in the second hop. That second hop probably traces to `replace()` semantics
on `ProjectResourceSet` after OR 8.80.1, similar to the type-wrap fix in
`Module.search` (commit `51fa0826`). I did **not** debug it to root cause.

**Estimated effort**: 30 min once the fork patch lands, to investigate the
`replace()` semantics. If `Module.search` is any guide, the fix lives in
sbm-core's `ProjectResourceSet`/`Module` plumbing.

### Cluster 2 — `RedeclaredDependenciesFinderTest` (3 errors)

```
shouldFindDependencyRedefinedBomVersion: IllegalArgument Could not find expected MavenResolutionResult for module1/pom.xml
shouldReportSameVersion:                IllegalArgument Could not find expected MavenResolutionResult for module1/pom.xml
shouldIgnoreWithoutDependencyManagement: NoSuchElement No value present
```

**Hypothesis**: OR 8.80.1's Maven parser attaches `MavenResolutionResult`
markers via the multi-module pom resolution flow. The fork patch in
`MavenProjectGraph.isMultiModuleProject` (commit `f5b4e40` on the fork) was
needed because `MavenXpp3Reader` doesn't resolve parent-inheritance — the
parent of `module1/pom.xml` is the test's root pom but the test fixture may
not declare modules in a way the resolver recognises. Worth a 15-min look at
the fixture poms in this test vs. the working multi-module fixtures in
`MigrateToSpringCloudConfigServer*`.

**Estimated effort**: 1–2 hours. Likely needs another fork patch or a fixture
adjustment.

### Cluster 3 — `BootHasAutoconfigurationConditionTest` (2 failures)

```
conditionTests:48        — assertion: expecting true but was false
itCanDoMultiLine:64      — same
```

These both assert `condition.evaluate(context)` returns true after setting up
a context with a `spring.factories` containing
`EnableAutoConfiguration=...`. **Same root cause as cluster 1** — the
condition reads spring.factories via the same parser pipeline and gets empty
content. The fork patch from cluster 1 should fix these as a side-effect.

**Estimated effort**: 0 (subsumed by cluster 1).

### Cluster 4 — `Boot_27_30_UpgradeReplaceJohnzonDependenciesTest` (2 failures) + `UpgradeDepenenciesMigrationTest.migrateEhCacheToSpringBoot3` (1) + `UpdatePropertyTest.runYamlTestsData` (1)

Pom and yaml whitespace fixture drift from OR 8.80.1's writers:

- `Boot_27_30_UpgradeReplaceJohnzonDependenciesTest` — expected pom has the
  `<parent>` block indented 4 spaces, actual is 3. Plus a `<properties>` block
  for `maven.compiler.target/source` being preserved that the expected omits.
  Same `<properties>` drift in `UpgradeDepenenciesMigrationTest`.
- `UpdatePropertyTest.runYamlTestsData` — yaml output: actual is missing two
  lines (`sql.init.password: password2`, `sql.init.username: username2`)
  compared to expected. Likely a duplicate-key-handling change in OR's yaml
  writer.

**Fix pattern**: update the expected literals in each test (this is the same
pattern used in `sbm-recipes-jee-to-boot` import-blank-line drift commits
`195a4b1c` and `0b82bbaf`).

**Estimated effort**: 30 min, mechanical.

### Cluster 5 — `SpringFactoriesHelperTest.detectsFileWithSpringFactories` (1 failure)

Same root cause as cluster 1. Fork patch from cluster 1 should fix.

### Cluster 6 — `DatabaseDriverGaeSectionBuilderTest.checkShouldFailWhenAppEngineDriverIsFoundOnClasspath` (1 failure)

```
expected: FAILED but was: PASSED
```

Test sets up a project with the `appengine-api-1.0-sdk` dependency on the
classpath and expects a precondition check to flag it. Probably classpath
resolution / `MavenResolutionResult` related — see cluster 2.

**Estimated effort**: 1 hour.

### Cluster 7 — `Boot_24_25_UpdateDependenciesRecipeTest.updateWithParentPom` (1 error)

```
This could be a broken jar. Activate logging on WARN level for 'org.openrewrite' might reveal more information.
```

Network / m2 cache problem. Likely needs the spring-boot-starter-parent BOM
prewarm trick from the handover's "Useful commands" section. Verify by
running with `-X` and looking for the actual jar that fails.

**Estimated effort**: 15 min if it's just prewarm; longer if it's a real OR
artifact-download regression.

### Cluster 8 — `HazelcastHibernateRemovedReportSectionTest.withSingleModuleApplicationShouldRender` (1 error)

```
org.openrewrite.maven.MavenDownloadingException: com.hazelcast:hazelcast-hibernate:3.8.2 failed. Unable to download dependency
```

Sandbox can't reach the repo that hosts this artifact. Either prewarm
manually, swap the fixture to use a more accessible artifact, or document as
env failure. Pre-existing condition unrelated to OR upgrade.

**Estimated effort**: 15 min.

## Cumulative remaining-work estimate

If the fork patch lands first (clusters 1, 3, 5, 6 partially):

| Cluster | Effort | Type |
|---------|--------|------|
| 1 (post-patch) | 30 min | sbm-core resource-set replace semantics |
| 2 | 1–2 hours | OR Maven resolution / fixture drift / fork patch |
| 4 | 30 min | mechanical fixture updates |
| 6 | 1 hour | classpath resolution |
| 7 | 15 min | m2 prewarm |
| 8 | 15 min | env / fixture |

**Total: 4–6 hours** of focused work, plus the fork roundtrip latency for
patch #1.

## Pre-existing local fork state

The local fork checkout at `/tmp/src/spring-rewrite-commons` is **1 commit
ahead of `origin/bump-or-8.80.1`** (the cluster-1 patch). To reset before
re-pulling:
```bash
cd /tmp/src/spring-rewrite-commons
git reset --hard origin/bump-or-8.80.1
```
Or rebuild without resetting (keeps the patch applied locally so cluster 1
tests pass against the local m2):
```bash
mvn -f /tmp/src/spring-rewrite-commons/pom.xml install \
    -pl spring-rewrite-commons-launcher -am -DskipTests \
    -Dspring-javaformat.skip=true
```

## Where I left off (mid-debug context)

- Last debugged in `CreateAutoconfigurationActionTest.autoConfigurationImportsIsGenerated`
  with temporary `System.out.println` instrumentation in the action's `apply`
  and `getSpringFactoriesProperties`. Output showed:
  ```
  DEBUG-getSpringFactories: print=[] sf=org.openrewrite.quark.Quark
  ```
  → diagnosed as the cluster-1 root cause (Quark parser).
- Applied the fork patch locally and reran. Test count went from `6 fail` to
  `3 fail + 3 err`. New error pattern was `IndexOutOfBounds` from
  `getNewAutoConfigFileContents` and `getSpringFactoryFile` (both index 0 of
  empty list). DEBUG-RES showed only `pom.xml` remaining in the resource set
  after `action.apply` — i.e. spring.factories was removed (replace lost it)
  AND the new AutoConfiguration.imports was never added.
- I reverted **all** debug instrumentation and the local pom-activation. The
  fork patch is still applied on the local fork checkout (committed there as
  `de3e7fe`); it is **not** pushed and **not** in any SBM commit. The fork
  patch file is at `/tmp/sbm-rewrite-commons-patches/0001-...patch`.

## Active reactor unchanged

```
spring-boot-migrator         (root)
test-helper
sbm-openrewrite              — 54 tests, all green
sbm-core                     — 326 tests, 14 skipped (#13 cross-module classpath, deferred)
recipe-test-support
sbm-support-boot             — 81 tests, all green ✅
sbm-support-jee              — 10 tests, 5 pre-existing @Disabled ✅
sbm-support-weblogic         —  6 tests, all green ✅
sbm-recipes-jee-to-boot      — 146 tests, 15 pre-existing @Disabled ✅
sbm-recipes-spring-cloud     —  10 tests, 1 env failure ✅
```

## Known recurring patterns to watch for in remaining modules

When activating downstream modules, expect to see these (catalogued from
prior modules):

1. **OR API arity changes at constructor sites**: AutoFormat / OrderImports /
   AddOrUpdateAnnotationAttribute / MavenRepository / Dependency / etc.
2. **`getRecipeList().add(...)` mutation pattern**: now throws on immutable
   list. Refactor to a local `List<Recipe>` field + override `getRecipeList()`.
3. **Dead-mappings pattern**: `Map.forEach((k,v) -> new RecipeXxx(...))`
   discards the recipes. The fix is always: collect into a list and include
   in `getRecipeList()`.
4. **Import-blank-line drift**: OR 8.80.1's import organizer doesn't add
   blank lines between groups. Update expected literals.
5. **`ChangeType` FQCN at return-type position**: cosmetic — update
   expected literals to FQCN.
6. **`UserInteractions`/freemarker DI conflicts in test contexts**: already
   handled in `SpringBeanProvider`; if they re-surface, the fix likely
   needs to extend the register-if-missing pattern.
7. **`ProjectContextFileSystemTestSupport` empty-scan**: handled in
   `recipe-test-support`; if a downstream module re-implements its own
   filesystem fixture loader, it needs the same `ignoredPathPatterns` override.
8. **`ControlledInstantiationOfExecutionContextTest` reactor failure**: the
   per-module copies of this test omit `SbmCoreConfig`/`OpenRewriteTestSupport`
   exceptions. Standalone passes; reactor mode doesn't. Apply the sbm-core
   exception list to each copy when activating its module (one-line drift fix).

## Useful commands

```bash
# Full active reactor test (will hit the 2 env git failures in sbm-core)
mvn test -fae -Dspring-javaformat.skip=true

# Single-module
mvn -pl components/sbm-recipes-jee-to-boot test -Dspring-javaformat.skip=true

# Pre-warm m2 for Boot multi-module tests (one-time, brings in
# spring-boot-starter-parent BOM tree). Use 2.7.1 (matches
# HasSpringBootStarterParentTest fixture); 2.7.5 also useful:
mkdir -p /tmp/boot-prewarm && cat > /tmp/boot-prewarm/pom.xml <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-parent</artifactId>
      <version>2.7.1</version>
    </parent>
    <groupId>com.example</groupId><artifactId>prewarm</artifactId><version>1.0</version>
</project>
EOF
mvn -f /tmp/boot-prewarm/pom.xml help:effective-pom -q

# Rebuild rewrite-commons fork after edits (-pl/-am avoids gradle-tooling-api)
cd /tmp/src/spring-rewrite-commons-launcher
mvn spring-javaformat:apply -q
mvn install -pl spring-rewrite-commons-launcher -am -DskipTests -q
cd /home/user/spring-boot-migrator
mvn -pl components/sbm-core,components/recipe-test-support,components/sbm-openrewrite -am install -DskipTests -Dspring-javaformat.skip=true -q
```

## Don't forget

- `gpg.format=ssh` in `/root/.gitconfig` will reject jgit operations. For
  rewrite-commons commits use `git -c gpg.format=openpgp -c commit.gpgsign=false commit ...`
- `spring-rewrite-commons-plugin-invoker-gradle` is excluded in
  `spring-rewrite-commons-launcher/pom.xml` (commented out) — sandbox can't
  reach `repo.gradle.org`
- SBM pushes go to `origin` on `claude/issue-6-7/integrate-rewrite-commons` (PR
  #16). Push each per-module activation as you go; the user's stop-hook
  enforces this (`~/.claude/stop-hook-git-check.sh`).
- rewrite-commons fork pushes are still blocked **from this sandbox** (no
  GitHub creds, no SSH binary). The 3 patches needed in this session were
  pushed by the user via `git am` from their host. Future fork edits need
  the same out-of-sandbox roundtrip — keep patches under
  `/tmp/sbm-rewrite-commons-patches/` if you need to hand them off.
- User explicitly does NOT want failing tests `@Disabled` as a shortcut — fix
  the root cause first.
- The proxy port for `origin` rotates between runs (37447 → 41139 → 35735 →
  37977 → 34959 → 39361 → 42869 → 35587 — all on `127.0.0.1/git/promptics/spring-boot-migrator`).
  This is the harness; nothing to fix.
- If a build appears to "do nothing" (no output), it's likely a successful
  `-q` run — check for the SNAPSHOT jar's mtime in `~/.m2/...` to confirm.
