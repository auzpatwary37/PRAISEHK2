package ust.hk.praisehk.metamodelcalibration.fixtures;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;

import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLNetwork;

/**
 * Deterministic synthetic network builders shared by fixtures B, C and D.
 *
 * <p>All networks are built in memory with {@link NetworkUtils}; no Hong Kong
 * data and no absolute file paths are involved.</p>
 */
public final class SyntheticNetworks {

	private SyntheticNetworks() {
	}

	public static final Id<Node> A = Id.createNodeId("A");
	public static final Id<Node> B = Id.createNodeId("B");
	public static final Id<Node> C = Id.createNodeId("C");
	public static final Id<Node> D = Id.createNodeId("D");

	public static final Id<Link> L_AB = Id.createLinkId("L_AB");
	public static final Id<Link> L_BD = Id.createLinkId("L_BD");
	public static final Id<Link> L_AC = Id.createLinkId("L_AC");
	public static final Id<Link> L_CD = Id.createLinkId("L_CD");

	/**
	 * Two-node, one-link network.
	 * <pre>  A ---L_AB---> B </pre>
	 */
	public static Network twoNodeNetwork() {
		Network network = NetworkUtils.createNetwork();
		addNode(network, A, 0., 0.);
		addNode(network, B, 1000., 0.);
		addLink(network, L_AB, A, B, 1000., 20., 1800., "car");
		return network;
	}

	/**
	 * Four-node, four-link network offering exactly two A-&gt;D routes.
	 * <pre>
	 *   route1 (short/fast): A --L_AB--&gt; B --L_BD--&gt; D   (2 x 1000 m  = 100 s free-flow)
	 *   route2 (long/slow):  A --L_AC--&gt; C --L_CD--&gt; D   (2 x 3000 m  = 300 s free-flow)
	 * </pre>
	 * Free-flow link times: L_AB = L_BD = 50 s, L_AC = L_CD = 150 s.
	 */
	public static Network twoRouteNetwork() {
		Network network = NetworkUtils.createNetwork();
		addNode(network, A, 0., 0.);
		addNode(network, B, 1000., 0.);
		addNode(network, C, 1000., 1000.);
		addNode(network, D, 2000., 0.);
		addLink(network, L_AB, A, B, 1000., 20., 1800., "car");
		addLink(network, L_BD, B, D, 1000., 20., 1800., "car");
		addLink(network, L_AC, A, C, 3000., 20., 1800., "car");
		addLink(network, L_CD, C, D, 3000., 20., 1800., "car");
		return network;
	}

	/**
	 * Two-node multimodal network: the single link permits both {@code car} and
	 * {@code pt}. Used by fixture D.
	 */
	public static Network carPtNetwork() {
		Network network = NetworkUtils.createNetwork();
		addNode(network, A, 0., 0.);
		addNode(network, D, 2000., 0.);
		addLink(network, L_AB, A, D, 2000., 20., 1800., "car", "pt");
		return network;
	}

	public static CNLNetwork cNLNetwork(Network network) {
		return new CNLNetwork(network);
	}

	public static void addNode(Network network, Id<Node> id, double x, double y) {
		network.addNode(network.getFactory().createNode(id, new Coord(x, y)));
	}

	public static Link addLink(Network network, Id<Link> id, Id<Node> from, Id<Node> to,
			double length, double freespeed, double capacity, String... modes) {
		Link link = network.getFactory().createLink(id,
				network.getNodes().get(from), network.getNodes().get(to));
		link.setLength(length);
		link.setFreespeed(freespeed);
		link.setCapacity(capacity);
		link.setNumberOfLanes(1.);
		Set<String> modeSet = new HashSet<>(Arrays.asList(modes));
		link.setAllowedModes(modeSet);
		network.addLink(link);
		return link;
	}
}
