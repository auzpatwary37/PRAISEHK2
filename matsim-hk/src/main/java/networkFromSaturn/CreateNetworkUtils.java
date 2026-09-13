package networkFromSaturn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalData;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesFactory;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.lanes.LanesUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import createBus.L2lLeastCostCalculatorFactory;
import createBus.L2lNetworkLeastCostPathCalculator;

/**
 * A utility class for the helper functions
 * 
 * @author eleead
 *
 */

public class CreateNetworkUtils {
	public static double scaleDownFactor_pt = 1; // This scale down factor for pt is used to efficient scale
															// down.
	public static double laneCapacity = 1400;
	private final static Logger log = Logger.getLogger(CreateNetworkUtils.class);

	public static int get_int(String string, int beginIndex, int endIndex) {
		return Integer.parseInt(string.substring(beginIndex, endIndex).replace(" ", "").replaceAll("[A-Za-z]", ""));
	}

	public static Id<Node> getNodeId(String string, int beginIndex, int endIndex, int zoneId) {
		if (endIndex - 5 != beginIndex) {
			throw new IllegalArgumentException("The node code should be 5 characters length!");
		}

		String id = Integer.toHexString(zoneId) + string.substring(beginIndex, endIndex).replace(" ", "0");
		return Id.createNodeId(id);
	}

	public static Id<Link> getLinkId(Id<Node> fromNodeId, Id<Node> toNodeId) {
		return getLinkId(fromNodeId.toString(), toNodeId.toString());
	}

	public static Id<Link> getLinkId(String fromNodeId, String toNodeId) {
		return Id.createLinkId(fromNodeId + "_" + toNodeId);
	}
	
	public static double obtainLaneLength(double linkLength) {
		return Math.min(150, linkLength * 0.8);
	}
	
	public static Link closestLinkToCoord(List<Link> linkList, Coord coord) {
		Link closestLink = null;
		double minDist = Double.MAX_VALUE;
		for(Link link: linkList) {
			double dist = NetworkUtils.getEuclideanDistance(link.getCoord(), coord);
			if( dist < minDist) {
				minDist = dist;
				closestLink = link;
			}
		}
		return closestLink;
	}
	
	private static boolean isBusOnlyLink(Link link) {
		if(link.getAllowedModes().contains("bus") && !link.getAllowedModes().contains(TransportMode.car)) {
			return true;
		}else {
			return false;
		}
	}
	
	/**
	 * If some link is bus only link, we track the upstream 
	 * and downstream links and change it to bus only if they are absolutely related.
	 * @param net
	 * @param lanes
	 */
	public static void adjustBusLinks(Scenario scenario, Network net, Lanes lanes) {
		Link trialLink = net.getLinks().entrySet().iterator().next().getValue(); //Randomly select a link for experiments
		
		List<Link> linkToProcess = Lists.newArrayList();
		for(Link link: net.getLinks().values()) {
			if(isBusOnlyLink(link)) {
				linkToProcess.add(link);
			}
		}
		
		while(linkToProcess.size()!=0) {
			Link link = linkToProcess.remove(0);
			L2lNetworkLeastCostPathCalculator l2lCal = new L2lLeastCostCalculatorFactory(scenario, 
					Sets.newHashSet(TransportMode.car)).getRoutingAlgo();
			
			//We process the downstream links
			LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(link.getId());
			List<Id<Link>> toLinkIds = new ArrayList<>();
			//We get the toLinks.
			if(l2l!=null) {
				for(Lane lane: l2l.getLanes().values()) {
					if(lane.getToLinkIds()!=null)
						toLinkIds.addAll(lane.getToLinkIds());
				}
			}else {
				for(Link toLink: link.getToNode().getOutLinks().values()) {
					toLinkIds.add(toLink.getId());
				}
			}
			for(Id<Link> linkId: toLinkIds) {
				if( !net.getLinks().get(linkId).getAllowedModes().contains(TransportMode.car) ) {
					continue;
				}
				try{
					l2lCal.calcLeastCostPath(trialLink.getId(), linkId, 0, null, null);
				}catch(RuntimeException e){
					if(e.getMessage().contains("No route found")) {
						Set<String> allowedModes = Sets.newHashSet(net.getLinks().get(linkId).getAllowedModes());
						allowedModes.remove(TransportMode.car);
						allowedModes.add("bus");
						net.getLinks().get(linkId).setAllowedModes(allowedModes);
						linkToProcess.add(net.getLinks().get(linkId));
					}
				}
			}
			
			List<Link> fromLinks = new ArrayList<>();
			//We get all upstream links
			for(Link fromLink: link.getFromNode().getInLinks().values()) {
				LanesToLinkAssignment fromL2l = lanes.getLanesToLinkAssignments().get(fromLink.getId());
				if(fromL2l == null) {
					fromLinks.add(fromLink);
				}else {
					for(Lane lane: fromL2l.getLanes().values()) {
						if(lane.getToLinkIds()!=null && lane.getToLinkIds().contains(link.getId())) {
							fromLinks.add(fromLink);
							break;
						}
					}
				}
			}
			for(Link fromLink: fromLinks) {
				if( !fromLink.getAllowedModes().contains(TransportMode.car) ) {
					continue;
				}
				try {
					l2lCal.calcLeastCostPath(fromLink.getId(), trialLink.getId(), 0, null, null);
				}catch(RuntimeException e) {
					if(e.getMessage().contains("No route found")) {
						Set<String> allowedModes = Sets.newHashSet(fromLink.getAllowedModes());
						allowedModes.remove(TransportMode.car);
						allowedModes.add("bus");
						fromLink.setAllowedModes(allowedModes);
						linkToProcess.add(fromLink);
					}
				}
			}
		}
	}
	
	public static void addNextLinkToLink(Lanes lanes, Id<Link> firstLinkId, Id<Link> secondLinkId) {
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(firstLinkId);
		if(l2l==null) {
			throw new IllegalArgumentException("No lanesToLinkAssignment of link "+firstLinkId.toString()+ "!");
		}
		addNextLinkToLink(lanes, firstLinkId, secondLinkId, Math.max(l2l.getLanes().size()-1, 1));
	}
	
	public static void addNextLinkToLink(Lanes lanes, Id<Link> firstLinkId, Id<Link> secondLinkId, int numOfLanes) {
		if(numOfLanes<=0) {
			throw new IllegalArgumentException("The number of lanes must be positive!");
		}
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(firstLinkId);
		if(l2l==null) {
			throw new IllegalArgumentException("No lanesToLinkAssignment of link "+firstLinkId.toString()+ "!");
		}
		
		int count = 0;
		for(Lane lane: l2l.getLanes().values()) {
			if(lane.getToLinkIds()!=null) {
				if(count<numOfLanes) {
					if(!lane.getToLinkIds().contains(secondLinkId))
						lane.addToLinkId(secondLinkId);
					count++;
				}else break;
			}
		}
		
		if(count!=numOfLanes) {
			throw new IllegalArgumentException("The number of lanes "+numOfLanes+" more than possible!");
		}
	}
	
	/**
	 * It will create or get a LanesToLinkAssignment object, which would create and add an empty 
	 * LanesToLinkAssigment if it is not appropriate.
	 * @param lanes The Lanes container
	 * @param linkId The linkId of the proposed LanesToLinkAssignment
	 * @return
	 */
	private static LanesToLinkAssignment createOrGetLanesToLinkAssignment(Lanes lanes, Link link) {
		LanesToLinkAssignment l2l;
		if(lanes.getLanesToLinkAssignments().containsKey(link.getId())) {
			l2l = lanes.getLanesToLinkAssignments().get(link.getId());
		}else {
			l2l = lanes.getFactory().createLanesToLinkAssignment(link.getId());
			lanes.addLanesToLinkAssignment(l2l);
			//Create the first lane.
			LanesUtils.createAndAddLane(l2l, lanes.getFactory(), Id.create(link.getId()+".ol", Lane.class), 
					link.getCapacity(), link.getLength(), 0, (int) link.getNumberOfLanes(), null, Lists.newArrayList());
		}
		return l2l;
	}
	
	/**
	 * Create a LanesToLinkAssignment,and give them links
	 * @param lanes
	 * @param fromLink
	 * @param alignment
	 * @param numOfRepresentedLanes
	 * @param toLinkIds
	 */
	public static void addNextLinksToLane(Lanes lanes, Link fromLink, int alignment, int numOfRepresentedLanes, 
			Id<Link>... toLinkIds) {
		LanesToLinkAssignment l2l = createOrGetLanesToLinkAssignment(lanes, fromLink);
		int seq = l2l.getLanes().size() - 1;
		Id<Lane> laneId = Id.create(fromLink.getId()+"_"+seq, Lane.class);
		LanesUtils.createAndAddLane(l2l, lanes.getFactory(), laneId, numOfRepresentedLanes * laneCapacity, 
				obtainLaneLength(fromLink.getLength()), alignment, numOfRepresentedLanes, 
				Lists.newArrayList(toLinkIds), null);
		
		for(Lane lane: l2l.getLanes().values()) {
			int lanesCount = 0;
			if(lane.getStartsAtMeterFromLinkEnd() == fromLink.getLength()) {
				lane.addToLaneId(laneId);
			}else {
				lanesCount += lane.getNumberOfRepresentedLanes();
			}
			if(lanesCount > fromLink.getNumberOfLanes()) {
				throw new IllegalArgumentException("The number of out lanes are more than it should be!");
			}
		}
	}
	
	public static void removeNextLinkFromLink(Lanes lanes, Id<Link> linkId, Id<Link> linkIdToRemove) {
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(linkId);
		Lane firstLane = null;
		List<Id<Lane>> lanesToRemove = new ArrayList<>();
		for(Lane lane: l2l.getLanes().values()) {
			if(lane.getToLinkIds()!=null) {
				List<Id<Link>> toLinkList = lane.getToLinkIds();
				if(toLinkList.contains(linkIdToRemove)) {
					toLinkList.remove(linkIdToRemove);
				}
				if(toLinkList.isEmpty()) {
					lanesToRemove.add(lane.getId());
				}
			}else {
				firstLane = lane;
			}
		}
		//Remove the lane that is empty
		for(Id<Lane> laneId: lanesToRemove) {
			firstLane.getToLaneIds().remove(laneId);
			l2l.getLanes().remove(laneId);
		}
		if(l2l.getLanes().isEmpty()) {
			throw new RuntimeException("No link to go!");
		}
	}
	
	/**
	 * Replace a linkId in lanes container.
	 * @param lanes
	 * @param oldLinkId
	 * @param newLinkId
	 */
	public static void replaceLinkForLanes(Lanes lanes, Id<Link> oldLinkId, Id<Link> newLinkId) {
		for(LanesToLinkAssignment l2l: lanes.getLanesToLinkAssignments().values()) {
			for(Lane lane: l2l.getLanes().values()) {
				if(lane.getToLinkIds()!=null) {
					List<Id<Link>> toLinkList = lane.getToLinkIds();
					if(toLinkList.contains(oldLinkId)) {
						toLinkList.remove(oldLinkId);
						toLinkList.add(newLinkId);
					}
				}
			}
		}
	}
	
	/**
	 * A helper method to split one link into two, the lane will be processed.
	 * @param net The network
	 * @param lanes The lanes container
	 * @param linkId The ID of link to be splitted
	 * @param nodeName The name of node
	 * @param firstLinkLength The length of first link
	 * @param secondLinkLength The length of second link
	 * @param splitCoord The coordinate of the splitting
	 */
	public static Node splitLink(Network net, Lanes lanes, SignalsData sig, Id<Link> linkId, 
			String nodeName, double firstLinkLength, double secondLinkLength, Coord splitCoord) {
		
		Node fromNode = net.getLinks().get(linkId).getFromNode();
		Node toNode = net.getLinks().get(linkId).getToNode();
		Node newNode; //Create and save a new node.
		Id<Node> newNodeId = Id.createNodeId(nodeName);
		if(!net.getNodes().containsKey(newNodeId)) {
			newNode = NetworkUtils.createAndAddNode(net, newNodeId, splitCoord);
		}else {
			newNode = net.getNodes().get(newNodeId);
			if(!newNode.getCoord().equals(splitCoord)) {
				throw new IllegalArgumentException("The coordinate is wrong!");
			}
			//log.warn("Getting a node "+newNodeId+" in the network.");
		}
		
		Link oldLink = net.removeLink(linkId);
		Link firstLink = NetworkUtils.createAndAddLink(net, Id.createLinkId(fromNode.getId().toString()+"_"+newNode.getId().toString()), 
				fromNode, newNode, firstLinkLength, oldLink.getFreespeed(), oldLink.getCapacity(), oldLink.getNumberOfLanes());
		firstLink.setAllowedModes(oldLink.getAllowedModes()); //Also correct the allowed modes
		Link secondLink = NetworkUtils.createAndAddLink(net, Id.createLinkId(newNode.getId().toString()+"_"+toNode.getId().toString()), 
				newNode, toNode, secondLinkLength, oldLink.getFreespeed(), oldLink.getCapacity(), oldLink.getNumberOfLanes());
		secondLink.setAllowedModes(oldLink.getAllowedModes()); //Also correct the allowed modes
		
		LanesFactory lf = lanes.getFactory();
		LanesToLinkAssignment l2lForSecondLink = null;
		//Replace the link entry to old link to the new first link.
		for(LanesToLinkAssignment l2l: lanes.getLanesToLinkAssignments().values()) {
			if( l2l.getLinkId().equals(oldLink.getId()) ){ //Once we found the old link information
				l2lForSecondLink = lf.createLanesToLinkAssignment(secondLink.getId());
				for(Lane lane: l2l.getLanes().values()) {
					//Modify the laneIds to be the new link ID
					Id<Lane> laneId = Id.create(lane.getId().toString().
							replace(oldLink.getId().toString(), secondLink.getId().toString()), Lane.class);
					if(lane.getToLinkIds()==null) {
						List<Id<Lane>> toLaneIds = new ArrayList<>();
						for(Id<Lane> oldToLaneId: lane.getToLaneIds()) {
							toLaneIds.add(Id.create(oldToLaneId.toString().
									replace(oldLink.getId().toString(), secondLink.getId().toString()), Lane.class));
						}
						LanesUtils.createAndAddLane(l2lForSecondLink, lf, laneId, lane.getCapacityVehiclesPerHour(), 
								secondLink.getLength(), lane.getAlignment(), (int) lane.getNumberOfRepresentedLanes(), 
								null, toLaneIds);
					}else { //For the lane that is in the end.
						LanesUtils.createAndAddLane(l2lForSecondLink, lf, laneId, lane.getCapacityVehiclesPerHour(), 
								obtainLaneLength(secondLink.getLength()), lane.getAlignment(), (int) lane.getNumberOfRepresentedLanes(), 
								lane.getToLinkIds(), null);
						if(sig.getSignalSystemsData().getSignalSystemData().containsKey(Id.create(
								toNode.getId().toString(), SignalSystem.class)))
							TESTreplaceLaneInSignal(sig, laneId.toString(), lane.getId().toString());
					}
				}
				continue;
			}
			for(Lane lane: l2l.getLanes().values()) {
				if(lane.getToLinkIds()!=null && lane.getToLinkIds().contains(linkId)) {
					lane.getToLinkIds().remove(linkId);
					lane.getToLinkIds().add(firstLink.getId());
				}
			}
		}
		if(l2lForSecondLink!=null) {
			lanes.getLanesToLinkAssignments().remove(oldLink.getId());
			lanes.addLanesToLinkAssignment(l2lForSecondLink);
		}
		
		for(Link link: fromNode.getInLinks().values()) {
			if(link.getFromNode().equals(toNode)) {
				//log.warn("The reverse link has not been processed yet.");
			}
		}
		
		return newNode;
	}
	
	/**
	 * Swaps the lane and link Ids from oldLane to newLane in SignalSystem.Signals.Signal <p>
	 * SignalSystem taken as the identical toNode of both oldLane and new Lane<br>
	 * added support for airport links
	 * 
	 * @param signalsData
	 * @param newLane
	 * @param oldLane
	 */
	public static void TESTreplaceLaneInSignal(SignalsData signalsData, String newLane, String oldLane) {
		//get the signal system from newLane
		Pattern pattern = Pattern.compile("_a?\\d+_");
		Matcher matcher1;
		Matcher matcher2;
		matcher1 = pattern.matcher(newLane);
		matcher2 = pattern.matcher(oldLane);
		matcher1.find();
		matcher2.find();
		String toNode1 = newLane.substring(matcher1.start()+1, matcher1.end()-1);
		String toNode2 = oldLane.substring(matcher2.start()+1, matcher2.end()-1);
		if(!toNode1.equals(toNode2))
			throw new IllegalArgumentException("The 2 Lanes have different ToNodes!!!");
		//get Link
		Pattern pattern2 = Pattern.compile("a?\\d+_a?\\d+_");
		Matcher matcher3;
		matcher3 = pattern2.matcher(newLane);
		matcher3.find();
		if(matcher3.start()!=0)
			throw new IllegalArgumentException("cannot match link from newLane "+newLane);
		
		//get the signal system ID
		Id<SignalSystem> TsigSys = Id.create(toNode1, SignalSystem.class);
		if(!signalsData.getSignalSystemsData().getSignalSystemData().containsKey(TsigSys))
			throw new IllegalArgumentException("No signal system at node "+toNode1);
		
		//make the swap
		for(Id<Signal> sig: signalsData.getSignalSystemsData().getSignalSystemData().get(TsigSys).getSignalData().keySet()) {
			if(signalsData.getSignalSystemsData().getSignalSystemData().get(TsigSys).getSignalData().get(sig).getLaneIds().contains(Id.create(oldLane, Lane.class))) {
				SignalData TsignalData = signalsData.getSignalSystemsData().getFactory().createSignalData(sig);
				TsignalData.setLinkId(Id.createLinkId(newLane.substring(0, matcher3.end()-1)));
				TsignalData.addLaneId(Id.create(newLane, Lane.class));
				signalsData.getSignalSystemsData().getSignalSystemData().get(TsigSys).getSignalData().replace(sig, TsignalData);
			}
		}
	}
	
	/**
	 * 
	 * @param net
	 * @param lanes
	 * @param fromNode
	 * @param toNode
	 * @param length
	 * @param freeSpeed
	 * @param toLinkIds
	 */
	public static Link addSingleLaneLink(Network net, Lanes lanes, Node fromNode, Node toNode, double length,
			double freeSpeed, Id<Link>... toLinkIds) {
		int numOfLanes = 1;
		Id<Link> linkId = Id.createLinkId(fromNode.getId().toString()+"_"+toNode.getId().toString());
		Link link = NetworkUtils.createAndAddLink(net, linkId, fromNode, toNode, length, freeSpeed, 
				numOfLanes * CreateNetworkUtils.laneCapacity, numOfLanes);
		
		if(toLinkIds!=null) {
			LanesToLinkAssignment l2l = lanes.getFactory().createLanesToLinkAssignment(linkId);
			Id<Lane> outLaneId = Id.create(linkId+"_0", Lane.class);
			LanesUtils.createAndAddLane(l2l, lanes.getFactory(), outLaneId, numOfLanes * CreateNetworkUtils.laneCapacity, 
					obtainLaneLength(length), 0, 1, Lists.newArrayList(toLinkIds), null); //Till the end
			
			//From the start
			LanesUtils.createAndAddLane(l2l, lanes.getFactory(), Id.create(linkId+".ol", Lane.class),
					numOfLanes * CreateNetworkUtils.laneCapacity, length, 0, 1, null, Lists.newArrayList(outLaneId));
			lanes.addLanesToLinkAssignment(l2l);
		}
		return link;
	}
	
	public static void checkL2lNetworkValidity(Scenario scenario) {
		L2lNetworkLeastCostPathCalculator l2l = new L2lLeastCostCalculatorFactory(scenario, 
				Sets.newHashSet(TransportMode.car, "bus")).getRoutingAlgo();
		Network net = scenario.getNetwork();
		Link trialLink = net.getLinks().entrySet().iterator().next().getValue(); //Randomly select a link
		
		int count = 0;
		//Check if every link can go to the link
		for(Link link: net.getLinks().values()) {
			if(!link.getId().equals(trialLink.getId())){
				if(link.getAllowedModes().contains("car")||link.getAllowedModes().contains("bus")) {
					l2l.calcLeastCostPath(trialLink, link, 0, null, null);
					l2l.calcLeastCostPath(link, trialLink, 0, null, null);
				}
			}
			count++;
			if(count%1000==0 && link.getAllowedModes().contains("car")) {
				trialLink = link;
				log.info("Checked "+count+" links.");
			}
		}
		
	}
	
	/**
	 * A convenient function to remove a link with it's respective lane.
	 * @param net
	 * @param lanes
	 * @param linkIds
	 */
	public static void removeLinkLane(Network net, Lanes lanes, SignalsData signalsData, Id<Link>... linkIds) {
		for(Id<Link> linkId: linkIds) {
			net.removeLink(linkId);
		}
		Iterator<Entry<Id<Link>, LanesToLinkAssignment>> iterL2l = lanes.getLanesToLinkAssignments().entrySet().iterator();
		while(iterL2l.hasNext()) {
			Entry<Id<Link>, LanesToLinkAssignment> l2l= iterL2l.next();
			if(Arrays.asList(linkIds).contains(l2l.getKey())) {
				iterL2l.remove(); //Remove it if the it is the l2l assignment with that link id.
				continue;
			}
			List<Id<Lane>> lanesToRemove = new ArrayList<>();
			Lane firstLane = null;
			Iterator<Entry<Id<Lane>, Lane>> iterLane = l2l.getValue().getLanes().entrySet().iterator();
			while(iterLane.hasNext()) {
				Entry<Id<Lane>, Lane> lane = iterLane.next();
				List<Id<Link>> toLinkIds = lane.getValue().getToLinkIds();
				if(toLinkIds!=null) {
					for(Id<Link> linkId: linkIds) {
						if(toLinkIds.contains(linkId))
							toLinkIds.remove(linkId); //Remove the link Id if not appropriate
					}
				}
				if(firstLane==null || toLinkIds==null) {
					firstLane=lane.getValue(); // Store the lane
				}
				if(toLinkIds!=null && toLinkIds.isEmpty()) {
					lanesToRemove.add(lane.getValue().getId());
					iterLane.remove(); //Remove whole lane if it has no link.
				}
			}
			if(firstLane.getToLaneIds()!=null) //This condition exists for those link with only one lane.
				firstLane.getToLaneIds().removeAll(lanesToRemove);//remove lanes that is removed from the first link
			
			if(l2l.getValue().getLanes().size()==0) {
				throw new RuntimeException("There is no downstream link!");
			}
			if(firstLane.getToLaneIds()!=null && l2l.getValue().getLanes().size()==1) {
				iterL2l.remove(); //Remove if it has only the .ol lane.
			}
		}
	}
	
	/**
	 * This function is to fix the transit link after replacing a link by two links.
	 * @param net Network
	 * @param ts TransitSchedule
	 * @param originLinkId The link originally there
	 * @param replacement A list of id of link replaced the link
	 */
	public static void replaceTransitLink(Network net, TransitSchedule ts, Id<Link> originLinkId, 
			List<Id<Link>> replacement) {
		List<Link> replacementLinkList = new ArrayList<Link>();
		//Validity check
		for(Id<Link> linkId: replacement) {
			if( ! net.getLinks().containsKey(linkId)) {
				throw new IllegalArgumentException("The link "+linkId+" is not in the network!");
			}
			replacementLinkList.add(net.getLinks().get(linkId));
		}
		
		//Fix the link in the transit stop facility.
		for(TransitStopFacility tsf: ts.getFacilities().values()) {
			if(tsf.getLinkId().equals(originLinkId)) {
				Link replaceLink = closestLinkToCoord(replacementLinkList, tsf.getCoord());
				tsf.setLinkId(replaceLink.getId());
			}
		}
		
		for(TransitLine tl: ts.getTransitLines().values()) {
			for(TransitRoute tr: tl.getRoutes().values()) {
				NetworkRoute route = tr.getRoute();
				//Case 1. The replacement link is the start
				if(route.getStartLinkId().equals(originLinkId)) {
					List<Id<Link>> newList = Lists.newLinkedList(route.getLinkIds());
					for(int i = replacement.size() - 1; i > 0 ; i--) {
						newList.add(0, replacement.get(i));
					}
					route.setLinkIds(replacement.get(0), newList, route.getEndLinkId());					

				}else if(route.getEndLinkId().equals(originLinkId)) {
					//Case 2. The replaced link is in the end
					List<Id<Link>> newList = Lists.newLinkedList(route.getLinkIds());
					//route.setEndLinkId(replacement.get(replacement.size()-1));
					for( int i = 0; i < replacement.size() - 1 ; i++) {
						newList.add(replacement.get(i));
					}
					route.setLinkIds(route.getStartLinkId(), newList, replacement.get(replacement.size()-1));
					
				}else if(route.getLinkIds().contains(originLinkId)){
					//Case 3. The replaced link is in the middle
					List<Id<Link>> newList = Lists.newLinkedList(route.getLinkIds());
					int index = newList.indexOf(originLinkId);
					newList.remove(index);
					for(int i = replacement.size() - 1; i >= 0 ; i--) {
						newList.add(index, replacement.get(i));
					}
					route.setLinkIds(route.getStartLinkId(), newList, route.getEndLinkId());	
				}
			}
		}
	}
	
	public static void setLinkLengthWithLane(Network net, Lanes lanes, String linkId, double length) {
		Link oldLink = net.getLinks().get(Id.createLinkId(linkId));
		double oldLength = oldLink.getLength();
		oldLink.setLength(length); //Set the length
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(Id.createLinkId(linkId));
		if(l2l!=null) {
			for(Lane lane: l2l.getLanes().values()) {
				if(lane.getStartsAtMeterFromLinkEnd()==oldLength) {
					lane.setStartsAtMeterFromLinkEnd(length);
				}else {
					lane.setStartsAtMeterFromLinkEnd(obtainLaneLength(length));
				}
			}
		}
	}
	
	public static void setLinkCapacityWithLane(Network net, Lanes lanes, String linkId, double capacity_pcu) {
		Link link = net.getLinks().get(Id.createLinkId(linkId));
		link.setCapacity(capacity_pcu);
		double capacityPerLane = capacity_pcu / link.getNumberOfLanes();
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(Id.createLinkId(linkId));
		if(l2l!=null) {
			for(Lane lane: l2l.getLanes().values()) {
				lane.setCapacityVehiclesPerHour(capacityPerLane * lane.getNumberOfRepresentedLanes());
			}
		}
	}

	/**
	 * Get the distance between a link and a point Reference to
	 * https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line
	 * 
	 * @param link
	 * @param coord
	 * @return
	 */
	public static double getDistance(Link link, Coord coord) {
		Coord coord1 = link.getFromNode().getCoord();
		double y1 = coord1.getY();
		double x1 = coord1.getX();
		Coord coord2 = link.getToNode().getCoord();
		double y2 = coord2.getY();
		double x2 = coord2.getX();
		double y = coord.getY();
		double x = coord.getX();
		return Math.abs((y2 - y1) * x - (x2 - x1) * y + x2 * y1 - y2 * x1)
				/ Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));
	}
}
