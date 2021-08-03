package ust.hk.praisehk.metamodelcalibration.matamodels;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;

import de.xypron.jcobyla.Calcfc;
import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class GradientOptimizedMetaModel extends MetaModelImpl{
	
	private LinkedHashMap<String,Double> simGrad;
	private LinkedHashMap<String,Double> anaGrad;
	private Map<Integer,Double> anaData=new HashMap<>();
	private int currentParamIterNo=0;
	
	public GradientOptimizedMetaModel(Id<Measurement>measurementId,Map<Integer, Measurements> SimData,Map<Integer, Measurements> anaData,
			Map<Integer, LinkedHashMap<String, Double>> paramsToCalibrate, String timeBeanId, int currentParamIterNo,LinkedHashMap<String,Double>SimGradient,
			LinkedHashMap<String,Double>anaGradient,int currentSimIter) {
		super(measurementId,SimData, paramsToCalibrate, timeBeanId, currentParamIterNo);
		this.simGrad=SimGradient;
		this.anaGrad=anaGradient;
		for(Integer i:anaData.keySet()) {
			this.anaData.put(i,anaData.get(i).getMeasurements().get(this.measurementId).getVolumes().get(timeBeanId));
		}
		this.noOfMetaModelParams=this.params.get(currentParamIterNo).size()+2;
		this.MetaModelParams=new double[this.noOfMetaModelParams];
		this.currentParamIterNo=currentParamIterNo;
		this.calibrateMetaModel(currentParamIterNo);
	}


	@Override
	public double calcMetaModel(double analyticalModelPart, LinkedHashMap<String, Double> param) {
		double modelOutput=this.MetaModelParams[0]+MetaModelParams[1]*analyticalModelPart;
		int i=1;
		for(double d:param.values()) {
			modelOutput+=this.MetaModelParams[i+1]*d;
			i++;
		}
		return modelOutput;
	}
	
	protected void calibrateMetaModel(final int counter) {
		Calcfc optimization=new Calcfc() {

			@Override
			public double compute(int m, int n, double[] x, double[] constrains) {
				double objective=0;
				MetaModelParams=calcMetaModelParam(x);
//				constrains[0]=simData.get(counter)-calcMetaModel(anaData.get(counter),params.get(currentParamIterNo));
//				constrains[0]=-simData.get(counter)+calcMetaModel(anaData.get(counter),params.get(currentParamIterNo));
				for(int i:params.keySet()) {
					objective+=Math.pow(calcMetaModel(anaData.get(i), params.get(i))-simData.get(i),2)*calcEuclDistanceBasedWeight(params, i,counter);
				}
				return objective;
			}
			
		};
		double[] x=new double[this.noOfMetaModelParams]; 
		for(int i=0;i<this.noOfMetaModelParams;i++) {
			x[i]=0.1;
		}
	    CobylaExitStatus result = Cobyla.findMinimum(optimization, 1, 0, x,0.5,Math.pow(10, -6) ,3, 1500);
	    this.MetaModelParams=this.calcMetaModelParam(x);
	}
	
	private double[] calcMetaModelParam(double[] x) {
		double m1=x[0];
		double[] m=new double[this.noOfMetaModelParams];
		m[1]=m1;
		m[0]=this.simData.get(this.currentParamIterNo)-m1*this.anaData.get(this.currentParamIterNo);
		int i=2;
		for(String s:this.params.get(0).keySet()) {
			m[0]+=this.params.get(this.currentParamIterNo).get(s)*(this.anaGrad.get(s)*m1-this.simGrad.get(s));
			m[i]=this.simGrad.get(s)-this.anaGrad.get(s)*m1;
			i++;
		}
		
		return m;
	}


	@Override
	public String getMetaModelName() {
		return this.GradientBased_III_MetaModelName;
	}


	@Override
	public double[] getGradientVector() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Double getanaGradMultiplier() {
		// TODO Auto-generated method stub
		return null;
	}
}
