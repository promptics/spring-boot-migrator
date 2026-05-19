---
name: sbm-dep-kb
description: Read/write helper for the dependency knowledge base under components/sbm-recipes-boot-upgrade/src/main/resources/dep-kb/. Use when manually inspecting cached records, adding a record by hand, validating the index, or pruning stale entries. Most callers should go through sbm-dep-tree instead.
---

# sbm-dep-kb

Direct interface to the dep-kb cache. Lower-level than `sbm-dep-tree` — use this when you need to inspect or curate the KB itself rather than run an analysis.

## KB layout

```
components/sbm-recipes-boot-upgrade/src/main/resources/dep-kb/
├── README.md            # human overview
├── schema.json          # JSON Schema for records
├── index.json           # flat list of all records (for fast lookup)
└── <groupId>/<artifactId>/<fromVersion>__<toVersion>.json
```

- `groupId` directory uses dots as-is (e.g. `org.springframework.boot/`).
- Version pair is double-underscore separated: `2.7.18__3.0.0.json`.
- One record per upgrade *pair*, never per artifact. Records compose: a 2.6→3.0 analysis merges the 2.6→2.7 and 2.7→3.0 records.

## Common operations

### Lookup
- Path: `dep-kb/<groupId>/<artifactId>/<from>__<to>.json`.
- Returns null if absent — that's the signal to dispatch a `dep-researcher`.

### Write
- After `dep-researcher` returns a record, write it to the canonical path.
- Append to `index.json`:
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
- Validate against `schema.json` before writing. If validation fails, do NOT write — surface the error.

### Invalidate / refresh
- If a record's `researchedAt` is older than the configured staleness threshold (default: 180 days), the next `sbm-dep-tree` run should re-research.
- Manual invalidation: delete the record file and the matching `index.json` entry.

### Compose
- For an N-step upgrade (e.g. 2.5 → 3.0), merge intermediate records by union-ing `removed`, `deprecated`, `signatureChanges`, `behaviorChanges`, and `configKeys`. Deduplicate by symbol/key. Preserve all source URLs.

## Notes
- The KB is committed to the repo. Treat writes as code: review the JSON before committing, especially for high-impact deps like `spring-boot` or `spring-framework`.
- Do not edit records to "fix" findings the app team disagrees with — open an issue or PR instead. The KB reflects upstream reality, not project policy.
