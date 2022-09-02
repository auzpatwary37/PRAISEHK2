package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;

import ust.hk.praisehk.metamodelcalibration.measurements.MTRLinkVolumeInfo;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class MTRPassengerFlowCounter implements TransitDriverStartsEventHandler, LinkEnterEventHandler, 
		PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, VehicleLeavesTrafficEventHandler {
	
	private Measurements m;
	private TransitSchedule ts;
	private Map<String,Tuple<Double,Double>>timeBean;
	private List<Id<Link>>links = new ArrayList<>();
	private Map<Id<Vehicle>, Double> passengerOnBoard = new HashMap<>();
	private Map<Id<Vehicle>,Tuple<Id<TransitLine>,Id<TransitRoute>>> vehicleToLineRouteMap = new HashMap<>();
	private Map<Id<Link>,Map<String,Measurement>> incidenceMap = new HashMap<>();//linkId->lineId___routeId->Measurement
	
	/**
	 * the measurement will not be cloned. 
	 * @param m
	 * @param ts
	 */
	public MTRPassengerFlowCounter(Measurements m, TransitSchedule ts) {
		this.m = m;
		this.timeBean = m.getTimeBean();
		this.ts = ts;
		for(Measurement mm:m.getMeasurementsByType().get(MeasurementType.TransitPhysicalLinkVolume)) {
			for(MTRLinkVolumeInfo info :(List<MTRLinkVolumeInfo>) mm.getAttribute(Measurement.MTRLineRouteStopLinkInfosName)) {
				links.add(info.linkId);
				if(!this.incidenceMap.containsKey(info.linkId))this.incidenceMap.put(info.linkId, new HashMap<>());
				this.incidenceMap.get(info.linkId).put(info.lineId.toString()+"___"+info.routeId, mm);
				
			};
			
		}
	}
	
	@Override
	public void reset(int iteration) {
		
		//Reset the measurement volume per iteration
		for(Measurement mm:m.getMeasurementsByType().get(MeasurementType.TransitPhysicalLinkVolume)) {
			for(String timeBeanId : timeBean.keySet()) {
				mm.putVolume(timeBeanId, 0);
			}
		}
	}
	
	@Override
	public void handleEvent(LinkEnterEvent event) {
		if(this.links.contains(event.getLinkId()) && this.passengerOnBoard.containsKey(event.getVehicleId())) {
			String timeId = null;
			double time = event.getTime();
			if(time>24*3600)time = time-24*3600;
			if(time==0)time=1;
			for(Entry<String, Tuple<Double, Double>> e:timeBean.entrySet()) {
				if(time<=e.getValue().getSecond() && time>e.getValue().getFirst())timeId = e.getKey();
			}
			if(timeId!=null) {
				Measurement mm = this.incidenceMap.get(event.getLinkId()).get(this.vehicleToLineRouteMap.get(event.getVehicleId()).getFirst().toString()+"___"+this.vehicleToLineRouteMap.get(event.getVehicleId()).getSecond().toString());
				mm.putVolume(timeId, mm.getVolume(timeId)+this.passengerOnBoard.get(event.getVehicleId()));
			}
		}
		
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		if(ts.getTransitLines().containsKey(event.getTransitLineId()) &&
				ts.getTransitLines().get(event.getTransitLineId()).getRoutes().get(event.getTransitRouteId()).getTransportMode().equals("train")) {
			this.passengerOnBoard.put(event.getVehicleId(), 0.);
			this.vehicleToLineRouteMap.put(event.getVehicleId(), new Tuple<>(event.getTransitLineId(),event.getTransitRouteId()));
		}
	
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if(this.passengerOnBoard.containsKey(event.getVehicleId()))this.passengerOnBoard.compute(event.getVehicleId(), (k,v)->v+1);
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if(this.passengerOnBoard.containsKey(event.getVehicleId()))this.passengerOnBoard.compute(event.getVehicleId(), (k,v)->v-1);
	}

	public Measurements getMeasurements() {
		return m;
	}

	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		this.passengerOnBoard.remove(event.getVehicleId());
		this.vehicleToLineRouteMap.remove(event.getVehicleId());
	}

}
