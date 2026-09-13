package ust.hk.praisehk.metamodelcalibration.fixtures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

/**
 * FIXTURE E - synthetic calibration problem.
 *
 * <p>Two decision parameters, two measurements, several calibration iterations,
 * with a <b>known</b> analytical response and a <b>known</b> simulation
 * response. Intended for meta-model and trust-region tests.</p>
 *
 * <p>Ground truth used throughout:</p>
 * <pre>
 *   A(theta) = 100 + 3*theta1 - 2*theta2          (analytical model)
 *   S(theta) = A(theta) + bias                     (simulation model)
 *   bias(measurement M1) =  5
 *   bias(measurement M2) = -3
 * </pre>
 *
 * <p>{@link #exact()} sets bias to zero, so the analytical model and the
 * simulation model agree exactly. {@link #biased()} introduces the systematic
 * bias above. Both variants are deterministic: no randomness is used.</p>
 */
public final class FixtureE_SyntheticCalibration {

	private FixtureE_SyntheticCalibration() {
	}

	public static final String THETA_1 = "theta1";
	public static final String THETA_2 = "theta2";

	public static final String M1 = "M1";
	public static final String M2 = "M2";

	/** A(theta) = INTERCEPT + A1*theta1 + A2*theta2 */
	public static final double INTERCEPT = 100.;
	public static final double A1 = 3.;
	public static final double A2 = -2.;

	public static final double BIAS_M1 = 5.;
	public static final double BIAS_M2 = -3.;

	/** Deterministic sample points (theta1, theta2). */
	public static final double[][] SAMPLE_POINTS = {
			{ 1.0, 1.0 },
			{ 1.5, 1.0 },
			{ 1.0, 1.5 },
			{ 2.0, 0.5 },
			{ 0.5, 2.0 },
	};

	public static double analyticalResponse(double theta1, double theta2) {
		return INTERCEPT + A1 * theta1 + A2 * theta2;
	}

	public static double simulationResponse(double theta1, double theta2, double bias) {
		return analyticalResponse(theta1, theta2) + bias;
	}

	public static final class Problem {
		private final Map<Integer, Measurements> simMeasurements = new LinkedHashMap<>();
		private final Map<Integer, Measurements> anaMeasurements = new LinkedHashMap<>();
		private final Map<Integer, LinkedHashMap<String, Double>> params = new LinkedHashMap<>();
		private final Measurements calibrationMeasurements;

		private Problem(boolean biased) {
			for (int i = 0; i < SAMPLE_POINTS.length; i++) {
				double t1 = SAMPLE_POINTS[i][0];
				double t2 = SAMPLE_POINTS[i][1];

				LinkedHashMap<String, Double> p = new LinkedHashMap<>();
				p.put(THETA_1, t1);
				p.put(THETA_2, t2);
				params.put(i, p);

				Measurements sim = newMeasurements();
				Measurements ana = newMeasurements();
				sim.getMeasurements().get(Id.create(M1, Measurement.class))
						.putVolume(TimeBeans.ONE_HOUR, simulationResponse(t1, t2, biased ? BIAS_M1 : 0.));
				sim.getMeasurements().get(Id.create(M2, Measurement.class))
						.putVolume(TimeBeans.ONE_HOUR, simulationResponse(t1, t2, biased ? BIAS_M2 : 0.));
				ana.getMeasurements().get(Id.create(M1, Measurement.class))
						.putVolume(TimeBeans.ONE_HOUR, analyticalResponse(t1, t2));
				ana.getMeasurements().get(Id.create(M2, Measurement.class))
						.putVolume(TimeBeans.ONE_HOUR, analyticalResponse(t1, t2));
				simMeasurements.put(i, sim);
				anaMeasurements.put(i, ana);
			}

			// "Observed" data used as the calibration target: the simulation
			// response at the last sample point.
			this.calibrationMeasurements = simMeasurements.get(SAMPLE_POINTS.length - 1).clone();
		}

		private static Measurements newMeasurements() {
			Measurements m = Measurements.createMeasurements(TimeBeans.singleHour());
			m.createAnadAddMeasurement(M1, MeasurementType.linkVolume);
			m.createAnadAddMeasurement(M2, MeasurementType.linkVolume);
			return m;
		}

		public Map<Integer, Measurements> simMeasurements() {
			return simMeasurements;
		}

		public Map<Integer, Measurements> anaMeasurements() {
			return anaMeasurements;
		}

		public Map<Integer, LinkedHashMap<String, Double>> params() {
			return params;
		}

		public Measurements calibrationMeasurements() {
			return calibrationMeasurements;
		}

		public List<String> parameterNames() {
			return new ArrayList<>(params.get(0).keySet());
		}
	}

	/** Analytical and simulation models agree exactly (bias = 0). */
	public static Problem exact() {
		return new Problem(false);
	}

	/** Simulation model carries a known systematic bias vs the analytical model. */
	public static Problem biased() {
		return new Problem(true);
	}

	/** Minimal empty measurements container for negative tests. */
	public static Measurements emptyMeasurements() {
		return Measurements.createMeasurements(TimeBeans.singleHour());
	}

	/** Holder for a measurement id + declared type, for negative tests. */
	public static Measurement addLinkVolume(Measurements m, String id) {
		return m.createAnadAddMeasurement(id, MeasurementType.linkVolume);
	}

	/** Unused-parameter guard so the class is not mistaken for a data holder. */
	public static Map<String, Object> metadata() {
		Map<String, Object> md = new HashMap<>();
		md.put("intercept", INTERCEPT);
		md.put("a1", A1);
		md.put("a2", A2);
		return md;
	}
}
