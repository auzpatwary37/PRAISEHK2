package ust.hk.praisehk.metamodelcalibration.calibrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

/**
 * PHASE 3 - ObjectiveCalculator characterization.
 *
 * <p>These tests record what the LEGACY implementation actually does. They are
 * deliberately written BEFORE any clean-up, and several of them assert
 * behaviour that is believed to be a defect. Each such test names the item in
 * {@code docs/modernization/REVIEW_REQUIRED.md} and does NOT propose a fix.</p>
 *
 * <p>Terminology: "TS" = {@code TypeMeasurementAndTimeSpecific}; "AADT" =
 * {@code TypeAADT}.</p>
 */
class ObjectiveCalculatorTest {

	private static final String TB1 = "Hour1";
	private static final String TB2 = "Hour2";
	private static final double EPS = 1e-9;

	private static Measurements newMeasurements() {
		return Measurements.createMeasurements(TimeBeans.twoHours());
	}

	private static Measurement add(Measurements m, String id) {
		return m.createAnadAddMeasurement(id, MeasurementType.linkVolume);
	}

	/** real and sim share one measurement id, one time bean. */
	private static Measurements[] pair(String id, double observed, double modelled, double sd) {
		Measurements real = newMeasurements();
		Measurements sim = newMeasurements();
		Measurement r = add(real, id);
		add(sim, id);
		r.putVolume(TB1, observed);
		r.putSD(TB1, sd);
		sim.getMeasurements().get(Id.create(id, Measurement.class)).putVolume(TB1, modelled);
		return new Measurements[] { real, sim };
	}

	// ------------------------------------------------------------------
	// Exact match
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("exact match -> zero objective")
	class ExactMatch {

		@Test
		void allObjectiveTypesAreZeroWhenPerfect() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			Measurement r = add(real, "m1");
			add(sim, "m1");
			r.putVolume(TB1, 1000.);
			r.putSD(TB1, 50.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 1000.);

			assertEquals(0., ObjectiveCalculator.calcObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
			assertEquals(0., ObjectiveCalculator.calcObjective(real, sim,
					ObjectiveCalculator.TypeAADT), EPS);
			assertEquals(0., ObjectiveCalculator.calcSDWeightedObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
			assertEquals(0., ObjectiveCalculator.calcSDWeightedObjective(real, sim,
					ObjectiveCalculator.TypeAADT), EPS);
			assertEquals(0., ObjectiveCalculator.calcGEHObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
			assertEquals(0., ObjectiveCalculator.calcSDWeightedGEHObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
		}

		@Test
		void zeroObservedZeroModelledIsZeroForSquaredError() {
			Measurements[] p = pair("m1", 0., 0., 0.);
			assertEquals(0., ObjectiveCalculator.calcObjective(p[0], p[1],
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
		}
	}

	// ------------------------------------------------------------------
	// Squared error
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("squared error")
	class SquaredError {

		@Test
		@DisplayName("TS: one residual contributes exactly residual^2")
		void singleResidual() {
			Measurements[] p = pair("m1", 1000., 900., 0.);
			assertEquals(10000., ObjectiveCalculator.calcObjective(p[0], p[1],
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
		}

		@Test
		@DisplayName("TS: several measurements and time beans are summed per (m,tb)")
		void multipleMeasurementsAndTimeBeans() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			for (String id : new String[] { "m1", "m2" }) {
				add(real, id);
				add(sim, id);
			}
			// m1: h1 residual 10, h2 residual -20 -> 100 + 400
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 110.);
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB2, 180.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 100.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB2, 200.);
			// m2: h1 residual 0, h2 residual 5 -> 25
			real.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 50.);
			real.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB2, 75.);
			sim.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 50.);
			sim.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB2, 70.);

			assertEquals(525., ObjectiveCalculator.calcObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED OBJ-1: AADT accumulates station counts ACROSS "
				+ "measurements, so the objective is not a sum of per-station residuals")
		void aadtIsCumulativeAcrossMeasurements() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			for (String id : new String[] { "m1", "m2" }) {
				add(real, id);
				add(sim, id);
			}
			// two independent residuals, r1 = 10 and r2 = 10
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 110.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 100.);
			real.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 210.);
			sim.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 200.);

			double aadt = ObjectiveCalculator.calcObjective(real, sim,
					ObjectiveCalculator.TypeAADT);
			// ACTUAL legacy behaviour: 10^2 + (10+10)^2 = 100 + 400 = 500.
			assertEquals(500., aadt, EPS);
			// The mathematically expected AADT objective would be 100 + 100 = 200.
			assertFalse(Math.abs(aadt - 200.) < EPS,
					"documents that AADT is NOT the per-station sum of squares");
		}

		@Test
		@DisplayName("AADT equals the per-station sum when there is exactly one measurement")
		void aadtSingleMeasurementMatchesPerStation() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			add(real, "m1");
			add(sim, "m1");
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 110.);
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB2, 220.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 100.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB2, 200.);
			// counts are summed over time beans, then squared: (10+20)^2 = 900
			assertEquals(900., ObjectiveCalculator.calcObjective(real, sim,
					ObjectiveCalculator.TypeAADT), EPS);
		}
	}

	// ------------------------------------------------------------------
	// Missing data
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("missing measurement / missing time bean")
	class MissingData {

		@Test
		@DisplayName("TS: a missing measurement is SKIPPED (logged, 'continue')")
		void tsSkipsMissingMeasurement() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			add(real, "m1");
			add(real, "m2");
			add(sim, "m1");
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 100.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 90.);
			real.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 55.);

			// m2 contributes nothing; only m1's residual 10^2 = 100 remains.
			assertEquals(100., ObjectiveCalculator.calcObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED OBJ-2: AADT only logs a missing measurement and "
				+ "then dereferences it -> NullPointerException")
		void aadtThrowsOnMissingMeasurement() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			add(real, "m1");
			add(real, "m2");
			add(sim, "m1");
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 100.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 90.);
			real.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 55.);

			assertThrows(NullPointerException.class, () -> ObjectiveCalculator.calcObjective(
					real, sim, ObjectiveCalculator.TypeAADT));
		}

		@Test
		@DisplayName("TS: a missing time bean is skipped; AADT throws")
		void missingTimeBeanDiffersByType() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			add(real, "m1");
			add(sim, "m1");
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 100.);
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB2, 200.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 90.);
			// sim has no volume for TB2

			assertEquals(100., ObjectiveCalculator.calcObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
			assertThrows(NullPointerException.class, () -> ObjectiveCalculator.calcObjective(
					real, sim, ObjectiveCalculator.TypeAADT));
		}
	}

	// ------------------------------------------------------------------
	// GEH
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("GEH")
	class GehTests {

		@Test
		@DisplayName("REVIEW_REQUIRED OBJ-4: the GEH objective accumulates GEH^2, not GEH")
		void gehObjectiveIsSquaredGeh() {
			Measurements[] p = pair("m1", 100., 80., 0.);
			// GEH = sqrt(2*(100-80)^2/(100+80)) = sqrt(800/180) = 2.1081851...
			double geh = Math.sqrt(2. * 400. / 180.);
			double legacy = ObjectiveCalculator.calcGEHObjective(p[0], p[1],
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific);

			assertEquals(800. / 180., legacy, EPS);          // GEH^2
			assertEquals(geh * geh, legacy, 1e-12);           // explicitly squared
			assertFalse(Math.abs(legacy - geh) < 1e-6,
					"documents that the summed quantity is NOT the GEH statistic itself");
		}

		@Test
		@DisplayName("TS: GEH guards against an all-zero observation/modelled pair")
		void gehTsGuardsZeroDenominator() {
			Measurements[] p = pair("m1", 0., 0., 0.);
			assertEquals(0., ObjectiveCalculator.calcGEHObjective(p[0], p[1],
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED OBJ-5: AADT GEH has no zero-denominator guard -> NaN")
		void gehAadtHasNoZeroGuard() {
			Measurements[] p = pair("m1", 0., 0., 0.);
			double v = ObjectiveCalculator.calcGEHObjective(p[0], p[1],
					ObjectiveCalculator.TypeAADT);
			assertTrue(Double.isNaN(v), "expected NaN from 0/0, got " + v);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED OBJ-5: SD-weighted GEH TS has no zero-denominator "
				+ "guard -> NaN (unlike the unweighted GEH)")
		void sdWeightedGehTsHasNoZeroGuard() {
			Measurements[] p = pair("m1", 0., 0., 0.);
			double v = ObjectiveCalculator.calcSDWeightedGEHObjective(p[0], p[1],
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific);
			assertTrue(Double.isNaN(v), "expected NaN from 0/0, got " + v);
		}

		@Test
		void sdWeightedGehAadtHasNoZeroGuard() {
			Measurements[] p = pair("m1", 0., 0., 0.);
			double v = ObjectiveCalculator.calcSDWeightedGEHObjective(p[0], p[1],
					ObjectiveCalculator.TypeAADT);
			assertTrue(Double.isNaN(v), "expected NaN from 0/0, got " + v);
		}
	}

	// ------------------------------------------------------------------
	// SD weighting
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("SD weighting")
	class SdWeighting {

		@Test
		@DisplayName("ORACLE: weight is 1/(1+SD^2), not 1/SD^2")
		void weightIsOneOverOnePlusSdSquared() {
			Measurements[] p = pair("m1", 100., 90., 3.);
			// residual^2 = 100 ; weight = 1/(1+9) = 0.1 -> 10
			assertEquals(10., ObjectiveCalculator.calcSDWeightedObjective(p[0], p[1],
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
		}

		@Test
		void absentSdIsTreatedAsZero() {
			Measurements[] p = pair("m1", 100., 90., 0.);
			// putVolume auto-inserts SD=0, so weight = 1/(1+0) = 1
			assertEquals(100., ObjectiveCalculator.calcSDWeightedObjective(p[0], p[1],
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), EPS);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED OBJ-3: calcSDWeightedObjective MUTATES its input "
				+ "measurement by inserting SD=0 when the SD entry is absent")
		void sdWeightedObjectiveMutatesInput() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			add(real, "m1");
			add(sim, "m1");
			Measurement r = real.getMeasurements().get(Id.create("m1", Measurement.class));
			// Bypass putVolume so that the SD map stays empty for TB1.
			r.getVolumes().put(TB1, 100.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 90.);
			assertNull(r.getSD().get(TB1), "precondition: SD entry absent");

			ObjectiveCalculator.calcSDWeightedObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific);

			assertEquals(0., r.getSD().get(TB1),
					"legacy implementation inserted SD=0 into the input object");
		}

		@Test
		@DisplayName("CHARACTERIZATION: SD-weighted AADT sums SD variances but sums counts "
				+ "across measurements (mixed accumulation scope)")
		void sdWeightedAadtMixedAccumulation() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			for (String id : new String[] { "m1", "m2" }) {
				add(real, id);
				add(sim, id);
			}
			// m1: residual 10, sd 1 -> sigma 1
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 110.);
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putSD(TB1, 1.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 100.);
			// m2: residual 10, sd 2 -> sigma 4
			real.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 210.);
			real.getMeasurements().get(Id.create("m2", Measurement.class)).putSD(TB1, 2.);
			sim.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 200.);

			// m1: 1/(1+1) * 10^2 = 50
			// m2: sigma=4 ; cumulative residual = 20 -> 1/(1+4) * 400 = 80
			assertEquals(130., ObjectiveCalculator.calcSDWeightedObjective(real, sim,
					ObjectiveCalculator.TypeAADT), EPS);
		}

		@Test
		void sdWeightedGehWeightsByOneOverOnePlusSdSquared() {
			Measurements[] p = pair("m1", 100., 80., 2.);
			// 2 * 1/(1+4) * 400/180 = 400/900 = 0.444444...
			double expected = 2. * (1. / 5.) * 400. / 180.;
			assertEquals(expected, ObjectiveCalculator.calcSDWeightedGEHObjective(p[0], p[1],
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific), 1e-12);
		}
	}

	// ------------------------------------------------------------------
	// Multi-objective decomposition
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("multi-objective decomposition")
	class MultiObjective {

		@Test
		void decomposesByMeasurementType() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			add(real, "vol1");
			add(sim, "vol1");
			real.createAnadAddMeasurement("tt1", MeasurementType.linkTravelTime);
			sim.createAnadAddMeasurement("tt1", MeasurementType.linkTravelTime);

			real.getMeasurements().get(Id.create("vol1", Measurement.class)).putVolume(TB1, 100.);
			sim.getMeasurements().get(Id.create("vol1", Measurement.class)).putVolume(TB1, 90.);
			real.getMeasurements().get(Id.create("tt1", Measurement.class)).putVolume(TB1, 60.);
			sim.getMeasurements().get(Id.create("tt1", Measurement.class)).putVolume(TB1, 50.);

			Map<MeasurementType, Double> obj = ObjectiveCalculator.calcMultiObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific);

			assertEquals(100., obj.get(MeasurementType.linkVolume), EPS);
			assertEquals(100., obj.get(MeasurementType.linkTravelTime), EPS);
		}

		@Test
		@DisplayName("CHARACTERIZATION: multi-objective TS dereferences a missing "
				+ "measurement (no 'continue'), unlike calcObjective TS which skips")
		void multiObjectiveTsThrowsOnMissingMeasurement() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			add(real, "m1");
			add(real, "m2");
			add(sim, "m1");
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 100.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 90.);
			real.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 55.);

			assertThrows(NullPointerException.class,
					() -> ObjectiveCalculator.calcMultiObjective(real, sim,
							ObjectiveCalculator.TypeMeasurementAndTimeSpecific));
		}

		@Test
		@DisplayName("CHARACTERIZATION: multi-objective AADT is PER MEASUREMENT, unlike "
				+ "calcObjective AADT which accumulates across measurements")
		void multiObjectiveAadtIsPerMeasurement() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			for (String id : new String[] { "m1", "m2" }) {
				add(real, id);
				add(sim, id);
			}
			real.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 110.);
			sim.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 100.);
			real.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 210.);
			sim.getMeasurements().get(Id.create("m2", Measurement.class)).putVolume(TB1, 200.);

			Map<MeasurementType, Double> obj = ObjectiveCalculator.calcMultiObjective(real, sim,
					ObjectiveCalculator.TypeAADT);

			// 10^2 + 10^2 = 200 (per-measurement), NOT 500 as calcObjective AADT gives.
			assertEquals(200., obj.get(MeasurementType.linkVolume), EPS);
			assertEquals(500., ObjectiveCalculator.calcObjective(real, sim,
					ObjectiveCalculator.TypeAADT), EPS);
		}

		@Test
		void subObjectiveFilteringByMeasurementType() {
			Measurements real = newMeasurements();
			Measurements sim = newMeasurements();
			add(real, "vol1");
			add(sim, "vol1");
			real.createAnadAddMeasurement("tt1", MeasurementType.linkTravelTime);
			sim.createAnadAddMeasurement("tt1", MeasurementType.linkTravelTime);
			real.getMeasurements().get(Id.create("vol1", Measurement.class)).putVolume(TB1, 100.);
			sim.getMeasurements().get(Id.create("vol1", Measurement.class)).putVolume(TB1, 90.);
			real.getMeasurements().get(Id.create("tt1", Measurement.class)).putVolume(TB1, 60.);
			sim.getMeasurements().get(Id.create("tt1", Measurement.class)).putVolume(TB1, 50.);

			Set<MeasurementType> onlyVolume = Collections.singleton(MeasurementType.linkVolume);
			assertEquals(100., ObjectiveCalculator.calcObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific, onlyVolume), EPS);

			Map<String, Double> grouped = ObjectiveCalculator.calcMultiObjective(real, sim,
					ObjectiveCalculator.TypeMeasurementAndTimeSpecific,
					Collections.singletonMap("groupA", onlyVolume));
			assertEquals(100., grouped.get("groupA"), EPS);
		}
	}
}
