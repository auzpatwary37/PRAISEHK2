package ust.hk.praisehk.metamodelcalibration.fixtures;

import java.util.ArrayList;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLRoute;

/**
 * FIXTURE C - one OD pair, two routes.
 *
 * <p>Both routes have known free-flow times and capacities, and both use links
 * of capacity 1800 veh/h, so congestion effects are hand-computable. Used for
 * route utility, logit split, congestion and (later) SUE iteration and
 * convergence tests.</p>
 *
 * <pre>
 *   route1: [L_AB, L_BD]  distance 2000 m  free-flow 100 s
 *   route2: [L_AC, L_CD]  distance 6000 m  free-flow 300 s
 * </pre>
 */
public final class FixtureC_OneOdTwoRoutes {

	private FixtureC_OneOdTwoRoutes() {
	}

	public static final Id<AnalyticalModelODpair> OD_ID = Id.create("od_c", AnalyticalModelODpair.class);
	public static final Id<AnalyticalModelRoute> ROUTE_1 = Id.create("r_1", AnalyticalModelRoute.class);
	public static final Id<AnalyticalModelRoute> ROUTE_2 = Id.create("r_2", AnalyticalModelRoute.class);

	public static final double DISTANCE_1 = 2000.;
	public static final double DISTANCE_2 = 6000.;

	public static final double FREE_FLOW_TIME_1 = 100.;
	public static final double FREE_FLOW_TIME_2 = 300.;

	public static Network network() {
		return SyntheticNetworks.twoRouteNetwork();
	}

	public static CNLNetwork cNLNetwork() {
		return new CNLNetwork(network());
	}

	public static AnalyticalModelODpair odPair(Network network) {
		return new AnalyticalModelODpair(OD_ID,
				network.getNodes().get(SyntheticNetworks.A),
				network.getNodes().get(SyntheticNetworks.D),
				network, TimeBeans.singleHour());
	}

	public static AnalyticalModelODpair odPair() {
		return odPair(network());
	}

	public static CNLRoute route1() {
		return route(ROUTE_1, SyntheticNetworks.L_AB, SyntheticNetworks.L_BD, DISTANCE_1);
	}

	public static CNLRoute route2() {
		return route(ROUTE_2, SyntheticNetworks.L_AC, SyntheticNetworks.L_CD, DISTANCE_2);
	}

	private static CNLRoute route(Id<AnalyticalModelRoute> id, Id<Link> first, Id<Link> second,
			double distance) {
		ArrayList<Id<Link>> links = new ArrayList<>();
		links.add(first);
		links.add(second);
		return new CNLRoute(id, links, distance, null);
	}

	/** Congested car volume such that both routes carry {@code volume} on every link. */
	public static void loadEveryLinkWith(CNLNetwork network, double volume) {
		for (Id<Link> id : network.getLinks().keySet()) {
			network.getLinks().get(id).addLinkCarVolume(volume);
		}
	}
}
