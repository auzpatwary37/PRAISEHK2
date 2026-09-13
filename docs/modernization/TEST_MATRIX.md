# TEST_MATRIX — mathematical behaviour coverage

The meaningful metric is **mathematical behaviour coverage**, not line/branch coverage. Each row is
an equation, algorithm or component; each column records whether that behaviour is protected.

Legend: **C** = characterization test (pins legacy behaviour), **O** = independent oracle test
(hand-computed / mathematically independent), **B** = boundary/edge test, **I** = integration test.
`—` = absent. `P` = planned (this roadmap).

Snapshot: **82 deterministic tests, 0 failures, ~9 s**, runnable offline
(`mvn -o test` in `MetaModelCalibration`).

---

## 1. Analytic assignment / SUE core

| Component / equation | C | O | B | I | Tests | Notes |
|---|---|---|---|---|---|---|
| `CNLLink` free-flow time `t0 = L/v` | ✔ | ✔ | ✔ | — | `CNLLinkTravelTimeTest.freeFlowTravelTimeAtZeroFlow` | oracle 50 s |
| `CNLLink` BPR `t = t0(1+α(v/c)^β)` | ✔ | ✔ | ✔ | — | `bprTravelTimeAtHalfCapacity`, `travelTimeIncreasesWithVolume` | oracle 50.46875 s; monotonicity |
| `CNLLink` transit PCU contribution | ✔ | ✔ | — | — | `transitVolumeContributesToPcuLoad` | oracle 87.96875 s |
| `CNLLink` residual car volume | ✔ | ✔ | — | — | `residualVolumeContributesToPcuLoad` | |
| `CNLLink` `CapacityMultiplier` scaling | ✔ | ✔ | — | — | `capacityMultiplierScalesLoadAndCapacity` | oracle 57.5 s |
| `CNLLink` `gcRatio` scaling | ✔ | ✔ | — | — | `gcRatioScalesCapacity` | oracle 57.5 s |
| `CNLLink` BPR α/β from analytical params | ✔ | ✔ | — | — | `bprExponentsComeFromAnalyticalParams` | oracle 51.25 s |
| `CNLLink` train branch (unit discontinuity) | ✔ | ✔ | ✔ | — | `trainLinkUsesSeparateBranch` | **REVIEW_REQUIRED LINK-1** |
| `CNLLink.getCapacityPeriod()` (returns 0) | — | — | — | — | — | P (LINK-2) |
| `CNLRoute.getTravelTime` (link sum) | — | P | — | — | — | P (fixture C ready) |
| `CNLRoute.calcRouteUtility` (car utility) | — | P | — | — | — | P; formula in `LEGACY_BEHAVIOR.md` |
| `CNLTransitRoute` utility (fare/wait/transfer) | — | P | — | — | — | P (phase 2) |
| `CNLTransitDirectLink` travel components | — | P | — | — | — | P |
| `CNLTransitTransferLink` travel components | — | P | — | — | — | P |
| `CNLSUEModel` MSA β sequence / update weight | — | P | — | — | — | P (phase 8) |
| `CNLSUEModel` convergence error / stopping rule | — | P | — | — | — | P |
| `CNLSUEModel` logit route split | — | P | — | — | — | P |
| `CNLSUEModel` OD demand conservation | — | P | — | — | — | P |
| `CNLSUEModel` link-flow aggregation | — | P | — | — | — | P |
| `CNLSUEModel` car/PT mode split | — | P | — | — | — | P |
| `CNLSUEModel` sub-populations | — | P | — | — | — | P |
| `SUEModelContTime` (vs `CNLSUEModel`) | — | P | — | — | — | P; documented as *not* interchangeable (UNVERIFIED — must be confirmed) |
| internal param calibration (BPRα/β, LinkMiu, ModeMiu, Transferα/β) | — | P | — | — | — | P (phase 9) |

## 2. Objective functions

| Component / equation | C | O | B | I | Tests | Notes |
|---|---|---|---|---|---|---|
| `calcObjective` TS squared error | ✔ | ✔ | ✔ | — | `SquaredError.singleResidual`, `multipleMeasurementsAndTimeBeans`, `ExactMatch.allObjectiveTypesAreZeroWhenPerfect` | |
| `calcObjective` AADT accumulation | ✔ | ✔ | ✔ | — | `aadtIsCumulativeAcrossMeasurements`, `aadtSingleMeasurementMatchesPerStation` | **REVIEW_REQUIRED OBJ-1** |
| missing measurement / time bean | ✔ | — | ✔ | — | `MissingData.*` | **OBJ-2** (TS skips, AADT NPE) |
| `calcSDWeightedObjective` TS weight `1/(1+SD²)` | ✔ | ✔ | ✔ | — | `SdWeighting.weightIsOneOverOnePlusSdSquared`, `absentSdIsTreatedAsZero` | **OBJ-8** |
| `calcSDWeightedObjective` input mutation | ✔ | — | ✔ | — | `sdWeightedObjectiveMutatesInput` | **OBJ-3** |
| `calcSDWeightedObjective` AADT mixed scope | ✔ | — | — | — | `sdWeightedAadtMixedAccumulation` | **OBJ-9** |
| `calcGEHObjective` = GEH² not GEH | ✔ | ✔ | — | — | `GehTests.gehObjectiveIsSquaredGeh` | **OBJ-4** |
| `calcGEHObjective` zero denominator | ✔ | — | ✔ | — | `gehTsGuardsZeroDenominator`, `gehAadtHasNoZeroGuard` | **OBJ-5** |
| `calcSDWeightedGEHObjective` zero denominator | ✔ | — | ✔ | — | `sdWeightedGehTsHasNoZeroGuard`, `sdWeightedGehAadtHasNoZeroGuard` | **OBJ-5** |
| `calcSDWeightedGEHObjective` weighting | ✔ | ✔ | — | — | `sdWeightedGehWeightsByOneOverOnePlusSdSquared` | |
| `calcMultiObjective` decomposition by type | ✔ | ✔ | — | — | `MultiObjective.decomposesByMeasurementType` | |
| `calcMultiObjective` missing data | ✔ | — | ✔ | — | `multiObjectiveTsThrowsOnMissingMeasurement` | **OBJ-6** |
| `calcMultiObjective` AADT per-station | ✔ | ✔ | — | — | `multiObjectiveAadtIsPerMeasurement` | **OBJ-1** inconsistency |
| sub-objective filtering by `MeasurementType` | ✔ | ✔ | — | — | `subObjectiveFilteringByMeasurementType` | |
| meta-model-based objective overload | — | — | — | — | — | P (needs meta-models, phase 6) |
| purity of objective evaluation | — | — | — | — | — | P — the modern target |

## 3. Measurements

| Component / behaviour | C | O | B | I | Tests | Notes |
|---|---|---|---|---|---|---|
| `putVolume` store + auto `SD=0` | ✔ | ✔ | ✔ | — | `MeasurementTest.putVolumeStoresValueAndDefaultSd`, `putVolumeReplaces` | |
| `putVolume`/`putSD` unknown time bean ignored | ✔ | — | ✔ | — | `putVolumeIgnoresUnknownTimeBean`, `putSdIgnoresUnknownTimeBean` | |
| `getVolume` unset → null | ✔ | — | ✔ | — | `getVolumeUnsetIsNull` | |
| default empty link list | ✔ | — | — | — | `newMeasurementHasEmptyLinkList` | |
| `Measurement.clone` volumes/SD deep | ✔ | — | — | — | `cloneCopiesVolumesAndSd` | |
| `Measurement.clone` attributes shallow | ✔ | — | — | — | `cloneSharesAttributeObjects` | **MEAS-1** |
| `Measurements` byType pre-population | ✔ | ✔ | — | — | `byTypePrePopulatedForAllTypes` | |
| `addMeasurement` routing | ✔ | — | — | — | `addRoutesToItsOwnBucket` | |
| `Measurements.clone` deep for measurements | ✔ | — | — | — | `cloneIsDeepForMeasurements` | |
| `Measurements.clone` drops attributes | ✔ | — | — | — | `cloneDoesNotCopyContainerAttributes` | **MEAS-2** |
| `applyFactor` | ✔ | ✔ | — | — | `applyFactorScalesVolumesAndSd` | |
| `resetMeasurements` | ✔ | ✔ | — | — | `resetMeasurementsZeroesVolumes` | |
| `removeMeasurement` / `removeMeasurementsByType` | ✔ | — | ✔ | — | `removeMeasurementRemovesFromBothIndices`, `removeMeasurementsByTypeRemovesBucketKey` | **OBJ-7** risk |
| `addRedundantTimeBean` | ✔ | ✔ | — | — | `addRedundantTimeBeanAddsOnlyMissingBeans` | |
| CSV write/read round trip | ✔ | ✔ | — | — | `writeCsvHeaderAndRows`, `updateFromFileRoundTrip` | |
| CSV malformed input | ✔ | — | ✔ | — | `updateFromFileIsBrittle` | |
| XML round trip: volume/SD/links/gradient | ✔ | ✔ | — | — | `linkVolumeRoundTrip` | |
| XML element ordering assumption | ✔ | — | — | — | `timeBeansAreWrittenFirst` | |
| XML round trip: smartCard attributes | ✔ | — | — | — | `smartCardEntryRoundTrip` | |
| `Variables` serialization | ✔ | — | ✔ | — | `variablesAttributeCannotBeSerialized` | **MEAS-5/6/7** |
| writer silent failure | ✔ | — | ✔ | — | `writerSwallowsExceptionsSilently` | **MEAS-6** |
| `MeasurementType.linkVolume` aggregation + gradient sum | ✔ | ✔ | — | — | `linkVolumeAggregatesVolumeAndGradient` | |
| `linkVolume` gradient absent | ✔ | — | ✔ | — | `linkVolumeThrowsWhenGradientsAreAbsent` | **MEAS-3a** |
| `linkVolume` partial gradient | ✔ | — | ✔ | — | `linkVolumeSilentlyDropsLinksWithoutGradient` | **MEAS-3b** |
| `linkVolume` id fallback for empty link list | ✔ | — | ✔ | — | `linkVolumeFallsBackToMeasurementIdAsLink` | |
| `linkVolume` empty volumes / output | ✔ | — | ✔ | — | `linkVolumeIsSilentNoOpWhenVolumesEmpty` | |
| `linkTravelTime` first link only | ✔ | ✔ | — | — | `linkTravelTimeUsesFirstLinkOnly` | |
| `linkTravelTime` missing link → 0 | ✔ | — | ✔ | — | `linkTravelTimeMissingLinkLeavesZero` | |
| `fareLinkVolume` fallback ineffective | ✔ | — | ✔ | — | `fareLinkVolumeFallbackIsIneffective` | **MEAS-4** |
| `fareLinkVolume` container present | ✔ | ✔ | — | — | `fareLinkVolumeReadsContainerWhenPresent` | |
| `fareLinkVolumeCluster` installs fallback | ✔ | ✔ | — | — | `fareLinkVolumeClusterInstallsFallback` | **MEAS-4** asymmetry |
| `fareLinkVolumeCluster` missing fares | ✔ | — | ✔ | — | `fareLinkVolumeClusterRequiresFareLinks` | |
| `MaaSPacakgeUsage` literal `"All"` key | ✔ | — | ✔ | — | `maasPackageUsageWritesAllLiteralKey` | **MEAS-8** |
| `averagePTOccumpancy` missing map | ✔ | — | ✔ | — | `averagePtOccupancyThrowsWhenAbsent` | **MEAS-9** |
| `TransitPhysicalLinkVolume` | — | — | — | — | — | P (**MEAS-9b**) |
| `maasSpecificFareLinkVolume` | — | — | — | — | — | P |
| `smartCardEntryAndExit` | — | — | — | — | — | P |

## 3b. Vendored transit fare contract (`transit.fare`)

`FareCalculator` and `FareLink` were vendored from the HK MATSim fork (verbatim except the package
declaration). `FareLink`'s grammar is a serialization contract for `MeasurementType`, so it is pinned.

| Component / behaviour | C | O | B | I | Tests | Notes |
|---|---|---|---|---|---|---|
| `FareLink` NetworkWideFare parse | ✔ | ✔ | — | — | `FareLinkTest.parsesNetworkWideFare` | `type___board___alight___mode` |
| `FareLink` NetworkWideFare round trip | ✔ | ✔ | — | — | `networkWideFareRoundTrips` | |
| `FareLink` InVehicleFare parse | ✔ | ✔ | — | — | `parsesInVehicleFare` | `type___line___route___board___alight___mode` |
| `FareLink` InVehicleFare round trip | ✔ | ✔ | — | — | `inVehicleFareRoundTrips` | |
| `FareLink` unknown type rejected | ✔ | ✔ | — | — | `rejectsUnknownType` | |
| `FareLink` truncated description | ✔ | — | ✔ | — | `truncatedDescriptionThrowsAIOOBE` | **FARE-1** (raw AIOOBE) |
| `FareLink` from measurement id | ✔ | — | ✔ | — | `measurementIdMustBeAValidFareDescription` | what `MeasurementType` does |
| `FareLink` constructor validation | ✔ | ✔ | ✔ | — | `fullConstructorValidates` | type/stop/mode combinations |
| `FareLink` serialization constants | ✔ | — | — | — | `serialisationConstantsAreStable` | feeds XML writer/reader |
| `FareLink` separator collision | ✔ | — | ✔ | — | `separatorIsNotEscaped` | **FARE-2** |
| `FareCalculator` contract | — | — | — | — | — | P — no implementation exists in this module (**UNVERIFIED**, see MATSimHK doc §5.5) |

## 4. Calibration / meta-models / parameters

| Component / algorithm | C | O | B | I | Tests | Notes |
|---|---|---|---|---|---|---|
| `ParamReader` valid file, bounds, codes | — | — | — | — | — | P (phase 5) |
| `ParamReader` missing file fallback | — | — | — | — | — | P (**PARAM-1**) |
| `ParamReader` malformed CSV / missing columns | — | — | — | — | — | P (**PARAM-2**) |
| `ScaleUp` / `ScaleDown` / `ScaleUpLimit` | — | — | — | — | — | P (**PARAM-5/6**) |
| `SetParamToConfig` disk round trip | — | — | — | — | — | P (**PARAM-3**) |
| `AnalyticLinearMetaModel` `y = β0 + βA·A(x) + βᵀx` | — | P | — | — | — | P (phase 6); oracle = weighted ridge |
| `AnalyticLinearMetaModel` weighting | — | — | — | — | — | P |
| `AnalyticLinearMetaModel` 5 fitting paths equivalence | — | — | — | — | — | P (**MODEL-2**) |
| `AnalyticLinearMetaModel` dense-index assumption | — | — | P | — | — | P (**MODEL-1**) |
| `AnalyticLinearMetaModel` static `errorT` | — | — | P | — | — | P (**MODEL-3**) |
| `LinearMetaModel`, `QuadraticMetaModel` | — | P | — | — | — | P |
| `AnalyticalQuadraticMetaModel` | — | P | — | — | — | P |
| `GradientBasedMetaModel` (I/II/III) | — | — | — | — | — | P |
| `SimAndAnalyticalGradientCalculator` | — | P | — | — | — | P; must be distinguished from analytic derivatives |
| `CalibratorImpl` trust ratio / accept / reject | — | P | — | — | — | P (phase 7) |
| `CalibratorImpl` `maxTrRadius` init bug | — | — | P | — | — | P (**CAL-1**) |
| `CalibratorImpl` NaN / Inf / zero predicted reduction | — | — | P | — | — | P (**CAL-3**) |
| `createMetaModel` null gradient handling | — | — | P | — | — | P (**CAL-4**) |
| `updateAnalyticalMeasurement` gate | — | — | P | — | — | P (**CAL-5**) |
| `drawRandomPoint` seeded RNG | — | — | P | — | — | P (**CAL-6**) |
| `parallelStream` determinism | — | — | P | — | — | P (**CAL-7**) |
| `calcAverageMetaParamsChange` k=0 | — | — | P | — | — | P (**CAL-8**) |

## 5. Differentiation (ODEstimation) — blocked

| Component / derivative | C | O | B | I | Tests | Notes |
|---|---|---|---|---|---|---|
| `GradientUtils.getLinkTravelTimeGrad` = `t0αβv^(β−1)/c^β · dv/dθ` | — | P | — | — | — | P; **blocked**: ODEstimation cannot build (undeclared PRAISEHK dep) |
| `GradientUtils.getTransitLinkTravelTimeGrad` (Eq. 64–66) | — | P | — | — | — | P |
| `GradientUtils.getLinkFlowGrad` (MSA-consistent, Eq. 52) | — | P | — | — | — | P |
| `GradientUtils.getTrLinkVolumeGrad` (Eq. 53–54) | — | P | — | — | — | P |
| logit route-probability derivative `P(δ−ΣP)` | — | P | — | — | — | P |
| mode-choice derivative conservation `∂ΣP_mode/∂θ = 0` | — | P | — | — | — | P |
| route-flow product rule | — | P | — | — | — | P |
| link-flow aggregation `v_a = Σ δ_ar f_r` | — | P | — | — | — | P |
| finite-difference oracle harness (h ∈ 1e-4,1e-5,1e-6) | — | — | — | — | — | P (phase 0 of the ODE PR) |
| seeded gradient fire test | — | — | — | — | — | P |
| `MapToArray` variable ordering determinism | — | — | — | — | — | P (**critical**) |
| `gradMultiplier` / `Clip` effect on the model derivative | — | — | — | — | — | P |

## 6. Build / harness

| Behaviour | Status | Evidence |
|---|---|---|
| Clean clone builds offline | ✔ | `mvn -o test` in `MetaModelCalibration`; the MATSim-HK fork is no longer a build dependency |
| No Hong Kong production data required | ✔ | all fixtures in-memory; only `paramReaderTrial1.csv` values copied as literals |
| No absolute filesystem paths in tests | ✔ | `@TempDir` used; legacy absolute-path helper excluded |
| No MATLAB required | ✔ | MATLAB jars unused; `MatlabOptimizer` returns null |
| No network required | ✔ | offline run verified |
| No `Math.random()` in tests | ✔ | new tests deterministic; legacy nondeterministic test excluded |
| No `HashMap` iteration-order dependence | ✔ | CSV assertions are order-independent |
| Legacy nondeterministic/hanging test quarantined | ✔ | surefire `<excludes>` in `MetaModelCalibration/pom.xml` |

## 7. CI quality gate (planned)

* `mvn -o -B test` (deterministic, offline) — the primary gate, run in `MetaModelCalibration`.
* Do **not** gate on code-coverage percentage.
* Add `mvn dependency:analyze` once the pom is cleaned, to catch undeclared/load-bearing deps.
* Static checks only after the build is reproducibly green.
