package measurements;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

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
}
