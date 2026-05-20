---
name: sbm-dep-tree
description: Orchestrates the dependency blast-radius analysis for a Spring Boot upgrade. Use when the user asks to "analyze upgrade impact", "estimate Spring Boot upgrade risk", "find breaking changes for a version bump", or similar. Resolves the target app's dependency tree, computes the upgrade frontier (deltas between current and target BOMs), consults the in-repo dep-kb cache, and fans out dep-researcher sub-agents in parallel for any uncached pairs.
---

# sbm-dep-tree

Root orchestrator for the dependency upgrade analysis workflow.

## When to use
- "What breaks if I upgrade Spring Boot from X to Y in this app?"
- "Analyze the dependency tree and tell me the blast radius."
- "Run an upgrade impact analysis."

## Inputs
- Path to the target app (Maven or Gradle).
- Source version + target version (e.g. Spring Boot `2.7.18` → `3.0.0`).

## Steps

### 1. Resolve the current tree
- For Maven: `mvn -f <app>/pom.xml dependency:tree -DoutputType=text` (or `-DoutputType=json` if the plugin version supports it).
- For Gradle: use the project's wrapper with `./gradlew :dependencies --configuration runtimeClasspath`, or reuse the resolution code in `sbm-gradle-tooling-model` if invoked from a Java context.
- Parse into a flat list of `{groupId, artifactId, version}`.

### 2. Compute the target tree
- Resolve the target BOM (e.g. `org.springframework.boot:spring-boot-dependencies:<targetVersion>`) and produce the same flat list.
- For dependencies pinned by the user (not BOM-managed), keep their version unless the user passed an override.

### 3. Build the upgrade frontier
- Join the two lists on `groupId:artifactId`.
- Emit one tuple per dep where `fromVersion != toVersion`: `{groupId, artifactId, fromVersion, toVersion}`.
- This is the *max blast radius* set — bounded by the actual dep tree, not the universe of libraries.

### 4. Consult the KB (via the provider contract)
- All KB access goes through the `sbm-dep-kb` provider contract. Do **not** read files under `dep-kb/` directly — the active backend may be local, hosted MCP, or on-prem MCP, and the contract hides that.
- Call `analyzeFrontier(deps)` once with the full frontier. Receive `{cached, missing}`.
- For multi-hop pairs (e.g. 2.5→3.0 where only 2.5→2.6 / 2.6→2.7 / 2.7→3.0 exist), call `compose(g, a, from, to)` instead of `lookup` — the provider handles the merge.

### 5. Fill the `missing` set
- For each missing tuple, call `requestResearch({g, a, from, to})`.
- On the **local** backend this spawns the `dep-researcher` sub-agent. Spawn them in a single message (multiple Agent tool calls in one response = parallel execution); cap at ~8 in flight. If the frontier is larger, run successive batches.
- On **MCP** backends `requestResearch` blocks on the server-side researcher — fan out by issuing the tool calls in parallel exactly the same way; the provider does not care.
- Each call returns a single record conforming to `dep-kb/schema.json`. Persistence is the provider's job — do not write files yourself.

### 7. Hand off to sbm-impact-map (optional)
- If the user asked for the *app-specific* blast radius (not just the library-level diff), invoke the `sbm-impact-map` skill with:
  - The path to the app's sources.
  - The aggregated set of `removed`, `deprecated`, `signatureChanges` symbols from the merged KB records.

## Output
A markdown report with three sections:
1. **Frontier summary** — N deps changing, M newly researched, K from cache.
2. **Per-dependency findings** — one block per record, sorted by severity (`removed` > `signatureChanges` > `deprecated` > `behaviorChanges`).
3. **Next step** — suggest running `sbm-impact-map` against the app sources to narrow this to the actual symbols the app uses.

## Notes
- Never invoke this skill on an unbuilt project without checking — `mvn dependency:tree` needs the project to resolve. If the build fails, surface the error rather than guessing.
- The KB is intentionally pair-keyed (`from__to`), so 2.6→2.7 and 2.7→3.0 are separate composable records. Don't collapse them.
