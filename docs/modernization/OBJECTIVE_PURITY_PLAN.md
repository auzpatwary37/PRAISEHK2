# Objective purity plan

**Status:** plan only — **no behaviour changed**. The mission requires that objective evaluation
"must eventually be a PURE FUNCTION". This document records why, what is currently impure, and the
order in which it can be made pure without hiding correctness changes inside a refactor.

Evidence references are to `REVIEW_REQUIRED.md` (items prefixed `OBJ-`) and `TEST_MATRIX.md`.

## 1. Why purity matters *specifically here*

Purity is not aesthetic tidiness in this codebase — it is load-bearing for four other requirements:

1. **Reproducibility** (mission priority 3). The same inputs must give the same number. Today the
   objective can mutate its arguments, so the value depends on call history.
2. **Easier to characterize.** Characterization tests can and do record impure, stateful behaviour —
   that is exactly what the `OBJ-3` mutation test does today. Purity does not make such tests
   possible; it makes them *simpler to reason about and to keep honest*, because a passing test can
   no longer be explained by hidden state or by call order.
3. **Derivative work (Track B).** `ObjectiveDerivative` is defined as `(dL/dy)·(dy/dθ)`. If `L` is not
   a function of its stated inputs, the chain rule is not valid and the finite-difference oracle
   compares against a moving target.
4. **Cost.** The SUE evaluation is the expensive part; a pure objective is safely memoizable and
   safely parallel. The calibrator already uses `parallelStream`, so purity is a correctness
   prerequisite for that parallelism, not an optimisation.

**Two properties that purity does *not* imply**, and which must not be conflated with it:

* **Totality.** A pure function may return `NaN` or throw. Purity constrains *where* a value comes
  from, not *which* values are in the domain. Zero-denominator handling, missing-data handling and
  `NaN` avoidance are **domain/correctness policies**, decided from the intended mathematics — not
  consequences of purity.
* **Correctness.** Purity is orthogonal to whether the number is right. See §6.

## 2. Current impurity inventory (all verified by tests)

| # | Impurity | Location | Item | Test |
|---|---|---|---|---|
| 1 | Writes `SD = 0` into the **observed** measurement | `calcSDWeightedObjective`, TS branch | OBJ-3 | `ObjectiveCalculatorTest.sdWeightedObjectiveMutatesInput` |
| 2 | Throws `NullPointerException` on missing measurement / time bean (AADT) | all AADT branches | OBJ-2 | `aadtThrowsOnMissingMeasurement`, `missingTimeBeanDiffersByType` |
| 3 | Throws `NullPointerException` on missing measurement (multi-objective) | `calcMultiObjective`, filtered overload | OBJ-6/7 | `multiObjectiveTsThrowsOnMissingMeasurement` |
| 4 | Returns `NaN` for a zero denominator (AADT GEH; SD-weighted GEH both branches) | `calcGEHObjective`, `calcSDWeightedGEHObjective` | OBJ-5 | `gehAadtHasNoZeroGuard`, `sdWeightedGehTsHasNoZeroGuard` |
| 5 | Non-total: depends on container iteration/state | AADT accumulation scope | OBJ-1 | `aadtIsCumulativeAcrossMeasurements` |
| 6 | Hidden coupling to container state: `Measurement.putVolume` auto-inserts `SD=0` | `Measurement` | — | `putVolumeStoresValueAndDefaultSd` |
| 7 | `Measurements.clone()` drops container attributes, so "copy the inputs" is lossy | `Measurements` | MEAS-2 | `cloneDoesNotCopyContainerAttributes` |
| 8 | `Measurement.clone()` shares attribute objects, so a "copy" is not isolated | `Measurement` | MEAS-1 | `cloneSharesAttributeObjects` |

Items 6–8 matter because the obvious purity strategy — *clone the inputs, never touch them* — is
currently unsound: the clone is both lossy (7) and aliased (8).

## 3. Target shape (conceptual, not yet written)

```
ObjectiveSpec            // type (AADT | MeasurementAndTimeSpecific), SD weighting,
                         //   missing-data policy, zero-denominator policy
evaluate(observed, modelled, spec) -> ObjectiveResult   // pure, total, no mutation
ObjectiveResult          // total value + per-measurement/per-time-bean contributions
```

Returning a structured result removes the need for the `logger.debug` / `System.out` side effects
currently used for diagnostics, and makes per-measurement contributions testable without scraping logs.

## 4. Ordered steps

Each step is a small PR. Three markers are used:

* **[purity-preserving]** — must not change any number *and* must not change observable API behaviour.
* **[behaviour-preserving]** — intends not to change any number, but the claim must be demonstrated
  against the golden corpus rather than assumed.
* **[behaviour-changing]** — deliberately changes numbers and needs the algorithmic decision plus an
  oracle test. **[potentially behaviour-changing]** changes observable API behaviour even when no
  number moves.

They must never be smuggled into one another.

1. **[purity-preserving] Golden corpus.** Freeze the current objective value for every family, type
   branch and fixture in `FixtureE` plus the edge cases already covered. This is the regression net
   that makes the later steps safe: if a purity refactor moves a number, it fails.
2. **[behaviour-changing] Decide and implement the missing-data policy** (OBJ-2, OBJ-6/7). Options are
   skip / zero / fail-fast. This needs the intended semantics from the publication; the legacy code
   is internally inconsistent, so no choice can be read off it.
3. **[behaviour-changing] Decide and implement the zero-denominator policy** (OBJ-5). Currently
   `0`, `NaN` and `NaN` for the same input across three code paths. This is a **domain policy**, not a
   consequence of purity — a pure function may legitimately return `NaN`. The decision must come from
   the intended mathematics. Separately, once a policy is chosen, purity requires that the *same*
   policy applies at every call site.
4. **[behaviour-preserving] Remove the input mutation (OBJ-3)** once (2)/(3) have defined the intended
   behaviour. This is behaviour-preserving in the common path, because `putVolume` already
   auto-inserts `SD = 0`; the mutation is observable only when volumes are populated directly — so it
   is *observably* behaviour-preserving only for inputs where the inserted value is already implied.
   The golden corpus (1) must confirm that.
5. **[behaviour-changing] Resolve the AADT accumulation scope (OBJ-1).** The scalar AADT branch is
   cumulative across measurements while `calcMultiObjective` and the filtered overload are
   per-station. One of them is wrong; this is an algorithmic decision with a large numerical effect
   (500 vs 200 on the two-station case).
6. **[purity-preserving] Introduce the pure API and delegate.** Add `ObjectiveSpec` /
   `evaluate(...)`, make the legacy static methods delegate to it, and keep the legacy signatures
   until every caller has moved. Delete the legacy methods only once nothing calls them.
7. **[potentially behaviour-changing] Fix the clone semantics** (MEAS-1, MEAS-2). Changing
   `Measurement.clone()`/`Measurements.clone()` alters **observable API behaviour**: callers that
   currently rely on the shared attribute object (MEAS-1) or on the dropped container attributes
   (MEAS-2) would change behaviour. This step therefore needs its own compatibility tests and a
   caller survey; it must not be filed under "purity-preserving" and slipped through. It is a
   prerequisite for any `evaluate` implementation that defensively copies its inputs.

## 5. Acceptance criteria for "pure"

The objective may be called pure when all of the following hold, each with a test:

* **No mutation:** evaluation must leave its arguments observably unchanged. Assert **structural
  equivalence of the observable object graph** before and after — measurement ids, declared time
  beans, volumes, SD, attributes and their contents — rather than reference or byte identity, which is
  not a meaningful Java object-level contract. Since `Measurement`/`Measurements` do not implement
  `equals` (CC-3), that comparison has to be written explicitly.
* **Total under a declared policy:** with the chosen missing-data and zero-denominator policies, the
  function returns a defined value for every input in its domain, including zero observation, zero
  model, zero denominator and absent SD. Totality here is a *policy* property (see §1), not a
  consequence of purity.
* **No static mutable state read or written** (contrast: `AnalyticLinearMetaModel.errorT`, MODEL-3).
* **Deterministic:** identical results across runs and independent of `HashMap`/`HashSet` iteration
  order.
* **Self-contained:** no logging side effects required to obtain the value; contributions are returned.
* **Thread-safe:** safe to call concurrently on independent inputs — the precondition for the
  calibrator's `parallelStream` usage.

## 6. Explicit warning

Purity is **not** correctness. Steps 2, 3 and 5 change numbers; step 7 may change observable API
behaviour while moving no number at all; steps 1, 4 and 6 must change neither. Keeping those groups in
separate PRs is what prevents "I made it pure" from becoming an undocumented formula change — which the
mission forbids ("Do not change formulas because they 'look wrong'").
