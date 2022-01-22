package ust.hk.praisehk.metamodelcalibration.matamodels;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;
import org.matsim.core.utils.collections.Tuple;

public class MatrixBasedUnconstrainedAdam {
	private double alpha = .005;
	private double beta1 = .9;
	private double beta2 = 0.999;
	private double eta = 10e-8;
	private RealVector m;
	private double dropout = 0.2;
	//private Map<String,Double> v = new HashMap<>(); 
	private RealVector v;
	private int counter;
	private double c = 1000;
	private Tuple<Double,Double> limitFor2ndElement = new Tuple<Double,Double>(0.,2.);
	
	public MatrixBasedUnconstrainedAdam(int noOfVar) {
		m = MatrixUtils.createRealVector(new double[noOfVar]);
		v = MatrixUtils.createRealVector(new double[noOfVar]);
		counter = 0;
	}
	
	public void setLimitFor2ndElement(Tuple<Double, Double> limitFor2ndElement) {
		this.limitFor2ndElement = limitFor2ndElement;
	}

	public double[] update(double[] current,double[] gradient) {
		RealVector g = MatrixUtils.createRealVector(gradient);
		RealVector p = MatrixUtils.createRealVector(current);
		if(g.getNorm()>c) {//Clipping
			if(!Double.isInfinite(g.getNorm())) {
				g = g.mapDivide(g.getNorm()).mapMultiply(c);
			}else {
				g = g.mapDivide(g.getL1Norm()).mapMultiply(c);
			}
		}
		m = m.mapMultiply(this.beta1).add(g.mapMultiply(1-beta1));
		v = v.mapMultiply(this.beta2).add(g.ebeMultiply(g).mapMultiply(1-this.beta2));
		RealVector m_h = m.mapDivide(1-this.beta1);
		RealVector v_h = v.mapDivide(1-this.beta2);
		//RealVector p_new = p.subtract(m_h.mapMultiply(this.alpha/(1+(counter-1)*0.4)).ebeDivide(v_h.map(k->Math.sqrt(k)).mapAdd(this.eta)));
		RealVector p_new = p.subtract(m_h.mapMultiply(this.alpha).ebeDivide(v_h.map(k->Math.sqrt(k)).mapAdd(this.eta)));
		counter++;
		if(p_new.getEntry(1)<limitFor2ndElement.getFirst())p_new.setEntry(1, limitFor2ndElement.getFirst());
		if(p_new.getEntry(1)>limitFor2ndElement.getSecond())p_new.setEntry(1, limitFor2ndElement.getSecond());
		for(int i = 0; i<p_new.getDimension(); i++) {
			if(Math.random()<this.dropout)p_new.setEntry(i, current[i]);
		}
		return p_new.toArray();
	}
	public RealVector update(RealVector p,RealVector g) {
		
		if(g.getNorm()>c) {//Clipping
			if(!Double.isInfinite(g.getNorm())) {
				g = g.mapDivide(g.getNorm()).mapMultiply(c);
			}else {
				g = g.mapDivide(g.getL1Norm()).mapMultiply(c);
			}
		}
		m = m.mapMultiply(this.beta1).add(g.mapMultiply(1-beta1));
		v = v.mapMultiply(this.beta2).add(g.ebeMultiply(g).mapMultiply(1-this.beta2));
		RealVector m_h = m.mapDivide(1-this.beta1);
		RealVector v_h = v.mapDivide(1-this.beta2);
		//RealVector p_new = p.subtract(m_h.mapMultiply(this.alpha/(1+(counter-1)*0.4)).ebeDivide(v_h.map(k->Math.sqrt(k)).mapAdd(this.eta)));
		RealVector p_new = p.subtract(m_h.mapMultiply(this.alpha).ebeDivide(v_h.map(k->Math.sqrt(k)).mapAdd(this.eta)));
		if(p_new.getEntry(1)<limitFor2ndElement.getFirst())p_new.setEntry(1, limitFor2ndElement.getFirst());
		if(p_new.getEntry(1)>limitFor2ndElement.getSecond())p_new.setEntry(1, limitFor2ndElement.getSecond());
		for(int i = 0; i<p_new.getDimension(); i++) {
			if(Math.random()<this.dropout)p_new.setEntry(i, p.getEntry(i));
		}
		counter++;
		return p_new;
	}
}
