package createBus;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class MiniBusRoute extends BusRoute {
	final static int distancePerStop = 300;
	static int numOfIntermediateStop = 0;

	String district; // Either HK, KLN or NT

	public MiniBusRoute(Scenario scenario, String operator, String district, String routeName,
			BusPathCalculator routingAlgo) {
		super(scenario.getNetwork(), operator, routeName, routingAlgo);
		invertedNetworkRouteAlgo = new L2lLeastCostCalculatorFactory(scenario, 
				Sets.newHashSet(TransportMode.car, "bus", "minibus")).getRoutingAlgo();
		this.district = district;
	}

	@Override
	public BusRoute getSubRoute(int fromStopSequence, int toStopSequence) {
		// TODO Auto-generated method stub
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public TransitRoute createRouteFromMap(Scenario scenario, Id<TransitRoute> transitRouteId,
			List<Id<Vehicle>> vehicleIDs, int startVehicleIndex, Map<Integer, Integer> departures) {

		TransitSchedule ts = scenario.getTransitSchedule();
		TransitScheduleFactory tsFactory = ts.getFactory();
		Network network = scenario.getNetwork();
		List<Id<Link>> routeLinkList = new LinkedList<Id<Link>>();
		List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
		Id<Link> prevLinkId = null;
		TransitStopFacility prevTsf = null;
		double delay = 0;

		firstBusStop.setTerminus(true);
		lastBusStop.setTerminus(true);

		invertedNetworkRouteAlgo = new L2lLeastCostCalculatorFactory(scenario, 
				Sets.newHashSet(TransportMode.car, "bus", "minibus")).getRoutingAlgo();

		// Create the bus stops and fill the path between their neighbor.
		for (Tuple<String, BusStop> busStopTuple : busStops) {
			BusStop busStop = busStopTuple.getSecond();

			TransitStopFacility currTsf = busStop.createOrGetTransitStopFacility(network, ts);
			Id<Link> currLinkId = currTsf.getLinkId(); // It should be consistent with the TransitStopFacility

			// For the iterations after the first (with prevLinkId), we fill the path
			// between stops.
			if (prevLinkId != null && !prevLinkId.equals(currLinkId)) { // We fill the path only for different links
				routeLinkList.add(prevLinkId);
				Link prevLink = scenario.getNetwork().getLinks().get(prevLinkId);
				delay += 1.2 * prevLink.getLength() / prevLink.getFreespeed() + 10;
				Path pathBetween = getAndFillPathBetweenTwoNode(prevTsf, currTsf, routeLinkList);

				// Create and add a stop for each n m.
				double distanceTravelled = 0;
				for (Link link : pathBetween.links) {
					distanceTravelled += link.getLength();
					delay += 1.2 * link.getLength() / link.getFreespeed() + 10;
					if (distanceTravelled > distancePerStop) { // Create a stop whenever the distance is enough.
						BusStop tempMinibusStop = new BusStop("minibus", "GMB_M_" + numOfIntermediateStop, "",
								link.getCoord(), Lists.newArrayList(link.getId()));
						tempMinibusStop.setLinkId(link.getId());
						TransitStopFacility minibusStop = tempMinibusStop.createOrGetTransitStopFacility(network, ts);
						numOfIntermediateStop++;

						TransitRouteStop minibusRouteStop = tsFactory.createTransitRouteStop(minibusStop, delay,
								delay + 10);
						stops.add(minibusRouteStop);
						minibusRouteStop.setAwaitDepartureTime(false);

						distanceTravelled = distanceTravelled > 600 ? 301 : distanceTravelled - 300; // Reset the
																										// distance
																										// afterward.
					}
				}
				
			} else if (prevLinkId != null) {
				//XXX Possible modification to provide additional stops in long links JLo
				
				delay += (NetworkUtils.getEuclideanDistance(currTsf.getCoord(), prevTsf.getCoord()) / 15);
			}
			
			TransitRouteStop trs = tsFactory.createTransitRouteStop(currTsf, delay, delay + 10);
			trs.setAwaitDepartureTime(false);
			stops.add(trs); // Add the stop to the transitRouteStop
			
			prevTsf = currTsf;
			prevLinkId = currLinkId;
		}
		// new L2lLeastCostCalculatorFactory(scenario).test(scenario);

		routeLinkList.add(prevLinkId); // Add the last link after the final iteration

		NetworkRoute route = RouteUtils.createLinkNetworkRouteImpl(firstBusStop.getLinkId(),
				routeLinkList.subList(1, routeLinkList.size() - 1), lastBusStop.getLinkId());

		TransitRoute transitRoute = tsFactory.createTransitRoute(transitRouteId, route, stops, "minibus");
		fillDeparture(tsFactory, vehicleIDs, startVehicleIndex, transitRoute, departures); // fill the departure of the
																							// route
		return transitRoute;
	}

}
