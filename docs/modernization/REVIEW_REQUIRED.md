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

## `transit.fare` (vendored from the HK MATSim fork)

### FARE-1 — `VERIFIED` — `FareLink(String)` throws a raw `ArrayIndexOutOfBoundsException` on a truncated description
* **Where:** `FareLink(String fareLinkDescription)` splits on `"___"` and indexes `part[1..3]` or
  `part[1..5]` with no length check.
* **Legacy:** `new FareLink("NetworkWideFare___STOP_A")` and
  `new FareLink("InVehicleFare___LINE_1___ROUTE_1")` throw `ArrayIndexOutOfBoundsException` rather than
  a diagnostic `IllegalArgumentException`.
* **Expected:** reject malformed input with a message naming the expected grammar.
* **Evidence:** `FareLinkTest.truncatedDescriptionThrowsAIOOBE`.
* **Risk:** `MeasurementType.fareLinkVolume` calls `new FareLink(m.getId().toString())` when the
  `fareLink` attribute is absent, so any measurement whose id is not a valid fare description aborts
  measurement extraction with an obscure error.
  Evidence: `FareLinkTest.measurementIdMustBeAValidFareDescription`.
* **Proposed resolution:** validate the token count and throw `IllegalArgumentException`. Do this only
  after the fare-measurement pipeline has characterization tests, because the grammar is also a
  serialization contract for `MeasurementsReader`/`Writer`.

### FARE-2 — `VERIFIED` — the `___` separator is neither escaped nor validated
* **Where:** `FareLink.seperator = "___"`; parsing and `toString()` both use it raw.
* **Legacy:** an id containing `"___"` produces extra tokens; the parser silently reads the first
  fields and **discards the tail**. Example: `NetworkWideFare___A___B___STOP___X___bus` parses with
  `mode = "STOP"` and drops `X___bus` — no error, wrong result.
* **Expected:** either escape the separator, or validate that the token count is exact.
* **Evidence:** `FareLinkTest.separatorIsNotEscaped`.
* **Proposed resolution:** add an exact token-count check so silent truncation becomes a failure.

### FARE-3 — `READ` — dead fare code and dead imports removed with the fork
* `MTRFareCalculator`, `ZonalFareCalculator`, `TransitStop`, `TransitFareHandler`,
  `TransferDiscountCalculator`, `RouteHelper` had **zero** active references in PRAISEHK; the imports
  of four of them were removed. The three usages of `MTRFareCalculator` in `CNLTransitRoute`
  (lines ~317–332) were already commented out.
* `AnalyticalModelTransitRoute.getFare(...)` accepts `Map<String, FareCalculator>` and
  `List<FareLink>`, but **no `FareCalculator` implementation exists in this module**, so fare
  calculation has no live source. This must be characterized before the transit utility is touched.
* If MTR fare calculation was meant to be re-enabled, it must now be re-implemented against the
  two-class `transit.fare` contract (with oracle tests), not re-imported from the fork.

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
* **Related — MEAS-8b `VERIFIED`:** `MaaSPacakgeUsage.parseAttribute` wraps the MaaS package name in a
  `FareLink` (`new FareLink(...)`), so reading a package name that is not a valid fare-link
  description throws. Inconsistent with `updateMeasurement`, which treats the attribute as a plain
  package-key string. Verified by writing a measurement with package name `"pkg1"` and reading it
  back: the reader throws `IllegalArgumentException`, so the type cannot round trip at all.
  Evidence: `MeasurementTypeTransitAndFareTest.MaasPackageNameCannotRoundTrip`.

### MEAS-9 — `VERIFIED` — `averagePTOccumpancy` dereferences without a guard
* **Where:** `modelOut.getAveragePtOccupancyOnLink().get(s).get(linkId)`.
* **Legacy:** NPE when the occupancy map is absent.
* **Evidence:** `MeasurementTypeTest.averagePtOccupancyThrowsWhenAbsent`.
* **Related — MEAS-9b `READ`:** `TransitPhysicalLinkVolume.updateMeasurement` dereferences
  `modelOut.getTrainCount().get(timeBean).get(linkId)` and mutates volumes via
  `m.getVolumes().entrySet().forEach(v -> v.setValue(...))` — unguarded and a mutation during
  iteration of a `ConcurrentHashMap` (safe for the map, but the inner map may be absent).

### MEAS-10 — `VERIFIED` — `TransitPhysicalLinkVolume` is not idempotent: it ADDS to the existing volume
* **Where:** `MeasurementType.TransitPhysicalLinkVolume.updateMeasurement`:
  `v.setValue(v.getValue() + modelOut.getTrainCount()...)`.
* **Legacy:** the extractor accumulates into `m.getVolumes()` instead of replacing it. Calling it
  twice on the same measurement **double-counts** (30 → 60). Every other extractor in
  `MeasurementType` replaces the volume.
* **Expected:** an extractor should be idempotent for a fixed model output, or the accumulation should
  be explicit and paired with a reset.
* **Evidence:** `MeasurementTypeTransitAndFareTest.TransitPhysicalLinkVolumeTests.isNotIdempotent`.
* **Risk:** silent over-counting if a measurement container is re-used across iterations without
  `resetMeasurements()`. `Measurements.updateMeasurements` does not reset first.
* **Related — MEAS-10b `VERIFIED`:** a missing `MTRLineRouteStopLinkInfosName` attribute or a missing
  train-count map is dereferenced without a guard (`NullPointerException`);
  evidence `missingAttributeThrows`, `missingTrainCountThrows`. A line/route absent from the model
  output, by contrast, contributes nothing silently (`unknownLineRouteContributesNothing`).

### MTR-1 — `VERIFIED` — `MTRLinkVolumeInfo(String)` throws a raw `ArrayIndexOutOfBoundsException`
* **Where:** `MTRLinkVolumeInfo(String s)` splits on `"___"` and indexes `part[0..3]` with no length
  check.
* **Legacy:** `new MTRLinkVolumeInfo("LINE_1___ROUTE_1")` throws `ArrayIndexOutOfBoundsException`.
* **Expected:** a diagnostic `IllegalArgumentException` naming the
  `line___route___stop___link` grammar.
* **Evidence:** `MeasurementTypeTransitAndFareTest.MtrLinkVolumeInfoTests.truncatedDescriptionThrowsAIOOBE`.
* **Risk:** the grammar is a serialization contract — `TransitPhysicalLinkVolume.writeAttribute`
  emits comma-joined records in exactly this format and `parseAttribute` re-parses them, so a
  malformed record aborts measurement deserialization.

### MEAS-11 — `VERIFIED` — `maasSpecificFareLinkVolume` reads the correct container (contrast with MEAS-4)
* **Where:** `MeasurementType.maasSpecificFareLinkVolume.updateMeasurement`.
* **Legacy:** unlike `fareLinkVolume` (MEAS-4, whose MaaS fallback is dead code), this variant reads
  `getMaaSSpecificFareLinkFlow()` directly and returns the true value; an unknown MaaS package or
  fare-link key silently yields `0`.
* **Evidence:** `MeasurementTypeTransitAndFareTest.MaasSpecificFareLinkVolumeTests.readsMaasSpecificFlow`,
  `unknownPackageYieldsZero`, `correctContainerIsUsed`.
* **Additional guards missing (`VERIFIED`):** a null `MaaSPackageAttributeName` is dereferenced
  (`missingMaasAttributeThrows`), and an **empty** volume map dereferences
  `getFareLinkVolume()` during initialisation, so a null `FareLinkVolume` throws
  (`emptyVolumesThrowsWhenFareLinkVolumeIsNull`).

### MEAS-12 — `VERIFIED` — `smartCardEntry` and `smartCardEntryAndExit` extraction is a NO-OP
* **Where:** both `updateMeasurement` bodies are empty.
* **Legacy:** calling `updateMeasurement` leaves any pre-existing volume untouched; these types are
  produce-side only (populated by the MATSim event handlers), not model-output-derived.
* **Evidence:** `MeasurementTypeTransitAndFareTest.SmartCardTests.smartCardEntryUpdateIsNoOp`,
  `smartCardEntryAndExitUpdateIsNoOp`.
* **Expected:** this is plausibly deliberate (the data comes from smart-card events, not from the SUE),
  but it means `Measurements.updateMeasurements` silently skips them. Confirm intent and document it
  in the type's contract rather than leaving an empty method.

### MEAS-14 — `VERIFIED` — `MeasurementsWriter`'s generic attribute loop is DEAD CODE
* **Where:** `MeasurementsWriter.write`:
  ```java
  for(String s:mm.getAttributes().keySet()) {
      if(measurement.getAttribute(s)==null) {          // never true
          measurement.setAttribute(s, mm.getAttribute(s).toString());
      }
  }
  ```
  `measurement` is a `org.w3c.dom.Element`, and `Element.getAttribute(name)` returns the **empty
  string** for an absent attribute — it never returns `null`. The guard is therefore always false and
  the loop body never executes.
* **Legacy:** every measurement-level attribute that the type's `writeAttribute` does not set
  explicitly is **silently not serialized**. Concretely, `smartCardEntry`'s optional
  `ifForValidation` flag never reaches the XML and cannot come back on read.
* **Expected:** the guard should be `measurement.hasAttribute(s)` (or `getAttribute(s).isEmpty()`),
  so that the attribute copy actually runs — or the loop should be deleted if the copy is not wanted.
* **Evidence:** `MeasurementTypeTransitAndFareTest.SmartCardTests.smartCardEntryValidationFlagDoesNotRoundTrip`
  asserts both that `ifForValidation` is absent from the written XML and that it is `null` after reading.
  (This test was originally written expecting a round trip; the failure is what exposed the defect.)
* **Scope note:** this does **not** affect attributes written by each type's `writeAttribute`, which is
  why `LineId`/`RouteId`/`BoardingStop`, `FareLink`, the fare-link cluster and the MTR info list all do
  round trip. It affects only the *generic* fallback path.
* **Proposed resolution:** fix the guard (one line) and add a round trip for an attribute that only the
  generic path carries — `ifForValidation` is exactly such a case. Check callers first: if nothing ever
  relied on the generic path, deleting the loop is the honest alternative.

### MEAS-15 — `VERIFIED` — measurement-level attribute serialization is inconsistent by type
* **Where:** `MeasurementType` — `parseAttribute` for `smartCardEntry`, `smartCardEntryAndExit`,
  `fareLinkVolume`, `fareLinkVolumeCluster` and `maasSpecificFareLinkVolume` all read an optional
  `ifForValidation` attribute, but only via that type's own `parseAttribute`; the writer can only supply
  it through the dead generic path (MEAS-14). So the flag is readable-but-never-writable.
* **Evidence:** `MeasurementTypeTransitAndFareTest` (round trips for the five types above).

### MEAS-16 — `VERIFIED` — serialization coverage is now complete for the types that have an attribute contract
* Added in PR 2's revision: round trips for `smartCardEntry`, `fareLinkVolume`, `fareLinkVolumeCluster`
  and `TransitPhysicalLinkVolume`, alongside the existing `linkVolume`, `smartCardEntryAndExit` and
  `maasSpecificFareLinkVolume` round trips.
* `linkTravelTime` and `averagePTOccumpancy` have no type-specific attributes (their `writeAttribute`
  and `parseAttribute` are empty), so there is nothing further to round trip; their link-list payload is
  covered by the `linkVolume` round trip.
* `MaaSPacakgeUsage` is the one type whose attribute **cannot** round trip (MEAS-8b).

---

### MEAS-17 — `VERIFIED` — `Measurement.clone()` silently drops the coordinate
* **Where:** `Measurement.clone()` copies `volumes`, `sd` and `attributes`, and never touches `coord`.
* **Legacy:** `getCoord()` is public observable state (set by `MeasurementsReader` from a `<Coord>`
  element), and a clone loses it: the original reports a coordinate, the clone reports `null`.
* **Expected:** a clone should reproduce the observable object graph — `coord` is part of it, and the
  objective-purity plan's acceptance criterion is exactly deep structural equivalence.
* **Evidence:** `MeasurementTest.cloneDropsCoord`.
* **Contrast (also pinned):** `Measurement.clone()` *does* give the child its own copy of the declared
  time-bean map (`MeasurementTest.cloneCopiesTheTimeBeanMap`), which is what makes MEAS-18 possible.

### MEAS-18 — `VERIFIED` — `Measurements.clone()` ALIASES the container time-bean map, and the clone can
diverge from its own children
* **Where:** `Measurements.clone()` constructs `new Measurements(this.timeBean)` — the **same map
  instance**, not a copy — while each child `Measurement.clone()` builds `new HashMap<>(timeBean)`.
* **Legacy, two consequences:**
  1. `addRedundantTimeBean(...)` on the clone mutates the **original** container's time-bean map, because
     both refer to the same object (`assertSame` holds on `getTimeBean()`);
  2. the cloned container and its cloned children then **disagree**: the container declares two time
     beans while the child still holds a single-bean copy, so a volume for the newly declared bean is
     silently ignored by the child (logged and dropped).
* **Expected:** cloning a container should produce an independent object graph, or the sharing should be
  explicit and documented. Today a "copy" is neither independent nor faithfully consistent.
* **Evidence:** `MeasurementsTest.cloneAliasesTheContainerTimeBeanMap`,
  `MeasurementsTest.clonedContainerDivergesFromItsChildren`.
* **Why it matters:** the purity plan's defensive-copy strategy is unsound while this holds — this is the
  container-level counterpart of MEAS-1 (shared attribute objects) and MEAS-2 (dropped attributes).

### MEAS-19 — `VERIFIED` — the CSV writer rewrites `,` to `__` in the measurement id and the reader never
restores it
* **Where:** `Measurements.writeCSVMeasurements` writes `m.getId().toString().replace(",", "__")`; the
  `ifForValidation` column is written but `updateMeasurementsFromFile` reads only columns 0–3.
* **Legacy:** a measurement whose id contains a comma is written as `a__b` and read back as `a__b`, so
  the **identity is silently changed**; the original id is unrecoverable from the file.
* **Expected:** an escaping scheme that round trips, or an explicit error.
* **Evidence:** `MeasurementsTest.csvRewritesCommaInMeasurementId`.

### MEAS-20 — `VERIFIED` — `ifForValidation` is written but ignored on read
* **Where:** as above — the fifth CSV column is dropped by `updateMeasurementsFromFile`.
* **Legacy:** the flag appears in the file (`...,true`) and is absent after a read, so the persistence
  layer loses a validation-partition marker.
* **Evidence:** `MeasurementsTest.csvDropsIfForValidation`.

### MEAS-21 — `VERIFIED` — the empty-volume path of BOTH fare-link types throws before the MaaS fallback
* **Where:** `fareLinkVolume` and `fareLinkVolumeCluster` run
  `if (m.getVolumes().size() == 0) { for (tb) if (modelOut.getFareLinkVolume().containsKey(tb)) ... }`
  **before** the fallback map is built.
* **Legacy:** with an empty volume map and a `null` `FareLinkVolume`, the method throws
  `NullPointerException` **before reaching the fallback** — so MEAS-4's finding that the cluster fallback
  "works" is **conditional on the measurement already having a volume entry**. The earlier tests used a
  populated volume map and therefore missed this.
* **Expected:** the fallback should be resolved before any container is read, or the null case handled.
* **Evidence:** `MeasurementTypeTest.fareLinkEmptyVolumePathThrowsBeforeTheFallback` (both types).
* **Also pinned:** the CSV type column IS honoured for a new measurement but an **existing** measurement
  keeps its own type (`MeasurementsTest.csvTypeHandling`), and a multi-time-bean round trip preserves id,
  times, volumes and type (`csvRoundTripAllColumns`).

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

## `CNLSUEModel` (`analyticalModelImpl/CNLSUEModel.java`) — the MSA core

**Scope of this section: the car-link / no-transit path only.** Every test below drives
`UpdateLinkVolume`/`CheckConvergence` with an empty transit map. Both methods also run a transit loop
with its own guard (SUE-5), so the `O` marks on these rows cover the car half of the loop, not the method
as a whole.

The assignment loop appends the residual norm to `error` in `CheckConvergence`, then reads the last two
of those norms in `UpdateLinkVolume` to advance `beta` and move every volume by
`(1/beta) * (loaded - current)`. Characterized with the step weight recovered from the observable
volume change, so the test does not trust the internal list.

**The step weight is an adaptive `1/beta`, NOT the classic harmonic `1/k`.** `beta` is a per-time-bean
`ArrayList<Double>` seeded to `1.0` at `counter == 1` and thereafter advanced by `+gammaMSA` (0.1) on a
strictly decreasing residual, or `+alphaMSA` (1.9) otherwise; the move then uses `1/beta[counter-1]`.
Along an all-decreasing run that is `beta_k = 1 + 0.1(k-1)`, i.e. a weight of **`1/(1 + 0.1(k-1))`** —
1, 1/1.1, 1/1.2, … An earlier revision of this document said "classic harmonic `1/k`" *and* `1/(1 + 0.1k)`;
both were wrong (an off-by-one on the second, the wrong sequence on the first). The `1./counter` variant
does exist in the source, but it is **commented out** (line 1226).

### SUE-1 — `CORRECTED` — the α branch depends on state that only `generateRoutesAndOD` initialises

*This item previously read "`consecutiveSUEErrorIncrease` is never seeded, so the alpha branch THROWS",
concluded that the adaptive policy is "inert", and gave the weight as `1/(1 + 0.1k)`. **All three claims
were wrong.** The correction is recorded rather than quietly edited, because the original reasoning is
exactly the kind the redesign would have relied on.*

* `consecutiveSUEErrorIncrease` **is** seeded per time bean: `generateRoutesAndOD`,
  line 314 — `this.getConsecutiveSUEErrorIncrease().put(timeBeanId, 0.);`. The write in
  `UpdateLinkVolume` is **not** its only write; the earlier claim that it was is false.
* It is *not* seeded by the constructor (lines 140–163, which do initialise `beta`/`error`/`error1`) nor
  by `perFormSUE`.
* **It cannot be unseeded in a working production run.** `generateRoutesAndOD` is also the *only* code
  that populates `networks` (line 303), and `perFormSUE` dereferences
  `this.networks.get(timeBeanId).getLinks()`. So any run that reaches the MSA loop has, by construction,
  already executed the very method that seeds the counter. The α branch is therefore **reachable in
  production**, and the weight *does* respond to stagnation.

What survives is a narrower and lower-severity point: `UpdateLinkVolume`'s α branch reads state that
neither the constructor nor `perFormSUE` establishes, so the MSA core is **not self-contained**. Driving
the loop on a model built by the constructor alone — injecting a network by hand, as the unit harness
does — hits `null + 1` and throws `NullPointerException` before any volume moves. Such a caller would in
any case fail earlier on the unpopulated `networks` map, so this is a latent initialisation/coupling
defect, not a production outage.

```java
if (error.get(timeBeanId).get(counter-1) < error.get(timeBeanId).get(counter-2)) {
    beta.get(timeBeanId).add(beta.get(timeBeanId).get(counter-2) + this.gammaMSA);
} else {
    this.getConsecutiveSUEErrorIncrease().put(timeBeanId,
        this.getConsecutiveSUEErrorIncrease().get(timeBeanId) + 1);   // <-- null until generateRoutesAndOD runs
    beta.get(timeBeanId).add(beta.get(timeBeanId).get(counter-2) + this.alphaMSA);
}
```
* **Expected:** the constructor should seed the map (or `perFormSUE` should), so that `UpdateLinkVolume`
  does not silently depend on a prior `generateRoutesAndOD`.
* **Legacy reference:** the older `SUEModelContTime` uses `protected int consecutiveSUEErrorIncrease = 0;`
  and simply `++`s it — a field that exists from construction, which is the property this class lost when
  it became a per-time-bean map.
* **Evidence:** `CNLSUEModelMSATest.nonDecreasingErrorThrowsWhenTheCounterWasNeverSeeded` (asserts the map
  is empty *on the constructor path*, that the call throws, and that the volume is untouched);
  `nonDecreasingErrorGrowsBetaByAlphaOnceTheCounterIsSeeded` proves the branch itself is correct by
  seeding the map by hand and recovering `beta = 2.9` from the volume change.

### SUE-2 — `VERIFIED` — the stopping rule ORs three criteria of different kinds, and the tolerance argument can force convergence
* `CheckConvergence` returns true when **any** of:
  ```java
  squareSum <= 1                                        // absolute: norm of SQUARED errors
  || sum == 0                                           // relative: no link breaches `tollerance`
  || linkBelow1 == linkVolume.size()+transitlinkVolume.size()   // pointwise: every link below 1
  ```
* The middle disjunct is decided by `error / newVolume * 100 > tollerance`, where `tollerance` is a
  **method parameter** — so passing a large value declares convergence regardless of the actual state.
  (Note `UpdateLinkVolume` takes no such parameter and instead reads the `tollerance` *field*: the same
  quantity is a parameter in one method and a field in the other.)
* The first disjunct compares a norm of *squared* errors against 1, so it means "the root-sum-square of
  the deltas is at most 1" — easy to mistake for a tolerance test.
* **Evidence:** `theToleranceArgumentAloneForcesConvergence` (identical state, converged only because
  the argument was raised to 1000), `convergenceBoundaryIsTheUnitSquaredErrorNorm` (passes at exactly
  `|diff| = 1`, fails at `|diff| = 2`).

### SUE-3 — `VERIFIED` — an UNLOADED link is excluded from `linkBelow1`, making the pointwise disjunct unreachable
* The `if (error < 1) { linkBelow1++; }` increment sits **inside** the `else` branch of
  `if (linkVolume.get(linkid) == 0)`. A link with zero loaded volume therefore contributes `0` to
  `squareSum` (helping the norm test) but **never** counts toward `linkBelow1`.
* Since `linkBelow1 == linkVolume.size() + transitlinkVolume.size()` requires *every* link to be
  counted, the pointwise disjunct **cannot** fire for any time bean containing an unloaded link — the
  counter can reach at most `N - (number of unloaded links)`.
* This is the opposite of what the code reads like: an unloaded link looks trivially converged.
* **Evidence:** `unloadedLinksAreExcludedFromThePointwiseDisjunct` — three loaded links with squared
  errors of 0.81 each converge via the pointwise disjunct, yet unloading just one of them flips the same
  state to NOT converged.

### SUE-4 — `VERIFIED` — the `error == Double.NaN` guards are DEAD, and a NaN residual reports CONVERGED
* `CheckConvergence` guards with `error == Double.NaN` and `squareSum == Double.NaN`. `NaN == NaN` is
  always false, so neither guard can ever fire (the same dead-comparison shape as MEAS-14's
  `== null` writer guard).
* `(Inf - Inf)^2` is `NaN`. With an infinite current volume the infinity guard (`error == ±Infinity`)
  does not fire either, every subsequent comparison is false, `sum` therefore stays `0`, and the
  `sum == 0` disjunct **declares convergence on unusable state**.
* The car loop throws `IllegalArgumentException("Error is infinity!!!")` for `±Infinity`, so the two
  non-finite cases are handled inconsistently: `+Inf` throws, `NaN` silently converges.
* **Evidence:** `nanErrorIsSilentlyReportedAsConverged` (asserts the throw for `+Inf` and the silent
  `true` for `Inf - Inf`).

### SUE-5 — `READ` — recorded, not tested
* `tolleranceLink` and the `linkSum` counter in `UpdateLinkVolume` are computed for every link and then
  **discarded**: neither is returned nor used. The per-link relative-change diagnostic is dead.
* The transit loop's guard is `error == Double.NaN || error == Double.NEGATIVE_INFINITY` — it tests NaN
  (dead) where the car loop tests `+Infinity`, so a transit link can carry `+Infinity` error without
  throwing. Not tested: it would need a `TransitLink` stub, and no test can distinguish it from the
  car path today.
* `UpdateLinkVolume` takes `(…, int counter, String timeBeanId)` while `CheckConvergence` takes
  `(…, String timeBeanId, int counter)` — the argument order is transposed between the two halves of
  the same loop.

## ParamReader (`calibrator/ParamReader.java`)

All six items below are now `VERIFIED` by `ParamReaderTest` (23 tests). Correction to an earlier draft:
the internal maps are keyed by the CSV **Code** column and the `id` column is ignored entirely, which
matters for every reading of this class.

### PARAM-1 — `VERIFIED` — silent fallback to a **relative** default path
* `new ParamReader(fileLoc)`: if `fileLoc` does not exist, `this.paramFile = new File("src/main/resources/paramReaderTrial1.csv")` with **no warning and no exception**. The path is relative to the process CWD, so the same call loads the bundled sample parameters or silently yields **empty maps**, depending on where the JVM was launched. A missing *requested* file therefore produces the wrong parameter set rather than an error.
* **Evidence:** `ParamReaderTest.MissingFile.missingFileSilentlyFallsBack` — asserting `getDefaultFileLoc()` is the relative path, then (guarded by an assumption so the test is honest off the module dir) that the bundled file's codes `1` and `14` are loaded.
* **Note:** `paramReaderTrial1.csv` leaves the SubPopulation column empty on every row, so the fallback also yields an **empty** sub-population list.

### PARAM-2 — `VERIFIED` — raw `split(",")` parsing, and the `id` column is discarded
* `line.split(",")` removes trailing empty fields, so a row ending in an empty column makes
  `part[7]` throw `ArrayIndexOutOfBoundsException`; a short row fails earlier at `part[5]`. No
  header validation, no quoted-field handling, and no use of the declared-but-unused `commons-csv`.
* `String paramId=part[2];` is **immediately overwritten** in both branches of the following
  `if/else`, so the `id` column is dead input: `paramId` is always rebuilt as `subPopulation + " " + parameterName` (or just `parameterName` when the sub-population is empty).
* **Evidence:** `Malformed.tooFewColumnsThrows`, `nonNumericThrows`, `trailingEmptyIncludeFlagThrows`,
  `Parsing.idColumnIsIgnored`.
* **PARAM-2b — `VERIFIED` — the first line is discarded unconditionally:** the constructor calls
  `bf.readLine()` to skip a header with no validation, so a headerless file silently loses its first
  data row. Evidence: `Malformed.firstLineIsAlwaysDiscarded`.

### PARAM-3 — `VERIFIED` — `SetParamToConfig` writes to disk and reloads
* `new ConfigWriter(config).write("config_Intermediate.xml"); Config configOut = ConfigUtils.loadConfig("config_Intermediate.xml");` — a **CWD-relative** path, a filesystem round trip and a parse dependency inside what should be a pure transformation. The Config does reach the caller with the values applied.
* **Evidence:** `SetParamToConfigTests.writesConfigToCwdAndAppliesValues` asserts the file appears in the CWD, that `qsim().getFlowCapFactor()` equals the CSV's `CapacityMultiplier`, and deletes the file afterwards so the working tree is left clean.
* Minor: `System.out.println(config.isLocked())` prints on every call.

### PARAM-4 — `VERIFIED` — a duplicated code is inconsistent between the general and initial maps
* `DefaultParam`, `paramLimit`, `initialParam` and `initialParamLimit` are keyed by the **Code**
  column (not `paramName`/`paramId`). On a duplicate code the values and bounds are **last-wins**, but
  `initialParam` is only *written* when `IncludeIninitialParam` is true — it is never removed — so a
  later `FALSE` row leaves the **first** row's value in place, and `initialParamLimit` keeps the
  bounds captured at the first row's time. The general map and the initial map therefore **disagree**
  about the same code.
* `subParamAndLimit.csv` legitimately repeats codes across sub-populations (e.g. `3`, `4`, `8`, `13`),
  and the maps key on code, so cross-sub-population codes collide by design.
* **Evidence:** `Parsing.duplicateCodeInconsistency` (value/bounds 150/(100,200) from the last row vs
  initial value/bounds 5/(0,10) from the first), `duplicateCodeLaterRowIncluded`.

### PARAM-5 — `VERIFIED` — `ScaleDown` can emit a `null` key; `generateSubPopSpecificParam` can throw
* `ScaleDown` returns the input unchanged when **no** key overlaps `ParamNoCode`, but otherwise maps
  **every** key through `paramNoCode.get(s)`, so a partially-overlapping input yields an entry with a
  **`null` key**. Downstream `ScaleUp`/`ScaleUpLimit` then iterate `ParamNoCode`, so the null-keyed
  entry is silently dropped — but it has already been inserted into a map handed to callers.
* `generateSubPopSpecificParam` selects keys by `s.contains(subPopName) || s.contains("All")` and then
  does `s.split(" ")[1]`, so a matching key with no space (`"All"` itself, or a bare sub-population
  name) throws `ArrayIndexOutOfBoundsException`.
* **Evidence:** `Scaling.scaleDownNoOverlapReturnsInput`, `scaleDownPartialOverlapEmitsNullKey`,
  `SubPopExtraction.matchingKeyWithoutSpaceThrows`, `extractsMatchingEntries`.

### PARAM-6 — `VERIFIED` — `ScaleUp` dispatch on `containsAll`, and the unknown-parameter switch
* `if ((this.ParamNoCode.values()).containsAll(trialParam.keySet())) { }` has an **empty body**, so the
  "all keys are codes" case falls through to the loop; the `else if` handles "already scaled" and
  returns the input; the `else` throws unless `allowUnkownParamaeterWhileScalingUp` is true, in which
  case unknown keys are passed through unchanged. Behaviour therefore depends on a mutable flag.
* `ScaleUp` also **omits** codes that are absent from the input rather than defaulting them
  (the defaulting line is commented out), so a partial input silently yields a partial parameter map.
* **Evidence:** `Scaling.scaleUp`, `scaleUpOmitsAbsentCodes`, `scaleUpAlreadyScaledIsIdentity`,
  `scaleUpUnknownInput`, `scaleUpLimit`, `scaleUpLimitAlreadyScaledIsIdentity`,
  `scaleUpLimitMixedKeysThrow`, `scaleUpLimitOmitsAbsentCodes`.

### PARAM-7 — `VERIFIED` — conflicting scoped values COLLAPSE onto one code, and the survivor is order-dependent
* **Where:** `ScaleDown` maps every input key through `ParamNoCode.get(s)` into a single
  `LinkedHashMap` keyed by code.
* **Legacy:** a code shared by several sub-populations is a many-to-one relation, so
  `ScaleDown({person_A MuMoney: 1.0, person_B MuMoney: 1.2})` inserts `3 → 1.0` and then `3 → 1.2`
  into the **same** map. The surviving value is whichever was inserted last, so the same *set* of
  scoped values yields **different** canonical values depending only on iteration order. The other
  sub-population's value is silently discarded — there is no place in the representation to keep it.
* **Expected:** an explicit policy. Either the collapse is defined (documented, deterministic) or the
  typed model rejects conflicting scoped values for one canonical code.
* **Evidence:** `SharedCodes.scaleDownCollapsesConflictingValues`,
  `SharedCodes.firstSubPopulationsBoundsAreLost`.
* **Risk:** this is the highest-value item for the typed redesign: whatever
  `ParameterDefinition`/`ParameterSpace` chooses, it must decide this deliberately rather than
  inherit an iteration-order accident.

### PARAM-7b — `INTENTIONAL, MUST BE PRESERVED` — one code deliberately GROUPS several scoped ids
* The class javadoc states the intent: *"The code will be used to identify the parameters... same code
  parameters will be treated as one parameter."* The behaviour is therefore **documented alias/group
  semantics**, not an accident:
  * `ParamNoCode` maps several scoped ids (`person_A MuMoney`, `person_B MuMoney`) to one canonical
    code;
  * `ScaleUp({3: v})` **fans out** that single value to **every** scoped name (verified);
  * so `SetParamToConfig` writes the *same* canonical value into every sub-population sharing the code
    (verified end-to-end through to the two `ScoringParameterSet`s);
  * while `ScaleDown` collapses them back, order-dependently (PARAM-7).
* **Consequence for the redesign:** the typed model **must** be able to express "one canonical
  parameter, several scoped aliases". A naive `Map<ParameterName, …>` would silently break it.
* **Evidence:** `SharedCodes.oneCodeGroupsScopedParameterIds`, `scaleUpFansOutOneCodeToManyNames`,
  `SetParamToConfigTests.sharedCodeFeedsEverySubPopulationConfig`.

### PARAM-4 (extended) — shared codes across real sub-populations
* The reviewer's point was correct: the original tests used unscoped rows sharing a code, which
  exercised only the degenerate case. With **two real sub-populations** sharing code `3`, the value
  and bounds are last-wins (`1.2`, `(0.9, 1.5)`) while the initial maps retain the first *included*
  row (`1.0`, `(0.8, 1.2)`) — so the first sub-population's bounds are **not recoverable from any
  map**. When both rows are included, the initial maps follow last-wins like the general maps.
* **Evidence:** `SharedCodes.sharedCodeValueAndBoundsAreLastWins`, `sharedCodeIncludedByBothIsLastWins`.

### PARAM-8 — `VERIFIED` — the GV sub-population branch silently omits the PT-family parameters
* **Where:** `SetParamToConfig`, `if (!subPop.contains("GV")) { … } else { … }`. GV names are matched
  by **substring** (`contains("GV")`), not equality.
* **Legacy:** the non-GV branch writes car travel/distance, money, car money cost, PT travel,
  PT distance cost, waiting, line switch, walk travel, walk money cost, PT constant, car constant and
  performing utility. The **GV branch writes only** car travel, car distance, money, car money cost,
  walk travel, walk money cost and performing utility — it does **not** write PT travel, PT distance
  cost, waiting, line switch or the mode constants, so those keep their MATSim defaults and the values
  present in the CSV are silently ignored.
* **Expected:** intentional (goods vehicles have no PT leg) — but it should be stated, and the
  substring match on `"GV"` is a fragile dispatch key.
* **Evidence:** `SetParamToConfigTests.gvSubPopulationOmitsPtParameters` (asserts the written fields
  *and* that the PT fields equal a fresh sub-population's defaults and differ from the CSV values),
  `nonGvSubPopulationIsFullyMapped`.

### PARAM-9 — `VERIFIED` — `setDefaultParams(Config, String)` is a second, separate application path
* **Where:** `ParamReader.setDefaultParams(Config, String)`.
* **Legacy:** reads its values from `ScaleUp(this.DefaultParam)` — i.e. the **bare** parameter names
  regardless of the `subPop` argument — and writes them into
  `getOrCreateScoringParameters(subPop)`. It does **not** touch `qsim` (no `CapacityMultiplier`
  handling), unlike `SetParamToConfig`.
* **Expected:** the reviewer's point stands — this public path must be protected, or explicitly
  excluded with evidence that no caller depends on it. Characterized now.
* **Evidence:** `SetParamToConfigTests.setDefaultParamsWritesIntoNamedSubPopulation`.

---

## CalibratorImpl (`calibrator/CalibratorImpl.java`) — trust region

### CAL-1 — `VERIFIED` — `maxTrRadius` ignores the configured initial radius
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
* **Evidence:** `CalibratorImplStateMachineTest.Construction.maxTrRadiusIgnoresTheConfiguredInitialRadius`
  — with `initialTRRadius = 100` the getters report `TrRadius = 100` and `maxTrRadius = 62.5`.

### CAL-2 — `VERIFIED` — an improved simulation objective is accepted even when `rho < thresholdErrorRatio`
* ```java
  if (SimObjectiveChange > 0 && rouk >= thresholdErrorRatio) { accept; grow; }
  else if (SimObjectiveChange > 0 && rouk < thresholdErrorRatio) { accept; /* no growth */ }
  else { reject; shrink; }
  ```
  So acceptance depends only on `SimObjectiveChange > 0`; `rho` controls only whether the radius
  grows. Standard trust-region logic would reject a step with `rho` below the threshold. This is
  the policy the brief explicitly asks to preserve until it is compared with the publication.

### CAL-11 — `VERIFIED` — the internal recalibration is invoked and its RESULT IS DISCARDED
* **Where:** `CalibratorImpl.generateNewParam`:
  ```java
  Map<Integer,Measurements> newAnaMeasurements = this.sueAssignment.calibrateInternalParams(
          this.simMeasurements, scaledParam, …);      // recalibrated measurements
  this.updateAnalyticalMeasurement(newAnaMeasurements);
  this.successiveRejection = 0;                       // reset regardless
  ```
* **Legacy:** `this.simMeasurements` and `this.anaMeasurements` hold the same iteration keys, so the two
  maps have **equal sizes**. `updateAnalyticalMeasurement` short-circuits entirely on equal sizes
  (CAL-5), so the recalibrated measurements **never reach the calibrator's state**, while the rejection
  counter is reset anyway. The recovery mechanism therefore *appears* to run and recovers nothing: the
  model keeps its old analytical measurements and the trigger can fire again later with the same effect.
* **Evidence:** `CalibratorImplStateMachineTest.StateMachine.internalCalibrationResultIsDiscarded` — the
  stub returns the same iteration keys with volume `777`, and all three recorded iterations still hold
  `100` after the call, with `successiveRejection` reset to 0. Also
  `counterRestartsAfterTheTrigger`, which proves the counter genuinely restarts (so the trigger is not
  immediately re-entered) and that the radius is *not* reset by the trigger.
* **Combined with CAL-5**, this makes the successive-rejection recovery path effectively inert. That
  changes the interpretation of the whole trust-region mechanism, so it is recorded as its own item -
  the reviewer's point, confirmed by test rather than by inspection.

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

### CAL-5 — `VERIFIED` — `updateAnalyticalMeasurement` gate is inverted
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
* **Verified in four parts:** a fresh calibrator's update is a complete no-op (the loop iterates the
  empty existing key set); with equal sizes the method short-circuits and even key 0 stays stale; with
  different sizes only the existing keys are refreshed and the extra iteration is still not added; and
  an existing iteration missing from the new map throws `IllegalArgumentException`.
* **Evidence:** `CalibratorImplStateMachineTest.UpdateAnalyticalMeasurement.*` (four tests).

### CAL-6 — `VERIFIED` — `drawRandomPoint` uses `Math.random()`
* Non-seedable; makes random restarts and any test that reaches them nondeterministic. The modern
  target must inject a seeded RNG.
* **Evidence:** `CalibratorImplStateMachineTest.DrawRandomPoint.boundsRespectedAndKeyedByCode` — the
  point respects the bounds and is keyed by the CSV **Code** column. The test deliberately does **not**
  assert that two draws differ: that would make a "deterministic" suite depend on `Math.random()` and
  could fail by chance. Non-seedability is **source-established** — the production path calls
  `Math.random()` and offers no seed or RNG parameter — rather than proven by comparing draws. This
  distinction is the same one the C/`O` legend insists on: a test that exists is not a test that
  proves the property in its name.

### CAL-7 — `READ` — `parallelStream()` over measurements while mutating maps
* `createMetaModel` (instance method) does
  `calibrationMeasurements.getMeasurements().values().parallelStream().forEach(m -> { this.metaModels.put(m.getId(), new HashMap<>()); … })`.
  `metaModels` is a `ConcurrentHashMap` (safe), but the static overload uses a plain `HashMap`
  (`metaModels = new HashMap<>()`), which is **not** safe for concurrent `put` from a parallel
  stream. Also `oldMetaModel` is populated from `this.metaModels` outside the stream, so ordering
  between the two is unspecified. Correctness before parallelism: replace with a sequential
  reduction, then benchmark.

### CAL-8 — `VERIFIED` — `calcAverageMetaParamsChange` divides by `k` without checking `k == 0`
* `z = z / k;` with `k` incremented per (measurement, time bean) when meta-model types match. If
  `metaModels` is empty, or types differ (the `break outerloop` path sets `comparable=false` but
  still divides), `k` can be 0 → `NaN`. Also `this.oldMetaModel.get(m)` is dereferenced assuming
  the previous iteration populated every key → NPE on the first comparison.
* **Consequential effect, now pinned:** with no meta-models the mean is `NaN`, and the caller's guard
  is `change < minMetaParamChange` — which is **false** for `NaN`, so the random-restart branch can
  never fire. The empty-meta-model case therefore silently disables the restart mechanism instead of
  triggering it.
* **Evidence:** `CalibratorImplStateMachineTest.AverageMetaParamsChange.noMetaModelsYieldsNaN`
  (`0/0` and the downstream comparison) and `missingOldMetaModelThrows` (NPE with `metaModels`
  populated, because `oldMetaModel` is private and starts empty).

### CAL-9 — `READ` — logging is nondeterministic
* `interLogger` writes `LocalDateTime.now()` into `iterLogger.csv`, and the header is written based
  on `this.iterationNo == 1`. Tests must not depend on these artifacts.

---

## Meta-models (`matamodels/`)

### MODEL-1 — `VERIFIED` — the constructor requires iteration key 0; the alternative fitters assume dense 0-based keys
* `MetaModelImpl` does `this.noOfParams = params.get(0).size()`, so a parameter map without iteration
  **0** throws `NullPointerException`. Reachable in principle after a restart or the deserialisation
  constructor; latent in practice because calibration always starts at iteration 0.
* Separately, `calibrateMetaModelAnalytically` / `...WithApache` / `...WithSmile` / `...WithAdam` use the
  iteration number as a **dense array index** (`weights[i]`, `y[i]`, `x.setRow(i, …)`), so a gapped or
  offset key set would throw `AIOOBE` or silently misalign rows. The **live** COBYLA path does not do
  this — it iterates `params.keySet()` directly.
* **Evidence:** `AnalyticLinearMetaModelOracleTest.constructorRequiresIterationZero`. The dense-index
  variants are unreachable (see MODEL-2), so they are recorded rather than pinned.

### MODEL-2 — `VERIFIED` — four of the five fitting paths are UNREACHABLE, not merely unused
* Paths: COBYLA (`calibrateMetaModel`, invoked by the constructor), analytical matrix/ND4J
  (`calibrateMetaModelAnalytically`), Apache GLS (`...WithApache`), Smile LASSO (`...WithSmile`) and
  Adam/ND4J (`...WithAdam`).
* The constructor calls **only** COBYLA and then executes
  `this.params.clear(); this.simData.clear(); this.analyticalData.clear();`. Every alternative fitter
  iterates exactly those three cleared collections, so **none of them can produce a fit after
  construction** — they are not "alternative implementations that happened to fall out of use", they
  are unreachable code paths.
* **Evidence:** `AnalyticLinearMetaModelOracleTest.alternativeFittersAreUnreachable` — all four throw.
* **Consequence:** `deeplearning4j-core`, `nd4j-native-platform`, `smile-core` and `smile-data` exist
  **only** to support these four unreachable paths. This is the evidence `DEPENDENCIES.md` asked for
  before removing them; the removal itself is a separate, isolated change.

### MODEL-3 — `READ` — static mutable state
* `private static double errorT = 0;` and `public static synchronized void updateErrorT(double e)`
  accumulate across **all** instances. It is only written by the (unreachable) Adam path and has no
  getter, so it is currently unobservable — which is why it is recorded rather than tested. It must not
  survive the redesign.

### MODEL-4 — `VERIFIED` — the live scaling fields are inert, and diagnostics go to stdout
* `scaleMean`/`scaleSigma` are initialised to `0`/`1` (and `scaleMeanY`/`scaleSigmaY` to `0`/`1`) and
  are only ever **populated** by the unreachable Adam path. In the live COBYLA path `calcMetaModel` is
  therefore the plain affine model `beta0 + betaA*A + beta^T x` — the scaling scaffolding is dead
  weight, and if it were ever activated it would change every fitted coefficient.
* **Evidence:** `AnalyticLinearMetaModelOracleTest.scalingFieldsAreIdentity` (asserts the identity
  values; the oracle in the same class relies on them).
* Also recorded: `calcMetaModel` prints when `out > 6000` and the Adam path prints every iteration, so a
  test must not depend on stdout being clean.

### MODEL-5 — `VERIFIED` — the live fitter exhausts its evaluation budget and DISCARDS the error status
* **Where:** `calibrateMetaModel` → `Cobyla.findMinimum(optimization, noOfMetaModelParams, 0, x, 0.5, 1e-6, 0, 1500)`.
  The returned `CobylaExitStatus` is assigned to `result` and then never inspected.
* **Legacy:** on a dataset whose true coefficients are O(1)–O(10) and whose analytical part is
  `A ≈ 100`, COBYLA returns **`MAX_ITERATIONS_REACHED`** after its 1500 evaluations, having barely moved
  the coefficients. The declared objective at the returned point is **16.3615**, against **0.015204**
  at the closed-form optimum — a factor of **≈1076**. Raising the budget improves the objective
  monotonically (**5000 → 12.84, 20000 → 6.09, 100000 → 0.245**) and the status remains
  `MAX_ITERATIONS_REACHED` each time.
* **Expected:** attain (or approach) the optimum, and at minimum **detect and report** non-convergence
  instead of returning a silently poor fit. Contributing cause: the design matrix is badly scaled —
  `A ≈ 100` beside `x ≈ 1` — which is precisely what the (unreachable) Adam path's
  `scaleMean`/`scaleSigma` standardisation was for.
* **Method:** independent closed-form weighted ridge, `beta = (X'WX + λI)⁻¹ X'Wy`, solved by LU
  decomposition (`commons-math3`), with the legacies' own weight function and `λ = 1e-3`.
* **Numerical example** (`farOptimum`: 5 iterations, 2 parameters, `A = 100 + 3x₁² − 2x₂`,
  `y = 1.5A + 2x₁ − 3x₂`):
  * legacy fit `[1.6205, 1.4529, 1.0671, 0.7340]`, objective 16.3615
  * closed-form optimum `[-0.1714, 1.5018, 1.9861, -2.9932]`, objective 0.015204 — it recovers the
    generating coefficients `βA = 1.5, β₁ = 2, β₂ = −3` exactly.
* **Contrast (proves the objective is correct):** with O(1) coefficients near the hard-coded all-ones
  start, the same code **does** reach the closed-form optimum to within 5e-2. So the objective and the
  model are right; the optimizer setup is what fails.
* **Evidence:** `AnalyticLinearMetaModelOracleTest.iterationBudgetIsExhaustedAndStatusIsIgnored`
  (replicates the legacy call, asserts the identical objective, asserts `MAX_ITERATIONS_REACHED`, and
  shows the monotone improvement with a larger budget), `fitMatchesOracleWhenParametersAreWellScaled`,
  `constantSimulationOutputIsAlsoShortOfTheOptimum`, `duplicateParameterPointsAreAlsoShortOfTheOptimum`.
  The mathematical expectation is encoded as a **disabled** test,
  `fitShouldAttainTheClosedFormOptimum`, to be enabled when this is resolved.
* **Proposed resolution:** inspect `CobylaExitStatus` and fail or warn; raise `maxfun`; and standardise
  the columns (the machinery already exists but is only wired into the unreachable Adam path). Each
  step needs the closed-form oracle to stay green.

### MODEL-6 — `VERIFIED` — `calcEuclDistanceBasedWeight` is asymmetric and can throw
* **Where:** `MetaModelImpl.calcEuclDistanceBasedWeight` iterates `param1.keySet()` (the **reference**
  point) and reads `param2.get(s)` for the compared point.
* **Legacy:** the summation set is the reference point's key set, so:
  * a key present in the **compared** point but absent from the reference is **silently ignored** — the
    distance is too small and the weight is too large (in the extreme, `1.0`, as if the points were
    identical);
  * a key present in the **reference** point but absent from the compared point makes
    `param2.get(s)` return `null` → **`NullPointerException`** on unboxing.
  The weight is therefore **not symmetric** in its two arguments: `w(a,b) ≠ w(b,a)`, and whether it
  throws depends on which point is the reference.
* **Expected:** a symmetric distance over the union of keys, or an explicit error — silently weighting a
  point as if it were identical is the worst option, because the fit still looks plausible.
* **Numerical example:** reference `{θ₁: 0}`, compared `{θ₁: 0, θ₂: 4}` → weight `1.0` (θ₂ ignored).
  Reversed reference → `NullPointerException`.
* **Evidence:** `AnalyticLinearMetaModelOracleTest.weightFunctionMatchesItsDefinition`,
  `weightIgnoresKeysAbsentFromTheReferencePoint`.

### MODEL-7 — `VERIFIED` — the fitting corpus is serialised with the `Analysis` container
* `Measurements`/`Measurement` are the fitting corpus, so any measurement-serialization defect
  (MEAS-14/15) also affects meta-model reproducibility. Recorded for the redesign; no separate test.

---

### CAL-10 — `READ` — the trust-region optimizer starts from a PARTIALLY initialised vector
* **Where:** `AnalyticalModelOptimizerImpl.performOptimization`:
  ```java
  double[] x=new double[noOfVariables];
  for (int j=0;j<x.length;j++) {
      x[j]=1;
      j++;            // <-- double increment
  }
  ```
  The loop increments `j` twice per pass, so only the **even** indices are set to `1`; the odd indices
  keep the array default `0`. For two variables the start point is `[1, 0]`, not `[1, 1]`.
* **Legacy:** `ScaleUp(x)` maps a coordinate to `(1 + x[j]/100) * currentParam`, so a `0` coordinate
  means "leave this parameter exactly as it is". The optimizer therefore begins at the *current*
  parameter for every odd-index variable rather than at a perturbed point — a silent asymmetry in the
  starting simplex.
* **Expected:** all coordinates initialised consistently.
* **Status:** `READ` — not pinned by a test, because the initial vector is not exposed and the returned
  point is the output of a COBYLA run, so the start cannot be observed in isolation without first
  extracting the optimizer's setup. Recorded here so the redesign does not reproduce it.
* Also recorded: this path prints `iprint=3` COBYLA output plus one `System.out.println` per variable on
  every call, and `CalibratorImpl.writeMeasurementComparison` writes `Comparison<N>.csv` per iteration.
  Tests must capture stdout rather than assume it is clean —
  `CalibratorImplStateMachineTest` does exactly that.

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

### CC-2 — `VERIFIED` — 3 non-UTF-8 bytes in the HK fork source (external, no longer vendored)
* The fork's `createBus/BusDataExtractor.java` (lines 339: `0xA1`, `0xAF`; line 449: `0x92`) is
  unmappable as UTF-8; javac emits `[ERROR]` diagnostics while the build still succeeds.
* **This file is no longer part of this repository** — the fork is not a build dependency, and
  `BusDataExtractor` was never used by PRAISEHK. Recorded because it will resurface if the fork is
  ever re-imported. See `PRAISE_MATSIMHK_RELATIONSHIP.md` §5.

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
