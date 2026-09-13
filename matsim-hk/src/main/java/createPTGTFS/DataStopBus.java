/**
 * 
 */
package createPTGTFS;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.common.collect.Sets;

import networkFromSaturn.JacobTest;

/**
 * For use of building buses and mini-buses
 * @author JLo
 *
 */
public class DataStopBus extends DataStopPT{
	private ConcurrentHashMap<Id<Link>, Integer> possibleLinks;
	private TransitStopFacility terTSF;
	private Id<Link> terLinkId;
	private boolean isTerminus;
	private static final String BTtype = "BT";

	public DataStopBus(String stop_id, String stop_name, double stop_lat, double stop_lon) {
		super(stop_id, stop_name, stop_lat, stop_lon);
		
		//Override the stop naming
		int endindex = stop_name.length();
		int startindex = stop_name.indexOf("]");
		if(stop_name.indexOf("|")>0)
			endindex = stop_name.indexOf("|");
		if(startindex>0 && startindex<endindex)
			this.stopName = stop_name.substring(startindex+2, endindex);
		else
			this.stopName = stop_name.substring(0, endindex);
		
		this.possibleLinks = new ConcurrentHashMap<Id<Link>, Integer>();
		this.isTerminus = false;
		this.setType(Types.bus);
	}
	
	public Set<Id<Link>> getPossibleLinks() {
		return this.possibleLinks.keySet();
	}
	
	public TransitStopFacility getTerminusTransitStopFacility() {
		return this.terTSF;
	}
	
	public boolean isTerminus() {
		return this.isTerminus;
	}
	
	public Id<Link> getTerminusLinkId(){
		return this.terLinkId;
	}
	
	public boolean addCount(Id<Link> linkId) {
		return this.possibleLinks.replace(linkId, Integer.valueOf(this.possibleLinks.get(linkId).intValue()+1)) != null;
	}
	
	public synchronized void setTerminus() {
		if(!this.isTerminus)
			this.isTerminus = true;
	}
	
	/**
	 * Assign closest links
	 * @param closestLinkList
	 */
	public void addClosestLinks(List<Id<Link>> closestLinkList) {
		for(Id<Link> TL: closestLinkList)
			this.possibleLinks.put(TL, 0);
	}
	
	/**
	 * Uses findClosestLinksExactlyByMath to get closest links
	 * @param net
	 */
	public void findClosestLinks(Network net, double maxDistance) {
		addClosestLinks(JacobTest.getNearestLinksExactlyByMath(net, this.coord, maxDistance, Sets.newHashSet(TransportMode.car, "bus")));
	}
	
	public TransitStopFacility makeTransitStopFacility(Scenario scenario) {
		//extract counts to set linkId in superclass
		int max = 0;
		Id<Link> maxLink = null;
		for(Entry<Id<Link>, Integer> possibleLinksEntry: this.possibleLinks.entrySet()) {
			if(possibleLinksEntry.getValue()>max) {
				max = possibleLinksEntry.getValue();
				maxLink = possibleLinksEntry.getKey();
			}
		}
		this.setTransitStopFacility(scenario.getTransitSchedule(), maxLink, false);
		
		if(this.isTerminus)
			this.createNodeAndLinkForTerminus(scenario.getLanes(), scenario.getNetwork(), scenario.getTransitSchedule());
		
		return this.tsf;
	}
	
	/**
	 * Creates a node and a link for the bus terminus<br>
	 * creates a bus terminus TransitStopFacility<br>
	 * puts the bus terminus TSF at the first link going into the TSF new node<br>
	 * <p>
	 * For <b>HKIOnly</b>, tunnel stops will be made at the respective CHT Link
	 * </p><p>
	 * <i>Note: slightly fragmented the methods, due to porting from old stuff</i>
	 * 
	 * @param lanes
	 * @param net
	 * @param busStop
	 * @return False indicates nodes or links is not created.
	 */
	private boolean createNodeAndLinkForTerminus(Lanes lanes, Network net, TransitSchedule ts) {
		//For the HKI scenario, create the BT on tunnel instead.
		if (Arrays.asList("EHC", "WHC", "CHT").contains(this.linkId.toString())) {
			this.terTSF = this.tsf;
			this.terLinkId = this.linkId;
			return false;
		}
		
		//try new thing where if link selected is bus only link then just make the BT on there
//		Link linkFound = net.getLinks().get(this.linkId); //Use the link found
//		if(linkFound.getAllowedModes().size()==1 && linkFound.getAllowedModes().contains("bus")) {
//			this.terLinkId = this.linkId;
//			this.terTSF = ts.getFactory().createTransitStopFacility(Id.create(BusStop.BTtype+"_"+stopId, TransitStopFacility.class), this.coord, false);
//			this.terTSF.setName(this.stopName);
//			this.terTSF.setLinkId(terLinkId);
//			ts.addStopFacility(this.terTSF);
//		}
		
		//Create the node at the terminus end
		Node newNode = NetworkUtils.createAndAddNode(net, Id.createNodeId("BUS"+this.stopId), this.coord);
		
		//determine which node to make link to
		Link linkFound = net.getLinks().get(this.linkId); //Use the link found
		double distanceFromFrom = NetworkUtils.getEuclideanDistance(this.coord, linkFound.getFromNode().getCoord());
		double distanceFromTo = NetworkUtils.getEuclideanDistance(this.coord, linkFound.getToNode().getCoord());
		Node fromNode = distanceFromFrom <= distanceFromTo?
				linkFound.getFromNode() : linkFound.getToNode(); // Set the closest node to be the node to be connected
		
		//Make first link
		Link newLink1 = NetworkUtils.createLink(
				Id.createLinkId(fromNode.getId().toString()+"_"+this.stopId), fromNode, newNode, net,
				Math.max(1, Math.min(distanceFromFrom, distanceFromTo)), 40/3.6, 2000, 1);
		newLink1.setAllowedModes(Sets.newHashSet("bus"));
		net.addLink(newLink1);

		// Add the to link to the lane
		for (Link l : fromNode.getInLinks().values()) { // Every in bound link
			LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(l.getId());
			if (l2l != null)
				for (Lane lane : l2l.getLanes().values())
					if (lane.getToLinkIds()!=null)
						lane.addToLinkId(newLink1.getId());
		}

		// Add a return link.
		Link newLink2 = NetworkUtils.createLink(
				Id.createLinkId(this.stopId+"_"+fromNode.getId().toString()), newNode, fromNode, net,
				Math.max(1, Math.min(distanceFromFrom, distanceFromTo)), 40/3.6, 2000, 1);
		newLink2.setAllowedModes(Sets.newHashSet("bus"));
		net.addLink(newLink2);
		
		//set the bus stop on the new link
		this.terLinkId = newLink1.getId();
		this.terTSF = ts.getFactory().createTransitStopFacility(Id.create(DataStopBus.BTtype+"_"+stopId, TransitStopFacility.class), this.coord, false);
		this.terTSF.setName(this.stopName);
		this.terTSF.setLinkId(terLinkId);
		ts.addStopFacility(this.terTSF);
		
		//wanted to change to make this BT same as normal stop
//		this.terLinkId = newLink1.getId();
//		this.tsf.setLinkId(this.terLinkId);
//		this.terTSF = this.tsf;
//		this.linkId = this.terLinkId;
		return true;
	}
	
	
}
