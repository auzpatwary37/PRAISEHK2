package networkFromSaturn.pt;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import networkFromSaturn.WGS84toSaturn;

/**
 * This class is intended to create tram to the existing Hong Kong Island
 * network generated from Saturn.
 * 
 * The stop name and coordinate is obtained from OpenStreetMap, as of January,
 * 2017 The tram route is obtained from HKTramWay website, as of 15 May 2017 The
 * tram link and type (as input) is inputed by Enoch on early May, according to
 * Google StreetView
 * 
 * It provide two functions: 1) Create tram link in three types: a)
 * putTramToLink (C(ombine)) models the link in which tram and car shares one of
 * the lanes b) addTramLaneToLink (S(eparate)) models the link in which tram is
 * separated from car c) addTramLink (N(ew)) models the tram link that is
 * completely separated from road network in the direction 2) Create tram route
 * from the list of stop identification, it finds the shortest path between
 * stops and use it to create the exact route.
 * 
 * @author eleead
 *
 */
@SuppressWarnings("deprecation") // It is using some old codes, so leave it here.
public class CreateTram {
	/**
	 * 
	 * @param lanes:
	 *            LaneDefinitions
	 * @param IdToBeFound:
	 *            The link Id to be look for in the l2l assignment
	 * @param IdToBeCheck:
	 *            The link ID of l2l assignment to be check.
	 * @return
	 */
	private static boolean toLinkInl2l(Lanes lanes, Id<Link> IdToBeFound, Id<Link> IdToBeCheck) {
		if(lanes.getLanesToLinkAssignments().get(IdToBeCheck)==null) {
			return true;
		}
		for (Lane lane : lanes.getLanesToLinkAssignments().get(IdToBeCheck).getLanes().values()) {
			if (lane.getToLinkIds() != null && lane.getToLinkIds().contains(IdToBeFound)) {
				return true;
			}
		}
		return false;
	}

	private static void putTramToLink(Network net, Node fromNode, Node toNode) {
		Id<Link> currLinkId = Id.createLinkId(fromNode.getId().toString() + "_" + toNode.getId().toString());
		Link currLink = net.getLinks().get(currLinkId);
		Set<String> allowedModes = currLink.getAllowedModes();

		// A new one has to be created, because the old one does not allow add.
		Set<String> newAllowedModes = new HashSet<String>(allowedModes);
		newAllowedModes.add("tram");
		currLink.setAllowedModes(newAllowedModes);
		// Capacity does not change, because anyway MATSim is using a queuing model.
		// Also, the car is able to use the link of tram, so it is just like the same.
	}

	/**
	 * Modify the lane definition of link from fromfromNode to fromNode, to add the
	 * link from fromNode to toNode to one of the lane.
	 * 
	 * @param lanes
	 * @param fromfromNode
	 * @param fromNode
	 * @param toNode
	 */
	private static void modifyTramLane(Lanes lanes, Node fromfromNode, Node fromNode, Node toNode) {
		if (fromfromNode == null) {
			return;
		}
		Id<Link> currLinkId = Id.createLinkId(fromNode.getId().toString() + "_" + toNode.getId().toString());
		// Check if the last link assigned lane to this link, if not, add it to the lane
		// definition of last link.
		Id<Link> prevLinkId = Id.createLinkId(fromfromNode.getId().toString() + "_" + fromNode.getId().toString());
		if (!toLinkInl2l(lanes, currLinkId, prevLinkId)) {
			for (Lane lane : lanes.getLanesToLinkAssignments().get(prevLinkId).getLanes().values()) {
				if (lane.getToLinkIds() != null || lane.getToLaneIds() == null) {
					lane.addToLinkId(currLinkId);
				}
			}
		}
	}

	/**
	 * 
	 * @param net
	 * @param lanes
	 * @param fromNode
	 * @param toNode
	 * @param nextLinkId:
	 *            The ID of next link that has tram track.
	 */

	private static void addTramLaneToLink(Network net, Lanes lanes, Node fromNode, Node toNode, Node nextNode) {
		Id<Link> currLinkId = Id.createLinkId(fromNode.getId().toString() + "_" + toNode.getId().toString());
		Link currLink = net.getLinks().get(currLinkId);
		Set<String> allowedModes = currLink.getAllowedModes();
		// A new one has to be created, because the old one does not allow add.
		Set<String> newAllowedModes = new HashSet<String>(allowedModes);
		newAllowedModes.add("tram");
		currLink.setAllowedModes(newAllowedModes);

		currLink.setNumberOfLanes(currLink.getNumberOfLanes() + 1); // Increase the number of lanes

		// After added a lane, modify the lane definition for the lane to the next link
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(currLinkId);
		if(l2l==null) {
			return;
		}
		Id<Link> nextLinkId = Id.createLinkId(toNode.getId().toString() + "_" + nextNode.getId().toString());

		// Add the lanes to the link.
		for (Lane lane : l2l.getLanes().values()) {
			if (lane.getToLinkIds() == null) { // For the lane in the end.
				lane.setNumberOfRepresentedLanes(lane.getNumberOfRepresentedLanes() + 1);
				break;
			}
		}
		for (Lane lane : l2l.getLanes().values()) {
			if (lane.getToLinkIds() != null && lane.getToLinkIds().contains(nextLinkId)) {
				lane.setNumberOfRepresentedLanes(lane.getNumberOfRepresentedLanes() + 1);
				return; // We only need to add to one of the lane
			}
		}
		// If not, randomly assign a lane in the outside to go to the link
		for (Lane lane : l2l.getLanes().values()) {
			if (lane.getToLinkIds() != null || lane.getToLaneIds() == null) {
				lane.getToLinkIds().add(nextLinkId);
			}
		}
	}

	private static void addTramLink(Network net, Lanes lanes, Node fromFromNode, Node fromNode, Node toNode,
			boolean addLanesToLink) {
		// Create and add link
		Id<Link> currLinkId = Id.createLinkId(fromNode.getId().toString() + "_" + toNode.getId().toString());
		Link newLink = net.getFactory().createLink(currLinkId, fromNode, toNode);
		Set<String> allowedModes = new HashSet<String>();
		allowedModes.add("tram");
		newLink.setAllowedModes(allowedModes);
		newLink.setCapacity(1000);
		newLink.setFreespeed(40 / 3.6);
		newLink.getAttributes().putAttribute("type", "2"); // TODO: Remove it
		net.addLink(newLink);

		// Add the lanes to link assignment, if this function is enabled
		if (addLanesToLink) {
			LanesToLinkAssignment newl2l = lanes.getFactory().createLanesToLinkAssignment(currLinkId);
			Lane newLane = lanes.getFactory().createLane(Id.create("tramlane", Lane.class));
			newLane.setStartsAtMeterFromLinkEnd(newLink.getLength());
			newl2l.addLane(newLane);
			lanes.addLanesToLinkAssignment(newl2l);
		}

		// After added a link, modify the lane definition of previous link
		Id<Link> lastLinkId = Id.createLinkId(fromFromNode.getId().toString() + "_" + fromNode.getId().toString());
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(lastLinkId);
		for (Lane lane : l2l.getLanes().values()) {
			if (lane.getToLinkIds() != null || lane.getToLaneIds() == null) {
				lane.addToLinkId(currLinkId);
				return;
			}
		}
	}

	/**
	 * Add tram link from Node IDs
	 * 
	 * @param net
	 * @param lanes
	 * @param fromFromNodeID
	 * @param fromNodeID
	 * @param toNodeID
	 */
	private static void addTramLink(Network net, Lanes lanes, String fromFromNodeID, String fromNodeID,
			String toNodeID) {
		// Obtain the ID
		addTramLink(net, lanes, net.getNodes().get(Id.createNodeId(fromFromNodeID)),
				net.getNodes().get(Id.createNodeId(fromNodeID)), net.getNodes().get(Id.createNodeId(toNodeID)), false);
	}

	public static void createTramNetwork(Network net, Lanes lanes, TransitSchedule ts, String line_path)
			throws IOException {
		Reader line_in = new FileReader(line_path);
		Iterable<CSVRecord> nodes = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(line_in);
		Node currNode = null;
		Node lastNode = null;
		Node lastLastNode = null;
		boolean addNewLane = false;

		for (CSVRecord node : nodes) { // Iterate through every nodes
			currNode = net.getNodes().get(Id.createNodeId(node.get("To")));
			String currType = node.get("Type");

			if (addNewLane) {
				addTramLaneToLink(net, lanes, lastLastNode, lastNode, currNode); // It is done here because the lane of
																					// next link is to be added.
				addNewLane = false;
			}

			if (lastNode == null) {
				// For the first iteration, we save the node first.
			} else {
				if (currType.equals("C")) { // The road usage is combining road and tram. "C"
					putTramToLink(net, lastNode, currNode);
					modifyTramLane(lanes, lastLastNode, lastNode, currNode);
				} else if (currType.equals("S")) { // The road usage is separating road and tram. "C"
					addNewLane = true; // Record the lastLastNode to call the addTramtoLink function.
					modifyTramLane(lanes, lastLastNode, lastNode, currNode);
				} else if (currType.equals("N")) { // The tram line is separated
					addTramLink(net, lanes, lastLastNode, lastNode, currNode, true);
				} else {
					throw new IllegalArgumentException(
							"The type should be either C, S or N, but " + currType + " found.");
				}
			}

			// Create stops
			if (node.get("Stop") != null && !node.get("Stop").isEmpty()) {
				Coord coor = new Coord(Double.parseDouble(node.get("Lon")), Double.parseDouble(node.get("Lat")));
				Coord transformed = new WGS84toSaturn().transform(coor);
				TransitScheduleFactory transitScheduleFactory = ts.getFactory();
				TransitStopFacility tsf = transitScheduleFactory.createTransitStopFacility(
						Id.create(node.get("Stop"), TransitStopFacility.class), transformed, true);
				tsf.setLinkId(Id.createLinkId(lastNode.getId().toString() + "_" + currNode.getId().toString())); // Set
																													// the
																													// corresponding
																													// link
				tsf.setName(node.get("Stop Name")); // Set the name
				ts.addStopFacility(tsf);
			}

			lastLastNode = lastNode;
			lastNode = currNode;
		}
		line_in.close();
	}

	public static void createTramLine(Network net, Scenario scenario, VehicleType vt, int start, int headway, int end,
			int vehIDstart, int vehIDend, String from, String to, Tuple<String, String> routePaths) throws IOException {
		TravelDisutility costFunction = new TramTravelDisutility();
		LeastCostPathCalculator routingAlgo = new DijkstraFactory().createPathCalculator(net, costFunction,
				new TramTravelDisutility()); // It is like faking the algorithm.

		// Obtain the basic containers and its factory
		TransitSchedule ts = scenario.getTransitSchedule();
		Vehicles vehicles = scenario.getTransitVehicles();
		TransitScheduleFactory tsFactory = ts.getFactory();
		VehiclesFactory vehiclesFactory = vehicles.getFactory();
		TransitLine line = tsFactory.createTransitLine(Id.create(from + "_" + to, TransitLine.class));

		for (int i = 0; i < 2; i++) {
			String routePath;
			if (i == 0) {
				routePath = routePaths.getFirst();
			} else {
				routePath = routePaths.getSecond();
			}
			Reader route_in = new FileReader(routePath);
			Iterable<CSVRecord> stations = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(route_in);
			Id<Link> prev_link = null; // To store the previous link of the station, so that we can find the shortest
										// path.
			Id<Link> start_link = null;
			List<Id<Link>> routeLinkList = new ArrayList<Id<Link>>();
			List<TransitRouteStop> subRouteStopList = new ArrayList<TransitRouteStop>();

			for (CSVRecord station : stations) {
				TransitStopFacility tsf = ts.getFacilities()
						.get(Id.create(station.get("ID"), TransitStopFacility.class));
				Id<Link> curr_link = tsf.getLinkId();

				// Add the links to NetworkRoute
				if (prev_link != null) {
					// Add the links between stops
					Node fromNode = net.getLinks().get(prev_link).getToNode(); // The from node is the toNode of the
																				// first link
					Node toNode = net.getLinks().get(curr_link).getFromNode(); // The toNode is the fromNode of the
																				// second link
					Path path = routingAlgo.calcLeastCostPath(fromNode, toNode, 0, null, null);
					for (Link l : path.links) {
						routeLinkList.add(l.getId());
					}
					routeLinkList.add(curr_link);// Add the link of stop
				} else {
					start_link = curr_link;
				}

				// Add the stop to RouteStopList
				Double offset = Double.parseDouble(station.get("Offset"));
				TransitRouteStop newTRS = tsFactory.createTransitRouteStop(tsf, offset, offset);
				subRouteStopList.add(newTRS);

				prev_link = curr_link;

			}
			NetworkRoute route = RouteUtils.createLinkNetworkRouteImpl(start_link,
					routeLinkList.subList(0, routeLinkList.size() - 1), prev_link); // Sublist is used because the
																					// algorithm would count the first
																					// link twice.

			TransitRoute transitRoute = tsFactory.createTransitRoute(
					Id.create(routePath.substring(6, 14), TransitRoute.class), route, subRouteStopList, "tram");
			transitRoute.setTransportMode("tram");
			line.addRoute(transitRoute);

		}
		for (int i = vehIDstart; i < vehIDend; i++) {
			Vehicle newTram = vehiclesFactory.createVehicle(Id.create("Tram" + i, Vehicle.class), vt);
			vehicles.addVehicle(newTram);
		}
		boolean firstRoute = true;
		for (TransitRoute transitRoute : line.getRoutes().values()) {
			int vehicleID = vehIDstart;
			if (!firstRoute) {
				vehicleID = (vehIDstart + vehIDend) / 2; // For another half of tram, it starts in another side
			}
			for (int i = start; i < end; i += headway) {
				Departure newDeparture = tsFactory.createDeparture(Id.create("1_" + (int) i, Departure.class), i * 60);
				newDeparture.setVehicleId(Id.createVehicleId("Tram" + vehicleID)); // Tram are assigned one by one
				if (vehicleID != vehIDend - 1) {
					vehicleID++;
				} // Update the vehicle ID
				else {
					vehicleID = vehIDstart;
				}
				transitRoute.addDeparture(newDeparture);
			}
		}

		ts.addTransitLine(line);
	}

	private static void addConnection(Lanes lanes, Id<Link> firstLink, Id<Link> secondLink) {
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(firstLink);
		for (Lane lane : l2l.getLanes().values()) {
			if (lane.getToLinkIds() != null) {
				lane.addToLinkId(secondLink);
				break;
			}
		}
	}

	/**
	 * A summary function to manually input the files and the hard code stuffs.
	 * 
	 * @param net
	 * @param lanes
	 * @param scenario
	 * @throws IOException
	 */
	public static void createTram(Network net, Lanes lanes, Scenario scenario, boolean adjustment) throws IOException {
		if(adjustment) {
			createTramNetwork(net, lanes, scenario.getTransitSchedule(), "input/tram_track_main_adj.csv");
		}else {
			createTramNetwork(net, lanes, scenario.getTransitSchedule(), "input/tram_track_main.csv"); // Create the main
																									// route
		}
		createTramNetwork(net, lanes, scenario.getTransitSchedule(), "input/tram_track_HappyValley.csv");// Happy Valley
																											// subroute
		createTramNetwork(net, lanes, scenario.getTransitSchedule(), "input/tram_track_NP.csv");// North Point
		addTramLink(net, lanes, "101499", "101324", "101758");
		addConnection(lanes, Id.createLinkId("206103_206101"), Id.createLinkId("206101_206103")); // For Shau Kei Wan
		addConnection(lanes, Id.createLinkId("101090_101089"), Id.createLinkId("101089_101090")); // For Shek Tong Tsui
		addConnection(lanes, Id.createLinkId("101587_101502"), Id.createLinkId("101502_101535")); // For Causeway Bay

		VehicleType tram = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "tram", 11, 2, 25 / 3.6, 40, 70,
				1, 1, 3, "HK Tram");

		// Kennedy Town to Happy Valley
		createTramLine(net, scenario, tram, 6 * 60, 10, 24 * 60, 0, 25, "KTT", "HVT",
				new Tuple<String, String>("input/KTTtoHVT.csv", "input/HVTtoKTT.csv"));
		// Kennedy Town to Shau Kei Wan
		createTramLine(net, scenario, tram, 6 * 60, 30, 19 * 60 + 10, 25, 50, "KTT", "SKT",
				new Tuple<String, String>("input/KTTtoSKT.csv", "input/SKTtoKTT.csv"));
		// North Point to Shek Tong Tsui
		createTramLine(net, scenario, tram, 6 * 60, 10, 20 * 60, 50, 75, "NPT", "WST",
				new Tuple<String, String>("input/NPTtoWST.csv", "input/WSTtoNPT.csv"));
		// Shau Kei Wan to Happy Valley
		createTramLine(net, scenario, tram, 6 * 60, 10, 24 * 60, 75, 100, "SKT", "HVT",
				new Tuple<String, String>("input/SKTtoHVT.csv", "input/HVTtoSKT.csv"));
		// Shau Kei Wan to Western Market
		createTramLine(net, scenario, tram, 6 * 60, 10, 24 * 60, 100, 125, "SKT", "WM",
				new Tuple<String, String>("input/SKTtoWM.csv", "input/WMtoSKT.csv"));
		// Causeway Bay to Shek Tong Tsui
		createTramLine(net, scenario, tram, 6 * 60, 10, 24 * 60, 125, 150, "CBT", "WST",
				new Tuple<String, String>("input/CBTtoWST.csv", "input/WSTtoCBT.csv"));
		
		for(Lane lane: lanes.getLanesToLinkAssignments().get(Id.createLinkId("101587_101502")).getLanes().values()) {
			if(!lane.getId().toString().contains("ol")) {
				lane.addToLinkId(Id.createLinkId("101502_101535"));//Add a turning link to avoid bug.
				break;
			}
		}
	}
}
