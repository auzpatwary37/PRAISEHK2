package ust.hk.praisehk.metamodelcalibration.calibrator;



import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
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
	public static final String TypeMultiObjective = "MultiObjective";
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
			
		}else if(Type.equals(TypeMeasurementAndTimeSpecific)){
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
	
	public static Map<MeasurementType,Double> calcMultiObjective(Measurements realMeasurements,Measurements simOrAnaMeasurements,String Type) {
		Map<MeasurementType,Double> objective = new HashMap<>();
		if(Type.equals(TypeAADT)) {
			for(Entry<MeasurementType, List<Measurement>> d:realMeasurements.getMeasurementsByType().entrySet()) {
				if(simOrAnaMeasurements.getMeasurementsByType().get(d.getKey()).isEmpty())continue;
				double obj = 0;
				for(Measurement m:d.getValue()) {
					double stationCountReal=0;
					double stationCountAnaOrSim=0;
					for(String timeBeanId:m.getVolumes().keySet()) {
						if(simOrAnaMeasurements.getMeasurements().get(m.getId())==null) {
							logger.error("The Measurements entered are not comparable (measuremtn do not match)!!! This should not happen. Please check");

						}else if(simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)==null) {
							logger.error("The Measurements entered are not comparable (volume timeBeans do not match)!!! This should not happen. Please check");

						}

						stationCountReal+=m.getVolumes().get(timeBeanId);
						stationCountAnaOrSim+=simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId);
					}
					obj+=Math.pow((stationCountReal-stationCountAnaOrSim),2);
				}
				if(!d.getValue().isEmpty()) {
					objective.put(d.getKey(),obj);
				}
			}
		}else if(Type.equals(TypeMeasurementAndTimeSpecific)){
			for(Entry<MeasurementType, List<Measurement>> d:realMeasurements.getMeasurementsByType().entrySet()) {
				if(simOrAnaMeasurements.getMeasurementsByType().get(d.getKey()).isEmpty())continue;
				double obj=0;
				for(Measurement m:d.getValue()) {
					for(String timeBeanId:m.getVolumes().keySet()) {
						if(simOrAnaMeasurements.getMeasurements().get(m.getId())==null) {
							logger.error("The Measurements entered are not comparable (measuremtn do not match)!!! This should not happen. Please check");

						}else if(simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)==null) {
							logger.error("The Measurements entered are not comparable (volume timeBeans do not match)!!! This should not happen. Please check");

						}

						obj+=Math.pow((m.getVolumes().get(timeBeanId)-simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)),2);
					}
				}
				if(!d.getValue().isEmpty()) {
					objective.put(d.getKey(),obj);
				}
			}
		}
		return objective;
	}
	
	public static Map<String,Double> calcMultiObjective(Measurements realMeasurements,Measurements simOrAnaMeasurements,String Type,Map<String, Set<MeasurementType>> mTypes) {
		Map<String,Double> objective = new HashMap<>();
		for(Entry<String, Set<MeasurementType>> d:mTypes.entrySet()) {
			double obj = calcObjective(realMeasurements,simOrAnaMeasurements,Type,d.getValue());
			objective.put(d.getKey(),obj);
		}
		return objective;
	}
	
	public static double calcObjective(Measurements realMeasurements,Measurements simOrAnaMeasurements,String Type,Set<MeasurementType>mType) {
		double objective = 0;
		if(Type.equals(TypeAADT)) {
			for(Entry<MeasurementType, List<Measurement>> d:realMeasurements.getMeasurementsByType().entrySet()) {
				if(!mType.contains(d.getKey()))continue;
				double obj = 0;
				for(Measurement m:d.getValue()) {
					double stationCountReal=0;
					double stationCountAnaOrSim=0;
					for(String timeBeanId:m.getVolumes().keySet()) {
						if(simOrAnaMeasurements.getMeasurements().get(m.getId())==null) {
							logger.error("The Measurements entered are not comparable (measuremtn do not match)!!! This should not happen. Please check");

						}else if(simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)==null) {
							logger.error("The Measurements entered are not comparable (volume timeBeans do not match)!!! This should not happen. Please check");

						}

						stationCountReal+=m.getVolumes().get(timeBeanId);
						stationCountAnaOrSim+=simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId);
					}
					obj+=Math.pow((stationCountReal-stationCountAnaOrSim),2);
				}
				if(!d.getValue().isEmpty()) {
					objective+=obj;
				}
			}
		}else if(Type.equals(TypeMeasurementAndTimeSpecific)){
			for(Entry<MeasurementType, List<Measurement>> d:realMeasurements.getMeasurementsByType().entrySet()) {
				if(!mType.contains(d.getKey()))continue;
				double obj=0;
				for(Measurement m:d.getValue()) {
					for(String timeBeanId:m.getVolumes().keySet()) {
						if(simOrAnaMeasurements.getMeasurements().get(m.getId())==null) {
							logger.error("The Measurements entered are not comparable (measuremtn do not match)!!! This should not happen. Please check");

						}else if(simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)==null) {
							logger.error("The Measurements entered are not comparable (volume timeBeans do not match)!!! This should not happen. Please check");

						}

						obj+=Math.pow((m.getVolumes().get(timeBeanId)-simOrAnaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)),2);
					}
				}
				if(!d.getValue().isEmpty()) {
					objective+=obj;
				}
			}
		}
		return objective;
	}
	
	public static double calcObjective(Measurements realMeasurements,Measurements anaMeasurements,Map<Id<Measurement>,Map<String,MetaModel>>metaModels,LinkedHashMap<String,Double>param,String Type) {
		Measurements metaMeasurements=realMeasurements.clone();
		for(Measurement m:realMeasurements.getMeasurements().values()) {
			for(String timeBeanid:m.getVolumes().keySet()) {
				metaMeasurements.getMeasurements().get(m.getId()).putVolume(timeBeanid, metaModels.get(m.getId()).get(timeBeanid).calcMetaModel(anaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBeanid), param));
			}
		}
		return calcObjective(realMeasurements,metaMeasurements,Type);
	}
}
