# Dependency Knowledge Base (dep-kb)

A community-extensible cache of per-dependency, per-version-pair upgrade impact records.

## Why this exists

Spring Boot upgrades touch dozens of transitive dependencies. Researching each one for every project is wasted work — the *library-level* diff between, say, `org.hibernate.orm:hibernate-core:5.6 → 6.1` is the same for every app. Caching that research here means:

- Subsequent upgrade analyses skip dependencies already covered.
- The cache compounds over time into a reusable knowledge base.
- App-specific impact (the `sbm-impact-map` step) is then just a join between this KB and the app's symbol-usage index.

## Layout

```
dep-kb/
├── README.md            ← you are here
├── schema.json          ← JSON Schema for records
├── index.json           ← flat list of all records (lookup index)
└── <groupId>/<artifactId>/<fromVersion>__<toVersion>.json
```

- Group IDs use dots as directory names (e.g. `org.springframework.boot/`).
- Version pairs are double-underscore separated: `2.7.18__3.0.0.json`.
- One record per upgrade pair — **records compose**, so 2.6→3.0 is the union of 2.6→2.7 and 2.7→3.0.

## How records are produced

Records are written by the `dep-researcher` sub-agent (see `.claude/agents/dep-researcher.md`), orchestrated by the `sbm-dep-tree` skill. The agent consults release notes, migration guides, and the GitHub compare API, then emits a record validated against `schema.json`.

## How to contribute

1. Add or update a record under the correct `<groupId>/<artifactId>/<from>__<to>.json` path.
2. Validate it against `schema.json` (any JSON Schema validator works).
3. Append an entry to `index.json`.
4. Open a PR. Every entry must cite its sources — records without citations will be rejected.

## Staleness

Records carry a `researchedAt` timestamp. Records older than 180 days should be re-researched on the next analysis run. Re-research either updates the record in place (bump `researchedAt`, append new sources) or replaces it entirely if the upstream story has changed.
