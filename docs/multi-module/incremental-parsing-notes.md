# Multi-Module Incremental Parsing — Notes & Plan

Working document. Captures the current state of the parser, the in-progress
work that lives on branch `revamp/feature/879-parser-should-allow-parsing-of-javasources-when-dependencies-changed`,
the open questions, and the planned sequence of work.

## Roadmap

1. **First**, bring the existing parser (without the incremental
   multi-module work) up to the latest **OpenRewrite** and **Spring Boot 4**.
   This is the priority. The incremental-parsing problem is explicitly out
   of scope for that step.
2. **Then**, tackle multi-module incremental parsing on top of the upgraded
   baseline. Most likely by reviving / rebasing the work from branch 879.

## Current parser pipeline (baseline on `main`)

Entry point: `RewriteProjectParser.parse(...)` in
`sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/RewriteProjectParser.java`.

- **Build first, parse second.** `RewriteProjectParser.java:168` runs
  `mvn clean install` via `MavenExecutor.onProjectSucceededEvent(...)` before
  any source is parsed. Consequence: every module has a fresh `target/classes/`
  and an installed JAR in the local repo.
- **Module ordering.** `RewriteProjectParser.java:134` uses
  `mavenSession.getProjectDependencyGraph().getSortedProjects()` for
  topological order.
- **Sequential per-module parse.** `SourceFileParser.parseOtherSourceFiles(...)`
  in `sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/SourceFileParser.java:62`
  iterates modules and calls `parseModuleSourceFiles(...)` for each.
- **Fresh `JavaParser.Builder` per module.** `SourceFileParser.java:89-91`.
  No `JavaTypeCache` is shared across modules. No parser state crosses module
  boundaries.
- **Cross-module type resolution = bytecode only.** Inside OpenRewrite's
  `MavenMojoProjectParser#processMainSources(...)` (called via reflection in
  `MavenMojoProjectParserPrivateMethods.java:88-110`), types from sibling
  modules are resolved through `mavenProject.getCompileClasspathElements()` —
  i.e. the JARs that `mvn install` produced. Self-documented at
  `SourceFileParser.java:152`.
- **Dead hook for sibling-module awareness.**
  `SourceFileParser.pathsToOtherMavenProjects(...)` at
  `SourceFileParser.java:171-176` returns `Set.of()`. The result is wired
  into `ResourceParser` (`SourceFileParser.java:92-101`) as
  `pathsToOtherModules`, but only controls which dirs `ResourceParser` skips
  — it does **not** feed into `JavaParser`'s type resolution. Misleading at
  first glance.
- **TODO marker for the whole feature.** `RewriteProjectParser.java:122` —
  `// TODO: "runPerSubmodule"`.

## What was already built on branch 879

Branch: `revamp/feature/879-parser-should-allow-parsing-of-javasources-when-dependencies-changed`.
Author: Fabian Krüger. Last activity: 2023-10-18.

### Building blocks

- **`JavaParserMarker`** (commit `9ca1d252`,
  `sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/JavaParserMarker.java`).
  An OpenRewrite `Marker` that keeps a stateful `JavaParser` instance attached
  to the parsed LST. From its own javadoc:
  > Used to keep the stateful JavaParser for later parsing.
  > This is required when (re-)parsing a submodule somewhere (non-leaf) in a
  > reactor build tree. Then this module requires the JavaParser with types
  > from the last parse where types from lower modules are cached.
- **`ClasspathRegistry` + `ClasspathRegistryFactory`** (commit `93163049`).
  `Map<Path, MavenResolutionResult>` keyed by build file path. Lets a caller
  recompute the classpath of any module on demand via
  `ClasspathExtractor.extractClasspath(result, scope)` without re-running
  Maven.
- **`MavenModuleParser.parseLeafModule(...)`** (commit `d4848294`). Concrete
  application: parses a leaf module's sources, then attaches a
  `JavaParserMarker(UUID, javaParser)` to every produced `SourceFile`.

### Empirically validated rules for cross-module type resolution

From `MavenModuleParserTest$JavaCompilerTypeResolutionTest` on commit
`d4848294`. Two source files: `class A` in `com.foo`, `class B extends A` in
`com.bar`. Tested whether B's `extends` type resolves to `com.foo.A`.

| Setup | Resolves? |
|---|---|
| Same parser, single `parse(a, b)` call, shared cache | ✅ |
| Different parsers, separate calls, separate caches | ❌ |
| Different parsers, separate calls, **shared `JavaTypeCache`** | ❌ — *sharing the cache is not enough* |
| **Same parser**, separate calls, shared cache, with `parser.reset()` in between | ✅ — *that's what we need* |

Key learning: a shared `JavaTypeCache` alone does **not** make types from
parser-A visible to parser-B. The actual `JavaParser` instance must be
reused, and `parser.reset()` must be called between successive `parse(...)`
calls (otherwise OpenRewrite throws — see literal comment
`// fails! need to call reset()` in the test).

The follow-up test `reuseJavaParserFromMarkerExample` demonstrates the
round-trip: pull the parser back out of the `JavaParserMarker` on a
previously parsed `SourceFile`, then parse a new compilation unit with it —
types from the original module resolve correctly.

### Where the work stopped

Last commit on the branch (`f45d696b "Testing options"`, 2023-10-18) added
`MavenPartialProjectParsingTest`. It is the integration-test stub for the
incremental scenario. Three ordered tests:

1. `parseReactorProject` — parses 3-module reactor (a, b, c).
   Ends with `// TODO: verify initial parsing result here`.
2. `addingDependencyToB` — runs an `AddDependencyVisitor` against module B.
   Ends with `// TODO: assertion the classpath change`.
3. `addCodeUsingTheNewTypes` — wants to add code that uses types from the
   newly added dependency. **Cuts off mid-statement**:
   `JavaTemplate.builder("@Nullable").javaParser` (no `;`, no `()`).

The work paused at the point where a recipe **mutates the classpath** and a
following recipe needs to use **new** types that the cached
`JavaParserMarker`-parser does not yet know about.

## Likely root cause of the original blocker

Three candidates. (a) is the most likely one given where the work stopped.

**(a) Stale parser after recipe-driven classpath mutation.**
The `JavaParser` cached in the `JavaParserMarker` only knows the classpath
that was active when it was built. Once a recipe edits `<dependencies>` or
adds a new module, the marker-parser is outdated. Rebuilding it from scratch
loses the type cache that was the whole reason for keeping it. There was no
plan yet for *invalidating* or *augmenting* the marker-parser when the
classpath changes mid-run.

**(b) DAG vs. linear chain.** The marker-parser approach assumes a linear
chain (C depends on B depends on A). Real reactors are DAGs with diamond
shapes — a module may depend on two predecessors. There is no OpenRewrite
API to merge two `JavaParser` instances, so it is unclear which marker to
follow.

**(c) `reset()` semantics under recipe execution.** The same `JavaParser`
cannot parse the same source twice without `reset()`. In an incremental
setting where modules may be re-parsed multiple times across recipe runs,
this is fragile and may collide with OpenRewrite's own recipe execution
which builds parsers itself.

## New idea — synthetic classes / pre-computed type signatures

Instead of needing either (i) the up-to-date JAR on the classpath or
(ii) the LST of another module in memory, we could feed the parser
**synthetic type signatures** for cross-module types. OpenRewrite is
believed to have its own mechanism for this — likely the **`TypeTable`**
facility (rewrite ships pre-computed TSV-based type info for popular
libraries) and/or `JavaType.ShallowClass`.

Why this is attractive:

- Sidesteps the bytecode-vs-LST consistency problem from option (a)
  entirely. We feed signatures, not implementations.
- Sidesteps the parser-reuse / `reset()` fragility — every module gets a
  fresh parser, but with the right synthetic types pre-loaded.
- Naturally fits the DAG case: union of synthetic types from all
  predecessors.
- After a recipe modifies a module, we recompute the synthetic types of
  that module and the change propagates downstream.

Open: how exactly does OpenRewrite's `TypeTable` API let an external caller
inject types? Is there a public path, or is it an internal-only
optimisation? This needs investigation against the **upgraded** OpenRewrite
(post step 1 of the roadmap).

## TODOs / questions

Order roughly matches the roadmap.

### Step 1 — upgrade prep (do first)

- [ ] Pin the current `rewrite.version`, `spring-boot.version`, Java baseline.
      Decide the target OpenRewrite version and Spring Boot 4 line.
- [ ] Inventory the reflection-based hooks into OpenRewrite internals
      (e.g. `MavenMojoProjectParserPrivateMethods.invokeProcessMethod(...)`
      at `MavenMojoProjectParserPrivateMethods.java:88-110`). They will
      almost certainly break across OR major versions.
- [ ] Inventory removed/renamed OpenRewrite types we depend on
      (`ResourceParser`, `MavenMojoProjectParser`, `MavenResolutionResult`
      shape, `JavaParser.Builder` signatures, marker base classes).
- [ ] Dependency review for Spring Boot 4 (Jakarta EE 10/11, Java 17+
      baseline, Spring Framework 7).
- [ ] Plan: do the upgrade on a fresh branch off `main`, **not** off 879.
      Branch 879 will be rebased / re-implemented afterwards.

### Step 2 — incremental multi-module (after upgrade)

- [ ] Re-evaluate whether the `JavaParserMarker` approach still applies on
      the upgraded OpenRewrite, or whether a synthetic-types approach is
      now the better fit.
- [ ] Investigate OpenRewrite's `TypeTable` / `JavaType.ShallowClass` APIs.
      Is there a documented way to pre-load types into a `JavaParser` other
      than `.classpath(...)` and `.dependsOn(...)`?
- [ ] Define dirty-module detection. What counts as "changed"? Source diff,
      pom diff, classpath diff?
- [ ] Define how a classpath-mutating recipe (e.g. `AddDependencyVisitor`)
      signals invalidation to the parser cache.
- [ ] Decide DAG strategy: per-leaf parse with merged synthetic types from
      all predecessors, or stick with topological linear walk plus a join
      step.
- [ ] Resurrect `MavenPartialProjectParsingTest` and finish its three
      ordered tests as the acceptance harness.
- [ ] Decide whether `clean install` (`RewriteProjectParser.java:168`) can
      be replaced with something cheaper / per-module / skipped entirely
      when synthetic types are available.

### Open questions

- Does OpenRewrite (current and upgraded version) expose a public way to
  inject pre-computed `JavaType.FullyQualified` instances into a
  `JavaParser` without going through the classpath or `dependsOn`?
- Does the `TypeTable` mechanism support producing entries from our own
  in-memory LSTs, or only from JARs?
- How does OpenRewrite itself handle the multi-module case in
  `rewrite-maven-plugin` today? Is there prior art we can lift?
- What is the actual cost split between `mvn clean install` and the parse
  itself in a representative project? This decides whether incremental
  parsing alone is worth it, or whether `install` also needs to be made
  incremental.

## File reference

- `sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/RewriteProjectParser.java:122` — `// TODO: "runPerSubmodule"`
- `sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/RewriteProjectParser.java:134` — module ordering
- `sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/RewriteProjectParser.java:168` — `mvn clean install` before parsing
- `sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/SourceFileParser.java:62` — per-module loop
- `sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/SourceFileParser.java:89-91` — fresh `JavaParser.Builder` per module
- `sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/SourceFileParser.java:152` — classpath comes from `getCompileClasspathElements()`
- `sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/SourceFileParser.java:171-176` — empty `pathsToOtherMavenProjects`
- `sbm-support-rewrite/src/main/java/org/springframework/sbm/parsers/MavenMojoProjectParserPrivateMethods.java:88-110` — reflection bridge into OR private API

Branch 879 references (not on `main`):

- `JavaParserMarker.java` — commit `9ca1d252`
- `ClasspathRegistry.java`, `ClasspathRegistryFactory.java` — commit `93163049`
- `MavenModuleParser.parseLeafModule(...)` and `JavaCompilerTypeResolutionTest` — commit `d4848294`
- `MavenPartialProjectParsingTest` — commit `f45d696b`
