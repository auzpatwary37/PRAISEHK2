package createBus;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import networkFromSaturn.CreateNetworkUtils;

public class LinkGraph {
	List<LinkVertex> firstStopLinks; // Stores the possible links of the first stop
	List<LinkVertex> lastStopLinks; // Stores the possible links of the last stop

	List<LinkVertex> links; // Store every actually nodes/vertex.
	Map<LinkVertex, LinkVertex> prev; // The previous vertex for that vertex.
	Map<LinkVertex, Double> dist; // The distance from starting node to the vertex.
	Map<String, Integer> BusStopOccurence; // The number of occurrence of the bus stop

	LinkVertex startLink;
	LinkVertex endLink;

	BusPathCalculator routingAlgo; // The path calculator for the distance
	Network network;

	public LinkGraph(Network network, BusPathCalculator routingAlgo) {
		links = new LinkedList<LinkVertex>();
		this.routingAlgo = routingAlgo;
		this.network = network;
		this.BusStopOccurence = new HashMap<String, Integer>();
	}

	/**
	 * This function automatically test every combination of start and end link, and
	 * aims to get the shortest path
	 */
	public void autoDijkstra() {
		double min = 9999999;
		LinkVertex bestStart = null;
		LinkVertex bestEnd = null;
		for (LinkVertex startLink : firstStopLinks) {
			for (LinkVertex endLink : lastStopLinks) {
				double distance = setDijkstra(startLink, endLink);
				if (distance < min) {
					min = distance;
					bestStart = startLink;
					bestEnd = endLink;
				}
			}
		}
		if (bestStart == null || bestEnd == null) {
			throw new RuntimeException("The shortest dijkstra path not found.");
		}
		// To set the graph direct into correct position again
		this.startLink = bestStart;
		this.endLink = bestEnd;
		setDijkstra(bestStart, bestEnd);
	}

	/**
	 * It would reset the prev and dist, and calculate the shortest path and return
	 * the path length
	 * 
	 * @param startLink
	 * @param endLink
	 * @return
	 */
	public double setDijkstra(LinkVertex startLink, LinkVertex endLink) {
		// Ensure the link is in the first or last link
		if (!firstStopLinks.contains(startLink)) {
			throw new IllegalArgumentException("The start link " + startLink.getLinkId()
					+ " is not in the set of possible links for the first stop");
		} else if (!lastStopLinks.contains(endLink)) {
			throw new IllegalArgumentException(
					"The end link " + endLink.getLinkId() + " is not in the set of possible links for the last stop");
		}

		List<LinkVertex> unvisitedNode = new LinkedList<LinkVertex>();

		// Reset the variables
		this.prev = new HashMap<LinkVertex, LinkVertex>();
		this.dist = new HashMap<LinkVertex, Double>();
		this.startLink = startLink;
		this.endLink = endLink;

		for (LinkVertex link : links) { // Initialization
			prev.put(link, null);
			dist.put(link, Double.MAX_VALUE);
			unvisitedNode.add(link);
		}

		dist.put(startLink, 0.0); // Distance from start link to start link is 0

		while (!unvisitedNode.isEmpty()) {
			LinkVertex workingLink = findLeastDistanceLink(unvisitedNode);

			// The algorithm ends when the end link is the shortest possible.
			if (workingLink == endLink) {
				break;
			}
			unvisitedNode.remove(workingLink); // Remove the link

			for (Edge edge : workingLink.getEdges()) {
				LinkVertex toLink = edge.getToLink();
				if (toLink.busStopId == workingLink.busStopId) {
					throw new RuntimeException("The two stops has the same stop ID!");
				}
				double distanceViaThisLink = dist.get(workingLink) + edge.getDistance();
				if (distanceViaThisLink < dist.get(toLink)) { // Update if it is on the (currently) shortest path.
					prev.put(toLink, workingLink);
					dist.put(toLink, distanceViaThisLink);
				}
			}
		}

		return dist.get(endLink);
	}

	private LinkVertex findLeastDistanceLink(List<LinkVertex> candidates) {
		double minDistance = Double.POSITIVE_INFINITY;
		LinkVertex rtnLink = null;
		for (LinkVertex link : candidates) {
			if (dist.get(link) < minDistance) {
				minDistance = dist.get(link);
				rtnLink = link;
			}
		}
		if (rtnLink == null) {
			throw new RuntimeException("No link is suitable!");
		}
		return rtnLink;
	}

	/**
	 * This is the function to calculate the distance between two bus stops.
	 * 
	 * @param fromStop
	 * @param fromLink
	 * @param toStop
	 * @param toLink
	 * @return
	 */
	private double disutilityBetweenBusStopsOfLink(BusStop fromStop, Link fromLink, BusStop toStop, Link toLink) {
		double flyingFactor = 20;

		double pathCost = 0;
		//double pathTimeCost = 0;
		double distanceFromStopToLink = flyingFactor * (CreateNetworkUtils.getDistance(fromLink, fromStop.getCoord())
				+ CreateNetworkUtils.getDistance(toLink, toStop.getCoord()));

		if (fromLink.equals(toLink)) {
			pathCost = 0;
		} else if (fromLink.getFromNode().equals(toLink.getToNode())
				&& fromLink.getToNode().equals(toLink.getFromNode())) {
			pathCost = Math.max(fromLink.getLength(), // For U-turn, we get the total length of the link
					routingAlgo.calcLeastCostPath(fromLink, toLink, 0, null, null).travelCost); 
		//	pathTimeCost = fromLink.getLength() / fromLink.getFreespeed();
		} else {
			pathCost = routingAlgo.calcLeastCostPath(fromLink, toLink, 0, null, null).travelCost;
		//	pathTimeCost = routingAlgo.calcLeastCostPath(fromLink, toLink, 0, null, null).travelTime;
		}

		return pathCost + distanceFromStopToLink;
	}

	/*
	 * private double distanceFromNodeToCoord(Node node, Coord coord){ Coord coord2
	 * = node.getCoord(); return
	 * Math.sqrt(Math.pow(coord2.getX()-coord.getX(),2)+Math.pow(coord2.getY()-coord
	 * .getY(),2)); }
	 */

	/**
	 * Given two bus stop, put it into this graph and connect every link of prevStop
	 * to every link of currStop
	 * 
	 * @param prevStop
	 *            The previous bus stop
	 * @param currStop
	 *            The current bus stop
	 */
	public void addBusStop(BusStop prevStop, BusStop currStop) {
		List<Id<Link>> currLinks = currStop.getPossibleLinks();
		List<LinkVertex> currLinkVertices = new LinkedList<LinkVertex>();
		String currStopId = currStop.getStopId();
		int occurence = 1;
		if (BusStopOccurence.containsKey(currStopId)) {
			occurence += BusStopOccurence.get(currStopId);
		}
		BusStopOccurence.put(currStopId, occurence);
		// Create the vertices for current stop
		for (Id<Link> toLinkId : currLinks) {
			LinkVertex toLinkVertex = new LinkVertex(toLinkId, currStop.getStopId(), occurence);
			links.add(toLinkVertex);
			currLinkVertices.add(toLinkVertex);
		}

		// For the first bus stop, save it and return.
		if (prevStop == null) {
			firstStopLinks = currLinkVertices;
			return;
		}

		// Modify all previous links
		for (Id<Link> prevLinkId : prevStop.getPossibleLinks()) {
			// Obtain the link vertex that created.
			LinkVertex prevLV = new LinkVertex(prevLinkId, prevStop.getStopId(),
					BusStopOccurence.get(prevStop.getStopId()));
			int index = links.indexOf(prevLV);
			prevLV = links.get(index);

			// Add edge for each vertex.
			for (LinkVertex toLinkVertex : currLinkVertices) {
				double distance = disutilityBetweenBusStopsOfLink(prevStop, network.getLinks().get(prevLinkId),
						currStop, network.getLinks().get(toLinkVertex.getLinkId()));
				Edge edge = new Edge(prevLV, toLinkVertex, distance);
				prevLV.addEdge(edge);
			}
		}

		lastStopLinks = currLinkVertices; // If it is the last stop
	}

	/**
	 * @return A map mapping bus stop ID with the link.
	 */
	public LinkedHashMap<String, Id<Link>> getBusStopLinkMap() {
		LinkedHashMap<String, Id<Link>> busStopLinkMap = new LinkedHashMap<String, Id<Link>>();
		iteratePrev(busStopLinkMap, endLink);
		return busStopLinkMap;
	}

	private void iteratePrev(LinkedHashMap<String, Id<Link>> busStopLinkMap, LinkVertex curr) {
		if (!curr.equals(startLink)) {
			iteratePrev(busStopLinkMap, prev.get(curr));
		}
		busStopLinkMap.put(curr.getBusStopId(), curr.getLinkId());
	}

	/**
	 * A class for the edge, which is edge linking two link
	 * 
	 * @author eleead
	 *
	 */
	private class Edge {
		LinkVertex fromLink;
		LinkVertex toLink;
		double distance;

		public Edge(LinkVertex fromLink, LinkVertex toLink, double distance) {
			this.fromLink = fromLink;
			this.toLink = toLink;
			this.distance = distance;
		}

		public LinkVertex getFromLink() {
			return fromLink;
		}

		public LinkVertex getToLink() {
			return toLink;
		}

		public double getDistance() {
			return distance;
		}
	}

	/**
	 * A class for the vertex, which is link
	 * 
	 * @author eleead
	 *
	 */
	private class LinkVertex {
		Id<Link> linkId;
		String busStopId;
		List<Edge> edgeFromLink;
		int occurance; // The number of time this bus stop (ID) appeared for the route

		public LinkVertex(Id<Link> linkId, String busStopId, int occurance) {
			this.linkId = linkId;
			this.busStopId = busStopId;
			this.edgeFromLink = new LinkedList<Edge>();
			this.occurance = occurance;
		}

		public void addEdge(Edge edge) {
			edgeFromLink.add(edge);
		}

		public Id<Link> getLinkId() {
			return linkId;
		}

		public String getBusStopId() {
			return busStopId;
		}

		public List<Edge> getEdges() {
			return edgeFromLink;
		}

		public double getOccurance() {
			return this.occurance;
		}

		@Override
		public boolean equals(Object o) {
			LinkVertex other = (LinkVertex) o;
			if (other.getLinkId().equals(linkId) && other.getBusStopId().equals(busStopId)
					&& other.getOccurance() == occurance) {
				return true;
			}
			return false;
		}
	}
}
