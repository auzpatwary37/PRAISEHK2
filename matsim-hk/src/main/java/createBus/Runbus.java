 package createBus;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import createBus.BusDataExtractor.Route;
import createBus.BusDataExtractor.RouteSeqContainer;
import createBus.BusHeadway.DepartureList;
import dynamicTransitRouter.fareCalculators.ZonalFareCalculator;
import networkFromSaturn.pt.CreatePTUtils;

public class Runbus {
	public static String PATH = "C:\\Users\\eleead\\workspace\\MATSim-HK\\bus\\";
	public static String STOPS_PATH = PATH + "Stops\\";
	public static String HEADWAY_PATH = PATH + "Headway\\";
	public static String SECTION_FARE_PATH = PATH + "Section\\";
	
	/**
	 * For other usage of the code for network generation, they can set their own path for the files needed.
	 * @param path Path for the bus files.
	 */
	public static void setPath(String path) {
		PATH = path;
		STOPS_PATH = PATH + "Stops\\";
		HEADWAY_PATH = PATH + "Headway\\";
		SECTION_FARE_PATH = PATH + "Session\\";
	}

	/**
	 * A function to create and add buses.
	 * @param vehicles Vehicles container
	 * @param vt Vehicle type to be added
	 * @param operator The operator
	 * @param name The route Name
	 * @param numBus Number of buses to be created
	 * @return A list of vehicle ID for the vehicles just created
	 */
	public static List<Id<Vehicle>> createAndAddVehicles(Vehicles vehicles, VehicleType vt, String operator, String name,
			int numBus) {
		List<Id<Vehicle>> vehicleIDs = new ArrayList<Id<Vehicle>>();
		VehiclesFactory vehiclesFactory = vehicles.getFactory();
		for (int i = 0; i < numBus; i++) {
			Id<Vehicle> newBusId = Id.create("Bus" +operator+"_"+ name + "_" + i, Vehicle.class);
			Vehicle newBus = vehiclesFactory.createVehicle(newBusId, vt);
			vehicles.addVehicle(newBus);
			vehicleIDs.add(newBusId);
		}

		return vehicleIDs;
	}
	
	@SuppressWarnings("unused")
	@Deprecated
	private static String obtainCorrectSectionFare(List<String> sectionFareFilePaths, String direction) {
		String sectionFareFile = null;
		if(sectionFareFilePaths!=null) {
			for(String possibleSectionFareFile : sectionFareFilePaths) {
				if(possibleSectionFareFile.contains(direction.replace("To ", ""))) {
					if(sectionFareFile == null) {
						sectionFareFile = possibleSectionFareFile;
					}else {
						throw new RuntimeException("There are more than one possible candidate!");
					}
				}
			}
		}
		return sectionFareFile;
	}
	
	private static List<BusRoute> getListOfBusRouteAndMap(Scenario scenario, String operator, String name, 
			boolean HKIOnly) throws IOException{
		BusDataExtractor dataEx = new BusDataExtractor();
		
		List<BusRoute> listOfBusRoute = new ArrayList<BusRoute>();
		HashMap<Integer,List<Id<Link>>> stopBindings = getStopBindings(scenario.getNetwork());

		for (String routeID : dataEx.findRouteIDs(operator, name)) {
			List<RouteSeqContainer> routeSequenceList = dataEx.getRouteStopIDandName(routeID);
			if (routeSequenceList.size() > 2) {
				throw new RuntimeException("The route map is in a weird size, please check:" + routeSequenceList.size());
			}
			for (RouteSeqContainer routeSeq : routeSequenceList) {
				BusRouteB route = dataEx.createBusRouteB(scenario, operator, name, routeSeq, HKIOnly, stopBindings);
				listOfBusRoute.add(route); // Adding it to the route stop list for matching
			}
		}
		return listOfBusRoute;
	}

	/**
	 * This function must be called after the lane is defined in the scenario
	 * 
	 * @param scenario
	 * @param busFareCal
	 * @param vehicleIDs
	 * @param operator
	 * @param name
	 * @param fullFare
	 * @param HKIOnly true, if the route is created on HKI only.
	 * @throws IOException
	 */
	public static void createCertainBusLineWithDiffRoute(Scenario scenario, ZonalFareCalculator busFareCal,
			List<Id<Vehicle>> vehicleIDs, String operator, String name, double fullFare, 
			boolean HKIOnly) throws IOException {
		
		// Step 1: Create the stop facilities by the routes given by TD.
		List<BusRoute> listOfBusRoute = getListOfBusRouteAndMap(scenario, operator, name, HKIOnly);

		// Step 2: Create the routes based on bus stop data scrapped.
		TransitSchedule ts = scenario.getTransitSchedule();
		Id<TransitLine> transitLineId = Id.create(operator + "_" + name, TransitLine.class);
		TransitLine line = ts.getFactory().createTransitLine(transitLineId);

		BusRouteFareLoader creator = new BusRouteFareLoader();

		if(operator=="LWB") {
			operator = "KMB";
		}
		
		// We start at the headway data.
		List<String> headWayFilePaths = CreateBusUtils.findFilePaths(operator, name, HEADWAY_PATH, "headway");
		Map<Id<TransitRoute>, Tuple<Integer, Integer>> transitRouteToRouteIDAndSequence = new HashMap<>();
		for (int i = 0; i < headWayFilePaths.size(); i++) {
			String filePath = headWayFilePaths.get(i);
			BusHeadway headway = new BusHeadway("bus");
			String direction = findDirectionFromPath(filePath, operator, name);
		
			headway.makeBusDepartures(direction, filePath);
			DepartureList depAndRoute = headway.getDepartures();
			List<Id<TransitRoute>> uniqueRoutes = depAndRoute.getUniqueRoutes();

			// Create the routes that appears in headway
			for (int j = 0; j < uniqueRoutes.size(); j++) {
				char index = uniqueRoutes.get(j).toString().charAt(uniqueRoutes.get(j).toString().length() - 1);
				if (!Character.isDigit(index) || uniqueRoutes.get(j).toString().charAt(uniqueRoutes.get(j).toString().length() - 2)!='_') {
					index = '1';
				}
				// OBTAIN the bus route that is searching from the existing ones created in step
				// 1. Remember that it didn't create any new busRoute.
				BusRouteB foundRoute = (BusRouteB) creator.newLoadBusRoute(scenario,
						filePath.replace(".csv", "[" + index + "].csv").replace(HEADWAY_PATH, STOPS_PATH),
						uniqueRoutes.get(j), CreateBusUtils.findFilePaths(operator, name, STOPS_PATH, "stop"), 
						listOfBusRoute);

				if (foundRoute == null) { // The case for two headway conbined for one circular route.
					continue;
				}

				TransitRoute tr = foundRoute.createRouteFromMap(scenario, uniqueRoutes.get(j), vehicleIDs,
						(int) (vehicleIDs.size() * ((double) (i) / (double) headWayFilePaths.size())),
						depAndRoute.getDepartureAndOrderForRoute(uniqueRoutes.get(j)));
				transitRouteToRouteIDAndSequence.put(tr.getId(), 
						new Tuple<Integer, Integer>(foundRoute.getRouteId(), foundRoute.getRouteSequence()));
				line.addRoute(tr);
			}
		}
		ts.addTransitLine(line);

		// Step 3: Create the zones
		busFareCal.setFullFare(transitLineId, fullFare);
		
		//Normal section fare.
		Map<Id<TransitRoute>, List<Double>> fares = creator.getFares();
		for (Id<TransitRoute> routeId : fares.keySet()) {
			List<Double> adultFare = fares.get(routeId);
			busFareCal.addRoute(transitLineId, routeId, adultFare.get(0), 
					transitRouteToRouteIDAndSequence.get(routeId).getFirst(),
					transitRouteToRouteIDAndSequence.get(routeId).getSecond()); // Add the route into the transit calculator
			
		}
	}

	public static void createHKIBusRoutes(Scenario scenario, ZonalFareCalculator fareCal) throws IOException {
		BusDataExtractor dataEx = new BusDataExtractor();
		List<Route> HKIroutes = dataEx.getHKIOperatorsAndRoute();

		VehicleType vt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "bus", 11.3, 4, 50 / 3.6, 100,
				30, 1, 1, 3, "Hong Kong Island bus");

		createASetOfRoutes(scenario, fareCal, vt, HKIroutes, true);
//		for (Route route : HKIroutes) {
//			List<Id<Vehicle>> vehicleIDs = getBusLists(scenario.getTransitVehicles(), vt, route.getOperator(),
//					route.getRouteName(), 500);
//			createCertainBusLineWithDiffRoute(scenario, fareCal, vehicleIDs, route.getOperator(), route.getRouteName(),
//					route.getFullFare(), true);
//		}
	}

	public static void createCrossHarbourBusRoutes(Scenario scenario, ZonalFareCalculator fareCal, 
			boolean HKIOnly) throws IOException {
		BusDataExtractor dataEx = new BusDataExtractor();
		List<Route> crossHarbourRoute = dataEx.getOperatorsAndRoute(true);

		VehicleType vt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "crossHarbourbus", 11.3, 4,
				50 / 3.6, 100, 30, 1, 1, 3, "Double-decker bus");
		createASetOfRoutes(scenario, fareCal, vt, crossHarbourRoute, HKIOnly);
//
//		for (Route route : crossHarbourRoute) {
//			List<Id<Vehicle>> vehicleIDs = getBusLists(scenario.getTransitVehicles(), vt, route.getOperator(),
//					route.getRouteName(), 500);
//			createCertainBusLineWithDiffRoute(scenario, fareCal, vehicleIDs, route.getOperator(), 
//					route.getRouteName(), route.getFullFare(), HKIOnly);
//		}
	}
	
	public static void createtAllBusRoutes(Scenario scenario, ZonalFareCalculator fareCal) throws IOException, InterruptedException {
		BusDataExtractor dataEx = new BusDataExtractor();
		List<Route> busRoutes = dataEx.getOperatorsAndRoute(false);

		VehicleType vt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "normalBus", 11.3, 4,
				50 / 3.6, 100, 30, 1, 1, 3, "Double-decker bus");

		createASetOfRoutes(scenario, fareCal, vt, busRoutes, false);
	}
	
	/**
	 * A function to create a set of routes, given by busRoutes. The 
	 * @param scenario
	 * @param fareCal
	 * @param vt
	 * @param busRoutes The set of bus routes.
	 * @param HKIOnly
	 * @throws IOException
	 */
	public static void createASetOfRoutes(Scenario scenario, ZonalFareCalculator fareCal, 
			VehicleType vt, List<Route> busRoutes, boolean HKIOnly) throws IOException {
		//Initialize the bus stop facilities
		BusRoute.initializeStopFacilityLinkCount();
		for(Route route: busRoutes) {
			if(!route.getRouteName().startsWith("H") && !route.getRouteName().equals("314")) {
				getListOfBusRouteAndMap(scenario, route.getOperator(), route.getRouteName(), HKIOnly);
			}
		}
		Map<Id<TransitStopFacility>, List<Tuple<Id<Link>, Integer>>> stopFacilityLinkCount = 
				BusRoute.getStopFacilityLinkCount();
		Map<Id<TransitStopFacility>, Tuple<Coord, String>> stopFacilityCoord = BusRoute.getStopFacilityCoord();
		
		//Create all stop facilities
		for(Id<TransitStopFacility> tsfId: stopFacilityLinkCount.keySet()) {
			List<Tuple<Id<Link>, Integer>> linkIdCounts = stopFacilityLinkCount.get(tsfId);
			Id<Link> maxLinkId = null; //To store the link Id that occurred most.
			int maxInt = 0;
			for(Tuple<Id<Link>, Integer> linkIds: linkIdCounts) {
				if(linkIds.getSecond()>maxInt) {
					maxInt = linkIds.getSecond();
					maxLinkId = linkIds.getFirst();
				}
			}
			CreateBusUtils.createAndAddOrGetTransitStopFacility(scenario.getTransitSchedule(), "bus_"+tsfId.toString(), 
					stopFacilityCoord.get(tsfId).getSecond(), false, 
					maxLinkId, stopFacilityCoord.get(tsfId).getFirst(), false);
		}
		
		for (Route route : busRoutes) {
			if(!route.getRouteName().startsWith("H") && !route.getRouteName().equals("314")) {
				List<Id<Vehicle>> vehicleIDs = createAndAddVehicles(scenario.getTransitVehicles(), vt, route.getOperator(),
						route.getRouteName(), 500);
				createCertainBusLineWithDiffRoute(scenario, fareCal, vehicleIDs, route.getOperator(), 
						route.getRouteName(), route.getFullFare(), HKIOnly);
			}
		}
	}
	

	
	/**
	 * Give the direction from path.
	 * @param path
	 * @param operator
	 * @param name
	 * @return A string of directions, based on the path name.
	 */
	public static String findDirectionFromPath(final String path, String operator, String name) {
		int index = path.indexOf(operator);
		String direction = path.substring(index);
		int startIndex = direction.indexOf(name + " ") + name.length() + 1;
		int endIndex = direction.indexOf(".csv");
		return direction.substring(startIndex, endIndex);
	}

	/**
	 * Find the file path given the operator, name, directory and the direction to.
	 * 
	 * @param operator
	 * @param name
	 * @param dir
	 * @param lastStopName
	 * @return
	 * @throws IOException
	 */
	/*
	@Deprecated
	public static String findFilePath(String operator, String name, String dir, String lastStopName)
			throws IOException {
		String fileName = operator + "  " + name + " To " + lastStopName + ".csv";
		File directory = new File(dir);
		for (File file : directory.listFiles()) {
			if (file.isDirectory()) {
				throw new IllegalArgumentException("WTF!");
			} else if (fileName.equalsIgnoreCase(file.getName())) {
				return file.getCanonicalPath();
				// Two cases, one case is the last stop name is so long that maybe using a
				// shorter one can find
				// Another case is the last stop name is a small district
			} else if (file.getName().matches(operator + "  " + name + " To " + lastStopName.substring(0, 7) + ".*")
					|| file.getName().matches(operator + "  " + name + " To.*\\(" + lastStopName + "\\).csv")) { // Testing
				return file.getCanonicalPath();
			} else {
				List<String> a = Arrays.asList(file.getName().replace(".csv", "").split(" "));
				int terminusIndex = a.indexOf("OR");
				if (terminusIndex != -1) {
					// For the case where the terminus we are finding is after the "OR"
					for (int i = terminusIndex + 1; i < a.size(); i++) {
						if (lastStopName.contains(a.get(i)) && i == a.size() - 1) {
							return file.getCanonicalPath();
						}
						if (lastStopName.contains(a.get(i))) {
							continue;
						} else {
							break;
						}
					}

					// For the case whenever the terminus we are finding is before the "OR"
					for (int i = a.indexOf("To"); i < terminusIndex; i++) {
						if (lastStopName.contains(a.get(i)) && i == a.size() - 1) {
							return file.getCanonicalPath();
						}
						if (lastStopName.contains(a.get(i))) {
							continue;
						} else {
							break;
						}
					}
				}
			}
		}
		throw new IllegalArgumentException("File " + fileName + " not found!");
	}
	*/
	
	private static final String StopBindingsFilePath = "input\\busStopBindings.csv";
	
	/**
	 * reads the target file for stop bindings
	 * @param net
	 * @return
	 * @throws IOException
	 * @author JLo
	 */
	public static HashMap<Integer,List<Id<Link>>> getStopBindings(Network net) throws IOException{
		HashMap<Integer,List<Id<Link>>> stopBindings = new HashMap<Integer, List<Id<Link>>>();
		
		Reader line_in = new FileReader(StopBindingsFilePath);
		Iterable<CSVRecord> nodes = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(line_in);
		
		for(CSVRecord node :nodes) {
			int stopId = Integer.parseInt(node.get("Stop Id"));
			String linkId = node.get("Link Id");
			
			Pattern pattern = Pattern.compile("(BUS-)?a?\\d+_a?\\d+");
			Matcher matcher = pattern.matcher(linkId);
			if(!matcher.find())
				throw new IllegalArgumentException("Link Id of "+stopId+" is not to format!!!");
			
			Id<Link> link = Id.createLinkId(linkId.substring(matcher.start(), matcher.end()));
			if(net.getLinks().containsKey(link))
				stopBindings.put(stopId, Arrays.asList(link));
			
		}
		
		return stopBindings;
	}
	
	/**
	 * Helper function to check the stop bindings list input<br>
	 * <p>Must only be used to check full network, HKIOnly network will throw</p>
	 * Throws illegal argument exception if:
	 * <ul><li> Link does not exist
	 * <li> More than 1 link in list
	 * </ul>
	 * 
	 * @param net
	 * @throws IOException
	 * @author JLo
	 */
	public static void checkStopBindingsValidity(Network net) throws IOException{
		HashMap<Integer, List<Id<Link>>> stopBindingList = getStopBindings(net);
		for(Integer StopId:stopBindingList.keySet()) {
			if(stopBindingList.get(StopId).size()>1)
				throw new IllegalArgumentException("The closeLinkList of "+StopId+" has more than 1 link!!!");
			Id<Link> link = stopBindingList.get(StopId).get(0);
			if(!net.getLinks().containsKey(link))
				throw new IllegalArgumentException("Link of stop "+StopId+" does not exist!!!");
		}
		
	}
}




