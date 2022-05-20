package ust.hk.praisehk.metamodelcalibration.measurements;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;

/**
 * A simplified class for holding measurements 
 * Same for both Simulation measurement analytical Measurement and RealCount measurement 
 * TODO: Add linkIds to the reader and writers
 *
 * @author h
 *
 */
public class Measurements {
	
	private final Map<String,Tuple<Double,Double>> timeBean;
	private Map<Id<Measurement>,Measurement> measurements=new HashMap<>();
	private Map<MeasurementType,List<Measurement>> measurementsByType=new HashMap<>();
	
	
	private Measurements(Map<String,Tuple<Double,Double>> timeBean) {
		this.timeBean=timeBean;
		for(MeasurementType mt:MeasurementType.values()) {
			this.measurementsByType.put(mt,new ArrayList<>());
		}
	}
	
	public static Measurements createMeasurements(Map<String,Tuple<Double,Double>> timeBean) {
		return new Measurements(timeBean);
	}
	
	public Measurement createAnadAddMeasurement(String measurementId,MeasurementType mType) {
		Measurement m=new Measurement(measurementId,this.timeBean,mType);
		this.measurements.put(m.getId(), m);
		List<Measurement> mlist=this.measurementsByType.get(m.getMeasurementType());
		if(mlist==null) {
			mlist=new ArrayList<>();
			this.measurementsByType.put(mType, mlist);
		}
		mlist.add(m);
		return m;
	}
	
	public void addMeasurement(Measurement m) {
		this.measurements.put(m.getId(), m);
		if(!this.measurementsByType.containsKey(m.getMeasurementType())) {
			this.measurementsByType.put(m.getMeasurementType(), new ArrayList<>());
		}
		this.measurementsByType.get(m.getMeasurementType()).add(m);
	}
	
	public void removeMeasurementsByType(MeasurementType type) {
		for(Measurement m:this.measurementsByType.get(type)) {
			this.measurements.remove(m.getId());
		}
		this.measurementsByType.remove(type);
	}
	
	public void removeMeasurement(Id<Measurement> mId) {
		Measurement m = this.measurements.get(mId);
		this.measurements.remove(mId);
		this.measurementsByType.get(m.getMeasurementType()).remove(m);
		if(this.measurementsByType.get(m.getMeasurementType()).isEmpty())this.measurementsByType.remove(m.getMeasurementType());
	}

	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return timeBean;
	}

	public Map<Id<Measurement>, Measurement> getMeasurements() {
		return measurements;
	}
	
	/**
	 * Will deep clone the measurement and provide a new measurement exactly same as the current measurement 
	 * Modifying the current measurement will not affect the new created measurement and vise-versa The attributes are not deep copied
	 */
	public Measurements clone() {
		Measurements m=new Measurements(this.timeBean);
		for(Measurement mm: this.measurements.values()) {
			m.addMeasurement(mm.clone());
		}
		
		return m;
	}
	
	public void updateMeasurements(SUEModelOutput modelOut,AnalyticalModel sue,Object otherDataContainer) {
		for(Measurement m:this.measurements.values()) {
			if(m.getId().toString().contains("EAL")) {
				continue;
			}
			m.updateMeasurement(modelOut, sue,otherDataContainer);
		}
	}
	
	/**
	 * Will return a set containing all the links to count volume for 
	 * @return
	 */
	@Deprecated
	public Set<Id<Link>> getLinksToCount(){
		Set<Id<Link>>linkSet=new HashSet<>();
		if(this.measurementsByType==null) {
			System.out.println();
		}else if(this.measurementsByType.get(MeasurementType.linkVolume)==null) {
			System.out.println();
		}
		for(Measurement m: this.measurementsByType.get(MeasurementType.linkVolume)) {
			for(Id<Link>lId:(ArrayList<Id<Link>>)m.getAttribute(m.linkListAttributeName)) {
				linkSet.add(lId);
			}
		}
		
		return linkSet;
	}
	
	public void addRedundantTimeBean(Map<String,Tuple<Double,Double>> timeBeans) {
		boolean isConsistant=true;
		for(String timeId:this.timeBean.keySet()) {
			if(!timeBean.containsKey(timeId)) {
				isConsistant=false;
				throw new IllegalArgumentException("TimeBeanIds are inconsistant. Addition of time bean is not possible.");
			}
		}
		for(Entry<String, Tuple<Double, Double>> timeBeans1:timeBeans.entrySet()) {
			if(!this.timeBean.containsKey(timeBeans1.getKey())) {
				this.timeBean.put(timeBeans1.getKey(), timeBeans1.getValue());
			}
		}
	}
	
	public void writeCSVMeasurements(String fileLoc) {
		try {
			FileWriter fw=new FileWriter(new File(fileLoc),false);
			fw.append("MeasurementId,timeId,Count,Type,ifForValidation\n");
			for(Measurement m:this.measurements.values()) {
				Object forV = "0";
				if(m.getAttribute("ifForValidation")!=null)forV = m.getAttribute("ifForValidation");
				for(String timeId:m.getVolumes().keySet())
				fw.append(m.getId().toString().replace(",", "__")+","+timeId+","+m.getVolumes().get(timeId)+","+m.getMeasurementType().toString()+","+forV.toString()+"\n");
			}
			fw.flush();
			fw.close();
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}
	
	public void updateMeasurementsFromFile(String fileLoc) {
		try {
			BufferedReader bf=new BufferedReader(new FileReader(new File(fileLoc)));
			bf.readLine();
			String line;
			while((line=bf.readLine())!=null) {
				String[] part=line.split(",");
				Id<Measurement> measurementId=Id.create(part[0].trim(), Measurement.class);
				if(!this.measurements.containsKey(measurementId)) {
					this.createAnadAddMeasurement(measurementId.toString(),MeasurementType.valueOf(part[3]));
				}
				String timeBeanId=part[1].trim();
				this.measurements.get(measurementId).putVolume(timeBeanId, Double.parseDouble(part[2].trim()));
			}
			bf.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public Map<MeasurementType, List<Measurement>> getMeasurementsByType() {
		return measurementsByType;
	}
	
	public void resetMeasurements() {
		for(Measurement m:this.measurements.values()) {
			for(String s:m.getVolumes().keySet()) {
				m.putVolume(s, 0);
			}
		}
	}
	
	public void resetMeasurementsByType(MeasurementType type) {
		for(Measurement m:this.measurementsByType.get(type)) {
			for(String s:m.getVolumes().keySet()) {
				m.putVolume(s, 0);
			}
		}
	}
	
	public void applyFactor(Double factor) {
		for(Measurement m:this.measurements.values()) {
			for(String timeId:m.getVolumes().keySet()) {
				m.putVolume(timeId, m.getVolumes().get(timeId)*factor);
				m.putSD(timeId, m.getSD().get(timeId)*factor);
			}
		}
	}
	
}
