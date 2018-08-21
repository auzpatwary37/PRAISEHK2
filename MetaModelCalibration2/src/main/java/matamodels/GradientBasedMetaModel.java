package matamodels;

import java.util.HashMap;
import java.util.LinkedHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

public class GradientBasedMetaModel implements MetaModel{

	private static final double c=0.1;
	private double[] metaParams;
	private LinkedHashMap<String,Double> currentParam;
	private String timeBeanId;
	private Id<Link> linkId;
	
	
	/**
	 * This constructor do not call the simulation run implicitly.
	 * rather taking the already calculated gradients and preparing the metamodel.
	 * @param SimData
	 * @param AnalyticalData
	 * @param paramsToCalibrate
	 * @param timeBeanId
	 * @param counter
	 * @param SimGradient
	 * @param anaGradient
	 */
	public GradientBasedMetaModel(HashMap<Integer,HashMap<String,Double>> SimData, HashMap<Integer,HashMap<String,Double>> AnalyticalData,
			HashMap<Integer, LinkedHashMap<String, Double>> paramsToCalibrate,String timeBeanId, int counter,LinkedHashMap<String,Double>SimGradient,
			LinkedHashMap<String,Double>anaGradient) {
		this.currentParam=paramsToCalibrate.get(counter);
		this.metaParams=new double[paramsToCalibrate.get(counter).size()+1];
		this.timeBeanId=timeBeanId;
		//this.currentParam=currentParam;
		metaParams[0]=SimData.get(counter).get(timeBeanId)-AnalyticalData.get(counter).get(timeBeanId);
		int i=1;
		for(String s:SimGradient.keySet()) {
			metaParams[i]=SimGradient.get(s)-anaGradient.get(s);
			i++;
		}
	}
	@Override
	public double calcMetaModel(double analyticalModelPart, LinkedHashMap<String, Double> param) {
		double objective=metaParams[0];
		int i=1;
		for(String s:param.keySet()) {
			objective+=metaParams[i]*(this.currentParam.get(s)-param.get(s));
			i++;
		}
		objective+=analyticalModelPart;
		return objective;
	}

	@Override
	public String getTimeBeanId() {
		return timeBeanId;
	}

	@Override
	public double[] getMetaModelParams() {
		return metaParams;
	}

	public Id<Link> getLinkId(){
		return this.linkId;
	}
}
