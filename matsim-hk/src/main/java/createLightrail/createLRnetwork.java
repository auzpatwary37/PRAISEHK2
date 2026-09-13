/**
 * 
 */
package createLightrail;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import networkFromSaturn.pt.CreatePTUtils;

import com.google.common.collect.Sets;
import com.google.gson.Gson;

/**
 * @author JLo
 *
 */
public class createLRnetwork {
	
	private static final String LRstationsFilePath = "input\\lightrail_stops.json";
	private static final String LRroutesFilePath = "input\\lightrail_route.json";
	private static final String LRheadwayFilePath = "input\\lightrail_headway.json";
	public static final double stationLength = 45;
	public static final double dwellingTime = 20;	//seconds
	
	public static void CreateLRnetwork(Scenario scenario) throws IOException {
		Network net = scenario.getNetwork();
		TransitSchedule ts = scenario.getTransitSchedule();
		
		//reading station file
        BufferedReader ReaderLRstations = new BufferedReader(new FileReader(LRstationsFilePath));
		Gson gsonLRstations = new Gson();
		LRstationsFile LRstationsFile = gsonLRstations.fromJson(ReaderLRstations, LRstationsFile.class);
		ReaderLRstations.close();
		//reading route file
		BufferedReader ReaderLRroutes = new BufferedReader(new FileReader(LRroutesFilePath));
		Gson gsonLRroutes = new Gson();
		LRroutesFile LRroutesFile = gsonLRroutes.fromJson(ReaderLRroutes, LRroutesFile.class);
		ReaderLRroutes.close();
		//reading headwayfile
		BufferedReader ReaderLRheadways = new BufferedReader(new FileReader(LRheadwayFilePath));
		Gson gsonLRheadways = new Gson();
		LRheadwayFile LRheadwayFile = gsonLRheadways.fromJson(ReaderLRheadways, LRheadwayFile.class);
		ReaderLRheadways.close();
		
		//actual thing
		//building the stations first
		HashMap<String, LRstationLink> builtStations = new HashMap<String, LRstationLink>();
		LRstationLink.initstationlinkbind();		//to fix the angle of the stations for a better fit
		for(LRstationData TLRstation: LRstationsFile.getLRstations()) {
			//there are multiple nodes for each station, rep platforms
			if(builtStations.containsKey("LR"+TLRstation.getref()))
				continue;
			//only taking the first shown up platform coord as station
			LRstationLink LRstationLinkObj = new LRstationLink(TLRstation, net, ts);		//building it
			builtStations.put("LR"+TLRstation.getref(), LRstationLinkObj);
		}
		
		//pre-create easy-to-messup links
		createLRlink(net, net.getNodes().get(Id.createNodeId("LR075_1")), net.getNodes().get(Id.createNodeId("LR230_1")));
		createLRlink(net, net.getNodes().get(Id.createNodeId("LR280_0")), net.getNodes().get(Id.createNodeId("LR275_0")));
		createLRlink(net, net.getNodes().get(Id.createNodeId("LR425_1")), net.getNodes().get(Id.createNodeId("LR390_0")));
		
		//create in-between links and route as the route builds
		HashMap<String, TransitRoute> builtLRroutes = new HashMap<String, TransitRoute>();
		for(LRroute LRrouteData: LRroutesFile.getLRroutes()) {
			Node prevNode = null;
			Node currNode = null;
			List<TransitRouteStop> transitRouteStopList = new ArrayList<TransitRouteStop>();
			List<Id<Link>> linkIdList = new ArrayList<Id<Link>>();
			
			//make iterator on stop list
			Iterator<String> currRouteStops = LRrouteData.getStopsRef().iterator();
			int stopnum = 2;
			double offset = LRrouteData.avgTravelTimeBetweenStations();
			while(currRouteStops.hasNext()) {
				//for first stop
				if(prevNode == null) {
					//get the first 2 stations
					LRstationLink startStation = builtStations.get("LR"+LRstationsFile.getLRstationData(currRouteStops.next()).getref());		//have to translate to the MTR code
					LRstationLink secondStation = builtStations.get("LR"+LRstationsFile.getLRstationData(currRouteStops.next()).getref());
					while(startStation==secondStation)	//check for duplicated stops
						secondStation = builtStations.get("LR"+LRstationsFile.getLRstationData(currRouteStops.next()).getref());
					//find exit node
					Node from0to = closerNode(startStation.getNode(0), secondStation);
					double Lfrom0 = NetworkUtils.getEuclideanDistance(startStation.getNode(0).getCoord(), from0to.getCoord());
					Node from1to = closerNode(startStation.getNode(1), secondStation);
					double Lfrom1 = NetworkUtils.getEuclideanDistance(startStation.getNode(1).getCoord(), from1to.getCoord());
					if(Math.min(Lfrom0, Lfrom1)==Lfrom0) {	//why does this have to be so complicated
						prevNode = startStation.getNode(0);
						currNode = from0to;
					} else {
						prevNode = startStation.getNode(1);
						currNode = from1to;
					}
					//join the nodes if link don't already exists
					Id<Link> linkId = Id.createLinkId(prevNode.getId().toString()+"-"+currNode.getId().toString());
					if(!net.getLinks().containsKey(linkId))
						createLRlink(net, prevNode, currNode);
					//add TansitRouteStop
					TransitRouteStop firstStop = ts.getFactory().createTransitRouteStop(startStation.getTSFfromPrevNode(prevNode), 0, dwellingTime/2);
					firstStop.setAwaitDepartureTime(true);
					transitRouteStopList.add(firstStop);
					TransitRouteStop secondStop = ts.getFactory().createTransitRouteStop(secondStation.getTSFfromCurrNode(currNode), offset-dwellingTime/2, offset+dwellingTime/2);
					secondStop.setAwaitDepartureTime(true);
					transitRouteStopList.add(secondStop);
					//add route
					linkIdList.add(startStation.getLinkIdfromPrevNode(prevNode));
					linkIdList.add(linkId);
					linkIdList.add(secondStation.getLinkIdfromCurrNode(currNode));
					//statistic for removing unused station links
					startStation.addUsageFromLinkId(startStation.getLinkIdfromPrevNode(prevNode));
					secondStation.addUsageFromLinkId(secondStation.getLinkIdfromCurrNode(currNode));
					//prep for next iteration
					prevNode = secondStation.getOtherNode(currNode);
				} else {
					LRstationLink secondStation = builtStations.get("LR"+LRstationsFile.getLRstationData(currRouteStops.next()).getref());
					while(closerNode(prevNode, secondStation)==prevNode)	//check for duplicated stops
						secondStation = builtStations.get("LR"+LRstationsFile.getLRstationData(currRouteStops.next()).getref());
					//check for existing link between the 2 stations
					if(net.getLinks().containsKey(Id.createLinkId(prevNode.getId().toString()+"-"+secondStation.getNode(0).getId().toString())))
						currNode = secondStation.getNode(0);
					else if (net.getLinks().containsKey(Id.createLinkId(prevNode.getId().toString()+"-"+secondStation.getNode(1).getId().toString())))
						currNode = secondStation.getNode(1);
					else {
						currNode = closerNode(prevNode, secondStation);
						createLRlink(net, prevNode, currNode);
					}
					//add TansitRouteStop
					if(currRouteStops.hasNext()) {
						TransitRouteStop trs = ts.getFactory().createTransitRouteStop(secondStation.getTSFfromCurrNode(currNode), 
								offset*stopnum-dwellingTime/2, offset*stopnum+dwellingTime/2);
						trs.setAwaitDepartureTime(true);
						transitRouteStopList.add(trs);
					
					}else {	//last stop
						TransitRouteStop trs = ts.getFactory().createTransitRouteStop(secondStation.getTSFfromCurrNode(currNode), 
								offset*stopnum-dwellingTime/2, offset*stopnum);
						trs.setAwaitDepartureTime(true);
						transitRouteStopList.add(trs);
					}
					//add route
					linkIdList.add(Id.createLinkId(prevNode.getId().toString()+"-"+currNode.getId().toString()));
					linkIdList.add(secondStation.getLinkIdfromCurrNode(currNode));
					//statistic for removing unused station links
					secondStation.addUsageFromLinkId(secondStation.getLinkIdfromCurrNode(currNode));
					//prep for next iteration
					prevNode = secondStation.getOtherNode(currNode);
					stopnum++;
				}
			}
			
			//put route into schedule and ts
			TransitRoute tsRoute = ts.getFactory().createTransitRoute(Id.create("LR"+LRrouteData.getRouteNumber()+" TO "+LRrouteData.getTo(), TransitRoute.class), 
					RouteUtils.createNetworkRoute(linkIdList, net), transitRouteStopList, "LR");
			builtLRroutes.put(LRrouteData.getId(), tsRoute);
		}
		
		//create vehicles and departures for the LRs
		//transit line created inside as well
		createVehiclesAndDepartures(ts, scenario.getTransitVehicles(), LRroutesFile, LRheadwayFile, builtLRroutes);
		
		//clean up un-used stations
		for(LRstationLink Temp: builtStations.values())
			Temp.cleanup(net, ts);
	}
	
	private static void createVehiclesAndDepartures(TransitSchedule ts, Vehicles vics, LRroutesFile LRroutesFile, LRheadwayFile LRheadwayFile, HashMap<String, TransitRoute> builtLRroutes) {
		VehicleType LRV1car = CreatePTUtils.createVehicleType(vics, "LRV-1car", 20.2, 2.65, 70/3.6, 26, 200, 0.3, 0.3, 1, "North-West Light Rail in one car set");
		VehicleType LRV2car = CreatePTUtils.createVehicleType(vics, "LRV-2car", 40.5, 2.65, 70/3.6, 52, 400, 0.15, 0.15, 1, "North-West Light Rail in two cars set");
		HashMap<String, Tuple<Double, Double>> vehicleNumbers = LRheadwayFile.getVehicles();
		
		//loop through by route number(ref)
		for(String Ref:vehicleNumbers.keySet()) {
			//create the vehicles for this line
			//!!!VehicleAmountMultiplier is contained in the LRheadwayFile class!!!
			LinkedList<Vehicle> vicList1 = new LinkedList<Vehicle>();
			LinkedList<Vehicle> vicList2 = new LinkedList<Vehicle>();
			for(int i=0; i<vehicleNumbers.get(Ref).getFirst(); i++) {
				Vehicle Tvic = vics.getFactory().createVehicle(Id.createVehicleId("LR"+Ref+"_1_"+i), LRV1car);
				vicList1.add(Tvic);
				vics.addVehicle(Tvic);
			}
			for(int i=0; i<vehicleNumbers.get(Ref).getSecond(); i++) {
				Vehicle Tvic = vics.getFactory().createVehicle(Id.createVehicleId("LR"+Ref+"_2_"+i), LRV2car);
				vicList2.add(Tvic);
				vics.addVehicle(Tvic);
			}
			LinkedList<Vehicle> SortedvicList = vicListScrambler(vicList1, vicList2, vehicleNumbers.get(Ref));
			
			//create copy of SortedvicList to be used for other direction
			LinkedList<Vehicle> SortedvicList2 = new LinkedList<Vehicle>();
			if(!LRroutesFile.isCircular(Ref)) {
				for(Vehicle temp: SortedvicList)
					SortedvicList2.add(temp);
				//to put the first half of the list to the back
				for(int i=0; i<SortedvicList2.size()/2;i++) {
					//to prevent concurrent modification
					Vehicle temp = SortedvicList2.poll();
					SortedvicList2.add(temp);
				}
			}
			
			//create the ts line
			TransitLine tsLine = ts.getFactory().createTransitLine(Id.create("LR"+Ref, TransitLine.class));
			
			//create departures
			boolean seconddirection = false;
			//lets hope no routes have more than 2 directions, or this would break so badly
			for(String id:LRroutesFile.getLRroutesIdByRef(Ref)) {
				if(seconddirection)
					SortedvicList = SortedvicList2;	//use the starting at middle list
				LRheadwayData LRheadwayData = LRheadwayFile.getHeadwayData(id);
				TransitRoute tsRoute = builtLRroutes.get(id);
				
				//first train
				double departTime = LRheadwayData.getStartTime();
				int counter = 0;
				double lastheadwayto = departTime;
				
				//middle stuff
				Iterator<LRheadway> headwayEntrItr = LRheadwayData.getHeadways().iterator();
				while(headwayEntrItr.hasNext()) {
					LRheadway LRheadway = headwayEntrItr.next();
					do {
						//account for trains only operating in busy hours
						if(departTime<LRheadway.getFrom()) {
							departTime = lastheadwayto;
							
							Departure d = ts.getFactory().createDeparture(Id.create("LR"+Ref+"_"+id+"_"+counter, Departure.class), departTime);
							d.setVehicleId(assignVic(SortedvicList));
							tsRoute.addDeparture(d);
							
							counter++;
							departTime = LRheadway.getFrom();
						}
						
						Departure d = ts.getFactory().createDeparture(Id.create("LR"+Ref+"_"+id+"_"+counter, Departure.class), departTime);
						d.setVehicleId(assignVic(SortedvicList));
						tsRoute.addDeparture(d);
						
						departTime += LRheadway.getHeadway()*60;
						counter++;
						lastheadwayto = LRheadway.getTo();
					}while(departTime>=LRheadway.getFrom() && departTime<LRheadway.getTo());
				}
				
				//lastTrain
				if(departTime>=LRheadwayData.getEndTime()) {
					Departure d = ts.getFactory().createDeparture(Id.create("LR"+Ref+"_"+id+"_"+counter, Departure.class), LRheadwayData.getEndTime());
					d.setVehicleId(assignVic(SortedvicList));
					tsRoute.addDeparture(d);
				}
				
				tsLine.addRoute(tsRoute);
				
				seconddirection = true;
			}
			
			//add Transit line to ts proper
			ts.addTransitLine(tsLine);
		}
	}
	
	public static Link createLRlink(Network net, Node fromNode, Node toNode) {
		NetworkFactory fac = net.getFactory();
		Link l = fac.createLink(Id.createLinkId(fromNode.getId().toString()+"-"+toNode.getId().toString()), fromNode, toNode);
		l.setAllowedModes(Sets.newHashSet("LR"));
		l.setCapacity(2000);
		l.setFreespeed(50/3.6);
		l.getAttributes().putAttribute("type", "1");
		net.addLink(l);
		return l;
	}
	
	private static Node closerNode(Node orgNode, LRstationLink nextStation) {
		double L0 = NetworkUtils.getEuclideanDistance(orgNode.getCoord(), nextStation.getNode(0).getCoord());
		double L1 = NetworkUtils.getEuclideanDistance(orgNode.getCoord(), nextStation.getNode(1).getCoord());
		if(L0 <= L1)
			return nextStation.getNode(0);
		else
			return nextStation.getNode(1);
	}
	
	private static LinkedList<Vehicle> vicListScrambler(LinkedList<Vehicle> vicList1, LinkedList<Vehicle> vicList2, Tuple<Double, Double> vicNumber){
		//no sorting required if any one type is 0 or 1
		if(vicNumber.getFirst()<=1 || vicNumber.getSecond()<=1) {
			vicList1.addAll(vicList2);
			return vicList1;
		}
		
		//sorts the vics such that they are evenly spread out
		LinkedList<Vehicle> vicList = new LinkedList<Vehicle>();
		double r = vicNumber.getFirst()/vicNumber.getSecond();
		while(!vicList1.isEmpty() && !vicList2.isEmpty()) {
			double i=1;
			do {
				vicList.add(vicList1.poll());
				i++;
			} while(i<=r);
			double j=1;
			do {
				vicList.add(vicList2.poll());
				j++;
			} while(j*r<=1);
		}
		
		return vicList;
	}
	
	private static Id<Vehicle> assignVic(LinkedList<Vehicle> vicList){
		Vehicle temp = vicList.poll();
		vicList.add(temp);
		return temp.getId();
	}

}

