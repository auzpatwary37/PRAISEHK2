package networkFromSaturn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalGroupSettingsData;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalPlanData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalData;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalPlan;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesFactory;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.lanes.LanesUtils;
import com.google.common.collect.Sets;

import createBus.BusDataExtractor;
import networkFromSaturn.CreateNetwork;

public class JacobTest {
	
	private static final Logger log = Logger.getLogger(JacobTest.class);
	
	public static void jacobadjustmentstest(Network net, Lanes lane, SignalsData sig) {
		
	}
	
	/**
	 * adds additional toLinks to an already existing lane
	 * @param lanes
	 * @param fromLink
	 * @param laneseq
	 * @param toLinkIds
	 */
	@SafeVarargs
	public static void TESTmodifyNextLinksInLane(Lanes lanes, Id<Link> fromLink, int laneseq, Id<Link>... toLinkIds) {
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(fromLink);
		if(l2l == null)
			throw new IllegalArgumentException(fromLink.toString()+ " has no LanesToLinkAssignment");
		if(!l2l.getLanes().containsKey(Id.create(fromLink.toString()+"_"+laneseq, Lane.class))) {
			throw new IllegalArgumentException("lane seq "+laneseq+" does not exsist!");
		}
		
		Lane l = l2l.getLanes().get(Id.create(fromLink.toString()+"_"+laneseq, Lane.class));
		for(Id<Link> linkId: toLinkIds) {
			if(!l.getToLinkIds().contains(linkId))
				l.addToLinkId(linkId);
		}
	}
	
	/**
	 * In an already existing Lane, Remove and replace toLinkIds, Sets alignment
	 * @param lanes
	 * @param fromLink
	 * @param laneseq
	 * @param alignment
	 * @param toLinkIds
	 */
	@SafeVarargs
	public static void TESTredoLane(Lanes lanes, Id<Link> fromLink, int laneseq, int alignment, Id<Link>... toLinkIds) {
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(fromLink);
		if(l2l == null)
			throw new IllegalArgumentException("Link "+fromLink.toString()+ " has no LanesToLinkAssignment");
		if(!l2l.getLanes().containsKey(Id.create(fromLink.toString()+"_"+laneseq, Lane.class))) {
			throw new IllegalArgumentException("lane seq "+laneseq+" does not exsist!");
		}
		
		Lane l = l2l.getLanes().get(Id.create(fromLink.toString()+"_"+laneseq, Lane.class));
		l.getToLinkIds().clear();
		for(Id<Link> LinkId: toLinkIds)
			l.addToLinkId(LinkId);
		l.setAlignment(alignment);
	}
	
	/**
	 * Changes the number of lanes of an oldLink		<p>
	 * !!!Old LanesToLinkAssignment removed, new LanesToLinkAssignment NOT created!!!
	 * @param net
	 * @param lanes
	 * @param oldLinkId
	 * @param numoflanes
	 * @return Link		!!!NO LanesToLinkAssignment!!!
	 */
	public static Link TESTrenumlaneLink(Network net, Lanes lanes, Id<Link> oldLinkId, int numoflanes) {
		Link oldLink = net.getLinks().get(oldLinkId);
		if(oldLink == null)
			throw new IllegalArgumentException("Link "+oldLinkId.toString()+" does not exist!");
		Node fromNode = oldLink.getFromNode();
		Node toNode = oldLink.getToNode();
		double freespeed = oldLink.getFreespeed();
		double length = oldLink.getLength();
		net.removeLink(oldLinkId);
		lanes.getLanesToLinkAssignments().remove(oldLinkId);
		return CreateNetwork.create_links(net, fromNode.getId().toString(), toNode.getId().toString(), numoflanes, freespeed, length);
	}
	
	/**
	 * Creates roundabout at node by removing LanesToLinkAssignments of links going into Node
	 * @param net
	 * @param lanes
	 * @param Node
	 */
	public static void TESTcreateRoundabout(Network net, Lanes lanes, String Node) {
		if(!net.getNodes().containsKey(Id.createNodeId(Node)))
			throw new IllegalArgumentException("Node "+Node+" does not exist!");
		for(Link L: net.getLinks().values()) 
			if(L.getToNode().getId().equals(Id.createNodeId(Node))) 
				lanes.getLanesToLinkAssignments().remove(L.getId());
	}	
	
	/**
	 * Combines >2 Links into 1, changes lane info going into the firtsLink to newLink, retains toLinks of endLink	<p>
	 * entries into startLinks must be sequential starting from the first link 
	 *
	 * @param net
	 * @param lanes
	 * @param removeNodes	removes intermediate nodes if true
	 * @param endLink		
	 * @param startLinks	input must be sequential
	 * @return newLink	
	 */
	@SafeVarargs
	public static Link TESTcombineLinks(Network net, Lanes lanes, SignalsData sig, boolean removeNodes, String endLink, String... startLinks) {
		Node startNode = net.getLinks().get(Id.createLinkId(startLinks[0])).getFromNode();
		Node endNode = net.getLinks().get(Id.createLinkId(endLink)).getToNode();
		double numoflanes = net.getLinks().get(Id.createLinkId(endLink)).getNumberOfLanes();
		double freespeed = net.getLinks().get(Id.createLinkId(endLink)).getFreespeed();
		List<Node> nodesToRemove = new ArrayList<Node>();
		
		//get total length and check if links are sequential
		double length = net.getLinks().get(Id.createLinkId(endLink)).getLength();
		Node endLinkFromNode = net.getLinks().get(Id.createLinkId(endLink)).getFromNode();
		Node TstartLinksToNode = net.getLinks().get(Id.createLinkId(startLinks[0])).getToNode();
		for(String TlinkS:startLinks) {
			//get length part
			length += net.getLinks().get(Id.createLinkId(TlinkS)).getLength();
			//checking part
			Node TstartLinksFromNode = net.getLinks().get(Id.createLinkId(TlinkS)).getFromNode();
			if(TlinkS!=startLinks[0]&&TstartLinksFromNode!=TstartLinksToNode)
				throw new IllegalArgumentException("startLinks are not sequential"); 
			TstartLinksToNode = net.getLinks().get(Id.createLinkId(TlinkS)).getToNode();
			nodesToRemove.add(TstartLinksToNode);
			//checks if to remove nodes has signal
			if(sig.getSignalSystemsData().getSignalSystemData().containsKey(Id.create(nodesToRemove.toString(), SignalSystem.class)))
				log.warn("The node "+nodesToRemove.toString()+" contains signal!");
		}
		if(TstartLinksToNode!=endLinkFromNode)
			throw new IllegalArgumentException("startLinks does not lead to endLink");
		
		//create the new link
		Link newLink = CreateNetwork.create_links(net, startNode.getId().toString(), endNode.getId().toString(), Math.toIntExact(Math.round(numoflanes)), freespeed, length);
		
		//rip off from splitLink, made changes
		LanesFactory lf = lanes.getFactory();			//kept lf as it can keep old capacity
		LanesToLinkAssignment l2lForNewLink = null;
		//Replace the link entry to old link to the new first link.
		for(LanesToLinkAssignment l2l: lanes.getLanesToLinkAssignments().values()) {
			if( l2l.getLinkId().equals(Id.createLinkId(endLink)) ){ //Once we found the old link information
				l2lForNewLink = lf.createLanesToLinkAssignment(newLink.getId());
				for(Lane lane: l2l.getLanes().values()) {
					//Modify the laneIds to be the new link ID
					Id<Lane> laneId = Id.create(lane.getId().toString().replace(endLink, newLink.getId().toString()), Lane.class);
					if(lane.getToLinkIds()==null) {
						List<Id<Lane>> toLaneIds = new ArrayList<>();
						for(Id<Lane> oldToLaneId: lane.getToLaneIds()) {
							toLaneIds.add(Id.create(oldToLaneId.toString().replace(endLink, newLink.getId().toString()), Lane.class));
						}
						LanesUtils.createAndAddLane(l2lForNewLink, lf, laneId, lane.getCapacityVehiclesPerHour(), 
								length, lane.getAlignment(), (int) lane.getNumberOfRepresentedLanes(), 
								null, toLaneIds);
					}else {
						LanesUtils.createAndAddLane(l2lForNewLink, lf, laneId, lane.getCapacityVehiclesPerHour(), 
								CreateNetworkUtils.obtainLaneLength(length), lane.getAlignment(), (int) lane.getNumberOfRepresentedLanes(), 
								lane.getToLinkIds(), null);
						
						//patch for signal change
						if(sig.getSignalSystemsData().getSignalSystemData().containsKey(Id.create(endNode.getId().toString(), SignalSystem.class)))
							JacobTest.TESTreplaceLaneInSignal(sig, laneId.toString(), lane.getId().toString());
					}
				}
				continue;
			}
			for(Lane lane: l2l.getLanes().values()) {
				if(lane.getToLinkIds()!=null && lane.getToLinkIds().contains(Id.createLinkId(startLinks[0]))) {
					lane.getToLinkIds().remove(Id.createLinkId(startLinks[0]));
					lane.getToLinkIds().add(newLink.getId());
				}
			}
		}
		if(l2lForNewLink!=null) {
			lanes.addLanesToLinkAssignment(l2lForNewLink);
		}
		
		//remove old links
		CreateNetworkUtils.removeLinkLane(net, lanes, sig, Id.createLinkId(endLink));
		for(String TlinkS:startLinks)
			CreateNetworkUtils.removeLinkLane(net, lanes, sig, Id.createLinkId(TlinkS));
		//remove intermediate Nodes
		if(removeNodes)
			for(Node TNode:nodesToRemove)
				net.removeNode(TNode.getId());
		
		return newLink;
	}
	
	/**
	 * Removes lane info going into oldLink<br>
	 * Adds newLink iff its not already there
	 * @param net
	 * @param lanes
	 * @param newLink
	 * @param oldLink
	 * @param removeOldLink
	 */
	public static void TESTreplacel2lIntoOldLinkToNewLink(Network net, Lanes lanes, SignalsData sig, Link newLink, String oldLink, boolean removeOldLink) {
		//Replace the link entry to old link to the new link.
		for(LanesToLinkAssignment l2l: lanes.getLanesToLinkAssignments().values()) {
			for(Lane lane: l2l.getLanes().values()) {
				if(lane.getToLinkIds()!=null && lane.getToLinkIds().contains(Id.createLinkId(oldLink))) {
					lane.getToLinkIds().remove(Id.createLinkId(oldLink));
					if(!lane.getToLinkIds().contains(newLink.getId()))
						lane.getToLinkIds().add(newLink.getId());
				}
			}
		}
		if(removeOldLink)
			CreateNetworkUtils.removeLinkLane(net, lanes, sig, Id.createLinkId(oldLink));
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
		if(matcher3.start()!=0 && !newLane.substring(0, matcher3.start()).equals("BUS-"))
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
	 * Checks if lane already has a signal<br>
	 * If not, creates a new Signal for the Lane, where Signal Id taken from largest+1 of other Ids in the same SignalSystem	<p>
	 * Puts the Signal into the specified SignalGroup, creates a new SignalGroup if SignalGroup not present<br>
	 * Does not remove signal from other groups if signal already exists 		</p><p>
	 * SignalSystem taken as the toNode of new Lane																		</p><p>
	 * added untested support for airport links
	 * 
	 * @param signalsData
	 * @param newLane
	 * @param signalGroupNum
	 */
	public static void TESTaddLaneIntoSignalAndGroup(SignalsData signalsData, String newLane, int signalGroupNum) {
		//get signal system from newLane
		Pattern pattern = Pattern.compile("_a?\\d+_");
		Matcher matcher = pattern.matcher(newLane);
		matcher.find();
		Id<SignalSystem> sigSysId = Id.create(newLane.substring(matcher.start()+1, matcher.end()-1), SignalSystem.class);
		
		//get link from newLane
		Pattern pattern2 = Pattern.compile("a?\\d+_a?\\d+_");
		Matcher linkMatcher = pattern2.matcher(newLane);
		linkMatcher.find();
		if(linkMatcher.start()!=0 && !newLane.substring(0, linkMatcher.start()).equals("BUS-"))
			throw new IllegalArgumentException("cannot match link from newLane "+newLane);
		
		//check if signal for lane already exists
		Id<Signal> sigId = null;
		if(signalsData.getSignalSystemsData().getSignalSystemData().get(sigSysId).getSignalData() != null)
			for(SignalData sigData: signalsData.getSignalSystemsData().getSignalSystemData().get(sigSysId).getSignalData().values()) {
				if(sigData.getLaneIds().contains(Id.create(newLane, Signal.class))) {
					sigId = sigData.getId();
					break;
				}
			}
		//creates new sig if non-existence
		if(sigId == null) {
			//get largest signal		
			int maxId = 0;
			if(signalsData.getSignalSystemsData().getSignalSystemData().get(sigSysId).getSignalData() != null)
				for(Id<Signal> TsigId: signalsData.getSignalSystemsData().getSignalSystemData().get(sigSysId).getSignalData().keySet()) {
					maxId = Math.max(maxId, Integer.parseInt(TsigId.toString()));
				}
			sigId = Id.create(maxId+1, Signal.class);
	
			//add Signal
			SignalData TsignalData = signalsData.getSignalSystemsData().getFactory().createSignalData(sigId);
			TsignalData.setLinkId(Id.createLinkId(newLane.substring(0, linkMatcher.end()-1)));
			TsignalData.addLaneId(Id.create(newLane, Lane.class));
			signalsData.getSignalSystemsData().getSignalSystemData().get(sigSysId).addSignalData(TsignalData);
		}
		
		//add sigId to Signal Group, create Signal Group if it doesn't exist
		if(!signalsData.getSignalGroupsData().getSignalGroupDataBySystemId(sigSysId).containsKey(Id.create(signalGroupNum, SignalGroup.class))) 
			signalsData.getSignalGroupsData().addSignalGroupData(signalsData.getSignalGroupsData().getFactory().createSignalGroupData(sigSysId, Id.create(signalGroupNum, SignalGroup.class)));
		
		signalsData.getSignalGroupsData().getSignalGroupDataBySystemId(sigSysId).get(Id.create(signalGroupNum, SignalGroup.class)).addSignalId(sigId);
		
	}
	
	/**
	 * Creates a new SignalGroupSetting in signalController.SignalPlan1<p>
	 * !!!This method assumes only 1 SignalPlan that is Plan1, no plan B!!!
	 * 
	 * @param signalsData
	 * @param signalSystem
	 * @param signalGroupNum
	 * @param onset
	 * @param dropping
	 */
	public static void TESTcreateAndAddSignalGroupSetting(SignalsData signalsData, String signalSystem, int signalGroupNum, int onset, int dropping ) {
		SignalGroupSettingsData sigGpSet = signalsData.getSignalControlData().getFactory().createSignalGroupSettingsData(Id.create(signalGroupNum, SignalGroup.class));
		sigGpSet.setOnset(onset);
		sigGpSet.setDropping(dropping);
		signalsData.getSignalControlData().getSignalSystemControllerDataBySystemId().get(Id.create(signalSystem, SignalSystem.class)).getSignalPlanData().get(Id.create("1", SignalPlan.class)).addSignalGroupSettings(sigGpSet);		
		//add sigId to Signal Group, create Signal Group if it doesn't exist
		if(signalsData.getSignalGroupsData().getSignalGroupDataBySystemId(Id.create(signalSystem, SignalSystem.class)) == null)
			signalsData.getSignalGroupsData().addSignalGroupData(signalsData.getSignalGroupsData().getFactory().createSignalGroupData(Id.create(signalSystem, SignalSystem.class), Id.create(signalGroupNum, SignalGroup.class)));
		else if(!signalsData.getSignalGroupsData().getSignalGroupDataBySystemId(Id.create(signalSystem, SignalSystem.class)).containsKey(Id.create(signalGroupNum, SignalGroup.class))) 
			signalsData.getSignalGroupsData().addSignalGroupData(signalsData.getSignalGroupsData().getFactory().createSignalGroupData(Id.create(signalSystem, SignalSystem.class), Id.create(signalGroupNum, SignalGroup.class)));
	}
	
	/**
	 * Helper method to check if the declared number of lanes of the Link<br>
	 * equals to the total number of represented lanes of all the lanes<br>
	 * that are toLanes of the .ol lane<br><br>
	 * This is to check improper changes to the network where the l2l of the link was not fixed when:
	 * <ul><li> A link was removed
	 * <li> A link was replaced
	 * <li> Re-numbering of the number of lanes in a link
	 * <li> Incorrect tansfer of l2l info from old link to new link
	 * </ul>
	 * @param net
	 * @param lanes
	 * @param outputToFile if ture then write to file, if false then throws when error
	 * @author JLo
	 * @throws IOException 
	 */
	public static void TESTcheckRepresentedLanesNum(Network net, Lanes lanes, boolean outputToFile) throws IOException {
		final Path path = Paths.get("C:/Users/FYP/Desktop/All you need/networkLinkLanesdebug.txt");
		
		for(Id<Link> LinkId: net.getLinks().keySet()) {
			double numOfLanes = net.getLinks().get(LinkId).getNumberOfLanes();
			double numOfRepresentedLanes = 0;
			LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(LinkId);
			//no need to check free links
			if(l2l==null)
				continue;
			//pickup links that have names not reflected in l2l lanes
			if(!l2l.getLanes().containsKey(Id.create(LinkId.toString()+".ol", Lane.class))) {
				if(!outputToFile)
					throw new IllegalArgumentException("Lane Ids on Link "+LinkId.toString()+" is inconsistent!!!");
				Files.write(path, Arrays.asList(LinkId.toString()+" mismatched names"), StandardCharsets.UTF_8,
					Files.exists(path) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
				continue;
			}
			//get l2l-s that have more than .ol lane
			HashSet<Id<Lane>> dupLaneCheck = new HashSet<Id<Lane>>();
			if(l2l.getLanes().size()>1)
				for(Id<Lane> laneId: l2l.getLanes().get(Id.create(LinkId.toString()+".ol", Lane.class)).getToLaneIds()) {	//change to not dependent on perfect naming
					numOfRepresentedLanes = numOfRepresentedLanes + l2l.getLanes().get(laneId).getNumberOfRepresentedLanes();
					if(!dupLaneCheck.add(laneId)) {
						if(!outputToFile)
							throw new IllegalArgumentException("Lane Ids on Link "+LinkId.toString()+" has duplicates!!!");
						Files.write(path, Arrays.asList(LinkId.toString()+" duplicate lane Ids"), StandardCharsets.UTF_8,
							Files.exists(path) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
					}
				}
			//printout the problem links
			if(numOfLanes!=numOfRepresentedLanes) {
				if(!outputToFile)
					throw new IllegalArgumentException("Number of Lanes in Link "+LinkId.toString()+" is inconsistent to l2l!!!");		//due to full network incomplete, current solution to isolate working areas
				Files.write(path, Arrays.asList(LinkId.toString()), StandardCharsets.UTF_8,
				        Files.exists(path) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
			}
		}
	}
	
	/**
	 * Reorders the lanes of the specified link such that<br>
	 * the lane ids are sequentially ordered starting from 0
	 * @param net
	 * @param lanes
	 * @param sig
	 * @param LinkId
	 */
	public static void TESTfixLaneIds(Network net, Lanes lanes, SignalsData sig, String LinkId) {
		TreeMap<Integer, Tuple<Double, List<Id<Link>>>> tempMap = new TreeMap<Integer, Tuple<Double, List<Id<Link>>>>();
		
		Pattern pattern = Pattern.compile("\\d+_\\d+_");
		String oldId=null;
		
		for(Lane TLane: lanes.getLanesToLinkAssignments().get(Id.createLinkId(LinkId)).getLanes().values()) {
			Matcher match = pattern.matcher(TLane.getId().toString());
			if(match.find()) {
				oldId = match.group();		//so badly written
				tempMap.put(TLane.getAlignment(), new Tuple<Double, List<Id<Link>>>(TLane.getNumberOfRepresentedLanes(), TLane.getToLinkIds()));
			}
		}
		
		lanes.getLanesToLinkAssignments().remove(Id.createLinkId(LinkId));
		Link TLink = net.getLinks().get(Id.createLinkId(LinkId));
		String toNode = TLink.getToNode().getId().toString();
		
		int i = 0;
		for(Integer alignment: tempMap.keySet()) {
			CreateNetworkUtils.addNextLinksToLane(lanes, TLink, alignment.intValue(), Math.toIntExact(Math.round(tempMap.get(alignment).getFirst())), tempMap.get(alignment).getSecond().get(0));
			//gave up trying to jam the list into addNextLinksToLane
			if(tempMap.get(alignment).getSecond().size()>1)
				for(Id<Link> TID:tempMap.get(alignment).getSecond())
					TESTmodifyNextLinksInLane(lanes, Id.createLinkId(LinkId), i, TID);
			//check signal system exists thingie
			if(sig.getSignalControlData().getSignalSystemControllerDataBySystemId().containsKey(Id.create(toNode, SignalSystem.class)))
				TESTreplaceLaneInSignal(sig, LinkId+"_"+i, oldId+"_"+i);
			i++;
		}
	}
	
	/**
	 * Convenience function to convert WGS84 to Saturn
	 * @param lat
	 * @param lon
	 * @return
	 */
	public static Coord TESTconvertCoord(double lat, double lon) {
		CoordinateTransformation ct = new WGS84toSaturn();
		return ct.transform(new Coord(lon, lat));
	}
	
	/**
	 * Creates Bus-link at the specified link (ogLink), with same fromNode and toNode, named (<i>"BUS-"+LinkIdS</i>)<br>
	 * The bus link could be created as an replacement of an existing lane, or an additional lane/link.<br>
	 * If created as a replacement, the signal will be transferred as well<br>
	 * <b>Does NOT check for in-bound links</b>, have to add toLink to the newly created bus-Link outside
	 * <p>
	 * <b>If the ogLink has no l2l</b><br>
	 * creates bus link without l2l and renum ogLink if needed<p>
	 * 
	 * <b>--= Behaviour when creating bus-link as an additional lane/link (replacesLaneNum = -1) =--</b><br>
	 * toAllLinksInLanes; if true works as intended; if false no l2l will be created<br>
	 * no signal will be created for the bus link (use <i>TESTaddLaneIntoSignalAndGroup</i>)
	 * 
	 * @param net
	 * @param lanes
	 * @param sig
	 * @param LinkIdS - The linkId in string
	 * @param replacesLaneNum - Lane number which the bus lane will replace from, input -1 to create new bus link only
	 * @param toAllLinksInLanes - If true will aggregate all the tolinks in the ogLink; If false only the replace-lane's tolinks; doesn't matter if ogLink has no l2l
	 * @return Link - Newly created bus link
	 */
	public static Link createInSituBusLane(Network net, Lanes lanes, SignalsData sig, String LinkIdS, int replacesLaneNum, boolean toAllLinksInLanes) {
		//Get needed stuff
		HashMap<Integer, Lane> toLanesMap = new HashMap<Integer, Lane>();
		HashMap<Integer, Id<SignalGroup>> sigMap = new HashMap<Integer, Id<SignalGroup>>();
		Link ogLink = net.getLinks().get(Id.createLinkId(LinkIdS));
		int numOfLanes = 0;
		//check for signal system
		Boolean hasSignal = sig.getSignalControlData().getSignalSystemControllerDataBySystemId().containsKey(Id.create(ogLink.getToNode().getId().toString(), SignalSystem.class));
		Id<SignalSystem> sigSystem = null;
		//check for l2l
		Boolean hasl2l = lanes.getLanesToLinkAssignments().containsKey(Id.createLinkId(LinkIdS));
		
		if(hasl2l) {
			if(hasSignal)
				sigSystem = Id.create(ogLink.getToNode().getId().toString(), SignalSystem.class);
			
			//get needed info for rebuild
			for(Entry<Id<Lane>, Lane> TEntry: lanes.getLanesToLinkAssignments().get(Id.createLinkId(LinkIdS)).getLanes().entrySet()) {
				//ignore ol lane
				if(TEntry.getKey().toString().contains(".ol"))
					continue;
				//store whole lane for use later
				int laneNum = Integer.parseInt(TEntry.getKey().toString().substring(TEntry.getKey().toString().length()-1));
				toLanesMap.put(laneNum, TEntry.getValue());
				if(laneNum!=replacesLaneNum)
					numOfLanes+=TEntry.getValue().getNumberOfRepresentedLanes();
				
				//store sig gp
				if(hasSignal)
					for(Entry<Id<SignalGroup>, SignalGroupData> TSigEntry: sig.getSignalGroupsData().getSignalGroupDataBySystemId(sigSystem).entrySet()) {
						Iterator<Id<Signal>> sigGpItr = TSigEntry.getValue().getSignalIds().iterator();
						while(sigGpItr.hasNext()) {
							Id<Signal> TsigId = sigGpItr.next();
							if(sig.getSignalSystemsData().getSignalSystemData().get(sigSystem).getSignalData().get(TsigId).getLaneIds().contains(TEntry.getKey())) {
								sigMap.put(laneNum, TSigEntry.getKey());
								//remove signal assignments if lanes gets restructured
								if(replacesLaneNum>=0) {
									sig.getSignalSystemsData().getSignalSystemData().get(sigSystem).getSignalData().remove(TsigId);
									sigGpItr.remove();
								}
							}
						}
					}
			}
		}
		
		//check sig validity
		if(hasSignal && hasl2l)
			if(toLanesMap.size()!=sigMap.size()) {
				throw new IllegalArgumentException("The number of lanes doesn't match the number of signals");
//				log.error(ogLink.getToNode().getId().toString()+" The number of lanes doesn't match the number of signals, ignoring the signals!");
//				hasSignal = false;
			}
		
		//define bus link toLinks
		List<Id<Link>> busToLinks = new ArrayList<Id<Link>>();
		if(toAllLinksInLanes && hasl2l) {
			for(Lane TLane: toLanesMap.values())
				for(Id<Link> TLinkId: TLane.getToLinkIds())
					if(!busToLinks.contains(TLinkId))
						busToLinks.add(TLinkId);
		}
		if(replacesLaneNum>=0 && hasl2l){
			for(Id<Link> TlinkId : toLanesMap.get(replacesLaneNum).getToLinkIds())
				if(!busToLinks.contains(TlinkId))
					busToLinks.add(TlinkId);
			toLanesMap.remove(replacesLaneNum);
		}
		
		//build the new bus link first
		Id<Link> busLinkId = Id.createLinkId("BUS-"+LinkIdS);
		Link busLink = net.getFactory().createLink(busLinkId, ogLink.getFromNode(), ogLink.getToNode());
		busLink.setAllowedModes(Sets.newHashSet("bus"));
		busLink.setCapacity(CreateNetworkUtils.laneCapacity);
		busLink.setFreespeed(ogLink.getFreespeed());
		assert (ogLink.getLength()-1 > 0);
		busLink.setLength(ogLink.getLength()-1);
		busLink.setNumberOfLanes(1);
		net.addLink(busLink);
		if(!busToLinks.isEmpty()) {
			CreateNetworkUtils.addNextLinksToLane(lanes, busLink, 0, 1, busToLinks.get(0));
			if(busToLinks.size()>1) {	//I hate this imp
				busToLinks.remove(0);
				for(Id<Link> TLink: busToLinks)
					lanes.getLanesToLinkAssignments().get(busLinkId).getLanes().get(Id.create(busLinkId.toString()+"_0", Lane.class)).addToLinkId(TLink);
			}
		}
		
		if(!hasl2l) {
			if(replacesLaneNum>=0)
				JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId(LinkIdS), Math.toIntExact(Math.round(ogLink.getNumberOfLanes()))-1);
			return busLink;
		}
		
		//replace the ogLink
		if(replacesLaneNum>=0) {
			//clean old link defs
			net.removeLink(ogLink.getId());
			lanes.getLanesToLinkAssignments().remove(ogLink.getId());
			//create the link
			Link replacementLink = net.getFactory().createLink(ogLink.getId(), ogLink.getFromNode(), ogLink.getToNode());
			replacementLink.setAllowedModes(ogLink.getAllowedModes());
			replacementLink.setCapacity(CreateNetworkUtils.laneCapacity);
			replacementLink.setFreespeed(ogLink.getFreespeed());
			replacementLink.setLength(ogLink.getLength());
			replacementLink.setNumberOfLanes(numOfLanes);
			net.addLink(replacementLink);
			//create l2l
			int laneNumitr = 0;
			int laneAligNum = -toLanesMap.size()/2;
			LanesToLinkAssignment l2l = lanes.getFactory().createLanesToLinkAssignment(replacementLink.getId());
			//create .ol link
			Lane Tollane = lanes.getFactory().createLane(Id.create(replacementLink.getId().toString()+".ol", Lane.class));
			Tollane.setAlignment(0);
			Tollane.setCapacityVehiclesPerHour(replacementLink.getCapacity());
			Tollane.setNumberOfRepresentedLanes(replacementLink.getNumberOfLanes());
			Tollane.setStartsAtMeterFromLinkEnd(replacementLink.getLength());
			//create the other lanes
			for(Entry<Integer, Lane> laneEntry: toLanesMap.entrySet()) {
				Lane Tlane = lanes.getFactory().createLane(Id.create(replacementLink.getId().toString()+"_"+laneNumitr, Lane.class));
				Tlane.setAlignment(laneAligNum);
				Tlane.setCapacityVehiclesPerHour(laneEntry.getValue().getCapacityVehiclesPerHour());
				Tlane.setNumberOfRepresentedLanes(laneEntry.getValue().getNumberOfRepresentedLanes());
				Tlane.setStartsAtMeterFromLinkEnd(laneEntry.getValue().getStartsAtMeterFromLinkEnd());
				for(Id<Link> Ttolink: laneEntry.getValue().getToLinkIds())
					Tlane.addToLinkId(Ttolink);
				l2l.addLane(Tlane);
				Tollane.addToLaneId(Tlane.getId());
				laneNumitr++;
				laneAligNum++;
				
				if(hasSignal)
					JacobTest.TESTaddLaneIntoSignalAndGroup(sig, Tlane.getId().toString(), Integer.parseInt(sigMap.get(laneEntry.getKey()).toString()));
				
			}
			l2l.addLane(Tollane);
			lanes.addLanesToLinkAssignment(l2l);
			
			//add bus lane sig
			if(hasSignal)
				JacobTest.TESTaddLaneIntoSignalAndGroup(sig, busLinkId.toString()+"_0", Integer.parseInt(sigMap.get(replacesLaneNum).toString()));
		}
		
		return busLink;
	}
	
	public static List<Id<Link>> getNearestLinksExactlyByMath(Network net, Coord cord, double maxDistance, Set<String> AllowedModes){
		return getNearestLinksExactlyByMath(net, cord, maxDistance, AllowedModes, 300, Collections.emptySet());
	}
	
	/**
	 * Gets the links closest to a Coord<br>
	 * Does not modify net to find links, and is safe for use in threads<br>
	 * Finds the distance from Coord to Link, and then check if once coord shifted by d lies on the link<br>
	 * Gets Links to/from nearest node regardless of search distance after found links within search distance<br>
	 * Max Distance increments currently set at 20<p>
	 * Math: Rotates link by 90deg, denote as vector v<br>
	 * vector r, from cord to link from node<br>
	 * vector d, projection r onto v<br>
	 * cord moves by vector d, check if result within bounding box of link
	 * @param net
	 * @param cord
	 * @param maxDistance
	 * @param AllowedModes 
	 * @param maxSpeedAllowed - freespeed of links must be lower than
	 * @return List of Links
	 * @author JLo
	 */
	public static List<Id<Link>> getNearestLinksExactlyByMath(Network net, Coord cord, double maxDistance, Set<String> AllowedModes, double maxSpeedAllowed, Set<Id<Link>> exclusionLinks){
		//Very Important Bits
		final Logger log = Logger.getLogger(JacobTest.class);
		List<String> tunnelsId = BusDataExtractor.tunnelsId;
		
		if (maxDistance > 100) {
			log.warn("The distance searching is too large: " + maxDistance);
		}
		List<Id<Link>> linkIdList = new ArrayList<Id<Link>>();
		
		while(linkIdList.isEmpty()) {			//to ensure getting links
			for(Node TNode:NetworkUtils.getNearestNodes(net, cord, maxDistance))	{
				for(Link TTLink:TNode.getInLinks().values())
					if(!linkIdList.contains(TTLink.getId())
							&& TTLink.getFreespeed()<=maxSpeedAllowed
							&& !tunnelsId.contains(TTLink.getId().toString())
							&& !exclusionLinks.contains(TTLink.getId())
							&& Sets.intersection(TTLink.getAllowedModes(), AllowedModes).size()>0 
							 	)
						linkIdList.add(TTLink.getId());
				for(Link TTLink:TNode.getOutLinks().values())
					if(!linkIdList.contains(TTLink.getId())
							&& TTLink.getFreespeed()<=maxSpeedAllowed
							&& !tunnelsId.contains(TTLink.getId().toString())
							&& !exclusionLinks.contains(TTLink.getId())
							&& Sets.intersection(TTLink.getAllowedModes(), AllowedModes).size()>0 
							 	)
						linkIdList.add(TTLink.getId());
			}
			for(Link TLink:net.getLinks().values()) {
				if(!linkIdList.contains(TLink.getId())
						&& TLink.getFreespeed()<=maxSpeedAllowed
						&&!tunnelsId.contains(TLink.getId().toString())															//check tunnel links
						&& !exclusionLinks.contains(TLink.getId())
						&& Sets.intersection(TLink.getAllowedModes(), AllowedModes).size()>0) {	//check allowed modes	
//					if(TLink==Id.createLinkId("401420_401419"))		//for debug certain links
//						maxDistance=maxDistance;
					//math stuff
					double x1 = TLink.getFromNode().getCoord().getX();
					double y1 = TLink.getFromNode().getCoord().getY();
					double x2 = TLink.getToNode().getCoord().getX();
					double y2 = TLink.getToNode().getCoord().getY();
					double absv = Math.sqrt(Math.pow(x2-x1, 2)+Math.pow(y2-y1, 2));		//the magnitude of vector v
					double unitvdotr = (x2-x1)*(y1-cord.getY())-(x1-cord.getX())*(y2-y1);	//unit vector v dot r
					double d = Math.abs(unitvdotr)/absv;
					double ddir = unitvdotr/Math.abs(unitvdotr)*-1;		//*unitvdotr/Math.abs(unitvdotr) is a terrible solution to +-vector d, but it works
					
					if(d<maxDistance) {
						double x3 = cord.getX()+d*(y2-y1)/absv*ddir;	//shifts cord by vector d
						double y3 = cord.getY()-d*(x2-x1)/absv*ddir;
						if((Math.max(x1,x2)>=x3 && Math.min(x1,x2)<=x3) && (Math.max(y1,y2)>=y3 && Math.min(y1,y2)<=y3)) 	
							//check if new point lies in-between the link, assumes new point is correctly calculated on the link
							linkIdList.add(TLink.getId());
					}
//					if(TLink.getId()==Id.createLinkId("401420_401419"))		//for debug certain links
//						continue;
				}
			}
			maxDistance = maxDistance+20;		//not sure the proper distance
		} 			
		
		//get nearest node, as a safety mechanism		!!!BAD IDEA!!!
//		Node TNode0 = NetworkUtils.getNearestNode(net, cord);
//		for(Id<Link> TTLink:TNode0.getInLinks().keySet())
//			if(!linkIdList.contains(TTLink)
//					&& Sets.intersection(net.getLinks().get(TTLink).getAllowedModes(), AllowedModes).size()>0 && !tunnelsId.contains(TTLink.toString()) )
//				linkIdList.add(TTLink);
//		for(Id<Link> TTLink:TNode0.getOutLinks().keySet())
//			if(!linkIdList.contains(TTLink)
//					&& Sets.intersection(net.getLinks().get(TTLink).getAllowedModes(), AllowedModes).size()>0 && !tunnelsId.contains(TTLink.toString()) )
//				linkIdList.add(TTLink);
		
		return linkIdList;
	}
	
	/**
	 * Adds specified additional green time to a signal group specified<br>
	 * !!!Doesn't adjust the other signals to remove conflicts or add cycle time!!!<br>
	 * 
	 * @param net
	 * @param lanes
	 * @param sig
	 * @param node
	 * @param sigGroup
	 * @param addition
	 */
	public static void increaseSignalDuration(SignalsData sig, String node, int sigGroup, int addition) {
		//get data containers
		SignalPlanData sigPlan = sig.getSignalControlData().getSignalSystemControllerDataBySystemId().get(Id.create(node, SignalSystem.class)).getSignalPlanData().get(Id.create("1", SignalPlan.class));
		SignalGroupSettingsData sigGp = sigPlan.getSignalGroupSettingsDataByGroupId().get(Id.create(sigGroup, SignalGroup.class));
		//get and cal time
		int cycleTime = sigPlan.getCycleTime();
		int orgOff = sigGp.getDropping();
		int newOff = orgOff + addition;
		//set the timing
		sigGp.setDropping(newOff%cycleTime);
	}
	
	/**
	 * Helper method to be lazy
	 * @param sig
	 * @param node
	 * @param sigGroup
	 * @param signalId
	 */
	public static void removeSignalFromGroup(SignalsData sig, String node, int sigGroup, int signalId) {
		sig.getSignalGroupsData().getSignalGroupDataBySystemId(Id.create(node, SignalSystem.class)).get(Id.create(sigGroup, SignalGroup.class)).getSignalIds().remove(Id.create(signalId, Signal.class));
	}
	
	/**
	 * Reduces the number of repeated single lanes to lessen the queues that mobsim has to maintain<p>
	 * 
	 * <b>ONLY Call this function at the last possible moment!<br>
	 * DO NOT Call this function for network DEBUGING!</b></p><p>
	 * 
	 * Checks all the l2l for lanes that have the same toLinkIds,<br>
	 * then check that they are all in the same signal system if the sigSys exists.<br></p><p>
	 * 
	 * For each sets of lanes only the least Id lane will be kept,<br>
	 * and changes CapacityVehiclesPerHour NumberOfRepresentedLanes to correct value,<br>
	 * the others will be removed from l2l and the toLaneIds of the .ol lane,<br>
	 * such that the signals doesn't need to be resequenced.<br></p><p>
	 * 
	 * Gives warning when found l2l without .ol lane, or;<br>
	 * when lanes that have the same toLinkIds are not put in to the same signal groups
	 * 
	 * @param net
	 * @param lanes
	 * @param sig
	 */
	public static void optimiseLanes(Network net, Lanes lanes, SignalsData sig) {
		//stat counters
		int linkModed = 0;
		int laneMergd = 0;
		//to find sig system
		Pattern sigSysPat = Pattern.compile("_a?\\d+_");
		
		for(Entry<Id<Link>, LanesToLinkAssignment> l2lentry: lanes.getLanesToLinkAssignments().entrySet()) {
			
			HashMap<List<Id<Link>>, TreeSet<Id<Lane>>> toLinksIvrtLaneMap = new HashMap<List<Id<Link>>, TreeSet<Id<Lane>>>();
			//find same dir and store tolinklist, sort before putin to compare
			Lane olLane = null;
			for(Entry<Id<Lane>, Lane> lanesEntry: l2lentry.getValue().getLanes().entrySet()) {
				if(lanesEntry.getKey().toString().contains(".ol")) {	//avoiding nullpointer
					olLane = lanesEntry.getValue();
					continue;
				}
				Collections.sort(lanesEntry.getValue().getToLinkIds());
				List<Id<Link>> thisList = lanesEntry.getValue().getToLinkIds();
				if(!toLinksIvrtLaneMap.containsKey(thisList))
					toLinksIvrtLaneMap.put(thisList, new TreeSet<Id<Lane>>());
				toLinksIvrtLaneMap.get(thisList).add(lanesEntry.getKey());
			}
			//iff all have unique toLinkIds List then no need to make changes
			if(toLinksIvrtLaneMap.size() == l2lentry.getValue().getLanes().size()-1)
				continue;
			if(olLane == null) {
				log.warn(l2lentry.getKey().toString()+" l2l not properly defined, does not have a .ol lane!");
				continue;
			}
			
			int thisLaneModed = 0;	//stat
			//iterate the lane sets
			for(TreeSet<Id<Lane>> laneSet: toLinksIvrtLaneMap.values()) {
				//ignore single lane
				if(laneSet.size()<=1)
					continue;
				
				//finding the signal system, FIXME should've done this early on JLo
				Matcher match = sigSysPat.matcher(laneSet.first().toString());
				match.find();
				Id<SignalSystem> sigSys = Id.create(match.group().substring(1, match.group().length()-1), SignalSystem.class);
				
				if(sig.getSignalSystemsData().getSignalSystemData().containsKey(sigSys)) {
					//find the signal Id for each individual lane
					Set<Id<Signal>> sigIds = new TreeSet<Id<Signal>>();
					for(Entry<Id<Signal>, SignalData> sigDataEntry: sig.getSignalSystemsData().getSignalSystemData().get(sigSys).getSignalData().entrySet())
						for(Id<Lane> TLID: sigDataEntry.getValue().getLaneIds())
							if(laneSet.contains(TLID))
								sigIds.add(sigDataEntry.getKey());
					//check if they are all in the same sig group
					boolean sameSigGp = false;
					for(SignalGroupData gpSig: sig.getSignalGroupsData().getSignalGroupDataBySystemId(sigSys).values()) {
						Set<Id<Signal>> gpSigSet = gpSig.getSignalIds();
						if(Collections.disjoint(gpSigSet, sigIds))
							continue;
						else if(gpSigSet.containsAll(sigIds))
							sameSigGp = true;	//can still have another sig group that is not containsAll
						else {	//contains some (has intersect) but not all
							sameSigGp = false;
							log.warn(sigSys.toString()+" signal system has separated signals for lanes to same linkIds!");
							break;
						}
					}
					
					if(!sameSigGp)
						continue;
				}
				
				//mod(scale up) the naturally smallest lane, 
				int newLaneNum = 0;
				SortedMap<Id<Lane>, Lane> l2lLanesMap = l2lentry.getValue().getLanes();
				//figure out the new number of represented lanes
				for(Id<Lane> TL: laneSet)
					newLaneNum += l2lLanesMap.get(TL).getNumberOfRepresentedLanes();
				//isolate the lane that is to be kept and modified
				Lane toKeepLane = l2lLanesMap.get(laneSet.pollFirst());
				toKeepLane.setCapacityVehiclesPerHour(toKeepLane.getCapacityVehiclesPerHour()/toKeepLane.getNumberOfRepresentedLanes()*newLaneNum);
				toKeepLane.setNumberOfRepresentedLanes(newLaneNum);
				//remove all other, then the sig should be fine as lane numbering are not compromised, and no edit required
				for(Id<Lane> TL: laneSet) {
					l2lLanesMap.remove(TL);
					olLane.getToLaneIds().remove(TL);
					//stat
					thisLaneModed++;
				}
			}
			
			//stat
			if(thisLaneModed>0) {
				linkModed++;
				laneMergd+=thisLaneModed;
			}
		}
		
		//DEBUG stat 
		log.info("Links Optimised :"+linkModed);
		log.info("Lanes Merged    :"+laneMergd);
		
	}
}

















