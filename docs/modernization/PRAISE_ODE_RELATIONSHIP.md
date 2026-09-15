# PRAISEHK ↔ ODEstimation relationship

**Status:** forensic audit, no production code changed.
**Purpose:** establish objectively how `ODEstimation` relates to `PRAISEHK2` before any
unified architecture is designed, and identify the genuinely new mathematics that must
be preserved.

**Follow-up (this document is now actionable):** the differentiability subset has been brought into
the reactor as the `differentiation` module, so the derivative leaves can be characterised in place.
Two inert source changes were required — the HK-fork imports were redirected to the classes already
vendored in `MetaModelCalibration`, and two CPLEX-bound debug printers were removed. See
`REVIEW_REQUIRED.md` DIFF-3 (the change) and ARCH-1 (why the module depends on `MetaModelCalibration`,
reproducing the inversion this document describes rather than fixing it). The first characterisation
results are DIFF-1 and DIFF-2, and they are significant: the BPR sensitivity was measured at **3600×**
too small against the travel-time function it is paired with.

## Baselines audited (evidence: `git rev-parse HEAD`)

| Repository | Branch | Commit | Location |
|---|---|---|---|
| PRAISEHK2 | `ODEstimationMatsim` | `77f93f25ed0332061953dc22e88cfc37120a1fb9` | `github.com/auzpatwary37/PRAISEHK2` |
| ODEstimation | `MATSimCalibrationReorganize` (**primary**) + `MATSimCalibration` (**secondary**) | `ff1005432d74769bfbc6476c0ab83f7f1b99b603` / see below | `github.com/auzpatwary37/ODEstimation` |
| MATSim-HK | `MAAS` | `7ada92ecdb4193b692e2b4acb8ea4e03bffb348b` | local-only fork; **no longer a build dependency** (see `PRAISE_MATSIMHK_RELATIONSHIP.md`) |

## ODEstimation branch selection (resolved)

The task brief did not name an ODEstimation branch, so the branches were compared explicitly rather
than assumed.

| Branch | Last commit | `ODDifferentiableSUEModel` | `GradientUtils` / `Clip` | `ODUtils` | Multi-time OD optimizers | `ParamCalibratorFunction` |
|---|---|---|---|---|---|---|
| `master` | 2021-08-03 | 2,251 lines | absent | 367 lines | absent | absent |
| `MATSimCalibration` | 2023-02-14 | 2,687 lines | absent | 716 lines | absent | **present (137 lines)** |
| `MATSimCalibrationReorganize` | 2024-03-26 | 2,811 lines | **present (202 / 25 lines)** | 753 lines | **present** (539 + 245) | absent |

Lineage (evidence: `git merge-base --is-ancestor`):

* `master` **is** an ancestor of `MATSimCalibration`.
* `MATSimCalibration` and `MATSimCalibrationReorganize` are **siblings, not ancestors** — their merge
  base is `187e0516b664b60c4fcdb46ced6dd72430f967b1`, and neither is an ancestor of the other.
  **Reorganize did not build on MATSimCalibration's changes.**

**Decision: `MATSimCalibrationReorganize` is the primary reference**, because it is the only branch
that (a) extracts the derivative mathematics into a separately testable unit
(`analyticalModel/GradientUtils`), (b) adds `Clip`, and (c) contains the multi-time OD optimizers
(`ODAdditionOptimizerMultipleTime`) and the direct-scaling work — 13 source/test files absent from
`MATSimCalibration`. Verified: Reorganize's `ODDifferentiableSUEModel` **delegates** its four gradient
computations to `GradientUtils` (lines 1923, 1935, 2140, 2158), whereas `MATSimCalibration` computes
the same expressions **inline** — the same mathematics, refactored. The extraction is what makes the
derivatives unit-testable in isolation, which is why Reorganize is the right basis for the
derivative work.

**`MATSimCalibration` is retained as a secondary reference** for the two files it uniquely contains —
`core/ParamCalibratorFunction.java` (137 lines, a COBYLA `Calcfc` that calibrates parameters against
`ObjectiveCalculator.TypeMeasurementAndTimeSpecific`, i.e. the ODE-side analogue of PRAISEHK's
`OptimizationFunction`) and its test `analyticalModel/ParamCalibrate.java`. These are **absent from
Reorganize** and must not be silently lost when the unified calibration core is designed.

So: one primary reference for differentiation, plus one named gap to re-read when the parameter
calibration boundary is defined. Any future claim of the form "ODE does X" must state which branch it
came from.

---

## 1. Headline finding: ODEstimation is a DOWNSTREAM MODULE of PRAISEHK, not a fork

This reverses the natural "two independent copies" assumption.

Evidence — ODEstimation source imports **PRAISEHK** classes directly (30+ distinct
classes across 7 packages):

```
15  ust.hk.praisehk.metamodelcalibration.measurements.Measurements
10  ...measurements.Measurement
10  ...analyticalModel.AnalyticalModelODpair
 8  ...measurements.MeasurementType
 5  ...measurements.MeasurementsWriter
 4  ...matsimIntegration.LinkCountEventHandler
 4  ...matsimIntegration.FareLinkVolumeCountEventHandler
 4  ...analyticalModelImpl.CNLTransitDirectLink
 3  ...analyticalModelImpl.CNLLink
 3  ...analyticalModelImpl.CNLTransitTransferLink
 3  ...analyticalModelImpl.CNLSUEModel        (constants only: BPRalpha, BPRbeta, LinkMiu, ModeMiu, ...)
 3  ...calibrator.ObjectiveCalculator
 3  ...calibrator.ParamReader
 2  ...analyticalModel.SUEModelOutput
 2  ...analyticalModel.AnalyticalModelNetwork
 1  ...calibrator.CalibratorImpl
 1  ...matamodels.AnalyticLinearMetaModel
 1  ...matamodels.MetaModel
 1  ...analyticalModelImpl.CNLTripChain
 1  ...Utils.Tuple
...
```

Consequences:

* There is **no duplicated `CNLLink`/`CNLRoute`/`CNLTransitRoute`** in ODEstimation. ODEstimation
  *reuses* PRAISEHK's network, route and transit-link classes and adds differentiation on top.
* The relationship is therefore **not circular**; it is a one-way dependency
  `ODEstimation → PRAISEHK`. The "circular historical relationship" mentioned in the brief
  is realised through the *build system*, not the source: neither repo declares the other
  as a Maven dependency, so both only compile inside Eclipse via project references.
  `ODEstimation/odestimation/pom.xml` declares `MATSim-HK:MATSim-HK:11.0` and
  `org.matsim:matsim:14.0-SNAPSHOT` but **no** `MetaModelCalibration` artifact, despite the imports.
  → **ODEstimation cannot be built standalone from its own pom.** This is a build blocker
  that must be fixed before any ODEstimation characterization test can run.

### Therefore: "DO NOT duplicate network/route/OD classes" is already true of ODEstimation

The duplication risk for the unified target is the opposite of what was assumed: the thing to
avoid is *ODDifferentiableSUEModel duplicating the SUE iteration* that `CNLSUEModel` already owns.

---

## 2. Per-class relationship table

| ODEstimation class | PRAISEHK counterpart | Relationship | Behaviour changed | New mathematics | Which is later / more complete |
|---|---|---|---|---|---|
| `analyticalModel/ODDifferentiableSUEModel` | `analyticalModelImpl/CNLSUEModel` | **reimplemented sibling** — `public class ODDifferentiableSUEModel {` does **not** extend `CNLSUEModel`; it has its own `performAssignment(...)` | forwards SUE is re-implemented; constants (`BPRalphaName`, `LinkMiuName`, `ModeMiuName`, `MarginalUtilityof*`) are imported from `CNLSUEModel`/`AnalyticalModel` | full **forward sensitivity propagation** through assignment | ODEstimation version is later and adds differentiation; the forward SUE is *duplicated*, so neither is canonical |
| `analyticalModel/GradientUtils` | *(none)* | **new** | — | analytic BPR/transit travel-time derivatives and MSA-consistent link-flow sensitivity (comments cite Equations 52–54, 64, 66) | ODEstimation only |
| `analyticalModel/Clip` | *(none)* | **new** (trivial) | — | `Math.min(Math.max(x,min),max)` `UnivariateFunction`, applied as `timeClip(-3600,3600)` and `flowClip(-9999,9999)` | ODEstimation only |
| `core/MapToArray` | `Utils/MapToArray` | **conceptually same, different API** — PRAISEHK's takes a `Map` and exposes array access; ODEstimation's stores a `List<T> keySet` and offers `getMatrix(Map)` / `getMap(double[])` | ODEstimation adds `extractMap`, `getRealVector`, `writeCSV` | explicit **variable-ordering abstraction** | ODEstimation version is the more complete one; the two must be unified into one immutable parameter ordering |
| `core/ODUtils` | *(none)* | **new** | — | OD parameterisation (`ODDemand___O___D`, origin/destination/sub-population/time multipliers — 16 variable kinds), `applyODPairMultiplier`, `applyODPairMultiplierAndAddition`, chain-rule objective gradients, `timeSplitMeasurements` | ODEstimation only |
| `core/SparseIncidenceMatrix` | PRAISEHK builds incidence inside `AnalyticalModelODpair.generateLinkIncidence()` / `generateRouteandLinkIncidence()` | **new abstraction over the same data** | replaces ad-hoc `Map<Id<Link>,List<Route>>` with `OpenMapRealMatrix` | sparse route/link, transit-route/link, fare-link incidence matrices | ODEstimation only |
| `mapMatrixConversion/MapMatirx` | *(none)* | **new** | — | incidence-map ⇄ matrix conversion helpers | ODEstimation only |
| `optimizer/Optimizer` (interface) | `calibrator/Calibrator`, `AnalyticalModelOptimizer` | **new, different abstraction** | — | `update(...)` / `getOptimizationDetails()` contract | ODEstimation only |
| `optimizer/Adam`, `GD` | *(none)* | **new** | — | first-order optimizers over the flat variable vector | ODEstimation only |
| `optimizer/RandomOptimizer` | `CalibratorImpl.drawRandomPoint` (`Math.random()`) | **replacement candidate** | deterministic-seedable if injected | — | ODEstimation version is a class, so it is the better seam for a seeded RNG |
| `optimizer/VariableDetails` | `ParamReader` + `Tuple<Double,Double>` bounds keyed by code | **replacement** | binds name + bounds + current value in one object | — | ODEstimation version is the better `ParameterDefinition` seed |
| `optimizer/ODAdditionOptimizer`, `ODAdditionOptimizerMultipleTime` | *(none)* | **new** | — | OD demand addition / adjustment optimization | ODEstimation only (Reorganize branch) |
| `metamodelIntegration/GradientBasedCalibrator`, `ODOptimizer`, `SUEModelOutputMNL` | `calibrator/CalibratorImpl`, `AnalyticalModelOptimizerImpl`, `analyticalModel/SUEModelOutput` | **parallel implementations** | gradient-based instead of trust-region | adds gradient-based OD calibration | ODEstimation is later; relationship to PRAISEHK trust-region to be determined (UNVERIFIED whether it replaces or complements) |
| `metamodelIntegration/AnalyticalModelControllerListener`, `AnaModelCalibrationModule`, `SimRun`, `MTRPassengerFlowCounter`, `PlanTranslationControlerListener`, `PopulationGenerator` | `matsimIntegration/*` equivalents | **duplicated MATSim plumbing** | differing module wiring | — | both exist; must be merged, not duplicated |

---

## 3. Differentiation method — evidence

### Published specification (authoritative)

The implementation is a realisation of the authors' published method, so the papers are the authority
for what the equations are *intended* to be, and the working rule agreed for this project is **treat the
code as correct unless it is inconsistent with these equations, or internally inconsistent.**

**That rule needs a caveat, because a published paper has now been found to contradict its own
numbers.** The two-link validation in the 2023 paper states one set of parameters in prose and
publishes a table reproducible only with a different set (documented below). So the operative rule is
narrower than "the papers are the authority":

* the **code** is canonical for *observed behaviour* — what the program actually computes;
* the **papers** are authoritative for *intent* — what the equations were meant to be;
* published **numbers** are the strongest oracle, because a solved equilibrium is internally
  constrained; published **prose** about parameters is not, and must be reproduced before use.

| Reference | Scope | Key equations |
|---|---|---|
| A.U.Z. Patwary, S. Wang, H.K. Lo (2023), *Iterative Backpropagation Method for Efficient Gradient Estimation in Bilevel Network Equilibrium Optimization Problems*, Transportation Science 57(5):1134–1159, doi:10.1287/trsc.2021.0110 | The gradient method itself: the IB recursion, BPR and logit derivatives, the objective and its gradient, the small-network validation | (3) chain rule; (6) MSA flow update; (7), (13), (16) gradient update; (8)–(9) cost/choice gradients; (10)–(11) link-flow aggregation; (14) BPR function and its derivative; (15) logit derivative; (17) finite-difference reference; (18) objective; (19) objective gradient; (27)–(30) route and mode utilities |
| A.U.Z. Patwary et al. (2021), *Metamodel-based calibration of large-scale multimodal microscopic traffic simulation*, Transportation Research Part C 124:102859, doi:10.1016/j.trc.2020.102859 | The calibration framework: trust-region metamodel calibration, the objective PRAISEHK actually uses, the multimodal SUE formulation | **extracted** (author-supplied PDF). Table 1 default parameters; **Table 2 trust-region parameters**; (45) linear metamodel; (46) quadratic; (36)–(37) GD-I; (40) GD-II; GD-III; (48) RMSE; (49) GEH |

Two facts from (2023) that constrain this repository directly:

* **Eq (6)/(13) require the flow update and the gradient update to use the SAME learning rate** `α`
  (MSA: `α = 1/ia`). `GradientUtils.getLinkFlowGrad` applies `1/beta` to the sensitivity update and
  `CNLSUEModel` applies its counter to the flow update; whether the *same* `ia` reaches both is
  testable and is the first thing to check on that leaf.
* **Withdrawn: "Eq (14)'s printed BPR derivative carries no `/3600`" as support for DIFF-1.** Two
  reasons. Eq (14) reaches this document through a garbled OCR rendering; and decisively, **DIFF-1 was
  a false positive** — the `/3600` is a unit convention. The producer returns hours per flow unit and
  the consumer (`getCarRouteGrads`:1828) multiplies by the *raw* `ΔMU`, while the level utility
  (`CNLRoute.calcRouteUtility`:101/119) multiplies seconds by `MU/3600`; the factors cancel exactly.
  See REVIEW_REQUIRED DIFF-1, now `RETRACTED`.

**Open item — now closed: the 2021 Part C paper has been extracted** (author-supplied copy). Two
results land on the work order.

* Its **Table 2** specifies the trust-region policy and constants, and the legacy constants **differ**:
  `η = 0.001` against the code's `0.01`; `μ_dec = 0.75` against `0.9`; `Δ0 = 10` and `Δmax = 25`
  against `25` and `2.5×Δ0 = 62.5`; "maximum consecutive rejection 5" against `4`. Recorded as
  **CAL-12**. The *control flow* is faithful — its Step 4 is exactly the three branches in
  `CalibratorImpl.generateNewParam`:349–365.
* On the CC-4 weighting question it gives partial evidence: §3.1 states "the default weighting factors
  [of the] three measurement types are taken as 1", so the paper's own experiments use the
  **unweighted** objective. That does not by itself settle `1/(1+SD)` versus `1/(1+SD²)` for the Hong
  Kong application, but it removes the assumption that SD weighting is the paper's default.
* Its §2.8.2 lists exactly six metamodel schemes — quadratic; analytical linear with and without
  traffic-model improvement (Eq (30)); and GD-I/II/III — which is the legacy `MetaModel` taxonomy, so
  the scheme names in the code are the paper's names.

A third fact, which lands directly on CC-4 and on step 8 of the work order:

* **Eq (18) defines the objective as `(1/2)·Σ_t Σ_l (x*_{l,t} − x^obs_{l,t})²` — unweighted, with an
  explicit `1/2`** — and Eq (19) gives its gradient as the residual-weighted contraction
  `Σ (x* − x^obs)·∇θx*`. So the gradient of the objective is *linear in the residual*, which is what
  makes it cheap to validate against finite differences. Two discrepancies with the legacy code to
  settle deliberately rather than assume:
  * the legacy `ObjectiveCalculator` (both the plain and the SD-weighted branches) omits the `1/2`
    factor — a constant scaling, so it changes gradient magnitudes but not the argmin;
  * **this paper's OD-estimation objective carries no SD weighting at all**, whereas PRAISEHK offers
    plain / SD-weighted / GEH / SD-weighted-GEH variants. Which variant is canonically "the"
    objective, and whether the `1/(1+SD)` or `1/(1+SD²)` form is intended, cannot be resolved from
    the 2023 paper — it is a (2021) or publication-independent question, and is the first thing the
    objective step must decide.

#### Published oracle: the two-link network (2023, §3, Tables 1–2)

The paper's small-network validation is directly reproducible as a fixture: two links `O→D`, BPR with
`α = 0.15`, `β = 4`, logit route choice, demands `q = 10` and `q = 100`.

**Parameters — the paper's prose contradicts its own table, and the table wins.** §3.2 says "Both L1
and L2 have a free flow travel time of 10 … their capacities are different, 50 and 70, respectively."
No parameterisation of the logit reproduces the published Table 1 from those values:

* free-flow 10 and 10 makes the links near-symmetric at `q = 10` (the BPR terms differ by ~6e-4 in
  travel time), so `P(link 1) ≈ 0.5`, not the published **0.9933**;
* a single logit scale `θ` fits the `q = 100` row at `θ ≈ 1`, and that same `θ = 1` forces `≈ 0.5`
  at `q = 10` — **no `θ` fits both rows** under the prose values;
* with free flow **(10, 15)** and capacities **(50, 75)**, `θ = 1` reproduces **every published
  value in both rows**: `P(link 1) = 1/(1+e^{−5}) = 0.9933` and `t₂ = 15.000` at `q = 10`; at
  `q = 100`, `t₁ = 10(1+0.15(65.629/50)⁴) = 14.455` and `c₂ = 34.371/(0.044)^{1/4} = 75.0`.

The fixture therefore uses free flow **(10, 15)** and capacities **(50, 75)** — the only
parameterisation consistent with the published numbers. The prose values are treated as a typo and
recorded as **PUB-1**. This is the concrete reason the rule at the top of this section is written as
reproduce-the-numbers rather than trust-the-text. Published equilibrium values (Table 1, SUE):

| q | link 1 flow | link 2 flow | P(link 1) | P(link 2) | t₁ | t₂ |
|---|---|---|---|---|---|---|
| 10 | 9.933 | 0.067 | 0.9933 | 0.0067 | 10.002 | 15 |
| 100 | 65.629 | 34.371 | 0.6563 | 0.3437 | 14.453 | 15.099 |

Published gradients (Table 2, IB against FD): at `q = 10`, `∇t = (9.35E-04, 5.71E-13)` with FD
reporting `0` for the second component; at `q = 100`, `∇x = (0.1242, 0.8758)`,
`∇w = (0.0053, 0.0053)`, `∇t = (0.0338, 0.0101)`. The single IB/FD disagreement in the paper is a
**documented machine-precision artefact**, not an algorithm error: the true change was ~5.7E-21
against a 1E-8 step, below MATLAB's 1E-16 precision.

This is an **external oracle in the strongest sense** — published numbers from an independent
implementation — and it should be encoded before any derivative leaf is trusted, because one fixture
then exercises the forward SUE, the logit derivative, the route-flow product rule, link-flow
aggregation and the objective gradient together. The paper's own FD uses a **forward** difference at
a fixed `c = 1E-8`; the harness's central-difference sweep over several step sizes is better
conditioned, and where the two disagree at `1E-8` the artefact above explains why.

### Method — evidence from the implementation

**Conclusion: this is the authors' Iterative Backpropagation (IB).** It is **not** reverse-mode
automatic differentiation of an objective covector, and **not** finite differences. The naming has to
be stated precisely, because it is easy to get wrong in either direction:

* The published method is *named* "iterative backpropagation"; the paper names the gradient step
  itself "gradient backpropagation" (§2.2) and labels Step 3 of the algorithm the "**backward pass**"
  (§3.2, Figure 1) with Step 2 the "forward pass". So "backpropagation" is the authors' own term —
  it is **not** a misnomer, and an earlier revision of this document was wrong to say it was.
* What "backward pass" means here is a **reverse-order sweep over the assignment sub-components
  within an iteration** (cost → choice → flow), not a reverse sweep of the whole equilibrium. The
  sensitivity carrier is a dense `double[]` with one entry per decision variable — a Jacobian column,
  not an objective covector — and it is accumulated **across** iterations by the MSA recursion of
  Eq (7)/(13).

**Withdrawn:** the earlier claim that "the README's 'back propagation' label is misleading and must
not be carried into modern naming". Recorded here rather than deleted so the correction is visible.


Evidence:

1. Derivative-carrying data type is a dense `double[]` of length `gradientKeys.size()`, i.e. one
   entry per decision variable, propagated **in the same direction as the model evaluation**:
   ```java
   private Set<String> gradientKeys;
   private Map<String,Map<Id<Link>,double[]>> linkGradient;
   private Map<String,Map<Id<Link>,double[]>> linkTTGradient;
   private Map<String,Map<Id<Link>,double[]>> routeFlowGradient;
   private Map<String,Map<Id<Link>,double[]>> fareLinkGradient;
   private Map<String,Map<Id<Link>,Map<String,double[]>>> trPassengerOnPhysicalLinkGradient;
   private MapToArray<String> gradientArray;
   ```
   A reverse/adjoint implementation would carry a covector w.r.t. the *objective*, not a
   Jacobian column per variable, and would not need one array per state variable.

2. The chain rule is applied explicitly, arrow by arrow, in the forward direction:
   * link travel time: `GradientUtils.getLinkTravelTimeGrad` computes
     `cons = alpha*beta*t0/cap^beta * flow^(beta-1)/3600` and returns
     `linkGradient.get(linkId) * cons` — i.e. `dt/dv · dv/dθ`, clipped.
   * route utility → route probability: in `ODDifferentiableSUEModel`,
     `carUGradient` accumulates `∂V_r/∂θ`; the logit term is then
     `(∂V_r/∂θ − Σ_j P_j ∂V_j/∂θ)·d·pm·pr·LinkMiu` — the standard logit derivative
     `∂P_r/∂θ = P_r(∂V_r/∂θ − Σ_j P_j ∂V_j/∂θ)`, evaluated **forward**.
   * mode choice: `modeC = carUGradient*pm + trUtGrad*(1-pm)` (source cites "Equation (58)").
   * route flow: `tt2 = odInc.ebeDivide(p) * (pr*pm*d) / gradMultiplier` — the product rule
     `∂f_r/∂θ = ∂(q·P_mode·P_route)/∂θ` evaluated term by term.
   * link flow / MSA: `GradientUtils.getLinkFlowGrad` applies the **same MSA recursion to the
     sensitivities**: `gUpdate = old + (Σ routeFlowGrad − old)·(1/beta)`, so the derivative is
     carried through the fixed-point iteration rather than differentiated after convergence.

3. Finite differences appear only as a **verification/fallback** utility
   (`SimAndAnalyticalGradientCalculator` in PRAISEHK; `GradientUtils` is analytical), not as the
   primary mechanism. No adjoint code was found.

### Derivative-arrow map (verified / not verified)

| Arrow | Derivative propagated? | Method | Verified |
|---|---|---|---|
| θ → OD demand | yes | `ODUtils.applyODPairMultiplier` multipliers; `∂/∂θ` = product of present multipliers, handled via `ifMatch_1_else_0` | partial |
| OD demand → mode probability | yes | logit derivative, `modeC` term | yes (code read) |
| mode prob → car/PT demand | yes | `pm`,`(1-pm)` factors | yes |
| → route utility | yes | utility is linear in TT/distance/money → direct coefficients (`MarginalUtilityof*`) | yes |
| → route probability | yes | logit derivative `P(δ − ΣP)` | yes |
| → route flow | yes | product rule via `odInc.ebeDivide(p)*pr*pm*d` | yes |
| → link flow | yes | `getLinkFlowGrad`, MSA-consistent | yes |
| → link travel time | yes | `getLinkTravelTimeGrad`, BPR derivative | yes |
| → next SUE iteration | yes | MSA weight applied to sensitivities | partial |
| → measurement | yes | `ODUtils.calcODObjectiveGradient` and friends | yes |
| → objective | yes | `ODUtils.calcODObjective` (= 0.5·squared error) + gradient | yes |

**Not verified:** whether the sensitivity recursion is applied for *every* iteration type
(including the internal-parameter calibration re-runs) and whether `initializeGradient`/
`intiializeGradient` resets sensitivities at the right time. This needs dedicated tests
(see §7).

---

## 4. Variable ordering / `MapToArray`

* `MapToArray(String id, Map<T,Double> inputMap)` captures `new ArrayList<>(inputMap.keySet())`.
  **If the caller passes a `HashMap`, the ordering is not deterministic.** `ODDifferentiableSUEModel`
  builds `gradientArray` from `this.gradientKeys` (a `Set<String>`), so the ordering safety depends
  entirely on how `gradientKeys` is constructed — **UNVERIFIED, high risk.**
* `getMatrix(Map<T,Double> map)` fills `out[i]` from the captured ordering and **silently writes 0
  for any key not present in the map**. The dimension-mismatch guard is commented out:
  ```java
  //	if(map.size()!=this.keySet.size()) { ... throw new IllegalArgumentException("dimension mismatch!!!"); }
  ```
  ⇒ a missing variable silently contributes a zero gradient coordinate; an extra variable is
  silently ignored. This is a correctness hazard for gradient-based optimization.

### HARD PRECONDITION — no derivative result is trustworthy until ordering is pinned

This is not a "cleanup risk"; it invalidates results while producing numerically plausible vectors.
Explicitly:

> **No gradient-validation result, finite-difference comparison, or derivative-based conclusion in
> this modernization is trustworthy until (a) the parameter-name ↔ array-index mapping is
> deterministic, and (b) the key set is checked for exact equality (missing keys must fail, not
> silently become `0`).**

Two independent failure modes have to be closed:

1. **Non-deterministic ordering.** If `gradientKeys` is backed by a `HashMap`/`HashSet`, the same
   parameter vector can produce different coordinate assignments between runs. A finite-difference
   comparison would then compare derivatives along *different* coordinate axes and could pass or fail
   at random.
2. **Silent key-set mismatch.** `getMatrix` writes `0` for any key absent from the map and ignores
   extras, with the dimension guard commented out. A wrong-sized or misnamed parameter set therefore
   yields a *valid-looking* zero in the wrong coordinate.

Consequences for the plan:

* The legacy characterization harness must **first pin whatever ordering the current code actually
  uses**, then assert exact key-set equality, before any derivative oracle is written. This is item 1
  of the Track B gate in `ARCHITECTURE.md` §6.
* The modern target needs `ParameterOrdering` as a first-class **immutable** value: one explicit,
  serialisable ordering shared by `MapToArray`, the OD variable naming, the gradient vectors and the
  optimizer — with construction-time validation that the key set matches exactly.
* `PRAISEHK`'s own `Utils/MapToArray` has the same class of hazard and must be pinned by the same
  tests when the two are unified.

---

## 5. Legacy gradient scaling — exactly where and what it does

| Item | Location | Exact form | Alters the model derivative? |
|---|---|---|---|
| `gradMultiplier` | `ODDifferentiableSUEModel:163`, initialised to all-ones at `:1732` | used as a **divisor**: `tt2 = odInc.ebeDivide(p).mapMultiply(pr*pm*d).ebeDivide(MatrixUtils.createRealVector(this.gradMultiplier))` | **YES** — per-coordinate scaling of the computed sensitivity |
| `timeClip` | `GradientUtils:26` | `new Clip(-3600, 3600)`, applied via `.map(timeClip)` to travel-time gradients | **YES** — hard truncation of the derivative |
| `flowClip` | `GradientUtils:27` | `new Clip(-9999, 9999)` on transit link-volume gradients | **YES** — truncation |
| `linkGradL1NormThreshold` | `ODDifferentiableSUEModel:119`, re-set to `gradientKeys.size()*3600` at `:1769` | L1 rescaling — the active code is **commented out** in `GradientUtils` | currently NO (disabled); if re-enabled, YES |
| `maxAbsGrad = .2`, `minAbsGrad = 0.01` | `ODDifferentiableSUEModel:165–166` | fields present, used by `scaleGradients()` / `scaleBackGradients()` at `:2725` / `:2638` | **YES, potentially** — must be characterized before use |
| `maxAbsL1Norm = 1e150`, `minAbsL1Norm = 1e-150` | `:167–168` | extreme L1 guards | to be characterized |
| `gehMult` | `ODUtils.calcODGEHObjectiveGradient` | `4·real²/((1+σ²)·(model+real)²)`, applied only when finite | objective-side chain-rule factor (legitimate), not model scaling |
| dead debug guard | `GradientUtils.getLinkFlowGrad` | `if(g.getMaxValue() > 50) { System.nanoTime(); }` | NO — no-op; dead code (smell) |

**Requirement:** model derivative, objective derivative and optimizer transformation must be
separated. Items marked YES above are optimizer/robustness transformations that currently
contaminate the model-derivative layer and must move behind an explicit optimizer boundary.

---

## 6. Objective-gradient inconsistencies (REVIEW_REQUIRED)

The objective itself is PRAISEHK's `ObjectiveCalculator` (ODEstimation calls it), but
ODEstimation re-implements its gradient by hand. Two independent inconsistencies are visible
in `ODUtils`:

1. **SD weighting mismatch.** `ObjectiveCalculator.calcSDWeightedObjective` weights by
   `1/(1+SD²)` (see PRAISEHK `REVIEW_REQUIRED.md` OBJ-3). But
   * `ODUtils.calcODObjectiveGradient` uses `g.mapMultiplyToSelf(1/(1+sigma))`  ← **1+σ, not 1+σ²**
   * `ODUtils.calcMetamodelODObjectiveGradient` uses `g.mapMultiplyToSelf(1/(1+sigma*sigma))` ← 1+σ²
   So the plain objective gradient and the metamodel objective gradient use *different*
   weighting for the same objective family. At least one is inconsistent with the objective.
2. **Copy/paste index bug in `calcODObjectiveGradient`.** In the `fareLinkVolumeCluster` branch
   the loop variable `fl` is ignored and the lookup uses the singular attribute:
   ```java
   for(FareLink fl:fareLinks) {
       if(model.getFareLinkGradient().get(timeId.getKey()).get(fl.toString())!=null) {
           g = g.add(... model.getFareLinkGradient().get(timeId.getKey())
                       .get(m.getAttribute(Measurement.FareLinkAttributeName)) ...);   // <-- wrong key
       }
   }
   ```
   The equivalent branch in `calcMetamodelODObjectiveGradient` correctly uses `fl.toString()`.
   The singular attribute is normally `null` for cluster measurements, so this branch is at best
   a no-op and at worst an NPE.

---

## 7. Implications for the unified target architecture

**Genuinely new mathematics worth preserving (from ODEstimation):**
* `GradientUtils` analytic derivatives (BPR, transit direct/transfer links, MSA-consistent link flow).
* Logit/mode/route-flow/mode-choice forward sensitivity propagation in `ODDifferentiableSUEModel`.
* OD parameterisation and OD-parameter incidence (`ODUtils.createODMultiplierVariableName`,
  `applyODPairMultiplier`, `ifMatch_1_else_0`).
* `SparseIncidenceMatrix` / `MapMatirx` incidence abstractions.
* Chain-rule objective gradients (`ODUtils.calcOD{Objective,GEHObjective}Gradient`, metamodel variants)
  — *after* the inconsistencies in §6 are resolved.

**Must NOT be duplicated (already shared, or should be):**
* Network/route/transit-link classes (`CNLLink`, `CNLRoute`, `CNLTransitRoute`, `CNLTransit*Link`) — ODEstimation
  already imports these; the modern system must keep one copy.
* The SUE iteration itself. `ODDifferentiableSUEModel.performAssignment` duplicates
  `CNLSUEModel`'s assignment. The target must be **one** `StaticSUEEvaluator`, with
  differentiation as a separate capability — not a second assignment engine.
* Objective functions and measurement extraction (`ObjectiveCalculator`, `MeasurementType`) — ODEstimation
  already imports these.

**Optimizer-only concerns to move out of the derivative layer:**
* `gradMultiplier`, `Clip` usage, `maxAbsGrad`/`minAbsGrad`, L1 rescaling, Adam/GD learning rates.
* Target: `ModelDerivative` (exact, unclipped) → `ObjectiveDerivative` (chain rule) →
  `OptimizerTransformation` (clipping/scaling/trust-region). Today these three are interleaved.

**Recommended unified shape (to be validated by tests, not implemented now):**
```
StaticSUEEvaluator                     (one assignment engine — from CNLSUEModel, hardened)
   + SensitivityEngine                  (forward-mode, from GradientUtils + ODDifferentiableSUEModel)
   = DifferentiableStaticSUEEvaluator   (composition, not a copy)
```
with `ParameterOrdering` (immutable) as the shared contract between `MapToArray`,
`VariableDetails` and every `double[]` gradient vector.

---

## 8. Immediate blockers for ODEstimation work

1. **Undeclared dependency on PRAISEHK.** `ODEstimation/odestimation/pom.xml` has no
   `MetaModelCalibration` dependency although the source imports 30+ of its classes. Until this
   is declared (e.g. add `MetaModelCalibration` as a module dependency in the same reactor),
   ODEstimation cannot be compiled or tested in CI.
2. **MATSim version split.** ODEstimation targets `matsim 14.0-SNAPSHOT` while PRAISEHK and
   MATSim-HK target `15.0-SNAPSHOT` (and MATSim-HK is imported by ODEstimation). These cannot
   coexist in one reactor without an upgrade; do not attempt it in this PR.
3. **`gradientKeys` ordering determinism** (§4) must be established before any gradient test is
   meaningful.

## 9. Next PR for this area

See the PR plan in the final report. The recommended first ODEstimation PR is:
declare the `MetaModelCalibration` dependency + add a `SensitivityEngine` characterization harness
with the finite-difference oracle (`docs/modernization/TEST_MATRIX.md`, "differentiation" rows),
starting from the BPR derivative (`GradientUtils.getLinkTravelTimeGrad`) whose expected value is
hand-derivable: `dt/dv = t0·α·β·v^(β−1)/c^β`.
