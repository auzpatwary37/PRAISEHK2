package createBus;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

import com.google.common.collect.Lists;

public abstract class BusRoute {
	private static Map<Id<TransitStopFacility>, List<Tuple<Id<Link>, Integer>>> stopFacilityLinkCount;
	private static Map<Id<TransitStopFacility>, Tuple<Coord, String>> stopFacilityCoordAndName;
	protected BusStop firstBusStop;
	protected BusStop lastBusStop;
	public List<Tuple<String, BusStop>> busStops; // Enable you to extract bus Stop by bus Stop ID

	protected final String operator;
	protected final String routeName;

	private LinkGraph linkgraph; // For finding the links that make the shortest path.
	protected final BusPathCalculator routingAlgo;
	L2lNetworkLeastCostPathCalculator invertedNetworkRouteAlgo;

	public BusRoute(Network network, String operator, String routeName, BusPathCalculator routingAlgo) {
		linkgraph = new LinkGraph(network, routingAlgo);

		this.operator = operator;
		this.routeName = routeName;
		this.routingAlgo = routingAlgo;
		busStops = new ArrayList<Tuple<String, BusStop>>();
	}
	
	private static synchronized void putStopFacilityLinkCount(Id<TransitStopFacility> tsFId, List<Tuple<Id<Link>, Integer>> linkIdLists) {
		stopFacilityLinkCount.put(tsFId, linkIdLists);
	}
	private static synchronized void putStopFacilityCoordAndName(Id<TransitStopFacility> tsFId, Tuple<Coord, String> whateverthisis) {
		stopFacilityCoordAndName.put(tsFId, whateverthisis);
	}
	
	/**
	 * It is a helper constructor solely for the debug purpose.
	 * @param operator
	 * @param routeName
	 * @param rsc
	 */
	public BusRoute(String operator, String routeName) {
		routingAlgo = null;
		this.operator = operator;
		this.routeName = routeName;
		busStops = new ArrayList<Tuple<String, BusStop>>();
	}

	public void addBusStop(BusStop prevBusStop, BusStop currBusStop) {
		if (firstBusStop == null && prevBusStop == null) {
			firstBusStop = currBusStop;
		}
		busStops.add(new Tuple<String, BusStop>(currBusStop.getStopId(), currBusStop));
		linkgraph.addBusStop(prevBusStop, currBusStop);
		lastBusStop = currBusStop;
	}
	
	public static void initializeStopFacilityLinkCount() {
		stopFacilityLinkCount = Collections.synchronizedMap(new HashMap<>());
		stopFacilityCoordAndName = Collections.synchronizedMap(new HashMap<>());
	}
	
	public static Map<Id<TransitStopFacility>, List<Tuple<Id<Link>, Integer>>> getStopFacilityLinkCount() {
		return stopFacilityLinkCount;
	}
	
	public static Map<Id<TransitStopFacility>, Tuple<Coord, String>> getStopFacilityCoord(){
		return stopFacilityCoordAndName;
	}

	/**
	 * Match the bus stop to the link while editing the stop facility link count.
	 */
	@SuppressWarnings("unchecked")
	public void mapBusStop() {
		linkgraph.autoDijkstra();
		LinkedHashMap<String, Id<Link>> map = linkgraph.getBusStopLinkMap(); // Obtain the best stop links

		// Modify the bus stops, and merge the link Id found into it.
		for (Tuple<String, BusStop> busStop : busStops) {
			Id<TransitStopFacility> tsFId = Id.create(busStop.getFirst(), TransitStopFacility.class);
			List<Tuple<Id<Link>, Integer>> linkIdLists = stopFacilityLinkCount.get(tsFId);
			Id<Link> linkId = map.get(busStop.getFirst());
			if (linkId != null) {
				busStop.getSecond().setLinkId(linkId);
			} else {
				throw new RuntimeException("The bus stop have no link ID assigned.");
			}
			
			//Add the links information
			if(linkIdLists==null) {
				linkIdLists = Lists.newArrayList(new Tuple<Id<Link>,Integer>(linkId, 1));
			}else {
				Tuple<Id<Link>, Integer> tupleFound = null;
				for(Tuple<Id<Link>, Integer> linkCount: linkIdLists) { //Iterate to find the tuple with same linkId
					if(linkId.equals(linkCount.getFirst())) {
						tupleFound = linkCount;
						break;
					}
				}
				if(tupleFound!=null) { //Replace the tuple with one with higher count.
					linkIdLists.add(new Tuple<Id<Link>, Integer>(linkId, tupleFound.getSecond()+1));
					linkIdLists.remove(tupleFound);
				}else {
					linkIdLists.add(new Tuple<Id<Link>, Integer>(linkId, 1));
				}
			}
			putStopFacilityLinkCount(tsFId, linkIdLists);
			putStopFacilityCoordAndName(tsFId, new Tuple<Coord, String>(busStop.getSecond().getCoord(),
					busStop.getSecond().getName()));
		}
	}

	public List<Tuple<String, BusStop>> getBusStops() {
		return busStops;
	}

	protected void fillPathBetweenTwoNode(Link fromLink, Link toLink, BusPathCalculator routingAlgo,
			List<Id<Link>> routeLinkList) {
		Path path = routingAlgo.calcLeastCostPath(fromLink, toLink, 0, null, null);
		for (Link l : path.links) {
			routeLinkList.add(l.getId());
		}
	}

	/**
	 * Obtain the path between two stops, and return the path
	 * 
	 * @param prevTSF
	 * @param currTSF
	 * @param routeLinkList
	 * @return
	 */
	protected Path getAndFillPathBetweenTwoNode(TransitStopFacility prevTSF, TransitStopFacility currTSF,
			List<Id<Link>> routeLinkList) {
		invertedNetworkRouteAlgo.initBeforeCalcRoute(prevTSF, currTSF);
		Path path = invertedNetworkRouteAlgo.calcLeastCostPath((Node) null, (Node) null, 0, null, null);
		for (Link l : path.links) {
			routeLinkList.add(l.getId());
		}

		return path; // Return the travel time
	}

	/**
	 * An old departure filler.
	 * 
	 * @param tsF
	 *            The TransitScheduleFactory object
	 * @param vehicleIDs
	 *            The set of vehicle to be assigned
	 * @param startVehicleIndex
	 *            The vehicle index to be started to assigned
	 * @param transitRoute
	 *            The transit route to be filled
	 * @param departures
	 *            The list of departure as the time in second from 00:00
	 */
	@Deprecated
	protected void fillDeparture(TransitScheduleFactory tsF, List<Id<Vehicle>> vehicleIDs, int startVehicleIndex,
			TransitRoute transitRoute, List<Integer> departures) {
		int index = startVehicleIndex;
		for (int time : departures) {
			if (index == vehicleIDs.size()) {
				index = 0;
			}
			Departure departure = tsF.createDeparture(Id.create(time + "", Departure.class), time);
			departure.setVehicleId(vehicleIDs.get(index));
			index++;
			transitRoute.addDeparture(departure);
		}
	}

	/**
	 * Given a map, fill the departure for transitRoute
	 * 
	 * @param tsF
	 *            The TransitScheduleFactory object
	 * @param vehicleIDs
	 *            The set of vehicle to be assigned
	 * @param startVehicleIndex
	 *            The vehicle index to be started to assigned
	 * @param transitRoute
	 *            The transit route to be filled
	 * @param departures
	 *            A map with key as departure sequence, and value as the time in
	 *            second from 00:00am
	 */
	protected void fillDeparture(TransitScheduleFactory tsF, List<Id<Vehicle>> vehicleIDs, int startVehicleIndex,
			TransitRoute transitRoute, Map<Integer, Integer> departures) {
		for (int departureSeq : departures.keySet()) {
			int time = departures.get(departureSeq);
			Departure departure = tsF.createDeparture(Id.create(time + "", Departure.class), time);
			while (departureSeq >= vehicleIDs.size()) { // Ensure the departure sequence won't ask for a minibus.
				departureSeq -= vehicleIDs.size();
			}
			departure.setVehicleId(vehicleIDs.get(startVehicleIndex + departureSeq));
			transitRoute.addDeparture(departure);
		}
	}

	public abstract BusRoute getSubRoute(int fromStopSequence, int toStopSequence);

	public abstract TransitRoute createRouteFromMap(Scenario scenario, Id<TransitRoute> transitRouteId,
			List<Id<Vehicle>> vehicleIDs, int startVehicleIndex, Map<Integer, Integer> departures);
	
}
