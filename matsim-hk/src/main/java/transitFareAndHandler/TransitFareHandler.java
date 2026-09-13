package transitFareAndHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
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

import dynamicTransitRouter.RouteHelper;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;

/**
 * This fare handler is for handling the charge of the agents throughout the simulation.
 * 
 * @author eleead
 *
 */
public class TransitFareHandler
		implements TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler,
		VehicleArrivesAtFacilityEventHandler, VehicleDepartsAtFacilityEventHandler, ActivityStartEventHandler {

	private final Map<Id<Vehicle>, VehicleData> vehicleData = new HashMap<>();
	private final Map<Id<Vehicle>, Id<TransitStopFacility>> vehicleAtFacility = new HashMap<>();
	private final Map<Id<Person>, TripsDataForFareHandler> peopleTripData = new HashMap<>();
	private final Map<String, Integer> transitRouteTaken = new HashMap<>();
	private final EventsManager events;
	private final Map<String, FareCalculator> fareCalculators;
	private final Map<Id<TransitLine>, TransitLine> transitLines;
	private final TransferDiscountCalculator tdc;
	private final Map<String, Map<String, List<Integer>>> MTRODCount = new HashMap<>();
	
	private BigDecimal mtrFareCollected = BigDecimal.ZERO;
	private BigDecimal lrFareCollected = BigDecimal.ZERO;
	private BigDecimal tramFareCollected = BigDecimal.ZERO;
	private BigDecimal busFareCollected = BigDecimal.ZERO;
	private BigDecimal ferryFareCollected = BigDecimal.ZERO;
	private BigDecimal discountGiven = BigDecimal.ZERO;
	
	private int mtrTripCount;
	private int lrTripCount;
	private int tramTripCount;
	private int busTripCount;
	private int ferryTripCount;
	private int interchangeDiscountCount;
	
	private final static Logger log = Logger.getLogger(TransitFareHandler.class);
	
	private Map<String, Tuple<Double, Double>> timeBins;
	private Map<String, Double> timeBasedbusFareCollected;
	private Map<String, Double> timeBasedmtrFareCollected;
	private String currTimeBin;
	private double beginOfNextTimeBin;
	
	private List<Tuple<Double, Double>> mtrODTimeBins = new ArrayList<>(); // The OD time bins of MTR

	@Inject
	TransitFareHandler(final MatsimServices controler, final TransitSchedule transitSchedule,
			Map<String, FareCalculator> fareCals, TransferDiscountCalculator tdc) {
		this(controler.getEvents(), fareCals, transitSchedule.getTransitLines(), tdc);
	}

	public TransitFareHandler(final EventsManager events, final Map<String, FareCalculator> fareCalculators,
			final Map<Id<TransitLine>, TransitLine> transitLines, TransferDiscountCalculator tdc) {
		this.events = events;
		this.fareCalculators = fareCalculators;
		this.transitLines = transitLines;
		for(TransitLine tl: this.transitLines.values()) {
			for(Id<TransitRoute> trId: tl.getRoutes().keySet()) {
				transitRouteTaken.put(tl.getId().toString()+"_"+trId.toString(), 0);
			}
		}
		this.tdc = tdc;
	}
	
	public void updateCurrTimeBin(double timeNow) {
		if(timeBins!=null && timeNow > beginOfNextTimeBin) { //Update the timeBin if it is over the time
			for(String timeBin: timeBins.keySet()) {
				if(timeBins.get(timeBin).getFirst() == beginOfNextTimeBin) {
					currTimeBin = timeBin;
					beginOfNextTimeBin = timeBins.get(timeBin).getSecond();
					return;
				}
			}
		}
	}
	
	public void setMTRTimeBins(List<Tuple<Double, Double>> mtrODTimeBins) {
		this.mtrODTimeBins = mtrODTimeBins;
	}
	
	/**
	 * Add the OD for MTR
	 * @param fromStop
	 * @param toStop
	 */
	private void addMTROD(String fromStop, String toStop, double time) {
		for(int index = 0; index < mtrODTimeBins.size(); index++) {
			Tuple<Double, Double> timeBin = mtrODTimeBins.get(index);
			if(time < timeBin.getFirst() || time >= timeBin.getSecond()) {
				continue; //Do nothing for those time
			}else {
				Map<String, List<Integer>> toStopCount;
				if(MTRODCount.containsKey(fromStop)) {
					toStopCount = MTRODCount.get(fromStop);
				}else {
					toStopCount = new HashMap<>();
					MTRODCount.put(fromStop, toStopCount);
				}
				
				List<Integer> timeCounts;
				if(toStopCount.containsKey(toStop)) {
					timeCounts = toStopCount.get(toStop);
				}else {
					timeCounts = new ArrayList<Integer>(Collections.nCopies(this.mtrODTimeBins.size(), 0));
					toStopCount.put(toStop, timeCounts);
				}
				timeCounts.set(index, timeCounts.get(index) + 1);
				break;
			}
		}
	}
		
	
	/**
	 * It is a function for MetaModel to enable timeBin Measurement.
	 * @param timeBins
	 */
	public void enableTimeBinMeasurement(Map<String, Tuple<Double, Double>> timeBins) {
		this.timeBins = timeBins;
		this.timeBasedbusFareCollected = new HashMap<>();
		this.timeBasedmtrFareCollected = new HashMap<>();
		for(String timeBin : timeBins.keySet()) {
			this.timeBasedbusFareCollected.put(timeBin, 0.);
			this.timeBasedmtrFareCollected.put(timeBin, 0.);
		}
		this.beginOfNextTimeBin = 0.;
	}

	@Override
	public void reset(int iteration) {
		vehicleData.clear();
		vehicleAtFacility.clear();
		peopleTripData.clear();
		MTRODCount.clear();
		
		//Reset the fare collected
		mtrFareCollected = BigDecimal.ZERO;
		lrFareCollected = BigDecimal.ZERO;
		tramFareCollected = BigDecimal.ZERO;
		busFareCollected = BigDecimal.ZERO;
		ferryFareCollected = BigDecimal.ZERO;
		discountGiven = BigDecimal.ZERO;
		log.info("Resetting the data");
		
		mtrTripCount = 0;
		lrTripCount = 0;
		tramTripCount = 0;
		busTripCount = 0;
		ferryTripCount = 0;
		interchangeDiscountCount = 0;
		
		//Reset the two counts
		if(timeBins!=null) {
			for(String timeBin : timeBins.keySet()) {
				this.timeBasedbusFareCollected.put(timeBin, 0.);
				this.timeBasedmtrFareCollected.put(timeBin, 0.);
			}
			this.beginOfNextTimeBin = 0.;
		}
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

			updateCurrTimeBin(event.getTime());
			String transportMode = this.transitLines.get(vData.lineId).getRoutes().get(vData.routeId).getTransportMode();
			
			transitRouteTaken.put(vData.lineId.toString()+"_"+vData.routeId.toString(), transitRouteTaken.get(vData.lineId.toString()+"_"+vData.routeId.toString()) + 1);

			if (!transportMode.equals(RouteHelper.MTRMode) && !transportMode.equals(RouteHelper.LRMode)) {
				FareCalculator fareCal = this.fareCalculators.get(transportMode); // Get the fare calculator
				double fare = fareCal.getMinFare(vData.routeId, vData.lineId, tripsData.getLatestStartFacility(), stopAt); // Obtain the fare
				if(tdc!=null && tripsData.trips.size()>1) {
					//get the discount
					double discount = tdc.getInterchangeDiscount(tripsData.lastTransitLineId, vData.lineId, tripsData.lastTransitRouteId, vData.routeId,
							tripsData.lastTransitMode, transportMode, tripsData.lastStartTime, tripsData.lastEndTime, event.getTime(),
							tripsData.lastFare, fare);
					//interpret the discount
					if(fare<discount) {
						discountGiven = discountGiven.add(new BigDecimal(fare).setScale(2, RoundingMode.HALF_UP));
						this.interchangeDiscountCount++;
						fare = 0;
					} else if(discount>=0) { //It must be greater than or equal to
						fare -= discount;
						if(discount > 0)
							discountGiven = discountGiven.add(new BigDecimal(discount).setScale(2, RoundingMode.HALF_UP));
						this.interchangeDiscountCount++;
					} else {	//rebate
						fare = discount*-1;
						discountGiven = discountGiven.add(new BigDecimal(discount).setScale(2, RoundingMode.HALF_UP).negate());
						this.interchangeDiscountCount++;
					}
						
				}
				//realise the fare
				double time = event.getTime(); // Obtain the time
				this.events.processEvent(new PersonMoneyEvent(time, pid, -fare,FareLink.FareTransactionName,
						new FareLink(FareLink.InVehicleFare,vData.lineId,vData.routeId,tripsData.getLatestStartFacility(),stopAt,transportMode).toString()));
				tripsData.lastFare = fare;
				//counting
				if(transportMode.equals(RouteHelper.BusMode)) { //The bus trips indeed included minibus
					busFareCollected = busFareCollected.add(new BigDecimal(fare).setScale(2, RoundingMode.HALF_UP));
					tripsData.accumulatedBusFare += fare;
					if(timeBins!=null) {
						timeBasedbusFareCollected.put(currTimeBin, timeBasedbusFareCollected.get(currTimeBin) + fare);
					}
					busTripCount++;
				}else if(transportMode.equals(RouteHelper.TramMode)) {
					tramFareCollected = tramFareCollected.add(new BigDecimal(fare).setScale(2, RoundingMode.HALF_UP));
					tripsData.accumulatedOtherFare += fare;
					tramTripCount++;
				}else if(transportMode.equals(RouteHelper.FerryMode)) {
					ferryFareCollected = ferryFareCollected.add(new BigDecimal(fare).setScale(2, RoundingMode.HALF_UP));
					tripsData.accumulatedOtherFare += fare;
					ferryTripCount++;
				}else {
					tripsData.accumulatedOtherFare += fare; //Should collect the minibus fares
				}
			}
			// Ends the trip in the iteration.
			tripsData.endTrip(vData.routeId, vData.lineId, stopAt, event.getTime()); 
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
			// Get rid of the dummy driver.
			if (pid.toString().startsWith("pt_")) { 
				return;
			}
			// See if the trip is in or not.
			TripsDataForFareHandler tripsData; 
			if (peopleTripData.containsKey(pid)) {
				tripsData = peopleTripData.get(pid);
			} else {
				tripsData = new TripsDataForFareHandler(pid);
			}

			VehicleData vData = vehicleData.get(vehicleId);

			String transportMode = this.transitLines.get(vData.lineId).getRoutes().get(vData.routeId).getTransportMode();
			tripsData.startTrip(vData.routeId, vData.lineId, stopAt, transportMode, event.getTime());
			peopleTripData.put(pid, tripsData);
			// System.out.println("Agent "+pid+" boarded "+vData.routeId+" Line
			// "+vData.lineId+" at "+stopAt);
			
			//Set it as on first class if it exists
			if(vData.lineId.toString().equals(RouteHelper.firstClassName)) {
				tripsData.isOnFirstClass = true;
			}
		}

	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		String actType = event.getActType();
		if (!actType.equals("pt interaction")) {
			if (peopleTripData.containsKey(event.getPersonId())) {
				updateCurrTimeBin(event.getTime());
				// Leave MTR if it is still in
				TripsDataForFareHandler tripsData = peopleTripData.get(event.getPersonId());
				if(tripsData.inMTRPaidArea)
					tripsData.leaveAndChargeForMTR(event.getTime());
				else if(tripsData.inLRPaidArea)
					tripsData.leaveAndChargeForLR(event.getTime());
			}
		}
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
	
	
	public BigDecimal getMtrFareCollected() {
		return mtrFareCollected;
	}
	
	public BigDecimal getLRFareCollected() {
		return this.lrFareCollected;
	}

	public BigDecimal getTramFareCollected() {
		return tramFareCollected;
	}

	public BigDecimal getBusFareCollected() {
		return busFareCollected;
	}

	public BigDecimal getFerryFareCollected() {
		return ferryFareCollected;
	}
	
	public BigDecimal getDiscountGiven() {
		return this.discountGiven;
	}
	
	public int getMtrTripCount() {
		return mtrTripCount;
	}
	
	public int getLRTripCount() {
		return this.lrTripCount;
	}

	public int getTramTripCount() {
		return tramTripCount;
	}

	public int getBusTripCount() {
		return busTripCount;
	}

	public int getFerryTripCount() {
		return ferryTripCount;
	}
	
	public int getInterchangeDiscountCount() {
		return this.interchangeDiscountCount;
	}
	
	public Map<String, Double> getTimeBasedbusFareCollected() {
		return timeBasedbusFareCollected;
	}

	public Map<String, Double> getTimeBasedmtrFareCollected() {
		return timeBasedmtrFareCollected;
	}
	
	public Map<String, Integer> getTransitRouteTaken(){
		return transitRouteTaken;
	}
	
	public Map<Id<Person>, Double> getPersonMTRFareCollected(){
		return this.peopleTripData.entrySet().parallelStream().collect(Collectors.toMap(e->e.getKey(), e->e.getValue().accumulatedMTRFare));
	}
	
	public Map<Id<Person>, Double> getPersonBusFareCollected(){
		return this.peopleTripData.entrySet().parallelStream().collect(Collectors.toMap(e->e.getKey(), e->e.getValue().accumulatedBusFare));
	}
	
	public Map<Id<Person>, Double> getPersonOtherFareCollected(){
		return this.peopleTripData.entrySet().parallelStream().collect(Collectors.toMap(e->e.getKey(), e->e.getValue().accumulatedOtherFare));
	}

	private class TripsDataForFareHandler {
		private final Id<Person> personId;
		private LinkedList<TripData> trips;
		private Id<TransitStopFacility> lastEntryStation;
		private double mtrEntryTime;
		private boolean inMTRPaidArea;
		private boolean inLRPaidArea;
		private Id<TransitRoute> lastTransitRouteId; //The ID of last transit route alighted
		private Id<TransitLine> lastTransitLineId; //The ID of last transit line alighted
		private String lastTransitMode;
		private double lastFare;
		private double lastStartTime;
		private double lastEndTime;
		private boolean isOnFirstClass;
		
		private double accumulatedMTRFare;
		private double accumulatedBusFare;
		private double accumulatedOtherFare;

		private TripsDataForFareHandler(final Id<Person> personId) {
			this.personId = personId;
			this.trips = new LinkedList<TripData>();
			this.lastTransitMode = "";
		}

		public void leaveAndChargeForMTR(double time) {
			//finding the fare
			FareCalculator fareCal = isOnFirstClass?fareCalculators.get(RouteHelper.firstClassMode):fareCalculators.get(RouteHelper.MTRMode);
			TripData lastMTRTrip = trips.peekLast(); // It would be the last.
			double fare = fareCal.getMinFare(lastMTRTrip.routeId, lastMTRTrip.lineId, lastEntryStation, lastMTRTrip.endFacilityId); // Obtain the fare
			double discount = 0;
			if(tdc!=null) {
				discount = tdc.getInterchangeDiscount(this.lastTransitLineId, lastMTRTrip.lineId, this.lastTransitRouteId, lastMTRTrip.routeId, this.lastTransitMode, RouteHelper.MTRMode, 
						this.lastStartTime, this.lastEndTime, this.mtrEntryTime, this.lastFare, fare);
			}
			//interpret the discount
			if(fare<discount) {
				discountGiven = discountGiven.add(new BigDecimal(fare).setScale(2, RoundingMode.HALF_UP));
				fare = 0;
			} else if(discount>=0){ //It must be greater than or equal to
				discountGiven = discountGiven.add(new BigDecimal(discount).setScale(2, RoundingMode.HALF_UP));
				interchangeDiscountCount++;
				fare -= discount;
			} //does not handle rebate
			
			//realise the fare
			if (fareCal.getClass().equals(ZonalFareCalculator.class)){ //If it is a zonal fare, give it a vehicle farelink
				events.processEvent(new PersonMoneyEvent(time, personId, -fare, FareLink.FareTransactionName,
						new FareLink(FareLink.InVehicleFare, lastMTRTrip.lineId,lastMTRTrip.routeId, lastEntryStation, 
								lastMTRTrip.endFacilityId,RouteHelper.MTRMode).toString()));
			}else {
				events.processEvent(new PersonMoneyEvent(time, personId, -fare, FareLink.FareTransactionName,
						new FareLink(FareLink.NetworkWideFare,null,null,lastEntryStation,lastMTRTrip.endFacilityId,RouteHelper.MTRMode).toString()));
			}
			
			//Add the OD count
			addMTROD(lastEntryStation.toString().substring(4, 7), lastMTRTrip.endFacilityId.toString().substring(4, 7), time);
			
			//for transfer
			this.lastEntryStation = null;
			this.isOnFirstClass = false;
			this.inMTRPaidArea = false; // Leaving the MTR;
			this.lastTransitMode = RouteHelper.MTRMode;
			this.lastTransitLineId = lastMTRTrip.lineId;
			this.lastTransitRouteId = lastMTRTrip.routeId;
			this.lastFare = fare;
			this.lastStartTime = this.mtrEntryTime;
			this.lastEndTime = lastMTRTrip.endTime;
			//counting
			mtrFareCollected = mtrFareCollected.add(new BigDecimal(fare).setScale(2, RoundingMode.HALF_UP));
			this.accumulatedMTRFare += fare;
			if(timeBins!=null) {
				timeBasedmtrFareCollected.put(currTimeBin, timeBasedmtrFareCollected.get(currTimeBin) + fare);
			}
			mtrTripCount++;
		}
		
		public void leaveAndChargeForLR(double time) {
			//finding the fare
			FareCalculator fareCal = fareCalculators.get(RouteHelper.LRMode);
			TripData lastLRTrip = trips.peekLast(); // It would be the last.
			double fare = fareCal.getMinFare(lastLRTrip.routeId, lastLRTrip.lineId, lastEntryStation, lastLRTrip.endFacilityId); // Obtain the fare
			double discount = 0.;
			if(tdc!=null) {
				discount = tdc.getInterchangeDiscount(this.lastTransitLineId, lastLRTrip.lineId, this.lastTransitRouteId, lastLRTrip.routeId, this.lastTransitMode, RouteHelper.LRMode, 
						this.lastStartTime, this.lastEndTime, this.mtrEntryTime, this.lastFare, fare);
			}
			//interpret the discount
			if(fare<discount) {
				discountGiven = discountGiven.add(new BigDecimal(fare).setScale(2, RoundingMode.HALF_UP));
				interchangeDiscountCount++;
				fare = 0; 
			} else if(this.lastTransitMode.equals(RouteHelper.MTRMode) && fare>discount) {
				discount = 0;
			} else if(discount>=0){ //It must be greater than or equal to
				discountGiven = discountGiven.add(new BigDecimal(discount).setScale(2, RoundingMode.HALF_UP));
				interchangeDiscountCount++;
				fare -= discount;
			} //does not handle rebate
				
			//realise the fare
			events.processEvent(new PersonMoneyEvent(time, personId, -fare, FareLink.FareTransactionName,
					new FareLink(FareLink.NetworkWideFare,null,null,lastEntryStation,lastLRTrip.endFacilityId,RouteHelper.LRMode).toString()));
			//for transfer
			lastEntryStation = null;
			inLRPaidArea = false; // Leaving the MTR;
			lastTransitMode = RouteHelper.LRMode;
			this.lastTransitLineId = lastLRTrip.lineId;
			this.lastTransitRouteId = lastLRTrip.routeId;
			lastFare = fare;
			this.lastStartTime = this.mtrEntryTime;
			this.lastEndTime = lastLRTrip.endTime;
			//counting
			this.accumulatedMTRFare += fare;
			lrFareCollected = lrFareCollected.add(new BigDecimal(fare).setScale(2, RoundingMode.HALF_UP));
			lrTripCount++;
		}

		public void startTrip(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> startFacilityId,
				String transportMode, double time) {
			
			//process exit from MTR/LR system first
			if(!transportMode.equals(RouteHelper.MTRMode) && this.inMTRPaidArea) {
				leaveAndChargeForMTR(time);
			} else if(!transportMode.equals(RouteHelper.LRMode) && this.inLRPaidArea) {
				leaveAndChargeForLR(time);
			}
			
			//then process getting into MTR/LR system
			if (transportMode.equals(RouteHelper.MTRMode) && !this.inMTRPaidArea) { // Record for the entrance
				this.lastEntryStation = startFacilityId;
				this.mtrEntryTime = time;
				this.inMTRPaidArea = true;
			} else if (transportMode.equals(RouteHelper.LRMode) && !this.inLRPaidArea) {
				this.lastEntryStation = startFacilityId;
				this.mtrEntryTime = time;
				this.inLRPaidArea = true;
			} 
			
			this.trips.add(new TripData(lineId, routeId, startFacilityId, transportMode, time));
		}

		public void endTrip(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> endFacilityId, double endTime) {
			TripData toBeEdited = trips.peekLast();
			//only change if not within system end
			if(!this.inLRPaidArea && !this.inMTRPaidArea) {
				this.lastTransitRouteId = routeId;
				this.lastTransitLineId = lineId;
				this.lastTransitMode = toBeEdited.mode;
				this.lastStartTime = toBeEdited.startTime;
				this.lastEndTime = endTime;
			}
			
			if (toBeEdited.getRouteId().equals(routeId) && toBeEdited.getLineId().equals(lineId)) {
				toBeEdited.tripEnd(endFacilityId, endTime);
			} else {
				throw new IllegalArgumentException("The route ID or the line ID is not consistent!");
				//DEBUG: if this throws, unfortunately the tripsData last-"anything" is already scrambled, sorry JLo
			}
		}

		public Id<TransitStopFacility> getLatestStartFacility() {
			return trips.peekLast().getStartFacilityId();
		}
	}

	private class TripData {
		private final Id<TransitLine> lineId;
		private final Id<TransitRoute> routeId;
		private final Id<TransitStopFacility> startFacilityId;
		private Id<TransitStopFacility> endFacilityId;
		private final String mode;
		private double endTime;
		private double startTime;

		private TripData(final Id<TransitLine> lineId, final Id<TransitRoute> routeId,
				final Id<TransitStopFacility> startFacilityId, String mode, double startTime) {
			if (lineId == null || routeId == null || startFacilityId == null || mode == null) {
				throw new IllegalArgumentException("The input parameters cannot be null.");
			}
			this.startTime = startTime;
			this.lineId = lineId;
			this.routeId = routeId;
			this.mode = mode;
			this.startFacilityId = startFacilityId;
			this.endTime = Double.NaN;
		}

		public void tripEnd(Id<TransitStopFacility> endFacilityId, double endTime) {
			this.endFacilityId = endFacilityId;
			this.endTime = endTime;
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

		public Id<TransitStopFacility> getEndFacilityId() {
			return endFacilityId;
		}
		
		public String getMode() {
			return this.mode;
		}
	}

	private class VehicleData {
		private final Id<Vehicle> vehicleId;
		private final Id<TransitLine> lineId;
		private final Id<TransitRoute> routeId;
		private final Id<Departure> departureId;

		private VehicleData(final Id<Vehicle> vehicleId, final Id<TransitLine> lineId, final Id<TransitRoute> routeId,
				final Id<Departure> departureId) {
			this.vehicleId = vehicleId;
			this.lineId = lineId;
			this.routeId = routeId;
			this.departureId = departureId;
		}
	}

	public Map<String, Map<String, List<Integer>>> getMTRODCount() {
		return MTRODCount;
	}

}
