# OpenRewrite Dependency Licensing

Working document. Records the licence status of the OpenRewrite artifacts SBM
depends on, which versions SBM currently pins, and why this matters when
choosing target versions.

Relevant to issue #7 (adopting `rewrite-recipe-bom`), which proposes moving
the recipe libraries onto a BOM-managed version.

Verified against the upstream repositories. Licences change over time; check
the `<licenses>` block of a published POM, or the repository's licence file,
before relying on anything here.

## Current status

### Apache License 2.0

| Artifact | Repository |
|---|---|
| `org.openrewrite:rewrite-core`, `rewrite-java`, `rewrite-maven`, `rewrite-xml`, `rewrite-yaml`, `rewrite-properties`, `rewrite-gradle` and the rest of the core | `openrewrite/rewrite` |
| `org.openrewrite.recipe:rewrite-recipe-bom` | `openrewrite/rewrite-recipe-bom` |
| `org.openrewrite.recipe:rewrite-java-dependencies` | `openrewrite/rewrite-java-dependencies` |

The core parsers, LST model and visitor APIs are Apache 2.0 and have remained
so.

### Moderne Source Available License

| Artifact | Apache through | Source-available from |
|---|---|---|
| `org.openrewrite.recipe:rewrite-spring` | 5.5.0 | 6.0.0 |
| `org.openrewrite.recipe:rewrite-migrate-java` | 2.5.0 | 3.0.0 |
| `org.openrewrite.recipe:rewrite-testing-frameworks` | — | current |
| `org.openrewrite.recipe:rewrite-static-analysis` | — | current |
| `org.openrewrite.recipe:rewrite-logging-frameworks` | — | current |

In these repositories the top-level `LICENSE.md` contains a single line
pointing at `LICENSE/moderne-source-available-license.md`.

The Moderne Source Available License is not an OSI-approved open source
licence. It grants a non-exclusive, royalty-free, worldwide,
non-sublicensable, non-transferable licence to "use, copy, distribute
internally, and prepare derivative works", and then limits that grant:

> You may not and will not make the functionality of the Software or a
> Modified version available to third parties as a service or distribute the
> Software or a Modified version in a manner that makes the functionality of
> the Software directly or indirectly available to third parties.

The same clause defines that as including "offering a product or service, the
value of which derives to any extent from the value of the Software or
Modified version, or offering a product or service that accomplishes for users
any purpose of the Software or Modified version".

Anyone redistributing a build of SBM, or offering it to others in any form,
should read the full licence text rather than this summary.

## What SBM pins today

```
openrewrite (core)          8.80.1   Apache 2.0
rewrite-migrate-java        2.0.6    Apache 2.0 (pre-cutover)
rewrite-spring              5.0.5    Apache 2.0 (pre-cutover), in sbm-support-rewrite
```

Both recipe libraries are on versions released before the licence change, so
the current dependency set is Apache 2.0 throughout.

## Binary compatibility

The pre-cutover recipe libraries were built against much older core versions:

| Artifact | Version | Built against core |
|---|---|---|
| `rewrite-spring` | 5.5.0 | 8.17.1 |
| `rewrite-migrate-java` | 2.5.0 | 8.11.5 |

SBM runs core 8.80.1. Staying on the pre-cutover recipe libraries while moving
the core forward is therefore not guaranteed to keep working: any recipe that
calls into a core API removed since 8.11.x or 8.17.x will fail at runtime.
That it currently works is an empirical property of the specific recipes SBM
invokes, not a contract.

This makes "pin the recipe libraries and bump only the core" a fragile
position in the long run, independent of any licensing consideration.

## How much SBM depends on these recipes

Twelve recipe classes from `rewrite-spring` and `rewrite-migrate-java` are
referenced across the codebase.

From `org.openrewrite.java.spring.boot3`:
`RemoveConstructorBindingAnnotation`, `ImprovedConstructorBinding`,
`Micrometer_3_0`, `SpringBootPropertiesManual_2_7`,
`SpringBootPropertiesManual_2_7_Removed`, `data.UpgradeSpringData30`,
`data.UpgradeSpringCloudDependencies`, `data.UpgradeSSpringBoot3Dependencies`.

From `org.openrewrite.java.spring.boot2`:
`SpringBoot1To2Migration`, `SpringBoot2JUnit4to5Migration`,
`SpringBootPropertiesManual_2_7`.

From `org.openrewrite.java.migrate.jakarta`:
`JavaxMigrationToJakarta`.

Only one is referenced by a Java `import`
(`ConstructorBindingHelper.java:22`). One more is looked up by name at
`OrRecipesConfig.java:70`. The remainder appear as recipe names in YAML.

Production references are concentrated in
`components/sbm-recipes-boot-upgrade/src/main/resources/recipes/` — six files
under `27_30/migration/` plus `boot-2.7-3.0-dependency-version-update.yaml`.
The rest are test-side.

Note that `components/sbm-support-boot/src/main/java/org/openrewrite/java/spring/SpringBeanDeclarationFinder.java`
declares a package under `org.openrewrite.java.spring` but is SBM's own
Apache-licensed code and imports only core types. Moving it to an SBM-owned
package would avoid the confusion.

## Bearing on issue #7

Issue #7 proposes adopting `rewrite-recipe-bom` 3.29.0 as the single source
of truth for the recipe libraries. The BOM itself is Apache 2.0, but it
manages versions of `rewrite-spring` and `rewrite-migrate-java` that are past
the cutover. Importing the BOM and taking its managed versions therefore
changes the licence terms that apply to those artifacts.

Options, without recommending one:

- Adopt the BOM and take the source-available versions, accepting the
  redistribution limits described above.
- Adopt the BOM but override the two recipe-library versions to stay on the
  Apache-licensed line, accepting the binary-compatibility risk noted above
  and the maintenance cost of fighting the BOM on every bump.
- Depend on the core artifacts directly without the BOM, and provide the
  twelve referenced recipes within SBM, built on the Apache-licensed core.

The third option removes both the licensing question and the binary
compatibility risk, at the cost of implementing and maintaining those recipes.
Given the small number involved and their concentration in one directory, the
scope is bounded; two of them (`SpringBoot1To2Migration`,
`SpringBoot2JUnit4to5Migration`) may simply be droppable.

## Open items

- [ ] Decide which of the three options in the previous section to take, and
      record the decision on issue #7.
- [ ] Run a full dependency licence audit
      (`mvn license:aggregate-add-third-party`) and check the output for any
      artifact whose licence is not what this document assumes.
- [ ] Move `SpringBeanDeclarationFinder` out of the `org.openrewrite.java.spring`
      package.
- [ ] Re-check this document whenever the OpenRewrite version is bumped; the
      cutover versions recorded here are a point-in-time observation.
