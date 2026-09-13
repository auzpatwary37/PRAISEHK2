package createMTR;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;
import org.xml.sax.SAXException;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import networkFromSaturn.CreateNetworkUtils;
import networkFromSaturn.WGS84toSaturn;
import networkFromSaturn.pt.CreatePTUtils;

public class CreateMTR {

	private static final double stationLength = 300;

	private static void add_train_link(Network net, Id<Link> LinkId, Node from_node, Node to_node) {
		NetworkFactory fac = net.getFactory();
		Link l = fac.createLink(LinkId, from_node, to_node);
		l.setAllowedModes(Sets.newHashSet("train"));
		l.setCapacity(2000);
		l.setFreespeed(30); //30 mph
		l.getAttributes().putAttribute("type", "1"); // This attribute is for calculation of type.
		net.addLink(l);
	}

	/**
	 * 
	 * @param ts
	 * @param stopID
	 * @param name
	 * @param LinkId
	 * @param coordinate
	 * @param Offset:
	 *            The offset of the stop
	 * @return
	 * 
	 * 		The time between departure and arrival would be 20 seconds before the
	 *         standard time and 20 seconds after the standard time
	 */
	private static TransitRouteStop createTransitRouteStop(TransitScheduleFactory transitScheduleFactory,
			TransitStopFacility tsf, double Offset) {
		TransitRouteStop trs = transitScheduleFactory.createTransitRouteStop(tsf, 
				Offset>20? Offset - 20: Offset, Offset + 30); //The offset cannot be less than 0.
		trs.setAwaitDepartureTime(true);
		return trs;
	}

	/**
	 * This function is for create a MTR stop facility, as well as route stop.
	 * @param ts TransitSchedule, the container
	 * @param stopID stopID, e.g. TIK
	 * @param name stop name, e.g. Tiu King Leng
	 * @param LinkId, the link ID facility on
	 * @param coordinate the stop coordinate
	 * @param Offset the offset of the route stop
	 * @return
	 */
	private static TransitRouteStop createRouteStopAndCreateAndAddFacility(TransitSchedule ts, String stopID, String name,
			Id<Link> LinkId, Coord coordinate, double Offset) {
		TransitScheduleFactory transitScheduleFactory = ts.getFactory();
		TransitStopFacility tsf = transitScheduleFactory
				.createTransitStopFacility(Id.create(stopID, TransitStopFacility.class), coordinate, true);
		tsf.setLinkId(LinkId); // Set the corresponding link
		tsf.setName(name); // Set the name
		ts.addStopFacility(tsf);
		return createTransitRouteStop(transitScheduleFactory, tsf, Offset);
	}

	private static TransitRouteStop createRouteStopOnExistingStopFacility(TransitSchedule ts, String stopID,
			double Offset) {
		TransitScheduleFactory transitScheduleFactory = ts.getFactory();
		TransitStopFacility tsf = ts.getFacilities().get(Id.create(stopID, TransitStopFacility.class));

		return createTransitRouteStop(transitScheduleFactory, tsf, Offset);
	}

	/**
	 * It makes use of the MTR Config to fill the departure
	 * 
	 * @param transitScheduleFactory
	 * @param direction
	 * @param firstVehicleID
	 * @param totalVehicleCount
	 * @param vehicleIdFront
	 * @param tr
	 * @return
	 */
	private static int fillDeparture(TransitScheduleFactory transitScheduleFactory, Direction direction,
			int firstVehicleID, int totalVehicleCount, String vehicleIdFront, TransitRoute tr) {
		int vehicleID = firstVehicleID;
		// Obtain the tuple
		LinkedHashMap<Tuple<Integer, Integer>, Headway> frequencyFrom = MTRconfigReader.getFrequencyFrom(direction);

		// For every frequency change, create the corresponding schedule
		for (Tuple<Integer, Integer> frequencyStart : frequencyFrom.keySet()) {

			Headway freqObject = frequencyFrom.get(frequencyStart);
			double frequency = freqObject.getFrequency();
			int j = 0;
			// Create the departure one by one
			for (double i = frequencyStart.getFirst(); i < frequencyStart.getSecond(); i += frequency) {
				j++;
				if (freqObject.hasPortion() && j > freqObject.getPortion()) {
					if (freqObject.hasPortion() && j >= freqObject.getDenominator()) {
						j = 0;
					}
					continue;
				}
				Departure newDeparture = transitScheduleFactory
						.createDeparture(Id.create("1_" + (int) i, Departure.class), i * 60);
				newDeparture.setVehicleId(Id.createVehicleId(vehicleIdFront + vehicleID)); // Trains are assigned one by
																							// one
				vehicleID = (vehicleID==totalVehicleCount-1)? 0 : vehicleID+1; // Update the vehicle ID
				tr.addDeparture(newDeparture);
			}
		}
		// Create the last train
		int i = MTRconfigReader.getLastTrain(direction);
		Departure newDeparture = transitScheduleFactory.createDeparture(Id.create("1_" + (int) i, Departure.class),
				i * 60);
		newDeparture.setVehicleId(Id.createVehicleId(vehicleIdFront + vehicleID)); // Trains are assigned one by one
		tr.addDeparture(newDeparture);

		return vehicleID;
	}

	public static TransitRouteStop getTransitRouteStopByStop(List<TransitRouteStop> transitStopList, String stop) {
		for (TransitRouteStop trs : transitStopList) {
			if (trs.getStopFacility().getId().toString().substring(4, 7).equals(stop)) {
				return trs;
			}
		}
		return null;
	}

	/**
	 * It returns a TransitRouteStop list, with offset shifted, that the first stop
	 * must be 0:00.
	 * 
	 * @param tsF
	 * @param old
	 * @param fromIndex
	 * @param toIndex
	 * @return
	 */
	private static List<TransitRouteStop> getSubRouteStopListShiftedOffset(TransitScheduleFactory tsF,
			List<TransitRouteStop> old, int fromIndex, int toIndex) {
		List<TransitRouteStop> oldSubRouteStopList = old.subList(fromIndex, toIndex);
		List<TransitRouteStop> newSubRouteStopList = new ArrayList<TransitRouteStop>();
		double offset = -1;
		for (TransitRouteStop trs : oldSubRouteStopList) {
			if (offset == -1) {
				offset = trs.getArrivalOffset().seconds();
			}
			TransitStopFacility tsf = trs.getStopFacility();
			TransitRouteStop newTRS = tsF.createTransitRouteStop(tsf, trs.getArrivalOffset().seconds() - offset,
					trs.getDepartureOffset().seconds() - offset);
			newTRS.setAwaitDepartureTime(true);
			newSubRouteStopList.add(newTRS);
		}
		return newSubRouteStopList;
	}

	/**
	 * It is a more generic version that able to feed some lines.
	 * 
	 * @param tsF
	 *            A TransitScheduleFactory object
	 * @param line
	 *            The line ID
	 * @param direction
	 *            The Direction object from start to end
	 * @param trsListofList
	 *            List of List of TransitRouteStop, candidates for subList of
	 *            transitRouteStop
	 * @param routeList
	 *            List of NetworkRoute, a candidate for subRoute of transitRoute
	 * @return
	 */
	private static TransitRoute createSubRoutes(TransitScheduleFactory tsF, String line, Direction direction,
			List<List<TransitRouteStop>> trsListofList, List<NetworkRoute> routeList) {
		String firstStop = direction.getFirstStop();
		String lastStop = direction.getLastStop();

		for (int i = 0; i < routeList.size(); i++) {
			List<TransitRouteStop> trsList = trsListofList.get(i);
			TransitRouteStop trsFirstStop = getTransitRouteStopByStop(trsList, firstStop);
			TransitRouteStop trsLastStop = getTransitRouteStopByStop(trsList, lastStop);
			if (trsFirstStop == null || trsLastStop == null)
				continue; // If either of the stop is not found, then definitely this route is not the
							// route suitable

			// Create the line by sub list of "up", if it is in the order of same as "up"
			int trsFirstStopIndex = trsList.indexOf(trsFirstStop);
			int trsLastStopIndex = trsList.indexOf(trsLastStop);
			if (trsFirstStop.getArrivalOffset().seconds() < trsLastStop.getArrivalOffset().seconds()) {
				List<TransitRouteStop> subRouteStopList = getSubRouteStopListShiftedOffset(tsF, trsList,
						trsFirstStopIndex, trsLastStopIndex + 1);
				NetworkRoute subRoute = routeList.get(i).getSubRoute(trsFirstStop.getStopFacility().getLinkId(),
						trsLastStop.getStopFacility().getLinkId());
				return tsF.createTransitRoute(Id.create(line + "-" + firstStop + "_" + lastStop, TransitRoute.class),
						subRoute, subRouteStopList, "train");
			}
		}
		throw new RuntimeException("The direction is not found in every route!");
	}

	/**
	 * Create stop of link, either prev or next should be defined.
	 * 
	 * @param net
	 * @param angleR
	 * @param stopName
	 * @param stopCoord
	 * @param prevNodeCoord
	 * @param nextNodeCoord
	 * @return
	 */
	private static Node createStopLink(Network net, double angleR, String stopName, Coord stopCoord,
			Coord prevNodeCoord, Coord nextNodeCoord) {
		if (prevNodeCoord != null && nextNodeCoord != null) {
			throw new IllegalArgumentException("Only one coord should be specified.");
		}
		Node furtherNode;
		Node closerNode;
		Node stopNodeA = NetworkUtils.createAndAddNode(net, Id.createNodeId(stopName + "A"),
				new Coord(stopCoord.getX() - Math.cos(angleR) * stationLength / 2,
						stopCoord.getY() - Math.sin(angleR) * stationLength / 2));
		Node stopNodeB = NetworkUtils.createAndAddNode(net, Id.createNodeId(stopName + "B"),
				new Coord(stopCoord.getX() + Math.cos(angleR) * stationLength / 2,
						stopCoord.getY() + Math.sin(angleR) * stationLength / 2));
		if (nextNodeCoord != null) {
			double distA = NetworkUtils.getEuclideanDistance(stopNodeA.getCoord(), nextNodeCoord);
			double distB = NetworkUtils.getEuclideanDistance(stopNodeB.getCoord(), nextNodeCoord);
			closerNode = (distA >= distB ? stopNodeA : stopNodeB); // Closer to the downstream
			furtherNode = (distA >= distB ? stopNodeB : stopNodeA); // Further from the downstream
		} else {
			double distA = NetworkUtils.getEuclideanDistance(stopNodeA.getCoord(), prevNodeCoord);
			double distB = NetworkUtils.getEuclideanDistance(stopNodeB.getCoord(), prevNodeCoord);
			closerNode = (distA <= distB ? stopNodeA : stopNodeB); // Closer to the downstream
			furtherNode = (distA <= distB ? stopNodeB : stopNodeA); // Further from the downstream
		}
		add_train_link(net, Id.createLinkId(stopName + "Up"), closerNode, furtherNode);
		add_train_link(net, Id.createLinkId(stopName + "Down"), furtherNode, closerNode);

		return furtherNode;
	}

	private static void CreateComplexMTRNetworkAndSchedule(TransitSchedule ts, Vehicles vehicles, Network net,
			VehicleType vt, List<String> line_paths, String lineName, String coordinateSystem) throws IOException {
		TransitScheduleFactory transitScheduleFactory = ts.getFactory();

		List<NetworkRoute> networkRouteList = new ArrayList<NetworkRoute>(); //To store the network route
		List<List<TransitRouteStop>> transitRouteStopList = new ArrayList<List<TransitRouteStop>>(); //To store the transit route

		for (String line_path : line_paths) {
			/** Read the csv file and convert to appropriate coordinates */
			Reader line_in = new FileReader(line_path);
			Iterable<CSVRecord> lineRecord = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(line_in);

			// Determine the coordinate transformation for the respective coordinate system
			CoordinateTransformation ct = null;
			if (coordinateSystem == "SATURN")
				ct = new WGS84toSaturn();
			else if (coordinateSystem == "HK1980")
				ct = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:2326");
			else
				throw new InvalidParameterException(
						"coordinateSystem should be either SATURN or HK1980, " + coordinateSystem + " is found");

			// Create the network
			Node prevNode = null;
			Node currNode = null;
			Coord prevNodeCoord = null;
			String prev_stop = null; // The stop shortform, e.g. TIK
			String prev_name = null; // The stop name, e.g. Tiu King Leng
			double prev_up_offset = 0;
			double prev_down_offset = 0;
			boolean prev_is_station = false;
			List<TransitRouteStop> upRouteStopList = new ArrayList<TransitRouteStop>();
			List<TransitRouteStop> downRouteStopList = new ArrayList<TransitRouteStop>();
			List<Id<Link>> uplinkIdList = new ArrayList<Id<Link>>();
			List<Id<Link>> downlinkIdList = new ArrayList<Id<Link>>();
			int iteration = 0;

			// Iterate through the records
			for (CSVRecord location : lineRecord) {
				Coord nodeCoord = ct.transform(
						new Coord(Double.parseDouble(location.get("Lon")), Double.parseDouble(location.get("Lat")))); // Covert
																														// coordinates
				boolean isStation = Integer.parseInt(location.get("station"))==1?true:false;
				// Reset the iteration number if it is after a station
				iteration = isStation? 0:iteration;
				
				if(line_path.contains("TKL")) {
					System.out.print("");
				}

				// Insert nodes and links according to the coordinates
				if (currNode == null) { //First iteration
					Id<Node> currNodeId = Id.createNodeId(lineName + "_" + location.get("ref") + "_" + iteration);
					if (net.getNodes().containsKey(currNodeId)) {
						// Do nothing if the node is already exist, probably just move to another node.
						currNode = net.getNodes().get(currNodeId);
					} else {
						// Create and add the node
						currNode = NetworkUtils.createAndAddNode(net, currNodeId, nodeCoord);//TODO: This guy may not be needed at all. 
					}
				} else {
					// Create and add the node
					prevNode = currNode;
					String stopName = lineName + "_" + prev_stop;
					Id<Node> currNodeId = isStation? 
							Id.createNodeId(lineName + "_" + location.get("ref") + "_" + iteration)://Station node
							Id.createNodeId(stopName + "_" + iteration); // continue the sequence
							
					boolean replicated = net.getNodes().containsKey(currNodeId) || 
							net.getNodes().containsKey( Id.createNodeId(currNodeId.toString().replace("_0", "A")) )
							?true:false; //Check if this node existed before.

					currNode = net.getNodes().containsKey(currNodeId)?net.getNodes().get(currNodeId):
						NetworkUtils.createAndAddNode(net, currNodeId, nodeCoord); // Add or get the node
					
					// Insert the link
					Id<Link> up_linkId = Id
							.createLinkId(prevNode.getId().toString() + "-" + currNode.getId().toString());
					Id<Link> down_linkId = Id
							.createLinkId(currNode.getId().toString() + "-" + prevNode.getId().toString());

					Id<Link> up_stopLinkId = Id.createLinkId(stopName + "Up");
					Id<Link> down_stopLinkId = Id.createLinkId(stopName + "Down");

					if (replicated) {
						if (prev_is_station) { //Obtain the station
							if (!upRouteStopList.isEmpty()) {
								Link stopLinkUp = net.getLinks().get(up_stopLinkId);
								Link linkToStop = null;
								for (Link inLink : stopLinkUp.getFromNode().getInLinks().values()) {
									if (!inLink.getId().equals(down_stopLinkId)) {
										linkToStop = inLink;
										break;
									}
								}
								Node priorStopNode = linkToStop.getFromNode();

								Id<Link> priorUpLinkId = Id.createLinkId(priorStopNode.getId().toString() + "-"
										+ stopLinkUp.getFromNode().getId().toString());
								Id<Link> priorDownLinkId = Id.createLinkId(stopLinkUp.getFromNode().getId().toString()
										+ "-" + priorStopNode.getId().toString());

								uplinkIdList.remove(uplinkIdList.size() - 1);
								uplinkIdList.add(priorUpLinkId);

								downlinkIdList.remove(0);
								downlinkIdList.add(0, priorDownLinkId);
							}
							uplinkIdList.add(up_stopLinkId);
							downlinkIdList.add(0, down_stopLinkId);
							prevNode = net.getLinks().get(up_stopLinkId).getToNode(); // prevNode should be the node
																						// just after the stop
						}
						up_linkId = Id.createLinkId(prevNode.getId().toString() + "-" + currNode.getId().toString());
						down_linkId = Id.createLinkId(currNode.getId().toString() + "-" + prevNode.getId().toString());
					} else {
						if (prev_is_station) { //Create a station
							double angleR; // Angle of the station, in radian
							Node furtherNode; // The node that is further from what created.

							if (upRouteStopList.isEmpty()) { // For the first stop
								angleR = Math.atan((prevNodeCoord.getY() - nodeCoord.getY())
										/ (prevNodeCoord.getX() - nodeCoord.getX()));
								net.removeNode(prevNode.getId());
								furtherNode = createStopLink(net, angleR, stopName, prevNodeCoord, null, nodeCoord);
							} else { // For other stops
								// Obtain the angle
								Link linkToStop = prevNode.getInLinks().entrySet().iterator().next().getValue();
								Node priorStopNode = linkToStop.getFromNode();
								Coord priorStopNodeCoord = priorStopNode.getCoord(); // The coord prior to stop
								angleR = Math.atan((priorStopNodeCoord.getY() - nodeCoord.getY())
										/ (priorStopNodeCoord.getX() - nodeCoord.getX()));

								// Remove the node and link around the station
								net.removeLink(linkToStop.getId());
								net.removeLink(prevNode.getOutLinks().entrySet().iterator().next().getValue().getId());
								net.removeNode(prevNode.getId());

								// Create node and the link specific for station
								furtherNode = createStopLink(net, angleR, stopName, prevNodeCoord, null, nodeCoord);
								Node closerNode = net.getLinks().get(up_stopLinkId).getFromNode();

								// Connect the prior node with the closer node.
								Id<Link> priorUpLinkId = Id.createLinkId(
										priorStopNode.getId().toString() + "-" + closerNode.getId().toString());
								add_train_link(net, priorUpLinkId, priorStopNode, closerNode);
								Id<Link> priorDownLinkId = Id.createLinkId(
										closerNode.getId().toString() + "-" + priorStopNode.getId().toString());
								add_train_link(net, priorDownLinkId, closerNode, priorStopNode);

								// Replace the prior link with the new one.
								uplinkIdList.remove(uplinkIdList.size() - 1);
								uplinkIdList.add(priorUpLinkId);
								downlinkIdList.remove(0);
								downlinkIdList.add(0, priorDownLinkId);
							}
							// Add the station link to the route.
							uplinkIdList.add(up_stopLinkId);
							downlinkIdList.add(0, down_stopLinkId);
							prevNode = furtherNode;

						}
						up_linkId = Id.createLinkId(prevNode.getId().toString() + "-" + currNode.getId().toString());
						down_linkId = Id.createLinkId(currNode.getId().toString() + "-" + prevNode.getId().toString());
						add_train_link(net, up_linkId, prevNode, currNode); // Add links in both sides
						add_train_link(net, down_linkId, currNode, prevNode); // Add links in both sides
					}

					// Add to the link sequence
					uplinkIdList.add(up_linkId);
					downlinkIdList.add(0, down_linkId);

					// Insert stop facilities for LAST station and insert it to stop sequence
					if (prev_is_station) {
						TransitRouteStop up_stop;
						TransitRouteStop down_stop;
						if (!replicated) {
							// Create new facility and node if it is not created yet.
							up_stop = createRouteStopAndCreateAndAddFacility(ts, lineName + "_" + prev_stop + "Up",
									prev_name, Id.createLinkId(lineName + "_" + prev_stop + "Up"), prevNodeCoord,
									prev_up_offset);
							down_stop = createRouteStopAndCreateAndAddFacility(ts, lineName + "_" + prev_stop + "Down",
									prev_name, Id.createLinkId(lineName + "_" + prev_stop + "Down"), prevNodeCoord,
									prev_down_offset);
						} else {
							up_stop = createRouteStopOnExistingStopFacility(ts, lineName + "_" + prev_stop + "Up",
									prev_up_offset);
							down_stop = createRouteStopOnExistingStopFacility(ts, lineName + "_" + prev_stop + "Down",
									prev_down_offset);
						}
						upRouteStopList.add(up_stop);
						downRouteStopList.add(0, down_stop);
					}
				}
				iteration++;

				// Save the coordinate information for next iteration
				prevNodeCoord = nodeCoord;
				prev_is_station = false;
				// Save the station information, if it is a new station
				if (isStation) {
					prev_stop = location.get("ref");
					prev_name = location.get("name");
					prev_up_offset = Double.parseDouble(location.get("upOffset"));
					prev_down_offset = Double.parseDouble(location.get("downOffset"));
					prev_is_station = true;
				}
			}
			// Add the final stops after the iteration
			double angleR = Math.atan((prevNode.getCoord().getY() - currNode.getCoord().getY())
					/ (prevNode.getCoord().getX() - currNode.getCoord().getX()));
			net.removeNode(currNode.getId());
			Id<Link> up_stopLinkId = Id.createLinkId(lineName + "_" + prev_stop + "Up");
			Id<Link> down_stopLinkId = Id.createLinkId(lineName + "_" + prev_stop + "Down");
			createStopLink(net, angleR, lineName + "_" + prev_stop, currNode.getCoord(), prevNode.getCoord(), null);
			Node closerNode = net.getLinks().get(up_stopLinkId).getFromNode();

			// Connect the prior node with the closer node.
			Id<Link> priorUpLinkId = Id.createLinkId(prevNode.getId().toString() + "-" + closerNode.getId().toString());
			add_train_link(net, priorUpLinkId, prevNode, closerNode);
			Id<Link> priorDownLinkId = Id
					.createLinkId(closerNode.getId().toString() + "-" + prevNode.getId().toString());
			add_train_link(net, priorDownLinkId, closerNode, prevNode);

			net.removeLink(uplinkIdList.get(uplinkIdList.size() - 1));
			uplinkIdList.remove(uplinkIdList.size() - 1);
			uplinkIdList.add(priorUpLinkId);
			uplinkIdList.add(up_stopLinkId);
			TransitRouteStop up_stop = createRouteStopAndCreateAndAddFacility(ts, up_stopLinkId.toString(), prev_name,
					up_stopLinkId, prevNodeCoord, prev_up_offset);
			net.removeLink(downlinkIdList.get(0));
			downlinkIdList.remove(0);
			downlinkIdList.add(0, priorDownLinkId);
			downlinkIdList.add(0, down_stopLinkId);
			TransitRouteStop down_stop = createRouteStopAndCreateAndAddFacility(ts, down_stopLinkId.toString(), prev_name,
					down_stopLinkId, prevNodeCoord, prev_down_offset);
			upRouteStopList.add(up_stop);
			downRouteStopList.add(0, down_stop);

			// Create the NetworkRoute for the network
			NetworkRoute up_route = RouteUtils.createNetworkRoute(uplinkIdList, net);
			NetworkRoute down_route = RouteUtils.createNetworkRoute(downlinkIdList, net);

			// Add the elements to the lists
			networkRouteList.add(up_route);
			transitRouteStopList.add(upRouteStopList);
			networkRouteList.add(down_route);
			transitRouteStopList.add(downRouteStopList);
		}

		// Create Transit Route (with Transport mode=train)
		TransitLine line = transitScheduleFactory.createTransitLine(Id.create(lineName, TransitLine.class));

		// Create vehicles
		VehiclesFactory vehiclesFactory = vehicles.getFactory();

		int vehicleCount = MTRconfigReader.getTrainCount(lineName);

		ArrayList<Direction> directions = MTRconfigReader.getDirections(lineName);
		if(directions.size()==2) { //If there are only two points
			String vehicleIdFront = MTRconfigReader.getTrainIdFront(lineName) + "_" + lineName;
			List<Vehicle> trainFromStart = new ArrayList<>();
			List<Vehicle> trainFromEnd = new ArrayList<>();
			for (int i = 0; i < vehicleCount; i++) {
				Vehicle newTrain = vehiclesFactory.createVehicle(Id.create(vehicleIdFront + i, Vehicle.class), vt);
				vehicles.addVehicle(newTrain);
				if( i <= vehicleCount/2 ) {
					trainFromStart.add(newTrain);
				}else {
					trainFromEnd.add(newTrain);
				}
			}
			List<TrainDeparture> departureList = new ArrayList<>();
			Map<String, TransitRoute> startStopToRoute = new HashMap<>();
			//Make up the departure List
			for(Direction direction: directions) {
				LinkedHashMap<Tuple<Integer, Integer>, Headway> frequencyFrom = MTRconfigReader.getFrequencyFrom(direction);
				for (Tuple<Integer, Integer> frequencyStart : frequencyFrom.keySet()) {
					Headway freqObject = frequencyFrom.get(frequencyStart);
					double frequency = freqObject.getFrequency();
					int j = 0;
					// Create the departure one by one
					for (double i = frequencyStart.getFirst(); i < frequencyStart.getSecond(); i += frequency) {
						j++;
						if (freqObject.hasPortion() && j > freqObject.getPortion()) {
							if (freqObject.hasPortion() && j >= freqObject.getDenominator()) {
								j = 0;
							}
							continue;
						}
						departureList.add(new TrainDeparture(direction.getFirstStop(), (int) (i*60) ));
					}
				}
				// Create the last train
				int i = MTRconfigReader.getLastTrain(direction);
				departureList.add(new TrainDeparture(direction.getFirstStop(), (int) (i*60) ));
				
				TransitRoute transitRoute = createSubRoutes(transitScheduleFactory, lineName, direction,
						transitRouteStopList, networkRouteList);
				line.addRoute(transitRoute);
				startStopToRoute.put(direction.getFirstStop(), transitRoute);
			}
			Collections.sort(departureList);
			String startStop = null;
			for(TrainDeparture tDeparture: departureList) {
				String thisStartStop = tDeparture.startStop;
				if(startStop==null) startStop = thisStartStop; //Randomly assign one as start stop.
				Departure departure = transitScheduleFactory
						.createDeparture(Id.create("" + tDeparture.departureTime_s/60, Departure.class), 
								tDeparture.departureTime_s);
				Vehicle trainToUse = null; //Obtain the suitable train to use
				if(thisStartStop.equals(startStop)) {
					trainToUse = trainFromStart.remove(0);
					trainFromEnd.add(trainToUse);
				}else {
					trainToUse = trainFromEnd.remove(0);
					trainFromStart.add(trainToUse);
				}
				departure.setVehicleId(trainToUse.getId()); //Set it as the train to be used
				startStopToRoute.get(thisStartStop).addDeparture(departure); //Add departure to the corresponding route.
			}
			
			
		}else {
			vehicleCount = vehicleCount * 10; // TODO: Create more vehicles to ensure there is no go back traffic for MTR.
			for (Direction direction : directions) {
				String vehicleIdFront = MTRconfigReader.getTrainIdFront(lineName) + "_" + direction.getFirstStop() + "-"
						+ direction.getLastStop();
				for (int i = 0; i < vehicleCount; i++) {
					Vehicle newTrain = vehiclesFactory.createVehicle(Id.create(vehicleIdFront + i, Vehicle.class), vt);
					vehicles.addVehicle(newTrain);
				}
				// First, create a new TransitRoute that matches the direction
				TransitRoute transitRoute = createSubRoutes(transitScheduleFactory, lineName, direction,
						transitRouteStopList, networkRouteList);
				fillDeparture(transitScheduleFactory, direction, 0, vehicleCount, vehicleIdFront, transitRoute);
				line.addRoute(transitRoute);
			}
		}
		ts.addTransitLine(line);
	}

	/**
	 * Call the CreateComplexMTRNetworkAndSchedule anyway
	 * 
	 * @param ts
	 * @param vehicles
	 * @param net
	 * @param vt
	 * @param line_path
	 * @param name
	 * @param coordinateSystem
	 * @throws IOException
	 */
	public static void CreateMTRNetworkAndSchedule(TransitSchedule ts, Vehicles vehicles, Network net, VehicleType vt,
			String line_path, String name, String coordinateSystem) throws IOException {
		CreateComplexMTRNetworkAndSchedule(ts, vehicles, net, vt, Lists.newArrayList(line_path), name,
				coordinateSystem);
	}
	
	public static void createForWholeHK(Scenario scenario, String coordinateSystem) throws IOException, ParserConfigurationException, SAXException {
		run(scenario, coordinateSystem);
		Network net = scenario.getNetwork();
		
		TransitSchedule ts = scenario.getTransitSchedule();
		Vehicles vehicles = scenario.getTransitVehicles();
		VehicleType SP1900 = CreatePTUtils.createVehicleType(vehicles, "SP1900", 196.4, 3.1, 120 / 3.6, 372, 3200, 0.005,
				0.005, 1, "8-car train");
		CreateMTRNetworkAndSchedule(ts, vehicles, net, SP1900, "input/WRL.csv", "WRL", coordinateSystem);
		CreateMTRNetworkAndSchedule(ts, vehicles, net, SP1900, "input/MOL.csv", "MOL", coordinateSystem);
		CreateComplexMTRNetworkAndSchedule(ts, vehicles, net, SP1900, Lists.newArrayList("input/ERL.csv", 
				"input/ERL_LMC.csv"), "ERL", coordinateSystem);
		CreateMTRNetworkAndSchedule(ts, vehicles, net, SP1900, "input/DRL.csv", "DRL", coordinateSystem);
	}

	public static void run(Scenario scenario, String coordinateSystem)
			throws IOException, ParserConfigurationException, SAXException {
		Network net = scenario.getNetwork();

		// Create Transit Schedule and vehicles data, they should be empty at first.
		TransitSchedule ts = scenario.getTransitSchedule();
		Vehicles vehicles = scenario.getTransitVehicles();

		SAXParserFactory factory = SAXParserFactory.newInstance();
		InputStream xmlInput = new FileInputStream("input/mtrLineSettings.xml");
		SAXParser saxParser = factory.newSAXParser();
		MTRconfigReader handler = new MTRconfigReader();
		saxParser.parse(xmlInput, handler);

		//Data source: http://www.td.gov.hk/mini_site/atd/2017/en/section5_t_11.html
		VehicleType vt1 = CreatePTUtils.createVehicleType(vehicles, "Mtrain", 180.02, 3.2, 80 / 3.6, 324, 2172, 0.005,
				0.005, 1, "M-Train using by MTR");
		CreateMTRNetworkAndSchedule(ts, vehicles, net, vt1, "input/ISL.csv", "ISL", coordinateSystem);
		VehicleType vt2 = CreatePTUtils.createVehicleType(vehicles, "SIL_Ctrain", 67, 3.12, 80 / 3.6, 138, 540, 0.01,
				0.01, 1, "Automatic C-train for SIL");
		CreateMTRNetworkAndSchedule(ts, vehicles, net, vt2, "input/SIL.csv", "SIL", coordinateSystem);

		CreateComplexMTRNetworkAndSchedule(ts, vehicles, net, vt1,
				Lists.newArrayList("input/TKL.csv", "input/TKL_LOHAS.csv"), "TKL", coordinateSystem);
		CreateMTRNetworkAndSchedule(ts, vehicles, net, vt1, "input/KTL.csv", "KTL", coordinateSystem);
		CreateMTRNetworkAndSchedule(ts, vehicles, net, vt1, "input/TWL.csv", "TWL", coordinateSystem);
		CreateMTRNetworkAndSchedule(ts, vehicles, net, vt1, "input/TCL.csv", "TCL", coordinateSystem);
	}

	public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {

		Scenario scenario = ScenarioUtils.loadScenario(ConfigUtils.loadConfig("data/config.xml"));

		// Create Transit Schedule and vehicles data
		run(scenario, "SATURN");

		// Write the data
		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile("output/transitSchedule.xml");
		new VehicleWriterV1(scenario.getTransitVehicles()).writeFile("output/transitVehicles.xml");
		new NetworkWriter(scenario.getNetwork()).write("output/network.xml");
	}
}
