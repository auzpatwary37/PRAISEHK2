package ust.hk.praisehk.metamodelcalibration.measurements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

public class Measurement {
	
	/**
	 * Some attributes name are kept as public and final string
	 */
	public final String linkListAttributeName="LINK_LIST";
	
	private final Id<Measurement> id;
	private Map<String,Object> attributes=new HashMap<>();
	private final Map<String,Tuple<Double,Double>> timeBean;
	private Map<String,Double> volumes=new HashMap<>();
	private static final Logger logger=Logger.getLogger(Measurement.class);
	
	protected Measurement(String id, Map<String,Tuple<Double,Double>> timeBean) {
		this.id=Id.create(id, Measurement.class);
		this.timeBean=timeBean;
		this.attributes.put(linkListAttributeName, new ArrayList<Id<Link>>());
	}
	
	public void addVolume(String timeBeanId,double volume) {
		
		if(!this.timeBean.containsKey(timeBeanId)){
			logger.error("timeBean do not contain timeBeanId"+timeBeanId+", please check.");
			logger.warn("Ignoring volume for timeBeanId"+timeBeanId);
		}else {
			this.volumes.put(timeBeanId, volume);
		}
	}
	
	/**
	 * Call to this method with this.linkListAttributeName String will 
	 * return a ArrayList<Id<Link>> containing the link Ids of all the links in this measurement
	 * @param attributeName
	 * @return
	 */
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
	
	/**
	 * Default implementation of updater.
	 * Should be overridden if necessary.
	 * @param linkVolumes
	 * is a Map<timeBeanId,Map<Id<Link>,Double>> containing link traffic volumes of all the time beans
	 */
	@SuppressWarnings("unchecked")
	public void updateMeasurement(Map<String,Map<Id<Link>,Double>>linkVolumes) {
		if(((ArrayList<Id<Link>>)this.attributes.get(linkListAttributeName)).isEmpty()) {
			logger.warn("MeasurementId: "+this.getId().toString()+" LinkList is empty!!! creating linkId from measurement ID");
			((ArrayList<Id<Link>>)this.attributes.get(linkListAttributeName)).add(Id.createLinkId(this.getId().toString()));
		}
		if(this.volumes.size()==0) {
			logger.warn("MeasurementId: "+this.getId().toString()+" Volume is empty!!! Updating volume for all time beans");
			for(String s: this.timeBean.keySet()) {
				if(linkVolumes.containsKey(s)) {
					this.volumes.put(s, 0.);
				}
			}
		}
		for(String s:volumes.keySet()) {
			double volume=0;
			for(Id<Link>linkId:((ArrayList<Id<Link>>)this.attributes.get(linkListAttributeName))) {
				try {
					if(linkVolumes.get(s)==null) {
						throw new IllegalArgumentException("linkVolumes does not contain volume information");
					}
					if(linkVolumes.get(s).get(linkId)==null) {
						throw new IllegalArgumentException("linkVolumes does not contain volume information");
					}
					volume+=linkVolumes.get(s).get(linkId);
				}catch(Exception e) {
					logger.error("Illegal Argument Excepton. Could not update measurements. Volumes are missing for measurement Id: "+this.getId()+" timeBeanId: "
							+s+" linkId: "+linkId);
				}
				
			}
			this.volumes.put(s, volume);
		}
	}
}
