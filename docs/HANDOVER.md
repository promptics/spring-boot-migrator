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

## Active reactor (7 modules, all green) ✅

```
spring-boot-migrator         (root)
test-helper
sbm-openrewrite              — 54 tests, all green
sbm-core                     — 326 tests, 14 skipped (#13 cross-module classpath, deferred)
recipe-test-support
sbm-support-boot             — 81 tests, all green ✅
sbm-support-jee              — 10 tests, 5 pre-existing @Disabled ✅
sbm-support-weblogic         —  6 tests, all green ✅
sbm-recipes-jee-to-boot      — 146 tests, 15 pre-existing @Disabled ✅  ← just landed
```

The 2 sbm-core errors that surface in this sandbox (`GitSupportTest.addAllAndCommit`,
`PreconditionVerifierIntegrationTest.allChecksSucceed`) are pre-existing
**environmental** — `/root/.gitconfig` has `gpg.format=ssh` which JGit rejects.
Not regressions. Skip with `-Dtest='!GitSupportTest#addAllAndCommit,...'` if needed,
or run on a host without that config.

## Inactive modules (commented out in root pom — activate one at a time)

```
sbm-recipes-spring-cloud      ← NEXT
sbm-recipes-spring-framework
sbm-recipes-mule-to-boot
sbm-recipes-boot-upgrade
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

## spring-rewrite-commons fork patches (`/tmp/src/spring-rewrite-commons-launcher`, branch `bump-or-8.80.1`)

```
928e6ae fix(maven): skip classpath collection for unparseable sources             ← re-derived
f5b4e40 fix(maven): detect multi-module via <modules> when packaging is inherited ← re-derived
d481cdf parser: tolerate empty/blank-project resources                            ← re-derived
1a29dcb fix(or): RewriteRecipeDiscovery activation guard works with leaf recipes
fedfaf4 fix(test): adapt 4 launcher/polyglot test assertions to OR/Maven contracts
d3f3313 test(gradle): update brittle plugin-count literal 9 → 10 (not OR-related)
```

The top three are local-only — push to `origin/bump-or-8.80.1` is **blocked
from this sandbox**: GitHub HTTPS push asks for a username and
`GIT_TERMINAL_PROMPT=0` returns `could not read Username for 'https://github.com'`.
No `GH_TOKEN`/`GITHUB_TOKEN`/`~/.git-credentials`/credential helper is
configured. Patches are exported as `git format-patch` files in this sandbox
at `/tmp/sbm-rewrite-commons-patches/`:

```
0001-parser-tolerate-empty-blank-project-resources.patch
0002-fix-maven-detect-multi-module-via-modules-when-packa.patch
0003-fix-maven-skip-classpath-collection-for-unparseable-.patch
```

To push from a host with creds:
```bash
git clone https://github.com/promptics/spring-rewrite-commons.git
cd spring-rewrite-commons && git checkout bump-or-8.80.1
git am /path/to/0001-*.patch /path/to/0002-*.patch /path/to/0003-*.patch
git push origin bump-or-8.80.1
```

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

`sbm-recipes-spring-cloud` is next. To activate:

1. Edit `pom.xml` — uncomment `<module>components/sbm-recipes-spring-cloud</module>`
2. `mvn -pl components/sbm-recipes-spring-cloud -am install -DskipTests -Dspring-javaformat.skip=true`
3. `mvn -pl components/sbm-recipes-spring-cloud test -Dspring-javaformat.skip=true`
4. Triage failures one-by-one, prefer fixing root cause over disabling

After each module activation, commit fixes with focused one-line subjects, then
the activation commit `build: re-activate components/<module> in the reactor`,
and push to PR #16.

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
- rewrite-commons fork pushes are blocked from this sandbox (no GitHub creds)
  — the 3 re-derived patches are local-only until pushed from a host with
  creds. Patches available at `/tmp/sbm-rewrite-commons-patches/` for `git am`.
- User explicitly does NOT want failing tests `@Disabled` as a shortcut — fix
  the root cause first.
- The proxy port for `origin` rotates between runs (37447 → 41139 → 35735 →
  37977 → 34959 → 39361 → 42869 → 35587 — all on `127.0.0.1/git/promptics/spring-boot-migrator`).
  This is the harness; nothing to fix.
- If a build appears to "do nothing" (no output), it's likely a successful
  `-q` run — check for the SNAPSHOT jar's mtime in `~/.m2/...` to confirm.
