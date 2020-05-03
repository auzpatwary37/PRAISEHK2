package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

import com.google.inject.name.Named;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

/**
 * This fare handler is for handling the charge of the agents throughout the
 * simulation.
 * 
 * @author eleead
 *
 */
public class SmartCardEntryAndExitEventHandler
		implements TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler,
		VehicleArrivesAtFacilityEventHandler, VehicleDepartsAtFacilityEventHandler, ActivityStartEventHandler {

	private final Map<Id<Vehicle>, VehicleData> vehicleData = new HashMap<>();
	private final Map<Id<Vehicle>, Id<TransitStopFacility>> vehicleAtFacility = new HashMap<>();
	private final Map<Id<Person>, TripsDataForFareHandler> peopleTripData = new HashMap<>();

	
	
	private Scenario scenario;
	private Map<Id<TransitLine>, TransitLine> transitLines;
	
	private Measurements templateMeasurements;
	private Map<String,Measurement> smartCardEntry=new HashMap<>();
	//the first string is the key of the measurement, key=startStopId+"___"+"TranistLineId"+"___"+"RouteId" 
	//(line and route id is not present for mode = "train") 
			
	private Map<String,Measurement> smartCardEntryAndExit=new HashMap<>();
	//the first string is the key of the measurement, key=startStopId+"___"+endStopId+"___"+"TranistLineId"+"___"+"RouteId" 
	//(line and route id is not present for mode = "train") 
	
	private final static Logger log = Logger.getLogger(SmartCardEntryAndExitEventHandler.class);
	@Inject
	private MeasurementsStorage storage; 

	@Inject
	public SmartCardEntryAndExitEventHandler(@Named("Output Measurements") Measurements outputMeasurements,Scenario scneario) {
		this.scenario=scneario;
		this.templateMeasurements=outputMeasurements;
		//for counting  please see here
		if(this.templateMeasurements==null) {
			this.templateMeasurements=storage.getCalibrationMeasurements().clone();
		}
		for(Measurement m:this.templateMeasurements.getMeasurementsByType().get(MeasurementType.smartCardEntry)) {
			String key=null;
			String mode=m.getAttribute(Measurement.transitModeAttributeName).toString();
			if(mode.equals("train")) {
				key=m.getAttribute(Measurement.transitBoardingStopAtrributeName).toString();
			}else {
				key=m.getAttribute(Measurement.transitBoardingStopAtrributeName).toString()+"___"
						+m.getAttribute(Measurement.transitLineAttributeName)+"___"+m.getAttribute(Measurement.transitRouteAttributeName);
			}
			this.smartCardEntry.put(key, m);
		}
		for(Measurement m:this.templateMeasurements.getMeasurementsByType().get(MeasurementType.smartCardEntryAndExit)) {
			String key=null;
			String mode=m.getAttribute(Measurement.transitModeAttributeName).toString();
			if(mode.equals("train")) {
				key=m.getAttribute(Measurement.transitBoardingStopAtrributeName).toString()+"___"+m.getAttribute(Measurement.transitAlightingStopAttributeName).toString();
			}else {
				key=m.getAttribute(Measurement.transitBoardingStopAtrributeName).toString()+"___"+m.getAttribute(Measurement.transitAlightingStopAttributeName).toString()+"___"
						+m.getAttribute(Measurement.transitLineAttributeName)+"___"+m.getAttribute(Measurement.transitRouteAttributeName);
			}
			this.smartCardEntryAndExit.put(key, m);
		}
		this.transitLines=this.scenario.getTransitSchedule().getTransitLines();
		//--------------------------------------
	}
	
	public Measurements getUpdatedMeasurements() {
		return templateMeasurements;
	}

	@Override
	public void reset(int iteration) {
		vehicleData.clear();
		vehicleAtFacility.clear();
		peopleTripData.clear();
		
		//Reset the fare collected

		log.info("Resetting the data");
		
		//---------------------------
		this.templateMeasurements.resetMeasurementsByType(MeasurementType.smartCardEntry);
		this.templateMeasurements.resetMeasurementsByType(MeasurementType.smartCardEntryAndExit);
		//-----------------------------------
	}

	/**
	 * This handler is to store where an agent aboard the public transport
	 */
	@Override
	public void handleEvent(VehicleDepartsAtFacilityEvent event) {
		vehicleAtFacility.remove(event.getVehicleId()); // Not sure if a debug is needed.
	}

	/**
	 * This handler is to get where the agent alight from the transit vehicle
	 */
	@Override
	public void handleEvent(VehicleArrivesAtFacilityEvent event) {
		vehicleAtFacility.put(event.getVehicleId(), event.getFacilityId()); // Store the facility arrived
	}

	/**
	 * This handler is to capture the event that agent alight from the transit
	 * vehicle.
	 */
	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		Id<Vehicle> vehicleId = event.getVehicleId();
		if (vehicleData.containsKey(vehicleId)) {
			Id<TransitStopFacility> stopAt = vehicleAtFacility.get(vehicleId); // Obtain the stop facility stored
			Id<Person> pid = event.getPersonId();
			if (pid.toString().substring(0, 3).equals("pt_")) { // Get rid of the dummy driver.
				return;
			}
			TripsDataForFareHandler tripsData = peopleTripData.get(pid);
			VehicleData vData = vehicleData.get(vehicleId);

			// Get the fare calculator correspond to the mode calculator.
			// Obtain the transport mode
			String transportMode = this.transitLines.get(vData.lineId).getRoutes().get(vData.routeId)
					.getTransportMode();

			if (!transportMode.equals("train")) {
				//Record the entry and entry and exit
				String key=tripsData.getLatestStartFacility().toString()+"___"+vData.lineId.toString()+"___"+vData.routeId.toString();
				String timeId=this.getTimeBeanId(tripsData.getLastStartTime(false));
				if(this.smartCardEntry.get(key)!=null && timeId!=null) {
				Double oldVolume=this.smartCardEntry.get(key).getVolumes().get(timeId);
					if(oldVolume!=null) {
						this.smartCardEntry.get(key).putVolume(timeId, oldVolume+1);
					}
				}
				String key1=tripsData.getLatestStartFacility().toString()+"___"+stopAt+"___"+vData.lineId.toString()+"___"+vData.routeId.toString();
				if(this.smartCardEntryAndExit.get(key1)!=null) {
					Double oldVolume=this.smartCardEntryAndExit.get(key1).getVolumes().get(timeId);
					if(oldVolume!=null) {
						this.smartCardEntryAndExit.get(key1).putVolume(timeId, oldVolume+1);
					}
				}
				//----------------------------------------------
				
			}
			tripsData.endTrip(vData.routeId, vData.lineId, stopAt, event.getTime()); // Ends the trip in the iteration.
		}
	}

	/**
	 * This handler is to capture the event that agent alight from the transit
	 * vehicle.
	 */
	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		Id<Vehicle> vehicleId = event.getVehicleId();
		if (vehicleData.containsKey(vehicleId)) { // Only if it is a transit vehicle
			Id<TransitStopFacility> stopAt = vehicleAtFacility.get(vehicleId); // Obtain the stop facility stored
			Id<Person> pid = event.getPersonId();
			if (pid.toString().substring(0, 3).equals("pt_")) { // Get rid of the dummy driver.
				return;
			}
			TripsDataForFareHandler tripsData; // See if the trip is in or not.
			if (peopleTripData.containsKey(pid)) {
				tripsData = peopleTripData.get(pid);
			} else {
				tripsData = new TripsDataForFareHandler(pid);
			}

			VehicleData vData = vehicleData.get(vehicleId);

			String transportMode = this.transitLines.get(vData.lineId).getRoutes().get(vData.routeId)
					.getTransportMode();
			tripsData.startTrip(vData.routeId, vData.lineId, stopAt, transportMode, event.getTime());
			peopleTripData.put(pid, tripsData);
			// System.out.println("Agent "+pid+" boarded "+vData.routeId+" Line
			// "+vData.lineId+" at "+stopAt);
		}

	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		String actType = event.getActType();
		if (!actType.equals("pt interaction")) {
			if (peopleTripData.containsKey(event.getPersonId())) {
				// Leave MTR if it is still in
				peopleTripData.get(event.getPersonId()).leaveAndChargeForMTR(event.getTime());
			}
		}
		// System.out.println("The activity type is :"+actType);
	}

	@Override
	/**
	 * This function is for recording which is the valid transit vehicle
	 */
	public void handleEvent(TransitDriverStartsEvent event) {
		// This method allow only one vehicle Data for each vehicle ID.
		vehicleData.put(event.getVehicleId(), new VehicleData(event.getVehicleId(), event.getTransitLineId(),
				event.getTransitRouteId(), event.getDepartureId()));
	}
	
	
	
	//-------------------------------------------
	private String getTimeBeanId(Double time) {
		if(time>24*3600) {
			time=time-24*3600;
		}else if(time==0.) {
			time=1.;
		}
		for(Entry<String, Tuple<Double, Double>> timeBean: this.templateMeasurements.getTimeBean().entrySet()) {
			if(time>timeBean.getValue().getFirst() && time<=timeBean.getValue().getSecond()) {
				return timeBean.getKey();
			}
		}
		return null;
	}
	//--------------------------------------------
	
	private class TripsDataForFareHandler {
		private List<TripData> trips;
		private Id<TransitStopFacility> lastEntryStation;
		private boolean inPaidArea;
		private TripsDataForFareHandler(final Id<Person> personId) {
			this.trips = new LinkedList<TripData>();
		}

		/**
		 * A helper function to detect the MTR.
		 * 
		 * @param time
		 */
		public void leaveAndChargeForMTR(double time) {
			if (inPaidArea == true) { // You can only leave if it is in the MTR.
				TripData lastMTRTrip = trips.get(trips.size() - 1); // It would be the second last.
				//Save the 'entry' and 'entry and exit'................
				String key=this.lastEntryStation.toString()+"___"+lastMTRTrip.endFacilityId.toString();
				String timeId=SmartCardEntryAndExitEventHandler.this.getTimeBeanId(time);
				
				if(SmartCardEntryAndExitEventHandler.this.smartCardEntryAndExit.containsKey(key) && timeId!=null) {
				Double oldVolume=SmartCardEntryAndExitEventHandler.this.smartCardEntryAndExit.get(key).getVolumes().get(timeId);
				if(oldVolume!=null) {
					SmartCardEntryAndExitEventHandler.this.smartCardEntryAndExit.get(key).putVolume(timeId, oldVolume+1);
				}
				}
				String key1=this.lastEntryStation.toString();
				if(SmartCardEntryAndExitEventHandler.this.smartCardEntry.get(key1)!=null) {
				Double oldVolume1=SmartCardEntryAndExitEventHandler.this.smartCardEntry.get(key1).getVolumes().get(timeId);
				if(oldVolume1!=null) {
					SmartCardEntryAndExitEventHandler.this.smartCardEntry.get(key1).putVolume(timeId, oldVolume1+1);
				}
				}
				//.............................
				lastEntryStation = null;
				inPaidArea = false; // Leaving the MTR;
			}
		}
		
		public double getLastStartTime(boolean thisTripStarted) {
			if(thisTripStarted) {
				return trips.get(trips.size()-2).startTime;
			}
			return trips.get(trips.size()-1).startTime;
		}
		
		public void startTrip(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> startFacilityId,
				String transportMode, double time) {
			if (transportMode.equals("train") && !inPaidArea) { // Record for the entrance
				lastEntryStation = startFacilityId;
				inPaidArea = true;
			} else if (!transportMode.equals("train") && inPaidArea) {
				leaveAndChargeForMTR(time);
			}
			trips.add(new TripData(lineId, routeId, startFacilityId, time));
		}

		public void endTrip(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> endFacilityId,
				double endTime) {
			TripData toBeEdited = trips.get(trips.size() - 1);
			if (toBeEdited.getRouteId().equals(routeId) && toBeEdited.getLineId().equals(lineId)) {
				toBeEdited.tripEnd(endFacilityId, endTime);
			} else {
				throw new IllegalArgumentException("The route ID or the line ID is not consistent!");
			}
		}

		public Id<TransitStopFacility> getLatestStartFacility() {
			return trips.get(trips.size() - 1).getStartFacilityId();
		}
	}

	private class TripData {
		private final Id<TransitLine> lineId;
		private final Id<TransitRoute> routeId;
		private final Id<TransitStopFacility> startFacilityId;
		private Id<TransitStopFacility> endFacilityId;
		private double startTime;

		private TripData(final Id<TransitLine> lineId, final Id<TransitRoute> routeId,
				final Id<TransitStopFacility> startFacilityId, double startTime) {
			if (lineId == null || routeId == null || startFacilityId == null) {
				throw new IllegalArgumentException("The input parameters cannot be null.");
			}
			this.startTime = startTime;
			this.lineId = lineId;
			this.routeId = routeId;
			this.startFacilityId = startFacilityId;
		}

		public void tripEnd(Id<TransitStopFacility> endFacilityId, double endTime) {
			this.endFacilityId = endFacilityId;
		}

		public Id<TransitLine> getLineId() {
			return lineId;
		}

		public Id<TransitRoute> getRouteId() {
			return routeId;
		}

		public Id<TransitStopFacility> getStartFacilityId() {
			return startFacilityId;
		}
		
	}

	private class VehicleData {
		private final Id<TransitLine> lineId;
		private final Id<TransitRoute> routeId;
		private VehicleData(final Id<Vehicle> vehicleId, final Id<TransitLine> lineId, final Id<TransitRoute> routeId,
				final Id<Departure> departureId) {
			this.lineId = lineId;
			this.routeId = routeId;
		}
	}

}
