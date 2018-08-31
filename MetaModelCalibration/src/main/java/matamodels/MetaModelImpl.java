package matamodels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;

import de.xypron.jcobyla.Calcfc;
import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import measurements.Measurement;
import measurements.Measurements;
/**
 * 
 * @author Ashraf
 *
 */
public abstract class MetaModelImpl implements MetaModel{
	
	
	protected final Id<Measurement> measurementId;
	protected final String timeBeanId;
	protected double[] MetaModelParams;
	
	protected Map<Integer,Double> simData=new HashMap<>();
	protected Map<Integer,LinkedHashMap<String,Double>> params;
	protected int noOfParams=0;
	protected int noOfMetaModelParams;
	/**
	 * 
	 * @param SimData: SimData to be fitted meta model to
	 * @param paramsToCalibrate or independent Variable 
	 * @param hour
	 * @param counter
	 * 
	 * Does not contain any analytical model information. can be added if necessary
	 * in the subclass.
	 */
	
	protected MetaModelImpl(Id<Measurement> measurementId,Map<Integer,Measurements> SimData,
			Map<Integer, LinkedHashMap<String, Double>> paramsToCalibrate,String timeBeanId, int currentParamNo) {
		
		for(Entry<Integer, Measurements> e:SimData.entrySet()) {
			this.simData.put(e.getKey(),e.getValue().getMeasurements().get(measurementId).getVolumes().get(timeBeanId));
		}
		this.measurementId=measurementId;
		this.params=new HashMap<>(paramsToCalibrate);
		this.timeBeanId =timeBeanId;
		this.noOfParams=params.get(0).size();
	}
	
	
	
	
	@Override
	public abstract double calcMetaModel(double analyticalModelPart, LinkedHashMap<String, Double> param);

	@Override
	public String getTimeBeanId() {
		return this.timeBeanId;
	}
	
	@Override
	public Id<Measurement> getMeasurementId(){
		return this.measurementId;
	}

	@Override
	public double[] getMetaModelParams() {
		return this.MetaModelParams;
	}
	protected double calcEuclDistanceBasedWeight(Map<Integer,LinkedHashMap<String,Double>> params, int i, int currentParamNo) {
		LinkedHashMap<String,Double> param1=params.get(currentParamNo);
		LinkedHashMap<String,Double> param2=params.get(i);
		double squareDiff=0;
		for(String s:param1.keySet()) {
			squareDiff+=Math.pow(param1.get(s)-param2.get(s),2);
		}
		return 1.0/(1.0+Math.sqrt(squareDiff));
	}
	
	/**
	 * must be overridden if there is a analytical Model part attached with the meta-model
	 * @param counter
	 */
	
	protected void calibrateMetaModel(final int currentParamNo) {
		Calcfc optimization=new Calcfc() {

			@Override
			public double compute(int m, int n, double[] x, double[] constrains) {
				double objective=0;
				MetaModelParams=x;
				for(int i:params.keySet()) {
					objective+=Math.pow(calcMetaModel(0, params.get(i))-simData.get(i),2)*calcEuclDistanceBasedWeight(params, i,currentParamNo);
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
	};
}
