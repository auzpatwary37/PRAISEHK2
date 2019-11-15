package ust.hk.praisehk.metamodelcalibration.matamodels;

import java.util.LinkedHashMap;

import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;

public class WrappedMetaModel implements MetaModel {

	private final  Id<Measurement> measurementId;
	private final String timeBeanId;
	private final String metaModelName="WrappedMetaModel";
	
	
	public WrappedMetaModel(Id<Measurement> measurementId,String timeBeanId) {
		this.measurementId=measurementId;
		this.timeBeanId=timeBeanId;
	}
	
	@Override
	public double calcMetaModel(double analyticalModelPart, LinkedHashMap<String, Double> param) {
		
		return analyticalModelPart;
	}

	@Override
	public String getTimeBeanId() {
		// TODO Auto-generated method stub
		return timeBeanId;
	}

	@Override
	public double[] getMetaModelParams() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getMetaModelName() {
		// TODO Auto-generated method stub
		return this.metaModelName;
	}

	@Override
	public Id<Measurement> getMeasurementId() {
		// TODO Auto-generated method stub
		return this.measurementId;
	}

}
