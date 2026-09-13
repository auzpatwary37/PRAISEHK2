/**
 * 
 */
package createPTGTFS;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;


/**
 * @author JLo
 *
 */
public class ReaderPT {
	protected String FileDir;
	protected List<String> agencyWhitelist;
	protected DataContainerPT ptDataContainer;
	protected static final Logger log = Logger.getLogger(ReaderPT.class);
	
	protected ReaderPT(String FileDir) {
		this.ptDataContainer = new DataContainerPT();
		this.FileDir = FileDir;
		this.agencyWhitelist = null;
	}
	
	public ReaderPT(String FileDir, List<String> agencyWhitelist) {
		this.ptDataContainer = new DataContainerPT();
		this.FileDir = FileDir;
		this.agencyWhitelist = agencyWhitelist;
	}
	
	public DataContainerPT getData() throws FileNotFoundException, IOException {
		routesReader();
		calendarReader();
		tripsReader();
		stop_timesReader();
		frequenciesReader();
		stopsReader();
		fareReader();
		return this.ptDataContainer;
	}
	
	/**
	 * reads routes file to extract umbrella routes
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	protected void routesReader() throws FileNotFoundException, IOException {
		log.info("Starting to read routes");
		CSVParser parser = new CSVParser(new FileReader(FileDir+"routes.txt"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		List<CSVRecord> list = parser.getRecords();
		for(CSVRecord record:list) {
			if(this.agencyWhitelist.contains(record.get("agency_id")) || this.agencyWhitelist == null) {
				DataRoutePT TempPTDataRoute = new DataRoutePT(record.get("route_short_name"), record.get("agency_id"), record.get("route_id"));
				if(!this.ptDataContainer.addPTDataRoute(TempPTDataRoute))
					this.ptDataContainer.getPTDataRouteMap().get(TempPTDataRoute.getId()).getRouteIds().add(record.get("route_id"));
			}
		}
		parser.close();
		log.info("Finished reading routes");
	}
	
	/**
	 * reads calendar file to extract service_id that are applicable (weekdays)<br>
	 * ones that are mon-thur<br>
	 * XXX 266 was incorrect, kaito only has service on tue thur
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	protected void calendarReader() throws FileNotFoundException, IOException {
		log.info("Starting to read calendar");
		CSVParser parser = new CSVParser(new FileReader(FileDir+"calendar.txt"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		List<CSVRecord> list = parser.getRecords();
		for(CSVRecord record:list) {
			if(record.get("monday").equals("1") || record.get("tuesday").equals("1") || record.get("wednesday").equals("1") || record.get("thursday").equals("1"))
				this.ptDataContainer.getServiceIdSet().add(record.get("service_id"));
		}
		parser.close();
		log.info("Finished reading calendar");
	}
	
	/**
	 * reads trips file to extract headway based individual "routes"(indi-route) that has the applicable service id and route_id<br>
	 * cleans routes without any valid trips to save memory
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	protected void tripsReader() throws FileNotFoundException, IOException {
		log.info("Starting to read trips");
		CSVParser parser = new CSVParser(new FileReader(FileDir+"trips.txt"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		List<CSVRecord> list = parser.getRecords();
		for(CSVRecord record:list) {
			if(this.ptDataContainer.getServiceIdSet().contains(record.get("service_id"))) {
				DataRoutePT TRoute = this.ptDataContainer.getPTDataRouteByroute_id(record.get("route_id"));
				if(TRoute != null)
					TRoute.addTripId(record.get("trip_id"));
			}
		}
		parser.close();
		log.info("Finished reading trips");
		
		//clean routes without any valid trips
		Iterator<DataRoutePT> itr = this.ptDataContainer.getPTDataRouteMap().values().iterator();
		while(itr.hasNext())
			if(itr.next().getTripIdMap().isEmpty())
				itr.remove();
		log.info("Finished cleanning up routes with no valid trips");
	}
	
	/**
	 * reads stop_times file to extract headway based "indi-route" and identify unique routes<br>
	 * cleans the route of duplicated consecutive stops without renumbering the order<br>
	 * so I don't have to renumber the fare map
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	protected void stop_timesReader() throws FileNotFoundException, IOException {
		log.info("Starting to read stop_times");
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
		log.info("Finished reading stop_times");
		
		//clean duplicated stops
		for(DataRoutePT TempPTDataRoute: this.ptDataContainer.getPTDataRouteMap().values())
			TempPTDataRoute.cleanDuplicateStops();
		log.info("Finished cleaning up duplicated route stops");
	}
	
	/**
	 * reads frequencies file to extract the frequencies for each "indi-route"
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	protected void frequenciesReader() throws FileNotFoundException, IOException {
		log.info("Starting to read frequencies");
		CSVParser parser = new CSVParser(new FileReader(FileDir+"frequencies.txt"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		List<CSVRecord> list = parser.getRecords();
		for(CSVRecord record:list) {
			if(this.ptDataContainer.getPTDataRouteBytrip_id(record.get("trip_id")) != null)
				this.ptDataContainer.addPTDataFreq(new DataFreqPT(record.get("trip_id"), record.get("start_time"), record.get("end_time"), record.get("headway_secs")));
		}
		parser.close();
		log.info("Finished reading frequencies");
	}
	
	/**
	 * reads stops file to extract stop locations appeared in trips
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	protected void stopsReader() throws FileNotFoundException, IOException {
		log.info("Starting to read stops");
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
				this.ptDataContainer.addPTDataStop(new DataStopPT(record.get("stop_id").replace("&", "AND"), record.get("stop_name"), Double.parseDouble(record.get("stop_lat")), Double.parseDouble(record.get("stop_lon"))));
		}
		parser.close();
		log.info("Finished reading stops");
	}
	
	/**
	 * reads fare_attributes & fare_rules to extract fare costs
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
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
				PTDataRoute.getOrCreatePTDataFare(matcher.group()).newFareDataGTFS(record.get("fare_id"), record.get("origin_id"), record.get("destination_id"));
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
	
	
}

