package networkFromSaturn.pt;

import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import networkFromSaturn.CreateNetworkUtils;
import networkFromSaturn.WGS84toSaturn;

public class CreateFerry {
	private static TransitLine createFerryLineFromScratch(Network network, TransitSchedule ts, String firstPierName,
			Coord firstPierCoord, String secondPierName, Coord secondPierCoord, int travelTime_in_s) {

		CoordinateTransformation transformer = new SlightCoordinateTransform();

		// Create nodes for first pier
		Id<Node> firstPierNodeId = Id.createNodeId(firstPierName + "Pier");
		Node firstPier = null;
		Node firstPier2 = null;
		Link firstPierLink = null;
		if (network.getNodes().containsKey(firstPierNodeId)) {
			firstPier = network.getNodes().get(firstPierNodeId);
			firstPier2 = network.getNodes().get(Id.createNodeId(firstPierName + "Pier2"));
			firstPierLink = network.getLinks().get(Id.createLinkId(firstPierName + "Pier"));
		} else {
			firstPier = NetworkUtils.createAndAddNode(network, firstPierNodeId, firstPierCoord);
			firstPier2 = NetworkUtils.createAndAddNode(network, Id.createNodeId(firstPierName + "Pier2"),
					transformer.transform(firstPierCoord));
			firstPierLink = NetworkUtils.createAndAddLink(network, Id.createLinkId(firstPierName + "Pier"), firstPier,
					firstPier2, 5, 30 / 3.6, 1000, 1);
			firstPierLink.setAllowedModes(Sets.newHashSet("ship"));
		}

		// Create nodes for second pier
		Id<Node> secondPierNodeId = Id.createNodeId(secondPierName + "Pier");
		Node secondPier = null;
		Node secondPier2 = null;
		Link secondPierLink = null;
		if (network.getNodes().containsKey(secondPierNodeId)) {
			secondPier = network.getNodes().get(secondPierNodeId);
			secondPier2 = network.getNodes().get(Id.createNodeId(secondPierName + "Pier2"));
			secondPierLink = network.getLinks().get(Id.createLinkId(secondPierName + "Pier"));
		} else {
			secondPier = NetworkUtils.createAndAddNode(network, secondPierNodeId, secondPierCoord);
			secondPier2 = NetworkUtils.createAndAddNode(network, Id.createNodeId(secondPierName + "Pier2"),
					transformer.transform(secondPierCoord));
			secondPierLink = NetworkUtils.createAndAddLink(network, Id.createLinkId(secondPierName + "Pier"),
					secondPier, secondPier2, 5, 30 / 3.6, 1000, 1);
			secondPierLink.setAllowedModes(Sets.newHashSet("ship"));
		}

		Link tosecond = NetworkUtils.createAndAddLink(network,
				Id.createLinkId("Ferry" + firstPierName + "To" + secondPierName), firstPier2, secondPier,
				NetworkUtils.getEuclideanDistance(firstPier2.getCoord(), secondPier.getCoord()), 30 / 3.6, 1000, 1);
		Link tofirst = NetworkUtils.createAndAddLink(network,
				Id.createLinkId("Ferry" + secondPierName + "To" + firstPierName), secondPier2, firstPier,
				NetworkUtils.getEuclideanDistance(firstPier.getCoord(), secondPier2.getCoord()), 30 / 3.6, 1000, 1);
		tosecond.setAllowedModes(Sets.newHashSet("ship"));
		tofirst.setAllowedModes(Sets.newHashSet("ship"));

		TransitScheduleFactory tsF = ts.getFactory();
		Id<TransitStopFacility> firstStopFacilityId = Id.create(firstPierName, TransitStopFacility.class);
		TransitStopFacility firstFacility = createOrGetTransitStopFacility(ts, firstStopFacilityId,
				firstPierName + "Pier", firstPierCoord, firstPierLink.getId());

		Id<TransitStopFacility> secondStopFacilityId = Id.create(secondPierName, TransitStopFacility.class);
		TransitStopFacility secondFacility = createOrGetTransitStopFacility(ts, secondStopFacilityId,
				secondPierName + "Pier", secondPierCoord, secondPierLink.getId());

		TransitLine line = tsF.createTransitLine(Id.create(firstPierName + "-" + secondPierName, TransitLine.class));

		// Define the route from first stop to second stop
		List<Id<Link>> routeLinkList = Lists
				.newArrayList(Id.createLinkId("Ferry" + firstPierName + "To" + secondPierName));
		List<TransitRouteStop> trsList = Lists.newArrayList(tsF.createTransitRouteStop(firstFacility, 0, 0),
				tsF.createTransitRouteStop(secondFacility, travelTime_in_s, travelTime_in_s));
		NetworkRoute route = RouteUtils.createLinkNetworkRouteImpl(firstPierLink.getId(), routeLinkList,
				secondPierLink.getId());
		TransitRoute transitRoute = tsF.createTransitRoute(
				Id.create(firstPierName + "To" + secondPierName, TransitRoute.class), route, trsList, "ship");
		line.addRoute(transitRoute);

		// Define the route from second stop to first stop
		List<Id<Link>> routeLinkList2 = Lists
				.newArrayList(Id.createLinkId("Ferry" + secondPierName + "To" + firstPierName));
		List<TransitRouteStop> trsList2 = Lists.newArrayList(tsF.createTransitRouteStop(secondFacility, 0, 0),
				tsF.createTransitRouteStop(firstFacility, travelTime_in_s, travelTime_in_s));
		NetworkRoute route2 = RouteUtils.createLinkNetworkRouteImpl(secondPierLink.getId(), routeLinkList2,
				firstPierLink.getId());
		TransitRoute transitRoute2 = tsF.createTransitRoute(
				Id.create(secondPierName + "To" + firstPierName, TransitRoute.class), route2, trsList2, "ship");
		line.addRoute(transitRoute2);

		ts.addTransitLine(line); // Add back to the transit schedule
		return line;
	}

	/**
	 * Obtain the stop facility from the existing list or get a map.
	 * 
	 * @param ts
	 * @param stopFacilityId
	 * @param coordinate
	 * @param facilityLinkId
	 * @return
	 */
	private static TransitStopFacility createOrGetTransitStopFacility(TransitSchedule ts,
			Id<TransitStopFacility> stopFacilityId, String name, Coord coordinate, Id<Link> facilityLinkId) {
		if (ts.getFacilities().containsKey(stopFacilityId)) {
			return ts.getFacilities().get(stopFacilityId);
		} else {
			TransitScheduleFactory tsF = ts.getFactory();
			TransitStopFacility facility = tsF.createTransitStopFacility(stopFacilityId, coordinate, true);
			facility.setLinkId(facilityLinkId);
			facility.setName(name);
			ts.addStopFacility(facility);
			return facility;
		}
	}

	public static void create(Network network, TransitSchedule transitSchedule, Vehicles transitVehicle) {
		// Star ferry
		CoordinateTransformation WGStoSaturn = new WGS84toSaturn();
		Coord WGSTST = new Coord(114.1692, 22.2932);
		Coord WGSCentral = new Coord(114.1601, 22.2868);
		Coord WGSWC = new Coord(114.1761, 22.2830);
		Coord WGSHungHom = new Coord(114.1893, 22.3);
		Coord WGSNouthPoint = new Coord(114.2005276, 22.293351);

		VehicleType starFerry = transitVehicle.getFactory()
				.createVehicleType(Id.create("StarFerry", VehicleType.class));
		starFerry.setAccessTime(0.25);
		starFerry.setEgressTime(0.25);
		VehicleCapacity starFerryCap = starFerry.getCapacity();
		starFerryCap.setSeats((int) (500 * CreateNetworkUtils.scaleDownFactor_pt));
		starFerryCap.setStandingRoom((int) (100 * CreateNetworkUtils.scaleDownFactor_pt));
		starFerry.setMaximumVelocity(50 / 3.6);
		starFerry.setDescription("An ordinary ferry in the map");
		transitVehicle.addVehicleType(starFerry);

		TransitLine TSTtoWC = createFerryLineFromScratch(network, transitSchedule, "TST", WGStoSaturn.transform(WGSTST),
				"WanChai", WGStoSaturn.transform(WGSWC), 5 * 60);
		TransitLine TSTtoCentral = createFerryLineFromScratch(network, transitSchedule, "TST",
				WGStoSaturn.transform(WGSTST), "Central", WGStoSaturn.transform(WGSCentral), 5 * 60);
		TransitLine NWFFHungHom = createFerryLineFromScratch(network, transitSchedule, "HungHom",
				WGStoSaturn.transform(WGSHungHom), "NouthPoint", WGStoSaturn.transform(WGSNouthPoint), 15 * 60);

		fillDeparture(transitSchedule.getFactory(), transitVehicle, starFerry, TSTtoWC, 7 * 60 * 60, 20 * 60 * 60,
				10 * 60);
		fillDeparture(transitSchedule.getFactory(), transitVehicle, starFerry, TSTtoCentral, 6 * 60 * 60, 20 * 60 * 60,
				10 * 60);
		fillDeparture(transitSchedule.getFactory(), transitVehicle, starFerry, NWFFHungHom, 7 * 60 * 60, 21 * 60 * 60,
				15 * 60);

		// Add two links for agents to switch to car mode if they would like to

	}

	private static void fillDeparture(TransitScheduleFactory tsF, Vehicles vehicles, VehicleType vt,
			TransitLine transitLine, int fromTime, int toTime, int headWay) {
		for (TransitRoute tr : transitLine.getRoutes().values()) {
			for (int i = fromTime; i < toTime; i += headWay) {
				Departure departure = tsF.createDeparture(Id.create(i + "", Departure.class), i);
				Vehicle v = vehicles.getFactory().createVehicle(Id.createVehicleId(tr.getId().toString() + " " + i),
						vt);
				vehicles.addVehicle(v);
				departure.setVehicleId(v.getId());
				tr.addDeparture(departure);
			}
		}
	}

	private static class SlightCoordinateTransform implements CoordinateTransformation {
		@Override
		public Coord transform(Coord coord) {
			return new Coord(coord.getX() - 3, coord.getY() - 4);
		}

	}
}
