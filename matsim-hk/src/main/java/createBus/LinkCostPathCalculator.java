package createBus;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.vehicles.Vehicle;

/**
 * It is the link cost path calculator for the bus.
 * 
 * @author eleead
 *
 */
@Deprecated
public class LinkCostPathCalculator implements BusPathCalculator {

	private LeastCostPathCalculator dijkstra;

	public LinkCostPathCalculator(Network network) {
		this.dijkstra = new DijkstraFactory().createPathCalculator(network, new BusRouteDisutility(),
				new BusRouteDisutility());
	}

	@Override
	public Path calcLeastCostPath(Link fromLink, Link toLink, double starttime, Person person, Vehicle vehicle) {
		return dijkstra.calcLeastCostPath(fromLink.getToNode(), toLink.getFromNode(), starttime, person, vehicle);
	}

}
