package transitFareAndHandler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import javax.inject.Inject;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonScoreEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.MatsimServices;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.Vehicles;


public class ComfortHandler implements VehicleArrivesAtFacilityEventHandler, VehicleDepartsAtFacilityEventHandler,
		PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, TransitDriverStartsEventHandler,
		VehicleLeavesTrafficEventHandler{
	
	private final EventsManager events;
	private final TransitSchedule ts;
	private final Vehicles transitVehicles;
	
	private Map<Id<Vehicle>, VehicleInfo> operatingVehicle = new HashMap<>();
	private final Map<Id<Person>, Person> personMap;
	private final Map<String, Double> subpopUtilityStanding_s = new HashMap<>();
	
	@Inject
	ComfortHandler(final MatsimServices controler, final Scenario scenario) {
		this.events = controler.getEvents();
		this.ts = scenario.getTransitSchedule();
		this.transitVehicles = scenario.getTransitVehicles();
		this.personMap = (Map<Id<Person>, Person>) scenario.getPopulation().getPersons();
		
		PlanCalcScoreConfigGroup cG = (PlanCalcScoreConfigGroup) scenario.getConfig().getModules().get("planCalcScore");
		for(var subpop: cG.getScoringParametersPerSubpopulation().entrySet()) {
			subpopUtilityStanding_s.put(subpop.getKey(), subpop.getValue().getOrCreateModeParams("standing").getMarginalUtilityOfTraveling()/3600);
		}
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if(operatingVehicle.containsKey(event.getVehicleId())) {
			operatingVehicle.get(event.getVehicleId()).passengerLeft(event.getPersonId(), event.getTime());
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if(operatingVehicle.containsKey(event.getVehicleId())) {
			operatingVehicle.get(event.getVehicleId()).passengerEnter(event.getPersonId(), event.getTime());
		}
	}

	@Override
	public void handleEvent(VehicleDepartsAtFacilityEvent event) {
		operatingVehicle.get(event.getVehicleId()).currentStop = event.getFacilityId();
	}

	@Override
	public void handleEvent(VehicleArrivesAtFacilityEvent event) {
		operatingVehicle.get(event.getVehicleId()).currentStop = null;
	}
	
	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		operatingVehicle.remove(event.getVehicleId()); //Remove to ensure the vehicles is not overused.
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		VehicleInfo vi = new VehicleInfo(event.getVehicleId());
		operatingVehicle.put(event.getVehicleId(), vi);
	}
	
	private class VehicleInfo{
		private final int standing;
		private final int seating;
		private Map<Id<Person>, PassengerInfo> seatingPassengers = new HashMap<>();
		private Queue<PassengerInfo> standingPassengerQueue = new LinkedList<>();
		private Map<Id<Person>, PassengerInfo> allPassengers = new HashMap<>();
		private Id<TransitStopFacility> currentStop;
		
		private VehicleInfo(Id<Vehicle> vId) {
			VehicleCapacity vc = transitVehicles.getVehicles().get(vId).getType().getCapacity();
			this.standing = vc.getStandingRoom();
			this.seating = vc.getSeats();
		}
		
		/**
		 * If a passenger is left, deduct the standing score given by time
		 * @param pId
		 * @param currTime
		 */
		private void passengerLeft(Id<Person> pId, double currTime) {
			if(personMap.containsKey(pId)) {
				PassengerInfo piToRemove = allPassengers.remove(pId);
				double standingFactor = standingPassengerQueue.size()/(this.standing+this.seating)>=0.8?1.55:1;
				if(seatingPassengers.containsKey(pId)) {
					seatingPassengers.remove(pId);
					
					//Replace it with a standing one
					if(standingPassengerQueue.size() > 0) {
						PassengerInfo passToSit = standingPassengerQueue.poll();
						seatingPassengers.put(passToSit.personId, passToSit);
						
						//XXX: Known issue: This deduction is inconsistent with the route calculator
						events.processEvent(new PersonScoreEvent(currTime, passToSit.personId, 
								(currTime - passToSit.startStandingTime) * passToSit.utilityStanding_s * standingFactor, "Standing from "+passToSit.startStandingTime+" to "+currTime));
						passToSit.isStanding = false;
					}
				}else {
					standingPassengerQueue.remove(piToRemove);
					events.processEvent(new PersonScoreEvent(currTime, pId, 
							(currTime - piToRemove.startStandingTime) * piToRemove.utilityStanding_s * standingFactor, "Standing from "+piToRemove.startStandingTime+" to "+currTime));
				
				}
			}
		}
		
		private void passengerEnter(Id<Person> pId, double time) {
			if(personMap.containsKey(pId)) { //The drivers are omitted
				PassengerInfo pi = new PassengerInfo(pId);
				allPassengers.put(pId, pi);
				if(standingPassengerQueue.size() > 0 || allPassengers.size() > seating) {
					pi.startStandingTime = time;
					pi.isStanding = true;
					standingPassengerQueue.add(pi);
				}else {
					seatingPassengers.put(pId, pi);
				}
			}
		}
		
	}
	
	private class PassengerInfo{
		private double startStandingTime = -1;
		private boolean isStanding;
		private final Id<Person> personId;
		
		private final double utilityStanding_s;
		
		private PassengerInfo(Id<Person> personId) {
			this.personId = personId;
			String subpop = (String) personMap.get(personId).getAttributes().getAttribute("subpopulation");
			this.utilityStanding_s = subpopUtilityStanding_s.get(subpop);
		}
		
		@Override
		public int hashCode() {
			return personId.hashCode();
		}
	}

}
