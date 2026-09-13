# REVIEW_REQUIRED — suspected defects, recorded but NOT fixed

Per the mission: a characterization test proves what the legacy code **does**, not that it is
**correct**. Where legacy behaviour differs from the mathematically expected behaviour, nothing is
changed. Each item below records:

* **Where** — file and method
* **Legacy behaviour** (observed)
* **Mathematically expected behaviour**
* **Evidence** — the characterization test that pins the current behaviour
* **Proposed resolution**
* **Status** — `VERIFIED` = behaviour reproduced by a passing test; `READ` = established by reading
  the code, test still to be written.

No formula, tolerance, optimizer constant or weighting was modified in this PR.

---

## ObjectiveCalculator (`calibrator/ObjectiveCalculator.java`)

### OBJ-1 — `VERIFIED` — AADT objective accumulates station counts *across* measurements
* **Where:** `calcObjective(..., TypeAADT)`; also `calcSDWeightedObjective`, `calcGEHObjective`,
  `calcSDWeightedGEHObjective` (AADT branches).
* **Legacy:** `stationCountReal` and `stationCountAnaOrSim` are declared **outside** the
  `for(Measurement m : ...)` loop, so they accumulate over all measurements. The objective adds
  `(Σ_{j<=m} r_j)²` at each measurement instead of `r_m²`.
* **Expected:** an AADT objective should be `Σ_m (Σ_tb r_{m,tb})²` — i.e. station totals, then a
  sum over stations, not partial sums squared.
* **Evidence:** two stations with residuals 10 and 10 give **500** (`10² + 20²`), where the
  per-station expectation is **200**.
  `ObjectiveCalculatorTest.SquaredError.aadtIsCumulativeAcrossMeasurements`.
* **Aggravating inconsistency:** `calcMultiObjective(..., TypeAADT)` and
  `calcObjective(..., Type, Set<MeasurementType>)` declare the counters **inside** the measurement
  loop and are therefore *per-station* (same input → **200**).
  `ObjectiveCalculatorTest.MultiObjective.multiObjectiveAadtIsPerMeasurement`.
* **Proposed resolution:** decide which is intended (published algorithm), add an oracle test, then
  make all AADT branches consistent. Do **not** pick one silently.

### OBJ-2 — `VERIFIED` — AADT logs a missing measurement/time bean and then dereferences it
* **Where:** AADT branches; the missing-data checks only `logger.error(...)` with **no `continue`**,
  unlike the `TypeMeasurementAndTimeSpecific` branches which do `continue`.
* **Legacy:** `calcObjective(..., AADT)` throws `NullPointerException` if the simulated container
  lacks the measurement *or* the time bean. TS skips it.
* **Expected:** consistent, explicit handling (skip, or fail fast with a clear error).
* **Evidence:** `ObjectiveCalculatorTest.MissingData.aadtThrowsOnMissingMeasurement`,
  `...missingTimeBeanDiffersByType`.
* **Proposed resolution:** make missing-data policy an explicit, tested contract.

### OBJ-3 — `VERIFIED` — `calcSDWeightedObjective` mutates its input
* **Where:** TS branch: `if(m.getSD().get(timeBeanId)==null) m.putSD(timeBeanId, 0);`
* **Legacy:** inserts `SD = 0` into the **observed** measurement object, mutating caller state.
* **Expected:** objective evaluation should be a pure function of its inputs.
* **Evidence:** `ObjectiveCalculatorTest.SdWeighting.sdWeightedObjectiveMutatesInput`
  (SD entry absent beforehand, `0.0` afterwards).
* **Note:** the common path hides this because `Measurement.putVolume` already auto-inserts `SD=0`;
  the mutation only shows when volumes are populated without `putVolume` (which `MeasurementType`
  itself does with `m.getVolumes().put(...)`).
* **Proposed resolution:** eventual calm: make objective evaluation pure (no writes to inputs).

### OBJ-4 — `VERIFIED` — the "GEH" objective sums GEH², not GEH
* **Where:** `calcGEHObjective` (both branches) and `calcSDWeightedGEHObjective`.
* **Legacy:** adds `2·(v−w)²/(v+w)`. Since `GEH = sqrt(2(v−w)²/(v+w))`, the accumulated quantity is
  `GEH²`.
* **Expected:** if a GEH statistic is intended, the term is `sqrt(2(v−w)²/(v+w))`.
* **Evidence:** observation 100 vs modelled 80 → legacy returns `800/180 = 4.4444…`;
  `GEH = 2.1081851…`, and `4.4444… = GEH²`.
  `ObjectiveCalculatorTest.GehTests.gehObjectiveIsSquaredGeh`.
* **Do not rename or change** — the name may be intentional ("GEH-style squared").
* **Proposed resolution:** confirm against the publication, then either rename to
  `calcSquaredGehObjective` or take the square root — with an oracle test.

### OBJ-5 — `VERIFIED` — zero-denominator behaviour is inconsistent
* **Where:** `calcGEHObjective` TS branch has `if(v+w==0) continue;`. The AADT branch, and both
  branches of `calcSDWeightedGEHObjective`, have **no** guard.
* **Legacy:** observation 0 / modelled 0 gives
  * `calcGEHObjective` TS → **0**
  * `calcGEHObjective` AADT → **NaN**
  * `calcSDWeightedGEHObjective` TS → **NaN** (despite the unweighted TS variant guarding)
  * `calcSDWeightedGEHObjective` AADT → **NaN**
* **Expected:** one consistent rule (skip, or treat as 0), applied everywhere.
* **Evidence:** `ObjectiveCalculatorTest.GehTests.gehTsGuardsZeroDenominator`,
  `gehAadtHasNoZeroGuard`, `sdWeightedGehTsHasNoZeroGuard`, `sdWeightedGehAadtHasNoZeroGuard`.
* **Proposed resolution:** NaN in an objective silently poisons COBYLA/trust-region iteration; make
  it explicit and tested.

### OBJ-6 — `VERIFIED` — the multi-objective entry points dereference missing measurements
* **Where:** `calcMultiObjective(..., Type)` (TS and AADT branches) and
  `calcObjective(..., Type, Set<MeasurementType>)`.
* **Legacy:** they log the mismatch but do **not** `continue`, then dereference the missing
  measurement → `NullPointerException`. The scalar `calcObjective` TS path *does* `continue`.
* **Expected:** the same missing-data policy across all entry points.
* **Evidence:** `ObjectiveCalculatorTest.MultiObjective.multiObjectiveTsThrowsOnMissingMeasurement`.
* **Related — OBJ-7 `READ`:** `calcMultiObjective` calls
  `simOrAnaMeasurements.getMeasurementsByType().get(type).isEmpty()`. `Measurements` pre-populates
  every bucket, but `removeMeasurementsByType` (and `removeMeasurement`, when it empties a bucket)
  **removes the key**, making `.get(type)` `null` → NPE.

### OBJ-8 — `VERIFIED` — SD weighting is `1/(1+SD²)`, and AADT sums SD *variances* across time beans
* **Where:** `calcSDWeightedObjective`.
* **Legacy:** TS: `1/(1+SD²)`. AADT: `1/(1+Σ_tb SD²)`.
* **Expected:** a statistical weighting would normally be `1/SD²` (inverse variance). The legacy
  form is a *shrinkage* that equals 1 when `SD = 0` rather than being undefined — so it may be a
  deliberate regularisation, but it is not inverse-variance weighting.
* **Evidence:** SD 3 → weight `1/10 = 0.1`.
  `ObjectiveCalculatorTest.SdWeighting.weightIsOneOverOnePlusSdSquared`.
* **Proposed resolution:** document the intent; if inverse-variance is wanted, it needs a guard for
  `SD = 0` and an oracle test.

### OBJ-9 — `VERIFIED` — SD-weighted AADT mixes accumulation scopes
* **Where:** `calcSDWeightedObjective` AADT branch.
* **Legacy:** `sigma` is declared **inside** the measurement loop (per-measurement), while the
  station counts are **cumulative** (OBJ-1). The numerator and the weight therefore refer to
  different scopes.
* **Evidence:** residuals 10 and 10 with SD 1 and 2 → `1/(1+1)·10² + 1/(1+4)·20²` = `50 + 80 = 130`.
  `ObjectiveCalculatorTest.SdWeighting.sdWeightedAadtMixedAccumulation`.
* **Proposed resolution:** resolve together with OBJ-1.

---

## Measurement / Measurements

### MEAS-1 — `VERIFIED` — `Measurement.clone()` copies attributes shallowly
* **Where:** `Measurement.clone()` — `m.setAttribute(s, this.attributes.get(s))`.
* **Legacy:** the link-list `ArrayList` is **shared** between original and clone; mutating one
  mutates the other. Volumes and SD *are* deep-copied.
* **Expected:** a clone should not share mutable state.
* **Evidence:** `MeasurementTest.cloneSharesAttributeObjects`.
* **Risk:** the mission explicitly requires no test to depend on shared mutable state; production
  code that mutates a cloned measurement's link list would corrupt the original.

### MEAS-2 — `VERIFIED` — `Measurements.clone()` drops container-level attributes
* **Where:** `Measurements.clone()` does not copy `this.attributes`.
* **Legacy:** `Variables` (and any other container attribute) is **not** present on the clone.
* **Evidence:** `MeasurementsTest.cloneDoesNotCopyContainerAttributes`.
* **Proposed resolution:** decide whether the clone should carry `Variables`; the calibration code
  clones measurement containers heavily (`CalibratorImpl.CalcMetaModelPrediction`).

### MEAS-3 — `VERIFIED` — `MeasurementType.linkVolume` gradient handling is unsound
* **Where:** `MeasurementType.linkVolume.updateMeasurement`, final lines of the time-bean loop:
  ```java
  if(linkVolumeGradient.get(s).get(linkId)==null) {
      System.out.println("Gradients are not present. Only updating the volume.");
  } else { ... volumeGrad = ... }
  ...
  grad.put(s, volumeGrad.toArray());   // unconditional
  ```
* **Legacy (a):** if **no** link contributes a gradient (`linkVolumeGradient == null`, or no entry
  for any link), `volumeGrad` stays `null` and `volumeGrad.toArray()` throws
  **`NullPointerException`** — despite the message claiming "only updating the volume".
  Evidence: `MeasurementTypeTest.linkVolumeThrowsWhenGradientsAreAbsent`.
* **Legacy (b):** if **some** links have gradients and others do not, no exception is thrown, but the
  links without gradient entries are **silently dropped** from the aggregated gradient, so the
  gradient becomes inconsistent with the aggregated volume.
  Evidence: `MeasurementTypeTest.linkVolumeSilentlyDropsLinksWithoutGradient` (volume 150,
  gradient reflects only `L1`).
* **Expected:** either fail explicitly, or update the volume and omit the gradient entirely
  (consistently), with the two cases distinguished.
* **Proposed resolution:** make gradient absence a first-class, explicit outcome; add oracle tests
  comparing the aggregated gradient against a hand-computed sum.

### MEAS-4 — `VERIFIED` — `fareLinkVolume` fallback is dead code; `fareLinkVolumeCluster` is not
* **Where:** `MeasurementType.fareLinkVolume.updateMeasurement`.
* **Legacy:** when `modelOut.getFareLinkVolume() == null` the method builds a fallback map from
  `getMaaSSpecificFareLinkFlow()` into a **local** variable, but the accumulation then reads
  `modelOut.getFareLinkVolume().get(s).get(key)` — which is still `null`. The `NullPointerException`
  is swallowed by the surrounding `catch`, so the measurement silently becomes `0`.
* **Contrast:** `fareLinkVolumeCluster` calls `modelOut.setFareLinkVolume(fareLinkVolume)` in the
  same situation, so it **works**. The asymmetry is the defect.
* **Evidence:** `MeasurementTypeTest.fareLinkVolumeFallbackIsIneffective` (MaaS value 500 → volume 0),
  `fareLinkVolumeClusterInstallsFallback` (500 → volume 500).
* **Proposed resolution:** make both read the same resolved container. Note `fareLinkVolumeCluster`
  has a side effect on its input `SUEModelOutput` — that is also undesirable (purity).

### MEAS-5 — `VERIFIED` — the `Variables` attribute cannot be serialized at all
* **Where:** `MeasurementsWriter.write`:
  `variables.setAttribute(Integer.toString(i), var.get(i))`.
* **Legacy:** XML attribute names `"0"`, `"1"`, … are not valid XML `Name`s. The resulting
  `DOMException` is swallowed by the writer's catch-all, so **no file is produced at all**.
  `MeasurementsReader.readMeasurements` then returns **`null`** (it also swallows the `SAXException`),
  which NPEs every caller.
* **Expected:** variables must be serialized with valid names (e.g. an indexed child element or a
  `var0`/`var1` naming scheme), and serialization failure must be visible.
* **Evidence:** `MeasurementsXmlRoundTripTest.variablesAttributeCannotBeSerialized`.
* **Risk:** high — `Measurements.variablesAttributeName` is a documented public attribute in
  `Measurements`; note also that none of the committed sample files
  (`src/main/resources/Measurements*.xml`) contains a `Variables` element, which is why this was
  never noticed.

### MEAS-6 — `VERIFIED` — the writer swallows every exception
* **Where:** `MeasurementsWriter.write` ends with `catch(Exception e) { }` (empty body).
* **Legacy:** a failed write produces no error, no log, and no file. Callers cannot detect it.
* **Evidence:** `MeasurementsXmlRoundTripTest.writerSwallowsExceptionsSilently`;
  same mechanism as MEAS-5.
* **Proposed resolution:** propagate or log. This is the single change that would have surfaced
  MEAS-5 immediately.

### MEAS-7 — `VERIFIED` — the reader returns `null` on failure
* **Where:** `MeasurementsReader.readMeasurements` catches `SAXException`/`IOException` and
  `printStackTrace()`s, then returns the (null) field.
* **Legacy:** callers receive `null` instead of an actionable failure.
* **Evidence:** `MeasurementsXmlRoundTripTest.variablesAttributeCannotBeSerialized` (read == null).

### MEAS-8 — `VERIFIED` — `MaaSPacakgeUsage` writes to the literal key `"All"`
* **Where:** `m.getVolumes().put("All", modelOut.getMaaSPackageUsage().get(attr))`.
* **Legacy:** bypasses `putVolume` (so no `timeBean` validation) and leaves `SD` unset for `"All"`
  (later `applyFactor` would NPE on `getSD().get("All")`). `"All"` is not a declared time bean.
* **Evidence:** `MeasurementTypeTest.maasPackageUsageWritesAllLiteralKey`
  (volume present, `getSD().get("All") == null`).
* **Related — MEAS-8b `READ`:** `MaaSPacakgeUsage.parseAttribute` wraps the MaaS package name in a
  `FareLink` (`new FareLink(...)`), so reading a package name that is not a valid fare-link
  description throws. Inconsistent with `updateMeasurement`, which treats the attribute as a plain
  package-key string.

### MEAS-9 — `VERIFIED` — `averagePTOccumpancy` dereferences without a guard
* **Where:** `modelOut.getAveragePtOccupancyOnLink().get(s).get(linkId)`.
* **Legacy:** NPE when the occupancy map is absent.
* **Evidence:** `MeasurementTypeTest.averagePtOccupancyThrowsWhenAbsent`.
* **Related — MEAS-9b `READ`:** `TransitPhysicalLinkVolume.updateMeasurement` dereferences
  `modelOut.getTrainCount().get(timeBean).get(linkId)` and mutates volumes via
  `m.getVolumes().entrySet().forEach(v -> v.setValue(...))` — unguarded and a mutation during
  iteration of a `ConcurrentHashMap` (safe for the map, but the inner map may be absent).

---

## CNLLink (`analyticalModelImpl/CNLLink.java`)

### LINK-1 — `VERIFIED` — the `train` branch uses a different, flow-independent formula with a
3.6× unit discontinuity
* **Where:** `getLinkTravelTime`: `if(!this.link.getAllowedModes().contains("train")) { BPR } else {
  linkTravelTime = length / (freespeed*1000/3600); }`
* **Legacy:** for a link whose allowed modes contain `"train"` the travel time ignores volume,
  capacity, `BPRalpha`, `BPRbeta`, `CapacityMultiplier` and `gcRatio`, and evaluates
  `length / (freespeed · 1000/3600)`. For `length = 1000 m`, `freespeed = 20 m/s` this yields
  **180 s**, whereas the car branch gives a free-flow time of **50 s** — exactly **3.6×** larger.
* **Expected:** either the same unit convention as the car branch (`length/freespeed`, seconds), or an
  explicit km/h→m/s conversion applied consistently. A pure mode test must not change the time unit.
* **Evidence:** `CNLLinkTravelTimeTest.trainLinkUsesSeparateBranch` asserts 50.46875 s (car) vs 180 s
  (train) on the same physical link and documents the 3.6 ratio.
* **Proposed resolution:** confirm which branch is correct against the MTR line travel-time
  definition, then unify units. This changes travel times materially, so it must not be "fixed"
  without review.

### LINK-2 — `READ` — `AnalyticalModelLink.getCapacityPeriod()` returns a hard-coded `0`
* **Where:** `CNLLink.getCapacityPeriod()` → `return 0;` (with a `// TODO Auto-generated method
  stub`).
* Any consumer dividing by the capacity period would produce `Infinity`/`NaN`. Test to be written.

### LINK-3 — `READ` — `ifScalePt` is hard-coded `true`
* **Where:** `CNLLink.ifScalePt = true` with no setter. Consequently the transit PCU volume is always
  multiplied by `CapacityMultiplier`, **and** capacity is also multiplied by `CapacityMultiplier`.
  Both effects are intentional-looking but are untested and undocumented; a characterization test
  already pins the combined behaviour (`capacityMultiplierScalesLoadAndCapacity`).

---

## ParamReader (`calibrator/ParamReader.java`)

### PARAM-1 — `READ` — silent fallback to a **relative** default path
* `new ParamReader(fileLoc)`: if `fileLoc` does not exist, `this.paramFile = new File("src/main/resources/paramReaderTrial1.csv")` with **no warning**. The path is relative to the process CWD, so the same call succeeds or fails depending on where the JVM was launched. A missing *requested* file silently yields the wrong parameters.

### PARAM-2 — `READ` — raw `split(",")` parsing
* `line.split(",")` drops trailing empty fields, so a row with empty trailing columns makes
  `part[6]`/`part[7]` throw `ArrayIndexOutOfBoundsException`. No header validation; no quoted-field
  handling despite `commons-csv` being declared (and unused).

### PARAM-3 — `READ` — `SetParamToConfig` writes to disk and reloads
* `new ConfigWriter(config).write("config_Intermediate.xml"); Config configOut = ConfigUtils.loadConfig("config_Intermediate.xml");` — a relative path, a filesystem round trip, and a classpath/encoding dependency inside what should be a pure transformation. Also `System.out.println(config.isLocked())` prints state on every call.

### PARAM-4 — `READ` — duplicate parameter codes silently overwrite
* `DefaultParam.put(part[6], …)`, `paramLimit.put(part[6], …)`, `initialParam.put(part[6], …)` are keyed by the **Code** column (not `paramName`/`paramId`). Duplicate codes collapse last-wins with no warning. `subParamAndLimit.csv` legitimately repeats codes (e.g. code `3`, `4`, `8`, `13` across sub-populations), and `ParamReader`'s internal maps key on code, so cross-sub-population codes collide by design.

### PARAM-5 — `READ` — `ScaleDown` can emit `null` values
* `ScaleDown` returns early (input unchanged) when no key is in `ParamNoCode`. Otherwise it does
  `scaledDownParam.put(this.ParamNoCode.get(s), param.get(s))` for **every** key `s`, so keys absent
  from `ParamNoCode` produce a `null` key with a non-null value. `generateSubPopSpecificParam` then
  does `s.split(" ")[1]` → `ArrayIndexOutOfBoundsException` for any key without a space.

### PARAM-6 — `READ` — `ScaleUp`/`ScaleUpLimit` dispatch on `containsAll` and can misroute
* `if (this.ParamNoCode.values()).containsAll(trialParam.keySet())` then an empty body; the
  `else if` / `else` branches handle the rest. When `allowUnkownParamaeterWhileScalingUp == false`
  an unrecognised input throws — but when it is `true` the method silently passes unknown
  parameters through, which is a configuration-dependent behaviour change.

---

## CalibratorImpl (`calibrator/CalibratorImpl.java`) — trust region

### CAL-1 — `READ` — `maxTrRadius` ignores the configured initial radius
* Field initialisers run before the constructor body:
  ```java
  protected double TrRadius = 25;
  protected double maxTrRadius = 2.5 * this.TrRadius;   // == 62.5, computed at construction of the field
  ...
  public CalibratorImpl(..., double initialTRRadius, ...) { this.TrRadius = initialTRRadius; ... }
  ```
  The constructor assigns `TrRadius` **after** `maxTrRadius` was computed, and never recomputes
  `maxTrRadius`. With a non-default `initialTRRadius` (e.g. 100) the effective maximum stays
  **62.5** < initial radius, so the trust region can only shrink.
  A test with a non-default initial radius must pin this before any change.

### CAL-2 — `READ` — an improved simulation objective is accepted even when `rho < thresholdErrorRatio`
* ```java
  if (SimObjectiveChange > 0 && rouk >= thresholdErrorRatio) { accept; grow; }
  else if (SimObjectiveChange > 0 && rouk < thresholdErrorRatio) { accept; /* no growth */ }
  else { reject; shrink; }
  ```
  So acceptance depends only on `SimObjectiveChange > 0`; `rho` controls only whether the radius
  grows. Standard trust-region logic would reject a step with `rho` below the threshold. This is
  the policy the brief explicitly asks to preserve until it is compared with the publication.

### CAL-3 — `READ` — `rho` has no guard for a zero predicted reduction
* `double rouk = SimObjectiveChange / MetaObjectiveChange;` — with
  `MetaObjectiveChange == 0` this yields `±Infinity` or `NaN` (0/0). Downstream comparisons
  (`rouk >= thresholdErrorRatio`) are then silently false for `NaN`, so the step is accepted via
  the second branch. Behaviour to be pinned by tests (the brief lists NaN/Infinity/zero predicted
  improvement as required cases).

### CAL-4 — `READ` — gradient-based meta-models are requested with null gradients
* `createMetaModel(...)` wraps the null-gradient check in `try { ... throw ... } catch(Exception e) {
  System.out.print(e); }`, and then uses the **method parameter** `metaModelType` (not the field
  `this.metaModelType`) in the `switch`. The guard therefore never prevents construction: the
  gradient-based branch executes with `simGradient`/`anaGradient` null and dereferences
  `simGradient.get(m.getId())` → NPE. The intent ("switching to AnalyticalLinear") is not realised.
  The `catch` also swallows the message into `System.out` rather than logging.

### CAL-5 — `READ` — `updateAnalyticalMeasurement` gate is inverted
* ```java
  if (this.anaMeasurements.size() != measurements.size()) {
      logger.error("Measurements size must match. Aborting update");
      for (int i : this.anaMeasurements.keySet()) { if (measurements.get(i)==null) throw ...; 
                                                    this.anaMeasurements.put(i, measurements.get(i)); }
  }
  ```
  The update loop runs **only when the sizes differ**, and it iterates the *existing* keys (so a
  new iteration's measurement is never added). When the sizes *match* — the normal case — no update
  happens at all. The "same size / new contents" case is a no-op.

### CAL-6 — `READ` — `drawRandomPoint` uses `Math.random()`
* Non-seedable; makes random restarts and any test that reaches them nondeterministic. The modern
  target must inject a seeded RNG.

### CAL-7 — `READ` — `parallelStream()` over measurements while mutating maps
* `createMetaModel` (instance method) does
  `calibrationMeasurements.getMeasurements().values().parallelStream().forEach(m -> { this.metaModels.put(m.getId(), new HashMap<>()); … })`.
  `metaModels` is a `ConcurrentHashMap` (safe), but the static overload uses a plain `HashMap`
  (`metaModels = new HashMap<>()`), which is **not** safe for concurrent `put` from a parallel
  stream. Also `oldMetaModel` is populated from `this.metaModels` outside the stream, so ordering
  between the two is unspecified. Correctness before parallelism: replace with a sequential
  reduction, then benchmark.

### CAL-8 — `READ` — `calcAverageMetaParamsChange` divides by `k` without checking `k == 0`
* `z = z / k;` with `k` incremented per (measurement, time bean) when meta-model types match. If
  `metaModels` is empty, or types differ (the `break outerloop` path sets `comparable=false` but
  still divides), `k` can be 0 → `NaN`. Also `this.oldMetaModel.get(m)` is dereferenced assuming
  the previous iteration populated every key → NPE on the first comparison.

### CAL-9 — `READ` — logging is nondeterministic
* `interLogger` writes `LocalDateTime.now()` into `iterLogger.csv`, and the header is written based
  on `this.iterationNo == 1`. Tests must not depend on these artifacts.

---

## Meta-models (`matamodels/`)

### MODEL-1 — `READ` — dense array indexing assumes parameter iteration numbers are contiguous from 0
* `AnalyticLinearMetaModel.calibrateMetaModelAnalytically` / `...WithApache` / `...WithSmile` /
  `...WithAdam` all use the iteration number as a **dense array index**:
  ```java
  double[] weights = new double[this.params.size()];
  for (int i : params.keySet()) { weights[i] = …; y[i] = simData.get(i); x.setRow(i, xrow); }
  ```
  If `params` keys are not exactly `{0,1,…,n−1}` — which is possible after rejected iterations,
  random restarts, or the deserialisation constructor `CalibratorImpl(int iterPerformed, …)` —
  this throws `ArrayIndexOutOfBoundsException` or, if keys are a dense set that does not start at 0,
  silently misaligns rows. Must be characterized with a gapped/offset key set.

### MODEL-2 — `READ` — `AnalyticLinearMetaModel` contains five fitting paths, only one is live
* Paths present: COBYLA (`calibrateMetaModel`, **invoked by the constructor**), analytical
  matrix/ND4J (`calibrateMetaModelAnalytically`), Apache GLS (`calibrateMetaModelWithApache`),
  Smile LASSO (`calibrateMetaModelWithSmile`), and Adam/ND4J (`calibrateMetaModelWithAdam`).
  The other four are dead code reachable only by direct call. `deeplearning4j-core`, `nd4j-native-platform`
  and `smile-core`/`smile-data` exist **only** to support these dead paths.
* Consequence to verify: `scaleMean`/`scaleSigma`/`scaleMeanY`/`scaleSigmaY` are initialised to
  `0`/`1` in the constructor and are only *populated* by the Adam path. Since the live path is
  COBYLA, `calcMetaModel` currently applies the **identity** scaling
  (`(A−0)/1`, `(d−0)/1`, `*1+0`). If the Adam path were ever enabled, the scaling would change all
  fitted coefficients. This coupling must be pinned by tests before any of the five paths is removed
  or promoted.

### MODEL-3 — `READ` — static mutable state
* `private static double errorT = 0;` and `public static synchronized void updateErrorT(double e)`
  accumulate across **all** instances and tests. This is process-global state that makes tests
  order-dependent and cannot be reset.

### MODEL-4 — `READ` — silent debug side effects
* `calcMetaModel` prints on `out > 6000`; `calibrateMetaModelWithAdam` prints every iteration;
  `getSmile`/LASSO ignores the computed distance weights entirely. Test hygiene requires these to be
  characterized, not relied upon.

---

## Cross-cutting

### CC-1 — `VERIFIED` — the pre-existing test suite was not CI-viable
* `AnalyticLinearMetaModelTest` is **nondeterministic** (1000 random OD parameters via `Math.random()`)
  and does not terminate in a reasonable time (it drives COBYLA over that 1000-dimensional problem).
  It is now **excluded** from the default surefire run (see `MetaModelCalibration/pom.xml`) and kept
  as a legacy reference.
* `MeasurementsReaderWriterTest/MeasurementCreator` is not a runnable test at all: its class name does
  not match the surefire pattern, it writes into `src/main/resources/`, and it calls
  `assertEquals(m, m2)` on `Measurements`, which has no `equals` override. Excluded and documented.
* `AppTest` is an empty JUnit 3 `assertTrue(true)`.

### CC-2 — `VERIFIED` — 3 non-UTF-8 bytes in the imported fork
* `matsim-hk/src/main/java/createBus/BusDataExtractor.java` lines 339 (`0xA1`, `0xAF`) and 449
  (`0x92`) are unmappable as UTF-8; javac emits them as `[ERROR]` diagnostics while the build still
  succeeds. See `PRAISE_MATSIMHK_RELATIONSHIP.md` §5.

### CC-3 — `READ` — `Measurements` has no `equals`/`hashCode`
* Container and element equality is by reference only, which is why `MeasurementCreator`'s
  `assertEquals(m, m2)` was both broken and unnoticed, and why round-trip tests must compare fields
  explicitly.

### CC-4 — `READ` — ODEstimation's objective gradient disagrees with the objective it differentiates
* `ODUtils.calcODObjectiveGradient` weights by `1/(1+SD)` while
  `ODUtils.calcMetamodelODObjectiveGradient` weights by `1/(1+SD²)`, and PRAISEHK's
  `ObjectiveCalculator` uses `1/(1+SD²)`. Additionally the `fareLinkVolumeCluster` branch of
  `calcODObjectiveGradient` looks up the singular `FareLinkAttributeName` instead of `fl.toString()`.
  Full evidence in `PRAISE_ODE_RELATIONSHIP.md` §6. Not testable until ODEstimation is buildable in a
  reactor.
