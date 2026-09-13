package ust.hk.praisehk.metamodelcalibration.fixtures;

import java.util.ArrayList;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLRoute;

/**
 * FIXTURE B - one OD pair, one route.
 *
 * <p>No route-choice ambiguity, so demand conservation and assignment sanity
 * can be checked unambiguously. Free-flow route travel time is exactly
 * {@code 1000/20 = 50 s}.</p>
 */
public final class FixtureB_OneOdOneRoute {

	private FixtureB_OneOdOneRoute() {
	}

	public static final Id<AnalyticalModelODpair> OD_ID = Id.create("od_1", AnalyticalModelODpair.class);
	public static final Id<AnalyticalModelRoute> ROUTE_ID = Id.create("r_1", AnalyticalModelRoute.class);
	public static final double DISTANCE = 1000.;

	public static Network network() {
		return SyntheticNetworks.twoNodeNetwork();
	}

	public static CNLNetwork cNLNetwork() {
		return new CNLNetwork(network());
	}

	public static AnalyticalModelODpair odPair(Network network) {
		return new AnalyticalModelODpair(OD_ID,
				network.getNodes().get(SyntheticNetworks.A),
				network.getNodes().get(SyntheticNetworks.B),
				network, TimeBeans.singleHour());
	}

	public static AnalyticalModelODpair odPair() {
		return odPair(network());
	}

	public static CNLRoute route() {
		ArrayList<Id<org.matsim.api.core.v01.network.Link>> links = new ArrayList<>();
		links.add(SyntheticNetworks.L_AB);
		return new CNLRoute(ROUTE_ID, links, DISTANCE, null);
	}
}
