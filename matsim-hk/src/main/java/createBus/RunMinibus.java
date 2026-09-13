package createBus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.healthmarketscience.jackcess.Cursor;
import com.healthmarketscience.jackcess.CursorBuilder;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

import createBus.BusDataExtractor.Route;
import createBus.BusHeadway.DepartureList;
import dynamicTransitRouter.fareCalculators.ZonalFareCalculator;
import networkFromSaturn.WGS84toSaturn;
import networkFromSaturn.pt.CreatePTUtils;

/**
 * This class is intended to create the minibus.
 * 
 * @author eleead
 *
 */
public class RunMinibus {
	public static String PATH = "C:\\Users\\eleead\\workspace\\MATSim-HK\\minibus\\";
//	public static String PATH = "D:\\eclipse-workspace\\intern-project\\minibus";	//JLo
	public static String STOPS_PATH = PATH + "stop\\";
	public static String HEADWAY_PATH = PATH+ "Headway\\";
	public static String FARE_PATH = PATH+ "fare\\";
	public static String ROUTEFILE_PATH = PATH+ "ROUTE_GMB.mdb";
	public static final double SEARCH_DISTANCE = 40;

	public static int numOfGMBStops = 0;
	
	/**
	 * For other usage of the code for network generation, they can set their own path for the files needed.
	 * @param path Path of the minibus files.
	 */
	public static void setPath(String path) {
		PATH = path;
		STOPS_PATH = PATH + "stop\\";
		HEADWAY_PATH = PATH+ "Headway\\";
		FARE_PATH = PATH+ "fare\\";
		ROUTEFILE_PATH = PATH+ "ROUTE_GMB.mdb";
	}

	static List<Id<Vehicle>> getMiniBusLists(Vehicles vehicles, VehicleType vt, String district, String name,
			int numBus) {
		List<Id<Vehicle>> vehicleIDs = new ArrayList<Id<Vehicle>>();
		VehiclesFactory vehiclesFactory = vehicles.getFactory();
		for (int i = 0; i < numBus; i++) {
			Id<Vehicle> newBusId = Id.create("Minibus" + district+"_"+name + "_" + i, Vehicle.class);
			Vehicle newBus = vehiclesFactory.createVehicle(newBusId, vt);
			vehicles.addVehicle(newBus);
			vehicleIDs.add(newBusId);
		}

		return vehicleIDs;
	}

	/**
	 * 
	 * @param scenario
	 * @param operator
	 * @param routeName
	 * @param routeList
	 * @return
	 * @throws IOException
	 */
	public static MiniBusRoute createMiniBusRoute(Scenario scenario, String district, String routeName,
			List<MinibusStop> routeList) throws IOException {
		BusStop prev = null;
		BusPathCalculator routingAlgo = new L2lLeastCostCalculatorFactory(scenario,
				Sets.newHashSet(TransportMode.car, "bus")).getRoutingAlgo(); // The least cost path is calculated by the distance

		MiniBusRoute route = new MiniBusRoute(scenario, "GMB", district, routeName, routingAlgo); // TODO: Probably make
																									// a reference for
																									// it in injector

		for (MinibusStop stop : routeList) {
			Coord coord = stop.getCoord(); // The coordinate is the stop coordinate
			String stopIDString = "GMB_" + numOfGMBStops; // The stop ID is GMB_XXX, XXX is the number of stop.

			List<Id<Link>> closeLinksIdList = BusDataExtractor.getNearestLinksExactlyByMath(scenario.getNetwork(), coord, 
					SEARCH_DISTANCE, Sets.newHashSet(TransportMode.car, "bus"));

			BusStop curr = new BusStop("minibus", stopIDString, stop.getStopName(), coord, closeLinksIdList);

			if (prev != null && prev.getStopId().equals(curr.getStopId())) {
				throw new IllegalArgumentException("Two consecutive stops in the same station!");
			}
			route.addBusStop(prev, curr);

			prev = curr;
			numOfGMBStops++;
		}
		route.mapBusStop();
		return route;
	}

	/**
	 * A hardcode function to map the direction to the correct one.
	 * 
	 * @param district
	 *            District of the minibus route (HK / KLN / NT)
	 * @param name
	 *            Name of the minibus route (e.g. 16A)
	 * @param direction
	 *            (e.g. Ma Hang / Chung Hom Kok)
	 * @return
	 */
	private static String modifyDirection(String district, String name, String direction) {
		if (district.equals("HKI") && name.equals("16M") && direction.contains("Ma Hang / Chung Hom Kok")) {
			return direction.replace("Ma Hang / Chung Hom Kok", "Chung Hom Kok");
		} else if (district.equals("HKI") && name.equals("29A") && direction.contains("Ocean Park (Tai Shue Wan)")) {
			return direction.replace("Ocean Park (Tai Shue Wan)", "Shum Wan Road (Tai Shue Wan)");
		} else if (district.equals("HKI") && name.equals("40") && direction.contains("Stanley")) {
			return direction.replace("Stanley", "Stanley Village");
		}
		return direction;
	}
	
	private static List<Tuple<String, String>> equals = Lists.newArrayList(new Tuple("Tung Yan Street Temporary Public Light Bus \"Scheduled\" Service Terminus", "Tung Yan Street"),
			new Tuple("Festival Walk Public Transport Interchange", "Tat Chee Avenue (Public Transport Interchange)"), 
			new Tuple("Ho Man Tin (Sheung Foo Street)", "Ho Man Tin Public Transport Interchange"),
			new Tuple("Park Avenue", "MONG KOK (PARK AVENUE) BUS TERMINUS"),
			new Tuple("Sau Mau Ping Estate", "Sau Mau Ping Estate (Phase 5)"),
			new Tuple("Kwun Tong (Tung Yan Street Temporary GMB Terminus)", "Kwun Tong (Tung Yan Street)"),
			new Tuple("Hong Sing", "Hong Sing Garden"),
			new Tuple("Hang Hau (North)", "Hang Hau"),
			new Tuple("Tuen Mun Ferry Pier", "Tuen Mun Ferry Pier (Public Light Bus (Scheduled Services) Terminus on Wu Shan Road)"),
			new Tuple("Public Light Bus", "PLB"),
			new Tuple("LMCSL PTI", "Lok Ma Chau Spur Line Public Transport Interchange"),
			new Tuple("/", " or "),
			new Tuple("San Tsuen", "New Village"),
			new Tuple("Hong Kong United Dockyard", "Sai Tso Wan Road (Hong Kong United Dockyards)"),
			new Tuple("Tsz Wan Shan (North)", "Tsz Wan Shan (Shatin Pass Estate)"),
			new Tuple("Cyberport Public Transport Interchange", "Tin Wan Estate"),
			new Tuple("Kowloon Bay (Zero Carbon Building)", "Kowloon Bay(Sheung Yee Road)"));
	
	/**
	 * Obtain the equal degree of the stop name and the file name
	 * @param fileName
	 * @param district
	 * @param name
	 * @param fromDirection
	 * @param toDirection
	 * @return 0: Not equal, 1: Not that equal, 2: Quite equal, 3: Very equal
	 */
	private static int stopNameEqual(String fileName, String district, String name, String fromDirection, String toDirection) {
		List<String> district_name = Lists.newArrayList( fileName.split(" From") );
		if(district_name.size()!=2) {
			throw new IllegalArgumentException("Need to check!");
		}
		String proposedFileName = fromDirection + toDirection + ".csv";

		if( ("GMB " + district + " " + name).equals(district_name.get(0)) ) {
			if(new LevenshteinDistance(2).apply("From"+ district_name.get(1), proposedFileName) != -1 ){
				return 3;
			}
			List<String> fileFromNameParts = Lists.newArrayList(fromDirection.replace("Public Transport Interchange", "")
					.replace("Bus Terminus", "").split("[ |\\(|\\)|\\.]"));
			fileFromNameParts.add("");
			fileFromNameParts.replaceAll(String::toUpperCase);
			List<String> fileToNameParts = Lists.newArrayList( (toDirection+ ".csv").replace("Public Transport Interchange", "")
					.replace("Bus Terminus", "").split("[ |\\(|\\)|\\.]"));
			fileToNameParts.add("");
			fileToNameParts.replaceAll(String::toUpperCase);
			String[] names = district_name.get(1).split(" To (?!Kwa )");
			if(names.length!=2) {
				throw new IllegalArgumentException("");
			}
			List<String> fileFromStringFromStop = Lists.newArrayList(names[0].split("[ |\\(|\\)|\\.]"));
			List<String> fileFromStringToStop = Lists.newArrayList(names[1].split("[ |\\(|\\)|\\.]"));
			fileFromStringFromStop.add("FROM"); fileFromStringFromStop.add(""); fileFromStringFromStop.replaceAll(String::toUpperCase); 
			fileFromStringToStop.add("TO"); fileFromStringToStop.add("");fileFromStringToStop.replaceAll(String::toUpperCase);
			if( (fileFromNameParts.containsAll(fileFromStringFromStop) || fileFromStringFromStop.containsAll(fileFromNameParts)) &&
					(fileToNameParts.containsAll(fileFromStringToStop) || fileFromStringToStop.containsAll(fileToNameParts)) ) {
				return 1;
			}
			for(Tuple<String, String> equal: equals) {
				if(new LevenshteinDistance(2).apply(("From"+ district_name.get(1)).toUpperCase(), proposedFileName.replace(equal.getFirst(), equal.getSecond()).toUpperCase()) != -1 ||
						new LevenshteinDistance(2).apply(("From"+ district_name.get(1)).toUpperCase(), proposedFileName.replace(equal.getSecond(), equal.getFirst()).toUpperCase()) != -1){
					return 2;
				}
			}
		}
		return 0;
	}
	
	private static boolean stopPathEquivalent(String firstStop, String secondStop) {
		for(Tuple<String, String> equal: equals) {
			if(firstStop.toUpperCase().contains(secondStop.replace(equal.getFirst(), equal.getSecond()).toUpperCase()) ||
					firstStop.toUpperCase().contains(secondStop.replace(equal.getSecond(), equal.getFirst()).toUpperCase())){
				return true;
			}
		}
		return false;
	}

	private static List<MinibusStop> readMinibusStopFile(String district, String name, String fromDirection,
			String toDirection, boolean circular) throws IOException {
		fromDirection = modifyDirection(district, name, fromDirection);
		toDirection = modifyDirection(district, name, toDirection);

		List<MinibusStop> minibusStop = new ArrayList<MinibusStop>();
		String filePath = null;
		int filePathEqualRate = -1;
		Reader line_in = null;
		if (toDirection != null) {
			try {
				filePath = STOPS_PATH + "GMB " + district + " " + name + " " + fromDirection + toDirection + ".csv";
				line_in = new FileReader(filePath);
			} catch (FileNotFoundException e) {
				filePath = null; // Reset the filePath
				File dir = new File(STOPS_PATH);
				for (File f : dir.listFiles()) {
					int equalRate = stopNameEqual(f.getName(),district, name, fromDirection, toDirection);
					if (equalRate>0) {
						if (filePath != null) {
							if( (filePath.contains("via") && !f.getAbsolutePath().contains("via")) ||
									(filePath.contains("omit") && !f.getAbsolutePath().contains("omit")) ||
									filePathEqualRate < equalRate) {
								filePath = f.getAbsolutePath();
								filePathEqualRate = equalRate;
							}else if( (!filePath.contains("via") && f.getAbsolutePath().contains("via")) ||
									(!filePath.contains("omit") && f.getAbsolutePath().contains("omit")) ||
									filePathEqualRate > equalRate) {
								//Do nothing
							}else {
								throw new RuntimeException("Two files are found!");
							}
						}else {
							filePath = f.getAbsolutePath();
							filePathEqualRate = equalRate;
						}
					}
				}
				line_in = new FileReader(filePath);
			}
		} else {
			// Find the file
			File dir = new File(STOPS_PATH);
			for (File f : dir.listFiles()) {
				String currFileName = f.getName().replaceAll("\\( ", "\\(").toUpperCase(); //The file name processed
				String proposedName = ("GMB " + district + " " + name + " " + fromDirection.replace("\\( ", "\\(") ) .toUpperCase(); //The proposed file name given district and name.
				if (currFileName.contains( proposedName.substring(0,20) ) || stopPathEquivalent(currFileName, proposedName)) {
					if (filePath != null) {
						if( (filePath.toUpperCase().contains( proposedName ) && !currFileName.contains( proposedName )) || 
								(circular && filePath.contains("Circular") && !currFileName.contains("Circular")) ||
								(filePath.toLowerCase().contains("via") && !currFileName.toLowerCase().contains("via")) ||
								(!filePath.toLowerCase().contains("omit") && currFileName.toLowerCase().contains("omit"))) {
							//Do nothing, as the original one is better
						}else if( (!filePath.toUpperCase().contains( proposedName ) && currFileName.toUpperCase().contains( proposedName )) ||
									(circular && !filePath.contains("Circular") && currFileName.contains("Circular") ) ||
										(!filePath.toLowerCase().contains("via") && currFileName.toLowerCase().contains("via")) ||
										(filePath.toLowerCase().contains("omit") && !currFileName.toLowerCase().contains("omit")) ){
							filePath = f.getAbsolutePath();
						}else
							throw new RuntimeException("Two files are found!");
					}else {
						filePath = f.getAbsolutePath();
					}
				}
			}
			if (!circular || filePath != null) { // The circular would be granted a chance.
				line_in = new FileReader(filePath);
			} else {
				return null;
			}
		}
		Iterable<CSVRecord> nodes = CSVFormat.RFC4180.withAllowMissingColumnNames().withFirstRecordAsHeader().parse(line_in);

		for (CSVRecord node : nodes) {
			minibusStop.add(new MinibusStop(node.get("Stop"), node.get("LAT"), node.get("LON")));
		}
		return minibusStop;
	}

	public static void createMinibusLine(Scenario scenario, ZonalFareCalculator busFareCal,
			List<Id<Vehicle>> vehicleIDs, String district, String name, double fullFare) throws IOException {
		TransitSchedule ts = scenario.getTransitSchedule();
		MiniBusRoute.initializeStopFacilityLinkCount();
		Id<TransitLine> lineId = Id.create("GMB_" + district + "_" + name, TransitLine.class);
		TransitLine line = ts.getFactory().createTransitLine(lineId);

		// Step 1: We start with the headway.
		List<String> headWayFilePaths = CreateBusUtils.findFilePaths("GMB " + district, name, HEADWAY_PATH, "headway");
		String headwayFilePath = headWayFilePaths.get(0); // Only one filePath is found.
		boolean isCircular = headwayFilePath.contains("Circular route");
		BusHeadway headway = new BusHeadway("minibus");
		headway.makeMinibusDepartures(headwayFilePath);
		DepartureList depAndRoute = headway.getDepartures();
		List<Id<TransitRoute>> uniqueRoutes = depAndRoute.getUniqueRoutes();

		// Step 2: Create the routes that appears in headway
		for (int j = 0; j < uniqueRoutes.size(); j++) {
			// Obtain the minibus stop list.
			String[] directionList = uniqueRoutes.get(j).toString().split(" To (?!Kwa )");
			String fromDirection = directionList[0] + " ";
			String toDirection = directionList.length == 2 && !isCircular
					? new StringBuilder(directionList[1]).insert(0, "To ").toString()
					: null;

			List<MinibusStop> miniBusStops = readMinibusStopFile(district, name, fromDirection, toDirection,
					isCircular);
			if (miniBusStops == null) {
				if (!isCircular) {
					throw new RuntimeException("The minibus stops should not be empty!");
				} else {
					continue;
				}
			}

			// Create the route.
			MiniBusRoute minibusRoute = createMiniBusRoute(scenario, district, name, miniBusStops);
			TransitRoute tr = minibusRoute.createRouteFromMap(scenario, uniqueRoutes.get(j), vehicleIDs, 0,
					depAndRoute.getDepartureAndOrderForRoute(uniqueRoutes.get(j)));
			line.addRoute(tr);
		}
		if (line.getRoutes().isEmpty() && !uniqueRoutes.isEmpty()) {
			return;
			//throw new RuntimeException("The minibus route should not be empty!");
		}else if(uniqueRoutes.isEmpty()) {
			return;
		}
		ts.addTransitLine(line);

		// Step 3: Dual with the fare
		busFareCal.setFullFare(lineId, fullFare); // TODO: Set the session fare
	}
	
	public static List<Route> getAllMinibusRoute() throws IOException{
		List<Route> routes = new LinkedList<Route>();

		Database db = DatabaseBuilder.open(new File(ROUTEFILE_PATH));
		Table table = db.getTable("ROUTE");
		Cursor cursor = CursorBuilder.createCursor(table);
		cursor.beforeFirst();
		while (cursor.moveToNextRow()) {
			Row currRow = cursor.getCurrentRow();

			String district = currRow.getString("DISTRICT");
			String routeName = currRow.getString("ROUTE_NAMEE");
			Route currRoute = new BusDataExtractor().new Route(district, routeName,currRow.getInt("ROUTE_ID"),
					currRow.getBigDecimal("FULL_FARE").doubleValue());
			if (!routes.contains(currRoute) && !( currRoute.getOperator()=="HKI" && currRoute.getOperator()=="36X")) {
				routes.add(currRoute);
			} else {
				Route prevRoute = routes.get(routes.indexOf(currRoute));
				double fullFare = currRow.getBigDecimal("FULL_FARE").doubleValue();
				if (fullFare > prevRoute.getFullFare()) {
					prevRoute.setFullFare(fullFare);
				}
			}
		}
		return routes;
	}

	public static List<Route> getMiniBusHKOperatorsAndRoute() throws IOException {
		List<Route> routes = new LinkedList<Route>();

		Database db = DatabaseBuilder.open(new File(ROUTEFILE_PATH));
		Table table = db.getTable("ROUTE");
		Cursor cursor = CursorBuilder.createCursor(table);
		cursor.beforeFirst();
		while (cursor.moveToNextRow()) {
			Row currRow = cursor.getCurrentRow();

			String district = currRow.getString("DISTRICT");

			// We only consider the HKI district
			if (!district.equals("HKI")) {
				continue;
			}

			// The bus route ID and fare is here.
			String routeName = currRow.getString("ROUTE_NAMEE");
			Route currRoute = new BusDataExtractor().new Route(district, routeName,currRow.getInt("ROUTE_ID"),
					currRow.getBigDecimal("FULL_FARE").doubleValue());
			if (!routes.contains(currRoute)) {
				routes.add(currRoute);
			} else {
				Route prevRoute = routes.get(routes.indexOf(currRoute));
				double fullFare = currRow.getBigDecimal("FULL_FARE").doubleValue();
				if (fullFare > prevRoute.getFullFare()) {
					prevRoute.setFullFare(fullFare);
				}
			}
		}
		return routes;
	}

	/**
	 * A convenient function that creates all HKI minibuses fleets and routes
	 * 
	 * @param scenario
	 * @param fareCal
	 * @throws IOException
	 */
	public static void createHKIMinibusRoutes(Scenario scenario, ZonalFareCalculator fareCal) throws IOException {
		List<Route> routes = getMiniBusHKOperatorsAndRoute();
		VehicleType vt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "minibus", 8, 3.5, 70 / 3.6, 16,
				0, 1, 1, 1.5, "Minibus");

		for (Route route : routes) {
			if(route.getOperator().equals("HKI") && (route.getRouteName().equals("36X") || route.getRouteName().equals("36S") || 
					route.getRouteName().equals("37A"))) {
				continue;
			}
			List<Id<Vehicle>> vehicleIDs = getMiniBusLists(scenario.getTransitVehicles(), vt, route.getOperator(),
					route.getRouteName(), 500);
			createMinibusLine(scenario, fareCal, vehicleIDs, route.getOperator(), route.getRouteName(),
					route.getFullFare());
		}
		
		allowMinibusOnBusLinks(scenario.getNetwork());
	}
	
	/**
	 * A convenient function that creates all HKI minibuses fleets and routes
	 * 
	 * @param scenario
	 * @param fareCal
	 * @throws IOException
	 */
	public static void createAllMinibusRoutes(Scenario scenario, ZonalFareCalculator fareCal) throws IOException {
		List<Route> routes = getAllMinibusRoute();
		routes.add(new BusDataExtractor().new Route("NT","98C", 1231, 4.6));
		VehicleType vt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "minibus", 8, 3.5, 70 / 3.6, 16,
				0, 1, 1, 1.5, "Minibus");

		for (Route route : routes) {
			if( (route.getOperator().equals("HKI") && (route.getRouteName().equals("36X") || route.getRouteName().equals("36S") || 
					route.getRouteName().equals("37A"))) || (route.getOperator().equals("NT") && route.getRouteName().equals("807P")) 
					|| (route.getOperator().equals("KLN") && route.getRouteName().equals("85"))) {
				continue;
			}
			List<Id<Vehicle>> vehicleIDs = getMiniBusLists(scenario.getTransitVehicles(), vt, route.getOperator(),
					route.getRouteName(), 1000);
			createMinibusLine(scenario, fareCal, vehicleIDs, route.getOperator(), route.getRouteName(),
					route.getFullFare());
		}
		
		allowMinibusOnBusLinks(scenario.getNetwork());
	}
	
	private static void allowMinibusOnBusLinks(Network net) {
		for(Link TL: net.getLinks().values()) {
			Set<String> allowedModes = Sets.newHashSet(TL.getAllowedModes());
			if(allowedModes.contains("bus")) {
				allowedModes.add("minibus");
				TL.setAllowedModes(allowedModes);
			}	
		}
	}

	private static class MinibusStop {
		private final String stopName;
		private final double lat;
		private final double lon;

		public MinibusStop(String stopName, String latitude, String longtitude) {
			this(stopName, Double.parseDouble(latitude), Double.parseDouble(longtitude));
		}

		public MinibusStop(String stopName, double latitude, double longtitude) {
			this.stopName = stopName;
			this.lat = latitude;
			this.lon = longtitude;
		}

		public Coord getCoord() {
			CoordinateTransformation ct = new WGS84toSaturn();
			return ct.transform(new Coord(this.lat, this.lon));
		}

		public String getStopName() {
			return this.stopName;
		}
	}

}
