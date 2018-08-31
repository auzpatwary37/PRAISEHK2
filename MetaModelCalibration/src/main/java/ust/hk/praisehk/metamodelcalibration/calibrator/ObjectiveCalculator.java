package ust.hk.praisehk.metamodelcalibration.calibrator;



import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

/**
 * This class will basically calculate the objective 
 * same for both sim and anaMeasurements This can be AADT or specific measurement based 
 * Direct change should e implemented inside the method
 * @author h
 *
 */
public class ObjectiveCalculator {
	
	public static final String TypeAADT="AADT";
	public static final String TypeMeasurementAndTimeSpecific="MeasurementAndTimeSpecific";
	public static final Logger logger=Logger.getLogger(ObjectiveCalculator.class);
	public static final String Type=TypeMeasurementAndTimeSpecific;
	/**
	 * 
	 * @param realMeasurements
	 * @param simOrAnaMeasurement
	 * @param Type: AADT or linkAnadTimeSpecific(default)
	 * @return
	 */
	public static double calcObjective(Measurements realMeasurements,Measurements simOrAnaMeasurements,String Type) {
		double objective=0;
		if(Type.equals(TypeAADT)) {
			double stationCountReal=0;
			double stationCountAnaOrSim=0;
			for(Measurement m:realMeasurements.getMeasurements().values()) {
				for(String timeBeanId:m.getVolumes().keySet()) {
					if(simOrAnaMeasurements.getMeasurements().get(m.getId())==null) {
						logger.error("The Measurements entered are not comparable (measuremtn do not match)!!! This should not happen. Please check");
						
					}else if(simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)==null) {
						logger.error("The Measurements entered are not comparable (volume timeBeans do not match)!!! This should not happen. Please check");
						
					}
					
					stationCountReal+=m.getVolumes().get(timeBeanId);
					stationCountAnaOrSim+=simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId);
				}
				objective+=Math.pow((stationCountReal-stationCountAnaOrSim),2);
			}
			
		}else {
			for(Measurement m:realMeasurements.getMeasurements().values()) {
				for(String timeBeanId:m.getVolumes().keySet()) {
					if(simOrAnaMeasurements.getMeasurements().get(m.getId())==null) {
						logger.error("The Measurements entered are not comparable (measuremtn do not match)!!! This should not happen. Please check");
						
					}else if(simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)==null) {
						logger.error("The Measurements entered are not comparable (volume timeBeans do not match)!!! This should not happen. Please check");
						
					}
					
					objective+=Math.pow((m.getVolumes().get(timeBeanId)-simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)),2);
				}
			}
			
		}
		return objective;
	}
	
	
	public static double calcObjective(Measurements realMeasurements,Measurements anaMeasurements,Map<Id<Measurement>,Map<String,MetaModel>>metaModels,LinkedHashMap<String,Double>param,String Type) {
		Measurements metaMeasurements=realMeasurements.clone();
		for(Measurement m:realMeasurements.getMeasurements().values()) {
			for(String timeBeanid:m.getVolumes().keySet()) {
				metaMeasurements.getMeasurements().get(m.getId()).addVolume(timeBeanid, metaModels.get(m.getId()).get(timeBeanid).calcMetaModel(anaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanid), param));
			}
		}
		return calcObjective(realMeasurements,metaMeasurements,Type);
	}
}
