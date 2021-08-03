package ust.hk.praisehk.metamodelcalibration.matamodels;

import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class GradientBasedMetaModel implements MetaModel{

	private static final double c=0.1;
	private double[] metaParams;
	private LinkedHashMap<String,Double> currentParam;
	private String timeBeanId;
	private Id<Measurement> measurementId;
	
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
	public GradientBasedMetaModel(Id<Measurement>measurementId,Map<Integer,Measurements> SimData, Map<Integer,Measurements> AnalyticalData,
			Map<Integer, LinkedHashMap<String, Double>> paramsToCalibrate,String timeBeanId, int currentParamNo, LinkedHashMap<String,Double>SimGradient,
			LinkedHashMap<String,Double>anaGradient) {
		this.measurementId=measurementId;
		this.currentParam=paramsToCalibrate.get(currentParamNo);
		this.metaParams=new double[paramsToCalibrate.get(currentParamNo).size()+1];
		this.timeBeanId=timeBeanId;
		//this.currentParam=currentParam;
		metaParams[0]=SimData.get(currentParamNo).getMeasurements().get(this.measurementId).getVolumes().get(timeBeanId)-AnalyticalData.get(currentParamNo).getMeasurements().get(this.measurementId).getVolumes().get(timeBeanId);
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

	
	@Override
	public String getMetaModelName() {
		return this.GradientBased_I_MetaModelName;
	}



	@Override
	public Id<Measurement> getMeasurementId() {
		return this.measurementId;
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
