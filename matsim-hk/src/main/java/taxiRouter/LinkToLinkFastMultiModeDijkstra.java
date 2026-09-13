package taxiRouter;

import java.util.Collection;

import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.FastMultiNodeDijkstra;
import org.matsim.core.router.FastRouterDelegateFactory;
import org.matsim.core.router.ImaginaryNode;
import org.matsim.core.router.InitialNode;
import org.matsim.core.router.util.PreProcessDijkstra;
import org.matsim.core.router.util.RoutingNetwork;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.vehicles.Vehicle;

public class LinkToLinkFastMultiModeDijkstra extends FastMultiNodeDijkstra {

	protected LinkToLinkFastMultiModeDijkstra(RoutingNetwork routingNetwork, TravelDisutility costFunction,
			TravelTime timeFunction, PreProcessDijkstra preProcessData, FastRouterDelegateFactory fastRouterFactory,
			boolean searchAllEndNodes) {
		super(routingNetwork, costFunction, timeFunction, preProcessData, fastRouterFactory, searchAllEndNodes);
		// TODO Auto-generated constructor stub
	}
	
	/*
	 * Replace the references to the from and to nodes with their corresponding
	 * nodes in the routing network.
	 */
	@Override
	public Path calcLeastCostPath(final Node fromNode, final Node toNode, final double startTime, final Person person, final Vehicle vehicle) {
		Path superPath = super.calcLeastCostPath(fromNode, toNode, startTime, person, vehicle);
		//Based on the 
		
		
		return superPath;
	}
}
