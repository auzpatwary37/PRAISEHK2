/**
 * 
 */
package createPTGTFS;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.VehicleType;

import com.google.common.collect.Sets;

import createPTGTFS.DataStopPT.Types;
import networkFromSaturn.CreateNetwork;
import networkFromSaturn.pt.CreatePTUtils;

/**
 * @author JLo
 *
 */
public class RunFerryGTFS {
	private static String FileDir = "PTGTFS\\";	//XXX
	private static final double dwellingTime = 600;
	private static final Logger log = Logger.getLogger(RunFerryGTFS.class);

	private static final String CentralTerminal = "CENTRAL";
	private static final String StarFerryTerminal = "TSIM SHA TSUI";	//this is way slower
	private static final String ApLeiChauTerminal = "AP LEI CHAU";
	private static final HashSet<String> BiggestRoutes = new HashSet<String>(Arrays.asList("CENTRAL - CHEUNG CHAU", "CENTRAL - MUI WO", "CENTRAL - PENG CHAU", "CENTRAL - YUNG SHUE WAN"));	//these have very high demand
	
	public static void setFilePath(String dir) {
		FileDir = dir;
	}
	
	public static FareCalculatorPTGTFS createFerries(Scenario scenario, boolean HKIOnly) throws FileNotFoundException, IOException {
		//#1	Load all the ferry data from GTFS
		log.info("Reading GTFS files");
		DataContainerPT FerryData = (new ReaderFerry(FileDir, HKIOnly)).getData();
		
		//#2	Create Links and TransitStopFacility-s for route
		log.info("Creating the TransitStopFacility-s");
		for(DataStopPT TStop: FerryData.getPTDataStopMap().values())
			makeFerryPiers(scenario, TStop);
		//Create helper central links such that ferries will sort of miss HKI
		createCentralFerryLinks(scenario.getNetwork());
		
		//#3	Make the TransitLines
		log.info("Creating the ferry TransitLines");
		VehicleType gvt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "BiggestFerry", 50, 10, 30/3.6, 1000, 0, 0.5, 0.5, 1, "Biggest ferry");	//for outlaying 4 islands ferry, wanted to slow this down but will create jam
		VehicleType bvt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "BigFerry", 50, 10, 30/3.6, 500, 0, 0.5, 0.5, 1, "Big ferry");			//somewhere inbetween fast/slow but time roughly ok
		VehicleType svt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "StarFerry", 50, 10, 9/3.6, 500, 0, 0.5, 0.5, 1, "Star ferry");		//assuming 10min run 1.5km
		VehicleType kvt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "KaitoFerry", 10, 5, 6/3.6, 50, 0, 1, 1, 1, "Kaito ferry");			//using Aberdeen run time
		VehicleType nvt = CreatePTUtils.createVehicleType(scenario.getTransitVehicles(), "NormalFerry", 30, 10, 20/3.6, 300, 0, 0.5, 0.5, 1, "Normal ferry");	//basically only for in-harbour routes
		PTDeparturesBuilder ptdbuilderN = new PTDeparturesBuilder(scenario, nvt, FerryData.getPTDataFreqMap());
		PTDeparturesBuilder ptdbuilderS = new PTDeparturesBuilder(scenario, svt, FerryData.getPTDataFreqMap());
		PTDeparturesBuilder ptdbuilderK = new PTDeparturesBuilder(scenario, kvt, FerryData.getPTDataFreqMap());
		PTDeparturesBuilder ptdbuilderB = new PTDeparturesBuilder(scenario, bvt, FerryData.getPTDataFreqMap());
		PTDeparturesBuilder ptdbuilderG = new PTDeparturesBuilder(scenario, gvt, FerryData.getPTDataFreqMap());
		
		//copy from RunBusGTFS
		for(DataRoutePT bigRoutes: FerryData.getPTDataRouteMap().values()) {
			TransitLine TL = scenario.getTransitSchedule().getFactory().createTransitLine(Id.create(bigRoutes.getId(), TransitLine.class));
			
			for(Entry<String, TreeSet<String>> invTripIdsEntry: bigRoutes.invertTripIdMap().entrySet()) {
				String nameToCheck = bigRoutes.getName().toUpperCase();
				//get the first 2 things
				double vicSpeed = 0;
				if(BiggestRoutes.contains(nameToCheck))
					vicSpeed = gvt.getMaximumVelocity();
				else if(nameToCheck.contains(StarFerryTerminal))
					vicSpeed = svt.getMaximumVelocity();
				else if(nameToCheck.contains(ApLeiChauTerminal))
					vicSpeed = kvt.getMaximumVelocity();
				else if(nameToCheck.contains(CentralTerminal))
					vicSpeed = bvt.getMaximumVelocity();
				else
					vicSpeed = nvt.getMaximumVelocity();
				Tuple<ArrayList<Id<Link>>, ArrayList<TransitRouteStop>> stuffLists = 		//save some space using terrible coding
						makeRouteLists(scenario.getNetwork(), scenario.getTransitSchedule(), FerryData.getPTDataStopMap(), bigRoutes.getName(), bigRoutes.getStopsMap().get(invTripIdsEntry.getKey()), vicSpeed);
				
				//creates TransitRoute, Id is TripIdMap value XXXX_X (routeId;direction) TO: name of last stop
				Id<TransitRoute> TRId = Id.create(invTripIdsEntry.getKey()+" TO: "+stuffLists.getSecond().get(stuffLists.getSecond().size()-1).getStopFacility().getName(), TransitRoute.class);
				TransitRoute TR = scenario.getTransitSchedule().getFactory().createTransitRoute(	TRId,
						RouteUtils.createNetworkRoute(stuffLists.getFirst(), scenario.getNetwork()), stuffLists.getSecond(), "ferry");
				bigRoutes.putTRId(invTripIdsEntry.getKey(), TRId);
				
				//make departures
				if(BiggestRoutes.contains(nameToCheck))
					ptdbuilderG.makeDepartures(bigRoutes, TR, invTripIdsEntry.getValue(), invTripIdsEntry.getKey());
				else if(nameToCheck.contains(StarFerryTerminal))
					ptdbuilderS.makeDepartures(bigRoutes, TR, invTripIdsEntry.getValue(), invTripIdsEntry.getKey());
				else if(nameToCheck.contains(ApLeiChauTerminal))	//specifically for aberdeen ap lei chau high freq service
					ptdbuilderK.makeDepartures(bigRoutes, TR, invTripIdsEntry.getValue(), invTripIdsEntry.getKey());
				else if(nameToCheck.contains(CentralTerminal))
					ptdbuilderB.makeDepartures(bigRoutes, TR, invTripIdsEntry.getValue(), invTripIdsEntry.getKey());
				else	//"normal" ferry
					ptdbuilderN.makeDepartures(bigRoutes, TR, invTripIdsEntry.getValue(), invTripIdsEntry.getKey());
				
				TL.addRoute(TR);
			}
			scenario.getTransitSchedule().addTransitLine(TL);
		}
		log.info("Finished creating the TransitLines");
		
		//#4	Optimise data for fareLoader
		log.info("Creating the Ferry FareCalculator");
		return new FareCalculatorPTGTFS(FerryData);
		
	}
	
	private static Tuple<ArrayList<Id<Link>>, ArrayList<TransitRouteStop>> makeRouteLists(Network net, TransitSchedule ts, HashMap<String, DataStopPT> ptDataStopMap, String routeName, TreeMap<Integer, String> stopsList, double vicSpeed) {
		ArrayList<Id<Link>> linkIdList = new ArrayList<Id<Link>>();
		ArrayList<TransitRouteStop> trsList = new ArrayList<TransitRouteStop>(); 
		
		boolean centralFlag = false;
		if(routeName.toUpperCase().contains(CentralTerminal) && !routeName.toUpperCase().contains(StarFerryTerminal))
			centralFlag = true;
		
		Iterator<String> itr = stopsList.values().iterator();
		DataStopPT thisStop = ptDataStopMap.get(itr.next());
		
		//make first stop
		trsList.add(ts.getFactory().createTransitRouteStop(thisStop.getTransitStopFacility(), 0, dwellingTime));
		linkIdList.add(thisStop.getLinkId());
		int stopCount = 1;
		DataStopPT lastStop = thisStop;
		
		while(itr.hasNext()) {
			thisStop = ptDataStopMap.get(itr.next());
			
			//add out link from last pier
			linkIdList.add(getPierOutlinkId(lastStop.getStopId()));
			//add middle paths
			if(centralFlag)
				linkIdList.addAll(getOrCreateCentralFerryLinks(net, lastStop, thisStop));
			else
				linkIdList.add(getOrCreateFerryLink(net, lastStop.getStopId(), thisStop.getStopId()));
			//path time
			double pathTime = 0;
			for(Id<Link> TLID: linkIdList)
				pathTime += net.getLinks().get(TLID).getLength()/vicSpeed;
			//add TRS
			trsList.add(ts.getFactory().createTransitRouteStop(thisStop.getTransitStopFacility(), pathTime+dwellingTime*stopCount, pathTime+dwellingTime*(stopCount+1)));
			stopCount++;
			//add pier in link
			linkIdList.add(thisStop.getLinkId());
			lastStop = thisStop;
		}
		
		return new Tuple<ArrayList<Id<Link>>, ArrayList<TransitRouteStop>>(linkIdList, trsList);
	}

	private static void makeFerryPiers(Scenario scenario, DataStopPT PTDataStop) {
		PTDataStop.setType(Types.ferry);
		Network net = scenario.getNetwork();
		Coord coord = PTDataStop.getCoord();
		String node1 = "F"+PTDataStop.getStopId();
		String node2 = node1+"A";
		NetworkUtils.createAndAddNode(net, Id.createNodeId(node1), new Coord(coord.getX(), coord.getY()-50));
		NetworkUtils.createAndAddNode(net, Id.createNodeId(node2), coord);
		Link link1 = CreateNetwork.create_links(net, node1, node2, 1, 20/3.6, 50);
		Link link2 = CreateNetwork.create_links(net, node2, node1, 1, 20/3.6, 50);
		link1.setAllowedModes(Sets.newHashSet("ferry"));
		link2.setAllowedModes(Sets.newHashSet("ferry"));
		PTDataStop.setTransitStopFacility(scenario.getTransitSchedule(), Id.createLinkId(node1+"_"+node2), false);
	}
	
	private static Id<Link> getOrCreateFerryLink(Network net, String stopId1, String stopId2) {
		Id<Link> TLID = Id.createLinkId("F"+stopId1+"_F"+stopId2);
		if(net.getLinks().get(TLID) == null) {
			double d = NetworkUtils.getEuclideanDistance(
					net.getNodes().get(Id.createNodeId("F"+stopId1)).getCoord(), 
					net.getNodes().get(Id.createNodeId("F"+stopId2)).getCoord());
			Link link1 = CreateNetwork.create_links(net, "F"+stopId1, "F"+stopId2, 1, 30/3.6, d);
			link1.setAllowedModes(Sets.newHashSet("ferry"));
		}
		return TLID;
	}

	private static ArrayList<Id<Link>> getOrCreateCentralFerryLinks(Network net, DataStopPT fromStop, DataStopPT toStop){
		ArrayList<Id<Link>> output = new ArrayList<Id<Link>>();
		if(fromStop.getStopName().toUpperCase().contains(CentralTerminal)) {
			output.add(getOrCreateFerryLink(net, fromStop.getStopId(), "Central1"));
			output.add(Id.createLinkId("FCentral1_FCentral2"));
			output.add(getOrCreateFerryLink(net, "Central2", toStop.getStopId()));
		} else if (toStop.getStopName().toUpperCase().contains(CentralTerminal)) {
			output.add(getOrCreateFerryLink(net, fromStop.getStopId(), "Central2"));
			output.add(Id.createLinkId("FCentral2_FCentral1"));
			output.add(getOrCreateFerryLink(net, "Central1", toStop.getStopId()));
		} else {
			log.warn("Neither fromStop nor toStop is Central!!!");
			output.add(getOrCreateFerryLink(net, fromStop.getStopId(), toStop.getStopId()));
		}
		return output;
	}
	
	private static void createCentralFerryLinks(Network net) {
		Node node1 = NetworkUtils.createAndAddNode(net, Id.createNodeId("FCentral1"), new Coord(34150,16860));
		Node node2 = NetworkUtils.createAndAddNode(net, Id.createNodeId("FCentral2"), new Coord(29500,16860));
		double d = NetworkUtils.getEuclideanDistance(node1.getCoord(), node2.getCoord());
		Link link1 = CreateNetwork.create_links(net, node1.getId().toString(), node2.getId().toString(), 1, 50/3.6, d);
		Link link2 = CreateNetwork.create_links(net, node2.getId().toString(), node1.getId().toString(), 1, 50/3.6, d);
		link1.setAllowedModes(Sets.newHashSet("ferry"));
		link2.setAllowedModes(Sets.newHashSet("ferry"));
	}
	
	private static Id<Link> getPierOutlinkId(String stopId) {
		return Id.createLinkId("F"+stopId+"A_F"+stopId);
	}
	
	
}







