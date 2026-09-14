# LEGACY_BEHAVIOR — mathematical responsibility of the existing code

This document records what each important legacy class **is responsible for** and what it actually
computes, so that behaviour can be protected before it is changed.

**Confidence markers** — used deliberately, because a wrong claim here is worse than an absent one:

* `[V]` **verified** — established by reading the code and, where noted, reproduced by a passing test.
* `[T]` **tested** — the current behaviour is pinned by a characterization/oracle test in this PR.
* `[U]` **UNVERIFIED** — read at a high level only or not read at all; a claim here is a *hypothesis*
  and must not be relied upon until a test or a full read confirms it. These are the highest-priority
  items for the next phase.

Nothing in this document is a proposal to change behaviour. Defects are cross-referenced to
`REVIEW_REQUIRED.md`.

---

## 1. `analyticalModel/` — interfaces and shared state

| Class | Responsibility | Notes |
|---|---|---|
| `AnalyticalModel` `[V]` | The SUE engine contract. Declares the calibration decision-variable names (`MarginalUtilityofTravelCar`, `MarginalUtilityofDistanceCar`, `MarginalUtilityofMoney`, `DistanceBasedMoneyCostCar`, `MarginalUtilityofTravelpt`, `MarginalUtilityOfDistancePt`, `MarginalUtilityofWaiting`, `UtilityOfLineSwitch`, `MarginalUtilityOfWalking`, `DistanceBasedMoneyCostWalk`, `ModeConstantPt`, `ModeConstantCar`, `MarginalUtilityofPerform`, `CapacityMultiplier`, `StandingUtilitypt`, `MarginalUtilityofTravelMetro`) and the entry points `perFormSUE(...)`, `calibrateInternalParams(...)`, `getAnalyticalModelInternalParams()`, `clearLinkCarandTransitVolume()`. | Parameter **names** are a public contract: they are the keys used by `ParamReader`, the CSV files and ODEstimation. |
| `AnalyticalModelLink` `[V]` `[T]` | Wraps a MATSim `Link` and accumulates: `linkCarVolume`, `linkTransitVolume` (PCU of transit vehicles), `linkTransitPassenger` (passengers, non-PCU), `residualCarVolume`, `gcRatio`, `vehicleSpecificVolume`, `transitVehicles`. Provides `addLinkCarVolume`, `addLinkTransitVolume`, `clearTransitPassangerFlow`, `clearLinkCarFlow`, `resetLinkVolume`. `getLinkAADTVolume() = linkCarVolume + linkTransitVolume`. | `clearNANFlow()` uses `== Double.NaN`, which is **never true** — a no-op guard. `resetLinkVolume()` resets car volume but **not** transit passenger volume (deliberate, per the comment). |
| `AnalyticalModelNetwork` `[V]` | Network of `AnalyticalModelLink`s keyed by `Id<Link>`. |
| `AnalyticalModelRoute` `[V]` | `getTravelTime`, `calcRouteUtility` (several overloads), `getRouteDistance`, `getLinkIds`, `getLinkReachTime`, `updateToOdBasedId(odId, routeNumber)`, `getOldRouteId`, `clone`. `updateToOdBasedId` rewrites the route id to `odId + "_r_" + n` (`CNLRoute.routeIdSubscript`). |
| `AnalyticalModelODpair` `[V]` | Per-OD state: `demand`, `vehicleSpecificDemand`, `RouteUtility`, `TrRouteUtility`, `RouteFlow`, `TrRouteFlow`, route set, link incidence, departures (`TruncatedNormal`), `averagePCU`, `expansionFactor`, `subPopulation`. Two constructors are lightweight (map initialisation only) — this is what makes small fixtures possible. |
| `AnalyticalModelODpairs` `[V]` | Aggregate OD set: `generateODpairset`, `generateRouteandLinkIncidence`, `resetDemand`, `generateOdSpecificRouteKeys`, `sharePathbetweenSubPop`, `getdemand(timeBeanId)`. Uses Guava (`Lists`/`Sets`). |
| `SUEModelOutput` `[V]` | The model-output container consumed by measurements: `linkVolume`, `linkTransitVolume`, `linkTravelTime`, `trLinkTravelTime`, `trainCount`, `trainTransfers`, `averagePtOccupancyOnLink`, `MaaSPackageUsage`, `FareLinkVolume`, `MaaSSpecificFareLinkFlow`, plus a parallel set of `*Grad` maps holding `double[]` sensitivity vectors. `splitMap`/`setLinkVolumeAndGradient` split a `Tuple<value, double[]>` map into parallel value/gradient maps. | Optional fields default to `null`; several measurement extractors dereference them without guards (`REVIEW_REQUIRED.md` MEAS-4/8/9). |
| `TransitLink`, `TransitDirectLink`, `TransitTransferLink` `[V]` | Transit-network element contracts: direct links own line/route/headway/frequency/capacity and a physical-link list; transfer links own a starting physical link and a `nextdLink`. |
| `Trip`, `TripChain` `[V]` | Person-level trip representation used to build routes/OD demand. |
| `TimeUtils` `[V]` | Time-bean id resolution. |
| `InternalParamCalibratorFunction` `[V]` | Objective wrapper used when calibrating the analytical model's **internal** parameters (BPRα/β, LinkMiu, ModeMiu, Transferα/β) — a *separate* concept from the external calibration variables. |

## 2. `analyticalModelImpl/CNLLink` — link performance `[V]` `[T]`

```
if ("train" ∉ link.getAllowedModes()):
    multiplier   = params["CapacityMultiplier"]        # because ifScalePt is hard-coded true
    totalPCU     = linkCarVolume + linkTransitVolume*multiplier + residualCarVolume
    capacity     = capacity * (end-start)/3600 * params["CapacityMultiplier"] * gcRatio
    t0           = length / freespeed                   # seconds
    t            = t0 * (1 + anaParams["BPRalpha"] * (totalPCU/capacity)^anaParams["BPRbeta"])
else:
    t            = length / (freespeed * 1000/3600)     # flow-, capacity- and parameter-independent
```

* Defaults: `alpha = 0.15`, `beta = 4` (fields on `CNLLink`); the **effective** values come from
  `anaParams` (`BPRalphaName`, `BPRbetaName`), whose defaults live in `CNLSUEModel`
  (`BPRbeta = 4`, `Transferbeta = 1`) with limits `BPRbeta ∈ (1,15)`, `Transferbeta ∈ (0.75,4)`.
* `ifScalePt` is hard-coded `true`, so `CapacityMultiplier` scales **both** the transit PCU load and
  the capacity. Both effects are pinned by tests (`capacityMultiplierScalesLoadAndCapacity`).
* `getCapacityPeriod()` returns a hard-coded `0` (**LINK-2**).
* The `train` branch is flow-independent and 3.6× the car free-flow time for the same geometry
  (**LINK-1**, `trainLinkUsesSeparateBranch`).
* Transit passenger bookkeeping is additive (`addTransitPassengerVolume` accumulates both
  `linkTransitPassenger` and a `lineId_routeId → volume` map, recording the contributing direct links).

## 3. `analyticalModelImpl/CNLRoute` — car route `[V]`

* Route construction: `CNLRoute(Id, ArrayList<Id<Link>>, distance, planElements)` (test-friendly) and
  `CNLRoute(Route)` which expands a MATSim `NetworkRoute` into `start + intermediate links + end`.
* Travel time (`getTravelTime`): `t = Σ_{L ∈ links} CNLLink.getLinkTravelTime(...)`, recording
  `linkReachTime[L]` as the cumulative time **before** entering `L`. Always resets `travelTime = 0`
  first, so repeated calls are idempotent.
* Time-dependent overload: for links after the first, interpolates between the current and next time
  bean using the OD pair's departure-time distribution:
  `t += tt1·P(T ≥ reachTime) + tt2·(1 − P(T ≥ reachTime))` where
  `P = odpair.getDepartureTimeDistributions().get(timeBeanId).cumulativeProbability(end − reachTime)`.
* Utility (`calcRouteUtility`, three overloads that differ only in how the travel time is obtained):
  ```
  MUTravelTime = params["MarginalUtilityofTravelCar"]/3600 − params["MarginalUtilityofPerform"]/3600
  ModeConstant = params["ModeConstantCar"]        (0 if absent)
  MUMoney      = params["MarginalUtilityofMoney"] (1 if absent)
  DBMoneyCost  = params["DistanceBasedMoneyCostCar"] (0 if absent)
  MUDistance   = params["MarginalUtilityofDistanceCar"]
  U  = ModeConstant
     + travelTime · MUTravelTime
     + (MUDistance + MUMoney·DBMoneyCost) · distance
  return U · anaParams["LinkMiu"]
  ```
  Note: `LinkMiu` scales the **whole** utility (a global scale for the logit), and
  `MarginalUtilityofPerform` enters with a negative sign through `MUTravelTime`.
* `getOtherMoneyCost()` returns `0` (future expansion).
* `clone()` does **not** copy `travelTime`, `linkReachTime` or `RouteUtility` (fresh state).

## 3b. `transit/fare/` — vendored fare contract `[V]` `[T]`

Two classes vendored verbatim from the Hong Kong MATSim fork (package declaration aside) because
PRAISEHK depends on their **contract** but the fork is no longer a build dependency. Provenance and
the reduction argument: `PRAISE_MATSIMHK_RELATIONSHIP.md`.

| Class | Responsibility | Notes |
|---|---|---|
| `FareCalculator` `[V]` | Interface for fare schemes. Methods: `getMinFare(route, line, from[, to])`, `getFares(route, line, from, to)`, `getFare(route, line, from, fromOccurrence, to, toOccurrence)`, `setFareFactor(double)`. | Used by PRAISEHK only as `Map<String, FareCalculator>` (keyed by mode, e.g. `"train"`, `"bus"`). **No implementation exists in this module** — the map is empty unless a caller supplies one, so `AnalyticalModelTransitRoute.getFare(...)` has no live fare source here. `[U]` and must be characterised before the transit utility is touched. |
| `FareLink` `[V]` `[T]` | Identifies a fare observation so that fare measurements can be aggregated. Two grammars, separated by `___`: <br>`NetworkWideFare : type___boardingStop___alightingStop___mode` <br>`InVehicleFare   : type___transitLine___transitRoute___boardingStop___alightingStop___mode` <br>Also holds the XML attribute-name constants (`FareLinkAttributeName = "fareLink"`, `FareTransactionName = "fare"`) used by `MeasurementType`/`MeasurementsReader`/`MeasurementsWriter`. | Pinned by `FareLinkTest` (10 tests). Parsing is **not escaped and not length-checked**: a truncated description throws a raw `ArrayIndexOutOfBoundsException` (FARE-1), and an id containing `___` silently corrupts the parse, discarding the tail (FARE-2). `MeasurementType.fareLinkVolume` constructs a `FareLink` from the measurement id when the attribute is absent, so measurement ids must be valid fare descriptions. |

**Deliberately not vendored** (zero active references in PRAISEHK — import-only): `MTRFareCalculator`,
`ZonalFareCalculator`, `TransitStop`, `TransitFareHandler`, `TransferDiscountCalculator`, `RouteHelper`
and the 31 further files of the fork's transitive closure (`createBus/*`, `running/RunUtils`,
`networkFromSaturn/CreateNetworkUtils`, `withinDay/EquivalentStopForFare`, the dynamic transit router).
See `PRAISE_MATSIMHK_RELATIONSHIP.md` §4.

## 4. `analyticalModelImpl/CNLTransitRoute` and transit links — `[U]` mostly

* `CNLTransitRoute` (755 LOC) computes the transit utility from fare (`transit.fare.FareCalculator`,
  `transit.fare.FareLink`), in-vehicle time, waiting time, transfer penalties, walking
  distance and money, using `MarginalUtilityofTravelpt`, `MarginalUtilityofWaiting`,
  `UtilityOfLineSwitch`, `MarginalUtilityofWalking`, `DistanceBasedMoneyCostWalk`,
  `MarginalUtilityOfDistancePt`, `Transferalpha`/`Transferbeta` (standing/transfer crowding terms).
  **The exact expression has not been read end-to-end and must not be quoted as fact.** Phase 2 of
  the roadmap (independent characterization of direct-link, transfer-link, fare and waiting
  components) is a prerequisite for touching it.
* `CNLTransitDirectLink`: headway, frequency, capacity, line/route identity and a physical-link list;
  `calcLineRouteId(lineId, routeId)` produces the `lineId_routeId` key used by link passenger maps.
* `CNLTransitTransferLink`: starting physical link plus `nextdLink`; the *last* (alighting-only) leg
  has `nextdLink == null`, and its waiting-time derivative is treated as zero in ODEstimation's
  `GradientUtils`.

## 5. `analyticalModelImpl/CNLSUEModel` — the equilibrium engine `[V]` (partially) `[U]` (iteration body)

* 1672 LOC, `implements AnalyticalModel`. Constants (public API): `BPRalphaName`, `BPRbetaName`,
  `LinkMiuName`, `ModeMiuName`, `TransferalphaName`, `TransferbetaName`.
* Internal (analytical) parameters are held in `AnalyticalModelInternalParams` with defaults
  `BPRbeta = 4`, `Transferbeta = 1` and limits `BPRbeta ∈ (1,15)`, `Transferbeta ∈ (0.75,4)`;
  `setDefaultParameters(params)` seeds them.
* Entry points: `perFormSUE(params, originalMeasurements)` and
  `perFormSUE(params, anaParams, originalMeasurements)`.
* **Weighted MSA - now `[V]`.** `beta` is a `Map<String /*timeBeanId*/, ArrayList<Double>>` explicitly
  commented "related to weighted MSA of the SUE". Verified by `CNLSUEModelMSATest`:
  * at `counter == 1` the list is cleared and seeded with `1.0`, so the first step takes the full
    loaded volume;
  * for `counter > 1`, `beta` grows by `gammaMSA = 0.1` when the last residual is **smaller** than the
    one before it, and by `alphaMSA = 1.9` otherwise - so stagnation shrinks the step faster;
  * the move applied to every link and transit link is `(1 / beta[counter-1]) * (loaded - current)`,
    the classic harmonic `1/k` weight when the counter advances by 1 each iteration;
  * the step norm is `sqrt(sum of squared moves)` and is compared against the **field** `tollerance`
    (default `1`), not against a parameter - `UpdateLinkVolume` takes none;
  * **`alphaMSA` is unreachable**: see SUE-1, the `else` branch throws because
    `consecutiveSUEErrorIncrease` is never seeded, so in practice the weight only decays as
    `1/(1 + 0.1k)`.
* **Stopping rule - now `[V]`.** `CheckConvergence` appends the residual norm to `error` and returns
  true if **any** of: the squared-error norm is `<= 1`; no link breaches the relative `tollerance`
  **parameter**; or every link is below a squared error of 1. The three criteria are of different
  kinds and interact (SUE-2, SUE-3), and the `== Double.NaN` guards are dead so a NaN residual reports
  convergence (SUE-4). Full statements in REVIEW_REQUIRED.
* **Logit/mode split**: route and mode probabilities use a numerically stabilised logit — the
  accumulation is written `totalUtility += Math.exp(d - u)`, i.e. a max-shifted (log-sum-exp)
  denominator. The shift variable `d` and the dispersion parameters (`LinkMiu`, `ModeMiu`) are `[U]`
  as to exact use; they must be pinned by a two-route oracle test before refactoring.
* **Concurrency**: network loading is performed by `CarNetworkLoadingRunnable` and
  `TransitNetworkLoadingRunnable` (inner `Runnable`s) and `beta` is a `ConcurrentHashMap`.
  Determinism under concurrency is **not** established.
* `clearLinkCarandTransitVolume()` resets accumulated volumes between evaluations.
* `calibrateInternalParams(simMeasurements, scaledParam, internalParamsLimit, currentParamNo)`
  calibrates BPRα/β, LinkMiu, ModeMiu, Transferα/β — deliberately separate from the external
  decision-variable optimization.
* `generateRoutesAndOD(...)`, `performTransitVehicleOverlay(...)`, `setFareCalculator(...)`,
  `getODtoODMultiplierId(...)`, `routeContain(...)` support network/transit construction and
  OD-multiplier identity.

## 6. `SUEModelContTime` — `[U]`

1411 LOC, `implements AnalyticalModel`, with a `SUEModelContTimeSubPop` companion. Named
"continuous time". It is **structurally different** from `CNLSUEModel` (different file, different
internals, extra standing/MTR cost handling per the branch history) and must **not** be assumed
interchangeable. Its purpose and its precise difference from `CNLSUEModel` are `[U]` and are an
explicit deliverable of roadmap phase 8. Do not refactor it or merge it with `CNLSUEModel`.

## 7. `measurements/` — observations `[V]` `[T]`

| Class | Responsibility |
|---|---|
| `Measurement` `[V]` `[T]` | One observation: id, declared `timeBean` (`Tuple<start,end>`), `volumes` (`ConcurrentHashMap`), `sd` (`ConcurrentHashMap`), typed `attributes` (link list, transit line/route/stops, fare link, fare-link cluster, MaaS package, MTR line/route/stop info, gradients), `coord`, `measurementType`. `putVolume` **replaces** and auto-inserts `SD=0`; unknown time beans are logged and ignored. `updateMeasurement` delegates to `MeasurementType`. |
| `Measurements` `[V]` `[T]` | Container: `timeBean`, `measurements` (`HashMap`), `measurementsByType` (pre-populated for **every** `MeasurementType`), `attributes` (notably `Variables`). `clone()` deep-copies measurements but **drops container attributes** (MEAS-2). CSV (`writeCSVMeasurements`/`updateMeasurementsFromFile`) and `applyFactor`/`resetMeasurements*`/`addRedundantTimeBean`. No `equals`/`hashCode` (CC-3). |
| `MeasurementType` `[V]` `[T]` | A 544-LOC enum that fuses four concerns: (1) observation semantics, (2) extraction from `SUEModelOutput` (`updateMeasurement`), (3) gradient extraction (`Gradient` attribute), (4) XML (`writeAttribute`/`parseAttribute`). Implemented kinds: `linkVolume`, `linkTravelTime`, `smartCardEntry`, `smartCardEntryAndExit`, `averagePTOccumpancy`, `fareLinkVolume`, `fareLinkVolumeCluster`, `maasSpecificFareLinkVolume`, `MaaSPacakgeUsage`, `TransitPhysicalLinkVolume`. Defect inventory: MEAS-3…MEAS-9. |
| `MeasurementsReader` `[V]` `[T]` | Single-pass SAX `DefaultHandler`; relies on `timeBeans` being parsed **before** `Measurement` elements; `linkId` matching is case-insensitive; gradients are comma-joined strings; returns `null` on parse failure (MEAS-7). |
| `MeasurementsWriter` `[V]` `[T]` | DOM writer; writes `timeBeans` first; gradient arrays as comma-joined attributes; silently swallows every exception (MEAS-6); cannot serialize `Variables` (MEAS-5). |
| `MTRLinkVolumeInfo` `[V]` | `lineId`, `routeId`, `linkId` triple for MTR physical-link-volume measurements; `toString`/parse via the `___`-style grammar. |
| `MeasurementsUtils` | `[U]` helper. |

**Clone semantics, pinned in full (MEAS-1, MEAS-2, MEAS-17, MEAS-18):** `Measurement.clone()`
deep-copies volumes and SD, copies the declared time-bean map into a **private** `HashMap`, copies
attributes **shallowly** (the link list is shared) and silently **drops `coord`**. `Measurements.clone()`
deep-copies the child measurements but constructs the container with `new Measurements(this.timeBean)` —
the **same** map instance — so `addRedundantTimeBean` on the clone mutates the original, and the cloned
container can declare a time bean that its own cloned children do not know about. A "copy" is therefore
neither independent nor internally consistent.

**CSV persistence, pinned (MEAS-19, MEAS-20):** `writeCSVMeasurements` writes five columns, rewriting
`,` to `__` in the measurement id; `updateMeasurementsFromFile` reads only columns 0–3, so a comma in an
id is **not** restored (the identity changes) and `ifForValidation` is silently dropped. The type column
is honoured for a *new* measurement, while an existing measurement keeps its own type.

## 8. `calibrator/ObjectiveCalculator` `[V]` `[T]`

Static utility computing four objective families, each with `TypeAADT` and
`TypeMeasurementAndTimeSpecific` branches, plus multi-objective decomposition by `MeasurementType`:

| Method | Quantity (legacy, as implemented) |
|---|---|
| `calcObjective(..., Type)` | TS: `Σ_m Σ_tb (obs − sim)²`. AADT: `Σ_m (Σ_{j≤m} r_j)²` — cumulative (OBJ-1). |
| `calcSDWeightedObjective(..., Type)` | TS: `Σ (1/(1+SD²))·r²`. AADT: `Σ_m (1/(1+Σ_tb SD²))·(cumulative r)²` (OBJ-1/8/9). Mutates input (OBJ-3). |
| `calcGEHObjective(..., Type)` | `Σ 2·r²/(obs+sim)` — i.e. **GEH²**, not GEH (OBJ-4). TS guards `obs+sim==0`; AADT does not (OBJ-5). |
| `calcSDWeightedGEHObjective(..., Type)` | `Σ 2·(1/(1+SD²))·r²/(obs+sim)`; **no** zero-denominator guard in either branch (OBJ-5). |
| `calcMultiObjective(...)` | Decomposes the above per `MeasurementType` (per-station AADT — inconsistent with the scalar AADT, OBJ-1); dereferences missing measurements (OBJ-6); NPE if a type bucket was removed (OBJ-7). |
| `calcObjective(real, ana, metaModels, param, Type)` | Builds meta-model predictions into a clone of `real`, then delegates. |

**The modern target is a pure function**; today it is not (OBJ-3) and its missing-data,
zero-denominator and accumulation policies are inconsistent.

## 9. `calibrator/CalibratorImpl` — trust-region calibration `[V]`

* State: `simMeasurements` and `anaMeasurements` (`Map<iteration, Measurements>`), `metaModels`
  (`Map<measurementId, Map<timeBeanId, MetaModel>>`), `params` (`Map<iteration, LinkedHashMap>`),
  `currentParam`/`trialParam`, `currentParamNo`, `iterationNo`.
* Trust-region constants: `TrRadius = 25` (field default), `maxTrRadius = 2.5·TrRadius` computed in
  the **field initialiser** (**CAL-1**), `minTrRadius = 0.001`, `maxSuccesiveRejection = 4`,
  `minMetaParamChange = 0.001`, `thresholdErrorRatio = 0.01`, `trusRegionIncreamentRatio = 1.25`,
  `trustRegionDecreamentRatio = 0.9`, `ObjectiveType = TypeMeasurementAndTimeSpecific`,
  `metaModelType = AnalyticalLinearMetaModelName`.
* `generateNewParam(sue, simMeasurements, gradFactory, metaModelType)` flow:
  1. record the simulation measurement for this iteration; run the analytical SUE (`perFormSUE`) and
     store `anaMeasurements[iterationNo]`;
  2. on `iterationNo == 0`: evaluate and persist the 0th objective;
  3. on `iterationNo > 0`: compute
     `CurrentSimObjective`, `CurrentMetaModelObjective`, `trialSimObjective`, `trialMetaModelObjective`;
     `SimObjectiveChange = Current − trial`, `MetaObjectiveChange = Current − trial`;
     `rouk = SimObjectiveChange / MetaObjectiveChange`;
     then accept/reject (see below);
  4. if `successiveRejection ≥ maxSuccesiveRejection` **and** internal calibration is enabled, call
     `sue.calibrateInternalParams(...)` and reset `successiveRejection`;
  5. recompute gradients if a gradient-based meta-model was accepted; otherwise null them;
  6. rebuild all meta-models (`createMetaModel`);
  7. choose the next trial point: random restart if `calcAverageMetaParamsChange() < minMetaParamChange`
     (except for `GradientBased_I`), otherwise `AnalyticalModelOptimizerImpl.performOptimization()`;
  8. increment `iterationNo`, persist params.
* **Acceptance policy** (the behaviour the brief asks to preserve pending review, CAL-2):
  ```
  if (SimObjectiveChange > 0 && rouk >= thresholdErrorRatio)  accept, grow TrRadius (×1.25, capped)
  else if (SimObjectiveChange > 0 && rouk <  thresholdErrorRatio) accept, no growth
  else                                                        reject, shrink (×0.9, floored), successiveRejection++
  ```
  Acceptance depends only on the **simulation** objective improving; `rho` controls only whether the
  radius grows. `rouk` is unguarded against `MetaObjectiveChange == 0` (CAL-3).
* **Acceptance policy, now driven by tests** (`CalibratorImplStateMachineTest`): iteration 0 performs no
  acceptance test; an improved simulation objective is accepted (`successiveRejection` stays 0 and the
  radius is not shrunk); a worsened one is rejected (`TrRadius * 0.9`, rejection count +1); the radius is
  floored at `minTrRadius`; and reaching `maxSuccesiveRejection` invokes the analytical model's
  `calibrateInternalParams` and resets the counter. Because the three outcomes each move `TrRadius`
  differently, the otherwise-internal `accepted` flag is observable from outside.
* Bookkeeping defects: `updateAnalyticalMeasurement` only updates when sizes **differ**, and even then it
  iterates the *existing* keys — so a fresh call is a no-op, equal sizes short-circuit everything, new
  iterations are never added, and a missing existing key throws (CAL-5, four tests);
  `createMetaModel` cannot actually reject null gradients (CAL-4); `parallelStream` + plain `HashMap`
  in the static meta-model factory (CAL-7); `calcAverageMetaParamsChange` divides by a possibly-zero
  `k`, yielding `NaN` that **silently disables** the random-restart guard rather than triggering it, and
  throws `NullPointerException` when `oldMetaModel` lacks a key (CAL-8); `drawRandomPoint` uses
  `Math.random()` (CAL-6). `maxTrRadius` is computed from the field default 25 before the constructor
  assigns the configured radius, so a configured radius above 62.5 leaves a region that can only shrink
  (CAL-1). `AnalyticalModelOptimizerImpl` starts from a **partially initialised** vector because of a
  double increment in its init loop (CAL-10), and prints `iprint=3` COBYLA output plus one line per
  variable on every call, so tests must capture stdout.
* `OptimizerName` defaults to `AnalyticalModelOptimizer.TROptimizerName`.

## 10. `calibrator/ParamReader` `[V]` `[T]`

* Parses `SubPopulation,Parameter Name,id,Lower Limit,UpperLimit,CurretValue,Code,IncludeIninitialParam`
  (header misspelled in the source and in the CSV). The **first line is always discarded as a header**,
  with no validation — a headerless file silently loses its first data row (PARAM-2b).
* Builds, all keyed by the **Code** column `part[6]`: `DefaultParam`, `paramLimit`,
  `initialParam`, `initialParamLimit`; plus `paramName` (list) and `ParamNoCode`
  (`subPopName + " " + paramName → code`). The `id` column (`part[2]`) is assigned and then
  **immediately overwritten** in both branches of the following `if/else`, so it is dead input.
* `ScaleUp(code→value)` → `paramName→value` (codes absent from the input are omitted, not defaulted);
  `ScaleDown` the reverse; `ScaleUpLimit`; `All` handling; `generateSubPopSpecificParam`;
  `SetParamToConfig(config, params)` writes the MATSim `Config` to a **CWD-relative**
  `config_Intermediate.xml` and reloads it; `setDefaultParams`.
* An unrecognised input throws from `ScaleUp`/`ScaleUpLimit` unless the mutable
  `allowUnkownParamaeterWhileScalingUp` flag is set, in which case unknown keys pass through.
* Defects (all now `VERIFIED`): silent relative-path fallback (PARAM-1), raw `split(",")` plus the dead
  `id` column (PARAM-2/2b), disk round trip (PARAM-3), duplicate-code inconsistency between the
  general and initial maps (PARAM-4), `ScaleDown` null keys and `generateSubPopSpecificParam`
  `split(" ")[1]` (PARAM-5), `containsAll` dispatch (PARAM-6), order-dependent collapse of
  conflicting scoped values (PARAM-7), GV-branch parameter omissions (PARAM-8), and the second
  `setDefaultParams` application path (PARAM-9).
* **Shared-code semantics — the production case.** A single code is deliberately reused across
  sub-populations, so `ParamNoCode` is a **many-to-one alias/group relation**, exactly as the class
  javadoc states ("same code parameters will be treated as one parameter"). Consequences, all pinned:
  `ScaleUp({code: v})` **fans out** the one canonical value to every scoped name; `SetParamToConfig`
  therefore writes the same value into each sharing sub-population's `ScoringParameterSet`; and
  `ScaleDown` **collapses** them back, so conflicting scoped values cannot both be represented and the
  survivor depends on iteration order (PARAM-7). A typed redesign **must** be able to express this
  relation, or it will silently change behaviour.
* `SetParamToConfig` has three materially different branches: **no sub-population** (bare parameter
  names), **non-GV** (all 13 scoring fields), and **GV** (matched by the *substring* `"GV"`; writes
  car/walk/performing but deliberately omits PT travel, PT distance cost, waiting, line switch and the
  mode constants, leaving the CSV values unapplied). `setDefaultParams` is a separate path that reads
  bare names and never touches `qsim`.
* `getDefaultTimeBean()` returns the five canonical Hong Kong periods
  (`BeforeMorningPeak`, `MorningPeak`, `AfterMorningPeak`, `EveningPeak`, `AfterEveningPeak`) — this
  is what the test fixtures mirror.

## 11. `matamodels/` — surrogates `[V]` `[T]`

All meta-models implement `MetaModel` and extend `MetaModelImpl`, which holds the measurement id,
`timeBeanId`, `noOfParams`, `noOfMetaModelParams`, `params` (`Map<iteration, LinkedHashMap>`),
`simData` (`Map<iteration, Double>`), `currentParamNo`, and the fitted `MetaModelParams`.
`MetaModel` declares the family names used throughout (`AnalyticalLinearMetaModelName`,
`AnalyticalQuadraticMetaModelName`, `LinearMetaModelName`, `QudaraticMetaModelName`,
`GradientBased_I/II/III_MetaModelName`).

The constructor requires iteration key **0** (`noOfParams = params.get(0).size()`), and the constructor
of `AnalyticLinearMetaModel` calls the fitter and then **clears** `params`, `simData` and
`analyticalData`.

| Family | Formal model | Fitter actually used |
|---|---|---|
| `AnalyticLinearMetaModel` | `y = β0 + β_A·A(x) + βᵀx` with Shepard distance weighting and a ridge penalty `‖β‖²·1e-3` | **COBYLA**, `findMinimum(…, rhobeg 0.5, rhoend 1e-6, iprint 0, maxfun 1500)`, started at `x = all ones`; the returned `CobylaExitStatus` is **discarded**. The other four fitters are **unreachable** (MODEL-2) |
| `AnalyticalQuadraticMetaModel` | quadratic in `A(x)` and `x` | `[U]` |
| `LinearMetaModel` | `y = β0 + βᵀx` | `[U]` |
| `QuadraticMetaModel` | quadratic in `x` | `[U]` |
| `GradientBasedMetaModel` (I) | uses supplied sim/ana gradients | `[U]` |
| `GradientBaseOptimizedMetaModel` (II) | gradient meta-model with an internal fit | `[U]` |
| `GradientOptimizedMetaModel` (III) | gradient meta-model with optimized weighting | `[U]` |
| `SimAndAnalyticalGradientCalculator` | **numerical** gradient calculator (finite differences) for the sim/ana models — must be clearly distinguished from ODEstimation's analytic derivatives | `[V]` by name/role; internals `[U]` |
| `MatrixBasedUnconstrainedAdam`, `MatrixBasedUnconstrainedGD` | first-order update rules used by the unreachable Adam fitter | `[V]` by role |

**Weighting, now pinned exactly:** `calcEuclDistanceBasedWeight(params, i, currentParamNo)` is
`1 / (1 + ‖x_i − x_current‖)` — `1.0` at the reference point itself, decaying with Euclidean distance.
Two caveats, both verified: the distance sums over the **reference point's key set only**, so it is
**asymmetric** and throws `NullPointerException` when the reference has a key the compared point lacks
(MODEL-6).

**Fitted model, now pinned:** with the live path the scaling fields are at their identity defaults, so
`calcMetaModel` is the plain affine model `β0 + β_A·A + βᵀx` (MODEL-4). The declared objective is a
weighted ridge problem whose closed-form solution is computed independently in
`AnalyticLinearMetaModelOracleTest`. **The live fitter does not attain it**: on a dataset with O(1)–O(10)
coefficients, COBYLA exhausts `maxfun = 1500` (`MAX_ITERATIONS_REACHED`, status ignored) and returns a
point whose objective is **≈1076×** the optimum (MODEL-5). With O(1) coefficients near the start the
same code *does* reach the optimum, which localises the fault to the optimizer setup rather than the
objective or the model.

## 12. `Utils/` and the MATLAB dead end `[V]`

* `MapToArray` (PRAISEHK version): converts a `Map<T,Double>` to a `double[]` using the map's
  **iteration order**. Ordering determinism is a known risk (`PRAISE_ODE_RELATIONSHIP.md` §4).
* `Tuple`, `TruncatedNormal` (departure-time distribution), `LinkVehicleCompReader` (MATSim-HK
  vehicle-composition reader).
* `MatlabObj`, `MatlabResult`, `Trial`, `MatlabOptimizer`: `MatlabOptimizer.performOptimization()`
  has every MATLAB statement commented out and returns `null`. The four MATLAB JARs (~27 MB) are dead
  weight. Do not delete until a test pins the `null` behaviour.

## 13. `matsimIntegration/` — the MATSim boundary

Classified in `PRAISE_MATSIMHK_RELATIONSHIP.md` §4 alongside the fork's packages. Summary:

* **(a) core adapter worth preserving** — event handlers that turn MATSim events into observations:
  `LinkCountEventHandler`, `LinkPCUCountEventHandler`, `AverageOccupancyEventHandler`,
  `TravelTimeEventHandler`, `FareLinkVolumeCountEventHandler`, `SmartCardEntryAndExitEventHandler`,
  `MTRPassengerFlowCounter`, plus `MeasurementsStorage`.
* **(b) version-specific plumbing** — `AnaModelCalibrationModule`, `AnaModelControlerListener`,
  `LinkPCUCountModule`, `LinkPCUCountControlerListener`, `RouteAndODGeneratorModule`,
  `RoutesAndODGeneratorControllerListener`, `SimRun`, `SignalFlowReductionGenerator`.
* `[U]` for details of each handler's event arithmetic; these are phase 10 work.
