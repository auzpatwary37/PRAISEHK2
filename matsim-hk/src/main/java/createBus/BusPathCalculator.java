package createBus;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.vehicles.Vehicle;

public interface BusPathCalculator {
	public Path calcLeastCostPath(Link fromLink, Link toLink, double starttime, Person person, Vehicle vehicle);
}
