package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.vehicles.Vehicle;

import com.google.inject.Inject;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class TravelTimeEventHandler implements LinkEnterEventHandler, LinkLeaveEventHandler{
	@Inject
	private Scenario scenario;
	private Measurements outputMeasurements;
	private Map<Id<Link>,Map<String,List<Double>>>totalTime=new ConcurrentHashMap<>();
	//private Map<Id<Link>,Map<String,Double>>totalVehicle=new ConcurrentHashMap<>();
	private Map<Id<Link>,Map<Id<Vehicle>,Double>> vehicleBuffer=new ConcurrentHashMap<>();
	private Map<Id<Link>,Measurement> measurements=new ConcurrentHashMap<>();
	private Map<String,Tuple<Double,Double>>timeBean;
	
	public TravelTimeEventHandler(Measurements outputMeasurement) {
		this.outputMeasurements=outputMeasurement;
		this.timeBean=this.outputMeasurements.getTimeBean();
		for(Measurement m:this.outputMeasurements.getMeasurementsByType().get(MeasurementType.linkTravelTime)) {
			Id<Link> linkId=((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0);
			measurements.put(linkId, m);
			this.vehicleBuffer.put(linkId, new ConcurrentHashMap<>());
			//this.totalVehicle.put(linkId, new ConcurrentHashMap<>());
			this.totalTime.put(linkId, new ConcurrentHashMap<>());
			for(String timeId:m.getVolumes().keySet()) {
				this.totalTime.get(linkId).put(timeId, Collections.synchronizedList(new ArrayList<Double>()));
				//this.totalVehicle.get(linkId).put(timeId, 0.);
			}
		}
	}
	
	public Measurements getUpdatedMeasurements() {
		for(Entry<Id<Link>, Measurement> m:this.measurements.entrySet()) {
			for(String timeId:m.getValue().getVolumes().keySet()) {
				if(this.totalTime.get(m.getKey()).get(timeId).size()!=0) {
					m.getValue().putVolume(timeId, calcAverage(this.totalTime.get(m.getKey()).get(timeId)));
				}else {
					Link link=scenario.getNetwork().getLinks().get(m.getKey());
					m.getValue().putVolume(timeId, link.getLength()/link.getFreespeed());
				}
			}
		}
		return this.outputMeasurements;
	}
	
	private Double calcAverage(List<Double> list) {
		
		double sum=0;
		double num=list.size();
		for(Double d:list) {
			sum+=d;
		}
		if(num==0) {
			return 0.;
		}
		return sum/num;
	}
	
	@Override
	public void handleEvent(LinkLeaveEvent event) {
		Id<Link>linkId=event.getLinkId();
		if(this.vehicleBuffer.containsKey(linkId) && this.vehicleBuffer.get(linkId).containsKey(event.getVehicleId())) {
			double timeLength=event.getTime()-this.vehicleBuffer.get(linkId).get(event.getVehicleId());
			double middleTime=event.getTime()-timeLength/2;
			String timeId=this.getTimeId(middleTime);
			if(timeId!=null && this.totalTime.get(linkId).get(timeId)!=null) {
				this.totalTime.get(linkId).get(timeId).add(timeLength);
				//this.totalVehicle.get(linkId).put(timeId, this.totalVehicle.get(linkId).get(timeId)+1);
			}
			this.vehicleBuffer.get(linkId).remove(event.getVehicleId());
		}
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		if(this.vehicleBuffer.containsKey(event.getLinkId())){
			this.vehicleBuffer.get(event.getLinkId()).put(event.getVehicleId(), event.getTime());
		}
	}
	
	private String getTimeId(double time) {
		if(time==0) {
			time=1;
		}else if(time>24*3600) {
			time=time-24*3600;
		}
		for(Entry<String, Tuple<Double, Double>> timeBean:this.timeBean.entrySet()) {
			if(time>timeBean.getValue().getFirst() && time<=timeBean.getValue().getSecond()) {
				return timeBean.getKey();
			}
		}
		return null;
	}

	public void reset() {
		this.outputMeasurements.resetMeasurementsByType(MeasurementType.linkTravelTime);
		for(Measurement m:this.outputMeasurements.getMeasurementsByType().get(MeasurementType.linkTravelTime)) {
			Id<Link> linkId=((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0);
			measurements.put(linkId, m);
			this.vehicleBuffer.put(linkId, new ConcurrentHashMap<>());
			//this.totalVehicle.put(linkId, new ConcurrentHashMap<>());
			this.totalTime.put(linkId, new ConcurrentHashMap<>());
			for(String timeId:m.getVolumes().keySet()) {
				this.totalTime.get(linkId).put(timeId, Collections.synchronizedList(new ArrayList<>()));
			//	this.totalVehicle.get(linkId).put(timeId, 0.);
			}
		}
	}
}
