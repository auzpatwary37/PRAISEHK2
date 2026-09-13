/**
 * 
 */
package createPTGTFS;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.core.utils.collections.Tuple;

/**
 * Custom reader specifically for building buses<br>
 * Supports HKI-Only
 * @author JLo
 *
 */
public class ReaderBus extends ReaderPT {
	private boolean HKIonly = false;
	private HashMap<String, Tuple<Integer, String>> HKIChangedStops;	//necessary as stopsMap change during stom_timesReader does not reflect to fareReader when it runs
	
	public ReaderBus(String FileDir, boolean HKIonly) {
		super(FileDir);
		this.HKIonly = HKIonly;
		if(HKIonly) {
			this.agencyWhitelist = Arrays.asList("CTB", "NWFB", "KMB+CTB", "KMB+NWFB", "KMB");
			this.HKIChangedStops = new HashMap<String, Tuple<Integer, String>>();
		} else
			this.agencyWhitelist = Arrays.asList("CTB", "KMB", "KMB+CTB", "KMB+NWFB", "LWB+CTB", "NLB", "NWFB", "PI", "LWB", "DB", "LRTFeeder", "XB");
	}
	
	/**
	 * reads routes file to extract umbrella routes <br>
	 * skips non-HKIonly and non-crossHarbour if HKIonly
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	@Override
	protected void routesReader() throws FileNotFoundException, IOException {
		log.info("Starting to read Bus routes");
		CSVParser parser = new CSVParser(new FileReader(FileDir+"routes.txt"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		List<CSVRecord> list = parser.getRecords();
		for(CSVRecord record:list) {
			if(agencyWhitelist.contains(record.get("agency_id"))) {
				String routeName = record.get("route_short_name");
				if(HKIonly) {	//Sacrificing rows for less code invoked
					if(isRouteHKI(routeName, record.get("agency_id")) || isRouteCrossHarbour(routeName)) {
						DataRoutePT TempPTDataRoute = new DataRoutePT(routeName, record.get("agency_id"), record.get("route_id"));
						if(!ptDataContainer.addPTDataRoute(TempPTDataRoute))
							ptDataContainer.getPTDataRouteMap().get(TempPTDataRoute.getId()).getRouteIds().add(record.get("route_id"));
					}
				} else {
					DataRoutePT TempPTDataRoute = new DataRoutePT(routeName, record.get("agency_id"), record.get("route_id"));
					if(!ptDataContainer.addPTDataRoute(TempPTDataRoute))
						ptDataContainer.getPTDataRouteMap().get(TempPTDataRoute.getId()).getRouteIds().add(record.get("route_id"));
				}
			}
		}
		parser.close();
		log.info("Finished reading Bus routes");
	}
	
	/**
	 * reads stop_times file to extract headway based "indi-route" and identify unique routes<br>
	 * cleans the route of duplicated consecutive stops without renumbering the order<br>
	 * cut short CrossHarbour routes if HKIonly
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	@Override
	protected void stop_timesReader() throws FileNotFoundException, IOException {
		log.info("Starting to read Bus stop_times");
		long counter = 0;
		//read all the data
		CSVParser parser = new CSVParser(new FileReader(FileDir+"stop_times.txt"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		List<CSVRecord> list = parser.getRecords();
		for(CSVRecord record:list) {
			//for logging
			counter++;
			if(counter%100000==0)
				log.info("Reading stop_times line #"+counter);
			
			DataRoutePT TempPTDataRoute = this.ptDataContainer.getPTDataRouteBytrip_id(record.get("trip_id"));
			if(TempPTDataRoute != null)
				TempPTDataRoute.putStopId(record.get("trip_id"), Integer.parseInt(record.get("stop_sequence")), record.get("stop_id"));
		}
		parser.close();
		log.info("Finished reading Bus stop_times");
		
		//clean duplicated stops
		//cut short the routes that are Cross Harbour
		for(DataRoutePT TempPTDataRoute: this.ptDataContainer.getPTDataRouteMap().values()) {
			TempPTDataRoute.cleanDuplicateStops();
			if(this.HKIonly && isRouteCrossHarbour(TempPTDataRoute.getName()))
				for(String TripStopsKey: TempPTDataRoute.getStopsMap().keySet())
					findTunnelAndDirectionAndCutShort(TempPTDataRoute, TripStopsKey);
		}
		log.info("Finished cleaning up duplicated Bus route stops");
	}
	
	/**
	 * reads stops file to extract stop locations appeared in trips<br>
	 * <b>Stores in BusStop sub-class</b>
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	@Override
	protected void stopsReader() throws FileNotFoundException, IOException {
		log.info("Starting to read Bus stops");
		//Find used stops from PTDataRouteMap
		TreeSet<String> usedStops = new TreeSet<String>();
		for(DataRoutePT TempPTDataRoute: this.ptDataContainer.getPTDataRouteMap().values())
			for(TreeMap<Integer, String> Temp: TempPTDataRoute.getStopsMap().values())
				usedStops.addAll(Temp.values());
		//extract the used stops
		CSVParser parser = new CSVParser(new FileReader(FileDir+"stops.txt"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		List<CSVRecord> list = parser.getRecords();
		for(CSVRecord record:list) {
			if(usedStops.contains(record.get("stop_id")))
				this.ptDataContainer.addPTDataStop(new DataStopBus(record.get("stop_id"), record.get("stop_name").replace("&", "AND"), Double.parseDouble(record.get("stop_lat")), Double.parseDouble(record.get("stop_lon"))));
		}
		parser.close();
		log.info("Finished reading Bus stops");
	}
	
	/**
	 * reads fare_attributes & fare_rules to extract fare costs<br>
	 * changes stopId if due to HKIOnly
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	@Override
	protected void fareReader() throws FileNotFoundException, IOException {
		log.info("Starting to read fare");
		Pattern smallRoutepattern = Pattern.compile("\\d+-\\d");
		Pattern routeIdPattern = Pattern.compile("\\d+-");
		
		//read fare_rules first
		CSVParser parser = new CSVParser(new FileReader(FileDir+"fare_rules.txt"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		List<CSVRecord> list = parser.getRecords();
		for(CSVRecord record:list) {
			DataRoutePT PTDataRoute = this.ptDataContainer.getPTDataRouteByroute_id(record.get("route_id"));
			if(PTDataRoute != null) {
				//get short route id
				Matcher matcher = smallRoutepattern.matcher(record.get("fare_id"));
				matcher.find();
				//store the record
				if(!HKIonly)
					PTDataRoute.getOrCreatePTDataFare(matcher.group()).newFareDataGTFS(record.get("fare_id"), record.get("origin_id"), record.get("destination_id"));
				else {
					if(this.HKIChangedStops.containsKey(matcher.group())) {
						Tuple<Integer, String> info = this.HKIChangedStops.get(matcher.group());
						//finds the stop seq of this fare_id
						String fare_id = record.get("fare_id");
						int i = fare_id.indexOf("-");	//finds the "-" between the small route id
						i = fare_id.indexOf("-", i+1);	//finds the "-" after the small route id and should be the one before the from stop seq
						int i2 = fare_id.indexOf("-", i+1);
						int fromStopSeq = Integer.parseInt(fare_id.substring(i+1, i2));
						int toStopSeq = Integer.parseInt(fare_id.substring(i2+1));
						//replaces(or not) the OD stopID
						if(fromStopSeq == info.getFirst())
							PTDataRoute.getOrCreatePTDataFare(matcher.group()).newFareDataGTFS(record.get("fare_id"), info.getSecond(), record.get("destination_id"));
						else if(toStopSeq == info.getFirst())
							PTDataRoute.getOrCreatePTDataFare(matcher.group()).newFareDataGTFS(record.get("fare_id"), record.get("origin_id"), info.getSecond());
						else
							PTDataRoute.getOrCreatePTDataFare(matcher.group()).newFareDataGTFS(record.get("fare_id"), record.get("origin_id"), record.get("destination_id"));
					} else
						PTDataRoute.getOrCreatePTDataFare(matcher.group()).newFareDataGTFS(record.get("fare_id"), record.get("origin_id"), record.get("destination_id"));
				}
			}
		}
		parser.close();
		//then read fare_attributes
		parser = new CSVParser(new FileReader(FileDir+"fare_attributes.txt"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		list = parser.getRecords();
		for(CSVRecord record:list) {
			Matcher matcher2 = routeIdPattern.matcher(record.get("fare_id"));
			matcher2.find();
			String routeId = matcher2.group().substring(0, matcher2.group().length()-1);
			DataRoutePT PTDataRoute = this.ptDataContainer.getPTDataRouteByroute_id(routeId);
			if(PTDataRoute != null) {
				//get short route id
				Matcher matcher = smallRoutepattern.matcher(record.get("fare_id"));
				matcher.find();
				//store the record
				if(PTDataRoute.containsPTDataFare(matcher.group()))
					PTDataRoute.getOrCreatePTDataFare(matcher.group()).addFare(record.get("fare_id"), Double.parseDouble(record.get("price")));
			}
		}
		parser.close();
		log.info("Finished reading fare");
		
	}
	
	/**
	 * A helper function to determine if a route is HKI or not by its name and agency id.
	 * 
	 * @param routeName
	 * @return
	 */
	private static boolean isRouteHKI(String routeName, String agencyId) {
		if(agencyId.equalsIgnoreCase("CTB") || agencyId.equalsIgnoreCase("NWFB"))
			if ( ( routeName.matches("\\d\\d\\p{Alpha}?") || routeName.matches("\\d\\p{Alpha}?")
					|| routeName.matches("7[1-8]\\d\\p{Alpha}?") || routeName.matches("5\\d\\d\\p{Alpha}?")
					) && !routeName.matches("2(0|2)"))
				return true;
		return false;
	}
	
	/**
	 * A helper function to determine if a route is cross harbour or not by its name.
	 * 
	 * @param routeName - takes the route short name (the number bit)
	 * @return
	 */
	private static boolean isRouteCrossHarbour(String routeName) {
		if (routeName.matches("6\\d\\d\\p{Alpha}?") || routeName.matches("3(0|7)\\d\\p{Alpha}?")
				|| routeName.matches("1\\d\\d\\p{Alpha}?") || routeName.matches("9\\d\\d\\p{Alpha}?")
				|| routeName.equals("W1") 	) {
			if (!routeName.equals("629"))
				return true;
		}
		return false;
	}
	
	/**
	 * Terrible hard coding of identifying cross harbour routes and cutting short them
	 * @param routeSeq
	 * @param routeName
	 * @return True if it is entering HKI
	 */
	private void findTunnelAndDirectionAndCutShort(DataRoutePT PTDataRoute, String TripStopsKey) {
		String routeName = PTDataRoute.getName();
		boolean crossHarbourFromHKI = false;
		boolean crossHarbourToHKI = false;
		int stop_seq = -1;
		boolean noStopFlag = false;
		TreeMap<Integer, String> StopsList = PTDataRoute.getStopsMap().get(TripStopsKey);
		// First run for one time to figure out which direction
		for (Entry<Integer,String> stop : StopsList.entrySet()) {
			int stopID = Integer.parseInt(stop.getValue());
			// An exception for 621 619X 962C 969C 101X 182X W1, which does not stop at the Bus-Interchange stations.
			if ((routeName.equals("621") && stopID == 1361) || (routeName.equals("619X") && stopID == 1124)) {
				crossHarbourToHKI = true;
				noStopFlag = true;
				stop_seq = stop.getKey().intValue();
				break;
			} else if ((routeName.equals("962C") && stopID == 9773)
					|| (routeName.equals("969C") && stopID == 9895)) {
				crossHarbourToHKI = true;
				noStopFlag = true;
				stop_seq = stop.getKey().intValue();
				break;
			} else if (routeName.equals("101X") && stopID == 10337) {
				crossHarbourToHKI = true;
				noStopFlag = true;
				stop_seq = stop.getKey().intValue();
				break;
			} else if (routeName.equals("182X") && stopID == 845) {
				crossHarbourFromHKI = true;
				noStopFlag = true;
				stop_seq = stop.getKey().intValue();
				break;
			} else if (routeName.equals("W1") && stopID == 13342) {
				if(stop.getKey().intValue() == 2) {
					crossHarbourToHKI = true;
					noStopFlag = true;
					stop_seq = stop.getKey().intValue();
					break;
				} else if(stop.getKey().intValue() == 3){
					crossHarbourFromHKI = true;
					noStopFlag = true;
					stop_seq = stop.getKey().intValue();
					break;
				}
					
			}

			if (stopID == 1063 || stopID == 9015 || stopID == 921) {
				crossHarbourFromHKI = true;
				stop_seq = stop.getKey().intValue();
				break;
			}else if (stopID == 885 || stopID == 9014 || stopID == 219 || stopID == 911) {
				crossHarbourToHKI = true;
				stop_seq = stop.getKey().intValue();
				break;
			}
		}
		
		if((!crossHarbourFromHKI && !crossHarbourToHKI) || (crossHarbourFromHKI && crossHarbourToHKI) || stop_seq == -1)
			throw new RuntimeException("There's something wrong with with this stop list of route "+routeName+" !");
		
		//replace 621 619X 962C 969C 101X 182X W1 non-HKI stop to one of the CHBI stations
		//Entry of treeMap does not support setValue
		//Stores the changes in class for fareReader
		if(noStopFlag) {
			if(routeName.equals("621") || routeName.equals("619X")) {
				StopsList.replace(Integer.valueOf(stop_seq), "885");
				this.HKIChangedStops.put(TripStopsKey, new Tuple<Integer, String>(stop_seq, "885"));
			} else if(routeName.equals("962C") || routeName.equals("969C")) {
				StopsList.replace(Integer.valueOf(stop_seq), "911");
				this.HKIChangedStops.put(TripStopsKey, new Tuple<Integer, String>(stop_seq, "911"));
			} else if(routeName.equals("101X")) {
				StopsList.replace(Integer.valueOf(stop_seq), "9014");
				this.HKIChangedStops.put(TripStopsKey, new Tuple<Integer, String>(stop_seq, "9014"));
			} else if(routeName.equals("182X")) {
				StopsList.replace(Integer.valueOf(stop_seq), "9015");
				this.HKIChangedStops.put(TripStopsKey, new Tuple<Integer, String>(stop_seq, "9015"));
			} else if(routeName.equals("W1")) {
				if(crossHarbourToHKI) {
					StopsList.replace(Integer.valueOf(stop_seq), "911");
					this.HKIChangedStops.put(TripStopsKey, new Tuple<Integer, String>(stop_seq, "911"));
				} else if(crossHarbourFromHKI) {
					StopsList.replace(Integer.valueOf(stop_seq), "921");
					this.HKIChangedStops.put(TripStopsKey, new Tuple<Integer, String>(stop_seq, "921"));
				}
			}
		}
		
		//then cut it short using CHBI stations as last/first stop
		Iterator<Entry<Integer, String>> itr = StopsList.entrySet().iterator();
		while(itr.hasNext()) {
			Entry<Integer, String> stop = itr.next();
			if(crossHarbourFromHKI) {
				if(stop.getKey().intValue()>stop_seq)
					itr.remove();
			}else if(crossHarbourToHKI) {
				if(stop.getKey().intValue()<stop_seq)
					itr.remove();
			}
		}
	}
	

}




















