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

public class LinearMetaModel extends MetaModelImpl{

	/**
	 * The format is Y=Bo+BX
	 * Bo --> Const.
	 * B --> Vector of Meta-Model Parameters
	 * X --> Vector of Parameters to be Calibrated 
	 */
	/**
	 * constructor
	 */
	
	
	public LinearMetaModel(Id<Measurement>measurementId,Map<Integer,Measurements> SimData,
			Map<Integer, LinkedHashMap<String, Double>> paramsToCalibrate,String timeBeanId, int currentParamNo) {
		
		super(measurementId,SimData,paramsToCalibrate,timeBeanId,currentParamNo);
		this.noOfMetaModelParams=this.noOfParams+1;
		this.calibrateMetaModel(currentParamNo);
		
		//this.params.clear();
		this.simData.clear();
		
	}
		
	
	public void calibrateMetaModel(final int counter) {
		Calcfc optimization=new Calcfc() {

			@Override
			public double compute(int m, int n, double[] x, double[] constrains) {
				double objective=0;
				MetaModelParams=x;
				for(int i:params.keySet()) {
					objective+=Math.pow(calcMetaModel(0, params.get(i))-simData.get(i),2)*calcEuclDistanceBasedWeight(params, i,counter);
				}
				return objective;
			}
			
		};
		double[] x=new double[this.noOfMetaModelParams]; 
		for(int i=0;i<this.noOfMetaModelParams;i++) {
			x[i]=0;
		}
	    CobylaExitStatus result = Cobyla.findMinimum(optimization, this.noOfMetaModelParams, 0, x,0.5,Math.pow(10, -6) ,3, 1500);
	    this.MetaModelParams=x;
		
	}

	@Override
	public double calcMetaModel(double analyticalModelPart, LinkedHashMap<String, Double> param) {
		double modelOutput=this.MetaModelParams[0];
		int i=1;
		for(double d:param.values()) {
			modelOutput+=this.MetaModelParams[i]*d;
			i++;
		}
		
		return modelOutput;
		
	}


	@Override
	public String getMetaModelName() {
		return this.LinearMetaModelName;
	}
}
