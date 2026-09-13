package transitFareAndHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.router.CustomDataManager;
import org.matsim.pt.router.FakeFacility;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitTravelDisutility;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt.router.TransitRouterNetwork.TransitRouterNetworkLink;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;
import org.matsim.vehicles.Vehicle;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;

/**
 * This class intended to give the transit travel disutility that also considers
 * the fare.
 * 
 * @author eleead
 *
 */
@Deprecated
public class TransitFareTravelDisutility implements TransitTravelDisutility {
	private final static Logger log = Logger.getLogger(TransitFareTravelDisutility.class);

	final private TransitRouterNetworkTravelTimeAndDisutility disutility;
	final protected FareTransitRouterConfig config; // The config value to be obtained
	final private Map<String, FareCalculator> fareCalculators;

	public TransitFareTravelDisutility(final FareTransitRouterConfig config,
			PreparedTransitSchedule preparedTransitSchedule, TransitSchedule schedule,
			Map<String, FareCalculator> fareCalculators) {
		this.config = config;
		this.disutility = new TransitRouterNetworkTravelTimeAndDisutility(config, preparedTransitSchedule);
		this.fareCalculators = fareCalculators;
	}

	/**
	 * Obtain the fare calculator for certain mode
	 * 
	 * @param transportMode
	 * @return
	 */
	private FareCalculator getFareCalculator(String transportMode) {
		if (this.fareCalculators.containsKey(transportMode))
			return this.fareCalculators.get(transportMode);
		else {
			throw new IllegalArgumentException("The transport mode " + transportMode + " has no calculator for it!");
		}
	}

	/**
	 * The dataManager is used to store the starting point of the route, so it can
	 */
	@Override
	public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle,
			CustomDataManager dataManager) {

		// TODO: Change the fare based on type of person

		double originalDisutility = this.disutility.getLinkTravelDisutility(link, time, person, vehicle, dataManager);
		double fare = 0.0;
		double fareDiff = 0.0; // The fare between alight in previous node with in current node

		TransitRouterNetworkLink wrapped = (TransitRouterNetworkLink) link;

		FareCalculator currFareCal = getFareCalculator(wrapped.toNode.getRoute().getTransportMode());

		if (wrapped.getRoute() == null) {
			// If it is a transfer link, we will calculate the transfer disutility for the
			// transfer.
			Id<TransitRoute> toRouteId = wrapped.toNode.route.getId();
			Id<TransitLine> toLineId = wrapped.toNode.line.getId();
			Id<TransitStopFacility> fromStopId = wrapped.toNode.stop.getStopFacility().getId();
			String toTransportMode = wrapped.getToNode().getRoute().getTransportMode();
			RouteHelper helper = (RouteHelper) dataManager.getFromNodeCustomData();

			/*
			 * Notes that the node custom data will not be added if it is a transfer,
			 * therefore, in the next link, which should be the start of the another route,
			 * the start node that stored in @param dataManager will change
			 * 
			 * However, if it is MTR interchange, then we keep tracking the stop of gate
			 * entrance. On the other hand, no extra charge on the fare for any interchange
			 */
			if (wrapped.getFromNode().getRoute().getTransportMode().equals("train")
					&& toTransportMode.equals("train")) {
				fare = 0;
				dataManager.setToNodeCustomData(helper); // For train change train, the starting stop would be the same
				originalDisutility += (config.getUtilityOfLineSwitch_utl() / 2); // Train interchange take half cost
			} else {
				// We don't allow taking single bus line twice
				if (helper != null && helper.isLineTook(toLineId, toTransportMode)) {
					return Double.MAX_VALUE;
				}
				fare = currFareCal.getMinFare(toRouteId, toLineId, fromStopId, fromStopId); // Get the minimum fare for
																							// the transport mode

				if (helper == null) {
					dataManager.setToNodeCustomData(new RouteHelper(toLineId, fromStopId, toTransportMode));
				} else {
					dataManager.setToNodeCustomData(helper.createNewAndAddLine(toLineId,
							wrapped.getToNode().getStop().getStopFacility().getId(), toTransportMode));
				}
			}
			if ((!wrapped.getFromNode().getRoute().getTransportMode().equals("train")
					&& wrapped.getToNode().getRoute().getTransportMode().equals("train"))
					|| (wrapped.getFromNode().getRoute().getTransportMode().equals("train")
							&& !wrapped.getToNode().getRoute().getTransportMode().equals("train"))) {
				originalDisutility -= (config.getMarginalUtilityOfTravelTimePt_utl_s()) * 300.0; // For first and last
																									// stop, add 5
																									// minute
																									// interchange time
			}

			double discount = 0.0; // TODO: Calculate the discount / transfer benefit on top of the fare.
			fare -= discount; // The psuedo fare charged is full fare minus discount

		} else {
			// If it is not a transfer link.
			Id<TransitRoute> routeId = wrapped.getRoute().getId();
			Id<TransitLine> lineId = wrapped.getLine().getId();
			String transportMode = wrapped.getRoute().getTransportMode();

			Id<TransitStopFacility> fromStopId = wrapped.fromNode.stop.getStopFacility().getId();
			Id<TransitStopFacility> toStopId = wrapped.toNode.stop.getStopFacility().getId();

			RouteHelper helper = (RouteHelper) dataManager.getFromNodeCustomData();
			if (helper == null) { // For the first stop, or the first stop after transfer, the fare is the minimum
									// fare.
				fare = currFareCal.getMinFare(routeId, lineId, fromStopId);
				helper = new RouteHelper(lineId, fromStopId, transportMode); // Put here for the getEntryStationId
																				// later.
				dataManager.setToNodeCustomData(helper); // Store the starting node
			} else {
				dataManager.setToNodeCustomData(helper.createNewAndAddStation(fromStopId, transportMode)); // Still
																											// storing
																											// the
																											// starting
																											// node.
			}

			Id<TransitStopFacility> startStopId = helper.getEntryStationId();

			fareDiff = currFareCal.getMinFare(routeId, lineId, startStopId, toStopId)
					- currFareCal.getMinFare(routeId, lineId, startStopId, fromStopId);

			if (transportMode.equals("train")) {
				// To avoid going backward, for train
				if (helper.isStationVisited(toStopId)) {
					fareDiff = Double.MAX_VALUE;
				} else if (fareDiff < 0) {
					fareDiff = Double.MAX_VALUE;
					// log.warn("The fare difference is negative from "+fromStopId+" to "+toStopId+"
					// begins at "+startStopId+" for agent "+person.getId()+", fixed.");
				}
			} else if (transportMode.equals("bus")) {
				if (fareDiff < 0) {
					if (startStopId == fromStopId) {
						// If it is the beginning stop, get the minimum fare for the first item instead.
						fareDiff = currFareCal.getMinFare(routeId, lineId, startStopId, toStopId)
								- currFareCal.getMinFare(routeId, lineId, fromStopId);
					} else {
						List<Double> posFaresFrom = currFareCal.getFares(routeId, lineId, startStopId, fromStopId);
						List<Double> posFaresTo = currFareCal.getFares(routeId, lineId, startStopId, toStopId);
						if (posFaresFrom.size() == 1 && posFaresTo.size() == 2) {
							fareDiff = Collections.max(posFaresTo) - posFaresFrom.get(0);
						} else if (posFaresTo.size() == 1 && posFaresFrom.size() == 2) {
							throw new RuntimeException();
						} else {
							throw new RuntimeException("There are more than 2 combinations for the fare!");
						}
					}
				}
			}
			// System.out.println("The starting stop is: " + startStopId ); //Check if the
			// starting stop is right
		}
		if (fare < 0) {
			throw new RuntimeException("The fare is negative, not valid!");
		} else if (fareDiff < 0) {
			throw new RuntimeException("The fare difference is negative for longer travel, not valid!");
		}
		/*
		 * System.out.println("(Route "+wrapped.getFromNode().getRoute().getId()+")"+
		 * wrapped.getFromNode().getStop().getStopFacility().getId()+
		 * "->(Route "+wrapped.getToNode().getRoute().getId()+")"+wrapped.getToNode().
		 * getStop().getStopFacility().getId()+
		 * " The fare is: "+fare+" the fare difference is "+fareDiff);
		 */
		return originalDisutility * (0.8 + 0.4 * Math.random()) + // Added a random term to it.
				(fare + fareDiff) * config.getMarginalUtilityOfMoney();
	}

	@Override
	public double getWalkTravelTime(Person person, Coord coord, Coord toCoord) {
		return this.disutility.getWalkTravelTime(person, coord, toCoord);
	}

	@Override
	public double getWalkTravelDisutility(Person person, Coord coord, Coord toCoord) {
		return this.disutility.getWalkTravelDisutility(person, coord, toCoord);
	}

	/**
	 * This class is only designed for MTR. Otherwise, like in the
	 * transitRouterImplTest, it will be the fail point
	 * 
	 * @author eleead
	 *
	 */
	private class RouteHelper {
		private HashSet<String> trainStationsVisited; // For MTR (train) only
		private HashSet<Id<TransitLine>> busLineTook;
		private Id<TransitStopFacility> entryStationId;

		public RouteHelper(Id<TransitLine> lineId, Id<TransitStopFacility> startStationId, String transportMode) {
			this.entryStationId = startStationId;
			this.trainStationsVisited = new HashSet<String>();
			this.busLineTook = new HashSet<Id<TransitLine>>();
			if (transportMode.equals("train")) {
				this.addStation(startStationId);
			} else if (transportMode.equals("bus")) {
				this.addLine(lineId);
			}
		}

		public RouteHelper(final RouteHelper other) {
			this.entryStationId = other.getEntryStationId();
			if (other.trainStationsVisited != null) { // Copy a new HashSet if it does not exist.
				this.trainStationsVisited = new HashSet<String>();
				for (String code : other.trainStationsVisited) {
					this.trainStationsVisited.add(code);
				}
			}

			if (other.busLineTook != null) { // Copy a new HashSet if it does not exist.
				this.busLineTook = new HashSet<Id<TransitLine>>();
				for (Id<TransitLine> code : other.busLineTook) {
					this.busLineTook.add(code);
				}
			}
		}

		public void setEntryStationId(Id<TransitStopFacility> id) {
			this.entryStationId = id;
		}

		public Id<TransitStopFacility> getEntryStationId() {
			return this.entryStationId;
		}

		public RouteHelper createNewAndAddLine(Id<TransitLine> transitLineId, Id<TransitStopFacility> startStationId,
				String transportMode) {
			RouteHelper newHelper = new RouteHelper(this);
			newHelper.setEntryStationId(startStationId);
			if (transportMode.equals("bus")) {
				newHelper.addLine(transitLineId);
				return newHelper;
			} else {
				return newHelper;
			}
		}

		public RouteHelper createNewAndAddStation(Id<TransitStopFacility> visitedStationId, String transportMode) {
			if (transportMode.equals("train")) {
				RouteHelper newHelper = new RouteHelper(this);
				newHelper.addStation(visitedStationId);
				return newHelper;
			} else {
				return new RouteHelper(this);
			}
		}

		private void addStation(Id<TransitStopFacility> visitedStationId) {
			if (this.trainStationsVisited == null) { // Create if we don't have one
				this.trainStationsVisited = new HashSet<String>();
			}
			String code = visitedStationId.toString().substring(4, 7);
			if (!code.matches("[A-Z]+")) {
				throw new IllegalArgumentException("The substring of the station is invalid!");
			}
			this.trainStationsVisited.add(visitedStationId.toString().substring(4, 7)); // This line is customized for
																						// MTR.
		}

		private void addLine(Id<TransitLine> transitLineId) {
			if (this.busLineTook == null) { // Create if we don't have one
				this.busLineTook = new HashSet<Id<TransitLine>>();
			}
			this.busLineTook.add(transitLineId);
		}

		public boolean isStationVisited(Id<TransitStopFacility> station) {
			String code = station.toString().substring(4, 7);
			return trainStationsVisited.contains(code);
		}

		public boolean isLineTook(Id<TransitLine> lineId, String transportMode) {
			if (transportMode.equals("bus"))
				return busLineTook.contains(lineId);
			else {
				return false;
			}
		}
		/*
		 * private Id<TransitStopFacility> reverseDirection(Id<TransitStopFacility>
		 * toReverse){ String id = toReverse.toString().substring(0, 7); String postfix
		 * = toReverse.toString().substring(7); if(postfix.equals("Up")){ return
		 * Id.create(id+"Down", TransitStopFacility.class); }else if
		 * (postfix.equals("Down")){ return Id.create(id+"Up",
		 * TransitStopFacility.class); }else{ throw new
		 * RuntimeException("Something went wrong."); } }
		 */
	}
}
