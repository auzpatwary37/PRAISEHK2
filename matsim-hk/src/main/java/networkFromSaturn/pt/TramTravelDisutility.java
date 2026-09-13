package networkFromSaturn.pt;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

public class TramTravelDisutility implements TravelDisutility, TravelTime {

	@Override
	public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
		if (link.getAllowedModes().contains("tram")) {
			return link.getLength();
		} else {
			return 999999;
		}
	}

	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		return this.getLinkTravelDisutility(link, 0, null, null);
	}

	@Override
	public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
		return this.getLinkTravelDisutility(link, time, person, vehicle);
	}

}
