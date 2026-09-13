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
├── pom.xml                  Maven aggregator (praisehk-parent)
├── matsim-hk/               Hong Kong MATSim extension fork (legacy reference; transit router + fares)
├── MetaModelCalibration/    PRAISEHK core: analytical SUE, measurements, objectives,
│                            calibrator, meta-models, MATSim integration
└── docs/modernization/      Forensic audit, behaviour catalogue, test matrix, defects
```

## Build and test

Requirements: JDK 17. The build is self-contained and works **offline** once dependencies are cached.

```bash
mvn -B test          # full deterministic test suite
mvn -o -B test       # same, offline (no network access)
mvn -B -DskipTests test-compile
```

`matsim-hk` is a real source module in this reactor (it was previously an untracked JAR plus an
Eclipse project reference, which is why `mvn compile` never worked from a clean clone).
`MetaModelCalibration/src/main/resources/jcool-core.jar` is declared with `system` scope because it
is not published anywhere. See `docs/modernization/DEPENDENCIES.md`.

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
| [`PRAISE_ODE_RELATIONSHIP.md`](docs/modernization/PRAISE_ODE_RELATIONSHIP.md) | relationship to the `ODEstimation` repository; identification of the actual differentiation method |
| [`PRAISE_MATSIMHK_RELATIONSHIP.md`](docs/modernization/PRAISE_MATSIMHK_RELATIONSHIP.md) | relationship to the local-only `MATSim-HK` fork; how the build dependency is now modelled |

## Development rule

RED → GREEN → REFACTOR. Legacy code without tests first receives **characterization tests**, and any
important numerical code should also have an **independent oracle test**. If the two disagree, the
disagreement is recorded in `REVIEW_REQUIRED.md`; the equation is not changed until reviewed.
