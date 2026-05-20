# MCP backend contract (forward-looking)

The `sbm-dep-kb` skill documents four provider operations. The `local` backend is implemented today (read/write directly against this directory). This file specifies the **MCP tool surface** that the hosted and on-prem backends must expose so that switching `provider.backend` from `local` to `hosted-mcp` or `onprem-mcp` is a config change with no skill changes.

## Tool surface

All tools are namespaced under `mcp__depkb__` (configurable via `provider.hostedMcp.tool_prefix`).

### `mcp__depkb__lookup_upgrade_impact`
```
input:  { groupId, artifactId, fromVersion, toVersion }
output: Record | null    // conforms to schema.json
```
Single-pair lookup. Returns `null` for cache miss.

### `mcp__depkb__analyze_frontier`
```
input:  { deps: [{ groupId, artifactId, fromVersion, toVersion }, ...] }
output: { cached: Record[], missing: [{ groupId, artifactId, fromVersion, toVersion }, ...] }
```
Batch lookup. Hot path — invoked once per analysis.

### `mcp__depkb__request_research`
```
input:  { groupId, artifactId, fromVersion, toVersion }
output: Record    // produced by server-side researchers
```
May block; servers should return promptly with an async job ID if research takes >30s, and the caller polls a `get_research_status` tool. Persistence (writing the record into the hosted KB) is a server-side side effect — clients receive the record but do not write anything locally.

### `mcp__depkb__compose_path`
```
input:  { groupId, artifactId, fromVersion, toVersion }
output: Record    // merged from intermediate hops, or null if no chain exists
```
Multi-hop composition (e.g. 2.5→3.0 from 2.5→2.6, 2.6→2.7, 2.7→3.0). Composition rules per the `sbm-dep-kb` skill.

## What the server sees vs. never sees

The MCP server only ever receives the four tuple fields above and (optionally) the customer's license key in the auth header. It must **never**:

- Accept FQCNs from the customer's source code.
- Accept file paths, app names, or repository URLs.
- Log request bodies beyond the tuple keys (counts only, for prioritization).

The `sbm-impact-map` skill enforces the client-side half of this: it performs the symbol-usage join locally and only sends frontier triples upstream.

## Auth

- Header: `Authorization: Bearer <license-key>`.
- On-prem: license key is checked at server startup against a signed manifest; no per-request remote call.
- Hosted: per-request validation; rate-limited per key.

## Snapshot sync (on-prem only)

The on-prem variant ships a `sbm-kb sync` CLI that pulls signed snapshots outbound from a CDN, writes them into the container's KB volume, and verifies signatures with a pinned public key. Skill-level operations are unchanged; only the data freshness changes.

## What is **not** in scope

- No write tool over MCP. Clients cannot push records to the hosted KB. Records are produced by server-side researchers triggered via `request_research` (hosted) or pulled in via signed snapshots (on-prem).
- No symbol-level queries. Impact mapping stays client-side, by design — both for privacy (Tier 2 customers) and to keep the server stateless w.r.t. customer code.

## Status

Specification only. No server implementation exists in this repository — this file exists so the skills (`sbm-dep-tree`, `sbm-impact-map`, `sbm-dep-kb`) have a stable target to write against the day a backend ships.
