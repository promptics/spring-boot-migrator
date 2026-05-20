---
name: sbm-impact-map
description: Cross-references the target app's source code against the dep-kb to estimate the actual (not maximum) blast radius of a dependency upgrade. Use after sbm-dep-tree has populated the KB, or when the user asks "which of these breaking changes actually affect my app?".
---

# sbm-impact-map

Per-app analyzer. Joins symbol usage in the app's source against the breaking-change symbols in dep-kb records.

## When to use
- After `sbm-dep-tree` has produced/refreshed KB records for an upgrade frontier.
- When the user wants the *actual* impact list, not the library-level diff.

## Inputs
- Path to the app's source root.
- Either a set of KB records (passed by `sbm-dep-tree`) or an upgrade frontier to look up.

## KB access
All KB reads go through the `sbm-dep-kb` provider contract (`lookup`, `analyzeFrontier`, `compose`). Do **not** read files under `dep-kb/` directly — the active backend may be local, hosted MCP, or on-prem MCP.

When `provider.anonymous` is `true`, this skill must perform the symbol-usage join **locally** — never send FQCNs, file paths, or app names to any remote operation. Only the upgrade frontier (groupId/artifactId/version triples) is allowed to cross the backend boundary.

## Steps

### 1. Build a symbol-usage index for the app
- Two strategies, in order of preference:
  - **Reuse SBM's parser**: the `sbm-core` module already parses projects into OpenRewrite LSTs. If invoked from a Java context, walk the LST to collect `FQCN → [{file, line}]`. This is the high-fidelity path.
  - **Lightweight fallback**: ripgrep the source tree for imports and qualified references — `rg -n --type java "<FQCN>"`. Lossy (misses re-exports, dynamic refs) but adequate for a first-pass report when the LST path isn't available.
- Index keys: fully-qualified class names, method signatures where available, and property keys (for `application.properties` / `application.yml`).

### 2. Aggregate breaking symbols from KB records
- For each record, collect:
  - `removed[]` → severity HARD
  - `signatureChanges[]` → severity LIKELY
  - `deprecated[]` → severity LATENT
  - `behaviorChanges[]` → severity BEHAVIOR (no symbol match — surface as advisories)
  - `configKeys[]` (property renames/removals) → check against `application.{properties,yml}` files

### 3. Join
- For each breaking symbol, look up usages in the index.
- Drop symbols with zero usages (these are part of the max blast radius but not the actual one).

### 4. Rank and report
Group findings by severity, then by dependency:

```
HARD BREAKS (used + removed)
  org.springframework.boot:spring-boot 2.7.18 → 3.0.0
    - javax.servlet.http.HttpServletRequest  (used in 12 files, 47 sites)
      src/main/java/com/acme/web/AuthFilter.java:23
      ...

LIKELY BREAKS (used + signature changed)
  ...

LATENT RISK (used + deprecated)
  ...

BEHAVIOR ADVISORIES (no symbol match — read carefully)
  ...
```

### 5. Suggest next actions
- Point at OpenRewrite recipes under `components/sbm-recipes-boot-upgrade/` that already cover specific findings, when known.
- For findings without an existing recipe, note them as candidates for new recipes.

## Output
A markdown report scoped to the app, with file:line citations the user can click through.

## Notes
- The ripgrep fallback will produce false positives on common names (e.g. `Optional`, `Filter`). Prefer the LST path for anything beyond a sketch.
- Property-key matching against `application.{properties,yml}` is a separate pass — don't try to handle it in the Java symbol index.
