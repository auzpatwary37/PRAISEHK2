package ust.hk.praisehk.metamodelcalibration.fixtures;

import java.util.Arrays;
import java.util.HashSet;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;

import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;

/**
 * FIXTURE A - trivial link.
 *
 * <p>One road link with known physical attributes, wrapped in {@link CNLLink}.
 * Used for link-performance (BPR) tests only. No route choice, no demand.</p>
 *
 * <p>Hand-computable reference values (time bean = 1 hour, so the capacity
 * period ratio {@code (end-start)/3600} is exactly 1):</p>
 * <pre>
 *   length      = 1000 m
 *   freespeed   = 20 m/s
 *   capacity    = 1800 veh/h
 *   free-flow t = 1000/20 = 50 s
 *   BPR alpha   = 0.15
 *   BPR beta    = 4
 * </pre>
 *
 * <p>With a car volume of 900 and transit/residual volume of 0 and
 * {@code CapacityMultiplier = 1}:
 * {@code t = 50 * (1 + 0.15 * (900/1800)^4) = 50.46875 s}.</p>
 */
public final class FixtureA_TrivialLink {

	private FixtureA_TrivialLink() {
	}

	public static final Id<Link> LINK_ID = Id.createLinkId("L1");
	public static final Id<Node> FROM_NODE_ID = Id.createNodeId("A");
	public static final Id<Node> TO_NODE_ID = Id.createNodeId("B");

	public static final double LENGTH = 1000.;
	public static final double FREESPEED = 20.;
	public static final double CAPACITY = 1800.;
	public static final double CAR_VOLUME = 900.;

	public static final double FREE_FLOW_TRAVEL_TIME = LENGTH / FREESPEED; // 50 s
	public static final double TEXTBOOK_ALPHA = 0.15;
	public static final double TEXTBOOK_BETA = 4.;

	/** A link that only cars may use. */
	public static Network network() {
		return network(new HashSet<>(Arrays.asList("car")));
	}

	/** A link with an explicit allowed-mode set (e.g. add "train"). */
	public static Network network(java.util.Set<String> allowedModes) {
		Network network = NetworkUtils.createNetwork();
		Node from = network.getFactory().createNode(FROM_NODE_ID, new Coord(0., 0.));
		Node to = network.getFactory().createNode(TO_NODE_ID, new Coord(LENGTH, 0.));
		network.addNode(from);
		network.addNode(to);

		Link link = network.getFactory().createLink(LINK_ID, from, to);
		link.setLength(LENGTH);
		link.setFreespeed(FREESPEED);
		link.setCapacity(CAPACITY);
		link.setNumberOfLanes(1.);
		link.setAllowedModes(allowedModes);
		network.addLink(link);
		return network;
	}

	public static CNLLink cNLLink() {
		Network network = network();
		return new CNLLink(network.getLinks().get(LINK_ID), network);
	}

	/** Expected BPR travel time for a given car volume, all else equal. */
	public static double expectedBprTravelTime(double carVolume, double alpha, double beta,
			double capacityMultiplier) {
		double freeFlow = LENGTH / FREESPEED;
		double effectiveCapacity = CAPACITY * capacityMultiplier;
		return freeFlow * (1. + alpha * Math.pow(carVolume / effectiveCapacity, beta));
	}
}
