# PRAISEHK ↔ MATSim-HK relationship

**Status:** forensic audit complete; the fork is **no longer a build dependency**.
The two classes PRAISEHK actually needs have been vendored into this module.

## 1. Provenance of the audited fork

| Item | Value |
|---|---|
| Origin | `gitlab.com/leeenoch1005/MATSim-HK` (MATSim fork with Hong Kong extensions) |
| Branch | `MAAS` |
| HEAD commit | `7ada92ecdb4193b692e2b4acb8ea4e03bffb348b` |
| Local reference copy | `/home/ashraf/Documents/MATSim-HK` (not on GitHub, ~2.9 MB of source) |
| Coordinates | `MATSim-HK:MATSim-HK:11.0` |
| Committed state | **does not compile** — the committed `pom.xml` has the MATSim dependencies commented out (`matsim 13.0-SNAPSHOT`, `roadpricing`/`signals`/`emissions`). Only the working tree (which activates `15.0-SNAPSHOT`) builds. |
| Retained in this repo? | **No.** The fork is not committed here; it remains a separate local reference implementation. |

## 2. What PRAISEHK actually needs from it

PRAISEHK's sources referenced exactly **six** fork classes:

```
dynamicTransitRouter.TransitStop
dynamicTransitRouter.fareCalculators.FareCalculator
dynamicTransitRouter.fareCalculators.MTRFareCalculator
dynamicTransitRouter.transfer.TransferDiscountCalculator
transitFareAndHandler.FareLink
transitFareAndHandler.TransitFareHandler
```

Those six were used to seed a transitive closure of the fork. Two analyses were run, and the
difference between them matters:

| Closure analysis | Result |
|---|---|
| Import-only (explicit `import` statements) | 34 files / 9,281 LOC |
| **Import + same-package (implicit) references** — the correct analysis | **39 files / 11,024 LOC**, reaching `createBus/BusDataExtractor` (jackcess), `running/RunUtils`, `networkFromSaturn/CreateNetworkUtils`, `createPTGTFS/FareCalculatorPTGTFS` (gson), `withinDay/EquivalentStopForFare` |

A same-package reference (`dynamicTransitRouter.RouteHelper` using `TransitLineRoute` with no import)
is invisible to import-only analysis — an early under-count of "8 files" was wrong for exactly this
reason and is recorded here so the mistake is not repeated.

### The pivot edge

A single edge created almost the entire tail:

```java
// dynamicTransitRouter/fareCalculators/MTRFareCalculator.java
import dynamicTransitRouter.DynamicRoutingModule;                 // <-- the pivot
...
@Inject @Named(DynamicRoutingModule.fareRateName) private double fareFactor = 1.;
```

`DynamicRoutingModule.fareRateName` is just the string `"FareRate"`. That one import pulled in the
dynamic transit router → `PTRecordHandler` → `TransitRouterFareDynamicImpl` → `RunUtils` →
`BusDataExtractor` (jackcess), plus `EquivalentStopForFare` and `FareCalculatorPTGTFS` (gson).
Removing it was therefore a single-constant change.

### Which of the six are actually used

Active (non-comment) references in PRAISEHK main sources:

| Class | Active references | Verdict |
|---|---|---|
| `FareCalculator` (interface) | **21** — used as `Map<String, FareCalculator>` in `AnalyticalModel`, `CNLSUEModel`, `SUEModelContTime`, `AnaModelControlerListener`, `RoutesAndODGeneratorControllerListener`, `SmartCardEntryAndExitEventHandler` | **required** |
| `FareLink` | **31** — constructed/inspected throughout `MeasurementType`, `Measurement`, `CNLTransitRoute`, `AnalyticalModelTransitRoute`, `FareLinkVolumeCountEventHandler` | **required** |
| `MTRFareCalculator` | **0** — import only; the three usages in `CNLTransitRoute` are commented out | not required |
| `TransitStop` | **0** — unused import in `MeasurementType` | not required |
| `TransitFareHandler` | **0** — unused import in `AnaModelControlerListener` | not required |
| `TransferDiscountCalculator` | **0** — unused import in `SmartCardEntryAndExitEventHandler` | not required |

So the genuine requirement is **two classes**, both virtually dependency-free (they import only
`java.util` and `org.matsim`).

## 3. What was done

1. Vendored `FareCalculator` and `FareLink` into this module under a PRAISEHK namespace:

   ```
   MetaModelCalibration/src/main/java/ust/hk/praisehk/metamodelcalibration/transit/fare/
       FareCalculator.java
       FareLink.java
   ```

   Both are **byte-for-byte identical to the fork except for the `package` declaration** (verified by
   `diff`: one changed line each). Line endings were deliberately preserved as CRLF so the diff stays
   a one-liner and provenance is provable.

2. Removed the four dead imports (`MTRFareCalculator`, `TransitStop`, `TransitFareHandler`,
   `TransferDiscountCalculator`) and re-pointed the remaining imports.

3. Removed the `matsim-hk` module from the build and deleted the aggregator pom; the build is a single
   Maven module again.

4. Added `FareLinkTest` (10 tests) pinning the fare-link grammar, round-tripping, validation and the
   XML-serialisation constants, since `MeasurementType` depends on this grammar.

5. **No new third-party dependency was introduced.** `FareCalculator`/`FareLink` need only
   `org.matsim`; the transitive imports (`log4j`, `guava`, `commons-csv`) that the *dropped* classes
   would have needed are irrelevant now.

### Historical failure this fixes

The pre-existing build was:

* `MetaModelCalibration/.classpath` → `kind="src" path="/MATSim-HK"` (an Eclipse project reference),
  so `mvn compile` never worked from a clean clone;
* the `transitFareAndHandler` package was absent from **every** tracked JAR — it existed only in an
  untracked `MATSim-HK-11.0.jar` (the tracked `MATSim-HK-0.11.0.jar` does not contain it);
* `jcool-core.jar` (`cz.cvut.fit.jcool.*`) was required but undeclared;
* `ea.jar` / `javabuilder.jar` / `OptimDemo{1,2}.jar` (~27 MB) were dead weight —
  `Utils/MatlabOptimizer.performOptimization()` has every MATLAB statement commented out and
  `return null;`.

## 4. Deliberately NOT vendored (and why)

The remaining 37 files of the closure are **not** needed to compile or (on the evidence of §2) to
run PRAISEHK:

* `createBus/*` (14 files) incl. `BusDataExtractor` — HK minibus `.mdb` data pipeline; would require
  adding `jackcess`.
* `createPTGTFS/FareCalculatorPTGTFS`, `dynamicTransitRouter/transfer/AllPTTransferDiscount` — would
  require adding `gson`.
* `running/RunUtils`, `networkFromSaturn/CreateNetworkUtils`, `withinDay/EquivalentStopForFare`,
  `assignLinkToPlanActivity/*` — HK file/network plumbing.
* `dynamicTransitRouter/TransitRouterFareDynamicImpl`, `DynamicRoutingModule`,
  `FareDynamicTransitTimeAndDisutility`, `costs/*` — the dynamic transit router. PRAISEHK supplies its
  own `CNLTransitRoute`/`CNLTransitDirectLink`/`CNLTransitTransferLink` transit model.

If a later phase needs real fare *calculation* inside PRAISEHK (rather than the `FareCalculator`
contract), the right move is to implement the calculators against this two-class contract — and to
write the mathematical oracle tests first — not to re-import the fork.

## 5. Residual risks

1. **Dead imports were removed.** If a follow-up was intended to re-enable MTR fare calculation via
   `MTRFareCalculator`, that class no longer exists here. The commented-out block in
   `CNLTransitRoute` (lines ~317–332) documents the original intent and should be either implemented
   properly or deleted.
2. **`FareLink` is a parsing contract with weak validation** — see `REVIEW_REQUIRED.md` FARE-1/FARE-2.
   `MeasurementType` builds a `FareLink` from the measurement id when the attribute is absent, so
   measurement ids must be valid fare descriptions.
3. **The fork still cannot be built from its own committed HEAD** (§1). If it is ever needed again,
   its working tree must first be reconciled.
4. **No runtime call path was exercised.** These classes are compile-time contracts here; their
   behaviour under a MATSim run (fare extraction events) has not been executed in this environment.
5. `AnalyticalModelTransitRoute.getFare(...)` accepts `Map<String, FareCalculator>` and
   `List<FareLink>`, but **no `FareCalculator` implementation exists in this module** — the map is
   empty/null unless a caller supplies one. This is `[U]` and should be characterised before the
   transit utility is touched.

## 6. Next steps for this area

1. Decide the fate of the commented-out `MTRFareCalculator` usage in `CNLTransitRoute`.
2. Write oracle tests for whatever `FareCalculator` implementation is eventually supplied, and for
   `AnalyticalModelTransitRoute.getFare(...)`'s contribution to the transit utility.
3. Tighten `FareLink` parsing (FARE-1/FARE-2) only after those tests exist, since the grammar feeds
   `MeasurementType` serialization.
