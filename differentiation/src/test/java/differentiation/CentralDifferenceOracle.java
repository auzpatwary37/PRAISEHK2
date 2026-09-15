package differentiation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Reusable central-difference derivative checker for scalar responses.
 *
 * <p>For a scalar response {@code f} and a single variable coordinate {@code x}, the central
 * difference estimate of {@code df/dx} is
 *
 * <pre>
 *   g_FD = [ f(x + h) - f(x - h) ] / (2h)
 * </pre>
 *
 * <p>A single step size is never trusted. The sweep uses several magnitudes relative to the
 * parameter scale (default {@code 1e-4}, {@code 1e-5}, {@code 1e-6}), because the total error of a
 * central difference is the sum of two opposing terms: the truncation error, which grows with
 * {@code h}, and the floating-point cancellation error, which shrinks as {@code h} shrinks. A
 * well-behaved derivative therefore shows a minimum error somewhere in the middle of the sweep,
 * whereas a derivative computed from the wrong expression shows an error floor that does not
 * shrink as {@code h} falls.
 *
 * <p>Each estimate records the parameter label, the response label, the perturbation size, the
 * finite-difference value, and the absolute and relative error against the analytical value, so a
 * disagreement can be attributed to a genuine derivative error rather than to a precision limit.
 *
 * <p>This class performs no assertions: it reports, and the caller decides. That keeps the
 * "true derivative error" versus "numerical precision" distinction visible in the test that
 * consumes it rather than hidden inside the oracle.
 */
public final class CentralDifferenceOracle {

	/** Perturbation magnitudes, interpreted relative to {@code max(|x|, 1)}. */
	public static final double[] RELATIVE_STEPS = {1e-4, 1e-5, 1e-6};

	private CentralDifferenceOracle() {
	}

	/** Central difference of {@code f} at {@code x} with absolute step {@code h}. */
	public static double centralDifference(DoubleUnaryOperator f, double x, double h) {
		if (h <= 0.0) {
			throw new IllegalArgumentException("step must be positive, was " + h);
		}
		return (f.applyAsDouble(x + h) - f.applyAsDouble(x - h)) / (2.0 * h);
	}

	/** The sweep's absolute steps for a coordinate of magnitude {@code x}. */
	public static double[] stepsFor(double x) {
		double scale = Math.max(Math.abs(x), 1.0);
		double[] steps = new double[RELATIVE_STEPS.length];
		for (int i = 0; i < steps.length; i++) {
			steps[i] = RELATIVE_STEPS[i] * scale;
		}
		return steps;
	}

	/** One finite-difference estimate and its error against the analytical derivative. */
	public static final class Estimate {
		public final double step;
		public final double finiteDifference;
		public final double analytic;
		public final double absoluteError;
		public final double relativeError;

		Estimate(double step, double finiteDifference, double analytic) {
			this.step = step;
			this.finiteDifference = finiteDifference;
			this.analytic = analytic;
			this.absoluteError = Math.abs(finiteDifference - analytic);
			this.relativeError = analytic == 0.0 ? absoluteError : absoluteError / Math.abs(analytic);
		}

		@Override
		public String toString() {
			return String.format(
					"h=%-12.3e FD=%-20.12g analytic=%-20.12g absErr=%-12.4e relErr=%-12.4e",
					step, finiteDifference, analytic, absoluteError, relativeError);
		}
	}

	/** Single estimate at an explicit step. */
	public static Estimate estimate(DoubleUnaryOperator f, double x, double h, double analyticDerivative) {
		return new Estimate(h, centralDifference(f, x, h), analyticDerivative);
	}

	/** Estimates across {@link #RELATIVE_STEPS}, in descending step order. */
	public static List<Estimate> sweep(DoubleUnaryOperator f, double x, double analyticDerivative) {
		List<Estimate> estimates = new ArrayList<>();
		for (double h : stepsFor(x)) {
			estimates.add(estimate(f, x, h, analyticDerivative));
		}
		return estimates;
	}

	/**
	 * The smallest absolute error across the sweep. This is the number to compare against a
	 * tolerance: it is the best the finite difference can do at this point, so a large minimum
	 * indicates a real disagreement rather than an unlucky step size.
	 */
	public static double minimumAbsoluteError(DoubleUnaryOperator f, double x, double analyticDerivative) {
		double best = Double.POSITIVE_INFINITY;
		for (Estimate e : sweep(f, x, analyticDerivative)) {
			best = Math.min(best, e.absoluteError);
		}
		return best;
	}

	/**
	 * A multi-line, human-readable report of a sweep, tagged with the parameter and response it
	 * describes. Intended for assertion failure messages, where the per-step table is what makes a
	 * derivative error diagnosable.
	 */
	public static String report(String parameterLabel, String responseLabel, DoubleUnaryOperator f, double x,
			double analyticDerivative) {
		StringBuilder sb = new StringBuilder();
		sb.append("central-difference sweep for d(").append(responseLabel).append(")/d(")
				.append(parameterLabel).append(") at ").append(parameterLabel).append("=").append(x).append('\n');
		for (Estimate e : sweep(f, x, analyticDerivative)) {
			sb.append("  ").append(e).append('\n');
		}
		sb.append("  minimum absolute error = ").append(minimumAbsoluteError(f, x, analyticDerivative));
		return sb.toString();
	}
}
