package matsimIntegration;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import measurements.Measurements;

/**
 * This class can store measurements with Parameters
 * measurements can be queried with parameter directly as well.
 * @author h
 *
 */

public class MeasurementsStorage {
	private Map<String,Measurements> linkCounts=new ConcurrentHashMap<>();
	private final Measurements calibrationMeasurements;

	public MeasurementsStorage(Measurements calibrationMeasurements) {
		this.calibrationMeasurements=calibrationMeasurements;
	}


	public Measurements getSimMeasurement(LinkedHashMap<String,Double>param){
		return this.linkCounts.get(this.genearteParamId(param));
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
		this.linkCounts.put(this.genearteParamId(params), simMeasurements);
	}
	
	public void storeMeasurements(LinkedHashMap<String,Double>params,Map<String,Map<Id<Link>,Double>>linkVolumes) {
		Measurements simMeasurements=this.calibrationMeasurements.clone();
		simMeasurements.updateMeasurements(linkVolumes);
		this.linkCounts.put(this.genearteParamId(params), simMeasurements);
	}
	public Set<Id<Link>> getLinksToCount() {
		return calibrationMeasurements.getLinksToCount();
	}
	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return calibrationMeasurements.getTimeBean();
	}
}

