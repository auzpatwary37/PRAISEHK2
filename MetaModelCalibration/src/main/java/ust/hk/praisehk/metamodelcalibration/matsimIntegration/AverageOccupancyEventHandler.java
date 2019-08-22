package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import com.google.inject.Inject;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class AverageOccupancyEventHandler implements TransitDriverStartsEventHandler,LinkEnterEventHandler, LinkLeaveEventHandler,
	PersonEntersVehicleEventHandler,PersonLeavesVehicleEventHandler{
	
	private Measurements outputMeasurements;
	@Inject
	private Scenario scenario;
	
	private Map<Id<Vehicle>,Integer> inVehiclePassengerCount=new ConcurrentHashMap<>();
	private Map<Id<Link>,Measurement> measurements=new ConcurrentHashMap<>();
	
	private final Map<String,Tuple<Double,Double>> timeBean;
	
	private Map<Id<Link>,Map<String,Double>> passengerVolume=new ConcurrentHashMap<>();
	private Map<Id<Link>,Map<String,Double>>vehicleCapacity=new ConcurrentHashMap<>();
	
	public AverageOccupancyEventHandler(Measurements outputMeasurements) {
		this.outputMeasurements=outputMeasurements;
		this.timeBean=this.outputMeasurements.getTimeBean();
		for(Measurement m:this.outputMeasurements.getMeasurementsByType().get(MeasurementType.averagePTOccumpancy)) {
			Id<Link>linkId=((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0);
			this.measurements.put(linkId,m);
			this.passengerVolume.put(linkId, new ConcurrentHashMap<>());
			this.vehicleCapacity.put(linkId, new ConcurrentHashMap<>());
			for(String timeId:m.getVolumes().keySet()) {
				this.passengerVolume.get(linkId).put(timeId, 0.);
				this.vehicleCapacity.get(linkId).put(timeId, 0.);
			}
		}
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		String timeId=this.getTimeId(event.getTime());
		if(this.inVehiclePassengerCount.containsKey(event.getVehicleId()) && this.measurements.containsKey(event.getLinkId())) {
			VehicleCapacity vc= scenario.getTransitVehicles().getVehicles().get(event.getVehicleId()).getType().getCapacity();
			Double oldCap=this.vehicleCapacity.get(event.getLinkId()).get(timeId);
			Double oldPassenger=this.passengerVolume.get(event.getLinkId()).get(timeId);
			if(oldCap!=null) {
				oldCap+=vc.getSeats()+vc.getStandingRoom();
				oldPassenger+=this.inVehiclePassengerCount.get(event.getVehicleId());
				this.vehicleCapacity.get(event.getLinkId()).put(timeId, oldCap);
				this.passengerVolume.get(event.getLinkId()).put(timeId, oldPassenger);
			}
		}
		
	}
	
	public void reset() {
		this.outputMeasurements.resetMeasurementsByType(MeasurementType.averagePTOccumpancy);
		this.measurements.clear();
		this.inVehiclePassengerCount.clear();
		this.passengerVolume.clear();
		this.vehicleCapacity.clear();
		for(Measurement m:this.outputMeasurements.getMeasurementsByType().get(MeasurementType.averagePTOccumpancy)) {
			Id<Link>linkId=((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0);
			this.measurements.put(linkId,m);
			this.passengerVolume.put(linkId, new ConcurrentHashMap<>());
			this.vehicleCapacity.put(linkId, new ConcurrentHashMap<>());
			for(String timeId:m.getVolumes().keySet()) {
				this.passengerVolume.get(linkId).put(timeId, 0.);
				this.vehicleCapacity.get(linkId).put(timeId, 0.);
			}
		}
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
				
	}
	

	public Measurements getOutputMeasurements() {
		for(Entry<Id<Link>,Measurement> measurement:this.measurements.entrySet()) {
			for(String timeId:measurement.getValue().getVolumes().keySet()) {
				if(this.vehicleCapacity.get(measurement.getKey()).get(timeId)!=0){
					measurement.getValue().addVolume(timeId, this.passengerVolume.get(measurement.getKey()).get(timeId)/this.vehicleCapacity.get(measurement.getKey()).get(timeId));
				}
			}
		}
		return outputMeasurements;
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		this.inVehiclePassengerCount.put(event.getVehicleId(), 0);
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		this.inVehiclePassengerCount.put(event.getVehicleId(), this.inVehiclePassengerCount.get(event.getVehicleId())-1);		
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		this.inVehiclePassengerCount.put(event.getVehicleId(), this.inVehiclePassengerCount.get(event.getVehicleId())+1);
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
}
