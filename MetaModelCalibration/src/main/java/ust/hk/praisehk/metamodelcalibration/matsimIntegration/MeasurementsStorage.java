package ust.hk.praisehk.metamodelcalibration.matsimIntegration;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

/**
 * This class can store measurements with Parameters
 * measurements can be queried with parameter directly as well.
 * @author h
 *
 */

public class MeasurementsStorage {
	private Map<String,Measurements> Counts=new ConcurrentHashMap<>();
	private final Measurements calibrationMeasurements;
	private boolean isParamSpecific=true;

	public MeasurementsStorage(Measurements calibrationMeasurements) {
		this.calibrationMeasurements=calibrationMeasurements;
	}


	public Measurements getSimMeasurement(LinkedHashMap<String,Double>param){
		return this.Counts.get(this.genearteParamId(param));
	}
	
	public Measurements getSimMeasurement(String key){
		return this.Counts.get(key);
	}
	
	public static String genearteParamId(LinkedHashMap<String,Double> params) {
		String paramId="";
		for(String s:params.keySet()) {
			paramId=paramId+s+"_"+params.get(s)+"_";
		}
		return paramId;
	}

	public Measurements getCalibrationMeasurements() {
		return calibrationMeasurements;
	}


	public void storeMeasurements(LinkedHashMap<String,Double>params,Measurements simMeasurements) {
		this.Counts.put(this.genearteParamId(params), simMeasurements);
	}
	
	public void storeMeasurements(String key,Measurements simMeasurements) {
		this.isParamSpecific=false;
		this.Counts.put(key, simMeasurements);
	}
	
	
	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return calibrationMeasurements.getTimeBean();
	}
}

