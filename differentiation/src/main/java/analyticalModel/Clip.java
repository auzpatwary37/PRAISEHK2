package analyticalModel;

import org.apache.commons.math3.analysis.UnivariateFunction;

/**
 * This function clips the vectors 
 * @author Enoch
 *
 */
public class Clip implements UnivariateFunction {
	
	private final double min;
	private final double max;
	
	public Clip(double min, double max) {
		this.min = min;
		this.max = max;
	}

	@Override
	public double value(double x) {
		return Math.min(Math.max(x, min), max);
	}

}
