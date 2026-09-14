# Architecture

Baseline: PRAISEHK2 `ODEstimationMatsim` @ `77f93f2`. Audit only — no production behaviour changed.

## 1. Repository / build layout

### Before (as committed) — not buildable from a clean clone

```
PRAISEHK2/
└── MetaModelCalibration/
    ├── pom.xml                     # no parent; no declaration of the vendored JARs
    ├── .classpath                  # Eclipse: kind="src" path="/MATSim-HK"  <-- external project
    └── src/main/resources/         # jars carry resources: jcool-core.jar, MATSim-HK-0.1x, MATLAB jars
                                    # MATSim-HK-11.0.jar MISSING from git (untracked locally)
```

`mvn test-compile` failed with `package dynamicTransitRouter.fareCalculators does not exist`,
`package transitFareAndHandler does not exist`, `package cz.cvut.fit.jcool.core does not exist`.
Root cause and remedy: `PRAISE_MATSIMHK_RELATIONSHIP.md`, `DEPENDENCIES.md`.

### After the trunk (PR 1 + PR 2)

```
PRAISEHK2/
├── MetaModelCalibration/           # single Maven module again (no aggregator needed)
│   ├── pom.xml                     # + junit5/surefire, + jcool system dep; MATSim-HK dependency REMOVED
│   └── src/{main,test}/java/ust/hk/praisehk/metamodelcalibration/
│       ├── transit/fare/           # NEW: 2 vendored classes (FareCalculator, FareLink)
│       └── ...                     # unchanged packages
├── docs/modernization/             # NEW: audit + TDD foundation
│   ├── ARCHITECTURE.md
│   ├── LEGACY_BEHAVIOR.md
│   ├── TEST_MATRIX.md
│   ├── DEPENDENCIES.md
│   ├── REVIEW_REQUIRED.md
│   ├── OBJECTIVE_PURITY_PLAN.md
│   ├── PRAISE_MATSIMHK_RELATIONSHIP.md
│   └── PRAISE_ODE_RELATIONSHIP.md
└── README.md
```

Build: `cd MetaModelCalibration && mvn -o -B clean test` → BUILD SUCCESS, 130 tests, 0 failures, offline.

The Hong Kong MATSim fork was first imported as a 153-file module, then reduced: the dependency
closure was measured at 39 files / 11k LOC, but only **two** of those classes have any active use in
PRAISEHK (`FareCalculator`, `FareLink`). Those two are vendored verbatim (package declaration aside),
and the fork is no longer part of the build. Details in `PRAISE_MATSIMHK_RELATIONSHIP.md`.

Toolchain: JDK 17 (the only JDK present) compiling with `<release>13</release>`. Java 13 is not
installed, so the `release 13` target is the compatibility contract; this should be revisited
deliberately.

## 2. Package architecture (MetaModelCalibration, 90 main classes / ~18k LOC)

| Package | LOC (approx) | Responsibility | Depends on |
|---|---|---|---|
| `transit.fare` | ~230 | **vendored fare contract**: `FareCalculator` (interface used as `Map<String,FareCalculator>` by the transit utility) and `FareLink` (fare-observation identity + parsing, consumed by `MeasurementType`). Verbatim from the HK fork apart from the package declaration — see `PRAISE_MATSIMHK_RELATIONSHIP.md`. | MATSim only |
| `analyticalModel` | ~3.4k | **interfaces + shared state**: `AnalyticalModel`, `AnalyticalModelLink/Network/Route/TransitRoute/ODpair(s)`, `SUEModelOutput`, `Trip`, `TripChain`, `TransitLink/DirectLink/TransferLink`, `TimeUtils`, `InternalParamCalibratorFunction` | MATSim core |
| `analyticalModelImpl` | ~4.6k | **the SUE engine**: `CNLLink`, `CNLRoute`, `CNLTransitRoute`, `CNLTransitDirectLink`, `CNLTransitTransferLink`, `CNLNetwork`, `CNLODpairs`, `CNLSUEModel` (1672 LOC), `CNLSUEModelSubPop`, `SUEModelContTime` (1411 LOC), `SUEModelContTimeSubPop`, `CNLTripChain` | `analyticalModel`, `transit.fare` |
| `measurements` | ~1.6k | **observations**: `Measurement`, `Measurements`, `MeasurementType` (544 LOC enum mixing observation semantics + extraction + gradients + XML), `MeasurementsReader/Writer`, `MTRLinkVolumeInfo` | `analyticalModel`, `transit.fare` |
| `calibrator` | ~2.9k | **calibration**: `Calibrator`, `CalibratorImpl` (842 LOC, trust region), `AnalyticalModelOptimizer{,Impl}`, `ObjectiveCalculator`, `OptimizationFunction` + 8 objective/decision variants, `ParamReader` | `analyticalModel`, `matamodels`, `measurements`, jcobyla, jcool |
| `matamodels` | ~2.0k | **surrogates**: `MetaModel`, `MetaModelImpl`, `AnalyticLinearMetaModel`, `AnalyticalQuadraticMetaModel`, `LinearMetaModel`, `QuadraticMetaModel`, `GradientBasedMetaModel`, `GradientBaseOptimizedMetaModel`, `GradientOptimizedMetaModel`, `SimAndAnalyticalGradientCalculator`, `MatrixBasedUnconstrainedAdam/GD` | `measurements`, jcobyla, ND4J, Smile, commons-math3 |
| `matsimIntegration` | ~1.9k | **MATSim boundary**: event handlers (`LinkCount`, `LinkPCUCount`, `AverageOccupancy`, `FareLinkVolumeCount`, `TravelTime`, `SmartCardEntryAndExit`, `MTRPassengerFlowCounter`), modules, `SimRun`, `SignalFlowReductionGenerator`, `RoutesAndODGenerator*`, `MeasurementsStorage` | MATSim, Guice |
| `Utils` | ~1.1k | `MapToArray`, `Tuple`, `TruncatedNormal`, `LinkVehicleCompReader`, `Matlab*` (dead), `Trial` | mixed, MATLAB (dead) |

Dependency direction is downward-only in the intended sense:
`analyticalModel` ← `analyticalModelImpl` ← {`measurements`, `calibrator`, `matamodels`, `matsimIntegration}`.
`calibrator` ↔ `matamodels` is currently **bidirectional at the source level**
(`CalibratorImpl` constructs meta-models; `MetaModelImpl` is told the measurement ids), which is one
of the couplings the target architecture must break.

## 3. Cross-repository dependency reality

```
                  (source imports)                 (source imports)
  ODEstimation  ──────────────────▶  PRAISEHK  ◀──────────────  MATSim-HK (reference only)
                                                  └─ vendored: 2 classes (transit.fare)
```

* **ODEstimation → PRAISEHK**: hard source dependency (30+ imported classes), **not declared** in
  `ODEstimation/pom.xml`. Evidence in `PRAISE_ODE_RELATIONSHIP.md` §1.
* **PRAISEHK → MATSim-HK**: **no longer a build edge.** The 39-file transitive closure was reduced to
  the 2 classes actually used, which are now vendored as source in `transit.fare`. The fork remains a
  local reference implementation only.
* **ODEstimation → MATSim-HK**: declared (`MATSim-HK:11.0`). Once ODEstimation is buildable, the same
  vendoring question should be asked of it.
* MATSim version split: PRAISEHK on `15.0-SNAPSHOT`; ODEstimation on `14.0-SNAPSHOT`.

This means the historical "circular relationship" is **not** a source cycle between ODEstimation and
PRAISEHK; it is the absence of declared build edges, which forced both projects to compile only
inside one Eclipse workspace.

## 4. Target architecture (design intent — not implemented)

The brief's target structure, mapped from what actually exists. Nothing below is created in this PR;
this is the destination the characterization tests are meant to make safe.

```
calibration-core/
  parameters/        ◀── ParamReader, ODEstimation optimizer/VariableDetails, Tuple bounds
  observations/      ◀── Measurements, Measurement, + a modern MeasurementType split
  objectives/        ◀── ObjectiveCalculator  (must become pure)
  optimizers/        ◀── jcobyla usage, AnalyticalModelOptimizer*, ODE Adam/GD
  trust-region/      ◀── CalibratorImpl trust-region policy (extracted as a policy object)
  surrogate/         ◀── matamodels/*

assignment-api/      ◀── analyticalModel/* (interfaces only)

assignment-static-sue/
  network/           ◀── CNLNetwork, AnalyticalModelLink
  od/                ◀── AnalyticalModelODpair(s), CNLODpairs
  road/              ◀── CNLLink, CNLRoute
  transit/           ◀── CNLTransitRoute, CNLTransit{Direct,Transfer}Link
  multimodal/        ◀── car/PT split in CNLSUEModel
  equilibrium/       ◀── the ONE SUE iteration (CNLSUEModel, SUEModelContTime)

differentiation/     ◀── NEW boundary; from ODEstimation
  sensitivity-api/   forward-mode contract
  forward-sensitivity/  GradientUtils + ODDifferentiableSUEModel propagation
  finite-difference-oracle/  reusable derivative checker
  gradient-checking/ seeded gradient fire test

od-estimation/       ◀── ODUtils parameterisation, OD optimizers
matsim-adapter/      ◀── matsimIntegration/*, and the vendored transit.fare contract
```

**Binding design principles**

1. **One canonical static SUE.** `CNLSUEModel` and `ODDifferentiableSUEModel.performAssignment` must
   converge to a single `StaticSUEEvaluator`; differentiation is a *capability*
   (`StaticSUEEvaluator + SensitivityEngine = DifferentiableStaticSUEEvaluator`), not a second engine.
   In particular, do **not** port ODEstimation's duplicated assignment forward.
2. **No duplicated network/route/OD classes.** ODEstimation already imports PRAISEHK's versions; keep
   it that way.
3. **Three separated derivative layers**: `ModelDerivative` (exact) → `ObjectiveDerivative`
   (chain rule) → `OptimizerTransformation` (clipping/scaling/trust region). Today `gradMultiplier`,
   `Clip`, `maxAbsGrad`/`minAbsGrad` and the trust-region code are interleaved with the model
   derivative (`PRAISE_ODE_RELATIONSHIP.md` §5).
4. **Immutable parameter ordering** as a first-class value, shared by `MapToArray`,
   `VariableDetails` and every `double[]` gradient (`PRAISE_ODE_RELATIONSHIP.md` §4).
5. **Pure objective evaluation** (`ObjectiveCalculator` currently mutates inputs — OBJ-3).
6. **Calibration core must not depend on MATSim classes**; `matsim-adapter` implements the core's
   ports.
7. **Measurement extraction** (`MeasurementType`) must be split from observation semantics and XML
   serialization — but only after characterization (Phase 4).

## 5. Build & test infrastructure (as of this PR)

* **Single Maven module.** There is no aggregator: `MetaModelCalibration/pom.xml` is the only POM.
  All commands are run from `MetaModelCalibration/`.
* JUnit 5 + `junit-vintage-engine`; surefire 3.2.5 with the legacy nondeterministic test excluded.
* Fixtures live in `src/test/java/.../fixtures/` and are data + in-memory network builders only.
* No test requires: Hong Kong data, absolute paths, MATLAB, network access, `Math.random()`, or
  `HashMap` iteration order.
* Offline verification: `cd MetaModelCalibration && mvn -o -B clean test`
  → BUILD SUCCESS, 130 tests, 0 failures, zero compiler diagnostics.

## 6. Delivery roadmap (small, behaviour-protected PRs)

The **PR numbers below are roadmap ordinals, not GitHub PR numbers** — GitHub numbering diverges
because the CI workflow landed as its own GitHub PR (`#4`) and does not appear in this roadmap.

Two tracks. Track B (ODEstimation) is **independent of** Track A and must not be deferred behind it:
it is the gate for Track C.

### Infrastructure

| Item | Content | Status |
|---|---|---|
| CI | `.github/workflows/ci.yml` — deterministic suite on every PR into the trunk, plus a guard that fails if fewer than 80 tests report | done |
| Protection | `modernization/main` requires the `mvn -B clean test (JDK 17)` check (strict); force-push and deletion disallowed | done |

### Track A — PRAISEHK characterization

| PR | Content | Status |
|---|---|---|
| 1 | Audit + docs + build reproducibility + test harness + fixtures + ObjectiveCalculator/Measurement characterization | merged |
| 2 | `MeasurementType` extraction full coverage (remaining types) + objective-purity plan | merged |
| 3 | `ParamReader` characterization → typed `ParameterDefinition`/`ParameterSpace` (characterization done; the typed redesign is the follow-up) | **this PR** |
| 4 | Meta-model mathematical tests (weighted-ridge oracle; decide the fate of the 5 fitting paths) | planned |
| 5 | Calibrator/trust-region deterministic state-machine tests (incl. CAL-1…CAL-8) | planned |
| 6 | Link/route/transit analytical unit tests (oracle values) | planned |
| 7 | Small-network SUE characterization (fixtures B/C/D completed: PT route construction, MSA, conservation) | planned |

### Track B — ODEstimation build repair + derivative validation (the gate)

| PR | Content | Status |
|---|---|---|
| 8 | ODEstimation build repair (declare the PRAISEHK dependency) + `ParameterOrdering` determinism tests + leaf derivative oracles + reusable finite-difference oracle + seeded gradient fire test | **gate — start as early as possible; may run in parallel with Track A** |
| 9 | Fare-calculation decision: implement `FareCalculator` against the vendored two-class `transit.fare` contract with oracle tests, **or** delete the commented-out MTR path in `CNLTransitRoute` (REVIEW_REQUIRED FARE-3); then formalise the fare/transit adapter boundary | planned |

### Track C — architectural extraction

| PR | Content | Status |
|---|---|---|
| 10+ | Architectural extraction and dependency replacement, one family at a time | **blocked by Track B** |

### Gate: no static-SUE consolidation before derivative validation

No `StaticSUEEvaluator` may be created, and no Track C architectural extraction may begin, until all
of the following are green (tracked in `TEST_MATRIX.md` §5):

1. deterministic `ParameterOrdering` / `MapToArray` index tests, including exact key-set equality;
2. OD parameter-incidence derivative tests;
3. BPR derivative oracle (`dt/dv = t0·α·β·v^(β−1)/c^β`);
4. route-logit derivative oracle;
5. mode-choice derivative conservation (`Σ ∂P_mode/∂θ = 0`);
6. route-flow and link-flow sensitivity tests;
7. a reusable central finite-difference oracle evaluated at several perturbation sizes;
8. a seeded gradient fire test.

Rationale: the sensitivity engine constrains the SUE boundary, so the one-canonical-SUE decision must
not outrun derivative validation. Note also that PR 9 in an earlier draft named "MATSim-HK core
extraction + `FareLink`/`FareCalculator` characterization"; that is obsolete — `FareLink` is already
characterized in this PR and the fork is no longer a dependency, so PR 9 is now the fare-calculation
and adapter-boundary decision above.

## 7. Known architectural debt (summary; details in `REVIEW_REQUIRED.md`)

* `Observation` ↔ `Extraction` ↔ `Serialization` fused in a single enum (`MeasurementType`, 544 LOC).
* `ObjectiveCalculator` is a static utility with non-pure methods and inconsistent
  missing-data/zero-denominator policies.
* `ParamReader` conflates CSV parsing, parameter bookkeeping, scaling, and MATSim `Config` mutation
  with disk I/O.
* Trust-region policy is embedded in an 842-LOC method with manually duplicated bookkeeping maps.
* Five parallel meta-model fitting implementations (~2 of them dead) pulling three heavyweight
  numerical stacks (ND4J/DL4J, Smile, commons-math3).
* `HashMap`/`ConcurrentHashMap` containers used as de-facto ordered structures in places that need
  deterministic ordering.
* `parallelStream()` used for map-building before correctness was established.
* Dead MATLAB integration (~27 MB of JARs) retained.
