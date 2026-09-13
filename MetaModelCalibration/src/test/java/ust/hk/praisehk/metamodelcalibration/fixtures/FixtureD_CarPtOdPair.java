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
 * FIXTURE D - car/PT OD pair (smallest multimodal example).
 *
 * <p>A single physical link is shared by car and PT, so the multimodal capacity
 * interaction in {@code CNLLink.getLinkTravelTime} is exercised: the effective
 * PCU load is {@code carVolume + transitVolume * CapacityMultiplier + residual}.
 * Free-flow car time is {@code 2000/20 = 100 s}.</p>
 *
 * <p><b>SCOPE NOTE (honest limitation):</b> the PT side of this fixture is only
 * partially built here. Constructing a genuine PT route requires a MATSim
 * {@code TransitSchedule}, {@code TransitLine}, {@code TransitRoute} and
 * {@code TransitStopFacility}s, plus the HK fare classes
 * ({@code transitFareAndHandler.FareLink}, {@code dynamicTransitRouter.*}).
 * That scaffolding is deliberately deferred: the mission requires that
 * transit-route mathematics be characterized independently first
 * (see docs/modernization/TEST_MATRIX.md, phases 2 and 8). Until then this
 * fixture provides the shared car/PT physical link, the OD pair and the car
 * route, and is used for modal-split structural and capacity-sharing tests.
 * The PT-route construction must be completed in the SUE/transit PR.</p>
 */
public final class FixtureD_CarPtOdPair {

	private FixtureD_CarPtOdPair() {
	}

	public static final Id<AnalyticalModelODpair> OD_ID = Id.create("od_carpt", AnalyticalModelODpair.class);
	public static final Id<AnalyticalModelRoute> CAR_ROUTE_ID = Id.create("r_car", AnalyticalModelRoute.class);

	public static final double DISTANCE = 2000.;
	public static final double FREE_FLOW_TIME = 100.;

	public static Network network() {
		return SyntheticNetworks.carPtNetwork();
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

	public static CNLRoute carRoute() {
		ArrayList<Id<Link>> links = new ArrayList<>();
		links.add(SyntheticNetworks.L_AB);
		return new CNLRoute(CAR_ROUTE_ID, links, DISTANCE, null);
	}

	/** The one physical link shared by car and PT. */
	public static Id<Link> sharedLinkId() {
		return SyntheticNetworks.L_AB;
	}
}
