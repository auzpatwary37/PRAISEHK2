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

public final class IntersectionProcessor extends DefaultHandler {
	Network network;
	Node tempNode;
	
	Map<String, List<Id<Node>>> linkToNode;

	boolean isId = false;
	boolean isType = false;
	boolean isName = false;
	boolean isCoord = false;
	boolean isRoadId = false;
	
	public IntersectionProcessor(Network net) {
		this.network = net;
		this.linkToNode = new HashMap<>();
	}
	
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if(qName.equals("fme:INTERSECTION")) {
			tempNode = null;
		}else if(qName.equals("fme:INT_ID")) {
			isId = true;
		}else if(qName.equals("fme:INT_TYPE")) {
			isType = true;
		}else if(qName.equals("fme:INT_ENAME")) {
			isName = true;
		}else if(qName.equals("gml:pos")) {
			isCoord = true;
		}else if(qName.contains("fme:RD_ID")) {
			isRoadId = true;
		}
	}

	public void endElement(String uri, String localName, String qName) {
		if(qName.equals("fme:INTERSECTION")) {
			network.addNode(tempNode);
		}else if(qName.equals("fme:INT_ID")) {
			isId = false;
		}else if(qName.equals("fme:INT_TYPE")) {
			isType = false;
		}else if(qName.equals("fme:INT_ENAME")) {
			isName = false;
		}else if(qName.equals("gml:pos")) {
			isCoord = false;
		}else if(qName.contains("fme:RD_ID")) {
			isRoadId = false;
		}
	}
	
	//Find character between '>' and '<'
	public void characters(char ch[], int start, int length) throws SAXException{
		if(isId) {
			tempNode = NetworkUtils.createNode(Id.createNodeId(new String(ch, start, length)));
		}else if(isType) {
			tempNode.getAttributes().putAttribute("type", new String(ch, start, length));
		}else if(isName) {
			tempNode.getAttributes().putAttribute("name", new String(ch, start, length));
		}else if(isCoord) {
			String coordString = new String(ch, start, length);
			Coord nodeCoord = new Coord(Double.parseDouble(coordString.split(" ")[0]) - 800000, Double.parseDouble(coordString.split(" ")[1]) - 800000);
			tempNode.setCoord(nodeCoord);
		}else if(isRoadId) {
			String roadId = new String(ch, start, length);
			List<Id<Node>> linkToNodeList = null;
			if(linkToNode.containsKey(roadId)) {
				linkToNodeList = linkToNode.get(roadId);
			}else {
				linkToNodeList = new ArrayList<Id<Node>>();
			}
			linkToNodeList.add(tempNode.getId());
			linkToNode.put(roadId, linkToNodeList);
		}
	}
	
	public Map<String, List<Id<Node>>> getLinkToNode() {
		return linkToNode;
	}
}
