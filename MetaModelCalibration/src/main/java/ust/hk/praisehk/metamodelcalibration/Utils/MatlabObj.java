package ust.hk.praisehk.metamodelcalibration.Utils;

import java.util.LinkedHashMap;

public interface MatlabObj {

	public double evaluateFunction(double[] x);
	
	public double evaluateConstrain(double[] x);

	public LinkedHashMap<String, Double> ScaleUp(double[] x);
	
}
