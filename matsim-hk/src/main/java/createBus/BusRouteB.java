package createBus;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

import com.google.common.collect.Sets;

public class BusRouteB extends BusRoute {
	public static final int delayBetweenStops = 180;

	final Scenario scenario;
	private int routeId; //Route ID in the TD database
	private int routeSeq; //Route sequence in the TD database

	public BusRouteB(Scenario scenario, String operator, String routeName, BusPathCalculator routingAlgo, int routeId, int routeSeq) {
		super(scenario.getNetwork(), operator, routeName, routingAlgo);
		this.scenario = scenario;
		this.routeId = routeId;
		this.routeSeq = routeSeq;
	}
	
	public int getRouteId() {
		return this.routeId;
	}
	
	public int getRouteSequence() {
		return this.routeSeq;
	}

	/**
	 * This function is to create a node and a link for the bus terminus.
	 * 
	 * @param lanes
	 * @param net
	 * @param busStop
	 * @return False indicates nodes or links is not created.
	 */
	private boolean createNodeAndLinkForTerminus(Lanes lanes, Network net, TransitSchedule ts, BusStop busStop) {
		if (busStop.getPossibleLinks().contains(Id.createLinkId("EHC"))
				|| busStop.getPossibleLinks().contains(Id.createLinkId("WHC"))
				|| busStop.getPossibleLinks().contains(Id.createLinkId("CHT"))) {
			
			CreateBusUtils.createAndAddOrGetTransitStopFacility(ts, "BT_"+busStop.getStopId(), busStop.getName(),
					true, busStop.getLinkId(), busStop.getCoord(), false); //For the HKI scenario, create the BT on tunnel instead.
			return false;
		}

		Coord busStopCoord = busStop.getCoord();
		
		Node terminusNode = NetworkUtils.getNearestNode(net, busStopCoord); // Get the nearest node to the bus terminus
		if (terminusNode.getCoord().equals(busStopCoord)) { //This is to check if there is a non-terminus node at the same place. For what? Enoch Aug.2018
			if (!terminusNode.getId().equals(Id.createNodeId("BUS"+busStop.getStopId()))) {
				throw new RuntimeException("The terminus has a node in the same coordinate, that is not terminus.");
			}
			return false;
		} else {// Create a node and link if there is not any.
			Node newNode = NetworkUtils.createAndAddNode(net, Id.createNodeId("BUS"+busStop.getStopId()), busStopCoord);
			Link linkFound = net.getLinks().get(busStop.getLinkId()); //Use the link found
			
			double distanceFromFrom = NetworkUtils.getEuclideanDistance(busStopCoord,
					linkFound.getFromNode().getCoord());
			double distanceFromTo = NetworkUtils.getEuclideanDistance(busStopCoord, linkFound.getToNode().getCoord());
			Node fromNode = distanceFromFrom <= distanceFromTo?
					linkFound.getFromNode() : linkFound.getToNode(); // Set the closest node to be the node to be connected

			Link newLink1 = NetworkUtils.createLink(
					Id.createLinkId(fromNode.getId().toString() + "_" + busStop.getStopId()), fromNode, newNode, net,
					Math.min(distanceFromFrom, distanceFromTo), 40 / 3.6, 2000, 1);
			newLink1.setAllowedModes(Sets.newHashSet("bus"));
			net.addLink(newLink1);
			busStop.setLinkId(newLink1.getId()); // Set the bus stop on the new link
			TransitStopFacility tsf = CreateBusUtils.createAndAddOrGetTransitStopFacility(ts, 
					"BT_"+busStop.getStopId(), busStop.getName(),
					true, newLink1.getId(), busStop.getCoord(), false);

			// Add the to link to the lane
			for (Link l : fromNode.getInLinks().values()) { // Every inlink
				LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(l.getId());
				if (l2l != null) {
					for (Lane lane : l2l.getLanes().values()) {
						if (lane.getToLinkIds()!=null) {
							lane.addToLinkId(newLink1.getId());
						}
					}
				}
			}

			// Add a return link.
			Link newLink2 = NetworkUtils.createLink(
					Id.createLinkId(busStop.getStopId() + "_" + fromNode.getId().toString()), newNode, fromNode, net,
					Math.min(distanceFromFrom, distanceFromTo), 40 / 3.6, 2000, 1);
			newLink2.setAllowedModes(Sets.newHashSet("bus"));
			net.addLink(newLink2);
			return true;
		}
	}
	
	@Override
	public TransitRoute createRouteFromMap(Scenario scenario, Id<TransitRoute> transitRouteId,
			List<Id<Vehicle>> vehicleIDs, int startVehicleIndex, Map<Integer, Integer> departures) {
		TransitSchedule ts = scenario.getTransitSchedule();
		TransitScheduleFactory tsFactory = ts.getFactory();
		Network network = scenario.getNetwork();
		Lanes lanes = scenario.getLanes();
		List<Id<Link>> routeLinkList = new LinkedList<Id<Link>>();
		List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
		Id<Link> prevLinkId = null;
		TransitStopFacility prevTsf = null;
		double delay = 0;

		firstBusStop.setTerminus(true);
		lastBusStop.setTerminus(true);

		createNodeAndLinkForTerminus(lanes, network, ts, lastBusStop);
		if (!firstBusStop.getStopId().equals(lastBusStop.getStopId()))
			createNodeAndLinkForTerminus(lanes, network, ts, firstBusStop); // Not for circular
		invertedNetworkRouteAlgo = new L2lLeastCostCalculatorFactory(scenario, 
				Sets.newHashSet(TransportMode.car, "bus")).getRoutingAlgo();

		// Create the bus stops and fill the path between their neighbor.
		for (Tuple<String, BusStop> busStopTuple : busStops) {
			BusStop busStop = busStopTuple.getSecond();

			TransitStopFacility currTsf = busStop.createOrGetTransitStopFacility(network, ts);
			Id<Link> currLinkId = currTsf.getLinkId(); // It should be consistent with the TransitStopFacility

			TransitRouteStop trs = tsFactory.createTransitRouteStop(currTsf, delay, delay + 60);
			delay += 60;
			stops.add(trs);
			trs.setAwaitDepartureTime(true); // TODO: Remove this temp condition after testing

			// For the first iteration, we don't fill the path between stops.
			if (prevLinkId != null && !prevLinkId.equals(currLinkId)) {
				routeLinkList.add(prevLinkId);
				delay += getAndFillPathBetweenTwoNode(prevTsf, currTsf, routeLinkList).travelTime * 2;
			} else if (prevLinkId != null) {
				delay += (NetworkUtils.getEuclideanDistance(currTsf.getCoord(), prevTsf.getCoord()) / 15);
				trs.setAwaitDepartureTime(true);
			}
			prevTsf = currTsf;
			prevLinkId = currLinkId;
		}

		routeLinkList.add(prevLinkId); // Add the last link after the final iteration

		NetworkRoute route = RouteUtils.createLinkNetworkRouteImpl(firstBusStop.getLinkId(),
				routeLinkList.subList(1, routeLinkList.size() - 1), lastBusStop.getLinkId());

		TransitRoute transitRoute = tsFactory.createTransitRoute(transitRouteId, route, stops, "bus");
		fillDeparture(tsFactory, vehicleIDs, startVehicleIndex, transitRoute, departures); // fill the departure of the
																							// route
		return transitRoute;
	}

	@Override
	public BusRoute getSubRoute(int fromStopSequence, int toStopSequence) {
		BusRouteB subRoute = new BusRouteB(this.scenario, this.operator, this.routeName, this.routingAlgo, this.routeId, this.routeSeq);
		subRoute.addBusStop(null, busStops.get(fromStopSequence).getSecond());
		for (int i = fromStopSequence; i < toStopSequence - 1; i++) {
			subRoute.addBusStop(busStops.get(i).getSecond(), busStops.get(i + 1).getSecond());
		}
		subRoute.mapBusStop();
		return subRoute;
	}
}
