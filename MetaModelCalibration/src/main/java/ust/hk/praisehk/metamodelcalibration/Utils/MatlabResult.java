package ust.hk.praisehk.metamodelcalibration.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MatlabResult {
	
	private final double[] x;
	private final double fval;
	
	public MatlabResult(double[] x, double fval) {
		this.x=x;
		this.fval=fval;
	}

	public double[] getX() {
		return x;
	}

	public double getFval() {
		return fval;
	}

	
}
