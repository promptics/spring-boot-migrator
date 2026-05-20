---
name: sbm-dep-kb
description: The dependency knowledge-base provider contract. Defines the four operations (lookup, analyzeFrontier, requestResearch, compose) that callers use to read and write KB records. Backends are pluggable - default is the in-repo local filesystem; future backends include a hosted MCP server and an on-prem snapshot. Use this skill when manually inspecting cached records, adding records by hand, validating the index, or pointing the workflow at a different backend. Most analysis callers should go through sbm-dep-tree, which talks to this contract.
---

# sbm-dep-kb

The KB provider contract. Every higher-level skill (`sbm-dep-tree`, `sbm-impact-map`) reads and writes through these four operations and **must not** assume any particular backend. Swapping backends is a config change, not a code change.

## Why this is a contract, not a filesystem path

The MVP backend is the in-repo local filesystem. Future backends:

- **Hosted MCP** — operations resolve to tool calls against a remote MCP server. The KB never lives on disk; cache hits are network calls.
- **On-prem MCP** — same MCP tool surface, but the server runs inside the customer's VPC and reads from a signed snapshot pulled from a CDN on a schedule.

All three backends honor the **same contract below**. Callers should never branch on "which backend is configured" — that's the provider's job.

## Configuration

The active backend is selected by `dep-kb/provider.json` (read first) or the `SBM_DEPKB_PROVIDER` environment variable (override). Schema:

```json
{
  "backend": "local | hosted-mcp | onprem-mcp",
  "local": {
    "root": "components/sbm-recipes-boot-upgrade/src/main/resources/dep-kb"
  },
  "hostedMcp": {
    "endpoint": "https://mcp.example.com",
    "tool_prefix": "mcp__depkb__"
  },
  "onpremMcp": {
    "tool_prefix": "mcp__depkb__"
  },
  "anonymous": false,
  "stalenessDays": 180
}
```

When `anonymous: true`, callers MUST strip everything except `{groupId, artifactId, fromVersion, toVersion}` before any operation that goes over the network. App names, file paths, FQCNs from the customer's source — none of it crosses the boundary.

## The four operations

### 1. `lookup(groupId, artifactId, fromVersion, toVersion) -> Record | null`
Returns a single KB record for one upgrade pair, or `null` if absent. `null` is the signal that `requestResearch` is needed.

**Local backend**: read `<root>/<groupId>/<artifactId>/<from>__<to>.json`. Parse, validate against `schema.json`, return. Missing file → null.

**MCP backends**: call the `lookup_upgrade_impact` MCP tool with `{groupId, artifactId, fromVersion, toVersion}`. Return the record or null. Treat tool errors as null but surface a warning — do not silently fall through.

### 2. `analyzeFrontier(deps: [{g, a, from, to}]) -> {cached: Record[], missing: {g,a,from,to}[]}`
Batch form of `lookup`. The hot path — `sbm-dep-tree` calls this exactly once per analysis.

**Local backend**: iterate `deps`, call `lookup` for each, partition.

**MCP backends**: single `analyze_frontier` tool call with the whole list. Saves N round-trips.

### 3. `requestResearch({g, a, from, to}) -> Record`
Produces a record for a missing pair. The caller does not care whether this means "spawn a dep-researcher sub-agent locally" or "enqueue a job on the hosted server."

**Local backend**: spawn the `dep-researcher` sub-agent (see `.claude/agents/dep-researcher.md`). Validate its return against `schema.json`. Write to disk (see *Write* below). Return.

**Hosted MCP**: call the `request_research` tool. Server-side researchers run there, not in the customer's Claude session — saves their tokens. Tool blocks until done or returns a job ID the caller polls.

**On-prem MCP**: not supported in the default config — the on-prem snapshot is read-only between sync windows. If the caller insists, fall through to the local sub-agent path and write the record into a local override directory (configured by `onpremMcp.localOverrideRoot`). Override records get synced upstream on the next outbound sync only if the customer has opted in to telemetry.

### 4. `compose(g, a, fromVersion, toVersion) -> Record`
Multi-hop composition. If a direct record for `from → to` does not exist but a chain of intermediate records does (e.g. 2.5→2.6, 2.6→2.7, 2.7→3.0 covers 2.5→3.0), merge them by:

- Union of `removed`, `deprecated`, `signatureChanges`, `behaviorChanges`, `configKeys`.
- Deduplicate by `symbol` (or `key` for `configKeys`); when duplicates differ, prefer the entry from the later hop and keep the earlier `sourceUrl` in a `priorSourceUrls` field.
- Union of `sources`, preserving order.
- `transitiveImpact`: concat then dedupe by description prefix.
- `requiredJavaVersion`: take the maximum.
- `confidence`: minimum of the chain.

**Local backend**: pure local compute, no I/O after the lookups.

**MCP backends**: single `compose_path` tool call. Server-side composition is cheaper and ensures every customer gets the same answer.

## Write path (local backend only)

`requestResearch` and any manual record addition go through here:

1. Validate the record against `schema.json`. If invalid, do NOT write — surface the error.
2. Write to `<root>/<groupId>/<artifactId>/<from>__<to>.json`.
3. Append to `<root>/index.json`:
   ```json
   {
     "groupId": "...",
     "artifactId": "...",
     "fromVersion": "...",
     "toVersion": "...",
     "researchedAt": "<ISO-8601>",
     "sources": ["<url1>", "..."],
     "recordPath": "<groupId>/<artifactId>/<from>__<to>.json"
   }
   ```
4. The KB is committed to the repo — treat writes as code. PRs that add records must include the record file and the index entry in the same commit.

MCP backends do not expose a write operation to the client. Writes happen server-side as a side effect of `requestResearch`.

## Invalidation / staleness

Records carry a `researchedAt` timestamp. If `now - researchedAt > stalenessDays` (default 180), the next `lookup` should return the stale record but mark it for refresh. The orchestrator (`sbm-dep-tree`) decides whether to re-research immediately or defer.

## Notes for skill authors

- **Never read files under `dep-kb/` directly from another skill.** Go through `lookup` / `analyzeFrontier`. The whole point of this contract is that a future backend swap is invisible to you.
- **Never branch on `provider.json` from another skill.** If you need backend-specific behavior, push it into a new operation here.
- **Do not edit records to "fix" findings the app team disagrees with.** Open an issue or PR. The KB reflects upstream reality, not project policy.
