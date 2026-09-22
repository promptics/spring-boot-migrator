# Architecture Review

Working document. Describes the current structure of SBM's engine, records
where it duplicates capability that OpenRewrite 8 now provides natively, and
lists the design issues found while preparing the OpenRewrite 8.80.1 upgrade.

File references are to `main` unless stated otherwise. Related: issues #7,
#11, #13, #14, #19.

## Summary

SBM was written against OpenRewrite 7. A large part of its engine exists
because OR 7 lacked features that OR 8 has since gained: recipe composition,
a precondition algebra, a scanning phase, stable LST identity, per-module
dependency resolution, and a module graph.

Those parts are now duplication and can be removed. A separate part — the
imperative authoring API that recipe authors actually write against — is not
duplication and is discussed in
[`recipe-authoring-direction.md`](recipe-authoring-direction.md).

## Recipe / Action / Condition

`Recipe` (`components/sbm-core/src/main/java/org/springframework/sbm/engine/recipe/Recipe.java:34`,
152 lines) is a plain POJO: a name, a description, one `Condition`, an ordered
`List<Action>`, and an `Integer order`. It is not an OpenRewrite recipe.
Applicability is `condition.evaluate(ctx) && any action applicable`
(`Recipe.java:91`); execution is a sequential loop with no rollback
(`Recipe.java:95-107`).

There is **no composition**. No nesting, no `include`, no recipe referencing
another recipe. Ordering across recipes is a single flat integer. The only
quasi-composition is `ComposableAction` / `MultiModuleAwareAction`.

`Condition` (`Condition.java`) offers only `or()`. There is no `and` or `not`.

Separately, `engine/precondition/PreconditionCheck` implements global gates
that run before scanning — a second, unrelated condition system.

Recipes are declared in YAML under `classpath*:/recipes/**/*.yaml`
(`SbmRecipeLoader.java:33`), currently 37 files. Every action and condition
names a Java FQN in a `type:` field which is resolved by `Class.forName`
(`ActionDeserializationDispatcher.java:57`, `ConditionDeserializer.java:54`)
and then autowired into the bean factory (`ConditionDeserializer.java:63`).
YAML supplies parameters only; there are 72 `Action` implementations.

`RewriteRecipeLoader.loadRecipes()` returns an empty list on purpose
(`RewriteRecipeLoader.java:38-42`):

```java
// returns empty list for now. Otherwise, all rewrite recipes would be in the list of applicable recipes.
```

OpenRewrite recipes are therefore structurally invisible in SBM's recipe
list. The underlying need is namespacing and filtering, not suppression.

### OpenRewrite 8 equivalents

| SBM | OpenRewrite 8 |
|---|---|
| `Recipe` with flat `List<Action>` | `Recipe.getRecipeList()` — a composition tree |
| `Condition` (`or` only) | `Preconditions.and` / `.or` / `.not` |
| `Action` (imperative, stateful) | `ScanningRecipe` (scanner / accumulator / generate) |

## ProjectContext

`ProjectContext` (`engine/context/ProjectContext.java:45`) is mutable and
holds the project root, a `ProjectResourceSet`, a shared `JavaParser`, a
shared `ExecutionContext`, a refactoring factory, a base-package calculator
and a result merger. Two of its own accessors are already deprecated
(`getModules()` at `:71`, `getBuildFile()` at `:91`).

`ProjectResourceSet` is an `ArrayList` of
`RewriteSourceFileHolder<? extends SourceFile>` — a mutable box around
immutable OpenRewrite LSTs. `RewriteSourceFileHolder.replaceWith()`
(`:109`) swaps the LST and sets a dirty flag by comparing `printAll()`
output, which is O(size of tree) per check. Deletion is a tombstone filtered
in `stream()`.

Actions mutate either through typed facades (`OpenRewriteJavaSource`,
`OpenRewriteMavenBuildFile`) or through `ProjectContext.apply(org.openrewrite.Recipe)`
(`:109`), which runs the recipe over every source file and merges results
back by path (`RewriteMigrationResultMerger.java:36`, still carrying
`// TODO: handle added` at `:37`).

OpenRewrite 8 supersedes this layer with `LargeSourceSet` / `RecipeRun` and
stable `SourceFile` identity. Merging by path rather than by id, and
dirty-tracking by printed string, are both artefacts of the older model.

## OpenRewrite bridging

Three adapters, all `AbstractAction` subclasses:
`OpenRewriteDeclarativeRecipeAdapter` (inline YAML),
`OpenRewriteNamedRecipeAdapter` (by name) and
`OpenRewriteRecipeAdapterAction` (in-code recipe).
`OpenRewriteSourceFilesFinder` unwraps holders into `List<SourceFile>` for
each call, so every OpenRewrite recipe invocation is a full unwrap / run /
re-wrap round trip over the whole project. There is no source-set scoping.

`engine/recipe/OpenRewriteRecipeAdapterAction.java:44-48` carries a
`// FIXME: use getApplicableTest and getSingleSourceApplicableTest to calculate`
above a commented-out reflective lookup of `Recipe#getApplicableTest`. SBM
does not currently evaluate OpenRewrite's own applicability tests, so it runs
recipes that OpenRewrite would have skipped.

`components/sbm-openrewrite/` holds hand-written visitors —
`AddAnnotationVisitor`, `RemoveAnnotationVisitor`,
`AddOrReplaceAnnotationAttribute`, `FindTypesImplementing`,
`AddMavenRepository` and others. Most now exist natively in `rewrite-java`
and `rewrite-maven` 8.x. Eight modules still depend on this one. Removal or
merge is tracked as issue #11.

## Parsing and classpath

Two complete parsers coexist.

The legacy parser (`project/parser/MavenProjectParser.java:70`) is
hand-rolled: it parses poms, sorts them, builds provenance markers manually,
downloads artifacts, mutates a single shared `JavaParser` via `setClasspath()`
per module and per scope (`:195`, `:220`), and mutates marker lists in place
(`:212`, on structures that are meant to be immutable). It carries
`FIXME: ALL JavaParser should share the same TypeCache` at `:134`.

The replacement lives in `sbm-support-rewrite/` and runs a real Maven session,
delegating to OpenRewrite's `MavenMojoProjectParser`. It reaches OpenRewrite's
private methods reflectively in
`MavenMojoProjectParserPrivateMethods.java:89-110` and `:125`.

`ClasspathRegistry` (`java/impl/ClasspathRegistry.java:36`) is a static
singleton (`:93-105`) holding the dependencies of all modules in all scopes,
merged together, with `// FIXME: #7 respect scope` at `:74`. It is consumed
ad hoc by `JavaSourceSetImpl`, `OpenRewriteType` and `OpenRewriteMethod`,
each building throwaway `JavaParser` instances from it. Per-module classpath
isolation does not currently exist in the engine.

OpenRewrite 8 provides `MavenResolutionResult` per module, which is what this
singleton approximates.

## Multi-module handling

There is no persistent module graph. `ProjectContext.getModules()` (`:72`)
derives modules on every call by filtering build files and constructing fresh
`Module` objects with empty names.

`Module` (`build/api/Module.java:45`) is a transient view.
`getMainJavaSources()` (`:76`) returns `List.of()` behind a `// FIXME`
(`:77`, `:82`). Search methods filter by absolute-path string prefix
(`:109`, `:171`, `:175-190`), and `ImmutableFilteringProjectResourceSet`
(`:214`) is a private inner subclass overriding parts of
`ProjectResourceSet`, described in its own comment (`:211`) as "quite hacky".

Graph queries live in `ApplicationModules.java`.
`getTopmostApplicationModules()` (`:113`) reconstructs the graph using
`noOtherPomDependsOn()` (`:166`), which scans declared dependencies —
re-deriving information `MavenResolutionResult.getModules()` already carries.
`ApplicationModules.getModules(Module)` (`:81`) does read that marker, so the
two paths coexist.

`MultiModuleAwareAction` / `MultiModuleHandler` is the only multi-module
dispatch mechanism and is opt-in per action.

The incremental multi-module parsing work is documented separately in
[`../multi-module/incremental-parsing-notes.md`](../multi-module/incremental-parsing-notes.md)
and tracked as issue #13.

## Design issues

- **`OpenRewriteMavenBuildFile.java` is 848 lines.** It mixes resource holder,
  Maven model, refactoring driver and classpath resolver, and embeds a
  `RefreshPomModel extends Recipe` (`:63`) used to force pom re-resolution
  after every mutation (`:445`).
- **`ClasspathRegistry` is a static mutable singleton.** Not testable in
  isolation, not parallelisable, no per-module scope.
- **Reflection into OpenRewrite internals in three places:**
  `RewriteRecipeLoader.java:79-83` (private `DeclarativeRecipe#initialize`,
  via `ReflectionUtils.findMethod` + `makeAccessible`), and
  `MavenMojoProjectParserPrivateMethods.java:88-110` and `:125`. Each is a
  standing break risk across OpenRewrite versions; issue #7 names one of them.
- **Duplicated abstractions:** two parsers, two `ResourceParser`s, two
  `RewriteMavenArtifactDownloader`s, SBM `Module`/`ApplicationModules`
  against OpenRewrite `MavenResolutionResult`.
- **Dead or stale code:** `RewriteRecipeLoader.loadRecipes()` returning empty
  by design; `RecipesBuilder.beanRecipes` marked `forRemoval` but still
  present (`:35`); commented-out blocks in `ProjectResourceSet.java:68-75`,
  `ClasspathRegistry.java:60-67`, `SourceFileParser.java:81-86`.
- **Ad hoc scope management:** `RecipesBuilder` caches recipes in a mutable
  field with no invalidation (`:46`), while `ApplyCommand` clears an
  `ExecutionScope` manually in a `finally` block (`ApplyCommand.java:83`).

## What is not duplication

The following are SBM's own contribution and are not superseded by
OpenRewrite 8:

- The recipe catalogue — 37 YAML recipes and 72 actions encoding
  JEE, Mule and Spring Boot migration knowledge.
- The imperative authoring API that those actions are written against
  (finders, typed resource facades, intention-level mutation methods). See
  [`recipe-authoring-direction.md`](recipe-authoring-direction.md).
- The scan / list-applicable-recipes / apply interaction model and its
  precondition checks and reports.
- The multi-module incremental parsing research in
  [`../multi-module/incremental-parsing-notes.md`](../multi-module/incremental-parsing-notes.md).

## Suggested sequence

Ordered by leverage, not by size. Items marked with an issue number already
have one.

1. Establish whether OpenRewrite exposes a public way to inject pre-computed
   type information into a `JavaParser` (`TypeTable` / `JavaType.ShallowClass`).
   This gates the incremental-parsing design; see the open questions in
   `incremental-parsing-notes.md`.
2. Settle the recipe authoring model
   ([`recipe-authoring-direction.md`](recipe-authoring-direction.md)).
3. Replace `ClasspathRegistry` with per-module `MavenResolutionResult`.
   Prerequisite for 6.
4. Remove the legacy parser and `components/sbm-openrewrite` (issue #11).
5. Break up `OpenRewriteMavenBuildFile`; batch pom mutations instead of
   refreshing the model after each one.
6. Incremental multi-module parsing (issue #13), designed on the outcome of 1
   and 3.
7. Remove or isolate the three reflection bridges (partly issue #7).
8. Make OpenRewrite recipes visible in the recipe list via namespacing rather
   than the current blanket empty return.
