# PRAISEHK ↔ MATSim-HK relationship

**Status:** forensic audit + build integration, no algorithm changed.
MATSim-HK is now imported into this repository as a real source module (`matsim-hk/`)
instead of an opaque, untracked JAR or an Eclipse project reference.

## 1. Provenance

| Item | Value |
|---|---|
| Origin | `gitlab.com/leeenoch1005/MATSim-HK` (fork of MATSim with Hong Kong extensions) |
| Branch | `MAAS` |
| HEAD commit | `7ada92ecdb4193b692e2b4acb8ea4e03bffb348b` |
| Local path (reference) | `/home/ashraf/Documents/MATSim-HK` (not on GitHub) |
| Coordinates | `MATSim-HK:MATSim-HK:11.0` |
| Imported content | `src/main/java/**` (153 files, ~2.8 MB) + `pom.xml` |
| Imported from | **the WORKING TREE**, not `HEAD` (see §2) |
| Not imported | `target/`, `cache/` (540 MB `populationMTR.xml`), `fare/` (20 MB), `onlyMTR/` (14 MB), `minibus/*.mdb`, `test/`, `src/test` |

## 2. Why the working tree and not the commit

The **committed** `pom.xml` at HEAD has the MATSim dependencies *commented out*:

```xml
<!--  <dependency>
    <groupId>org.matsim</groupId>
    <artifactId>matsim</artifactId>
    <version>13.0-SNAPSHOT</version>
  </dependency> -->
...
<!--   roadpricing / signals / emissions (14.0-SNAPSHOT)  -->
```

so the committed state does **not compile**. The working tree carries uncommitted changes that
activate `org.matsim:matsim:15.0-SNAPSHOT` and `roadpricing`/`signals`/`emissions:15.0-SNAPSHOT`,
raise source/target to 17, and modify several sources
(`FareLink.java`, `MTRFlowEventHandler.java`, `ODFareCalculator.java`, `RunFullHK.java`,
`TransferWalkingTime.java`, `StopWaitingTimeHandler.java`, `FirstClassCountHandler.java`,
`MTRFlowAnalysis.java`, `MTRODAnalysis.java`, `TransitFareTravelDisutilityTest.java`).

The buildable state therefore lives in the working tree. **This must be reconciled upstream**:
either commit the working-tree pom + sources to the fork, or record that HEAD is not authoritative.

## 3. How PRAISEHK depends on MATSim-HK

PRAISEHK's compile-time surface on the fork is small — 4 packages / ~6 classes:

| Package | Classes used by PRAISEHK | Used by |
|---|---|---|
| `dynamicTransitRouter.fareCalculators` | `FareCalculator` (10 refs), `MTRFareCalculator` | `MeasurementType`, `CNLTransitRoute`, `SUEModelContTime`, `CNLSUEModel*`, `AnalyticalModelTransitRoute` |
| `dynamicTransitRouter.transfer` | `TransferDiscountCalculator` | `SmartCardEntryAndExitEventHandler` |
| `dynamicTransitRouter` | `TransitStop` | `MeasurementType` |
| `transitFareAndHandler` | `FareLink` (6 refs), `TransitFareHandler` | `Measurement`, `MeasurementType`, `FareLinkVolumeCountEventHandler`, `CNLSUEModel` |

**However, extraction is not clean.** Those packages themselves depend on other fork packages:

```
dynamicTransitRouter/*    -> createBus.Runbus
                          -> createPTGTFS.FareCalculatorPTGTFS
                          -> withinDay.EquivalentStopForFare
transitFareAndHandler/*   -> running.RunUtils
```

So the fork is internally coupled and cannot be reduced to "just the fare packages" without
editing it. Importing the whole `src/main/java` is the low-risk choice for this phase; carving
out a `matsim-hk-core` subset is a later, test-protected refactor.

### Historical failure this fixes

The pre-existing build was:

* `MetaModelCalibration/.classpath` → `kind="src" path="/MATSim-HK"` (Eclipse project reference), so
  `mvn compile` never worked from a clean clone;
* the `transitFareAndHandler` package was missing from every tracked JAR — it existed only in the
  local, untracked `MATSim-HK-11.0.jar`. (The tracked `MATSim-HK-0.11.0.jar` does **not** contain
  `transitFareAndHandler`; the tracked `MATSim-HK-11.0.jar` did not exist at all.)
* `jcool-core.jar` was needed for `cz.cvut.fit.jcool.*` but was not declared;
* `ea.jar` / `javabuilder.jar` / `OptimDemo{1,2}.jar` (**27 MB + 465 KB + 32 KB + 32 KB**) were
  dead weight: `Utils/MatlabOptimizer.performOptimization()` has every MATLAB import commented out
  and simply `return null;`.

## 4. Package classification (mirrors `matsimIntegration/` classification for PRAISEHK)

| Package | Files | Classification | Rationale |
|---|---|---|---|
| `dynamicTransitRouter` (+`costs`, `fareCalculators`, `transfer`) | 24 | **(a) core adapter worth preserving** | dynamic transit routing, fare calculators and transfer discounts; consumed directly by PRAISEHK's transit SUE |
| `transitFareAndHandler` | 15 | **(a) core adapter worth preserving** | `FareLink` is the fare-measurement identity used by `MeasurementType`; fare routing/handlers are the MATSim side of fare extraction |
| `withinDay` | 5 | **(b) MATSim-version-specific plumbing** | within-day replanning identifiers/filters; version-coupled |
| `running` | ~8 | **(b) version-specific plumbing / entry points** | `RunMTR`, `RunFullHK`, `RunUtils` — scenario drivers |
| `emissionHK` | ~5 | **(b) version-specific plumbing** | HK emission extension |
| `analysisOffline` | ~6 | **(c) event-based measurement extractor** | `MTRFlowAnalysis`, `MTRODAnalysis`, `CountFileForLinkIds` derive counts from events — same family as PRAISEHK's `matsimIntegration` handlers |
| `population`, `population/TCS`, `population/GVTCS` | ~20 | **(c) HK data pipeline** | TCS/GV travel-diary → population; input data dependent |
| `createBus`, `createMTR`, `createLightrail`, `createPTGTFS` | ~25 | **(c) HK data pipeline** | transit supply builders from HK source data |
| `networkFromSaturn`, `networkFromTDData` (+`pt`) | ~20 | **(c) HK data pipeline** | network construction from HK SATURN/TD data |
| `assignLinkToPlanActivity`, `taxiRouter` | ~8 | **(d) obsolete candidate** | narrow utilities; usage to be confirmed before removal |

Do not delete anything in this PR.

## 5. Known defects / risks introduced by the fork

1. **Non-UTF-8 bytes in source.** `createBus/BusDataExtractor.java` contains bytes `0xA1`, `0xAF`
   (line 339) and `0x92` (line 449) that are unmappable as UTF-8. javac emits them as `[ERROR]`
   diagnostics while the build still succeeds. This is a latent portability/reproducibility defect
   and should be repaired by correcting the file encoding (characterize the surrounding literals
   first — they may be user-visible strings).
2. **Undeclared MATSim version mismatch.** The fork's working tree targets MATSim `15.0-SNAPSHOT`
   (a *snapshot*, frozen at build 447, 2023-04-11); `ODEstimation` targets `14.0-SNAPSHOT`. They
   cannot coexist in one reactor without an upgrade.
3. **Fork-internal coupling** (§3) blocks clean modularization today.
4. **`MATSim-HK` classes may be binary-incompatible with MATSim 15** at runtime: the fork was
   written against the HK MATSim 11 fork, and it now compiles against 15 only for the subsets
   actually exercised. Runtime behaviour of fare calculators/dynamic router is untested here.
5. **No tests are imported.** The fork's `src/test` (which references HK data files) is excluded,
   consistent with the project rule that tests must not require the Hong Kong production dataset.
   These must be re-created as deterministic characterization tests.

## 6. What was changed in this PR

* Added aggregator `pom.xml` (`ust.hk.praisehk:praisehk-parent`) with modules `matsim-hk` and `MetaModelCalibration`.
* Imported `matsim-hk/` (working-tree `src/main/java` + adapted `pom.xml` with `<parent>`, `UTF-8` encoding).
* `MetaModelCalibration` now depends on `MATSim-HK:MATSim-HK:11.0` as a normal module dependency.
* Declared `jcool-core.jar` as a `system`-scope dependency (it is genuinely unavailable in any public repository).
* **Removed the need for** the untracked `MATSim-HK-11.0.jar` (the module supersedes it).
  The MATLAB JARs (`ea.jar`, `javabuilder.jar`, `OptimDemo*.jar`) and the older `MATSim-HK-0.1x` JARs
  are left in place, undeclared and unused, pending provenance review — **do not delete yet**.

## 7. Next steps for MATSim-HK

1. Reconcile the fork's working tree with its commit history (upstream either way).
2. Fix the `BusDataExtractor.java` encoding.
3. Extract a `matsim-hk-core` subset (dynamic transit router + fare handling only) once
   `dynamicTransitRouter`'s dependencies on the data pipeline are covered by tests.
4. Add deterministic characterization tests for `FareLink` parsing/serialization (its
   `NetworkWideFare___board___alight___mode` grammar is already exercised from the PRAISEHK side —
   see `MeasurementTypeTest`) and for `FareCalculator` subclasses.
