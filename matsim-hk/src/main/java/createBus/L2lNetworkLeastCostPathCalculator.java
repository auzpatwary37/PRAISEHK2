package createBus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.ImaginaryNode;
import org.matsim.core.router.InitialNode;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.facilities.Facility;
import org.matsim.vehicles.Vehicle;

public class L2lNetworkLeastCostPathCalculator implements LeastCostPathCalculator, BusPathCalculator {

	private final Network network;
	private final Network invertedNetwork;
	private final LeastCostPathCalculator dijkstra;

	private Id<Link> fromLinkId;
	private Id<Link> toLinkId;

	public L2lNetworkLeastCostPathCalculator(Network network, Network invertedNetwork,
			LeastCostPathCalculator delegate) {
		this.network = network;
		this.invertedNetwork = invertedNetwork;
		this.dijkstra = delegate;
	}

	public void initBeforeCalcRoute(Facility fromFacility, Facility toFacility) {
		fromLinkId = fromFacility.getLinkId();
		toLinkId = toFacility.getLinkId();
	}

	@Override
	public Path calcLeastCostPath(Node fromNode, Node toNode, double starttime, Person person, Vehicle vehicle) {
		/*
		if (fromNode instanceof ImaginaryNode) {
			Collection<? extends InitialNode> initialNodes = ((ImaginaryNode) fromNode).initialNodes;
			for (InitialNode node: initialNodes) {
				
			}
			Id<Node> nodeId = fromNode.getId();
		}*/
		return calcLeastCostPath(fromLinkId, toLinkId, starttime, person, vehicle);
	}

	@Override
	public Path calcLeastCostPath(Link fromLink, Link toLink, double starttime, Person person, Vehicle vehicle) {
		return calcLeastCostPath(fromLink.getId(), toLink.getId(), starttime, person, vehicle);
	}
	
	public Path calcLeastCostPath(Id<Link> fromLinkId, Id<Link> toLinkId, double starttime, Person person, Vehicle vehicle) {
		// ignore fromNode and toNode
		Node fromInvNode = this.invertedNetwork.getNodes().get(Id.create(fromLinkId, Node.class));
		Node toInvNode = this.invertedNetwork.getNodes().get(Id.create(toLinkId, Node.class));

		Path invPath = dijkstra.calcLeastCostPath(fromInvNode, toInvNode, starttime, person, vehicle);
		if (invPath == null) {
			throw new RuntimeException("No route found on inverted network from link " + fromLinkId + " to link "
					+ toLinkId + ".");
		}

		return invertPath(invPath);
	}

	private Path invertPath(Path invPath) {
		int invLinkCount = invPath.links.size();// ==> normal node count

		// path search is called only if fromLinkId != toLinkId
		// see: org.matsim.core.router.NetworkRoutingModule.routeLeg()
		// implies: fromInvNode != toInvNode
		if (invLinkCount == 0) {
			throw new RuntimeException("The path in the inverted network should consist of at least one link.");
		}

		List<Link> links = new ArrayList<>(invLinkCount - 1);
		for (int i = 1; i < invLinkCount; i++) {
			Id<Link> linkId = Id.create(invPath.nodes.get(i).getId(), Link.class);
			links.add(network.getLinks().get(linkId));
		}

		List<Node> nodes = new ArrayList<>(invLinkCount);
		// nodes.add(links.get(0).getFromNode());
		/*
		 * use the first link of the inverted path instead of the first node of the just
		 * created link list. also works for invLinkCount 1. theresa, jan'17
		 */
		nodes.add(network.getNodes().get(Id.create(invPath.links.get(0).getId(), Node.class)));
		for (Link l : links) {
			nodes.add(l.getToNode());
		}

		return new Path(nodes, links, invPath.travelTime, invPath.travelCost);
	}

}
