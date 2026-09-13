package createBus;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * This class is a helper class to load the bus route fare information.
 * @author eleead
 *
 */
public class BusRouteFareLoader {
	public static final String fullFareHeading = "Adult";
	public static final Logger log = Logger.getLogger(BusRouteFareLoader.class);

	private final Map<Id<TransitRoute>, List<Double>> fares;
	private final Map<Id<TransitRoute>, List<SectionFare>> sectionFares;

	public BusRouteFareLoader() {
		this.fares = new HashMap<Id<TransitRoute>, List<Double>>();
		this.sectionFares = new HashMap<Id<TransitRoute>, List<SectionFare>>();
	}

	/**
	 * Add a session fare to the map for the line.
	 * 
	 * @param routeId
	 * @param fromStop
	 * @param toStop
	 * @param fare
	 */
	private void addSectionFare(Id<TransitRoute> routeId, int fromStop, int toStop, double fare) {
		List<SectionFare> sectionFareList;
		if (this.sectionFares.containsKey(routeId)) {
			sectionFareList = this.sectionFares.get(routeId);
		} else {
			sectionFareList = new LinkedList<SectionFare>();
		}

		sectionFareList.add(new SectionFare(fromStop, toStop, fare));
		this.sectionFares.put(routeId, sectionFareList);
	}

	public String getFullFare(CSVRecord record, List<String> possibleHeadings) {
		for (String heading : possibleHeadings) {
			if (record.isMapped(heading)) {
				return record.get(heading);
			}
		}
		throw new IllegalArgumentException("The full fare is not found from the headings!");
	}

	private int obtainStopSequence(CSVRecord node) {
		if (node.isMapped("Stop")) {
			return Integer.parseInt(node.get("Stop"));
		} else {
			return Integer.parseInt(node.get(0)) + 1; // Get the first column, and add one to it
		}
	}

	/**
	 * A function to read stops from a file, modifying the stopNames and adultFare,
	 * and return the last stop sequence if it is circular, return -1 if not.
	 * 
	 * @param filePath
	 * @param stopNames
	 * @param adultFare
	 * @return -2 if it is not considered, -1 if the reading finished
	 * @throws IOException
	 */
	private int readStops(String filePath, List<String> stopNames, List<Double> adultFare, int lastStopSequence)
			throws IOException {
		Reader line_in = new FileReader(filePath);
		Iterable<CSVRecord> nodes = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(line_in);
		Iterator<CSVRecord> itr = nodes.iterator();
		
		while (itr.hasNext()) { // Iterate through the stops data
			CSVRecord node = itr.next();
			
			int thisStopSequence = obtainStopSequence(node);// Determine the stop sequence.
			String stopNameInFile = node.get("Bus Stop");
			
			if(stopNameInFile.contains("ALIGHTING STOP") && itr.hasNext()) {
				continue; //Skip the station if repeated or just an alighting one
			}

			if (lastStopSequence == -1 && thisStopSequence > 1) {
				log.warn("The route trial skipped");
				return -2; // It is the file of circular route.
			} else if (lastStopSequence != -1 && thisStopSequence == 1) {
				return -2; // If we are searching for another part of circular, but the stop sequence is
							// different
			} else if (lastStopSequence >= thisStopSequence) {
				if (stopNames.get(thisStopSequence - 1).equals(stopNameInFile)) {
					continue;
				} else {
					return -2; // If the stop name is not aligned, then skip it.
				}
			}
			lastStopSequence = thisStopSequence;

			String fullFare = getFullFare(node, Arrays.asList(fullFareHeading, "adult fare"));

			if (stopNames.isEmpty() || (!stopNames.get(stopNames.size() - 1).equals(stopNameInFile) ||
					stopNameInFile.equals("SUN TUEN MUN CENTRE") || stopNameInFile.equals("KEI SAN SECONDARY SCHOOL") || 
					stopNameInFile.equals("HONG SING GARDEN") || stopNameInFile.equals("LE PRESTIGE LOHAS PARK") ||
					stopNameInFile.equals("SHEUNG TSUEN PLAYGROUND"))) {
				stopNames.add(stopNameInFile);
				if (fullFare.equalsIgnoreCase("Alighting only")) { // The routing finished when it is alighting only
					adultFare.add(adultFare.get(adultFare.size() - 1));
					lastStopSequence = -1;
					continue;
				} else if(fullFare.equalsIgnoreCase("--")){
					adultFare.add(0.0);
				}else {
					adultFare.add(Double.parseDouble(fullFare));
				}
			} else {
				if(fullFare.equalsIgnoreCase("--")) {
					fullFare = "0.0";
				}else if(fullFare.equalsIgnoreCase("Alighting only")) {
					fullFare = ""+adultFare.get(adultFare.size() - 1);
					lastStopSequence = -1;
				}
				adultFare.set(adultFare.size() - 1, Double.parseDouble(fullFare)); // Else only update the fare.
			}

		}
		line_in.close();

		return lastStopSequence;
	}
	
	/**
	 * Process the section fares FOR CIRCULAR based on the fare list of boarding the bus.
	 * This operation would add the section fare to the list.
	 * @param routeId
	 * @param adultFare
	 * @param fullFare
	 */
	private void processSectionFare(Id<TransitRoute> routeId, List<Double> adultFare, double fullFare) {
		double lastFare = fullFare; // Store the fare of alighting at last stop
		boolean foundSectionFare = false; // Will turn to true if the section fare is found.

		// Iterate through the fare
		for (int i = 0; i < adultFare.size(); i++) {
			double currFare = adultFare.get(i);

			if (!foundSectionFare || currFare < fullFare) {
				adultFare.set(i, currFare + fullFare); //Add one more full fare for the extra trip.
			} else {
				break; // Break when it is back to the full fare.
			}

			if (currFare < lastFare) {
				this.addSectionFare(routeId, i, adultFare.size() - 1, currFare);// Add a section fare to the
																				// map.
				foundSectionFare = true;
			}
			lastFare = currFare;
		}
	}
	
	@Deprecated
	private void findAndLoadShortSectionFare(Id<TransitRoute> routeId, String filePath, 
			List<String> stopNames) throws IOException {
		if(filePath==null) { //No short section fare if it is null.
			return;
		}
		Reader line_in = new FileReader(filePath);
		Iterable<CSVRecord> nodes = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(line_in);

		for (CSVRecord node : nodes) { // Iterate through the stops data
			String stopNameFrom = node.get(0); //'from' column
			String stopNameTo = node.get(1); //'to' column
			int fromStop = -1;
			int toStop = -1;
			for (int i = 0; i < stopNames.size(); i++) { // We are checking if every stops is in the stop name
				String stopNameInFile = stopNames.get(i);
				// If the name is not equal and the edit distance is too high.
				if (BusDataExtractor.stopNamesEquivalent(stopNameInFile, stopNameFrom)) {
					if(fromStop==-1) {
						fromStop = i;
					}
				}
				if (BusDataExtractor.stopNamesEquivalent(stopNameInFile, stopNameTo)) {
					if(fromStop!=-1 && toStop==-1) {
						toStop = i;
					}
				}
			}
			if(fromStop!=-1 && toStop!=-1) {
				addSectionFare(routeId, fromStop, toStop, Double.parseDouble(node.get(2)));
			}else {
				throw new RuntimeException("The bus stop is not found!");
			}
		}
		
	}
	
	/**
	 * Given a stops file, load the bus route from the list of busRoutes, and process the section 
	 * fare in the same time.
	 * Notes that this class didn't create a new BusRoute, but pick one from busRoutes.
	 * @param scenario
	 * @param stopFilePath The file path of stop file, with stop name and fare information.
	 * @param routeId
	 * @param possibleStopFilePaths
	 * @param busRoutes
	 * @return
	 * @throws IOException
	 */
	public BusRoute newLoadBusRoute(Scenario scenario, String stopFilePath, Id<TransitRoute> routeId,
			List<String> possibleStopFilePaths, List<BusRoute> busRoutes) throws IOException {
		List<String> stopNames = new ArrayList<String>();
		List<Double> adultFare = new ArrayList<Double>();
		int lastStopSequence = readStops(stopFilePath, stopNames, adultFare, -1); // Start with -1.
		// TODO: Concession fare
		if (lastStopSequence == -2) {
			return null;
		}

		// Find another file and concat the stop list.
		if (lastStopSequence > 0) { // Circular route handling
			// Convert the fare if appropriate
			double fullFare = adultFare.get(0);
			this.addSectionFare(routeId, 0, adultFare.size() - 1, fullFare); //Add the full fare.
			if (Collections.min(adultFare) < fullFare) { // If there were some session fare
				processSectionFare(routeId, adultFare, fullFare);
			}

			boolean stopFound = false;
			for (String possibleStop : possibleStopFilePaths) {
				if (possibleStop.equals(stopFilePath)) {
					continue; // We don't search the same route
				}
				int endStopSequence = readStops(possibleStop, stopNames, adultFare, lastStopSequence);
				if (stopFound && endStopSequence == -1) {
					throw new RuntimeException("Two possible return trip file found for " + routeId + "!");
				} else if (endStopSequence == -1) {
					stopFound = true;
				}
			}
		}
		
		// Process the section fare file, if it is appropriate.
		//findAndLoadShortSectionFare(routeId, sectionFareFile, stopNames);

		// Try to find the bus route which the stop sequence is equal to the current
		// one.
		BusRoute found = null;
		for (BusRoute busRoute : busRoutes) { //Match the bus route generated from TD file.
			if (busRoute.getBusStops().size() >= stopNames.size()) { // First bus route should be longer than the stop
																		// in stops file
				found = busRoute;
				for (int i = 0; i < stopNames.size(); i++) { // We are checking if every stops is in the stop name
					String routeStopName = busRoute.getBusStops().get(i).getSecond().getName();
					String stopNameInFile = stopNames.get(i);
					// If the name is not equal and the edit distance is too high.
					if (!BusDataExtractor.stopNamesEquivalent(stopNameInFile, routeStopName)) {
						found = null;
						break;
					}
				}
				//Try to revive it by finding out the shorter route starts from middle
				if(found==null) {
					String firstStopNameInFile = stopNames.get(0);
					int startStop = -1;
					for (int i = 0; i < busRoute.getBusStops().size(); i++) { // We are checking if every stops is in the stop name
						if(found==null && busRoute.getBusStops().size()-i < stopNames.size()) {
							break; //Finish the loop whenever it is not long enough to accept another line
						}
						
						String routeStopName = busRoute.getBusStops().get(i).getSecond().getName();
						if(startStop==-1 && BusDataExtractor.stopNamesEquivalent(firstStopNameInFile, routeStopName)) {
							found = busRoute.getSubRoute(i, busRoute.busStops.size());
							startStop = i; //Identify the offset between two.
						}
						
						String stopNameInFile = (i - startStop < stopNames.size())?stopNames.get(i - startStop):"NOT MORE";
						// If the route is finished processed.
						if(found!=null && stopNameInFile.equals("NOT MORE")) {
							found = found.getSubRoute(0, i-startStop);
							break;
						}
						
						if (found!=null && !BusDataExtractor.stopNamesEquivalent(stopNameInFile, routeStopName)) {
							found = null;
							startStop = -1;
							continue; //Keep searching as it found a stop not equivalent.
						}
					}
				}else {
					// Get a bus route with only first n stops, n is the length of stopNames.
					if (busRoute.getBusStops().size() > stopNames.size()) {
						found = busRoute.getSubRoute(0, stopNames.size());
					}
				}
				
				if (found != null) {
					if (adultFare.size() != stopNames.size()) {
						throw new RuntimeException("The adult fare list is not as long as number of stops");
					}
					fares.put(routeId, adultFare);
					
					return found;
				}
			}
		}
		throw new RuntimeException("The bus route "+routeId.toString()+" is missing!");
	}

	/**
	 * Given a stops file, load the bus route from the list of busRoutes, and process the session 
	 * fare in the same time.
	 * Notes that this class didn't create a new BusRoute, but pick one from busRoutes.
	 * @param scenario
	 * @param filePath
	 * @param sectionFareFile
	 * @param routeId
	 * @param possiblePaths
	 * @param busRoutes
	 * @return
	 * @throws IOException
	 */
	public BusRoute loadBusRoute(Scenario scenario, String filePath, String sectionFareFile, Id<TransitRoute> routeId,
			List<String> possiblePaths, List<BusRoute> busRoutes) throws IOException {

		List<String> stopNames = new ArrayList<String>();
		List<Double> adultFare = new ArrayList<Double>();
		int lastStopSequence = readStops(filePath, stopNames, adultFare, -1); // Start with -1.
		// TODO: Concession fare
		if (lastStopSequence == -2) {
			return null;
		}

		// Find another file and concat the stop list.
		if (lastStopSequence > 0) { // Circular route handling
			// Convert the fare if appropriate
			double fullFare = adultFare.get(0);
			this.addSectionFare(routeId, 0, adultFare.size() - 1, fullFare); //Add the full fare.
			if (Collections.min(adultFare) < fullFare) { // If there were some session fare
				processSectionFare(routeId, adultFare, fullFare);
			}

			boolean stopFound = false;
			for (String possibleStop : possiblePaths) {
				if (possibleStop.equals(filePath)) {
					continue; // We don't search the same route
				}
				int endStopSequence = readStops(possibleStop, stopNames, adultFare, lastStopSequence);
				if (stopFound && endStopSequence == -1) {
					throw new RuntimeException("Two possible return trip file found for " + routeId + "!");
				} else if (endStopSequence == -1) {
					stopFound = true;
				}
			}
		}
		
		// Process the section fare file, if it is appropriate.
		//findAndLoadShortSectionFare(routeId, sectionFareFile, stopNames);

		// Try to find the bus route which the stop sequence is equal to the current
		// one.
		BusRoute found = null;
		for (BusRoute busRoute : busRoutes) { //Match the bus route generated from TD file.
			if (busRoute.getBusStops().size() >= stopNames.size()) { // First bus route should be longer than the stop
																		// in stops file
				found = busRoute;
				for (int i = 0; i < stopNames.size(); i++) { // We are checking if every stops is in the stop name
					String routeStopName = busRoute.getBusStops().get(i).getSecond().getName();
					String stopNameInFile = stopNames.get(i);
					// If the name is not equal and the edit distance is too high.
					if (!BusDataExtractor.stopNamesEquivalent(stopNameInFile, routeStopName)) {
						found = null;
						break;
					}
				}
				if (found != null) {
					// Get a bus route with only first n stops, n is the length of stopNames.
					if (busRoute.getBusStops().size() > stopNames.size()) {
						found = busRoute.getSubRoute(0, stopNames.size());
					}

					if (adultFare.size() != stopNames.size()) {
						throw new RuntimeException("The adult fare list is not as long as number of stops");
					}
					fares.put(routeId, adultFare);
					return found;
				}
			} else {
				continue;
			}
		}
		throw new RuntimeException("The bus route is not found");
	}

	public Map<Id<TransitRoute>, List<Double>> getFares() {
		return this.fares;
	}

	public Map<Id<TransitRoute>, List<SectionFare>> getSectionFares() {
		return this.sectionFares;
	}

	@Deprecated
	private List<Id<TransitStopFacility>> facilitiesByName(Map<Id<TransitStopFacility>, String> stopNameMap,
			String stopName) {
		List<Id<TransitStopFacility>> tsfList = new LinkedList<>();
		for (Id<TransitStopFacility> tsfId : stopNameMap.keySet()) {
			if (stopNameMap.get(tsfId).equals(stopName)) {
				tsfList.add(tsfId);
			}
		}

		if (tsfList.size() > 0) {
			return tsfList;
		} else {
			throw new IllegalArgumentException("The stop name " + stopName + " is not in the stop name map provided!");
		}
	}

	/**
	 * A storage for section fare.
	 * @author eleead
	 *
	 */
	public class SectionFare {
		private int startSequence;
		private int endSequence;
		private double fare;

		private SectionFare(int fromStopSequence, int toStopSequence, double fare) {
			this.startSequence = fromStopSequence;
			this.endSequence = toStopSequence;
			this.fare = fare;
		}

		public int getStartSequence() {
			return startSequence;
		}

		public int getEndSequence() {
			return endSequence;
		}

		public double getFare() {
			return fare;
		}
	}
}
