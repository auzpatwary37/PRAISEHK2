package networkFromSaturn;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.core.network.NetworkUtils;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;

import com.google.common.collect.Sets;


public class CreateCWB {
	private static Link createLink(Network network, Node fromNode, Node toNode, int numOfLanes) {
		Link a = NetworkUtils.createAndAddLink(network,
				Id.createLinkId(fromNode.getId().toString() + "_" + toNode.getId().toString()), fromNode, toNode,
				NetworkUtils.getEuclideanDistance(fromNode.getCoord(), toNode.getCoord()), 70 / 3.6,
				numOfLanes * CreateNetworkUtils.laneCapacity, numOfLanes);
		return a;
	}

	private static void addLinkToLanesOfLink(Lanes lanes, Id<Link> lanesLink, Id<Link> linkToBeAdded) {
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(lanesLink);
		for (Lane lane : l2l.getLanes().values()) {
			if (!lane.getId().toString().contains("ol")) {
				lane.addToLinkId(linkToBeAdded);
			}
		}
	}

	@Deprecated
	/**
	 * It is the old one, which 
	 * @param network
	 * @param lanes
	 */
	public static void create(Network network, Lanes lanes) {
		// For central
		Node centralE = network.getNodes().get(Id.createNodeId(104113));
		Node centralW = network.getNodes().get(Id.createNodeId(104107));

		Node rumseyW = network.getNodes().get(Id.createNodeId(101207));
		Node rumseyE = NetworkUtils.getNearestNode(network, new Coord(34334, 16437));

		Node centralTunnelE = NetworkUtils.createAndAddNode(network, Id.createNodeId(120001), new Coord(34406, 16375));
		Node centralTunnelW = NetworkUtils.createAndAddNode(network, Id.createNodeId(120002), new Coord(34417, 16354));

		Link rumseyWLink = createLink(network, centralTunnelW, rumseyW, 2);
		Link rumseyELink = createLink(network, rumseyE, centralTunnelE, 2);

		Link centralWLink = createLink(network, centralTunnelW, centralW, 2);
		Link centralELink = createLink(network, centralE, centralTunnelE, 2);

		Node manKatE = network.getNodes().get(Id.createNodeId(104120));
		Link manKatChiuE = createLink(network, manKatE, centralE, 2);
		addLinkToLanesOfLink(lanes, Id.createLinkId("101212_104120"), manKatChiuE.getId());

		addLinkToLanesOfLink(lanes, Id.createLinkId("104105_104113"), centralELink.getId());
		addLinkToLanesOfLink(lanes, Id.createLinkId("109737_104104"), centralELink.getId());

		Node manChiu = network.getNodes().get(Id.createNodeId(104135));
		Link manChiuN = createLink(network, manChiu, centralE, 2);
		addLinkToLanesOfLink(lanes, Id.createLinkId("104107_104135"), manChiuN.getId());

		// Create road P2
		Node p2 = NetworkUtils.createAndAddNode(network, Id.createNodeId(120003), new Coord(35408, 15911));
		Node p2East = NetworkUtils.createAndAddNode(network, Id.createNodeId(120004), new Coord(35672, 15902));

		Link p2ELink = createLink(network, network.getNodes().get(Id.createNodeId(110003)), p2, 2); // Lung Wo Road to
																									// p2
		createLink(network, p2, p2East, 2);
		
		createLink(network, p2East, network.getNodes().get(Id.createNodeId(101468)), 2); // p2 to Meeting Road
		addLinkToLanesOfLink(lanes, Id.createLinkId("109992_110003"), p2ELink.getId());

		Link p2WLink = createLink(network, network.getNodes().get(Id.createNodeId(101468)), p2East, 2); // Meeting Road
																										// to p2
		createLink(network, p2East, p2, 2);
		createLink(network, p2, network.getNodes().get(Id.createNodeId(110002)), 2); // p2 to Lung Wo Road
		addLinkToLanesOfLink(lanes, Id.createLinkId("101471_101468"), p2WLink.getId());
		addLinkToLanesOfLink(lanes, Id.createLinkId("101658_101468"), p2WLink.getId());
		addLinkToLanesOfLink(lanes, Id.createLinkId("101475_101468"), p2WLink.getId());

		// Create road P11 and connect to P2
		Link p11LinkN = createLink(network, network.getNodes().get(Id.createNodeId(101442)), p2, 2);
		addLinkToLanesOfLink(lanes, Id.createLinkId("102814_101442"), p11LinkN.getId());
		addLinkToLanesOfLink(lanes, Id.createLinkId("110001_101442"), p11LinkN.getId());
		Link p11LinkS = createLink(network, p2, network.getNodes().get(Id.createNodeId(101442)), 2);

		// For Wan Chai
		Node wcTunnelW1 = NetworkUtils.createAndAddNode(network, Id.createNodeId(120005), new Coord(35825, 15903));
		Node wcTunnelW2 = NetworkUtils.createAndAddNode(network, Id.createNodeId(120006), new Coord(35600, 15930));
		Node wcTunnelE1 = NetworkUtils.createAndAddNode(network, Id.createNodeId(120007), new Coord(35600, 15936));
		Node wcTunnelE2 = NetworkUtils.createAndAddNode(network, Id.createNodeId(120008), new Coord(35825, 15910));
		createLink(network, wcTunnelE1, network.getNodes().get(Id.createNodeId(101656)), 2);
		createLink(network, p2East, wcTunnelE2, 2); // In the tunnel to eastern direction

		createLink(network, wcTunnelW1, p2East, 1); // Out the tunnel from western direction
		createLink(network, wcTunnelW1, network.getNodes().get(Id.createNodeId(101467)), 1);

		// For causeway bay
		Node cbTunnelW = NetworkUtils.createAndAddNode(network, Id.createNodeId(120009), new Coord(37213, 16204));
		Node cbTunnelE = NetworkUtils.createAndAddNode(network, Id.createNodeId(120010), new Coord(37213, 16214));
		Link cwbWLink = createLink(network, network.getNodes().get(Id.createNodeId(208010)), cbTunnelW, 1);
		addLinkToLanesOfLink(lanes, Id.createLinkId("209080_208010"), cwbWLink.getId());
		addLinkToLanesOfLink(lanes, Id.createLinkId("207004_208010"), cwbWLink.getId());

		// For North Point
		createLink(network, cbTunnelE, network.getNodes().get(Id.createNodeId(201137)), 3); // Eastward out
		Link npWLink = createLink(network, network.getNodes().get(Id.createNodeId(201138)), // Westward in
				cbTunnelW, 3);
		addLinkToLanesOfLink(lanes, Id.createLinkId("201143_201138"), npWLink.getId());

		// Connect the tunnels in between
		createLink(network, centralTunnelE, wcTunnelE1, 3);
		createLink(network, wcTunnelE1, wcTunnelE2, 3);
		createLink(network, wcTunnelE2, cbTunnelE, 3);

		createLink(network, cbTunnelW, wcTunnelW1, 3);
		createLink(network, wcTunnelW1, wcTunnelW2, 3);
		createLink(network, wcTunnelW2, centralTunnelW, 3);
	}
	
	/**
	 * Guessed most of the exits based on very first blueprints for the infrastructure.
	 * 8/11/2018
	 * @param net
	 * @param lanes
	 * @param sig
	 */
	@SuppressWarnings("unchecked")
	public static void JacobCWBPrototype(Network net, Lanes lanes, SignalsData sig) {
		
		//#1	central entrance
		Node CEnt1 = NetworkUtils.createAndAddNode(net, Id.createNodeId("107800"), new Coord(34380.9,16380.7));
		Node CEnt2 = NetworkUtils.createAndAddNode(net, Id.createNodeId("107810"), new Coord(34388.6,16395.5));
		Node CEnt3 = NetworkUtils.createAndAddNode(net, Id.createNodeId("107811"), new Coord(34313.9,16434.4));
		net.getNodes().get(Id.createNodeId("104104")).setCoord(new Coord(34352.5,16426.3));
		net.getNodes().get(Id.createNodeId("109737")).setCoord(new Coord(34078,16480.5));
		net.getNodes().get(Id.createNodeId("104107")).setCoord(new Coord(34128.1,16410.9));
		CreateNetworkUtils.removeLinkLane(net, lanes, sig, Id.createLinkId("104107_104135"), Id.createLinkId("104113_104135"), Id.createLinkId("104135_104132"));
		//Eastward
		Link ClinkE1 = CreateNetwork.create_links(net, "107811", "107800", 2, 70/3.6, 80);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE1, 0, 2, Id.createLinkId("107800_107801"));
		Link ClinkE2 = CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("104104")), CEnt2, 50, 60/3.6, Id.createLinkId("107810_107801"));
		Link ClinkE3 = CreateNetworkUtils.addSingleLaneLink(net, lanes, CEnt3, CEnt2, 80, 60/3.6, Id.createLinkId("107810_107801"));
		Link ClinkE4 = CreateNetwork.create_links(net, "109737", "107811", 2, 70/3.6, 243);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE4, 0, 2, ClinkE1.getId());
		Link ClinkE5 = net.getLinks().get(Id.createLinkId("109737_104104"));
		lanes.getLanesToLinkAssignments().remove(ClinkE5.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE5, 0, 1, Id.createLinkId("104104_104322"));
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE5, 1, 1, Id.createLinkId("104104_104322"), ClinkE2.getId());
		CreateNetworkUtils.setLinkLengthWithLane(net, lanes, ClinkE5.getId().toString(), 290);
		CreateNetworkUtils.setLinkLengthWithLane(net, lanes, "104104_104322", 138);
		Link ClinkE6 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("101190_109737"), 3);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE6, -1, 1, ClinkE5.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE6, 0, 1, ClinkE5.getId(), ClinkE4.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE6, 1, 1, ClinkE4.getId());
		Link ClinkE7 = net.getLinks().get(Id.createLinkId("101189_101190"));
		lanes.getLanesToLinkAssignments().remove(ClinkE7.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE7, 0, 3, ClinkE6.getId());
		Link ClinkE8 = CreateNetwork.create_links(net, "104135", "107811", 2, 50/3.6, 210);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE8, -1, 1, ClinkE3.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE8, 0, 1, ClinkE1.getId());
		Link ClinkE9 = CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("104120")), net.getNodes().get(Id.createNodeId("104135")), 155, 50/3.6, ClinkE8.getId());
		CreateNetworkUtils.removeLinkLane(net, lanes, sig, Id.createLinkId("101190_101194"));
		//Westward
		Link ClinkW1 = CreateNetwork.create_links(net, CEnt3.getId().toString(), "101207", 2, 70/3.6, 484);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW1, 0, 2, Id.createLinkId("101207_101189"));
		Link ClinkW2 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("104110_104109"), 3);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW2, 0, 2, Id.createLinkId("104109_101215"));
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW2, 1, 1, Id.createLinkId("104109_101194"));
		Link ClinkW3 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("104132_104110"), 2);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW3, 0, 2, ClinkW2.getId());
		Link ClinkW4 = CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("104107")), net.getNodes().get(Id.createNodeId("104110")), 88, 50/3.6, ClinkW2.getId(), Id.createLinkId("104110_104316"));
		Link ClinkW5 = CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("104112")), net.getNodes().get(Id.createNodeId("104107")), 40, 50/3.6, ClinkW4.getId());
		JacobTest.TESTmodifyNextLinksInLane(lanes, Id.createLinkId("104120_104112"), 0, ClinkW5.getId());
		Link ClinkW6 = CreateNetwork.create_links(net, CEnt3.getId().toString(), "104107", 2, 50/3.6, 220);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW6, -1, 1, Id.createLinkId("104107_104108"));
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW6, 0, 1, ClinkW4.getId(), Id.createLinkId("104107_104135"));
		Link ClinkW7 = CreateNetwork.create_links(net, CEnt1.getId().toString(), CEnt3.getId().toString(), 3, 70/3.6, 80);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW7, -1, 1, ClinkW6.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW7, 0, 1, ClinkW6.getId(), ClinkW1.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW7, 1, 1, ClinkW1.getId());
		Link ClinkW8 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("104112_104132"), 2);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW8, 0, 2, ClinkW3.getId());
		//Update: adj post-opening
		Link ClinkE10 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("104120_104112"), 2);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE10, -1, 1, ClinkW8.getId(), ClinkW5.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE10, 0, 1, ClinkW8.getId());
		Link ClinkE11 = net.getLinks().get(Id.createLinkId("101212_104120"));
		lanes.getLanesToLinkAssignments().remove(ClinkE11.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE11, -1, 1, ClinkE9.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE11, 0, 1, ClinkE10.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkE11, 1, 1, ClinkE10.getId(), Id.createLinkId("104120_104132"));
		Link ClinkC3 = CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("104132")), net.getNodes().get(Id.createNodeId("104107")), 10, 50/3.6, ClinkW4.getId());
		ClinkC3.setAllowedModes(Sets.newHashSet("bus"));		//someone forgot CTB_7 and 914X/P
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("104120_104132"), ClinkC3.getId());
		Link ClinkW9 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("104326_104302"), 3);
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkW9, 0, 3, Id.createLinkId("104302_104100"));
		//circulating
		Link ClinkC1 = net.getLinks().get(Id.createLinkId("104105_104113"));
		lanes.getLanesToLinkAssignments().remove(ClinkC1.getId());
//		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkC1, 0, 1, Id.createLinkId("104113_104135"));
		CreateNetworkUtils.addNextLinksToLane(lanes, ClinkC1, 0, 2, Id.createLinkId("104113_104115"));
//		Link ClinkC2 = CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("104135")), net.getNodes().get(Id.createNodeId("104113")), 150, 50/3.6, Id.createLinkId("104113_104135"), Id.createLinkId("104113_104115"));
//		CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("104113")), net.getNodes().get(Id.createNodeId("104135")), 150, 50/3.6, ClinkE8.getId(), ClinkC2.getId());
//		CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("104107")), net.getNodes().get(Id.createNodeId("104135")), 80, 50/3.6, ClinkE8.getId(), ClinkC2.getId());
		//Update: post-opening, access with permit only
		CreateNetworkUtils.removeLinkLane(net, lanes, sig, Id.createLinkId("104107_104135"), Id.createLinkId("104113_104135"));
		
		//#2	Wan Chai
		Node W1 = NetworkUtils.createAndAddNode(net, Id.createNodeId("107801"), new Coord(35532.6,15942));
		Node W2 = NetworkUtils.createAndAddNode(net, Id.createNodeId("107802"), new Coord(36066,15956.2));
		Node W3 = NetworkUtils.createAndAddNode(net, Id.createNodeId("107820"), new Coord(35765.1,15940.3));
		Node W4 = NetworkUtils.createAndAddNode(net, Id.createNodeId("107821"), new Coord(35642,15912.5));
		Node W5 = NetworkUtils.createAndAddNode(net, Id.createNodeId("107822"), new Coord(35945.6,15923.1));
		net.getNodes().get(Id.createNodeId("101467")).setCoord(new Coord(35862.3,15857.6));
		net.getNodes().get(Id.createNodeId("110004")).setCoord(new Coord(35402.6,15921.8));
		net.getNodes().get(Id.createNodeId("110005")).setCoord(new Coord(35607.6,15939.7));
		//Eastbound
		Link WlinkE1 = CreateNetwork.create_links(net, W1.getId().toString(), W2.getId().toString(), 2, 70/3.6, 537);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkE1, 0, 2, Id.createLinkId("107802_107804"));
		Link WlinkE2 = CreateNetworkUtils.addSingleLaneLink(net, lanes, W3, W2, 302, 60/3.6, Id.createLinkId("107802_107804"));
		Link WlinkE3 = CreateNetworkUtils.addSingleLaneLink(net, lanes, W1, net.getNodes().get(Id.createNodeId("101655")), 200, 60/3.6, Id.createLinkId("101655_101656"));
		Link WlinkE4 = CreateNetworkUtils.addSingleLaneLink(net, lanes, CEnt2, W1, 1256, 70/3.6, WlinkE3.getId());
		Link WlinkE5 = CreateNetwork.create_links(net, CEnt1.getId().toString(), W1.getId().toString(), 2, 70/3.6, 1256);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkE5, 0, 2, WlinkE1.getId());
		//P2
		CreateNetworkUtils.removeLinkLane(net, lanes, sig, Id.createLinkId("110005_110001"), Id.createLinkId("101467_101655"), Id.createLinkId("101655_101467"), Id.createLinkId("101467_101468"), Id.createLinkId("110003_110005"));
		Link WlinkP1 = CreateNetwork.create_links(net, "110004", "110001", 2, 50/3.6, 165);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP1, -1, 1, Id.createLinkId("110001_101442"));
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP1, 0, 1, Id.createLinkId("110001_101442"), Id.createLinkId("110001_101675"), Id.createLinkId("110001_101673"));
		Link WlinkP2 = CreateNetwork.create_links(net, W4.getId().toString(), "110004", 2, 50/3.6, 240);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP2, -1, 1, Id.createLinkId("110004_110002"), WlinkP1.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP2, 0, 1, Id.createLinkId("110004_110002"));
		Link WlinkP3 = CreateNetwork.create_links(net, W5.getId().toString(), W4.getId().toString(), 2, 50/3.6, 186);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP3, 0, 2, WlinkP2.getId());
		net.getLinks().get(Id.createLinkId("101465_101467")).setLength(295);
		net.getLinks().get(Id.createLinkId("101467_101465")).setLength(295);
		
		//Remove the signal 101467
		sig.getSignalControlData().getSignalSystemControllerDataBySystemId().remove(Id.create("101467", SignalSystem.class));
		sig.getSignalGroupsData().getSignalGroupDataBySignalSystemId().remove(Id.create("101467", SignalSystem.class));
		sig.getSignalSystemsData().getSignalSystemData().remove(Id.create("101467", SignalSystem.class));
		
		Link WlinkP4 = CreateNetworkUtils.addSingleLaneLink(net, lanes, W5, net.getNodes().get(Id.createNodeId("101467")), 113, 50/3.6, Id.createLinkId("101467_101465"));
		Link WlinkP5 = CreateNetwork.create_links(net, "101468", W5.getId().toString(), 2, 50/3.6, 90);
		JacobTest.TESTreplacel2lIntoOldLinkToNewLink(net, lanes, sig, WlinkP5, "101468_101467", true);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP5, -1, 1, WlinkP4.getId(), WlinkP3.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP5, 0, 1, WlinkP3.getId());
		Link WlinkP6 = CreateNetwork.create_links(net, W3.getId().toString(), "101468", 2, 50/3.6, 275);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP6, -1, 1, Id.createLinkId("101468_101658"), Id.createLinkId("101468_101475"));
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP6, 0, 1, Id.createLinkId("101468_101475"), Id.createLinkId("101468_101469"));
		Link WlinkP7 = CreateNetwork.create_links(net, "110004", W3.getId().toString(), 2, 50/3.6, 366);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP7, -1, 1, WlinkE2.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP7, 0, 1, WlinkP6.getId());
		Link WlinkP8 = CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("110005")), W3, 159, 50/3.6, WlinkE2.getId());
		Link WlinkP9 = CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("110005")), net.getNodes().get(Id.createNodeId("101655")), 135, 50/3.6, Id.createLinkId("101655_101656"));
		Link WlinkP10 = net.getLinks().get(Id.createLinkId("101656_101655"));
		lanes.getLanesToLinkAssignments().remove(WlinkP10.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP10, -1, 1, Id.createLinkId("101655_101687"));
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP10, 0, 1, Id.createLinkId("101655_101687"), Id.createLinkId("101655_101656"));
		Link WlinkP11 = CreateNetwork.create_links(net, "110003", "110005", 2, 50/3.6, 286.5);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP11, -1, 1, WlinkP9.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP11, 0, 1, WlinkP8.getId());
		Link WlinkP12 = CreateNetwork.create_links(net, "110003", "110004", 2, 50/3.6, 87);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP12, 0, 1, WlinkP1.getId(), WlinkP7.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP12, 1, 1, WlinkP1.getId());
		CreateNetworkUtils.setLinkLengthWithLane(net, lanes, "110004_110002", 154);
		Link WlinkP13 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("109994_110003"), 3);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP13, -1, 1, WlinkP11.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkP13, 0, 2, WlinkP12.getId());
		//Westward
		Link WlinkW1 = CreateNetwork.create_links(net, W1.getId().toString(), CEnt1.getId().toString(), 3, 70/3.6, 1333);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkW1, 0, 3, ClinkW7.getId());
		Link WlinkW2 = CreateNetwork.create_links(net, W2.getId().toString(), W1.getId().toString(), 3, 70/3.6, 561);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkW2, 0, 3, WlinkW1.getId());
		Link WlinkW3 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("109987_102580"), 2);
		WlinkW3.setFreespeed(50/3.6);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkW3, -1, 1, Id.createLinkId("102580_101465"));
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkW3, 0, 1, Id.createLinkId("102580_101443"));
		Link WlinkW4 = CreateNetwork.create_links(net, W4.getId().toString(), "109987", 2, 50/3.6, 135);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkW4, 0, 2, WlinkW3.getId());		
		Link WlinkW5 = CreateNetwork.create_links(net, W2.getId().toString(), W4.getId().toString(), 2, 60/3.6, 527);
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkW5, 0, 1, WlinkW4.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, WlinkW5, 1, 1, WlinkW4.getId(), WlinkP2.getId());
		
		//#3	Causeway Bay
		//Tsing Fung St Ent
		Link TFlink1 = CreateNetworkUtils.addSingleLaneLink(net, lanes, net.getNodes().get(Id.createNodeId("208010")), W2, 1510, 70/3.6, WlinkW2.getId());
		JacobTest.TESTmodifyNextLinksInLane(lanes, Id.createLinkId("207004_208010"), 0, TFlink1.getId());
		Link TFlink2 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("24657_208010"), 2);
		CreateNetworkUtils.addNextLinksToLane(lanes, TFlink2, 0, 1, TFlink1.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, TFlink2, 1, 1, TFlink1.getId(), Id.createLinkId("208010_105141"));
		//Eastern corridor
		Node CB1 = CreateNetworkUtils.splitLink(net, lanes, sig, Id.createLinkId("201143_201138"), "107803", 260, 616, new Coord(37978.6,16979));
		Node CB2 = CreateNetworkUtils.splitLink(net, lanes, sig, Id.createLinkId("201137_201142"), "107804", 550, 350, new Coord(37989.7,17016.2));
		Link EClink1 = CreateNetwork.create_links(net, CB1.getId().toString(), W2.getId().toString(), 3, 70/3.6, 2191);
		CreateNetworkUtils.addNextLinksToLane(lanes, EClink1, -1, 1, WlinkW5.getId());
		CreateNetworkUtils.addNextLinksToLane(lanes, EClink1, 0, 2, WlinkW2.getId());
		Link EClink2 = net.getLinks().get(Id.createLinkId("201143_107803"));
		CreateNetworkUtils.addNextLinksToLane(lanes, EClink2, -1, 2, Id.createLinkId("107803_201138"));
		CreateNetworkUtils.addNextLinksToLane(lanes, EClink2, 0, 2, EClink1.getId());
		Link EClink3 = CreateNetwork.create_links(net, W2.getId().toString(), CB2.getId().toString(), 3, 70/3.6, 2297);
		CreateNetworkUtils.addNextLinksToLane(lanes, EClink3, 0, 3, Id.createLinkId("107804_201142"));
		Link EClink4 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("201137_107804"), 2);
		CreateNetworkUtils.addNextLinksToLane(lanes, EClink4, 0, 2, Id.createLinkId("107804_201142"));
		Link EClink5 = JacobTest.TESTrenumlaneLink(net, lanes, Id.createLinkId("101570_201137"), 2);
		CreateNetworkUtils.addNextLinksToLane(lanes, EClink5, 0, 2, EClink4.getId());
	}
}
