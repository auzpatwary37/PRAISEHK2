/**
 * 
 */
package createPTGTFS;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.VehicleType;

import com.google.common.collect.Sets;

import createBus.BusHeadway;
import createBus.BusPathCalculator;
import createBus.CreateBusUtils;
import createBus.L2lLeastCostCalculatorFactory;
import createBus.Runbus;
import createBus.BusHeadway.DepartureList;
import networkFromSaturn.pt.CreatePTUtils;

/**
 * @author JLo
 *
 */
public class RunBusGTFS {
	
	private static String FileDir = "PTGTFS\\";	//XXX
	private static final String StopBindingsFilePath = "input\\busStopBindings.csv";
	private static final double Search_Distance = 50;
	private static final double dwellingTime = 30;	//seconds
	private static final Logger log = Logger.getLogger(RunBusGTFS.class);
	
	public static void setFilePath(String dir) {
		FileDir = dir;
	}
	
	public static FareCalculatorPTGTFS createBusRoutes(Scenario scenario, boolean HKIOnly) throws FileNotFoundException, IOException {
		//#1	Load all the bus data from GTFS
		log.info("Reading GTFS files");
		DataContainerPT BusData  = (new ReaderBus(FileDir, HKIOnly)).getData();
		
		//#2	Fix Bus Stop Links or get Closest links for search
		log.info("Starting preSearchClosestLinksForBusStops");
		preSearchClosestLinksForBusStops(scenario.getNetwork(), BusData, HKIOnly);
		
		//#3 	Dry run to get Count for each link
		log.info("Starting possibleLinks counting");
		int count = 0;
		BusPathCalculator routingAlgo = new L2lLeastCostCalculatorFactory(scenario, Sets.newHashSet(TransportMode.car, "bus")).getRoutingAlgo();
		for(DataRoutePT bigRoutes: BusData.getPTDataRouteMap().values()) {
			//for logging 
			count++;
			if(count%100 == 0)
				log.info("Counting possibleLinks route count #"+count);
			
			for(TreeMap<Integer, String> stopsMap: bigRoutes.getStopsMap().values()) {
				//set first last stops as terminus
				((DataStopBus) BusData.getPTDataStopMap().get(stopsMap.firstEntry().getValue())).setTerminus();
				((DataStopBus) BusData.getPTDataStopMap().get(stopsMap.lastEntry().getValue())).setTerminus();
				//do the linkgraph
				LinkGraph LG = new LinkGraph(scenario.getNetwork(), routingAlgo);
				DataStopBus prevStop = null;
				for(Entry<Integer, String> stopEntry: stopsMap.entrySet()) {
					DataStopBus currStop = (DataStopBus) BusData.getPTDataStopMap().get(stopEntry.getValue());
					LG.addBusStop(prevStop, currStop);
					prevStop = currStop;
				}
				LG.autoDijkstra();
				//do the count
				for(Entry<String,Id<Link>> stopIdEntry: LG.getBusStopLinkMap().entrySet())
					((DataStopBus) BusData.getPTDataStopMap().get(stopIdEntry.getKey())).addCount(stopIdEntry.getValue());
			}
		}
		
		//#4	Create the TransitStopFacility-s 
		log.info("Creating the TransitStopFacility-s");
		for(DataStopPT busStop: BusData.getPTDataStopMap().values())
			((DataStopBus) busStop).makeTransitStopFacility(scenario);
		
		//#5	Make Actual TransitLines
		log.info("Creating the actual TransitLines");
		BusPathCalculator routingAlgo2 = new L2lLeastCostCalculatorFactory(scenario, Sets.newHashSet(TransportMode.car, "bus")).getRoutingAlgo();
		VehicleType vt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "normalBus", 11.3, 4, 70 / 3.6, 100, 30, 1, 1, 3, "Double-decker bus");
		PTDeparturesBuilder ptdbuilder = new PTDeparturesBuilder(scenario, vt, BusData.getPTDataFreqMap());
		
		count = 0;
		for(DataRoutePT bigRoutes: BusData.getPTDataRouteMap().values()) {
			//for logging 
			count++;
			if(count%50 == 0)
				log.info("Creating TransitLine count #"+count);
			
			TransitLine TL = scenario.getTransitSchedule().getFactory().createTransitLine(Id.create(bigRoutes.getId(), TransitLine.class));
			
			for(Entry<String, TreeSet<String>> invTripIdsEntry: bigRoutes.invertTripIdMap().entrySet()) {
				//get the first 2 things
				Tuple<ArrayList<Id<Link>>, ArrayList<TransitRouteStop>> stuffLists = 		//save some space using terrible coding
						makeRouteLists(scenario.getNetwork(), scenario.getTransitSchedule(), BusData.getPTDataStopMap(), routingAlgo2, bigRoutes.getStopsMap().get(invTripIdsEntry.getKey()));
				
				//XXX Manual adjustment of central bus routes going into WHC
				if(bigRoutes.getId().contains("_9") && stuffLists.getFirst().contains(Id.createLinkId("101245_101244")))
					divertCentralBuses(stuffLists.getFirst());
				
				//creates TransitRoute, Id is TripIdMap value XXXX_X (routeId;direction) TO: name of last stop
				String TRdirection = stuffLists.getSecond().get(stuffLists.getSecond().size()-1).getStopFacility().getName();
				Id<TransitRoute> TRId = Id.create(invTripIdsEntry.getKey()+" TO: "+TRdirection, TransitRoute.class);
				TransitRoute TR = scenario.getTransitSchedule().getFactory().createTransitRoute(	TRId,
						RouteUtils.createNetworkRoute(stuffLists.getFirst(), scenario.getNetwork()), stuffLists.getSecond(), "bus");
				bigRoutes.putTRId(invTripIdsEntry.getKey(), TRId);
				
				//make departures
				if(bigRoutes.getOperator().equals("LRTFeeder")) { //For LRT Feeder, we trust the actual headway file more.
					boolean departureMade = false;
					String operator = bigRoutes.getOperator();
					String name = bigRoutes.getName();
					List<String> headWayFilePaths = CreateBusUtils.findFilePaths(operator, name, Runbus.HEADWAY_PATH, "headway");
					for(String filePath: headWayFilePaths) {
						BusHeadway headway = new BusHeadway("bus");
						String direction = Runbus.findDirectionFromPath(filePath, operator, name);
						if(TRdirection.contains(direction.substring(3).toUpperCase())) {
							headway.makeBusDepartures(direction, filePath);
							Map<Integer, Integer> dOrder = headway.getDepartures().getDepartureAndOrderForRoute(headway.getDepartures().getDepartureRoute(0));
							ptdbuilder.makeDepartureFromTimes(bigRoutes, TR, invTripIdsEntry.getKey(), dOrder.values());
							departureMade = true;
							break; //After it is found, it can go out the loop
						}
					}
					if(!departureMade) {
						ptdbuilder.makeDepartures(bigRoutes, TR, invTripIdsEntry.getValue(), invTripIdsEntry.getKey());
					}
				}else {
					ptdbuilder.makeDepartures(bigRoutes, TR, invTripIdsEntry.getValue(), invTripIdsEntry.getKey());
				}
				
				TL.addRoute(TR);
			}
			scenario.getTransitSchedule().addTransitLine(TL);
		}
		log.info("Finished creating the TransitLines");
		
		//#6	Optimise data for fareLoader
		log.info("Creating the Bus FareCalculator");
		return new FareCalculatorPTGTFS(BusData);
		
	}
	
	private static Tuple<ArrayList<Id<Link>>, ArrayList<TransitRouteStop>> makeRouteLists(Network net, TransitSchedule ts, HashMap<String, DataStopPT> PTDataStopMap, BusPathCalculator routingAlgo, TreeMap<Integer, String> stopsList) {
		ArrayList<Link> linkList = new ArrayList<Link>();
		ArrayList<TransitRouteStop> trsList = new ArrayList<TransitRouteStop>(); 
		Iterator<String> itr = stopsList.values().iterator();
		DataStopBus firstStop = (DataStopBus) PTDataStopMap.get(itr.next());
		Link lastLink = net.getLinks().get(firstStop.getTerminusLinkId());
		Link thisLink;
		
		//make first stop
		trsList.add(ts.getFactory().createTransitRouteStop(firstStop.getTerminusTransitStopFacility(), 0, dwellingTime));
		double aggregatedTime = dwellingTime;
		linkList.add(lastLink);
		
		while(itr.hasNext()) {
			firstStop = (DataStopBus) PTDataStopMap.get(itr.next());
			
			//check for last stop
			if(itr.hasNext())
				thisLink = net.getLinks().get(firstStop.getLinkId());
			else
				thisLink = net.getLinks().get(firstStop.getTerminusLinkId());
			
			//check if stops are still at the same link
			if(thisLink.equals(lastLink)) {
				aggregatedTime += dwellingTime;
			} else {
				Path Tpath = routingAlgo.calcLeastCostPath(lastLink, thisLink, aggregatedTime, null, null);
				linkList.addAll(Tpath.links);
				linkList.add(thisLink);
				aggregatedTime += (dwellingTime + Tpath.travelTime*2);
				lastLink = thisLink;
			}
			
			//add the route-stop
			if(itr.hasNext())
				trsList.add(ts.getFactory().createTransitRouteStop(firstStop.getTransitStopFacility(), aggregatedTime, aggregatedTime+dwellingTime));
			else
				trsList.add(ts.getFactory().createTransitRouteStop(firstStop.getTerminusTransitStopFacility(), aggregatedTime, aggregatedTime+dwellingTime));
		}
		
		//Have to convert back to linkIds, wasting resources
		ArrayList<Id<Link>> linkIdList = new ArrayList<Id<Link>>();
		for(Link TL: linkList)
			linkIdList.add(TL.getId());
		
		return new Tuple<ArrayList<Id<Link>>, ArrayList<TransitRouteStop>>(linkIdList, trsList);
	}
	
	private static void preSearchClosestLinksForBusStops(Network net, DataContainerPT BusData, boolean HKIOnly) throws IOException {
		//#1	Get stop bindings from file
		HashMap<Integer,List<Id<Link>>> stopBindings = getStopBindings(net);
		
		//#2	HKIOnly Needs to override the list a little bit
		if(HKIOnly) {
			stopBindings.put(1063, Arrays.asList(Id.createLinkId("EHC")));
			stopBindings.put(885, Arrays.asList(Id.createLinkId("EHC")));
			stopBindings.put(9015, Arrays.asList(Id.createLinkId("CHT")));
			stopBindings.put(9014, Arrays.asList(Id.createLinkId("CHT")));
			stopBindings.put(219, Arrays.asList(Id.createLinkId("CHT")));
			stopBindings.put(921, Arrays.asList(Id.createLinkId("WHC")));
			stopBindings.put(911, Arrays.asList(Id.createLinkId("WHC")));
		}
		
		//#3	loop through the data and assign stuff
		int count = 0;
		for(Entry<String, DataStopPT> e: BusData.getPTDataStopMap().entrySet()) {
			count++;
			if(count%500 == 0)
				log.info("Searching for closest link stop count #"+count);
			
			if(stopBindings.containsKey(Integer.valueOf(e.getKey())))
				((DataStopBus) e.getValue()).addClosestLinks(stopBindings.get(Integer.parseInt(e.getKey())));
			else
				((DataStopBus) e.getValue()).findClosestLinks(net, Search_Distance);
		}
	}
	
	/**
	 * reads the target file for stop bindings
	 * @param net
	 * @return
	 * @throws IOException
	 * @author JLo
	 */
	private static HashMap<Integer,List<Id<Link>>> getStopBindings(Network net) throws IOException{
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
	 * <p><b>Must only be used to check full network, HKIOnly network will throw</b></p>
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
	
	/**
	 * Hard coding the diversion of central bus routes going into WHC
	 * Forcing the buses to take Morrison St instead of Hillier St
	 * @param linkList
	 * @author JLo
	 */
	private static void divertCentralBuses(ArrayList<Id<Link>> linkList) {
		int stIndex = linkList.indexOf(Id.createLinkId("101245_101244"));
		if(stIndex<0)
			return;
		//remove the 4 org links via Hillier St
		for(int i=0; i<4; i++)
			linkList.remove(stIndex);
		//put in the diversion route in reverse as I dont want to figure out the correct index in sequential order JLo
		linkList.add(stIndex, Id.createLinkId("101179_101692"));
		linkList.add(stIndex, Id.createLinkId("101182_101179"));
		linkList.add(stIndex, Id.createLinkId("101181_101182"));
		linkList.add(stIndex, Id.createLinkId("101244_101181"));
		linkList.add(stIndex, Id.createLinkId("18175_101244"));
		linkList.add(stIndex, Id.createLinkId("101245_18175"));
		
	}
	
}
