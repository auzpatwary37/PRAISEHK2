package ust.hk.praisehk.metamodelcalibration.Utils;

public interface MatlabObj {

	public double evaluateFunction(double[] x);
	
	public double evaluateConstraint(double[] x);
	
}
