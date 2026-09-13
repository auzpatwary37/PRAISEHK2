package dynamicTransitRouter.costs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.AgentWaitingForPtEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.core.utils.misc.OptionalTimes;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Sets;

import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.TransitStop;
import dynamicTransitRouter.TripsData;

@Singleton
/**
 * It is the class for handling the public transport record.
 * @author eleead
 *
 */
public class PTRecordHandler
		implements TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler,
		PersonLeavesVehicleEventHandler, AgentWaitingForPtEventHandler, VehicleArrivesAtFacilityEventHandler,
		VehicleDepartsAtFacilityEventHandler, StopStopTime, WaitingTime, VehicleOccupancy {

	private Vehicles transitVehicles;
	// private final EventsManager events; //Seems not need if we don't charge them.

	private Map<TransitLineRoute, Map<TransitStop, ArrivalInfo>> arriveMap;
	private Map<TransitLineRoute, Map<TransitStop, short[]>> queueLength;
	private Map<Id<Vehicle>, VehicleInfo> vehicleInfoCache;
	private final Map<Id<Person>, TripsData> peopleTripData = new HashMap<>();
	private final Set<Id<Person>> peopleStillQueueing = new HashSet<>();

	private int stepSizeInSecond;
	private int numOfBins;
	private final double simulationStartTime;
	private final double simulationEndTime;

	private final Map<Id<TransitLine>, TransitLine> transitLines;
	private final Map<Id<TransitStopFacility>, TransitStopFacility> stops;
	
	private final Map<Id<TransitStopFacility>, Set<TransitLineRoute>> lineAndRoutesAtStop;
	//boolean processedQueue; //If it is false, then the residual queue would need to be checked.
	private final static Logger log = Logger.getLogger(PTRecordHandler.class);

	@Inject
	public PTRecordHandler(final MatsimServices controler) {
		this(controler.getScenario());
	}
	
	private void initializeLineAndRouteAtStop() {
		for(TransitLine tl: transitLines.values()) {
			for(TransitRoute tr: tl.getRoutes().values()) {
				TransitLineRoute tlr = new TransitLineRoute(tl, tr);
				for(TransitRouteStop transitRouteStop: tr.getStops()) {
					Id<TransitStopFacility> tsfId = transitRouteStop.getStopFacility().getId();
					if(lineAndRoutesAtStop.containsKey(tsfId)) {
						lineAndRoutesAtStop.get(tsfId).add(tlr);
					}else {
						lineAndRoutesAtStop.put(tsfId, Sets.newHashSet(tlr));
					}
				}
			}
		}
	}
	
	private void initializeArrivalMapAndQueueLength(TransitSchedule ts) {
		log.info("Starting initializationfor the PT record.");
		int lineProcessed = 0;
		for (TransitLine tl : ts.getTransitLines().values()) {
			if (lineProcessed % 50 == 0) {
				log.info("Finishing the line initialization for " + lineProcessed + " lines.");
			}
			lineProcessed++;
			for (TransitRoute tr : tl.getRoutes().values()) {
				// Initialize the inner map
				Map<TransitStop, ArrivalInfo> arrivalInfoMap = new HashMap<>();
				Map<TransitStop, short[]> queueInfoMap = new HashMap<>();

				// The information to initialize the arrivalInfo or so.
				ArrivalInfo lastStopArrivalInfo = null;
				double lastStopArrivalTime = 0;
				for (TransitRouteStop trs : tr.getStops()) {
					int occ = 0;
					for (TransitStop stop : arrivalInfoMap.keySet()) {
						if (stop.getFacilityId().toString().replace("BT_", "").replace("bus_", "").
								equals(trs.getStopFacility().getId().toString().replace("BT_", "").replace("bus_", ""))) {
							occ++; // Obtain the occurrence before
						}
					}
					double arrivalTime = trs.getArrivalOffset().seconds();

					TransitStop stop = new TransitStop(trs, occ);
					ArrivalInfo initiateArrivalInfo = new ArrivalInfo(stop, tr.getDepartures().size());
					if (lastStopArrivalInfo == null) {
						initiateArrivalInfo.initializeFirstStop(tr.getDepartures());
					} else {
						for (int i = 0; i < lastStopArrivalInfo.seatAvailableAtStop.size(); i++) {
							initiateArrivalInfo.addArrival(lastStopArrivalInfo.departureIdList.get(i),
									lastStopArrivalInfo.arrivalTimeList.get(i) - lastStopArrivalTime + arrivalTime,
									lastStopArrivalInfo.seatAvailableAtStop.get(i),
									lastStopArrivalInfo.standingAvailableAtStop.get(i));
						}
					}

					arrivalInfoMap.put(stop, initiateArrivalInfo);
					queueInfoMap.put(stop, new short[numOfBins]);

					lastStopArrivalInfo = initiateArrivalInfo;
					lastStopArrivalTime = arrivalTime;
				}
				arriveMap.put(new TransitLineRoute(tl, tr), arrivalInfoMap);
				queueLength.put(new TransitLineRoute(tl, tr), queueInfoMap);
			}
		}
	}
	
	/**
	 * Add one person to the queue specified
	 * @param queue
	 * @param startTime Start time
	 * @param endTime End time
	 */
	private void addOnePersonToQueue(short[] queue, double startTime, double endTime) {
		int startBin = (int) ((startTime - this.simulationStartTime) / this.stepSizeInSecond) + 1;
		int endBin = (int) ((endTime - this.simulationStartTime) / this.stepSizeInSecond);
		for (int i = startBin; i < endBin; i++) { // Update the queue
			queue[i] += 1;
		}
	}

	public PTRecordHandler(Scenario scenario) {
		this.transitVehicles = scenario.getTransitVehicles();
		this.transitLines = scenario.getTransitSchedule().getTransitLines();
		this.stops = scenario.getTransitSchedule().getFacilities();
		this.lineAndRoutesAtStop = new HashMap<>();
		initializeLineAndRouteAtStop();
		
		this.simulationStartTime = scenario.getConfig().qsim().getStartTime().seconds();
		OptionalTime endTime = scenario.getConfig().qsim().getEndTime();
		if(endTime.isDefined()) {
			this.simulationEndTime = scenario.getConfig().qsim().getEndTime().seconds();
		}else {
			this.simulationEndTime = 30 * 3600;
		}
		this.stepSizeInSecond = scenario.getConfig().travelTimeCalculator().getTraveltimeBinSize();
		if(scenario.getConfig().qsim().getStartTime().seconds()==Double.NEGATIVE_INFINITY || 
				simulationEndTime==Double.NEGATIVE_INFINITY) {
			throw new IllegalArgumentException("The start time or end time in the scneario cannot be undefined!");
		}
		this.numOfBins = (int) ((this.simulationEndTime - this.simulationStartTime) / this.stepSizeInSecond + 1);
		
		TransitRouterFareDynamicImpl.initializeDirectWalkCount(); //Initialize the direct walk count.

		// Initialize the arrivalMap and queueLength
		arriveMap = new HashMap<>();
		queueLength = new HashMap<>();
		initializeArrivalMapAndQueueLength(scenario.getTransitSchedule());

		// Initialize the vehicleMap
		vehicleInfoCache = new HashMap<>();
	}

	public double getArrivalMapSize() {
		return arriveMap.size();
	}
	
	/**
	 * Process the queue for those agents who cannot board a vehicle.
	 */
	public synchronized void processResidualQueue() {
		int count = 0;
		while( !peopleStillQueueing.isEmpty() ) {
			for(Id<Person> personId: peopleStillQueueing) {
				count++;
				TripsData td = this.peopleTripData.get(personId);
				Id<TransitStopFacility> waitingAtStopId = td.getWaitingAtStopId();
				Id<TransitStopFacility> tsfId = td.getAnticipatedDestination();
				
				if(lineAndRoutesAtStop.get(waitingAtStopId) == null) {
					continue; //No route, which is weird but maybe considered
				}
				
				//Add to the queue for every lines with this issue.
				for(TransitLineRoute tlr: lineAndRoutesAtStop.get(waitingAtStopId)) {
					TransitRouteStop stopFound = null;
					int foundCount = 0;
					int i = 0;
					for(TransitRouteStop stop: this.transitLines.get(tlr.lineId).getRoutes().get(tlr.routeId).getStops()) {
						if(stop.getStopFacility().getId().equals(waitingAtStopId)) {
							stopFound = stop;
							foundCount++;
						}
						if(stopFound != null && stop.getStopFacility().getId().equals(tsfId)) {
							TransitStop transitStop = new TransitStop(stopFound, i); //First try of transit stop.
							Map<TransitStop,short[]> queueMap = queueLength.get(tlr);
							while(!queueMap.containsKey(transitStop)) {
								i++;
								transitStop = new TransitStop(stopFound, i); //Maybe in a higher order
							}
							while(i < 5 && foundCount!=0) { //Modify the queue if there are other same stops.
								transitStop = new TransitStop(stopFound, i);
								if(queueMap.containsKey(transitStop))
									addOnePersonToQueue(queueMap.get(transitStop), td.getStartWaitingTime(), this.simulationEndTime);
								i++;
								foundCount--;
							}
							break;
						}
					}
				}
			}
			peopleStillQueueing.clear();
		}
		if(count>0) 
			log.info("Processed queue for "+count+" agents.");
	}

	/**
	 * Calculate the expected waiting time with respect to time
	 * 
	 * @param transitLineId
	 * @param transitRouteId
	 * @param transitStop The transit stop object that I created
	 * @param time The time of arrival to there
	 * @return
	 */
	public double expectedWaitingTime(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId,
			TransitStop transitStop, double time) {
		
		if (time >= this.simulationEndTime) { // The time cannot be too long.
			return Double.MAX_VALUE;
		}

		TransitLineRoute tlr = new TransitLineRoute(transitLineId, transitRouteId); // Create the transit line route to
																					// be considered.
		double alpha = ( (time - simulationStartTime) % stepSizeInSecond) / stepSizeInSecond;
		double queue = 0;

		// The initialization would take the queue as 0.
		
		//TODO: Fix the setting if the simulation start time is not from 0
		if (Double.isFinite(time) && time != Double.MAX_VALUE) {
			queue = (1 - alpha) * queueLength.get(tlr).get(transitStop)[(int) (time-simulationStartTime) / stepSizeInSecond]
					+ alpha * queueLength.get(tlr).get(transitStop)[(int) (time-simulationStartTime) / stepSizeInSecond + 1]; // A linear queue.
		}

		ArrivalInfo aInfo = arriveMap.get(tlr).get(transitStop);
		List<Double> arriveTimeList = aInfo.arrivalTimeList;
		for (int i = 0; i < arriveTimeList.size(); i++) {
			if (arriveTimeList.get(i) < time) {
				continue;
			} else {
				queue = queue - aInfo.seatAvailableAtStop.get(i) - aInfo.standingAvailableAtStop.get(i); // Check if the agent can
																		// aboard or not
				if (arriveTimeList.get(i) - time > 1.25*3600) {
					return 999999; //Not preferred but still possible.
				}
				if (queue < 0) { // If the agent can aboard (No queue before)
					return arriveTimeList.get(i) - time;
				}
			}
		}
		return Double.MAX_VALUE; // The agent cannot aboard the vehicle at all.
	}

	public double expectedStopStopTime(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId,
			TransitStop fromTransitStop, TransitStop toTransitStop, double time) {
		TransitLineRoute tlr = new TransitLineRoute(transitLineId, transitRouteId);
		ArrivalInfo fromArrivalInfo = arriveMap.get(tlr).get(fromTransitStop);
		ArrivalInfo toArrivalInfo = arriveMap.get(tlr).get(toTransitStop);

		List<Double> fromArriveTimeList = fromArrivalInfo.arrivalTimeList;
		double stopStopTime = 0.0;

		int index;
		if (fromArriveTimeList.size() > 100) { // Use binary search if it is
			index = Collections.binarySearch(fromArriveTimeList, time);
			if (index < 0) {
				index = -1 * index - 1;
			}
		} else { // Triversal if the list is not that large
			for (index = 0; index < fromArriveTimeList.size(); index++) {
				if (fromArriveTimeList.get(index) < time) {
					continue;
				} else {
					break;
				}
			}
		}
		if (index == fromArriveTimeList.size()) {
			return 1e8; // The arrive time is not found.
		}

		Id<Departure> departureId = fromArrivalInfo.departureIdList.get(index);
		int toArrivingSeq = toArrivalInfo.departureIdList.indexOf(departureId);
		if (toArrivingSeq == -1) {
			return 1e8; // The agent never reach the destination
		} else {
			stopStopTime = toArrivalInfo.arrivalTimeList.get(toArrivingSeq) - fromArriveTimeList.get(index);
		}

		if (stopStopTime < 0) {
			throw new IllegalArgumentException("Why the arrival time has error?");
		}

		return stopStopTime;
	}

	@Override
	/**
	 * This function keep the seats remain
	 */
	public int getVehicleSeatRemain(TransitStop stop, Id<TransitLine> lineId, Id<TransitRoute> routeId,
			double time) {
		TransitLineRoute tlr = new TransitLineRoute(lineId, routeId);
		ArrivalInfo aInfo = arriveMap.get(tlr).get(stop);
		List<Double> arriveTimeList = aInfo.arrivalTimeList;

		for (int i = 0; i < arriveTimeList.size(); i++) {
			if (arriveTimeList.get(i) < time) {
				continue;
			} else {
				return aInfo.seatAvailableBeforeStop.get(i);
			}
		}
		return 1;
	}
	
	@Override
	public int getStandingPassenger(TransitStop stop, Id<TransitLine> lineId, Id<TransitRoute> routeId, double time) {
		TransitLineRoute tlr = new TransitLineRoute(lineId, routeId);
		ArrivalInfo aInfo = arriveMap.get(tlr).get(stop);
		List<Double> arriveTimeList = aInfo.arrivalTimeList;
		
		for (int i = 0; i < arriveTimeList.size(); i++) {
			if (arriveTimeList.get(i) < time) {
				continue;
			} else {
				return aInfo.standingNumberBeforeStop.get(i);
			}
		}
		return 0;
	}
	
	@Override
	public double getStandingProbability(TransitStop stop, Id<TransitLine> lineId, Id<TransitRoute> routeId, double time) {
		TransitLineRoute tlr = new TransitLineRoute(lineId, routeId);
		ArrivalInfo aInfo = arriveMap.get(tlr).get(stop);
		List<Double> arriveTimeList = aInfo.arrivalTimeList;

		for (int i = 0; i < arriveTimeList.size(); i++) {
			if (arriveTimeList.get(i) < time) {
				continue;
			} else {
				return aInfo.standingProbability.get(i);
			}
		}
		return 0;
	}

	@Override
	public void reset(int iteration) {
		// Clear the arrival informations
		for (Map<TransitStop, ArrivalInfo> stopArrivalMap : arriveMap.values()) {
			for (ArrivalInfo ai : stopArrivalMap.values()) {
				ai.reset();
			}
		}

		// Clear the queue values
		for (Map<TransitStop, short[]> queueMap : queueLength.values()) {
			for (short[] queueArray : queueMap.values()) {
				for (int i = 0; i < queueArray.length; i++) {
					queueArray[i] = 0;
				}
			}
		}

		// Clear the vehicleMap
		vehicleInfoCache.clear();
		peopleTripData.clear();
		if(!peopleStillQueueing.isEmpty()) { //The people still queueing should be emptied by calling the processResidualQueue
			peopleStillQueueing.clear();
			//throw new RuntimeException("There is bug!");
		}
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		VehicleType vt = transitVehicles.getVehicles().get(event.getVehicleId()).getType();

		vehicleInfoCache.put(event.getVehicleId(),
				new VehicleInfo(event.getTransitLineId(), event.getTransitRouteId(), event.getDepartureId(),
						event.getVehicleId(), vt.getCapacity().getSeats(), vt.getCapacity().getStandingRoom()));

	}

	@Override
	public void handleEvent(VehicleDepartsAtFacilityEvent event) {
		VehicleInfo vi = vehicleInfoCache.get(event.getVehicleId());

		// Obtain the arrival data in the arrival info
		ArrivalInfo ai = arriveMap.get(vi.getTransitLineRoute()).get(vi.getCurrStop());
		ai.vehicleDeparture(vi.departureId, vi.actualSeatAvailable, vi.actualStandingAvailable);
	}

	@Override
	public void handleEvent(VehicleArrivesAtFacilityEvent event) {
		VehicleInfo vi = vehicleInfoCache.get(event.getVehicleId());
		vi.arrivedStop(event.getFacilityId());

		ArrivalInfo ai = arriveMap.get(vi.getTransitLineRoute()).get(vi.getCurrStop());
		int standingPass = vi.numOfStandings - vi.standingAvailable;
		ai.vehicleArrive(vi.departureId, event.getTime(), vi.seatAvailable, standingPass,
				((double) standingPass) / (standingPass + vi.numOfseats+ 1e-8));
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		// For the vehicle side, do the egress operation
		if (!event.getPersonId().toString().contains("pt_")) { // Ignore driver events
			VehicleInfo vi = vehicleInfoCache.get(event.getVehicleId());
			if (vi == null) {
				return; // Not for the vehicle not in the transit vehicles.
			}
			vi.egress();
			
			//For the person side, end the trip
			TripsData personTripsData = peopleTripData.get(event.getPersonId());
			personTripsData.endTrip(vi.routeId, vi.lineId, vi.getCurrStop().getFacilityId(), event.getTime());
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (!event.getPersonId().toString().contains("pt_")) { // Ignore driver events
			peopleStillQueueing.remove(event.getPersonId());
			// For the vehicle side, do the access operation
			VehicleInfo vi = vehicleInfoCache.get(event.getVehicleId());
			if (vi == null) {
				return; // Not for the vehicle not in the transit vehicles.
			}
			vi.aboard();

			TripsData personTripsData = peopleTripData.get(event.getPersonId()); // Get the start waiting time.
			double startWaitTime = personTripsData.getStartWaitingTime();
			double endWaitTime = event.getTime();
			short[] queue = queueLength.get(vi.getTransitLineRoute()).get(vi.getCurrStop()); // Obtain the queue object
			addOnePersonToQueue(queue, startWaitTime, endWaitTime);

			// Perform the charging operation in agent level.
			String transportMode = this.transitLines.get(vi.lineId).getRoutes().get(vi.routeId).getTransportMode();
			personTripsData.startTrip(vi.routeId, vi.lineId, vi.getCurrStop().getFacilityId(), transportMode,
					event.getTime());
		}

	}

	@Override
	public void handleEvent(AgentWaitingForPtEvent event) {
		double time = event.getTime();
//		Id<TransitStopFacility> atFacilityId = event.getWaitingAtStopId();
//		Id<TransitStopFacility> toFacilityId = event.getDestinationStopId();
		Id<Person> personId = event.getPersonId();
		if(personId==null) {
			System.nanoTime();
		}
		
		TripsData personTripsData;
		if (peopleTripData.containsKey(personId)) {
			personTripsData = peopleTripData.get(personId);
		} else {
			personTripsData = new TripsData(personId);
			peopleTripData.put(personId, personTripsData);
		}
		personTripsData.startWait(time, event.getWaitingAtStopId(), event.getDestinationStopId());
		peopleStillQueueing.add(personId);
	}

	private class ArrivalInfo {
		private final TransitStop transitStop; // A secure field to ensure it is right

		private final List<Id<Departure>> departureIdList;
		private final List<Double> arrivalTimeList; // The exact arrival time to the facility
		private final List<Integer> seatAvailableAtStop; // The number of seats available after egress
		private final List<Integer> standingAvailableAtStop; // The number of standings available after egress
		private final List<Integer> seatAvailableBeforeStop; // The number of seats available on arrival
		private final List<Integer> standingNumberBeforeStop; // The number of people standing on arrival
		private final List<Double> standingProbability; // The number of people standing on arrival

		private ArrivalInfo(TransitStop transitStop, int numDeparture) {
			this.transitStop = transitStop;
			departureIdList = new ArrayList<Id<Departure>>(numDeparture);
			arrivalTimeList = new ArrayList<Double>(numDeparture);
			seatAvailableAtStop = new ArrayList<Integer>(numDeparture);
			standingAvailableAtStop = new ArrayList<Integer>(numDeparture);
			seatAvailableBeforeStop = new ArrayList<>(numDeparture);
			standingNumberBeforeStop = new ArrayList<>(numDeparture);
			standingProbability = new ArrayList<Double>(numDeparture);
		}

		private void addArrival(Id<Departure> departureId, double arrivalTime, int seats, int standing) {
			this.departureIdList.add(departureId);
			this.arrivalTimeList.add(arrivalTime);
			this.seatAvailableAtStop.add(seats);
			this.standingAvailableAtStop.add(standing);
			this.seatAvailableBeforeStop.add(seats);
			this.standingNumberBeforeStop.add(0);
			this.standingProbability.add(0.);
		}

		private void vehicleArrive(Id<Departure> departureId, double arrivalTime, int seatsAvailableAtStop, int standingNumberAtStop,
				double standingProbability) {
			this.departureIdList.add(departureId);
			this.arrivalTimeList.add(arrivalTime);
			this.seatAvailableAtStop.add(0);
			this.standingAvailableAtStop.add(0);
			this.seatAvailableBeforeStop.add(seatsAvailableAtStop);
			this.standingNumberBeforeStop.add(standingNumberAtStop);
			this.standingProbability.add(standingProbability);
		}

		private void vehicleDeparture(Id<Departure> departureId, int seats, int standing) {
			int index = this.departureIdList.indexOf(departureId);
			this.seatAvailableAtStop.set(index, seats);
			this.standingAvailableAtStop.set(index, standing);
		}

		private void initializeFirstStop(Map<Id<Departure>, Departure> departures) {
			// Put them to list
			for (Departure departure : departures.values()) {
				departureIdList.add(departure.getId());
				arrivalTimeList.add(departure.getDepartureTime());
				VehicleType vt = transitVehicles.getVehicles().get(departure.getVehicleId()).getType();
				seatAvailableAtStop.add(vt.getCapacity().getSeats());
				standingAvailableAtStop.add(vt.getCapacity().getStandingRoom());
			}

			// Sort them in time
			keySort(arrivalTimeList, arrivalTimeList, departureIdList, seatAvailableAtStop, standingAvailableAtStop);
		}

		private void reset() {
			this.departureIdList.clear();
			this.arrivalTimeList.clear();
			this.seatAvailableAtStop.clear();
			this.standingAvailableAtStop.clear();
			this.seatAvailableBeforeStop.clear();
			this.standingNumberBeforeStop.clear();
			this.standingProbability.clear();
		}

		// Copied from stackOverflow question 15400514
		private <T extends Comparable<T>> void keySort(final List<T> key, List<?>... lists) {
			// Create a List of indices
			List<Integer> indices = new ArrayList<Integer>();
			for (int i = 0; i < key.size(); i++)
				indices.add(i);

			// Sort the indices list based on the key
			Collections.sort(indices, new Comparator<Integer>() {
				@Override
				public int compare(Integer i, Integer j) {
					return key.get(i).compareTo(key.get(j));
				}
			});

			// Create a mapping that allows sorting of the List by N swaps.
			Map<Integer, Integer> swapMap = new HashMap<Integer, Integer>(indices.size());

			// Only swaps can be used b/c we cannot create a new List of type <?>
			for (int i = 0; i < indices.size(); i++) {
				int k = indices.get(i);
				while (swapMap.containsKey(k))
					k = swapMap.get(k);

				swapMap.put(i, k);
			}

			// for each list, swap elements to sort according to key list
			for (Map.Entry<Integer, Integer> e : swapMap.entrySet())
				for (List<?> list : lists)
					Collections.swap(list, e.getKey(), e.getValue());
		}
	}

	/**
	 * Record the real time info of a transit vehicle
	 * 
	 * @author eleead
	 *
	 */
	private class VehicleInfo {
		private final Id<TransitLine> lineId;
		private final Id<TransitRoute> routeId;
		private final Id<Departure> departureId;
		private final Id<Vehicle> vehicleId;

		// For capacity information
		private int seatAvailable;
		private final int numOfseats;
		private int standingAvailable;
		private final int numOfStandings;
		private int actualSeatAvailable; // Dynamic for stop, storing the ACTUAL seat available for passenger in the
											// stop
		private int actualStandingAvailable; // Dynamic for stop, storing the ACTUAL

		// For stop determination
		private final List<Id<TransitStopFacility>> stopFacilityIdList;
		// private double lastArrivalTime; // For storing the arrival time

		private VehicleInfo(Id<TransitLine> lineId, Id<TransitRoute> routeId, Id<Departure> departureId,
				Id<Vehicle> vehicleId, int numOfSeat, int numOfStanding) {
			this.lineId = lineId;
			this.routeId = routeId;
			this.departureId = departureId;
			this.vehicleId = vehicleId;

			numOfseats = numOfSeat;
			seatAvailable = numOfSeat;
			numOfStandings = numOfStanding;
			standingAvailable = numOfStanding;

			stopFacilityIdList = new ArrayList<Id<TransitStopFacility>>();
		}

		/**
		 * The function to be called whenever it arrives a stop, main purpose is storing
		 * the time, and initialize the calculation of the capacity.
		 * 
		 * @param stopFacilityId
		 * @param time
		 */
		private void arrivedStop(Id<TransitStopFacility> stopFacilityId) {
			actualSeatAvailable = seatAvailable;
			actualStandingAvailable = standingAvailable;

			// lastArrivalTime = time;
			stopFacilityIdList.add(stopFacilityId);
		}

		/**
		 * Get the TransitLineRoute object of this stop
		 * 
		 * @return
		 */
		private TransitLineRoute getTransitLineRoute() {
			return new TransitLineRoute(this.lineId, this.routeId);
		}

		/**
		 * Obtain the current stop that the vehicle is locating in.
		 * 
		 * @return
		 */
		private TransitStop getCurrStop() {
			Id<TransitStopFacility> currStopFacilityId = stopFacilityIdList.get(stopFacilityIdList.size() - 1);
			return new TransitStop(currStopFacilityId, stops.get(currStopFacilityId).getLinkId(), 
					this.occurrenceOfStopFacility(currStopFacilityId));
		}

		/**
		 * Get the occurence of stop facility, based on the past data.
		 * 
		 * @param stopFacilityId
		 * @return -1, if the stopFacilityId not occurred, 0 for once, 1 for twice, etc.
		 */
		private int occurrenceOfStopFacility(Id<TransitStopFacility> stopFacilityId) {
			int count = -1;
			for (Id<TransitStopFacility> tsfId : stopFacilityIdList) {
				if (tsfId.toString().replace("BT_", "").replace("bus_", "").
						equals(stopFacilityId.toString().replace("BT_", "").replace("bus_", ""))) {
					count++;
				}
			}
			return count;
		}

		/**
		 * This function is imitating the boarding of one passenger.
		 */
		private void aboard() {
			if (seatAvailable != 0) {
				seatAvailable--;
			} else {
				standingAvailable--;
			}

			if (seatAvailable < 0 || standingAvailable < 0) {
				throw new RuntimeException("The seating is wrong!");
			}
		}

		/**
		 * This function is imitating the alighting of one passenger.
		 */
		private void egress() {
			if (standingAvailable < numOfStandings) { // The standing place is to be added back first.
				standingAvailable++;
				actualStandingAvailable++;
			} else {
				seatAvailable++;
				actualSeatAvailable++;
			}
			if (seatAvailable > numOfseats || standingAvailable > numOfStandings) {
				throw new RuntimeException("The egress is wrong!");
			}
		}
	}

	/**
	 * A tuple like object containing lineId and routeId
	 * @author eleead
	 *
	 */
	private class TransitLineRoute {
		private final Id<TransitLine> lineId;
		private final Id<TransitRoute> routeId;

		/**
		 * Initialize by transit line and route object.
		 * @param line original TransitLine
		 * @param route original TransitRoute
		 */
		private TransitLineRoute(TransitLine line, TransitRoute route) {
			this(line.getId(), route.getId());
		}

		private TransitLineRoute(Id<TransitLine> lineId, Id<TransitRoute> routeId) {
			this.lineId = lineId;
			this.routeId = routeId;
		}

		/**
		 * Equal if the line Id and the route Id is the same
		 */
		@Override
		public int hashCode() {
			return 41 * lineId.hashCode() + routeId.hashCode();
		}

		@Override
		public boolean equals(Object other) {
			TransitLineRoute others = (TransitLineRoute) other;
			return others.lineId.equals(this.lineId) && others.routeId.equals(this.routeId);
		}

		@Override
		public String toString() {
			return "Line: " + this.lineId.toString() + " Route: " + this.routeId.toString();

		}
	}
}
