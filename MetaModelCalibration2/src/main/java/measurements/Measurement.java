package measurements;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

public class Measurement {
	
	private final Id<Measurement> id;
	private Map<String,Object> attributes=new HashMap<>();
	private final Map<String,Tuple<Double,Double>> timeBean;
	private Map<String,Double> volumes=new HashMap<>();
	private static final Logger logger=Logger.getLogger(Measurement.class);
	
	protected Measurement(String id, Map<String,Tuple<Double,Double>> timeBean) {
		this.id=Id.create(id, Measurement.class);
		this.timeBean=timeBean;
		
	}
	
	public void addVolume(String timeBeanId,double volume) {
		
		if(!this.timeBean.containsKey(timeBeanId)){
			logger.error("timeBean do not contain timeBeanId"+timeBeanId+", please check.");
			logger.warn("Ignoring volume for timeBeanId"+timeBeanId);
		}else {
			this.volumes.put(timeBeanId, volume);
		}
	}
	
	public Object getAttribute(String attributeName) {
		return this.attributes.get(attributeName);
	}
	
	public Id<Measurement> getId() {
		return id;
	}

	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return timeBean;
	}

	public void setAttribute(String attributeName, Object attribute) {
		this.attributes.put(attributeName, attribute);
	}
	public Map<String,Double> getVolumes(){
		return this.volumes;
	}
	
	public Measurement clone() {
		Measurement m=new Measurement(this.id.toString(),new HashMap<>(timeBean));
		for(String s:this.volumes.keySet()) {
			m.addVolume(s, this.getVolumes().get(s));
		}
		for(String s:this.attributes.keySet()) {
			m.setAttribute(s, this.attributes.get(s));
		}
		return m;
	}
	
	public void updateMeasurement(Map<String,Map<Id<Link>,Double>>linkVolumes) {
		
	}
}
