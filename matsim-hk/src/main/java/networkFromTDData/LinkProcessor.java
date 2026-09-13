package networkFromTDData;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public final class LinkProcessor extends DefaultHandler {
	Network network;
	
	private final Map<String, List<Id<Node>>> linkToNodeMap;
	private final List<Id<Link>> linksToIgnore;

	boolean isId = false;
	boolean isElevation = false;
	boolean isName = false;
	boolean isCoord = false;
	StringBuilder coordString;
	boolean isRoadId = false;
	boolean isLength = false;
	boolean isTravelDirection = false;
	boolean isALIAS_ENAME = false;
	
	//For the special treatment of arc by center point.
	boolean isArcByCenterPoint = false; //gml:ArcByCenterPoint
	double arcCenterX = 0;
	double arcCenterY = 0;
	boolean isRadius = false; //gml:radius
	double arcRadius = 0;
	boolean isStartAngle = false; //gml:startAngle
	boolean isEndAngle = false; //gml:endAngle
		
	boolean complexSegements = false; //Indicates a complicated segment type, updated in Nov 2018.
	List<String> coordsForSegments = null;
	boolean isRoundAbout = false; //True if this set of data is roundabout.
	
	String elevation; //Elevation, 1=bridge, 0=on road, -1 = tunnel
	Id<Link> thisLinkId;
	String thisRoadEName; //English name of the road
	double roadLength;
	int travelDirection; //1: Both way; 3:One way
	
	int isolatedLinkcount = 0;
	int bugRoundAboutCount = 0;
	
	public LinkProcessor(Network net, Map<String, List<Id<Node>>> linkToNodeMap, List<Id<Link>> linksToIgnore) {
		this.network = net;
		this.linkToNodeMap = linkToNodeMap;
		this.linksToIgnore = linksToIgnore;
	}
	
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if(qName.equals("fme:CENTRELINE")) {
			//tempLink = null;
		}else if(qName.equals("fme:ROUTE_ID")) {
			isId = true;
		}else if(qName.equals("fme:ELEVATION")) {
			isElevation = true;
		}else if(qName.equals("fme:STREET_ENAME")) {
			isName = true;
		}else if(qName.equals("fme:SHAPE_Length")) {
			isLength = true;
		}else if(qName.equals("fme:ALIAS_ENAME")) {
			isALIAS_ENAME = true;
		}else if(qName.equals("gml:posList")) {
			isCoord = true;
			coordString = new StringBuilder();
		}else if(qName.contains("fme:RD_ID")) {
			isRoadId = true;
		}else if(qName.contains("fme:TRAVEL_DIRECTION")) {
			isTravelDirection = true;
		}else if(qName.contains("gml:segments")) {
			complexSegements = true;
		}else if(qName.contains("gml:ArcByCenterPoint")) {
			isArcByCenterPoint = true;
		}else if(qName.contains("gml:radius")) {
			isRadius = true;
		}else if(qName.contains("gml:startAngle")) {
			isStartAngle = true;
		}else if(qName.contains("gml:endAngle")) {
			isEndAngle = true;
		}
	}
	
	private Id<Link> createReverseLinkId(Id<Link> linkId){
		return Id.createLinkId(linkId.toString()+"R");
	}
	
	/**
	 * This function is creating a link.
	 * @param travelDir
	 * @param fromNode
	 * @param toNode
	 */
	private void createNode(int travelDir, Node fromNode, Node toNode) {
		if(linksToIgnore.contains(thisLinkId)) {
			return; //We don't create the some links.
		}
		if(travelDirection==1 || travelDirection==3) {
			NetworkUtils.createAndAddLink(network, thisLinkId, fromNode, toNode, 
					roadLength, 50/3.6, 1700, 1);
			if(travelDirection==1) {
				NetworkUtils.createAndAddLink(network, createReverseLinkId(thisLinkId), toNode, fromNode,
						roadLength, 50/3.6, 1700, 1);
			}
		}else {
			throw new IllegalArgumentException("The travel direction is not handled!");
		}
	}
	
	private void createNodeAndLinkFromCoords(String[] coords, boolean isRoundAbout) {
		if(travelDirection==4 || linksToIgnore.contains(thisLinkId)) {
			return; //We ignore the links with travel direction 4.
		}
		Coord fromNodeCoord = new Coord(Double.parseDouble(coords[1]) - 800000, Double.parseDouble(coords[0]) - 800000);
		Coord toNodeCoord = new Coord(Double.parseDouble(coords[coords.length-1]) - 800000, 
				Double.parseDouble(coords[coords.length-2]) - 800000);
		
		List<Id<Node>> candidateNode = linkToNodeMap.get(thisLinkId.toString());
		if(candidateNode==null) {
			isolatedLinkcount++;
			return; //We ignore it for now.
			//throw new NullPointerException("");
		}
		if(candidateNode.size()==2) { //Both direction have an intersection.
			Node firstNode = this.network.getNodes().get(candidateNode.get(0));
			Node secondNode = this.network.getNodes().get(candidateNode.get(1));
			
			if(NetworkUtils.getEuclideanDistance(firstNode.getCoord(), fromNodeCoord) < 1 || 
					NetworkUtils.getEuclideanDistance(secondNode.getCoord(), toNodeCoord) < 1) {
				//First node is from node, second node is to node.
				createNode(this.travelDirection, firstNode, secondNode);
			}else if(NetworkUtils.getEuclideanDistance(secondNode.getCoord(), fromNodeCoord) < 1 ||
					NetworkUtils.getEuclideanDistance(firstNode.getCoord(), toNodeCoord) < 1) {
				//First node is to node, second node is first node.
				createNode(this.travelDirection, secondNode, firstNode);
			}else{
				if(isRoundAbout) {
					bugRoundAboutCount++;
				}else {
					throw new IllegalArgumentException("The node arrangement is wrong!");
				}
		}
		}else if(candidateNode.size()==1) {
			Node node = this.network.getNodes().get(candidateNode.get(0));
			if( NetworkUtils.getEuclideanDistance(node.getCoord(), fromNodeCoord) < 1){
				//That node from node
				Node anotherNode = NetworkUtils.createAndAddNode(network, Id.createNodeId(thisLinkId.toString()+"_"), 
						toNodeCoord);
				createNode(this.travelDirection, node, anotherNode);
			}else if( NetworkUtils.getEuclideanDistance(node.getCoord(), toNodeCoord) < 1) {
				//That node is to node
				Node anotherNode = NetworkUtils.createAndAddNode(network, Id.createNodeId(thisLinkId.toString()+"_"), 
						fromNodeCoord);
				createNode(this.travelDirection, anotherNode, node);
			}else {
				if(isRoundAbout) {
					bugRoundAboutCount++;
				}else {
					throw new IllegalArgumentException("The node arrangement is wrong!");
				}
			}
			
		}else if(candidateNode.size()>4){
			Node fromNode = null;
			Node toNode = null;
			for(int i = 0; i<candidateNode.size(); i++) {
				Node node = this.network.getNodes().get(candidateNode.get(0));
				if(NetworkUtils.getEuclideanDistance(node.getCoord(), fromNodeCoord) < 1) {
					fromNode = node;
				}else if(NetworkUtils.getEuclideanDistance(node.getCoord(), fromNodeCoord) < 1){
					toNode = node;
				}
			}
			if(fromNode!=null && toNode!=null) {
				createNode(this.travelDirection, fromNode, toNode);
			}else {
				throw new IllegalArgumentException("The number of candidate is not correct!");
			}
		}
	}

	public void endElement(String uri, String localName, String qName) {
		if(qName.equals("fme:CENTRELINE")) {
			isRoundAbout = false; //Reset the roundabout.
			//network.addLink(tempLink); //Add the link in the end of one reading
		}else if(qName.equals("fme:ROUTE_ID")) {
			isId = false;
		}else if(qName.equals("fme:ELEVATION")) {
			isElevation = false;
		}else if(qName.equals("fme:STREET_ENAME")) {
			isName = false;
		}else if(qName.equals("fme:SHAPE_Length")) {
			isLength = false;
		}else if(qName.equals("gml:posList")) {
			if(!complexSegements) {
				//Create the link here.
				String[] coords = coordString.toString().split(" ");
				createNodeAndLinkFromCoords(coords, isRoundAbout);
			}else {
				if(this.coordsForSegments == null) {
					if(this.isArcByCenterPoint) {
						this.coordsForSegments = new ArrayList<String>(); //Create an empty string.
					}else {
						this.coordsForSegments = Lists.newArrayList( coordString.toString().split(" ") );
					}
				}else {
					if(!this.isArcByCenterPoint) {
						this.coordsForSegments.addAll(ImmutableList.copyOf(coordString.toString().split(" ")));
					}
				}
			}
			isCoord = false;
		}else if(qName.contains("fme:RD_ID")) {
			isRoadId = false;
		}else if(qName.contains("fme:TRAVEL_DIRECTION")) {
			isTravelDirection = false;
		}else if(qName.contains("gml:segments")) {
			complexSegements = false; //Switch off the complex segment again.
			createNodeAndLinkFromCoords(this.coordsForSegments.toArray(new String[0]), isRoundAbout);
			this.coordsForSegments = null;
		}else if(qName.contains("fme:ALIAS_ENAME")) {
			isALIAS_ENAME = false;
		}else if(qName.contains("gml:ArcByCenterPoint")) {
			isArcByCenterPoint = false;
		}else if(qName.contains("gml:radius")) {
			isRadius = false;
		}else if(qName.contains("gml:startAngle")) {
			isStartAngle = false;
		}else if(qName.contains("gml:endAngle")) {
			isEndAngle = false;
		}
	}
	
	//Find character between '>' and '<'
	public void characters(char ch[], int start, int length) throws SAXException{
		if(isId) {
			thisLinkId = Id.createLinkId(new String(ch, start, length));
		}else if(isElevation) {
			elevation = new String(ch, start, length);
			//tempNode.getAttributes().putAttribute("elevation", );
		}else if(isName) {
			thisRoadEName = new String(ch, start, length);
		}else if(isCoord) {
			if(isArcByCenterPoint) {
				String[] XY = new String(ch, start, length).split(" ");
				arcCenterX = Double.parseDouble( XY[1] );
				arcCenterY = Double.parseDouble( XY[0] );
			}else {
				coordString.append(new String(ch, start, length));
			}
		}else if(isLength) {
			this.roadLength = Double.parseDouble(new String(ch, start, length));
		}else if(isTravelDirection) {
			travelDirection = Integer.parseInt(new String(ch, start, length));
		}else if(isALIAS_ENAME) {
			isRoundAbout = new String(ch, start, length).equals("Roundabout");
		}else if(isRadius) {
			arcRadius = Double.parseDouble(new String(ch, start, length)); //Obtain the start angle.
		}else if(isStartAngle || isEndAngle) {
			this.coordsForSegments.add(arcCenterY + arcRadius * 
					Math.sin(Double.parseDouble(new String(ch, start, length)) * Math.PI / 180)+ "");
			this.coordsForSegments.add(arcCenterX + arcRadius * 
					Math.cos(Double.parseDouble(new String(ch, start, length)) * Math.PI / 180)+ "");
		}
	}
}
