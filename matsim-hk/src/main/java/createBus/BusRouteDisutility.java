package createBus;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

/**
 * This class is a disutility for finding the bus path.
 * 
 * @author eleead
 *
 */
public class BusRouteDisutility implements TravelDisutility, TravelTime {

	@Override
	public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
		return link.getLength() / link.getFreespeed();
	}

	@Override
	public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
		return link.getLength();
	}

	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		return link.getLength();
	}

}
