# Stage 2 — Migrate target-tracking visitors to `ScanningRecipe` / `Preconditions`

> Tracking document for the second stage of the OpenRewrite 7 → 8 migration.
> Stage 1 (the `OnceTargetJavaVisitor` crutch) is already on the branch.

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

Stored-id lookups into the AST — evaluate per site whether the new
id-preserving semantics are intended or subtly broken:

- `components/sbm-recipes-jee-to-boot/.../MigrateJndiLookup.java` (block containment check, probably fine)
- `components/jaxrs-recipes/.../MigrateJndiLookup.java` (same, copy)
- `components/sbm-core/.../java/impl/OpenRewriteType.java`
- `components/sbm-core/.../java/impl/OpenRewriteMember.java`
- `components/sbm-core/.../java/impl/OpenRewriteMethod.java`
- `components/sbm-core/.../java/migration/visitor/VisitorUtils.java`

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

- [ ] Primary (A + B) call sites no longer hold a raw target `J` reference
- [ ] Transformation is demonstrably idempotent on a test fixture (applying
      twice changes nothing the second time)
- [ ] `OnceTargetJavaVisitor` from stage 1 is removed, or marked
      `@Deprecated(forRemoval = true)` with a migration note in the javadoc
- [ ] Secondary (D) sites are reviewed and either kept with an explanatory
      comment or migrated

## Non-goals

- Do **not** add new call sites using `OnceTargetJavaVisitor`. New visitors
  should be written directly against `ScanningRecipe` / `Preconditions`.
- Do **not** change OR-version semantics — the id-preserving behavior is the
  public API of OR 8 and will stay.
