package dynamicTransitRouter;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;

/**
 * It is a TransitRouterNetwork like in EBPTR
 * @author eleead
 *
 */
public final class TransitRouterNetworkHR implements Network {

	private final static Logger log = Logger.getLogger(TransitRouterNetworkHR.class);

	private final Map<Id<Link>, TransitRouterNetworkLink> links = new LinkedHashMap<Id<Link>, TransitRouterNetworkLink>();
	private final Map<Id<Node>, TransitRouterNetworkNode> nodes = new LinkedHashMap<Id<Node>, TransitRouterNetworkNode>();
	protected QuadTree<TransitRouterNetworkNode> qtNodes = null;

	private long nextNodeId = 0;
	protected long nextLinkId = 0;

	public static final class TransitRouterNetworkNode implements Node {
		public final TransitRouteStop stop;
		public final TransitStop tStop;
		public final TransitRoute route;
		public final TransitLine line;
		public final Coord coord;
		final Id<Node> id;
		final Map<Id<Link>, TransitRouterNetworkLink> ingoingLinks = new LinkedHashMap<Id<Link>, TransitRouterNetworkLink>();
		final Map<Id<Link>, TransitRouterNetworkLink> outgoingLinks = new LinkedHashMap<Id<Link>, TransitRouterNetworkLink>();

		public TransitRouterNetworkNode(final Id<Node> id, final TransitRouteStop stop, final TransitStop tStop,
				final TransitRoute route, final TransitLine line) {
			this.id = id;
			this.stop = stop;
			this.route = route;
			this.line = line;
			this.tStop = tStop;
			this.coord = stop.getStopFacility().getCoord();
		}

		@Override
		public Map<Id<Link>, ? extends Link> getInLinks() {
			return this.ingoingLinks;
		}

		@Override
		public Map<Id<Link>, ? extends Link> getOutLinks() {
			return this.outgoingLinks;
		}

		@Override
		@Deprecated
		public boolean addInLink(final Link link) {
			throw new UnsupportedOperationException();
		}

		@Override
		@Deprecated
		public boolean addOutLink(final Link link) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Coord getCoord() {
			return this.coord;
		}

		@Override
		public Id<Node> getId() {
			return this.id;
		}

		public TransitRouteStop getStop() {
			return stop;
		}

		public TransitRoute getRoute() {
			return route;
		}

		public TransitLine getLine() {
			return line;
		}

		@Override
		@Deprecated
		public Link removeInLink(Id<Link> linkId) {
			// TODO Auto-generated method stub
			throw new RuntimeException("not implemented");
		}

		@Override
		@Deprecated
		public Link removeOutLink(Id<Link> outLinkId) {
			// TODO Auto-generated method stub
			throw new RuntimeException("not implemented");
		}

		@Override
		@Deprecated
		public void setCoord(Coord coord) {
			// TODO Auto-generated method stub
			throw new RuntimeException("not implemented");
		}

		@Override
		public Attributes getAttributes() {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Looks to me like an implementation of the Link interface, with
	 * get(Transit)Route and get(Transit)Line on top. To recall: TransitLine is
	 * something like M44. But it can have more than one route, e.g. going north,
	 * going south, long route, short route. That is, presumably we have one such
	 * TransitRouterNetworkLink per TransitRoute. kai/manuel, feb'12
	 */
	public static final class TransitRouterNetworkLink implements Link {

		public final TransitRouterNetworkNode fromNode;
		public final TransitRouterNetworkNode toNode;
		public final TransitRoute route;
		public final TransitLine line;
		final Id<Link> id;
		private double length;

		public TransitRouterNetworkLink(final Id<Link> id, final TransitRouterNetworkNode fromNode,
				final TransitRouterNetworkNode toNode, final TransitRoute route, final TransitLine line,
				Network network) {
			this.id = id;
			this.fromNode = fromNode;
			this.toNode = toNode;
			this.route = route;
			this.line = line;
			if (route == null)
				this.length = CoordUtils.calcEuclideanDistance(this.toNode.stop.getStopFacility().getCoord(),
						this.fromNode.stop.getStopFacility().getCoord());
			else {
				this.length = 0;
				for (Id<Link> linkId : route.getRoute().getSubRoute(fromNode.stop.getStopFacility().getLinkId(),
						toNode.stop.getStopFacility().getLinkId()).getLinkIds())
					this.length += network.getLinks().get(linkId).getLength();
				this.length += network.getLinks().get(toNode.stop.getStopFacility().getLinkId()).getLength();
			}
		}

		@Override
		public TransitRouterNetworkNode getFromNode() {
			return this.fromNode;
		}

		@Override
		public TransitRouterNetworkNode getToNode() {
			return this.toNode;
		}

		@Override
		public double getCapacity() {
			return getCapacity(0);
		}

		@Override
		public double getCapacity(final double time) {
			return 9999; //Infite capacity
		}

		@Override
		public double getFreespeed() {
			return getFreespeed(0);
		}

		@Override
		public double getFreespeed(final double time) {
			return 10; //The speed also doesn't matter.
		}

		@Override
		public Id<Link> getId() {
			return this.id;
		}

		@Override
		public double getNumberOfLanes() {
			return getNumberOfLanes(0);
		}

		@Override
		public double getNumberOfLanes(final double time) {
			return 1;
		}

		@Override
		public double getLength() {
			return this.length;
		}

		@Override
		public void setCapacity(final double capacity) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setFreespeed(final double freespeed) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean setFromNode(final Node node) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setNumberOfLanes(final double lanes) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setLength(final double length) {
			this.length = length;
		}

		@Override
		public boolean setToNode(final Node node) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Coord getCoord() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<String> getAllowedModes() {
			return null;
		}

		@Override
		public void setAllowedModes(final Set<String> modes) {
			throw new UnsupportedOperationException();
		}

		public TransitRoute getRoute() {
			return route;
		}

		public TransitLine getLine() {
			return line;
		}

		@Override
		public double getFlowCapacityPerSec() {
			// TODO Auto-generated method stub
			throw new RuntimeException("not implemented");
		}

		@Override
		public double getFlowCapacityPerSec(double time) {
			// TODO Auto-generated method stub
			throw new RuntimeException("not implemented");
		}

		@Override
		public Attributes getAttributes() {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public double getCapacityPeriod() {
			return 3600; //The capacity is defined per hour.
		}
	}

	public TransitRouterNetworkNode createNode(final TransitRouteStop stop, final TransitRoute route,
			final TransitLine line, final TransitStop tStop) {
		Id<Node> id = null;
		if (line == null && route == null)
			id = Id.createNodeId(stop.getStopFacility().getId().toString());
		else
			id = Id.createNodeId("number:" + nextNodeId++);
		final TransitRouterNetworkNode node = new TransitRouterNetworkNode(id, stop, tStop, route, line);
		if (this.nodes.get(node.getId()) != null)
			throw new RuntimeException();
		this.nodes.put(node.getId(), node);
		return node;
	}

	/**
	 * This function creates a transfer link
	 * @param network
	 * @param fromNode
	 * @param toNode
	 * @return
	 */
	public TransitRouterNetworkLink createLink(final Network network, final TransitRouterNetworkNode fromNode,
			final TransitRouterNetworkNode toNode) {
		final TransitRouterNetworkLink link = new TransitRouterNetworkLink(Id.createLinkId(this.nextLinkId++), fromNode,
				toNode, null, null, network);
		this.links.put(link.getId(), link);
		fromNode.outgoingLinks.put(link.getId(), link);
		toNode.ingoingLinks.put(link.getId(), link);
		return link;
	}

	public TransitRouterNetworkLink createLink(final Network network, final TransitRouterNetworkNode fromNode,
			final TransitRouterNetworkNode toNode, final TransitRoute route, final TransitLine line) {
		final TransitRouterNetworkLink link = new TransitRouterNetworkLink(Id.createLinkId(this.nextLinkId++), fromNode,
				toNode, route, line, network);
		this.getLinks().put(link.getId(), link);
		fromNode.outgoingLinks.put(link.getId(), link);
		toNode.ingoingLinks.put(link.getId(), link);
		return link;
	}

	@Override
	public Map<Id<Node>, TransitRouterNetworkNode> getNodes() {
		return this.nodes;
	}

	@Override
	public Map<Id<Link>, TransitRouterNetworkLink> getLinks() {
		return this.links;
	}

	public void finishInit() {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (TransitRouterNetworkNode node : getNodes().values())
			if (node.line == null) {
				Coord c = node.stop.getStopFacility().getCoord();
				if (c.getX() < minX)
					minX = c.getX();
				if (c.getY() < minY)
					minY = c.getY();
				if (c.getX() > maxX)
					maxX = c.getX();
				if (c.getY() > maxY)
					maxY = c.getY();
			}
		QuadTree<TransitRouterNetworkNode> quadTree = new QuadTree<TransitRouterNetworkNode>(minX, minY, maxX, maxY);
		for (TransitRouterNetworkNode node : getNodes().values()) {
			if (node.line == null) {
				Coord c = node.stop.getStopFacility().getCoord();
				quadTree.put(c.getX(), c.getY(), node);
			}
		}
		this.qtNodes = quadTree;
	}

	public static TransitRouterNetworkHR createFromSchedule(final Network network, final TransitSchedule schedule,
			final double maxBeelineWalkConnectionDistance) {
		log.info("start creating transit network");
		final TransitRouterNetworkHR transitNetwork = new TransitRouterNetworkHR();
		final Counter linkCounter = new Counter(" link #");
		final Counter nodeCounter = new Counter(" node #");
		int numTravelLinks = 0, numWaitingLinks = 0, numInsideLinks = 0, numTransferLinks = 0;
		Map<Id<TransitStopFacility>, TransitRouterNetworkNode> stops = new HashMap<Id<TransitStopFacility>, TransitRouterNetworkNode>();
		TransitRouterNetworkNode nodeSR, nodeS;
		// build stop nodes
		for (TransitLine line : schedule.getTransitLines().values())
			for (TransitRoute route : line.getRoutes().values())
				for (TransitRouteStop stop : route.getStops()) {
					nodeS = stops.get(stop.getStopFacility().getId());
					if (nodeS == null) {
						nodeS = transitNetwork.createNode(stop, null, null, null);// Node for Transit Stop Facility
						nodeCounter.incCounter();
						stops.put(stop.getStopFacility().getId(), nodeS);
					}
				}
		transitNetwork.finishInit();
		// build transfer links
		log.info("add transfer links");
		// connect all stops with walking links if they're located less than
		// beelineWalkConnectionDistance from each other
		for (TransitRouterNetworkNode node : transitNetwork.getNodes().values()) {
			for (TransitRouterNetworkNode node2 : transitNetwork.getNearestNodes(node.stop.getStopFacility().getCoord(),
					maxBeelineWalkConnectionDistance))
				if (node != node2) {
					transitNetwork.createLink(network, node, node2);
					linkCounter.incCounter();
					numTransferLinks++;
				}
		}
		// build nodes and links connecting the nodes according to the transit routes
		log.info("add travel, waiting and inside links");
		for (TransitLine line : schedule.getTransitLines().values())
			for (TransitRoute route : line.getRoutes().values()) {
				TransitRouterNetworkNode prevNode = null;
				List<TransitRouteStop> stoplist = route.getStops();
				for (int i = 0; i < stoplist.size(); i++) {
					TransitRouteStop stop = stoplist.get(i);
					nodeS = stops.get(stop.getStopFacility().getId());

					//checks the occ of the same stop in the transit route
					int occ = 0;
					for (int j = 0; j < i; j++) {
						if (stoplist.get(j).getStopFacility().getId().toString().replace("BT_", "").replace("bus_", "")
								.equals(stop.getStopFacility().getId().toString().replace("BT_", "").replace("bus_", ""))) {
							occ++;
						}
					}

					nodeSR = transitNetwork.createNode(stop, route, line, new TransitStop(stop, occ)); // nodes Transit Route Stop
					nodeCounter.incCounter();
					if (prevNode != null) {
						transitNetwork.createLink(network, prevNode, nodeSR, route, line);
						linkCounter.incCounter();
						numTravelLinks++;
					}
					prevNode = nodeSR;
					if(i != 0) { //It is not allowed to alight in the first stop
						transitNetwork.createLink(network, nodeSR, nodeS);
						linkCounter.incCounter();
						numInsideLinks++;
					}
					if(i < stoplist.size() - 1) { //It is not allowed to board in the final stop.
						transitNetwork.createLink(network, nodeS, nodeSR);
						linkCounter.incCounter();
						numWaitingLinks++;
					}
				}
			}
		log.info("transit router network statistics:");
		log.info(" # nodes: " + transitNetwork.getNodes().size());
		log.info(" # links total:     " + transitNetwork.getLinks().size());
		log.info(" # travel links:  " + numTravelLinks);
		log.info(" # waiting links:  " + numWaitingLinks);
		log.info(" # inside links:  " + numInsideLinks);
		log.info(" # transfer links:  " + numTransferLinks);
		return transitNetwork;
	}

	public Collection<TransitRouterNetworkNode> getNearestTSFNodes(final Coord coord, final double distance) {
		HashSet<TransitRouterNetworkNode> output = new HashSet<TransitRouterNetworkNode>();
		for(TransitRouterNetworkNode TN: getNearestNodes(coord, distance))
			if(TN.route == null && TN.line == null)
				output.add(TN);
		return output;
	}
	
	public Collection<TransitRouterNetworkNode> getNearestNodes(final Coord coord, final double distance) {
		return this.qtNodes.getDisk(coord.getX(), coord.getY(), distance);
	}

	public TransitRouterNetworkNode getNearestNode(final Coord coord) {
		return this.qtNodes.getClosest(coord.getX(), coord.getY());
	}

	@Override
	public double getCapacityPeriod() {
		return 3600.0;
	}

	@Override
	public NetworkFactory getFactory() {
		return null;
	}

	@Override
	public double getEffectiveLaneWidth() {
		return 3;
	}

	@Override
	public void addNode(Node nn) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addLink(Link ll) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Link removeLink(Id<Link> linkId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Node removeNode(Id<Node> nodeId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setCapacityPeriod(double capPeriod) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public void setEffectiveCellSize(double effectiveCellSize) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public void setEffectiveLaneWidth(double effectiveLaneWidth) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public void setName(String name) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public double getEffectiveCellSize() {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public Attributes getAttributes() {
		throw new UnsupportedOperationException();
	}
}
