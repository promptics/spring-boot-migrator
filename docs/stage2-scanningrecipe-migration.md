# Stage 2 — Migrate target-tracking visitors to `ScanningRecipe` / `Preconditions`

> Tracking document for the second stage of the OpenRewrite 7 → 8 migration.
> Stage 1 (the `OnceTargetJavaVisitor` crutch) **and** the primary part of
> stage 2 are now on the branch. This document stays for the remaining
> secondary (Kategorie D) scope.

## Background

OpenRewrite 8.1+ preserves the `UUID` of an element across `tree.withX()`
operations. This is intentional (stable `SearchResult` propagation, cursor
tracking, cross-revision diffing) and will **not** change in future versions.

The pre-OR-8 SBM pattern

```java
public class SomeVisitor extends JavaIsoVisitor<ExecutionContext> {
    private final J target;

    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext p) {
        cd = super.visitClassDeclaration(cd, p);
        if (target.getId().equals(cd.getId())) {    // used to match exactly once
            cd = ...apply transformation...;        // now matches again after transform
        }
        return cd;
    }
}
```

no longer matches exactly once: the transformed node keeps the same id, so the
next visit pass re-applies the transformation. The `targetVisited` boolean
flag, introduced on the revamp branch and centralized in stage 1 as the
`OnceTargetJavaVisitor` helper, is a workaround, not a fix.

## Stage 2 goal

Replace constructor-based target tracking with one of the OR-8-native patterns:

1. **`ScanningRecipe<State>`** — two phases: `getScanner(State)` accumulates
   target element ids (or `SearchResult` markers) into `State`, `getVisitor(State)`
   transforms only the accumulated targets.
2. **`Preconditions.check(scanner, main)`** — gate that runs the main visitor
   only when the scanner matched.
3. **`SearchResult` markers** — scan-phase visitor adds markers, transform-phase
   visitor reads them via `getMarkers().findFirst(SearchResult.class)`.
4. **`Cursor.putMessage()` / `getNearestMessage()`** — scoped state in the
   cursor stack when the transformation is local to a sub-tree.

## Scope

### Primary (Kategorie A + B from the analysis)

Constructor-based target tracking — migrate first:

- `components/sbm-openrewrite/.../AddAnnotationVisitor.java`
- `components/sbm-openrewrite/.../AddOrReplaceAnnotationAttribute.java`
- `components/sbm-openrewrite/.../RemoveAnnotationVisitor.java`

### Secondary (Kategorie D)

Reviewed. All seven sites are **correct** under OR 8 id-preserving semantics:

| Site | Purpose | OR 8 verdict |
|---|---|---|
| `OpenRewriteType.getClassDeclaration()` | id → ClassDeclaration lookup inside the current SourceFile | safe — lookup, benefits from id stability |
| `OpenRewriteMember.getVariableDeclarations()` | same for VariableDeclarations | safe |
| `OpenRewriteMethod.getMethodDecl()` | same for MethodDeclaration | safe |
| `VisitorUtils.AddTemplateMark.postVisit` | add marker when tree id matches | safe — `computeByType(..., (m1, m2) -> m2)` replaces the marker with itself on a re-visit |
| `VisitorUtils` `ChangeMethodReturnTypeRecipe` predicate | id-based method predicate | safe — return-type change is self-stabilizing |
| `MigrateJndiLookup` x2 `removeFromMethodBlock` | block-containment check by id | safe — this is exactly what id-preserving `with*()` guarantees |

The four less-obvious sites have explanatory comments in the code. The three
`get*()` lookups in `sbm-core` are self-explanatory and left as-is.

### Out of scope (Kategorie C)

SourceFile-level id lookups in the refactoring engine are working as intended
under the new semantics:

- `JavaGlobalRefactoringImpl`, `MavenBuildFileRefactoring`, `OpenRewriteRecipeJavaSearch`, `ProjectJavaSourcesImpl`

## API impact

Every primary visitor is currently constructed by SBM callers with the raw
target `J` node. Removing that parameter in favor of a scan phase is a
breaking change for any external recipe author that extends these visitors.
Coordinate with a minor version bump.

## Acceptance criteria

- [x] Primary (A + B) call sites no longer hold a raw target `J` reference
      (moved to `ScanningRecipe<Set<UUID>>` in `AddAnnotationRecipe`,
      `RemoveAnnotationRecipe`, `AddOrReplaceAnnotationAttributeRecipe`)
- [x] `OnceTargetJavaVisitor` + the three old visitor classes are marked
      `@Deprecated(forRemoval = true)` with a migration pointer to the
      recipe variants
- [ ] Transformation is demonstrably idempotent on a test fixture (applying
      twice changes nothing the second time) — deferred until the OR-8 test
      infrastructure migration is done (`Recipe.run(LargeSourceSet)`,
      `Stream<SourceFile>` vs `List<SourceFile>`)
- [x] Secondary (D) sites are reviewed and either kept with an explanatory
      comment or migrated (all seven are safe under OR 8 semantics — see table
      above)

## Pattern used

All three recipes follow the same two-phase shape:

```java
public class SomeRecipe extends ScanningRecipe<Set<UUID>> {
    private final UUID targetId;

    public Set<UUID> getInitialValue(ExecutionContext ctx) { return new HashSet<>(); }

    public TreeVisitor<?, ExecutionContext> getScanner(Set<UUID> acc) {
        return new JavaIsoVisitor<>() {
            // record the target id into acc when the node is found
        };
    }

    public TreeVisitor<?, ExecutionContext> getVisitor(Set<UUID> acc) {
        return new JavaIsoVisitor<>() {
            // acc.remove(node.getId()) returns true exactly once per run;
            // any subsequent visit of the transformed node is a no-op.
        };
    }
}
```

The critical property is `acc.remove(node.getId())` — it returns `true` only
the first time and removes the id, so the re-visit caused by OR 8's
id-preserving `with*()` semantics doesn't re-trigger the transformation.

## Non-goals

- Do **not** add new call sites using `OnceTargetJavaVisitor`. New visitors
  should be written directly against `ScanningRecipe` / `Preconditions`.
- Do **not** change OR-version semantics — the id-preserving behavior is the
  public API of OR 8 and will stay.
