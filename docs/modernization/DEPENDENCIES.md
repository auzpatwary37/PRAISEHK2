# Dependencies

Scope: the `MetaModelCalibration` module of PRAISEHK (`ODEstimationMatsim` baseline, commit
`77f93f2`), plus the newly imported `matsim-hk` module. **Nothing was upgraded or removed in this
PR** except declaring what was already required and adding the JUnit 5 test harness.

Usage counts are `grep -rl "^import <pkg>" MetaModelCalibration/src/main/java | wc -l`.

## A. Declared dependencies of `MetaModelCalibration`

| Dependency | Version | Declared scope | Files using it | Purpose | Verdict |
|---|---|---|---|---|---|
| `org.matsim:matsim` | 15.0-SNAPSHOT | transitive (via roadpricing/signals/emissions) + `test-jar` test | many | core API: `Network`, `Link`, `Id`, `Tuple`, config, scoring | **keep**; snapshot is a reproducibility risk (§C) |
| `org.matsim.contrib:roadpricing` | 15.0-SNAPSHOT | compile | few | contrib classes used by network/OD generation | **keep**; version-coupled |
| `org.matsim.contrib:signals` | 15.0-SNAPSHOT | compile | 1 (`SignalFlowReductionGenerator`) | signal flow reduction | **keep**; version-coupled |
| `org.matsim.contrib:emissions` | 15.0-SNAPSHOT | compile | few | `LinkPCUCount*`, emission-aware counting | **keep**; version-coupled |
| `de.xypron.jcobyla:jcobyla` | 1.2 | compile | **14** | derivative-free constrained optimizer (COBYLA) used for meta-model fitting and internal parameter calibration | **primary optimizer**; replacement requires equivalence tests |
| `org.apache.commons:commons-math3` | *(not declared; transitive 3.6.1 via smile/jcobyla)* | — | **7** (`MeasurementType`, `AnalyticLinearMetaModel`, `MatrixBasedUnconstrained*`, `AnalyticalModelODpair`, …) | `RealVector`/`RealMatrix`/`MatrixUtils` + `GLSMultipleLinearRegression` | **declare explicitly** — a transitive dep must not be load-bearing |
| `org.deeplearning4j:deeplearning4j-core` | 1.0.0-beta6 | compile | **1** (`AnalyticLinearMetaModel`, imports DL4J runtime indirectly) | experimental meta-model fitting | **experimental only** → elimination candidate |
| `org.nd4j:nd4j-native-platform` | 1.0.0-beta6 | compile | **1** (`AnalyticLinearMetaModel`: `Nd4j`, `INDArray`, `InvertMatrix`, `CheckUtil`, `DataType`) | matrix inversion in the analytical meta-model fitter | **experimental only**; pulls native binaries for *all* platforms → build/repo bloat; replace with commons-math3 inverse or drop the dead path |
| `com.github.haifengl:smile-core` + `smile-data` | 2.5.3 | compile | **1** (`AnalyticLinearMetaModel`: `LASSO`, `LinearModel`, `DataFrame`, `Formula`) | alternative (dead) meta-model fitter | **experimental / dead code** → elimination candidate |
| `com.google.inject:guice` | 4.2.0 | compile | **12** | MATSim module wiring in `matsimIntegration` | **keep** (MATSim couples to Guice); note MATSim-HK wants Guice 5.0.1 — version conflict to resolve |
| `com.google.guava:guava` | 26.0-jre | compile | **3** (`AnalyticalModelODpairs`, `SignalFlowReductionGenerator`, `SUEModelContTime`) | `Lists`/`Sets` only | **replace with `java.util`** → drop Guava |
| `org.apache.logging.log4j:log4j-api/core/1.2-api` | 2.13.2 | compile | **14** (`org.apache.log4j.Logger` → bridge) | legacy Log4j 1.x API bridged to Log4j2 | **keep for now**; consolidate on one logging API later |
| `org.slf4j:slf4j-api` | 2.0.0-alpha4 | compile | 0 direct | MATSim logging | **keep**; alpha version is a flag |
| `com.healthmarketscience.jackcess:jackcess` | 2.1.12 | compile | **0 in MetaModelCalibration** | MS Access reader (used by MATSim-HK for HK `.mdb` data) | **dead in this module** → remove from this pom |
| `org.apache.commons:commons-csv` | 1.5 | compile | **0** | CSV parsing | **dead** (this module uses raw `split(",")`, see REVIEW_REQUIRED PARAM-2) |
| `org.apache.commons:commons-lang3` | 3.7 | compile | 0 direct | utility | likely dead; confirm before removal |
| `edu.ucar:cdm` | 4.5.5 | compile | **0** | NetCDF/CDM | **dead in this module** (pulled for MATSim contribs); removal needs MATSim-version coordination |
| `javax.inject:javax.inject` | 1 | compile | **1** (`SmartCardEntryAndExitEventHandler`) | DI annotation | keep (Guice ecosystem) |
| `junit:junit` | 4.12 | test | legacy JUnit 4 tests | legacy harness | keep as long as JUnit 4 tests remain |
| **NEW** `org.junit.jupiter:junit-jupiter` | 5.10.2 | test | new deterministic tests | modern harness | added in this PR |
| **NEW** `org.junit.vintage:junit-vintage-engine` | 5.10.2 | test | runs the JUnit 4 tests | bridge | added in this PR |
| **NEW** module `MATSim-HK:MATSim-HK` | 11.0 | compile | 6+ | HK fare calculators / transit router / `FareLink` | replaces the untracked JAR |
| **NEW** `cz.cvut.fit:jcool-core` | 1.0 | **system** (`systemPath` → `src/main/resources/jcool-core.jar`) | `AnalyticalModelOptimizerImpl`, `HessianObjective` | `ObjectiveFunction`, `Point`, `Gradient`, `Hessian`, `CentralDifferenceHessian` | **genuinely unavailable** in any public repo; `system` scope keeps the build offline-reproducible |

## B. Vendored JARs in `MetaModelCalibration/src/main/resources`

| JAR | Size | Used? | Provenance / verdict |
|---|---|---|---|
| `jcool-core.jar` | 36 KB | **yes** (declared `system`) | jCool optimization toolkit, `cz.cvut.fit.jcool`; no longer maintained/published. Keep until the two classes are ported. |
| `MATSim-HK-0.11.0.jar`, `MATSim-HK-0.11.0-sources.jar`, `MATSim-HK-0.10.0-SNAPSHOT.jar` | 0.35–0.62 MB | **no** | older packages of the HK fork; `0.11.0` does **not** contain `transitFareAndHandler`. Superseded by the `matsim-hk` module. Provenance understood → removal candidates, **but not in this PR**. |
| `ea.jar` | **26 MB** | **no** | MATLAB JavaBuilder runtime. |
| `javabuilder.jar` | 0.45 MB | **no** | MATLAB JavaBuilder runtime. |
| `OptimDemo1.jar`, `OptimDemo2.jar` | 32 KB each | **no** | MATLAB-generated optimization components (`OptimDemo1.Optimizer`). |
| *(missing from git)* `MATSim-HK-11.0.jar` | 0.91 MB | — | **was untracked locally and required to compile**; contained `transitFareAndHandler` / `dynamicTransitRouter`. Now superseded by the `matsim-hk` module, so it is **not** committed. |

MATLAB verdict: `Utils/MatlabOptimizer.performOptimization()` has **all** MATLAB code commented out
and `return null;`. `Utils/MatlabObj`, `MatlabResult`, `Trial` support that dead path. The four
MATLAB JARs (~27 MB) are therefore dead weight. Recommended: confirm no other usage, then remove
and port `MatlabOptimizer` to COBYLA or delete it — **after** a characterization test proves the
current `null` behavior is not relied upon.

## C. Reproducibility risks

1. **MATSim `15.0-SNAPSHOT`.** `repo.matsim.org` currently serves a *frozen* snapshot
   (`15.0-20230411.165853-447`, build 447, 2023-04-11), so builds are stable **today** but the
   coordinates are mutable and the repository could disappear. Target: pin to a timestamped
   version or a released MATSim.
2. **`nd4j-native-platform` 1.0.0-beta6** resolves native artifacts for all platforms; it is the
   dominant download and inflates the local repository. If the ND4J path is dead (see §A), removing
   it also removes this burden.
3. **`slf4j-api 2.0.0-alpha4`, Log4j `2.13.2` (pre-2.17 CVE-affected line), Smile/ND4J beta6** — all
   dated. Each needs an equivalence-tested, family-by-family upgrade, never a bulk one.
4. **Two MATSim major lines in one ecosystem**: PRAISEHK/MATSim-HK on `15.0-SNAPSHOT`, ODEstimation
   on `14.0-SNAPSHOT`. They cannot share a reactor today.
5. **Undeclared-but-load-bearing deps**: `commons-math3` (§A) and, historically,
   `transitFareAndHandler`. `mvn dependency:analyze` should be part of the eventual CI gate.

## D. Upgrade order (once behavior is protected)

1. Declare `commons-math3` explicitly; remove dead declared deps (`jackcess`, `commons-csv`,
   `cdm`, `commons-lang3` if unused) — no version change, just correctness of the pom.
2. Remove the MATLAB JARs + port/delete `MatlabOptimizer` (equivalence test required).
3. Decide the fate of the ND4J/Smile/Adam meta-model fitting paths via tests
   (`AnalyticLinearMetaModel` has 5 parallel fitters; see `TEST_MATRIX.md` meta-model rows), then
   drop the losers and their dependencies. **Apache commons-math3 `GLSMultipleLinearRegression`
   is the natural survivor** because commons-math3 is already load-bearing.
4. Consolidate logging onto one API.
5. Pin/upgrade MATSim as a single, isolated change with all tests green before and after.
6. Replace `system`-scoped `jcool-core` by porting the two consumers off jCool.

## E. Rules observed in this PR

* No dependency was upgraded.
* No vendored JAR was deleted.
* New dependencies were added only for the mandated test harness (JUnit 5) and to declare what the
  build already required (`matsim-hk` module, `jcool-core`).
* `javax.inject` / Guice / Log4j were left untouched even though they are dated.
