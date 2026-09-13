package networkFromTDData;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;

import networkFromSaturn.CreateNetworkUtils;

public class MatchingUtils {
	private static void matchNodes(Node saturnNode, Node ctsNode) {
		saturnNode.setCoord(ctsNode.getCoord());
		saturnNode.getAttributes().putAttribute("matched", ctsNode.getId().toString());
		ctsNode.getAttributes().putAttribute("matched", saturnNode.getId().toString());
	}
	
	private static boolean isEndNode(Node node) {
		if(node.getInLinks().size()>1 || node.getOutLinks().size()>1) {
			return false;
		}else if( (node.getInLinks().size()==0 && node.getOutLinks().size()==1) ||
				(node.getInLinks().size()==1 && node.getOutLinks().size()==0) ){
			return true;
		}else { //Check The case of passing node
			Node firstNode = ( (Link) node.getInLinks().values().toArray()[0]).getFromNode();
			Node secondNode = ( (Link) node.getOutLinks().values().toArray()[0]).getToNode();
			if(firstNode.equals(secondNode)) {
				return true;
			}else {
				return false;
			}
		}
	}
	
	/**
	 * Get the number of node not in the end, connected to the node.
	 * @param node
	 * @return
	 */
	private static int numNodeConnectedConsidersAngle(Node TDnode, boolean countAtEnd, boolean considersAngle) {
		Set<Node> nodeSet = new HashSet<>();
		for (Link inLink: TDnode.getInLinks().values()) {
			Node fromNode = inLink.getFromNode();
			if(countAtEnd || !isEndNode(fromNode) ) {
				nodeSet.add( fromNode );
			}
		}
		for (Link outLink: TDnode.getOutLinks().values()) {
			Node toNode = outLink.getToNode();
			if(countAtEnd || !isEndNode(toNode) ) {
				nodeSet.add( toNode );
			}
		}
		
		if(!considersAngle) {
			return nodeSet.size();
		}
		
		Set<Id<Node>> nodeIdSet = new HashSet<Id<Node>>();
		List<Coord> coords = new ArrayList<Coord>();
		double TDNodeX = TDnode.getCoord().getX();
		double TDNodeY = TDnode.getCoord().getY();
		for(Node node: nodeSet) {
			Coord thisCoord = node.getCoord();
			//Calculate the angle one by one
			boolean closeAngle = false;
			for(Coord coord: coords) {
				double dot_prod = (coord.getX()-TDNodeX) * (thisCoord.getX()-TDNodeX) + (coord.getY()-TDNodeY) * (thisCoord.getY()-TDNodeY);
				double this_vector_magn = Math.sqrt( Math.pow(thisCoord.getX()-TDNodeX, 2)+Math.pow(thisCoord.getY()-TDNodeY, 2));
				double check_vector_magn = Math.sqrt( Math.pow(coord.getX()-TDNodeX, 2)+Math.pow(coord.getY()-TDNodeY, 2));
				
				double angle = Math.acos(dot_prod / (this_vector_magn * check_vector_magn));
				
				if(angle <= Math.PI/6) {
					closeAngle = true;
				}
			}
			if(!closeAngle) {
				coords.add(thisCoord);
				nodeIdSet.add(node.getId());
			}
		}
		
		return nodeIdSet.size();
	}
	
	public static void matchNodes(Network ctsNet, Network saturnNet, Lanes lanes) throws IOException {
		int count = 0;
		Iterator<?> nodeIter = ctsNet.getNodes().entrySet().iterator();
		List<Id<Node>> nodeToDelete = new ArrayList<>();
		while(nodeIter.hasNext()) {
			Entry<Id<Node>, Node> nodeEntry = (Entry<Id<Node>, Node>) nodeIter.next();
			Node node = nodeEntry.getValue();
//			if(node.getId().toString().equals("115769_")) {
//				System.out.println("");
//			}
			if(isEndNode(node)) {
				boolean tooShort = false;
				if(node.getInLinks().size()==1) {
					Link removeLink = (Link) node.getInLinks().values().toArray()[0];
					if(removeLink.getLength() < 25.5) {
						ctsNet.removeLink(removeLink.getId());
						tooShort = true;
					}
				}
				if(node.getOutLinks().size()==1) {
					Link removeLink = (Link) node.getOutLinks().values().toArray()[0];
					if(removeLink.getLength() < 25.5) {
						ctsNet.removeLink(removeLink.getId());
						tooShort = true;
					}
				}
				if(tooShort) {
					nodeToDelete.add(node.getId());
				}
			}
		}
		for(Id<Node> nodeId: nodeToDelete) {
			ctsNet.removeNode(nodeId);
		}
		
		BufferedWriter writer = new BufferedWriter(new FileWriter("output/topoNode.txt"));
		
		for(Node node: saturnNet.getNodes().values()) {			
			Coord nodeCoord = node.getCoord();
			List<Node> ctsClosestNodes;// = (List<Node>) NetworkUtils.getNearestNodes(ctsNet, nodeCoord, 11);
			for(int i = 0; i < 3; i++) {
				ctsClosestNodes = (List<Node>) NetworkUtils.getNearestNodes(ctsNet, nodeCoord, 11 + 5 * i);
				if(ctsClosestNodes.size()==1) { //If there are only one, match by coordinate
					Node ctsNode = ctsClosestNodes.get(0);
					if( numNodeConnectedConsidersAngle(node, false, false) > 2 ) {
						if( numNodeConnectedConsidersAngle(ctsNode, false, false)<=2) {
							writer.write(node.getId().toString() + " " + ctsNode.getId().toString() + " not match. \n");
							continue; //Avoid network topology error.
						}
					}
					
					if(node.getAttributes().getAttribute("matched")==null && 
							ctsNode.getAttributes().getAttribute("matched")==null) {
						matchNodes(node, ctsNode);
						count++;
						break;
					}
				}else if (ctsClosestNodes.size() > 1) {
					Node candidateCtsNode = null; //A candidate for match
					//Try to match by topology by ignoring passing node (2 in 2 out)
					for (Node ctsNode: ctsClosestNodes) {
						if( numNodeConnectedConsidersAngle(node, true, false) > 2 &&
								(numNodeConnectedConsidersAngle(ctsNode, true, true)>2)) {
							if(candidateCtsNode == null) {
								candidateCtsNode = ctsNode;
							}else {
								candidateCtsNode = null;
								break;
							}
						}
					}
					if(candidateCtsNode!=null) {
						matchNodes(node, candidateCtsNode);
						count++;
						break;
					}
					
				}
			}
		}
		System.out.println("Adjusted "+count+" node coordinates.");
		writer.close();
		
		//Map the link, the simplest
		int linkMatchCount = 0;
		for(Link link: saturnNet.getLinks().values()) {
			Node fromNode = link.getFromNode();
			Node toNode = link.getToNode();
//			if(link.getId().toString().contains("101056_101055")){
//				System.currentTimeMillis();
//			}
			
			String ctsFromNodeId = (String) fromNode.getAttributes().getAttribute("matched");
			String ctsToNodeId = (String) toNode.getAttributes().getAttribute("matched");
			if( ctsFromNodeId!=null && ctsToNodeId!= null) {
				//Immediate next to.
				Node ctsNode = ctsNet.getNodes().get(Id.createNodeId(ctsFromNodeId));
				for(Link ctsLink: ctsNode.getInLinks().values()) {
					if(ctsLink.getFromNode().getId().toString().equals(ctsToNodeId) && 
							!ctsLink.getId().toString().contains("R")) {
						ctsLink.getAttributes().putAttribute("matched", link.getId().toString());
						link.getAttributes().putAttribute("matched", ctsLink.getId().toString());
						link.setLength(ctsLink.getLength());
						LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().
								get(link.getId());
						if(l2l != null && l2l.getLanes().size()!=1) {
							for(Lane lane : l2l.getLanes().values()) {
								if(lane.getId().toString().contains(".ol")) {
									lane.setStartsAtMeterFromLinkEnd(ctsLink.getLength());
								}else {
									lane.setStartsAtMeterFromLinkEnd(CreateNetworkUtils.
											obtainLaneLength(ctsLink.getLength()));
								}
							}
						}else if(l2l!=null && l2l.getLanes().size()==1) {
							for(Lane lane : l2l.getLanes().values()) {
								lane.setStartsAtMeterFromLinkEnd(ctsLink.getLength());
							}
						}
						linkMatchCount++;
						
					}
				}
				for(Link ctsLink: ctsNode.getOutLinks().values()) {
					if(ctsLink.getToNode().getId().toString().equals(ctsToNodeId) && 
							!ctsLink.getId().toString().contains("R")) {
						ctsLink.getAttributes().putAttribute("matched", link.getId().toString());
						link.getAttributes().putAttribute("matched", ctsLink.getId().toString());
						link.setLength(ctsLink.getLength());
						LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().
								get(link.getId());
						if(l2l != null) {
							for(Lane lane : l2l.getLanes().values()) {
								if(lane.getId().toString().contains(".ol")) {
									lane.setStartsAtMeterFromLinkEnd(ctsLink.getLength());
								}else {
									lane.setStartsAtMeterFromLinkEnd(CreateNetworkUtils.
											obtainLaneLength(ctsLink.getLength()));
								}
							}
						}
						linkMatchCount++;
					}
				}
			}
		}
		System.out.println("Adjusted "+linkMatchCount+" link profiles.");
	}
}
