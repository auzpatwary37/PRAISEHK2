package dynamicTransitRouter.costs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.router.util.AStarNodeData;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.collections.PseudoRemovePriorityQueue;
import org.matsim.core.utils.collections.RouterPriorityQueue;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.router.CustomDataManager;
import org.matsim.pt.router.TransitTravelDisutility;
import org.matsim.vehicles.Vehicle;

import dynamicTransitRouter.TransitRouterFareDynamicImpl;

/**
 * 1 time disposable class for single routing request
 */
public class TransitAStarLeastCostPathTree {

	/**
	 * The network on which we find routes.
	 */
	protected Network network;

	/**
	 * The cost calculator. Provides the cost for each link and time step.
	 */
	private final TransitTravelDisutility costFunction;

	/**
	 * The travel time calculator. Provides the travel time for each link and time
	 * step.
	 */
	private final TravelTime timeFunction;

	private final HashMap<Id<Node>, AStarNodeData> nodeData;
	private final double distanceFactor; // The weight of distance (m) to the expected cost.
	private final Person person;
	private final Vehicle vehicle = null;
	private final CustomDataManager customDataManager = new CustomDataManager();
	private final Map<Node, InitialNode> fromNodes;
	private final Map<Node, InitialNode> toNodes;
	private Node minCostNode = null;

	private final RouterPriorityQueue<Node> pendingNodes;
	
	private final Coord fromCoord;
	private final Coord toCoord;
	private final double directLength;
	
	//FIXME Settings for clacRemainingExpectedCost
	private final boolean opB;
	private final boolean opC;
	
	private Logger log = Logger.getLogger(TransitAStarLeastCostPathTree.class);

//	@Deprecated		//is this deprecated? JLo
//	public TransitAStarLeastCostPathTree(final Network network, final TransitTravelDisutility costFunction,
//			final TravelTime timeFunction, double distanceFactor, final Coord toCoord,
//			final Map<Node, InitialNode> fromNodes, final Person person) {
//		this.network = network;
//		this.costFunction = costFunction;
//		this.timeFunction = timeFunction;
//		this.distanceFactor = distanceFactor;
//
//		this.nodeData = new HashMap<>((int) (network.getNodes().size() * 1.1), 0.95f); // Only to be modified in
//																						// createOrGetData
//
//		// create tree
//		this.resetNetworkVisited(toCoord);
//		this.person = person;
//		this.customDataManager.reset();
//		this.fromNodes = fromNodes;
//
//		pendingNodes = (RouterPriorityQueue<Node>) createRouterPriorityQueue();
//		for (Map.Entry<Node, InitialNode> entry : fromNodes.entrySet()) {
//			AStarNodeData data = createOrGetData(entry.getKey(), toCoord);
//			visitNode(entry.getKey(), data, pendingNodes, entry.getValue().initialTime, entry.getValue().initialCost,
//					null, toCoord);
//		}
//
//		// do the real work
//		while (pendingNodes.size() > 0) {
//			Node outNode = pendingNodes.poll();
//			relaxNode(outNode, pendingNodes, toCoord);
//		}
//	}

	public TransitAStarLeastCostPathTree(final Network network, final TransitTravelDisutility costFunction,
			final TravelTime timeFunction, double distanceFactor, final Map<Node, InitialNode> fromNodes,
			final Map<Node, InitialNode> toNodes, final Coord fromCoord, final Coord toCoord, final Person person) {
		this.network = network;
		this.costFunction = costFunction;
		this.timeFunction = timeFunction;
		this.distanceFactor = distanceFactor;

		this.nodeData = new HashMap<>((int) (network.getNodes().size() * 1.1), 0.95f);

		// create tree
//		this.resetNetworkVisited(toCoord);	//only current function is to pre- create all the nodes, therefore waste of resources JLo
//		this.customDataManager.reset();		//its new anyways therefore useless JLo
		this.person = person;
		this.fromNodes = fromNodes;
		this.toNodes = toNodes;
		
		//A* heuristic static stuff
		this.toCoord = toCoord;
		this.fromCoord = fromCoord;
		this.directLength = euclieanDistance(fromCoord, toCoord);

		this.pendingNodes = (RouterPriorityQueue<Node>) createRouterPriorityQueue();
		
		for (Map.Entry<Node, InitialNode> entry : fromNodes.entrySet()) {
			AStarNodeData data = createOrGetData(entry.getKey());
			visitNode(entry.getKey(), data, pendingNodes, entry.getValue().initialTime, entry.getValue().initialCost,
					null, toCoord);
		}
		
		//Make the a star settings
		switch(TransitRouterFareDynamicImpl.aStarSetting) {
		case 'b':
			opB = true;
			opC = false;
			break;
		case 'c':
		default:			//made default c as well JLo
			opB = false;
			opC = true;
			break;
		}

		expandNodeData();
	}

	private int getIterationId() {
		// TODO could delete all the now unnecessary occurrences of the IterationID
		// especially in DijkstraNodeData and
		// TODO replace it with a flag visited but that would interfere the useage of
		// the MultiNodeDijkstra
		return 0;
	}

	/**
	 * Resets all nodes in the network as if they have not been visited yet.
	 * 
	 * Current function is just pre-creating all the nodes JLo
	 */
	@Deprecated
	private void resetNetworkVisited(Coord toCoord) {
		for (Node node : this.network.getNodes().values()) {
			AStarNodeData data = createOrGetData(node);
			data.resetVisited();
		}
	}

	private void expandNodeData() {
		Set<Node> endNodes = new HashSet<>(this.toNodes.keySet());
		double minCost = Double.MAX_VALUE;

		// do the real work
		while (endNodes.size() > 0) {
			Node outNode = this.pendingNodes.poll();

			if (outNode == null) {
				// seems we have no more nodes left, but not yet reached all endNodes...
				endNodes.clear();
			} else {
				//escape assuming a good solution already reached
				//Revert back to the old one, as it has the error that allows a huge jump in the end. Enoch,Dec21
//				if(endNodes.contains(outNode))
//					break;
//				else
//					this.relaxNode(outNode);
				
				//This comparison and recaculation is necessary, cause it takes into account of last initial cost
				AStarNodeData data = createOrGetData(outNode);
				boolean isEndNode = endNodes.remove(outNode); // Check if it reached the destination or not
				if (isEndNode) {
					InitialNode initData = this.toNodes.get(outNode);
					double cost = data.getCost() + initData.initialCost;	//removed heuristic term, replaced with pre-calculated walking cost -JLo
					if (cost < minCost) {
						minCost = cost;		//comparing the whole path cost, inc transit walk from org to start node and from end node to dest -JLo
						this.minCostNode = outNode;
					}
				}
				if (data.getCost() > minCost) { //See if it is wrong
					/*this is a little bit weird, this is based on the thinking any other nodes reached the top of the queue after this node will have inferior cost, 
						then why not just use the first reached node directly be the exit clause JLo*/
					//XXX This does have a possibility of being wrong due to BBI rebates, but given it is not enabled, this is correct at this stage -JLo
					//XXX possible work around for rebate, find out the max rebate, and put it here
					endNodes.clear(); // we can't get any better now
				} else if(!isEndNode){
					relaxNode(outNode);
				}
			}
		}
	}

	/**
	 * Method to request the path from the (cached) fromNodes to the passed toNodes.
	 * Should only be requested after calling createTransitLeastCostPathTree().
	 *
	 * @param toNodes
	 *            The nodes that are the next stops to the toCoord and you like to
	 *            route to.
	 *
	 * @return the leastCostPath between the fromNode and the toNode. Will be null
	 *         if the path could not be found.
	 */
	public Path getPath() {
		// find the best node
		double minCost = Double.POSITIVE_INFINITY;
		// Node minCostNode = null;
//		for (Entry<Node, InitialNode> currentNodeEntry : toNodes.entrySet()) {
//			AStarNodeData r = this.nodeData.get(currentNodeEntry.getKey().getId());
//			if (r == null) {
//				continue;
////				expandNodeData(toNodes, toCoord);		//this just creates an infinite loop if node is unreachable, at class creation already relaxed nodes??? so assuming that worked this is useless -JLo
//			}
//			
////			AStarNodeData data = createOrGetData(currentNodeEntry.getKey(), toCoord);		//useless as above is doing the exact same check -JLo
////			if (data.getPrevLink() == null) { // Ignore the links that haven't even been explored.
////				continue;
////			}
//			double cost = r.getCost() + currentNodeEntry.getValue().initialCost;		//add in the final walking cost for cost comparison
//			if (r.getCost() != 0.0 || fromNodes.containsKey(currentNodeEntry.getKey())) {	//allow 0 cost iff same start/end node
//				if (cost < minCost) {
//					minCost = cost;
//					minCostNode = currentNodeEntry.getKey();
//				}
//			}
//		}

		// cannot find a reasonable path to any endNodes
		if (minCostNode == null) {
			return null;
		}
		
		//DEBUG FIXME
//		log.debug("minCostNode = "+minCostNode.getId()+" cost = "+this.nodeData.get(minCostNode.getId()).getCost()+" final walking cost = "+toNodes.get(minCostNode).initialCost);

		// now construct the path
		List<Node> nodes = new LinkedList<>();
		List<Link> links = new LinkedList<>();
		AStarNodeData toNodeData = createOrGetData(minCostNode);

		nodes.add(0, minCostNode);
		Link tmpLink = toNodeData.getPrevLink();
		while (tmpLink != null) {
			
			//DEBUG FIXME
//			log.debug("traceback nodeId = "+tmpLink.getFromNode().getId()+" cost = "+this.nodeData.get(tmpLink.getFromNode().getId()).getCost()+" expected total cost = "+this.getPriority(this.createOrGetData(tmpLink.getFromNode())));
			
			links.add(0, tmpLink);
			nodes.add(0, tmpLink.getFromNode());
			tmpLink = createOrGetData(tmpLink.getFromNode()).getPrevLink();
			
//			if(links.size()>1000) {
//				System.nanoTime();
//			}
		}

		InitialNode toNodeWarpped = toNodes.get(minCostNode);

//		return new Path(nodes, links, toNodeData.getTime() - startNodeData.getTime(),
//				toNodeData.getCost() - startNodeData.getCost());
		//Changed to not remove and add back in the initial walking cost, and this adds the walking cost from outNode to Dest -JLo
		return new Path(nodes, links, toNodeData.getTime() + toNodeWarpped.initialTime, toNodeData.getCost() + toNodeWarpped.initialCost);
	}

	/**
	 * <strike>Allow replacing the RouterPriorityQueue.</strike><br>
	 * Streamlined this class, replacing the RouterPriorityQueue is no longer allowed JLo
	 */
	private static RouterPriorityQueue<? extends Node> createRouterPriorityQueue() {
		return new PseudoRemovePriorityQueue<>(500);
	}

	/**
	 * Inserts the given Node n into the pendingNodes queue and updates its time and
	 * cost information.
	 *
	 * @param n
	 *            The Node that is revisited.
	 * @param data
	 *            The data for n.
	 * @param pendingNodes
	 *            The nodes visited and not processed yet.
	 * @param time
	 *            The time of the visit of n.
	 * @param cost
	 *            The accumulated cost at the time of the visit of n.
	 * @param outLink
	 *            The node from which we came visiting n.
	 */
	private void visitNode(final Node n, final AStarNodeData data, final RouterPriorityQueue<Node> pendingNodes,
			final double time, final double cost, final Link outLink, final Coord toCoord) {
		double priority = getPriority(data);
		if(priority < Double.MAX_VALUE) { //Only for node with reasonable priority
			data.visit(outLink, cost, time, getIterationId());
//			data.setExpectedRemainingCost(this.calcExpectedRemainingCost(n));	//XXX Currently the cost remains the same, so unnecessary to re-calc JLo
			pendingNodes.add(n, getPriority(data));
		}
	}

	/**
	 * Expands the given Node in the routing algorithm; may be overridden in
	 * sub-classes.
	 * 
	 * Added a check on whether the node is an artificial "dead end" already (cost set to max_value) JLo
	 *
	 * @param outNode
	 *            The Node to be expanded.
	 * @param pendingNodes
	 *            The set of pending nodes so far.
	 */
	private void relaxNode(final Node outNode) {

		AStarNodeData outData = createOrGetData(outNode);
		double priority = getPriority(outData);
		if(priority < Double.MAX_VALUE) { //Only for nodes with reasonable priority JLo
			double currTime = outData.getTime();
			double currCost = outData.getCost(); // It is the real cost, as it would be used for update later.
			for (Link l : outNode.getOutLinks().values()) {
				addToPendingNodes(l, l.getToNode(), currTime, currCost);
			}
		}
	}

	/**
	 * Logic that was previously located in the relaxNode(...) method. By doing so,
	 * the FastDijkstra can overwrite relaxNode without copying the logic.
	 */
	@Deprecated
	private void relaxNodeLogic(final Link l, final double currTime, final double acutalCurrCost) {
		addToPendingNodes(l, l.getToNode(), currTime, acutalCurrCost);
	}

	/**
	 * Adds some parameters to the given Node then adds it to the set of pending
	 * nodes.
	 *
	 * @param l
	 *            The link from which we came to this Node.
	 * @param n
	 *            The Node to add to the pending nodes.
	 * @param pendingNodes
	 *            The set of pending nodes.
	 * @param currTime
	 *            The time at which we started to traverse l.
	 * @param acutualCurrCost
	 *            The cost at the time we started to traverse l.
	 * @return true if the node was added to the pending nodes, false otherwise
	 *         (e.g. when the same node already has an lower cost).
	 */
	private boolean addToPendingNodes(final Link l, final Node n, final double currTime, final double acutualCurrCost) {

		this.customDataManager.initForLink(l);
		double travelTime = this.timeFunction.getLinkTravelTime(l, currTime, this.person, this.vehicle);
		double travelCost = this.costFunction.getLinkTravelDisutility(l, currTime, this.person, this.vehicle,
				this.customDataManager);
		AStarNodeData data = createOrGetData(n);
		if (!data.isVisited(getIterationId())) {
			visitNode(n, data, this.pendingNodes, currTime + travelTime, acutualCurrCost + travelCost, l, this.toCoord);
			this.customDataManager.storeTmpData();
			return true;
		}
		double nCost = data.getCost(); //See if it is wrong or not. It should be cost, as it would be for the comparison below.
		
		//XXX It would have a problem here indeed, as sometimes arriving at the node with higher cost could
		//reduce the cost (e.g. Getting aboard to a bad bus)
		double totalCost = acutualCurrCost + travelCost;
		if (totalCost < nCost) {
			revisitNode(n, data, this.pendingNodes, currTime + travelTime, totalCost, l, this.toCoord);
			this.customDataManager.storeTmpData();
			return true;
		}

		return false;
	}

	/**
	 * Changes the position of the given Node n in the pendingNodes queue and
	 * updates its time and cost information.
	 *
	 * @param n
	 *            The Node that is revisited.
	 * @param data
	 *            The data for n.
	 * @param pendingNodes
	 *            The nodes visited and not processed yet.
	 * @param time
	 *            The time of the visit of n.
	 * @param cost
	 *            The accumulated cost at the time of the visit of n.
	 * @param outLink
	 *            The link from which we came visiting n.
	 */
	void revisitNode(final Node n, final AStarNodeData data, final RouterPriorityQueue<Node> pendingNodes,
			final double time, final double grossCost, final Link outLink, final Coord toCoord) {
		pendingNodes.remove(n);

		data.visit(outLink, grossCost, time, getIterationId());
//		data.setExpectedRemainingCost(this.calcExpectedRemainingCost(n));	//XXX Currently the cost remains the same, so unnecessary to re-calc JLo
		pendingNodes.add(n, getPriority(data));
	}

	/**
	 * The value used to sort the pending nodes during routing. This implementation
	 * compares the total effective travel cost to sort the nodes in the pending
	 * nodes queue during routing.
	 */
	private double getPriority(final AStarNodeData data) {
		return data.getExpectedCost();
	}

	public static class InitialNode {
		public final double initialCost;
		public final double initialTime;

		public InitialNode(final double initialCost, final double initialTime) {
			this.initialCost = initialCost;
			this.initialTime = initialTime;
		}
	}

	/**
	 * Returns the data for the given node. Creates a new NodeData if none exists
	 * yet.
	 *
	 * @param n
	 *            The Node for which to return the data.
	 * @return The data for the given Node
	 */
	private AStarNodeData createOrGetData(final Node n) {
		AStarNodeData r = this.nodeData.get(n.getId());
		if (null == r) {
			r = new AStarNodeData();
			r.setExpectedRemainingCost(this.calcExpectedRemainingCost(n)); 
			this.nodeData.put(n.getId(), r);
		}
		return r;
	}
	
	/**
	 * Calculates the heuristic factor governing the A* search behaviour<p>
	 * It would only be called once when creating the node, as it solely depends on the node.
	 * <b>
	 * If the cost calculation no longer solely depends on the static final fixed location of each nodes, <br>
	 * Code modification may be required to this.visitNode & this.revisitNode</b>
	 */
	private double calcExpectedRemainingCost(final Node n) {
		// !!! WARN	!!! For ANY CHANGES READ method documentation !!! JLo
		double ouput = 0;
		if(opB)
			ouput += this.distanceFactor * euclieanDistance(n.getCoord(), this.toCoord);
		else if(opC) {
			ouput += this.distanceFactor*(euclieanDistance(this.fromCoord, n.getCoord()) + euclieanDistance(this.toCoord, n.getCoord()) - this.directLength);
		}
		return ouput;
	}

	/**
	 * Convenience method which might just be the same performance as CoordUtils.calcEuclideanDistance JLo
	 */
	private static double euclieanDistance(Coord fromCoord, Coord toCoord) {
		return Math.sqrt(Math.pow(fromCoord.getX() - toCoord.getX(), 2) + Math.pow(fromCoord.getY() - toCoord.getY(), 2));
	}
}
