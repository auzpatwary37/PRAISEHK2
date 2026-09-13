package dynamicTransitRouter.costs;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.pt.router.TransitTravelDisutility;

public class MultiNodeAStarEucliean {
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

	private final double distanceFactor; // The weight of distance (m) to the expected cost.

	public MultiNodeAStarEucliean(final Network network, final TransitTravelDisutility costFunction,
			final TravelTime timeFunction, final double distanceFactor) {
		this.network = network;
		this.costFunction = costFunction;
		this.timeFunction = timeFunction;
		this.distanceFactor = distanceFactor;
	}

	public Path calcLeastCostPath(final Map<Node, InitialNode> fromNodes, final Map<Node, InitialNode> toNodes,
			Coord fromCoord, Coord toCoord, final Person person) {
		Map<Node, TransitAStarLeastCostPathTree.InitialNode> swapedToNodes = swapNodes(toNodes);
		TransitAStarLeastCostPathTree tree = new TransitAStarLeastCostPathTree(network, costFunction, timeFunction,
				distanceFactor, swapNodes(fromNodes), swapedToNodes, fromCoord, toCoord, person);
		return tree.getPath();
	}

	private Map<Node, TransitAStarLeastCostPathTree.InitialNode> swapNodes(final Map<Node, InitialNode> original) {
		Map<Node, TransitAStarLeastCostPathTree.InitialNode> result = new HashMap<>();
		for (Map.Entry<Node, InitialNode> entry : original.entrySet()) {
			result.put(entry.getKey(), new TransitAStarLeastCostPathTree.InitialNode(entry.getValue().initialCost,
					entry.getValue().initialTime));
		}
		return result;
	}

	public static class InitialNode {
		public final double initialCost;
		public final double initialTime;

		public InitialNode(final double initialCost, final double initialTime) {
			this.initialCost = initialCost;
			this.initialTime = initialTime;
		}
	}

}
