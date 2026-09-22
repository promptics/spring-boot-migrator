# Recipe Authoring Direction

Working document. Records the question of whether SBM's imperative `Action`
model should be replaced by OpenRewrite's visitor and `ScanningRecipe` model,
the evidence considered, and the direction chosen.

Companion to [`architecture-review.md`](architecture-review.md).

## The question

The architecture review finds that much of SBM's engine duplicates capability
OpenRewrite 8 now provides. A first reading of that finding suggests removing
`Action` as well and having recipe authors write `ScanningRecipe` and
`TreeVisitor` implementations directly.

That reading is too broad. `Action` and the API it is written against are not
the same thing as the mutation engine underneath them, and the evidence points
in opposite directions for the two.

## Evidence

### Actions that stay within the authoring API are short and work

`ReplaceJavaxWithJakartaAction`
(`components/sbm-recipes-boot-upgrade/src/main/java/org/springframework/sbm/boot/upgrade/common/actions/ReplaceJavaxWithJakartaAction.java`,
38 lines total) is eight lines of logic:

```java
context.getProjectJavaSources()
        .asStream()
        .forEach(js -> javaxPackagePatterns.stream()
            .filter(js::hasImportStartingWith)
            .forEach(p -> js.replaceImport(p, p.replace("javax.", "jakarta."))));
```

`Boot_27_30_JmxEndpointExposureAction` (44 lines) reads a Spring Boot
properties file in the topmost modules and sets a property if absent. Both
read top to bottom and require no knowledge of recipe cycles, cursors or
scanning phases.

### The Action that broke is the one that left the authoring API

`MigrateJndiLookup`
(`components/sbm-recipes-jee-to-boot/src/main/java/org/springframework/sbm/jee/ejb/actions/MigrateJndiLookup.java`,
191 lines) declares an inner `MigrateJndiLookupVisitor extends JavaIsoVisitor`
and manages state across visits. Five of its tests are disabled, with the
recorded reason (issue #19):

> Migrate JNDI lookup needs refactoring. Test is affected by concurrency
> settings in Rewrite visitors through `Recipe.run(...)`.

The failure is not that an imperative abstraction leaked. It is that dropping
out of the authoring API into OpenRewrite's visitor API means inheriting
OpenRewrite's contracts — recipe cycles, cursor handling, parallel scheduling,
`super.visitX` discipline — without the authoring layer mediating them.

Related: `ReplaceStaticFieldAccessVisitor` (issue #14) broke for a similar
reason when OpenRewrite 8.80.1 changed how static-import accesses are
represented.

## Two layers, two verdicts

| Layer | Examples | Verdict |
|---|---|---|
| Authoring API | finders, typed resource facades, `ProjectContext` accessors, `js.replaceImport(...)`, `p.setProperty(...)` | Keep and invest |
| Mutation engine | `ProjectResourceSet` mutable box, dirty-tracking via `printAll()` comparison, merge-by-absolute-path, static `ClasspathRegistry` | Replace with OpenRewrite-native |

The duplication table in `architecture-review.md` applies to the second row
only.

## Direction

Keep imperative authoring. Change what running an action *does*: rather than
mutating a parallel data structure and merging results back, record the
requested operations and emit a composed OpenRewrite `Recipe`, which
OpenRewrite then executes.

Sketch:

```java
class JmxEndpointExposure extends SbmRecipe {
    void define(Project p) {
        p.springProperties()
         .inTopmostModules()
         .ensureProperty("management.endpoints.jmx.exposure.include", "*");
    }
}
```

Two things matter in that sketch.

**The verb is at the level of migration intent.** `ensureProperty`, not
`getProperty` followed by a conditional `setProperty`. Read-then-decide-then-write
is precisely what `ScanningRecipe` exists for, so an intention-level verb can
hide the scan/generate split instead of making each author manage it.

**Operations are recorded, not executed.** The result is a recipe tree, which
OpenRewrite runs with its own semantics for cycles, scanning, parallelism and
LST identity.

### What this gives

- Imperative, top-to-bottom authoring is preserved.
- Execution is OpenRewrite-native, so the mutable wrapper, the
  `printAll()`-based dirty check and merge-by-path all become unnecessary.
- Composition comes from the emitted tree rather than from a parallel recipe
  model, which answers the missing nesting noted in the architecture review.
- Recorded operations can be validated before anything runs.
- State held across cycles — the `MigrateJndiLookup` failure mode — becomes
  structurally impossible, because there is no place to put it.

The pattern is a familiar one: author at the intent level, compile to the
engine's native model.

### Escape hatch

Some transformations genuinely need cursor context and type-attribution
walking. There should be a documented way to drop to a raw OpenRewrite recipe,
clearly marked as taking on OpenRewrite's contracts directly. The
`MigrateJndiLookup` class of transformation is the expected user.

## Note on generated recipes

An additional argument for a narrow, intention-level, recorded API is that it
constrains automatically generated recipe code more tightly than either of the
alternatives.

| Property | Current `Action` API | Raw OpenRewrite visitors | Recorded intent API |
|---|---|---|---|
| Small verb vocabulary | yes | no | yes |
| Local reasoning, no framework lifecycle | yes | no | yes |
| Wrong code fails to compile | partly | no | yes, with stronger types |
| Errors surface at validation, not at runtime | no | no | yes |
| Composes without surprising interaction | no | no | yes, explicit tree |

The current API already wins the first two rows against raw visitors. It
loses the last two, and those are where mistakes are hardest to catch by
inspection. Recording operations makes them checkable before execution.

## Consequences for sequencing

If this direction is taken, settling the authoring API moves earlier relative
to the engine work in `architecture-review.md`, because the API determines
what the engine underneath has to expose.

## Open questions

- [ ] Does the intention-level vocabulary hold up across the existing 72
      actions, or does a significant fraction need the escape hatch?
- [ ] How are conditions expressed in a recorded model — as recorded
      predicates compiled to `Preconditions`, or evaluated during a scan
      phase?
- [ ] What is the migration path for the existing actions? Most are short
      enough to port directly; the larger ones need individual assessment.
- [ ] Does the YAML recipe dialect survive unchanged as a front-end, or does
      it need to grow composition syntax to match the emitted tree?
- [ ] How do recorded operations report progress, given actions currently
      publish Spring application events during `apply`?
