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

## 4. `analyticalModelImpl/CNLTransitRoute` and transit links — `[U]` mostly

* `CNLTransitRoute` (755 LOC) computes the transit utility from fare (`dynamicTransitRouter.fareCalculators.FareCalculator`,
  `transitFareAndHandler.FareLink`), in-vehicle time, waiting time, transfer penalties, walking
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
* **Weighted MSA**: `beta` is a `Map<String /*timeBeanId*/, ArrayList<Double>>` explicitly commented
  "related to weighted MSA of the SUE". The sequence is seeded with `1.0` and extended by adding
  `gammaMSA` or `alphaMSA` depending on the branch; the update weight used when averaging new flows
  is `1/beta(counter-1)`. Tunables: `setMSAAlpha`, `setMSAGamma`, `setTollerance`. **The exact branch
  condition, the initial values of alpha/gamma, and the stopping rule are `[U]` and are the subject
  of roadmap phase 8.**
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
* Bookkeeping defects: `updateAnalyticalMeasurement` only updates when sizes **differ** (CAL-5);
  `createMetaModel` cannot actually reject null gradients (CAL-4); `parallelStream` + plain `HashMap`
  in the static meta-model factory (CAL-7); `calcAverageMetaParamsChange` divides by a possibly-zero
  `k` (CAL-8); `drawRandomPoint` uses `Math.random()` (CAL-6).
* `OptimizerName` defaults to `AnalyticalModelOptimizer.TROptimizerName`.

## 10. `calibrator/ParamReader` `[V]`

* Parses `SubPopulation,Parameter Name,id,Lower Limit,UpperLimit,CurretValue,Code,IncludeIninitialParam`
  (header misspelled in the source and in the CSV).
* Builds, all keyed by the **Code** column `part[6]`: `DefaultParam`, `paramLimit`,
  `initialParam`, `initialParamLimit`; plus `paramName` (list) and `ParamNoCode`
  (`subPopName + " " + paramName → code`).
* `ScaleUp(code→value)` → `paramName→value`; `ScaleDown` the reverse; `ScaleUpLimit`; `All` handling;
  `generateSubPopSpecificParam`; `SetParamToConfig(config, params)` writes the MATSim `Config` to
  `config_Intermediate.xml` and reloads it; `setDefaultParams`.
* Defects: silent relative-path fallback (PARAM-1), raw `split(",")` (PARAM-2), disk round trip
  (PARAM-3), duplicate-code collapse (PARAM-4), `ScaleDown` null keys (PARAM-5), `containsAll`
  dispatch (PARAM-6).
* `getDefaultTimeBean()` returns the five canonical Hong Kong periods
  (`BeforeMorningPeak`, `MorningPeak`, `AfterMorningPeak`, `EveningPeak`, `AfterEveningPeak`) — this
  is what the test fixtures mirror.

## 11. `matamodels/` — surrogates `[V]`

All meta-models implement `MetaModel` and extend `MetaModelImpl`, which holds the measurement id,
`timeBeanId`, `noOfParams`, `noOfMetaModelParams`, `params` (`Map<iteration, LinkedHashMap>`),
`simData` (`Map<iteration, Double>`), `currentParamNo`, and the fitted `MetaModelParams`.
`MetaModel` declares the family names used throughout (`AnalyticalLinearMetaModelName`,
`AnalyticalQuadraticMetaModelName`, `LinearMetaModelName`, `QudaraticMetaModelName`,
`GradientBased_I/II/III_MetaModelName`).

| Family | Formal model | Fitter actually used |
|---|---|---|
| `AnalyticLinearMetaModel` | `y = β0 + β_A·A(x) + βᵀx` with Euclidean distance-based weighting and a ridge penalty `‖β‖²·1e-3` | **COBYLA** (`Cobyla.findMinimum(..., 1500)`); four other fitters exist as dead code (MODEL-2) |
| `AnalyticalQuadraticMetaModel` | quadratic in `A(x)` and `x` | `[U]` — reads `.md`-style; verify |
| `LinearMetaModel` | `y = β0 + βᵀx` | `[U]` |
| `QuadraticMetaModel` | quadratic in `x` | `[U]` |
| `GradientBasedMetaModel` (I) | uses supplied sim/ana gradients | `[U]` |
| `GradientBaseOptimizedMetaModel` (II) | gradient meta-model with an internal fit | `[U]` |
| `GradientOptimizedMetaModel` (III) | gradient meta-model with optimized weighting | `[U]` |
| `SimAndAnalyticalGradientCalculator` | **numerical** gradient calculator (finite differences) for the sim/ana models — must be clearly distinguished from ODEstimation's analytic derivatives | `[V]` by name/role; internals `[U]` |
| `MatrixBasedUnconstrainedAdam`, `MatrixBasedUnconstrainedGD` | first-order update rules used by the dead Adam fitter | `[V]` by role |

Weighting (from the live path): `calcEuclDistanceBasedWeight(params, i, currentParamNo)` — a
distance-based weight favouring sample points near the current parameter point. The exact expression
is `[U]` and must be pinned by tests before the meta-model weighting is touched.

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
