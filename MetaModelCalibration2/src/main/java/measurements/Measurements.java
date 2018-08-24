package measurements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

/**
 * A simplified class for holding measurements 
 * Same for both Simulation measurement analytical Measurement and RealCount measurement 
 * 
 *
 * @author h
 *
 */
public class Measurements {
	
	
	
	private final Map<String,Tuple<Double,Double>> timeBean;
	private Map<Id<Measurement>,Measurement> measurements=new HashMap<>();
	
	private Measurements(Map<String,Tuple<Double,Double>> timeBean) {
		this.timeBean=timeBean;
	}
	
	public static Measurements createMeasurements(HashMap<String,Tuple<Double,Double>> timeBean) {
		return new Measurements(timeBean);
	}
	
	public void createAnadAddMeasurement(String measurementId) {
		Measurement m=new Measurement(measurementId,this.timeBean);
		this.measurements.put(m.getId(), m);
	}
	
	protected void addMeasurement(Measurement m) {
		this.measurements.put(m.getId(), m);
	}

	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return timeBean;
	}

	public Map<Id<Measurement>, Measurement> getMeasurements() {
		return measurements;
	}
	
	/**
	 * Will deep clone the measurement and provide a new measurement exactly same as the current measurement 
	 * Modifying the current measurement will not affect the new created measurement and vis-versa
	 */
	public Measurements clone() {
		Measurements m=new Measurements(this.timeBean);
		for(Measurement mm: this.measurements.values()) {
			m.addMeasurement(mm);
		}
		return m;
	}
	
	public void updateMeasurements(Map<String,Map<Id<Link>,Double>> linkVolumes) {
		for(Measurement m:this.measurements.values()) {
			m.updateMeasurement(linkVolumes);
		}
	}
	
	/**
	 * Will return a set containing all the links to count volume for 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Set<Id<Link>> getLinksToCount(){
		Set<Id<Link>>linkSet=new HashSet<>();
		
		for(Measurement m: this.measurements.values()) {
			for(Id<Link>lId:(ArrayList<Id<Link>>)m.getAttribute(m.linkListAttributeName)) {
				linkSet.add(lId);
			}
		}
		
		return linkSet;
	}
	
	public void writeCSVMeasurements(String fileLoc) {
		try {
			FileWriter fw=new FileWriter(new File(fileLoc),false);
			fw.append("timeId,MeasurementId,PCUCount\n");
			for(Measurement m:this.measurements.values()) {
				for(String timeId:m.getVolumes().keySet())
				fw.append(timeId+","+m.getId().toString()+","+m.getVolumes().get(timeId)+"\n");
			}
			fw.flush();
			fw.close();
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}
}
