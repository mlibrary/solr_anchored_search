# Migration Plan: Solr 8 → Solr 10.0.0

## Overview

This project is a Lucene/Solr `TokenFilter` plugin (`LeftAnchoredSearchFilter` and
`FullyAnchoredSearchFilter`) with their corresponding `TokenFilterFactory` classes.
The plugin is packaged as a JAR and dropped into Solr's classpath.

The codebase currently targets Solr 8 (pom defaults to 6.6.6 but the README describes
Solr 8 usage). Lucene 9 (bundled with Solr 9) and Lucene 10 (bundled with Solr 10)
introduced several **breaking changes** that affect this plugin.

---

## What Changed in Lucene/Solr Plugin APIs: 8 → 10

### 1. `TokenFilterFactory` package moved (Lucene 9 / LUCENE-9317)

| Before (Lucene 8, Solr 8)                           | After (Lucene 9+, Solr 9+)                   |
|-----------------------------------------------------|----------------------------------------------|
| `org.apache.lucene.analysis.util.TokenFilterFactory` | `org.apache.lucene.analysis.TokenFilterFactory` |
| `org.apache.lucene.analysis.util.TokenizerFactory`  | `org.apache.lucene.analysis.TokenizerFactory`   |
| `org.apache.lucene.analysis.util.CharFilterFactory` | `org.apache.lucene.analysis.CharFilterFactory`  |

Both `LeftAnchoredSearchFilterFactory` and `FullyAnchoredSearchFilterFactory` in this
project import from the old `util` package — **this is the primary compile-time break**.

### 2. Factories now require a static `NAME` field and a no-arg constructor (LUCENE-8778 / LUCENE-9281)

Lucene 9 switched from its own SPI loader to the standard `java.util.ServiceLoader`.
This requires:

- A `public static final String NAME = "..."` field on every factory. The name is the
  short alias used in `schema.xml` (e.g. `"leftAnchoredSearch"`). An exception is thrown
  at class-loading time if this field is missing.
- A **public no-arg constructor** alongside the existing `Map<String,String>` constructor.
  The no-arg constructor is never called by Lucene itself; it conventionally throws
  `UnsupportedOperationException`. Its presence is required for `ServiceLoader` to
  enumerate the class.

`FullyAnchoredSearchFilterFactory` already has a no-arg constructor but lacks `NAME`.
`LeftAnchoredSearchFilterFactory` lacks both.

### 3. `META-INF/services` SPI file must be renamed

If the project registers its factories via `META-INF/services` (needed for Solr to
discover the plugin without explicit `<lib>` config), the services file key must match
the new fully-qualified class name:

| Before                                                   | After                                        |
|----------------------------------------------------------|----------------------------------------------|
| `META-INF/services/org.apache.lucene.analysis.util.TokenFilterFactory` | `META-INF/services/org.apache.lucene.analysis.TokenFilterFactory` |

Currently the project has **no `META-INF/services` file at all** — this is fine if
discovery happens via Solr's `<lib>` directive, but one should be added for SPI-based
auto-discovery.

### 4. Lucene analysis artifact renamed (Lucene 9 / LUCENE-9562)

| Before                                    | After                                 |
|-------------------------------------------|---------------------------------------|
| `lucene-analyzers-common`                 | `lucene-analysis-common`              |
| `lucene-analyzers-icu`                    | `lucene-analysis-icu`                 |

This project does not depend on these directly — it pulls them transitively through
`solr-core` and `solr-analysis-extras` — but it matters for understanding transitive
dependency resolution.

### 5. `solr-analysis-extras` module: renamed and restructured

In Solr 9+, `solr-analysis-extras` became a **Solr module** (not a separate Maven
artifact in the same sense). In Solr 10, the `analysis-extras` content (ICU filters,
OpenNLP, etc.) is still accessible but the Maven groupId/artifactId coords for
development must be confirmed against the Solr 10 release artifacts on Maven Central.
The project only uses `solr-analysis-extras` as a compile-scope dependency — if the
plugin code itself does not call ICU classes directly (it doesn't), this dependency can
likely be **dropped** in favour of relying on `solr-core` alone.

### 6. Java version requirements

| Solr Version | Minimum Java |
|--------------|-------------|
| Solr 8       | Java 8       |
| Solr 9       | Java 11      |
| Solr 10      | **Java 21**  |

The `pom.xml` currently compiles to `source/target 1.8`. This must be updated to at
least `21` (or `17` as a compatible bytecode target if the JDK supports it, but `21`
is safest for Solr 10 deployment).

### 7. Solr 10: Jetty 12 / Jakarta namespace

Solr 10 migrated from `javax.*` to `jakarta.*` (Jakarta EE 10). This does **not** affect
a pure analysis-filter plugin that has no HTTP/servlet dependencies, but it is relevant
context if the plugin is ever extended with request handlers.

### 8. Removed Solr features (context for future work)

The following Solr features removed in 9/10 are **not used by this plugin** but are
noted for completeness: DIH, VelocityResponseWriter, auto-scaling, `BlobRepository` /
`.system` collection, legacy `CircuitBreakerManager`, analytics module, hadoop-auth.

---

## Inventory of Required Changes

| # | File | Change Required |
|---|------|-----------------|
| A | `pom.xml` | Bump `solr.version` default to `10.0.0` |
| B | `pom.xml` | Update `maven.compiler.source/target` from `1.8` to `21` |
| C | `pom.xml` | Verify/update `solr-analysis-extras` coords or remove it |
| D | `pom.xml` | Update `slf4j-api` from `1.7.30` to `2.x` (Solr 10 uses SLF4J 2) |
| E | `pom.xml` | Update `junit-jupiter` to `5.x` latest |
| F | `LeftAnchoredSearchFilterFactory.java` | Change import to `org.apache.lucene.analysis.TokenFilterFactory` |
| G | `LeftAnchoredSearchFilterFactory.java` | Add `public static final String NAME = "leftAnchoredSearch"` |
| H | `LeftAnchoredSearchFilterFactory.java` | Add public no-arg constructor throwing `UnsupportedOperationException` |
| I | `FullyAnchoredSearchFilterFactory.java` | Change import to `org.apache.lucene.analysis.TokenFilterFactory` |
| J | `FullyAnchoredSearchFilterFactory.java` | Add `public static final String NAME = "fullyAnchoredSearch"` |
| K | `src/main/resources/META-INF/services/` | Create SPI file `org.apache.lucene.analysis.TokenFilterFactory` listing both factory classes |

---

## Staged Migration Plan

### Stage 1 — Dependency Update (pom.xml) [independent, do first]

Update Maven build metadata. No source changes yet; this stage can be validated by
checking that `mvn dependency:resolve` succeeds with the new coordinates.

**Tasks:**

1. Set `<solr.version>10.0.0</solr.version>` as the default (items A).
2. Set `maven.compiler.source` and `maven.compiler.target` to `21` (item B).
3. Check whether `solr-analysis-extras` is still published as
   `org.apache.solr:solr-analysis-extras:10.0.0` on Maven Central; if not, replace
   with the correct coordinates or remove (item C).
4. Update `slf4j-api` to `2.0.x` (item D).
5. Update `junit-jupiter` to `5.11.x` (item E).

**Validation:** `mvn dependency:resolve -U` completes without download errors.

---

### Stage 2 — Source Code Changes [depends on Stage 1 for compile verification]

All four source-level changes (F–K) are independent of each other and can be made in
parallel (in the same commit, or by two developers simultaneously on different files).

#### 2a. Fix factory imports and add required fields (items F–J)

In **both** `LeftAnchoredSearchFilterFactory.java` and
`FullyAnchoredSearchFilterFactory.java`:

1. Replace:
   ```java
   import org.apache.lucene.analysis.util.TokenFilterFactory;
   ```
   with:
   ```java
   import org.apache.lucene.analysis.TokenFilterFactory;
   ```
2. Add the static `NAME` constant immediately after the class declaration:
   ```java
   public static final String NAME = "leftAnchoredSearch"; // or "fullyAnchoredSearch"
   ```
3. Add the required no-arg constructor (if not present):
   ```java
   /** No-arg constructor required by Lucene's java.util.ServiceLoader. Never called directly. */
   public LeftAnchoredSearchFilterFactory() {
       throw new UnsupportedOperationException("Use the Map<String,String> constructor.");
   }
   ```

#### 2b. Add SPI service file (item K) [can be done in parallel with 2a]

Create the file:

```
src/main/resources/META-INF/services/org.apache.lucene.analysis.TokenFilterFactory
```

Contents:
```
edu.umich.lib.solr.LeftAnchoredSearchFilterFactory
edu.umich.lib.solr.FullyAnchoredSearchFilterFactory
```

Ensure `src/main/resources` is included in the Maven build (add `<resources>` block to
`pom.xml` if not already picked up automatically — Maven includes `src/main/resources`
by default, so this should require no extra config).

---

### Stage 3 — Build and Test Verification [depends on Stages 1 & 2]

1. Run `mvn clean package` and confirm the JAR builds without compile errors.
2. Run `mvn test` — the existing unit tests exercise `LeftAnchoredSearchFilter` and
   `FullyAnchoredSearchFilter` directly (no Solr server needed); confirm all pass.
3. **Smoke-test in Solr 10**: Drop the built JAR into a local Solr 10 instance,
   configure a field type using the filter factories, and index a test document to
   confirm the SPI loader resolves the factories correctly.

---

### Stage 4 — Documentation and Housekeeping [independent, can be done in parallel with Stage 2]

1. Update `README.md` to reflect Solr 10 compatibility and the new Java 21 requirement.
2. Update `CHANGELOG.md` with migration notes.
3. Update the pom `<version>` from `0.2` to `0.3` (or `1.0.0`) to signal the
   breaking-change version bump.

---

## Dependency Lookup Required (Before/During Stage 1)

The following coordinates need to be confirmed against Maven Central before finalising
the pom changes — Solr 10 restructured its Maven artifacts significantly:

- **`org.apache.solr:solr-core:10.0.0`** — confirm it exists and is the right
  compile-time dependency for plugin development (vs. a thinner `solr-analysis-*` dep).
- **`org.apache.solr:solr-analysis-extras:10.0.0`** — confirm existence; may have been
  renamed or merged. If this plugin does not call ICU/OpenNLP classes directly, the
  dependency can simply be removed.
- **SLF4J version** — Solr 10 ships with SLF4J 2.0.x; align the `slf4j-api` version
  in pom to avoid classpath conflicts at runtime.

---

## Parallel Work Summary

```
Stage 1 (pom deps)  ──────────────────────────────┐
Stage 4 (docs)      ────────────────────────────┐  │
                                                 │  ▼
Stage 2a (import/NAME fixes) ──┐              Stage 3 (build + test)
Stage 2b (SPI file)  ──────────┘
```

Stages 2a, 2b, and 4 can all be worked in parallel once Stage 1 is underway (the
import changes and SPI file creation require no compile round-trip). Stage 3 depends on
all of 1, 2a, and 2b being complete.
