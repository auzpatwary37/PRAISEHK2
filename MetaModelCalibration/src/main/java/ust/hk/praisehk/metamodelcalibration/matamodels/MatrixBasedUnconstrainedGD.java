package ust.hk.praisehk.metamodelcalibration.matamodels;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;
import org.matsim.core.utils.collections.Tuple;

public class MatrixBasedUnconstrainedGD {
	protected double alpha = .0001;
	
	private int counter;
	private double c = 100;
	private Tuple<Double,Double> limitFor2ndElement = new Tuple<Double,Double>(0.,2.);
	public MatrixBasedUnconstrainedGD(int noOfVar) {
		
		counter = 0;
	}
	
	public double[] update(double[] current,double[] gradient) {
		RealVector g = MatrixUtils.createRealVector(gradient);
		RealVector p = MatrixUtils.createRealVector(current);
		if(g.getNorm()>c*g.getDimension()) {//Clipping
			if(!Double.isInfinite(g.getNorm())) {
				g = g.mapDivide(g.getNorm()).mapMultiply(c*g.getDimension());
			}else {
				g = g.mapDivide(g.getL1Norm()).mapMultiply(c*g.getDimension());
			}
		}
		
		//RealVector p_new = p.subtract(m_h.mapMultiply(this.alpha/(1+(counter-1)*0.4)).ebeDivide(v_h.map(k->Math.sqrt(k)).mapAdd(this.eta)));
		RealVector p_new = p.subtract(g.mapMultiply(this.alpha));
		if(p_new.getEntry(1)<limitFor2ndElement.getFirst())p_new.setEntry(1, limitFor2ndElement.getFirst());
		if(p_new.getEntry(1)>limitFor2ndElement.getSecond())p_new.setEntry(1, limitFor2ndElement.getSecond());
		counter++;
		return p_new.toArray();
	}
	public RealVector update(RealVector p,RealVector g) {
		
		if(g.getNorm()>c*g.getDimension()) {//Clipping
			if(!Double.isInfinite(g.getNorm())) {
				g = g.mapDivide(g.getNorm()).mapMultiply(c*g.getDimension());
			}else {
				g = g.mapDivide(g.getL1Norm()).mapMultiply(c*g.getDimension());
			}
		}

		//RealVector p_new = p.subtract(m_h.mapMultiply(this.alpha/(1+(counter-1)*0.4)).ebeDivide(v_h.map(k->Math.sqrt(k)).mapAdd(this.eta)));
		RealVector p_new = p.subtract(g.mapMultiply(this.alpha));
		if(p_new.getEntry(1)<limitFor2ndElement.getFirst())p_new.setEntry(1, limitFor2ndElement.getFirst());
		if(p_new.getEntry(1)>limitFor2ndElement.getSecond())p_new.setEntry(1, limitFor2ndElement.getSecond());
		counter++;
		return p_new;
	}

	public Tuple<Double, Double> getLimitFor2ndElement() {
		return limitFor2ndElement;
	}

	public void setLimitFor2ndElement(Tuple<Double, Double> limitFor2ndElement) {
		this.limitFor2ndElement = limitFor2ndElement;
	}
	
}

