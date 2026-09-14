# PRAISEHK

Legacy scientific transportation-modelling software: an analytical multimodal static
user-equilibrium (SUE) assignment model with measurements, objective functions,
surrogate/meta-model calibration and trust-region MATSim calibration.

PRAISEHK is treated as a **scientific reference implementation**. The modernization effort
prioritises, in order: mathematical correctness, behavioural characterization, reproducibility,
modular architecture, dependency modernization, performance.

## Baseline

The behavioural reference baseline is branch **`ODEstimationMatsim`** (not `master`).
Modernization work happens on feature branches created from it.

## Repository layout

```
PRAISEHK2/
├── pom.xml                  reactor aggregator - lets Maven run from the repository root
├── MetaModelCalibration/    PRAISEHK core: analytical SUE, measurements, objectives,
│                            calibrator, meta-models, MATSim integration
├── docs/modernization/      Forensic audit, behaviour catalogue, test matrix, defects
└── README.md
```

The Hong Kong MATSim fork (`MATSim-HK`) is **no longer a build dependency**. The two classes PRAISEHK
actually needed from it — `FareCalculator` and `FareLink` — are vendored under
`ust.hk.praisehk.metamodelcalibration.transit.fare`, byte-for-byte identical to the fork apart from
their `package` declaration. See
[`PRAISE_MATSIMHK_RELATIONSHIP.md`](docs/modernization/PRAISE_MATSIMHK_RELATIONSHIP.md).

## Build and test

Requirements: JDK 17. The build is self-contained and works **offline** once dependencies are cached.

```bash
mvn -B test              # full deterministic test suite
mvn -o -B test           # same, offline (no network access)
mvn -B -DskipTests test-compile
```

There is a reactor POM at the repository root, so Maven can be run from the top level; `cd
MetaModelCalibration` works identically, because the Surefire harness pins the test working directory
to the module either way.

`MetaModelCalibration/src/main/resources/jcool-core.jar` is declared with `system` scope because it is
not published anywhere. See `docs/modernization/DEPENDENCIES.md`.

Test rules — every test must be deterministic and must **not** require:

* Hong Kong production data;
* absolute filesystem paths (use JUnit `@TempDir`);
* MATLAB;
* network access;
* `Math.random()`;
* `HashMap` iteration order.

The pre-existing `AnalyticLinearMetaModelTest` does not satisfy these rules (it is nondeterministic
and does not terminate in reasonable time); it is quarantined via a surefire `<exclude>` and is
scheduled for replacement.

## Modernization documentation

| Document | Contents |
|---|---|
| [`ARCHITECTURE.md`](docs/modernization/ARCHITECTURE.md) | module/package architecture, cross-repo dependencies, target architecture, roadmap |
| [`LEGACY_BEHAVIOR.md`](docs/modernization/LEGACY_BEHAVIOR.md) | mathematical responsibility of each important class, with `[V]`/`[T]`/`[U]` confidence markers |
| [`TEST_MATRIX.md`](docs/modernization/TEST_MATRIX.md) | mathematical-behaviour coverage: characterization / oracle / boundary / integration per equation |
| [`DEPENDENCIES.md`](docs/modernization/DEPENDENCIES.md) | every dependency, its users, provenance, verdict, upgrade order |
| [`REVIEW_REQUIRED.md`](docs/modernization/REVIEW_REQUIRED.md) | suspected defects recorded **without** fixing them |
| [`OBJECTIVE_PURITY_PLAN.md`](docs/modernization/OBJECTIVE_PURITY_PLAN.md) | why the objective must become a pure function, the impurity inventory, and the ordered, purity-preserving vs behaviour-changing steps |
| [`PRAISE_ODE_RELATIONSHIP.md`](docs/modernization/PRAISE_ODE_RELATIONSHIP.md) | relationship to the `ODEstimation` repository; identification of the actual differentiation method |
| [`PRAISE_MATSIMHK_RELATIONSHIP.md`](docs/modernization/PRAISE_MATSIMHK_RELATIONSHIP.md) | how the `MATSim-HK` fork dependency was audited and removed; what was vendored and what was not |

## Development rule

RED → GREEN → REFACTOR. Legacy code without tests first receives **characterization tests**, and any
important numerical code should also have an **independent oracle test**. If the two disagree, the
disagreement is recorded in `REVIEW_REQUIRED.md`; the equation is not changed until reviewed.
