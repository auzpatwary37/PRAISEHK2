/**
 * 
 */
package createLightrail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import networkFromSaturn.WGS84toSaturn;

/**
 * @author JLo
 *
 */
class LRstationLink {
	private String ID;		//format LRXXX
	private Node node0;		//format LRXXX_0
	private Node node1;		//format LRXXX_1
	private int usage0 = 0;		//use to delete un-used links later
	private int usage1 = 0;
	private Id<Link> link0Id;		//format LRXXX_0-LRXXX_1
	private Id<Link> link1Id;		//format LRXXX_1-LRXXX_0
	private TransitStopFacility tsf0;
	private TransitStopFacility tsf1;
	private Coord stopCoord;
	private static HashMap<String, String> stationlinkbind = new HashMap<String, String>();
	private static List<String> nonblockingstations = Arrays.asList("LR001", "LR920", "LR275", "LR280", "LR140", "LR100", "LR430", "LR550", "LR600");
	
	public String getId() {
		return this.ID;
	}
	
	public Node getNode(int endNumber) {
		if(endNumber==0)
			return this.node0;
		else
			return this.node1;
	}
	
	public Id<Link> getLinkIdfromPrevNode(Node node){
		if(node == this.node0)
			return this.link1Id;
		else if(node == this.node1)
			return this.link0Id;
		else
			throw new IllegalArgumentException("Input node not part of this station!!! (or I messed up)");
	}
	
	public Id<Link> getLinkIdfromCurrNode(Node node){
		if(node == this.node0)
			return this.link0Id;
		else if(node == this.node1)
			return this.link1Id;
		else
			throw new IllegalArgumentException("Input node not part of this station!!! (or I messed up)");
	}
	
	public Coord getCoord() {
		return this.stopCoord;
	}
	
	public TransitStopFacility getTSFfromPrevNode(Node node) {
		if(node == this.node0)
			return this.tsf1;
		else if(node == this.node1)
			return this.tsf0;
		else
			throw new IllegalArgumentException("Input node not part of this station!!! (or I messed up)");
	}
	
	public TransitStopFacility getTSFfromCurrNode(Node node) {
		if(node == this.node0)
			return this.tsf0;
		else if(node == this.node1)
			return this.tsf1;
		else
			throw new IllegalArgumentException("Input node not part of this station!!! (or I messed up)");
	}
	
	public void addUsageFromLinkId(Id<Link> linkid) {
		//by this time I felt like I should and could rewrite the direction recognition a bit better, but this stuff is here already, JLo
		if(linkid == this.link0Id)
			this.usage0++;
		else if(linkid == this.link1Id)
			this.usage1++;
	}
	
	
	//builds the station
	public LRstationLink(LRstationData LRstation, Network net, TransitSchedule ts){
		this.ID = "LR"+LRstation.getref();
		
		//make coord and find nearest link's slope as station slope
		CoordinateTransformation ct = new WGS84toSaturn();
		this.stopCoord = ct.transform(new Coord(LRstation.getlon(),LRstation.getlat()));
		Link nearestLink = NetworkUtils.getNearestLink(net, stopCoord);
		//over-ride link selection
		if(stationlinkbind.containsKey(this.ID))
			if(net.getLinks().containsKey(Id.createLinkId(stationlinkbind.get(this.ID))))	//check link exists
				nearestLink = net.getLinks().get(Id.createLinkId(stationlinkbind.get(this.ID)));
		
		//get the link angle
		double theta = Math.atan((nearestLink.getToNode().getCoord().getY()-nearestLink.getFromNode().getCoord().getY())/
								(nearestLink.getToNode().getCoord().getX()-nearestLink.getFromNode().getCoord().getX()));
		//create the nodes based on the link angle
		this.node0 = NetworkUtils.createAndAddNode(net, Id.createNodeId(ID+"_0"),
				new Coord(stopCoord.getX() - Math.cos(theta) * createLRnetwork.stationLength / 2,
						stopCoord.getY() - Math.sin(theta) * createLRnetwork.stationLength / 2));
		this.node1 = NetworkUtils.createAndAddNode(net, Id.createNodeId(ID+"_1"),
				new Coord(stopCoord.getX() + Math.cos(theta) * createLRnetwork.stationLength / 2,
						stopCoord.getY() + Math.sin(theta) * createLRnetwork.stationLength / 2));
		//create the station links
		this.link0Id = createLRnetwork.createLRlink(net, node0, node1).getId();
		this.link1Id = createLRnetwork.createLRlink(net, node1, node0).getId();
		
		//create the tansitstopfacility
		//extract non-blocking stations
		boolean isblocking = true;
		if(nonblockingstations.contains(this.ID))
			isblocking = false;
		this.tsf0 = ts.getFactory().createTransitStopFacility(Id.create(this.ID+"_0", TransitStopFacility.class), this.stopCoord, isblocking);
		tsf0.setLinkId(link0Id);
		tsf0.setName(LRstation.getName());
		ts.addStopFacility(tsf0);
		this.tsf1 = ts.getFactory().createTransitStopFacility(Id.create(this.ID+"_1", TransitStopFacility.class), this.stopCoord, isblocking);
		tsf1.setLinkId(link1Id);
		tsf1.setName(LRstation.getName());
		ts.addStopFacility(tsf1);
	}
	
	public static void initstationlinkbind() {
		//since only using the links for angle, they might be miles apart
		//to fix the angle of the stations for a better fit
		stationlinkbind.put("LR020", "875011_879826");
		stationlinkbind.put("LR050", "875104_875108");
		stationlinkbind.put("LR190", "879828_875201");
		stationlinkbind.put("LR070", "875239_876494");
		stationlinkbind.put("LR230", "875214_875219");
		stationlinkbind.put("LR220", "875213_875214");
		stationlinkbind.put("LR212", "875238_875204");
		stationlinkbind.put("LR330", "875318_875319");
		stationlinkbind.put("LR130", "875304_877035");
		stationlinkbind.put("LR340", "875318_875337");
		stationlinkbind.put("LR448", "871416_874019");
		stationlinkbind.put("LR468", "11883_871426");
		stationlinkbind.put("LR510", "874000_874510");
		stationlinkbind.put("LR520", "874510_874512");
	}
	
	public Node getOtherNode(Node node) {
		if(node == this.node0)
			return this.node1;
		else if(node == this.node1)
			return this.node0;
		else
			throw new IllegalArgumentException("Input node not part of this station!!! (or I messed up)");
	}
	
	public void cleanup(Network net, TransitSchedule ts) {
		if(usage0==0) {
			net.removeLink(link0Id);
			ts.removeStopFacility(tsf0);
		}
		if(usage1==0) {
			net.removeLink(link1Id);
			ts.removeStopFacility(tsf1);
		}
	}
	
	
	
}
