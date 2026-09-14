package ust.hk.praisehk.metamodelcalibration.calibrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.api.core.v01.Scenario;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.transit.fare.FareCalculator;

/**
 * PHASE 7 - deterministic state machine for {@link CalibratorImpl}'s trust-region loop.
 *
 * <p>Driven through a stub {@link AnalyticalModel}, so the loop can be exercised without a network, a
 * Hong Kong dataset or MATSim runtime.</p>
 *
 * <p><b>Scope, stated precisely (review point HIGH 2).</b> The policy has three outcomes, but the tests
 * below distinguish only <b>accept</b> from <b>reject</b>. The two accepted sub-branches - grow on
 * {@code rho >= 0.01}, and hold when {@code rho < 0.01} - are <b>not</b> pinned, because {@code rho}
 * depends on the fitted meta-model prediction and cannot be set from outside without first extracting
 * the policy, which would be a refactor. What is pinned is the rejection arithmetic ({@code * 0.9}, the
 * floor) and that an accepted step is never shrunk <i>while</i> {@code TrRadius <= maxTrRadius}:</p>
 * <pre>
 *   SimObjectiveChange &gt; 0 and rho &gt;= 0.01  -&gt; accept, TrRadius *= 1.25 (capped)
 *   SimObjectiveChange &gt; 0 and rho &lt;  0.01  -&gt; accept, TrRadius unchanged
 *   otherwise                                -&gt; reject, TrRadius *= 0.9  (floored), rejection count += 1
 * </pre>
 *
 * <p>All legacy side effects (COBYLA's {@code iprint=3} output, the per-variable {@code println}, the
 * {@code config} writers) are captured rather than printed, and the capture is itself asserted.</p>
 *
 * @see docs/modernization/REVIEW_REQUIRED.md (CAL-1 … CAL-8)
 */
class CalibratorImplStateMachineTest {

	private static final String TB = TimeBeans.ONE_HOUR;
	private static final String M1 = "m1";
	private static final Id<Measurement> M_ID = Id.create(M1, Measurement.class);

	// ------------------------------------------------------------------
	// fixture
	// ------------------------------------------------------------------

	/** A stub analytical model: returns a fixed volume, counts its calls, never touches a network. */
	private static final class StubSUE implements AnalyticalModel {
		final Measurements template;
		double anaVolume = 0;
		int perFormSUECalls = 0;
		int internalCalibrationCalls = 0;
		/** When >= 0, calibrateInternalParams returns the SAME iteration keys with this volume. */
		double internalCalibrationVolume = -1;
		String fileLoc = "";

		StubSUE(Measurements template) {
			this.template = template;
		}

		private Measurements out() {
			Measurements m = template.clone();
			m.getMeasurements().get(M_ID).putVolume(TB, anaVolume);
			perFormSUECalls++;
			return m;
		}

		@Override
		public Measurements perFormSUE(LinkedHashMap<String, Double> params, Measurements original) {
			return out();
		}

		@Override
		public Measurements perFormSUE(LinkedHashMap<String, Double> params,
				LinkedHashMap<String, Double> anaParams, Measurements original) {
			return out();
		}

		@Override
		public Map<Integer, Measurements> calibrateInternalParams(Map<Integer, Measurements> simMeasurements,
				Map<Integer, LinkedHashMap<String, Double>> params,
				LinkedHashMap<String, Double> initialParam, int currentParamNo) {
			internalCalibrationCalls++;
			if (internalCalibrationVolume < 0) {
				return simMeasurements;
			}
			// Same iteration keys, so same SIZE as the calibrator's anaMeasurements (the normal
			// shape for an internal recalibration), but obviously different volumes.
			Map<Integer, Measurements> recalibrated = new HashMap<>();
			for (Integer key : simMeasurements.keySet()) {
				recalibrated.put(key, newMeasurements(internalCalibrationVolume));
			}
			return recalibrated;
		}

		@Override
		public void clearLinkCarandTransitVolume() {
		}

		@Override
		public LinkedHashMap<String, Double> getAnalyticalModelInternalParams() {
			LinkedHashMap<String, Double> p = new LinkedHashMap<>();
			p.put("BPRalpha", 0.15);
			p.put("BPRbeta", 4.0);
			return p;
		}

		@Override
		public Map<String, Tuple<Double, Double>> getTimeBeans() {
			return TimeBeans.singleHour();
		}

		@Override
		public LinkedHashMap<String, Tuple<Double, Double>> getAnalyticalModelParamsLimit() {
			return new LinkedHashMap<>();
		}

		@Override
		public String getFileLoc() {
			return fileLoc;
		}

		@Override
		public void setFileLoc(String loc) {
			fileLoc = loc;
		}

		@Override
		public Map<String, AnalyticalModelNetwork> getNetworks() {
			return new HashMap<>();
		}

		@Override
		public void generateRoutesAndOD(Population population, Network network,
				TransitSchedule schedule, Scenario scenario, Map<String, FareCalculator> fareCalc) {
		}

		@Override
		public Population getLastPopulation() {
			return null;
		}

		@Override
		public void setDefaultParameters(LinkedHashMap<String, Double> defaultParam) {
		}
	}

	private static Measurements newMeasurements(double m1Volume) {
		Measurements m = Measurements.createMeasurements(TimeBeans.singleHour());
		m.createAnadAddMeasurement(M1, MeasurementType.linkVolume).putVolume(TB, m1Volume);
		return m;
	}

	/** A two-parameter reader. The calibrator keys its parameter maps by the CSV Code column. */
	private static ParamReader paramReader(Path dir) throws IOException {
		Path p = dir.resolve("params.csv");
		Files.write(p, Arrays.asList(
				"SubPopulation,Parameter Name,id,Lower Limit,UpperLimit,CurretValue,Code,IncludeIninitialParam",
				",MuCar,id1,-300,-100,-200,1,TRUE",
				",MuDist,id2,-0.02,-0.001,-0.0075,2,TRUE"));
		return new ParamReader(p.toString());
	}

	private static CalibratorImpl calibrator(Path dir, boolean internalCalibration, double initialTr,
			int maxRejections) throws IOException {
		return new CalibratorImpl(newMeasurements(100.), dir.toString() + "/", internalCalibration,
				paramReader(dir), initialTr, maxRejections);
	}

	/** Runs generateNewParam with the legacy stdout captured, so the suite output stays readable. */
	private static String runQuietly(CalibratorImpl c, StubSUE sue, Measurements sim) {
		PrintStream original = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(captured));
			c.generateNewParam(sue, sim, null, MetaModel.AnalyticalLinearMetaModelName);
		} finally {
			System.setOut(original);
		}
		return captured.toString();
	}

	// ==================================================================
	// constants and construction (no state machine needed)
	// ==================================================================

	@Nested
	@DisplayName("construction and tunables")
	class Construction {

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-1: maxTrRadius is computed from the FIELD default (25) before the "
				+ "constructor assigns the configured initial radius, so it can end up BELOW it")
		void maxTrRadiusIgnoresTheConfiguredInitialRadius(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 100.0, 4);

			assertEquals(100.0, c.getTrRadius(), 1e-12);
			// 2.5 * the field initialiser 25, NOT 2.5 * 100
			assertEquals(62.5, c.getMaxTrRadius(), 1e-12);
			assertTrue(c.getMaxTrRadius() < c.getTrRadius(),
					"the configured initial radius exceeds the maximum, so the trust region can only shrink");
		}

		@Test
		@DisplayName("CHARACTERIZATION: the trust-region tunables are pinned")
		void tunablesArePinned(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 10.0, 3);

			assertEquals(ObjectiveCalculator.TypeMeasurementAndTimeSpecific, c.getObjectiveType());
			assertEquals(0.001, c.getMinTrRadius(), 1e-12);
			assertEquals(0.001, c.getMinMetaParamChange(), 1e-12);
			assertEquals(0.01, c.getThresholdErrorRatio(), 1e-12);
			assertEquals(1.25, c.getTrusRegionIncreamentRatio(), 1e-12);
			assertEquals(0.9, c.getTrustRegionDecreamentRatio(), 1e-12);
			assertEquals(3.0, c.getMaxSuccesiveRejection(), 0.0);
			assertEquals(MetaModel.AnalyticalLinearMetaModelName, c.metaModelType);
			assertEquals(AnalyticalModelOptimizer.TROptimizerName, c.getOptimzerName());
		}
	}

	// ==================================================================
	// updateAnalyticalMeasurement (CAL-5)
	// ==================================================================

	@Nested
	@DisplayName("updateAnalyticalMeasurement")
	class UpdateAnalyticalMeasurement {

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-5: a fresh calibrator's update is a NO-OP, because the loop "
				+ "iterates the EXISTING (empty) key set")
		void freshCalibratorUpdateIsANoOp(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);

			Map<Integer, Measurements> incoming = new HashMap<>();
			incoming.put(0, newMeasurements(1.));
			incoming.put(1, newMeasurements(2.));

			c.updateAnalyticalMeasurement(incoming);

			// sizes differ (0 vs 2) so the guard passes, but the loop runs over this.anaMeasurements
			// which is empty - so nothing is ever added
			assertTrue(c.anaMeasurements.isEmpty(),
					"new iterations are never added: the loop iterates the existing keys");
		}

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-5: with EQUAL sizes the update is skipped entirely, even when the "
				+ "contents differ")
		void equalSizesSkipTheUpdate(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);
			c.anaMeasurements.put(0, newMeasurements(1.));
			c.anaMeasurements.put(1, newMeasurements(2.));

			Map<Integer, Measurements> incoming = new HashMap<>();
			incoming.put(0, newMeasurements(99.));
			incoming.put(2, newMeasurements(98.));

			c.updateAnalyticalMeasurement(incoming);

			assertEquals(1., c.anaMeasurements.get(0).getMeasurements().get(M_ID).getVolume(TB), 0.,
					"equal sizes short-circuit the whole method, so even key 0 is left stale");
			assertEquals(2, c.anaMeasurements.size(), "and key 2 is not added");
		}

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-5: with DIFFERENT sizes only the EXISTING keys are refreshed; new "
				+ "keys are still not added")
		void differentSizesRefreshExistingKeysOnly(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);
			c.anaMeasurements.put(0, newMeasurements(1.));
			c.anaMeasurements.put(1, newMeasurements(2.));

			Map<Integer, Measurements> incoming = new HashMap<>();
			incoming.put(0, newMeasurements(99.));
			incoming.put(1, newMeasurements(98.));
			incoming.put(2, newMeasurements(97.));

			c.updateAnalyticalMeasurement(incoming);

			assertEquals(99., c.anaMeasurements.get(0).getMeasurements().get(M_ID).getVolume(TB), 0.);
			assertEquals(98., c.anaMeasurements.get(1).getMeasurements().get(M_ID).getVolume(TB), 0.);
			assertEquals(2, c.anaMeasurements.size(), "the extra iteration 2 is not added");
		}

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-5: an existing iteration missing from the new map throws")
		void missingExistingIterationThrows(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);
			c.anaMeasurements.put(0, newMeasurements(1.));
			c.anaMeasurements.put(1, newMeasurements(2.));

			Map<Integer, Measurements> incoming = new HashMap<>();
			incoming.put(0, newMeasurements(99.)); // size 1 != 2

			assertThrows(IllegalArgumentException.class, () -> c.updateAnalyticalMeasurement(incoming));
		}
	}

	// ==================================================================
	// drawRandomPoint (CAL-6)
	// ==================================================================

	@Nested
	@DisplayName("drawRandomPoint")
	class DrawRandomPoint {

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-6: the point respects the bounds, is keyed by CODE, and uses "
				+ "Math.random() so it is not reproducible")
		void boundsRespectedButNondeterministic(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);
			LinkedHashMap<String, Tuple<Double, Double>> limits = new LinkedHashMap<>();
			limits.put("1", new Tuple<>(-300.0, -100.0));
			limits.put("2", new Tuple<>(-0.02, -0.001));

			LinkedHashMap<String, Double> p = c.drawRandomPoint(limits);

			// keyed by the limit keys (the CSV Code column), not by scaled parameter names
			assertEquals(Arrays.asList("1", "2"), new ArrayList<>(p.keySet()));
			assertTrue(p.get("1") >= -300.0 && p.get("1") <= -100.0);
			assertTrue(p.get("2") >= -0.02 && p.get("2") <= -0.001);

			// NOTE (MEDIUM 3): this test deliberately does NOT assert that two runtime draws differ.
			// That would make the "deterministic" suite depend on Math.random() and could fail by
			// chance. The non-injectable RNG is established from the source (see CAL-6) and by the
			// absence of any seed parameter, not by comparing random outcomes.
			for (int i = 0; i < 20; i++) {
				LinkedHashMap<String, Double> q = c.drawRandomPoint(limits);
				assertTrue(q.get("1") >= -300.0 && q.get("1") <= -100.0);
				assertTrue(q.get("2") >= -0.02 && q.get("2") <= -0.001);
			}
		}
	}

	// ==================================================================
	// calcAverageMetaParamsChange (CAL-8)
	// ==================================================================

	@Nested
	@DisplayName("calcAverageMetaParamsChange")
	class AverageMetaParamsChange {

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-8: with no meta-models the mean divides 0 by 0 -> NaN, and NaN "
				+ "silently disables the random-restart test downstream")
		void noMetaModelsYieldsNaN(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);

			double change = c.calcAverageMetaParamsChange();

			assertTrue(Double.isNaN(change), "0/0 with k = 0");
			// the caller tests `change < minMetaParamChange`, which is FALSE for NaN - so the
			// random restart never fires even though the meta-models are empty
			assertFalse(change < c.getMinMetaParamChange(),
					"NaN < x is false, so the random-restart branch can never fire");
		}

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-8: meta-models present but no previous fit -> NullPointerException")
		void missingOldMetaModelThrows(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);
			Map<String, MetaModel> perTimeBean = new HashMap<>();
			perTimeBean.put(TB, stubMetaModel("AnalyticalLinear"));
			c.metaModels.put(M_ID, perTimeBean);

			// oldMetaModel is private and empty, and the method dereferences it without a guard
			assertThrows(NullPointerException.class, () -> c.calcAverageMetaParamsChange());
		}

		private MetaModel stubMetaModel(String name) {
			return new MetaModel() {
				@Override
				public double calcMetaModel(double analyticalModelPart, LinkedHashMap<String, Double> param) {
					return analyticalModelPart;
				}

				@Override
				public String getTimeBeanId() {
					return TB;
				}

				@Override
				public Id<Measurement> getMeasurementId() {
					return M_ID;
				}

				@Override
				public double[] getMetaModelParams() {
					return new double[] { 1., 1. };
				}

				@Override
				public String getMetaModelName() {
					return name;
				}

				@Override
				public double[] getGradientVector() {
					return new double[] { 1., 1. };
				}

				@Override
				public Double getanaGradMultiplier() {
					return 1.0;
				}
			};
		}
	}

	// ==================================================================
	// the accept / reject state machine
	// ==================================================================

	@Nested
	@DisplayName("trust-region state machine")
	class StateMachine {

		@Test
		@DisplayName("CHARACTERIZATION: iteration 0 performs no acceptance test and leaves the radius alone")
		void iterationZeroHasNoAcceptanceTest(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);
			StubSUE sue = new StubSUE(newMeasurements(100.));
			sue.anaVolume = 100.;

			String printed = runQuietly(c, sue, newMeasurements(200.));

			// it ran the SUE, recorded the iteration, and reported the 0th objective on stdout
			assertTrue(sue.perFormSUECalls >= 1);
			assertEquals(1, c.anaMeasurements.size());
			assertEquals(1, c.params.size());
			assertTrue(printed.contains("0th Objective Value"), "the legacy stdout report is expected");
			// no acceptance test at iteration 0, so the radius and rejection count are untouched
			assertEquals(25.0, c.getTrRadius(), 1e-12);
			assertEquals(0.0, c.getSuccessiveRejection(), 0.0);
		}

		@Test
		@DisplayName("CHARACTERIZATION: an IMPROVED simulation objective is accepted - the rejection count "
				+ "stays 0 and the radius is not shrunk")
		void improvedObjectiveIsAccepted(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);
			StubSUE sue = new StubSUE(newMeasurements(100.));
			sue.anaVolume = 100.;

			runQuietly(c, sue, newMeasurements(200.));   // iteration 0: objective 10000
			runQuietly(c, sue, newMeasurements(110.));   // iteration 1: objective 100 -> improvement

			assertEquals(0.0, c.getSuccessiveRejection(), 0.0);
			assertTrue(c.getTrRadius() >= 25.0,
					"an accepted step must not shrink the radius (it grows or stays)");
			assertEquals(1, c.getCurrentParamNo());
		}

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-2: a WORSENED simulation objective is rejected - the radius is "
				+ "multiplied by 0.9 and the rejection count increments")
		void worsenedObjectiveIsRejected(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);
			StubSUE sue = new StubSUE(newMeasurements(100.));
			sue.anaVolume = 100.;

			runQuietly(c, sue, newMeasurements(105.));   // iteration 0: objective 25
			runQuietly(c, sue, newMeasurements(200.));   // iteration 1: objective 10000 -> worse

			assertEquals(1.0, c.getSuccessiveRejection(), 0.0);
			assertEquals(22.5, c.getTrRadius(), 1e-9, "25 * 0.9");
			assertEquals(0, c.getCurrentParamNo(), "the rejected point is not adopted");
		}

		@Test
		@DisplayName("CHARACTERIZATION: the radius is floored at minTrRadius, not shrunk below it")
		void radiusIsFlooredAtMinTrRadius(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, false, 25.0, 4);
			c.setMinTrRadius(24.0); // one 0.9 shrink would give 22.5, below this floor
			StubSUE sue = new StubSUE(newMeasurements(100.));
			sue.anaVolume = 100.;

			runQuietly(c, sue, newMeasurements(105.));
			runQuietly(c, sue, newMeasurements(200.));

			assertEquals(24.0, c.getTrRadius(), 1e-9, "max(24, 22.5)");
			assertEquals(1.0, c.getSuccessiveRejection(), 0.0);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-11: the rejection threshold fires the internal recalibration, but "
				+ "the RECALIBRATED MEASUREMENTS ARE DISCARDED because updateAnalyticalMeasurement "
				+ "short-circuits on equal sizes - only the counter is reset")
		void internalCalibrationResultIsDiscarded(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, true, 25.0, 2); // trigger at two consecutive rejections
			StubSUE sue = new StubSUE(newMeasurements(100.));
			sue.anaVolume = 100.;
			sue.internalCalibrationVolume = 777.; // what the recalibration "returns"

			runQuietly(c, sue, newMeasurements(105.));   // iteration 0: objective 25
			runQuietly(c, sue, newMeasurements(200.));   // iteration 1: rejected
			assertEquals(1.0, c.getSuccessiveRejection(), 0.0);
			assertEquals(0, sue.internalCalibrationCalls, "not yet: threshold is 2");

			runQuietly(c, sue, newMeasurements(210.));   // iteration 2: rejected -> threshold reached

			// The callback DID fire...
			assertEquals(1, sue.internalCalibrationCalls);
			// ...and returned measurements for the SAME iteration keys with volume 777.
			// updateAnalyticalMeasurement sees equal sizes and skips the whole update, so the
			// calibrator's analytical state is left at the OLD volume.
			// iterations 0, 1 and 2 are all present (2 was recorded before the acceptance test)
			assertEquals(3, c.anaMeasurements.size());
			for (int i = 0; i < 3; i++) {
				assertEquals(100., c.anaMeasurements.get(i).getMeasurements().get(M_ID).getVolume(TB), 0.,
						"iteration " + i + ": the recalibrated volume 777 was discarded, state is stale");
			}
			// ...while the rejection counter is reset regardless, so the calibration LOOKS recovered.
			assertEquals(0.0, c.getSuccessiveRejection(), 0.0);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED CAL-11: after the trigger the counter really does restart, so the "
				+ "trigger is not immediately re-entered before the threshold is reached again")
		void counterRestartsAfterTheTrigger(@TempDir Path dir) throws IOException {
			CalibratorImpl c = calibrator(dir, true, 25.0, 2);
			StubSUE sue = new StubSUE(newMeasurements(100.));
			sue.anaVolume = 100.;

			runQuietly(c, sue, newMeasurements(105.));   // 0
			runQuietly(c, sue, newMeasurements(200.));   // 1: rejected (1)
			runQuietly(c, sue, newMeasurements(210.));   // 2: rejected (2) -> trigger, reset to 0
			assertEquals(1, sue.internalCalibrationCalls);
			assertEquals(0.0, c.getSuccessiveRejection(), 0.0);
			assertEquals(20.25, c.getTrRadius(), 1e-9, "the radius is NOT reset by the trigger");

			runQuietly(c, sue, newMeasurements(220.));   // 3: rejected (1) -> below the threshold again

			assertEquals(1, sue.internalCalibrationCalls,
					"the trigger is not re-entered: the counter had restarted from 0");
			assertEquals(1.0, c.getSuccessiveRejection(), 0.0);
			assertEquals(18.225, c.getTrRadius(), 1e-9, "22.5 * 0.9 * 0.9 * 0.9, continuing to shrink");
		}

	}
}
