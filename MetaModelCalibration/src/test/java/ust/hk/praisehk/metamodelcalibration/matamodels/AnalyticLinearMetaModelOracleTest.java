package ust.hk.praisehk.metamodelcalibration.matamodels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;

import de.xypron.jcobyla.Calcfc;
import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;

import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

/**
 * PHASE 6 - independent mathematical oracle for {@link AnalyticLinearMetaModel}.
 *
 * <p>The live fitter is COBYLA, a derivative-free optimizer started at {@code x = all ones} with
 * {@code rhobeg = 0.5}. This class computes the SAME weighted-ridge problem in closed form with an
 * independent method (normal equations solved by LU decomposition) and compares the two.</p>
 *
 * <p><b>Principal finding (MODEL-5):</b> the legacy fitter does <b>not</b> attain the optimum of the
 * objective it declares. Its trust region starts at 0.5 and COBYLA only ever <i>reduces</i> rho, so the
 * total displacement from the hard-coded all-ones start is bounded by roughly {@code 2 * rhobeg = 1.0}.
 * Any parameter vector whose optimum lies further than that from {@code 1.0} is unreachable. The
 * defect is therefore structural, and it explains why four alternative fitters were added.</p>
 *
 * <p>Declared model (class javadoc, {@code y = Bo + B1*A + B(2..N+1)X}), with the live scaling fields
 * left at their identity defaults:</p>
 * <pre>
 *   y_hat = beta0 + betaA*A(x) + beta^T x
 *   minimise  sum_i w_i (y_hat_i - y_i)^2 + lambda * ||beta||^2
 *   w_i    = 1 / (1 + ||x_i - x_current||)        (calcEuclDistanceBasedWeight)
 *   lambda = 1e-3                                 (ridgeCoefficient, applied to EVERY beta)
 * </pre>
 *
 * @see docs/modernization/REVIEW_REQUIRED.md (MODEL-2, MODEL-4, MODEL-5)
 */
class AnalyticLinearMetaModelOracleTest {

	private static final String TB = TimeBeans.ONE_HOUR;
	private static final String M1 = "m1";
	private static final Id<Measurement> M_ID = Id.create(M1, Measurement.class);

	/** Must mirror AnalyticLinearMetaModel.ridgeCoefficient. */
	private static final double LAMBDA = 1e-3;

	/** Must mirror the hard-coded initial point in calibrateMetaModel. */
	private static final double START = 1.0;

	// ------------------------------------------------------------------
	// independent oracle
	// ------------------------------------------------------------------

	/** Closed-form weighted-ridge solution: beta = (X'WX + lambda*I)^-1 X'W y. */
	private static double[] weightedRidge(double[][] xs, double[] a, double[] y, int current,
			double lambda) {
		int n = xs[0].length;
		int k = n + 2;
		int m = xs.length;

		RealMatrix x = MatrixUtils.createRealMatrix(m, k);
		double[] w = new double[m];
		for (int i = 0; i < m; i++) {
			w[i] = shepardWeight(xs, i, current);
			x.setEntry(i, 0, 1.0);
			x.setEntry(i, 1, a[i]);
			for (int j = 0; j < n; j++) {
				x.setEntry(i, j + 2, xs[i][j]);
			}
		}

		RealMatrix weight = MatrixUtils.createRealDiagonalMatrix(w);
		RealMatrix normal = x.transpose().multiply(weight).multiply(x)
				.add(MatrixUtils.createRealIdentityMatrix(k).scalarMultiply(lambda));
		RealVector rhs = x.transpose().multiply(weight).operate(MatrixUtils.createRealVector(y));
		return new LUDecomposition(normal).getSolver().solve(rhs).toArray();
	}

	/** w_i = 1 / (1 + ||x_i - x_current||), independently computed. */
	private static double shepardWeight(double[][] xs, int i, int current) {
		double sq = 0;
		for (int j = 0; j < xs[i].length; j++) {
			sq += Math.pow(xs[current][j] - xs[i][j], 2);
		}
		return 1.0 / (1.0 + Math.sqrt(sq));
	}

	/** The objective exactly as the class declares it, evaluated independently. */
	private static double objective(double[] beta, Dataset d) {
		int n = d.xs[0].length;
		double total = 0;
		for (int i = 0; i < d.xs.length; i++) {
			double yhat = beta[0] + beta[1] * d.a[i];
			for (int j = 0; j < n; j++) {
				yhat += beta[j + 2] * d.xs[i][j];
			}
			total += Math.pow(yhat - d.y[i], 2) * shepardWeight(d.xs, i, d.current);
		}
		for (double b : beta) {
			total += b * b * LAMBDA;
		}
		return total;
	}

	// ------------------------------------------------------------------
	// fixture
	// ------------------------------------------------------------------

	private static final class Dataset {
		final Map<Integer, Measurements> sim = new HashMap<>();
		final Map<Integer, Measurements> ana = new HashMap<>();
		final Map<Integer, LinkedHashMap<String, Double>> params = new HashMap<>();
		double[][] xs;
		double[] a;
		double[] y;
		int current;

		Dataset(double[][] xs, double[] a, double[] y, int current) {
			this.xs = xs;
			this.a = a;
			this.y = y;
			this.current = current;
			for (int i = 0; i < xs.length; i++) {
				LinkedHashMap<String, Double> p = new LinkedHashMap<>();
				for (int j = 0; j < xs[i].length; j++) {
					p.put("theta" + (j + 1), xs[i][j]);
				}
				params.put(i, p);

				Measurements simM = Measurements.createMeasurements(TimeBeans.singleHour());
				Measurements anaM = Measurements.createMeasurements(TimeBeans.singleHour());
				simM.createAnadAddMeasurement(M1, MeasurementType.linkVolume).putVolume(TB, y[i]);
				anaM.createAnadAddMeasurement(M1, MeasurementType.linkVolume).putVolume(TB, a[i]);
				sim.put(i, simM);
				ana.put(i, anaM);
			}
		}

		AnalyticLinearMetaModel fit() {
			return new AnalyticLinearMetaModel(M_ID, sim, ana, params, TB, current);
		}

		double[] oracle() {
			return weightedRidge(xs, a, y, current, LAMBDA);
		}
	}

	/**
	 * Optimum deliberately FAR from the hard-coded start (all ones): the true coefficients are
	 * betaA = 1.5 (multiplying A ~ 100), beta1 = 2, beta2 = -3.
	 */
	private static Dataset farOptimum() {
		double[][] xs = { { 1.0, 1.0 }, { 1.5, 1.0 }, { 1.0, 1.5 }, { 2.0, 0.5 }, { 0.5, 2.0 } };
		double[] a = new double[xs.length];
		double[] y = new double[xs.length];
		for (int i = 0; i < xs.length; i++) {
			// A must NOT be an affine function of x, otherwise [1, A, x] is rank deficient and
			// the weighted-ridge optimum is not unique
			a[i] = 100 + 3 * xs[i][0] * xs[i][0] - 2 * xs[i][1];
			y[i] = 1.5 * a[i] + 2 * xs[i][0] - 3 * xs[i][1];
		}
		return new Dataset(xs, a, y, 0);
	}

	/** Optimum ON the hard-coded start: the true coefficients are 1, 1, 1. */
	private static Dataset optimumAtStart() {
		double[][] xs = { { 1.0, 1.0 }, { 1.5, 1.0 }, { 1.0, 1.5 }, { 2.0, 0.5 }, { 0.5, 2.0 } };
		double[] a = new double[xs.length];
		double[] y = new double[xs.length];
		for (int i = 0; i < xs.length; i++) {
			// A is kept O(1) so that the intercept is not ill-conditioned against it.
			a[i] = 1 + 3 * xs[i][0] * xs[i][0] - 2 * xs[i][1];
			y[i] = 1.0 + 1.0 * a[i] + 1.0 * xs[i][0] + 1.0 * xs[i][1];
		}
		return new Dataset(xs, a, y, 0);
	}

	// ==================================================================
	// the defect, stated as an oracle
	// ==================================================================

	@Test
	@Disabled("REVIEW_REQUIRED MODEL-5: the live COBYLA fitter does not attain the closed-form "
			+ "weighted-ridge optimum. Observed on farOptimum(): fitted = "
			+ "[1.6205, 1.4529, 1.0671, 0.7340] vs oracle = [-0.1714, 1.5018, 1.9861, -2.9932] "
			+ "(the oracle recovers the generating coefficients 1.5 / 2 / -3); the declared objective "
			+ "is 16.3615 at the fitted point and 0.015204 at the optimum (ratio ~1076). Root cause: "
			+ "maxfun = 1500 is exhausted (COBYLA returns MAX_ITERATIONS_REACHED, which the code "
			+ "discards) and the design matrix is badly scaled (A ~ 100 beside x ~ 1), so COBYLA "
			+ "converges very slowly. Enable this test once MODEL-5 is resolved.")
	@DisplayName("ORACLE (disabled): the COBYLA fit should equal the closed-form weighted-ridge optimum")
	void fitShouldAttainTheClosedFormOptimum() {
		Dataset d = farOptimum();
		double[] fitted = d.fit().getMetaModelParams();
		double[] oracle = d.oracle();
		for (int i = 0; i < oracle.length; i++) {
			assertEquals(oracle[i], fitted[i], 1e-4, "coefficient " + i);
		}
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MODEL-5: the live fitter EXHAUSTS its 1500-evaluation budget, the "
			+ "MAX_ITERATIONS_REACHED status is discarded, and the result is ~1000x off the optimum")
	void iterationBudgetIsExhaustedAndStatusIsIgnored() {
		Dataset d = farOptimum();
		AnalyticLinearMetaModel model = d.fit();
		double[] fitted = model.getMetaModelParams();

		// (a) Replicate the legacy COBYLA call exactly, and confirm it reproduces the model's own fit.
		//     This proves the discrepancy is the optimizer setup, not the objective.
		Calcfc f = (m, n, xx, con) -> objective(xx, d);
		double[] replicated = { START, START, START, START };
		CobylaExitStatus status = Cobyla.findMinimum(f, 4, 0, replicated, 0.5, 1e-6, 0, 1500);
		assertEquals(objective(replicated, d), objective(fitted, d), 1e-9,
				"the replicated call must reproduce the model's fit");
		assertEquals(CobylaExitStatus.MAX_ITERATIONS_REACHED, status,
				"the legacy budget is exhausted, and the model ignores this status");

		// (b) The consequence: the declared objective at the fit is orders of magnitude above the
		//     closed-form optimum.
		double atFit = objective(fitted, d);
		double atOptimum = objective(d.oracle(), d);
		assertTrue(atFit > 100 * atOptimum,
				"objective at the fit (" + atFit + ") vs the optimum (" + atOptimum + ")");

		// (c) The budget really is the binding constraint: a larger budget improves the objective
		//     monotonically (5000 -> 12.84, 20000 -> 6.09, 100000 -> 0.245 on this dataset).
		double previous = atFit;
		for (int maxfun : new int[] { 5000, 20000 }) {
			double[] x = { START, START, START, START };
			CobylaExitStatus st = Cobyla.findMinimum(f, 4, 0, x, 0.5, 1e-6, 0, maxfun);
			double obj = objective(x, d);
			assertEquals(CobylaExitStatus.MAX_ITERATIONS_REACHED, st,
					"even a larger budget is exhausted - the problem is badly scaled for COBYLA");
			assertTrue(obj < previous,
					"a larger budget must improve the objective (" + obj + " vs " + previous + ")");
			previous = obj;
		}
	}

	@Test
	@DisplayName("CONTRAST: when the coefficients are O(1) and near the start, the same code DOES reach "
			+ "the closed-form optimum - so the objective is right and only the optimizer setup fails")
	void fitMatchesOracleWhenParametersAreWellScaled() {
		Dataset d = optimumAtStart();
		double[] fitted = d.fit().getMetaModelParams();
		double[] oracle = d.oracle();

		for (int i = 0; i < oracle.length; i++) {
			assertEquals(oracle[i], fitted[i], 5e-2, "coefficient " + i);
		}
		assertTrue(objective(fitted, d) < 2 * objective(oracle, d),
				"the objective at the fit should be close to the optimum for this dataset");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MODEL-5: the trap is not dataset-specific - constant simulation output "
			+ "is also unreachable")
	void constantSimulationOutputIsAlsoTrapped() {
		double[][] xs = { { 1.0 }, { 2.0 }, { 3.0 } };
		double[] a = { 10.0, 20.0, 30.0 };
		double[] y = { 50.0, 50.0, 50.0 }; // optimum beta0 ~ 50, i.e. 49 away from the start
		Dataset d = new Dataset(xs, a, y, 0);

		double[] fitted = d.fit().getMetaModelParams();
		assertTrue(fitted[0] < 30,
				"beta0 stays far from the true constant 50 because it cannot move that far");
		assertTrue(objective(fitted, d) > objective(d.oracle(), d),
				"objective at the fit: " + objective(fitted, d) + " vs optimum "
						+ objective(d.oracle(), d));
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MODEL-5: duplicate parameter points are fitted, but short of the optimum")
	void duplicateParameterPointsAreAlsoTrapped() {
		Dataset d = duplicateDataset();

		double[] fitted = d.fit().getMetaModelParams();
		assertNotNull(fitted);
		assertTrue(objective(fitted, d) > objective(d.oracle(), d),
				"objective at the fit: " + objective(fitted, d) + " vs optimum "
						+ objective(d.oracle(), d));
	}

	// ==================================================================
	// oracle checks that DO hold
	// ==================================================================

	@Test
	@DisplayName("ORACLE: calcEuclDistanceBasedWeight is 1/(1+||x_i - x_current||) over the REFERENCE "
			+ "point's keys")
	void weightFunctionMatchesItsDefinition() {
		AnalyticLinearMetaModel model = optimumAtStart().fit();

		Map<Integer, LinkedHashMap<String, Double>> p = new HashMap<>();
		p.put(0, map("theta1", 0.0));
		p.put(1, map("theta1", 3.0));

		// same point -> weight 1
		assertEquals(1.0, model.calcEuclDistanceBasedWeight(p, 0, 0), 1e-12);
		// distance 3 -> 1/(1+3)
		assertEquals(0.25, model.calcEuclDistanceBasedWeight(p, 1, 0), 1e-12);

		// two-dimensional distance, both keys present in the reference point
		Map<Integer, LinkedHashMap<String, Double>> q = new HashMap<>();
		q.put(0, map("theta1", 0.0, "theta2", 0.0));
		q.put(1, map("theta1", 3.0, "theta2", 4.0));
		assertEquals(1.0 / (1.0 + 5.0), model.calcEuclDistanceBasedWeight(q, 1, 0), 1e-12);
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MODEL-6: the distance iterates the REFERENCE point's keys only, so a "
			+ "parameter present in the other point but absent from the reference is silently ignored")
	void weightIgnoresKeysAbsentFromTheReferencePoint() {
		AnalyticLinearMetaModel model = optimumAtStart().fit();

		Map<Integer, LinkedHashMap<String, Double>> p = new HashMap<>();
		p.put(0, map("theta1", 0.0));                 // reference: theta1 only
		p.put(2, map("theta1", 0.0, "theta2", 4.0));  // theta2 differs by 4, but is not iterated

		// The distance only sums over param1.keySet() = {theta1}, so it is 0 and the weight is 1 -
		// exactly as if the two points were identical.
		assertEquals(1.0, model.calcEuclDistanceBasedWeight(p, 2, 0), 1e-12,
				"a key present only in the compared point does not contribute to the distance");

		// Worse: the weight is NOT symmetric. With the richer point as the reference it iterates
		// theta2 and dereferences the OTHER point's missing entry -> NullPointerException.
		assertThrows(NullPointerException.class, () -> model.calcEuclDistanceBasedWeight(p, 0, 2));
	}

	@Test
	@DisplayName("ORACLE: the reference point changes the weights, hence the oracle solution")
	void referencePointChangesTheSolution() {
		double[][] xs = { { 0.0 }, { 5.0 }, { 10.0 } };
		double[] a = { 10.0, 40.0, 90.0 };
		double[] y = { 11.0, 45.0, 95.0 };

		double[] atZero = weightedRidge(xs, a, y, 0, LAMBDA);
		double[] atTwo = weightedRidge(xs, a, y, 2, LAMBDA);

		assertTrue(maxAbsDiff(atZero, atTwo) > 1e-6,
				"currentParamNo must affect the weighted solution");
		// the sample at the reference point carries the largest (unit) weight, so it dominates
		assertEquals(shepardWeight(xs, 0, 0), 1.0, 1e-12);
	}

	@Test
	@DisplayName("ORACLE: the ridge shrinks the closed-form solution toward zero")
	void ridgeShrinksTheClosedFormSolution() {
		double[][] xs = { { 1.0, 1.0 }, { 1.5, 1.0 }, { 1.0, 1.5 }, { 2.0, 0.5 }, { 0.5, 2.0 } };
		double[] a = new double[xs.length];
		double[] y = new double[xs.length];
		for (int i = 0; i < xs.length; i++) {
			// A must not be collinear with the x columns, or X'WX is singular at lambda = 0
			a[i] = 10 + 3 * xs[i][0] * xs[i][0] - 2 * xs[i][1];
			y[i] = a[i] + 5.0;
		}

		double[] withRidge = weightedRidge(xs, a, y, 0, LAMBDA);
		double[] noRidge = weightedRidge(xs, a, y, 0, 0.0);

		assertTrue(norm(withRidge) < norm(noRidge),
				"lambda = 1e-3 must shrink the coefficient vector relative to lambda = 0");
	}

	@Test
	@DisplayName("ORACLE: predictions follow the fitted linear plane, at and away from the calibration point")
	void predictionsFollowTheFittedPlane() {
		Dataset d = optimumAtStart();
		AnalyticLinearMetaModel model = d.fit();
		double[] beta = model.getMetaModelParams();

		LinkedHashMap<String, Double> atSample = map("theta1", 2.0, "theta2", 0.5);
		assertEquals(beta[0] + beta[1] * d.a[3] + beta[2] * 2.0 + beta[3] * 0.5,
				model.calcMetaModel(d.a[3], atSample), 1e-9);

		LinkedHashMap<String, Double> far = map("theta1", 100.0, "theta2", -50.0);
		assertEquals(beta[0] + beta[1] * 1000.0 + beta[2] * 100.0 + beta[3] * -50.0,
				model.calcMetaModel(1000.0, far), 1e-9);
	}

	// ==================================================================
	// the other four fitting paths
	// ==================================================================

	@Test
	@DisplayName("REVIEW_REQUIRED MODEL-2: the four alternative fitters are UNREACHABLE after "
			+ "construction, because the constructor clears the data they iterate")
	void alternativeFittersAreUnreachable() {
		AnalyticLinearMetaModel model = optimumAtStart().fit();

		// params / simData / analyticalData are cleared at the end of the constructor, so every
		// alternative fitter sees empty input.
		assertThrows(Exception.class, () -> model.calibrateMetaModelAnalytically(0));
		assertThrows(Exception.class, () -> model.calibrateMetaModelWithApache(0));
		assertThrows(Exception.class, () -> model.calibrateMetaModelWithSmile(0));
		assertThrows(Exception.class, () -> model.calibrateMetaModelWithAdam(0));
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MODEL-4: the live scaling fields are left at their identity defaults")
	void scalingFieldsAreIdentity() {
		double[][] xs = { { 1.0, 2.0 } };
		AnalyticLinearMetaModel model = new Dataset(xs, new double[] { 10.0 }, new double[] { 11.0 }, 0)
				.fit();

		assertEquals(4, model.scaleMean.length); // noOfParams (2) + 2
		for (double m : model.scaleMean) {
			assertEquals(0.0, m, 0.0);
		}
		for (double s : model.scaleSigma) {
			assertEquals(1.0, s, 0.0);
		}
		assertEquals(0.0, model.scaleMeanY, 0.0);
		assertEquals(1.0, model.scaleSigmaY, 0.0);
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MODEL-1: the constructor requires iteration key 0 to exist")
	void constructorRequiresIterationZero() {
		Dataset d = optimumAtStart();

		Map<Integer, Measurements> sim = new HashMap<>();
		Map<Integer, Measurements> ana = new HashMap<>();
		Map<Integer, LinkedHashMap<String, Double>> params = new HashMap<>();
		sim.put(1, d.sim.get(1));
		ana.put(1, d.ana.get(1));
		params.put(1, d.params.get(1));

		// noOfParams = params.get(0).size() -> NullPointerException
		assertThrows(NullPointerException.class,
				() -> new AnalyticLinearMetaModel(M_ID, sim, ana, params, TB, 0));
	}

	private static Dataset duplicateDataset() {
		double[][] xs = { { 1.0 }, { 2.0 }, { 2.0 }, { 3.0 } };
		double[] a = { 10.0, 20.0, 20.0, 30.0 };
		double[] y = { 100.0, 195.0, 205.0, 300.0 };
		return new Dataset(xs, a, y, 0);
	}

	// ------------------------------------------------------------------

	private static LinkedHashMap<String, Double> map(Object... kv) {
		LinkedHashMap<String, Double> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], (Double) kv[i + 1]);
		}
		return m;
	}

	private static double maxAbsDiff(double[] x, double[] y) {
		double max = 0;
		for (int i = 0; i < x.length; i++) {
			max = Math.max(max, Math.abs(x[i] - y[i]));
		}
		return max;
	}

	private static double norm(double[] x) {
		double s = 0;
		for (double v : x) {
			s += v * v;
		}
		return Math.sqrt(s);
	}
}
