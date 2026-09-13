package transitFareAndHandler;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

public class MTRFlowEventHandler implements TransitDriverStartsEventHandler, 
		VehicleDepartsAtFacilityEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler{
	
	Map<Id<TransitLine>, TransitLine> trainTransitLines = new HashMap<>();
	Map<Id<Vehicle>, VehicleData> vehicleData = new HashMap<>();
	Map<Id<TransitRoute>, RouteData> routeData = new HashMap<>();
	double fromTime;
	double toTime;
	private final Vehicles tv; //TransitVehicles
	
	public MTRFlowEventHandler(TransitSchedule ts, Vehicles tv, double fromTime, double toTime) {
		for(TransitLine tl: ts.getTransitLines().values()) {
			for(TransitRoute tr: tl.getRoutes().values()) {
				if(tr.getTransportMode().equals("train")) {
					trainTransitLines.put(tl.getId(), tl);
					routeData.put(tr.getId(), new RouteData(tr.getId(), tr));
				}
			}
		}
		this.fromTime = fromTime;
		this.toTime = toTime;
		this.tv = tv;
	}
	
	public Map<Id<TransitLine>, Map<Id<TransitStopFacility>, Double>> processLineFlow() {
		Map<Id<TransitLine>, Map<Id<TransitStopFacility>, Double>> lineStopFlow = new HashMap<>();
		for(TransitLine tl: trainTransitLines.values()) {
			Map<Id<TransitStopFacility>, Double> stopFlow = new HashMap<>();
			for(TransitRoute tr: tl.getRoutes().values()) {
				for(var entry: routeData.get(tr.getId()).tsFCounts.entrySet()) {
					if(stopFlow.containsKey(entry.getKey())) {
						stopFlow.put(entry.getKey(), stopFlow.get(entry.getKey()) + entry.getValue());
					}else {
						stopFlow.put(entry.getKey(), entry.getValue());
					}
				}
			}
			lineStopFlow.put(tl.getId(), stopFlow);
		}
		return lineStopFlow;
	}
	
	public Map<Id<TransitLine>, Map<Id<TransitStopFacility>, Double>> processCap() {
		Map<Id<TransitLine>, Map<Id<TransitStopFacility>, Double>> lineStopCap = new HashMap<>();
		for(TransitLine tl: trainTransitLines.values()) {
			Map<Id<TransitStopFacility>, Double> stopFlow = new HashMap<>();
			for(TransitRoute tr: tl.getRoutes().values()) {
				for(var entry: routeData.get(tr.getId()).tsFcapacity.entrySet()) {
					if(stopFlow.containsKey(entry.getKey())) {
						stopFlow.put(entry.getKey(), stopFlow.get(entry.getKey()) + entry.getValue());
					}else {
						stopFlow.put(entry.getKey(), entry.getValue());
					}
				}
			}
			lineStopCap.put(tl.getId(), stopFlow);
		}
		return lineStopCap;
	}
	
	@Override
	public void reset(int iteration) {
		//Reset the route data
		for(RouteData rd: routeData.values()) {
			rd.resetCount();
		}
	}
	
	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		Id<TransitLine> tlId = event.getTransitLineId();
		if(trainTransitLines.containsKey(tlId)) {
			VehicleType vt = tv.getVehicles().get(event.getVehicleId()).getType();
			int seats = vt.getCapacity().getSeats();
			int standing = vt.getCapacity().getStandingRoom();
			vehicleData.put(event.getVehicleId(), new VehicleData(event.getVehicleId(),
					event.getTransitLineId(), event.getTransitRouteId(), event.getDepartureId(),
					event.getDriverId(), standing, seats));
		}
	}
	
	@Override
	public void handleEvent(VehicleDepartsAtFacilityEvent event) {
		if(event.getTime() > fromTime && event.getTime() < toTime) {
			Id<Vehicle> vId = event.getVehicleId();
			if(vehicleData.containsKey(vId)) {
				VehicleData vd = vehicleData.get(event.getVehicleId());
				routeData.get(vd.routeId).addCount(event.getFacilityId(), vd.passengerOnboard - 1, 
						vd.seats + vd.standing);
			}
		}
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if(vehicleData.containsKey(event.getVehicleId())) {
			VehicleData vd = vehicleData.get(event.getVehicleId());
			vd.passengerOnboard--;
		}	
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if(vehicleData.containsKey(event.getVehicleId())) {
			VehicleData vd = vehicleData.get(event.getVehicleId());
			vd.passengerOnboard++;
		}
	}
	
	public double getCount(Id<TransitRoute> routeId, Id<TransitStopFacility> tsFId) {
		return routeData.get(routeId).tsFCounts.get(tsFId);
	}
	
	private class VehicleData{
		public final Id<Vehicle> vehicleId;
		public final Id<TransitLine> lineId;
		public final Id<TransitRoute> routeId;
		public final Id<Departure> departureId;
		public final Id<Person> driverId;
		public int passengerOnboard;
		
		private final int standing;
		private final int seats;

		public VehicleData(final Id<Vehicle> vehicleId, final Id<TransitLine> lineId, 
				final Id<TransitRoute> routeId, final Id<Departure> departureId, final Id<Person> driverId, 
				int standing, int seats) {
			this.vehicleId = vehicleId;
			this.lineId = lineId;
			this.routeId = routeId;
			this.departureId = departureId;
			this.driverId = driverId;
			this.passengerOnboard = 0;
			
			this.standing = standing;
			this.seats = seats;
		}
	}
	
	private class RouteData{
		public final Id<TransitRoute> routeId;
		public final Map<Id<TransitStopFacility>, Double> tsFCounts;
		public final Map<Id<TransitStopFacility>, Double> tsFcapacity;
		
		public RouteData(final Id<TransitRoute> routeId, TransitRoute tr) {
			this.routeId = routeId;
			this.tsFCounts = new HashMap<>();
			this.tsFcapacity = new HashMap<>();
			
			for(TransitRouteStop trs: tr.getStops()) {
				tsFCounts.put(trs.getStopFacility().getId(), 0.);
				tsFcapacity.put(trs.getStopFacility().getId(), 0.);
			}
		}
		
		public void resetCount() {
			for(Id<TransitStopFacility> tsfid: tsFCounts.keySet()) {
				tsFCounts.put(tsfid, 0.);
			}
		}
		
		public void addCount(Id<TransitStopFacility> tsfId, double count, double capacity) {
			tsFCounts.put(tsfId, tsFCounts.get(tsfId) + count);
			tsFcapacity.put(tsfId, tsFcapacity.get(tsfId) + capacity);
		}
	}	
}