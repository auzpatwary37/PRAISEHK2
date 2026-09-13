package dynamicTransitRouter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;
import org.matsim.pt.router.CustomDataManager;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import dynamicTransitRouter.TransitRouterNetworkHR.TransitRouterNetworkLink;
import dynamicTransitRouter.costs.StopStopTime;
import dynamicTransitRouter.costs.TransferWalkingTime;
import dynamicTransitRouter.costs.VehicleOccupancy;
import dynamicTransitRouter.costs.WaitingTime;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import transitFareAndHandler.FareTransitRouterConfig;

/**
 * A customized disutility considers the fare effect.
 * (Where is it using?)
 * @author eleead
 *
 */
public class FareDynamicTransitTimeAndDisutility extends TransitRouterNetworkTravelTimeAndDisutility {
//		implements TravelDisutility {	XXX pretty sure this is just a wrong call for TransitTravelDisutility, see if this breaks anything JLo

//	public static double NO_SIT_FACTOR = 1.5;
	private static final int MAX_TRANSFERS = 3; //The maximum number of transfer. Then (max_legs == MAX_TRANSFERS+1)
	private static final int MAX_TRANSFER_LINKS = 5;
	private static final double InFareSystemInterchange_GeneralDisutilityFactor = 0.5;	//For train interchange train/LR, the utility of line switch if just half.
	private Link previousLink;
	private double previousTime;
	private double cachedTravelTime;

	final WaitingTime waitingTime; // Time to wait until next bus
	final StopStopTime stopStopTime; // Time between two stops
	final VehicleOccupancy vehicleOccupancy; // The factor of vehicle occupancy
	final private Map<String, FareCalculator> fareCalculators;
	protected final FareTransitRouterConfig config;
	final TransferDiscountCalculator tdc;
	
	public FareDynamicTransitTimeAndDisutility(final FareTransitRouterConfig config, WaitingTime waitTime, 
			StopStopTime stopStopTime, VehicleOccupancy vehicleOccupancy, Map<String, FareCalculator> fareCalculators,
			TransferDiscountCalculator tdc, PreparedTransitSchedule preparedTransitSchedule) {
		super(config, preparedTransitSchedule);
		this.config = config;
		this.fareCalculators = fareCalculators;

		this.waitingTime = waitTime;
		this.stopStopTime = stopStopTime;
		this.vehicleOccupancy = vehicleOccupancy;
		
		this.tdc = tdc;
	}

	/**
	 * That's the original constructor when there was no transfer discount calculator.
	 * @param config An object of FareTransitRouterConfig
	 * @param routerNetwork The network of router
	 * @param waitTime
	 * @param stopStopTime
	 * @param vehicleOccupancy
	 * @param fareCalculators
	 * @param preparedTransitSchedule
	 */
	@Deprecated
	public FareDynamicTransitTimeAndDisutility(final FareTransitRouterConfig config, WaitingTime waitTime, 
			StopStopTime stopStopTime, VehicleOccupancy vehicleOccupancy, Map<String, FareCalculator> fareCalculators,
			PreparedTransitSchedule preparedTransitSchedule) {
		this(config, waitTime, stopStopTime, vehicleOccupancy, fareCalculators, null,
				preparedTransitSchedule);
	}

	/**
	 * Obtain the fare calculator for certain mode
	 * 
	 * @param transportMode
	 * @return
	 */
	protected FareCalculator getFareCalculator(String transportMode) {
		if (this.fareCalculators.containsKey(transportMode))
			return this.fareCalculators.get(transportMode);
		else {
			throw new IllegalArgumentException("The transport mode " + transportMode + " has no calculator for it!");
		}
	}

	@Override
	public double getLinkTravelTime(final Link link, final double time, Person person, Vehicle vehicle) {
		// Get the link travel time in seconds
		previousLink = link;
		previousTime = time;
		TransitRouterNetworkHR.TransitRouterNetworkLink wrapped = (TransitRouterNetworkHR.TransitRouterNetworkLink) link;
		if (wrapped.route != null)
			// in line link
			cachedTravelTime = stopStopTime.expectedStopStopTime(wrapped.line.getId(), wrapped.route.getId(),
					wrapped.fromNode.tStop, wrapped.toNode.tStop, time);
		else if (wrapped.toNode.route != null)
			// wait link
			cachedTravelTime = waitingTime.expectedWaitingTime(wrapped.toNode.line.getId(),
					wrapped.toNode.route.getId(), wrapped.toNode.tStop, time);
		else if (wrapped.fromNode.route == null)
			// walking link
			cachedTravelTime = wrapped.getLength() / this.config.getBeelineWalkSpeed();
		else
			// inside link
			cachedTravelTime = 0;
		return cachedTravelTime;
	}

	@Override
	public double getLinkTravelDisutility(final Link link, final double time, final Person person,
			final Vehicle vehicle, final CustomDataManager dataManager) {		
		boolean cachedTravelDisutility = false;
		if (previousLink == link && previousTime == time)
			cachedTravelDisutility = true;
		TransitRouterNetworkLink wrapped = (TransitRouterNetworkLink) link;
		
		double fareDisutility = getLinkTravelFareDisutility(wrapped, person, time, dataManager);
		if (fareDisutility>=Double.MAX_VALUE){
			return fareDisutility;
		}
		double generalDisutility = getGeneralDisutility(wrapped, time, cachedTravelDisutility, dataManager);

		return generalDisutility + fareDisutility;
	}
	
	/**
	 * It returns the disutility of time, distance, walking, congestion and line switch.
	 * @param wrapped
	 * @param time
	 * @param cachedTravelDisutility
	 * @param dataManager the RouteHelper
	 * @return
	 */
	private double getGeneralDisutility(final TransitRouterNetworkLink wrapped, final double time, 
			boolean cachedTravelDisutility , final CustomDataManager dataManager) {
		
		RouteHelper routeHelper = (RouteHelper) dataManager.getFromNodeCustomData();
		
		double generalDisutility;
		if (wrapped.route != null) {
			//In line link(i.e. travel in the same vehicle from a stop to another stop)
			double timeStopStop = cachedTravelDisutility ? cachedTravelTime
					: stopStopTime.expectedStopStopTime(wrapped.line.getId(), wrapped.route.getId(),
							wrapped.fromNode.tStop, wrapped.toNode.tStop, time);
			if( timeStopStop > 1e7) {
				return Double.MAX_VALUE; //Defined as infinite disutility for undefined time
			}else {
				if(wrapped.route.getTransportMode().equals(RouteHelper.MTRMode)) {
					generalDisutility = - timeStopStop * this.config.getMetroTimeDisutility()
							- wrapped.getLength() * this.config.getMarginalUtilityOfTravelDistancePt_utl_m();
				}else {
					generalDisutility = - timeStopStop * this.config.getMarginalUtilityOfTravelTimePt_utl_s()
							- wrapped.getLength() * this.config.getMarginalUtilityOfTravelDistancePt_utl_m();
				}

				if(!routeHelper.isSitting() && 
						vehicleOccupancy.getStandingPassenger(wrapped.toNode.tStop, wrapped.line.getId(), wrapped.route.getId(), time) > 0){
					double standingProbability = vehicleOccupancy.getStandingProbability(wrapped.toNode.tStop, wrapped.line.getId(), wrapped.route.getId(), time);
					double crowdedFactor = 1;
					if(standingProbability > 0.8) {
						crowdedFactor = 1.55;
					}
					generalDisutility -= timeStopStop * this.config.getStandingTimeDisutility() * crowdedFactor;
					
				}else {
					routeHelper.setSitting();
				}
			}
		}
		else if (wrapped.toNode.route != null) {
			// it's a wait link / boarding link
			double timeWait = cachedTravelDisutility ? cachedTravelTime
					: waitingTime.expectedWaitingTime(wrapped.toNode.line.getId(), wrapped.toNode.route.getId(),
							wrapped.toNode.tStop, time);
			if(routeHelper != null)
				routeHelper.setStanding(); //Reset the state of routeHelper
			if(timeWait == Double.MAX_VALUE) {
				return Double.MAX_VALUE; //Defined as infinite disutility for undefined time
			}else {
				String toTransportMode = wrapped.toNode.route.getTransportMode();
				//For train interchange train, the utility of line switch if just half.
				if (routeHelper != null && ((routeHelper.lastMode.equals(RouteHelper.MTRMode) && toTransportMode.equals(RouteHelper.MTRMode)) ||
						(routeHelper.lastMode.equals(RouteHelper.LRMode) && toTransportMode.equals(RouteHelper.LRMode)))) {
					generalDisutility = - timeWait * this.config.getMarginalUtilityOfWaitingPt_utl_s() 
							- this.config.getUtilityOfLineSwitch_utl() * InFareSystemInterchange_GeneralDisutilityFactor;
				}
				//If the number of transfer is more than the limit, we simply give it max value.
				else if (routeHelper != null && routeHelper.transfers >= MAX_TRANSFERS) {
					//reason for MAX_TRANSFERS-1: -1 cause routeHelper is from prev node not this node
					return Double.MAX_VALUE;
				}
				// For first and last stop of train, add 3 minute walking interchange time
				else if(routeHelper != null && (routeHelper.lastMode.equals(RouteHelper.MTRMode) || toTransportMode.equals(RouteHelper.MTRMode))) {	//both are MTRMode is already checked above, so TrueTrue case doesn't matter JLo
					generalDisutility = - timeWait * this.config.getMarginalUtilityOfWaitingPt_utl_s() 
							- this.config.getUtilityOfLineSwitch_utl() 
							- this.config.getMarginalUtilityOfTravelTimeWalk_utl_s() * 180.0;
				}else
					generalDisutility = - timeWait * this.config.getMarginalUtilityOfWaitingPt_utl_s() 
							- this.config.getUtilityOfLineSwitch_utl();
			}
		}
		else if (wrapped.fromNode.route == null) {
			// it's a transfer link (walk)
			if(routeHelper!= null) {
				if(routeHelper.numberOfTransferLinksPassed > MAX_TRANSFER_LINKS) {
					return Double.MAX_VALUE; //We not allow more than 2 transfers consecutively
				}else {
					RouteHelper newRouteHelper = routeHelper.clone(); //It should be replaced to avoid multiple link assigned to one
					newRouteHelper.numberOfTransferLinksPassed++;
					newRouteHelper.transferLinkPassed.add(wrapped);
					dataManager.setToNodeCustomData(newRouteHelper);
				}
			} else	//disallow transfers at start node(s)
				return Double.MAX_VALUE;
			
			double timeTransfer = -1;
			if(wrapped.fromNode.route!= null && 
					wrapped.fromNode.route.getTransportMode().equals("train")) {
				timeTransfer = TransferWalkingTime.getWalkingTime(
						wrapped.fromNode.getStop().getStopFacility().getId(), 
						wrapped.toNode.getStop().getStopFacility().getId());
			}
			
			if(timeTransfer == -1) {
				timeTransfer = cachedTravelDisutility ? cachedTravelTime
						: wrapped.getLength() / this.config.getBeelineWalkSpeed();
			}
			if(timeTransfer == Double.MAX_VALUE) {
				return Double.MAX_VALUE; //Defined as infinite disutility for undefined time
			}else {
				generalDisutility = -timeTransfer * this.config.getMarginalUtilityOfTravelTimeWalk_utl_s();
			}
		} else {
			// egress link
			generalDisutility = 0;
		}
		
		if(generalDisutility<0) {
			throw new RuntimeException("The disuility should not be negative!");
		}
		
		return generalDisutility;
	}
	
	protected double getBoardingLinkFare(final TransitRouterNetworkLink wrapped, boolean isFirstTrip, 
			RouteHelper fromNodeHelper, RouteHelper newHelper, double time) {
		Id<TransitRoute> toRouteId = wrapped.toNode.route.getId();
		Id<TransitLine> toLineId = wrapped.toNode.line.getId();
		TransitStop fromStop = wrapped.toNode.tStop;
		String toTransportMode = wrapped.toNode.getRoute().getTransportMode();

		FareCalculator modeFareCal = getFareCalculator(toTransportMode);
		//"in system" transfer
		if (!isFirstTrip && 
				((newHelper.lastMode.equals(RouteHelper.MTRMode) && toTransportMode.equals(RouteHelper.MTRMode)) ||
					(newHelper.lastMode.equals(RouteHelper.LRMode) && toTransportMode.equals(RouteHelper.LRMode)))) {
			//Not allowed to board the same line twice
			if(newHelper.lastTransitLineId.equals(toLineId))
				return Double.MAX_VALUE; 
			// For train change train, the starting stop would be the same
			return 0;
			
		//normal transfer of lines / first trip
		} else {
			// We don't allow taking single line multi times
			if (!isFirstTrip && newHelper.isLineTook(toLineId)) {
				return Double.MAX_VALUE;
			}else if (fromNodeHelper != null && //We don't allow taking a bus if transfer is already there.
					fromNodeHelper.transitLineRouteForbidden.contains(new TransitLineRoute(toLineId, toRouteId))) {
				return Double.MAX_VALUE;
			}
			double fare = modeFareCal.getMinFare(toRouteId, toLineId, fromStop.getFacilityId()); // Get the minimum fare for the transport mode
				//DEBUG org calls getMinFare(fromStop, toStop), hope this holds up JLo

			//Create or put the data
			if (!isFirstTrip) {				//transfer
				newHelper.changeLine(toLineId, fromStop, toTransportMode, time);
				
				//Check the interchange discount.
				if(tdc!=null) {
					double discount = tdc.getInterchangeDiscount(newHelper.lastTransitLineId, toLineId, newHelper.lastTransitRouteId, toRouteId,
							newHelper.lastMode, toTransportMode, newHelper.lastStartTime, newHelper.lastEndTime, time, newHelper.lastFare, fare);	//Calculate the discount / transfer benefit on top of the fare.
					if(fare<=discount) {	// <= because same fare amount can be applied to multi stops, just for LR
						newHelper.storeDiscountAndUnrealisedFare(discount, fare);
						fare = 0;
					} else if (discount<0) {	//for rebate
						fare = -1*discount;
					} else
						fare -= discount; // The psuedo fare charged is full fare minus discount
				}
			}
			return fare;
		}
	}
	
	/**
	 * Get the travelling fare difference
	 * @param wrapped The transit link
	 * @param newHelper The RouteHelper to get the entry stop.
	 * @return
	 */
	protected double getTravelFareDiff(final TransitRouterNetworkLink wrapped, RouteHelper newHelper) {
		//DEBUG used to limit which lines to take
//		if(!wrapped.getLine().getId().toString().contains("GMB_NT_88M") && !wrapped.getLine().getId().toString().contains("KMB_279X"))
//			return Double.MAX_VALUE;
		
		// Travel Link 	(i.e.If it is not a transfer link)
		Id<TransitRoute> routeId = wrapped.route.getId();
		Id<TransitLine> lineId = wrapped.line.getId();
		String transportMode = wrapped.route.getTransportMode();
		
		TransitStop fromStop = wrapped.fromNode.tStop;
		TransitStop toStop = wrapped.toNode.tStop;
		
		
		FareCalculator currFareCal = getFareCalculator(transportMode); //By default it is transport mode
		FareCalculator nextFareCal = currFareCal;
		boolean boardingFirstClass = false;
		if(transportMode.equals(RouteHelper.MTRMode)) { //If there is first class, get the first class fare calculator instead
			if(newHelper.isFirstClass()) {
				nextFareCal = currFareCal = getFareCalculator(RouteHelper.firstClassMode);
			}else if(wrapped.toNode.getLine().getId().toString().equals(RouteHelper.firstClassName)) {
				nextFareCal = getFareCalculator(RouteHelper.firstClassMode);
				boardingFirstClass = true;
			}
		}
		
		TransitStop startStop = newHelper.entryStop;
		double fareDiff = nextFareCal.getFare(routeId, lineId, startStop.getFacilityId(), startStop.getOccurrence(),
				toStop.getFacilityId(), toStop.getOccurrence())
				- currFareCal.getFare(routeId, lineId, startStop.getFacilityId(), startStop.getOccurrence(),
						fromStop.getFacilityId(), fromStop.getOccurrence());

		if (transportMode.equals(RouteHelper.MTRMode) || transportMode.equals(RouteHelper.LRMode)) {
			// To avoid going backward, for train
			if (newHelper.isStationVisited(toStop.getFacilityId())) {
				return Double.MAX_VALUE;
			} else if (fareDiff < 0) {
				//this could happen in MTR e.g. you can cross harbour more than 1 way 
				newHelper.addUnrealizedDiscount( - fareDiff );
				return 0; //This is an adjustment which disadvantaged TML, but at least get it right.
			}
			
			//Deal with the helper
			newHelper.addStation(fromStop, boardingFirstClass);
			//check if all the discount is realised
			if(transportMode.equals(RouteHelper.LRMode))	//from LR to MTR discount is checked inside the discount calculator JLo
				fareDiff = newHelper.realiseUnrealisedLRFare(fareDiff);
			else
				fareDiff = newHelper.realiseUnrealisedDiscount(fareDiff);
		} else if(fareDiff < 0) { //Store the negative fare to discount for future rebate.
			newHelper.addUnrealizedDiscount( - fareDiff );
			fareDiff = 0;
		}else {
			//check if all the discount is realised
			fareDiff = newHelper.realiseUnrealisedDiscount(fareDiff);
		}
		return fareDiff;
	}

	/**
	 * The part of the disutility of the fare is calculated here. Time is not passed as the fare is now
	 * assumed to be constant.
	 * 
	 * @param wrapped The transfer link concerned.
	 * @param person 
	 * @param vehicle
	 * @param dataManager
	 * @return
	 */
	protected double getLinkTravelFareDisutility(final TransitRouterNetworkLink wrapped, final Person person,
			double time, final CustomDataManager dataManager) {
		double fare = 0.0;
		double fareDiff = 0.0; // The fare between alight in previous node with in current node
		
		//As all of these will be putting in a new route helper anyways, rewriting to flow better JLo
		RouteHelper fromNodeHelper = (RouteHelper) dataManager.getFromNodeCustomData();
		RouteHelper newHelper = null;
		boolean isFirstTrip = false;
		if(fromNodeHelper==null) {
			isFirstTrip = true;
		} else newHelper = fromNodeHelper.clone();

		// If it is a transfer link, we will calculate the transfer disutility for the transfer.
		if (wrapped.getRoute() == null) { 
			if (wrapped.toNode.getRoute() != null) {  // A boarding link
				fare = getBoardingLinkFare(wrapped, isFirstTrip, fromNodeHelper, newHelper, time);
				if(isFirstTrip) {
					Id<TransitLine> toLineId = wrapped.toNode.line.getId();
					TransitStop fromStop = wrapped.toNode.tStop;
					String toTransportMode = wrapped.toNode.getRoute().getTransportMode();
					newHelper = new RouteHelper(toLineId, fromStop, toTransportMode, time);	
				}
			}else if (wrapped.fromNode.getRoute() != null) { // Egress link
				TransitRoute route = wrapped.fromNode.getRoute();
				newHelper.egress(route.getTransportMode(), wrapped.fromNode.getLine().getId(), route.getId(), time);
				//DEBUG: if this throws then There are still errors in finding start points! JLo
			} else { //Transfer links between stops
				if(isFirstTrip) return Double.MAX_VALUE;
			}
		} else {
			fareDiff = getTravelFareDiff(wrapped, newHelper);
		}
		if (fare < 0) {
			throw new RuntimeException("The fare is negative, not valid!");	
		} else if (fareDiff < 0) {
			throw new RuntimeException("The fare difference is negative for longer travel, not valid!");
		}
		//store the helper to toNode and return value
		newHelper.addChargedFare(fare+fareDiff);
		dataManager.setToNodeCustomData(newHelper);
		return (fare + fareDiff) * config.getMarginalUtilityOfMoney(); // The return value is definitely negative
	}
}

