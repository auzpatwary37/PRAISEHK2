package networkFromSaturn;
/* Network creation from Saturn 
 * It is a function solely for create a network from Saturn
 * 
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
//The Matsim inputs
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsScenarioWriter;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalControlData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupsData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsDataFactory;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.contrib.signals.utils.SignalUtils;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup.SnapshotStyle;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt.utils.TransitScheduleValidator.ValidationResult;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.VehicleWriterV1;
import org.xml.sax.SAXException;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesFactory;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.lanes.LanesUtils;
import org.matsim.lanes.LanesWriter;

import com.google.common.collect.Lists;

import createBus.RunMinibus;
import createBus.Runbus;
import createMTR.CreateMTR;
import createPTGTFS.FareCalculatorPTGTFS;
import createPTGTFS.RunBusGTFS;
import createPTGTFS.RunFerryGTFS;
import dynamicTransitRouter.fareCalculators.ZonalFareCalculator;
import networkFromSaturn.pt.CreateFerry;
import networkFromSaturn.pt.CreateTram;

@SuppressWarnings("deprecation")
public class CreateNetwork {
	public static String coordinateSystem = "SATURN";
	private static final Logger log = Logger.getLogger(CreateNetwork.class);

	private static void createSignalAndLaneFromFile(String filepath, Lanes lanes, Network net, NetworkFactory fac,
			int id, SignalsData signals) throws IOException {
		File dat = new File(filepath);

		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(dat)));
		String line = reader.readLine();
		LanesFactory factory = lanes.getFactory();

		boolean started = false;
		int linkLeft = 0;
		int linkCount = 0; // To record how many links is to be added
		Id<Node> toNodeId = null; // To store the to node Id, a dummy variable is assigned to prevent error
		int NodeType = -1; // To store the node type

		SignalSystemsData systems = signals.getSignalSystemsData();
		SignalGroupsData groups = signals.getSignalGroupsData();
		SignalControlData control = signals.getSignalControlData();
		SignalSystemsDataFactory SSDfactory = systems.getFactory();

		int numberOfSignalStages = 0; // Number of signal stage
		int offsets = -1; // Relative offset of the traffic signal
		int cycleTime = -1; // Cycle time of the traffic signal
		int greenTime = -1;
		List<List<List<Integer>>> turns = null;
		List<LinkedHashMap<Integer, Integer>> satFlow_list = new ArrayList<LinkedHashMap<Integer, Integer>>(6);
		// ArrayList<LinkedHashMap<Integer, ArrayList<Integer>>> turns_map = null;
		List<Id<Link>> link_Id_list = new ArrayList<Id<Link>>(6);	//XXX this and below are stored and accessed in sequential order, unsafe? JLo
		List<Id<Node>> node_Id_list = new ArrayList<Id<Node>>(6);
		LinkAndLanes linkAndLanes = new LinkAndLanes();
		SignalGenerator signalgenerator = null;

		while (true) {
			line = reader.readLine(); // Open a new line

			if (line.substring(0, 5).equals("99999"))
				break; // Break when it reaches the end

			//START OF SIM NETWORK RECORDS
			if (!started) {
				if (line.substring(0, 5).equals("11111")) {
					started = true;
				}
				continue;
			//RECORD TYPE 2B - LINK SPEED FLOW DATA
			} else if (line.charAt(0) == '*') {
				continue;
			}

			//RECORD TYPE 1 - NODE DATA
			if (!line.substring(0, 5).equals("     ") && !line.substring(0, 5).equals("    0") && linkLeft == 0
					&& numberOfSignalStages == 0) {
				toNodeId = CreateNetworkUtils.getNodeId(line, 0, 5, id);
				
				// TODO: Breakpoint Remove it back.
//				if (toNodeId.toString().equals("503761")) {
//					System.nanoTime(); 
//				}

				linkLeft = CreateNetworkUtils.get_int(line, 6, 10);
				linkCount = linkLeft;
				NodeType = CreateNetworkUtils.get_int(line, 11, 15);
				if (NodeType == 3) {
					numberOfSignalStages = CreateNetworkUtils.get_int(line, 16, 20); // Get the number of signal stages,
																					// if it is signal node (NodeType=3)
					if (line.substring(21, 26).equals("     ")) {
						offsets = 0; // Assumes that if it is empty, offset is automatically null
					} else {
						offsets = CreateNetworkUtils.get_int(line, 21, 25);
					}
					if (line.length() >= 30)
						cycleTime = CreateNetworkUtils.get_int(line, 25, 30);
					else {
						cycleTime = CreateNetworkUtils.get_int(line, 25, line.length());
						log.warn("Error in the file: Node " + toNodeId);
					}
					greenTime = 0;
					linkAndLanes.clear();
				}
				turns = Lists.newArrayList();
				// turns_map = new ArrayList<LinkedHashMap<Integer, ArrayList<Integer>>>();
				// Clean up the link list for storage.
				link_Id_list.clear();
				node_Id_list.clear();
				satFlow_list.clear();
			}

			//RECORD TYPE 2 - LINK AND TURN DATA
			// This part is really constructing links
			else if (linkLeft > 0) {
				linkLeft--;

				// 0 is the exception that no lane from fromNode to toNode
				//FIXME if length == 11, this would break JLo
				if (line.length() <= 10 || line.substring(11, 15).equals("    ")
						|| CreateNetworkUtils.get_int(line, 11, 15) == 0) {
					link_Id_list.add(null);		//adding null as a spacer, cause this list is accessed in sequential method, and needs to be synced up with other lists
					node_Id_list.add(CreateNetworkUtils.getNodeId(line, 5, 10, id));
					turns.add(null);
					satFlow_list.add(null);
				} else {
					int lane_count = CreateNetworkUtils.get_int(line, 11, 15);
					Id<Node> fromNodeId = CreateNetworkUtils.getNodeId(line, 5, 10, id);
					Node fromNode = net.getNodes().get(fromNodeId);
					if (fromNode == null) { // WHY?
						System.out.println(fromNodeId + " not found.");
						link_Id_list.add(null);
						node_Id_list.add(fromNodeId);
						turns.add(null);
						continue;
					}

					// Create a dummy link
					Id<Link> lId = CreateNetworkUtils.getLinkId(fromNodeId, toNodeId);

					// Create the lane
					List<List<Integer>> turnslane = Lists.newArrayList(); // List to indicate which lane can turn to which
					LinkedHashMap<Integer, ArrayList<Integer>> Turnslane = new LinkedHashMap<Integer, ArrayList<Integer>>();	//XXX unused big map commented out, just duplicate of turnslane JLo
					LinkedHashMap<Integer, Integer> satCapList = new LinkedHashMap<Integer, Integer>();	//map to store saturn flow capacity JLo

					// Create a list of possible turn (In integer) for every lane
					for (int i = 0; i < lane_count; i++) {
						turnslane.add(new ArrayList<Integer>());
						Turnslane.put(i, new ArrayList<Integer>());
						satCapList.put(i, 0);
					}

					// Iterate through the items and store the turn data to the corresponding lane
					for(int curr_pos = 0; curr_pos<=linkCount; curr_pos++) {
						if (line.length() <= 34 + 10 * curr_pos) {
							break; // If this link cannot turn to any other link, just end it
						}

						// If this link is able to turn to other link, but cannot turn to current
						// position, just skip it.
						else if (line.substring(25 + 10 * curr_pos, 30 + 10 * curr_pos).equals("     ")
								|| line.substring(25 + 10 * curr_pos, 30 + 10 * curr_pos).equals("    0")) {
							continue;
						}

						int startlane = Character.getNumericValue(line.charAt(32 + 10 * curr_pos));
						int endlane = Character.getNumericValue(line.charAt(34 + 10 * curr_pos));
						int thisCapacity = Integer.parseInt(line.substring(25+10*curr_pos, 30+10*curr_pos).replace(" ", "")) / (endlane-startlane+1);	//saturn flow capacity

						if (startlane == -1 && endlane != -1) {
							startlane = 1;
							System.out.println(fromNodeId + " has and error " + startlane + " " + endlane);
						}

						// To calculate the actual link position means for the 'current position'
						int link_position = linkCount - linkLeft + curr_pos;
						if (link_position >= linkCount)
							link_position -= linkCount;
						
						// Store the data to the corresponding lane
						while (startlane <= endlane) {
							turnslane.get(startlane - 1).add(link_position);
							ArrayList<Integer> possible_turn = Turnslane.get(startlane - 1);
							possible_turn.add(link_position);
							Turnslane.put(startlane - 1, possible_turn);
							//putting the saturn saturation capacity in, taking the larger one. JLo	XXX
							satCapList.replace(startlane - 1, Math.max(satCapList.get(startlane - 1), thisCapacity));
							startlane++;
						}
					}

					// Add the node, link and turn information to the list for further construction
					// of network
					link_Id_list.add(lId);
					turns.add(turnslane);
					node_Id_list.add(fromNodeId);
					satFlow_list.add(satCapList);
					assert (turns.size() == link_Id_list.size() && turns.size() == node_Id_list.size());

					if (line.charAt(10) == '*') // skip the next line, which defines more specific link flow data not
												// using in this stage
						line = reader.readLine();
				}

				// For last iteration, and with more than one link, create lanes (and the
				// corresponding signal) for that.
				if (linkLeft == 0 && linkCount > 1) {

					// It is not necessary in every iteration, but the compiler want me to do so
					SignalSystemData sys = SSDfactory
							.createSignalSystemData(Id.create(toNodeId.toString(), SignalSystem.class));

					// Add to signal system data only if it is really a signal junction
					if (NodeType == 3) {
						systems.addSignalSystemData(sys);
					}

					int signalIdNumber = 1;

					for (int i = 0; i < link_Id_list.size(); i++) {
						Id<Link> curr_linkId = link_Id_list.get(i);
						if (curr_linkId == null)
							continue; // If it is a link without lane, just ignore it
						Link curr_link = net.getLinks().get(curr_linkId);
						if (curr_link == null) {
							reader.close();
							throw new NullPointerException("The link " + curr_linkId + " is not found");
						}
						if (turns.get(i).size() == 1 && turns.get(i).get(0).isEmpty()) {
							continue; // No possible turn, then don't create the lane.
						} else if (NodeType == 5) { // For roundabout, we make the turn to be free with limited capacity
							List<Integer> turnsForFirstLane = turns.get(i).get(0);
							for (int j = 0; j < turns.get(i).size(); j++) { // Copy the first lane information to other
								if (turns.get(i).get(j).isEmpty()) {
									turns.get(i).set(j, turnsForFirstLane);
								}
							}
						}

						LanesToLinkAssignment lanesForLink = factory.createLanesToLinkAssignment(curr_link.getId());
						lanes.addLanesToLinkAssignment(lanesForLink);

						List<Id<Lane>> laneIds = new LinkedList<Id<Lane>>();

						// Create the lane information for each LINK
						for (int j = 0; j < turns.get(i).size(); j++) {
							Id<Link>[] LinkIdArray = new Id[turns.get(i).get(j).size()];
							Id[] NodeIdArray = new Id[turns.get(i).get(j).size()]; // For store all the to nodes
																					// provided by the lane

							// Obtain the link connected to that lane
							for (int k = 0; k < turns.get(i).get(j).size(); k++) {
								// Obtain the id of the link that should be collected to.
								String to_Id = node_Id_list.get(turns.get(i).get(j).get(k)).toString();

								// The to node number become 'from' for the link to be turned to
								Id<Link> temp = Id.createLinkId(toNodeId.toString() + "_" + to_Id);
								NodeIdArray[k] = node_Id_list.get(turns.get(i).get(j).get(k));
								LinkIdArray[k] = temp;
							}

							// Create lane ID and store into the list
							Id<Lane> thislaneId = Id.create(curr_linkId.toString() + "_" + j, Lane.class);
							laneIds.add(thislaneId);

							// Ensure the lane length is shorter than link length, adjust to max-2 if it is
							// not shorter
							double thisLaneLength = CreateNetworkUtils.obtainLaneLength(curr_link.getLength());

							// Ensure the toLinks are on the network
							for (Id<Link> link : LinkIdArray) {
								if (net.getLinks().get(link) == null) {
									reader.close();
									throw new NullPointerException("The link " + link + " is not found");
								}
							}

							// Really create and add lane, the align is not as appropriate
							LanesUtils.createAndAddLane(lanesForLink, factory, thislaneId,
									CreateNetworkUtils.laneCapacity, thisLaneLength, j - 1, 1,
									Arrays.asList(LinkIdArray), null);
							//jam org saturation flow data in here JLo
							lanesForLink.getLanes().get(thislaneId).getAttributes().putAttribute("saturnSatFlow", satFlow_list.get(i).get(j));

							// Store the appropriate informations for signals creation later.
							if (NodeType == 3) {
								Id<Signal> SignalId = Id.create(signalIdNumber, Signal.class);
								// Create signal
								SignalUtils.createAndAddSignal(sys, SSDfactory, SignalId, curr_link.getId(),
										Arrays.asList(thislaneId));
								signalIdNumber++;

								// Store the signal for the control later
								linkAndLanes.add(link_Id_list.get(i), thislaneId, SignalId,
										net.getLinks().get(link_Id_list.get(i)).getFromNode().getId(), NodeIdArray);
								// Store the link and lane data for signal construction
							}
						}

						// Create the first lane.
						LanesUtils.createAndAddLane(lanesForLink, factory,
								Id.create(curr_linkId.toString() + ".ol", Lane.class),
								CreateNetworkUtils.laneCapacity * curr_link.getNumberOfLanes(), curr_link.getLength(),
								0, (int) curr_link.getNumberOfLanes(), null, laneIds);
					}
					//OLD implementation
//					signalgenerator = new SignalGenerator(signalIdNumber - 1, linkAndLanes.get_signal_IDs());
					
					//FIXME replacement JLo
					signalgenerator = new SignalGenerator(linkAndLanes.get_signal_IDs(), cycleTime, offsets);
				}

			}

			//RECORD TYPE 3 - SIGNAL DATA
			// This part is for insert traffic signals data
			else if (numberOfSignalStages != 0) {
				numberOfSignalStages--;
				int cur_pos = 0; // The current position of the signal information
				int end_pos1 = CreateNetworkUtils.get_int(line, 20, 25) / 2; // The end position calculated from given
																			// information
				int end_pos2 = (line.length() - 25) / 10; // The end position calculated from the line length
				int green_length = CreateNetworkUtils.get_int(line, 11, 15);
				int intergreen_length = CreateNetworkUtils.get_int(line, 16, 20);

				/*
				 * if(toNodeNumber == 101541){ int a = 0; cur_pos+=a; //Why? }
				 */

				List<Id<Signal>> temp = new ArrayList<Id<Signal>>();

				while (cur_pos < end_pos2) {
					Id<Node> signalFromNodeId = CreateNetworkUtils.getNodeId(line, 25 + cur_pos * 10, 30 + cur_pos * 10,
							id);
					Id<Node> signalToNodeId = CreateNetworkUtils.getNodeId(line, 30 + cur_pos * 10, 35 + cur_pos * 10,
							id);
					if (signalToNodeId.toString().substring(1).equalsIgnoreCase("00000")) { // For all directions
						temp.addAll(linkAndLanes.get_signal_IDs(signalFromNodeId));
					} else {
						temp.addAll(linkAndLanes.get_signal_IDs(signalFromNodeId, signalToNodeId));
					}
					cur_pos++;

					// If there are too much signals, we would go next line.
					if (end_pos1 > 5 && cur_pos == 5) {
						end_pos2 = end_pos1 - end_pos2;
						cur_pos = 0;
						line = reader.readLine();
					}
				}
				temp = temp.stream().distinct().collect(Collectors.toList());
				
				//OLD implementation
//				offsets = signalgenerator.add_green(greenTime, greenTime + green_length, cycleTime, offsets, temp);
//				greenTime = greenTime + green_length + intergreen_length;
//				if (numberOfSignalStages == 0) {
//					if(offsets< 0) {
//						offsets += cycleTime; //Ensure it is not negative.
//					}
//					signalgenerator.create_signals(Id.create(toNodeId.toString(), SignalSystem.class), groups, control,
//							cycleTime, offsets); //Create the signal in the end.
//				}
				
				//FIXME replacement for after while loop JLo
				signalgenerator.addStage(green_length, intergreen_length, temp);
				if (numberOfSignalStages == 0)
					signalgenerator.createSignals(Id.create(toNodeId.toString(), SignalSystem.class), groups, control);
			}
		}
		reader.close();

	}

	/**
	 * Combines link1 and link2 to form a new link from fromNode1 to toNode2,<br>
	 * replaces lane info going into link1, preserves lane info and updates signal of link2<br><p>
	 * 
	 * <b>DOES NOT</b> do a clean removal of the removeLinkIds<br>
	 * <b>DOES NOT</b> check the validity of the new link definition against replaced ones<br>
	 * <b>DOES NOT</b> remove the nodes: toNodeId1 fromNodeId2</p><p>
	 * 
	 * JLo Rewrote to NOT create a new lanes container every time to preserve info stored in attributes<br>
	 * past behaviour preserved to avoid lengthly edits<br>
	 * 
	 * @param net			: The network
	 * @param fromNode1 	: fromNode of link 1
	 * @param toNode1		:  toNode of link 1
	 * @param fromNode2		: fromNode of link 2
	 * @param toNod2		: toNode of link 2
	 * @param lane			: number of lane for the new link
	 * @param speed			: speed of the new link
	 * @param length		: length of the new link
	 * @param removeLinkIds	: Extra link ids to be removed
	 * @return Lanes 		: the org lanes container, preserve backwards compatibility
	 */
	private static Lanes combineLinks(Network net, SignalsData signalsdata, Lanes lanes, String fromNodeId1,
			String toNodeId1, String fromNodeId2, String toNodeId2, int numberOfLanes, double speed, double length,
			String... removeLinkIds) {
		
		//remove unrelated links first
		for(String TLS: removeLinkIds)
			net.removeLink(Id.createLinkId(TLS));	//only removing from net to preserve previous behaviour JLo
//			CreateNetworkUtils.removeLinkLane(net, lanes, signalsdata, Id.createLinkId(TLS));
		
		//get the org links
		Id<Link> linkId1 = CreateNetworkUtils.getLinkId(fromNodeId1, toNodeId1);
		Id<Link> linkId2 = CreateNetworkUtils.getLinkId(fromNodeId2, toNodeId2);
		
		//XXX DEBUG
//		if(net.getLinks().get(linkId2) != null) {
//			if(net.getLinks().get(linkId2).getNumberOfLanes() != numberOfLanes)
//				System.out.println(fromNodeId1+"_"+toNodeId2+" !numoflanes");
//		} else
//			System.out.println(linkId2+" !linkInNet");
		
		LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().remove(linkId2);
		
		//create the replacement link
		Link newLink = CreateNetwork.create_links(net, fromNodeId1, toNodeId2, numberOfLanes, speed, length);
		
		//replace lane info going into the new link
		JacobTest.TESTreplacel2lIntoOldLinkToNewLink(net, lanes, signalsdata, newLink, linkId1.toString(), true);
		
		//make the new lanes
		if(l2l!=null) {
			//ripped off from splitLink / JacobTest.combineLinks, made changes
			LanesFactory lf = lanes.getFactory();			//kept lf as it can keep old capacity
			LanesToLinkAssignment l2lForNewLink = lf.createLanesToLinkAssignment(newLink.getId());
			//Replace the link entry to old link to the new first link.
			for(Lane lane: l2l.getLanes().values()) {
				//Modify the laneIds to be the new link ID
				Id<Lane> laneId = Id.create(lane.getId().toString().replace(linkId2.toString(), newLink.getId().toString()), Lane.class);
				if(lane.getToLinkIds()==null) {
					List<Id<Lane>> toLaneIds = new ArrayList<>();
					for(Id<Lane> oldToLaneId: lane.getToLaneIds()) {
						toLaneIds.add(Id.create(oldToLaneId.toString().replace(linkId2.toString(), newLink.getId().toString()), Lane.class));
					}
					LanesUtils.createAndAddLane(l2lForNewLink, lf, laneId, lane.getCapacityVehiclesPerHour(), 
							length, lane.getAlignment(), (int) lane.getNumberOfRepresentedLanes(), 
							null, toLaneIds);
				}else {
					LanesUtils.createAndAddLane(l2lForNewLink, lf, laneId, lane.getCapacityVehiclesPerHour(), 
							CreateNetworkUtils.obtainLaneLength(length), lane.getAlignment(), (int) lane.getNumberOfRepresentedLanes(), 
							lane.getToLinkIds(), null);
					
					//swap the signal
					if(signalsdata.getSignalSystemsData().getSignalSystemData().containsKey(Id.create(toNodeId2, SignalSystem.class)))
						JacobTest.TESTreplaceLaneInSignal(signalsdata, laneId.toString(), lane.getId().toString());
				}
			}
	
			lanes.addLanesToLinkAssignment(l2lForNewLink);
		}
		
		//remove the other org link
		CreateNetworkUtils.removeLinkLane(net, lanes, signalsdata, linkId2);
		
		//remove un-needed nodes, org doesn't so preserving org behaviour
//		net.removeNode(Id.createNodeId(toNodeId1));
//		net.removeNode(Id.createNodeId(fromNodeId2));
		
		//return same lanes for backwards compatibility
		return lanes;
		
		/*	old implementation

		// Obtain the three signal datas.
		SignalSystemsData systems = signalsdata.getSignalSystemsData();

		// Remove the links
		
		net.removeLink(LinkId1);
		net.removeLink(LinkId2);
		for (String id : removeLinkIds) {
			net.removeLink(Id.createLinkId(id));
		}

		// Create the link
		create_links(net, fromNodeId1, toNodeId2, numberOfLanes, speed, length);
		Id<Link> newLinkId = CreateNetworkUtils.getLinkId(fromNodeId1, toNodeId2);

		
		//Do the works on lanes
		Lanes new_lanes = LanesUtils.createLanesContainer();
		LanesFactory factory = new_lanes.getFactory();

		for (LanesToLinkAssignment l2l : lanes.getLanesToLinkAssignments().values()) {
			// The new LanesToLinkAssignment to replace the old one
			LanesToLinkAssignment new_l2l = null;

			Id<Link> linkId = l2l.getLinkId();
			double laneLength;

			// Ignore the link Ids that are to be deleted
			if (Arrays.asList(removeLinkIds).contains(linkId.toString())) {
				continue;
			}

			// For normal case, the LaneToLinkAssignment is just the copy of the old one.
			if (!linkId.equals(LinkId1) && !linkId.equals(LinkId2)) {
				new_l2l = factory.createLanesToLinkAssignment(linkId);
				laneLength = CreateNetworkUtils.obtainLaneLength(net.getLinks().get(linkId).getLength());
			}

			// For the second link (The downstream link to be replaced): replace the link
			// information to the new link
			else if (linkId.equals(LinkId2)) {
				new_l2l = factory.createLanesToLinkAssignment(newLinkId);
				laneLength = CreateNetworkUtils.obtainLaneLength(net.getLinks().get(newLinkId).getLength());
			} else {
				continue;
			}

			// Analyse the lanes one by one, if there are lane that connected to a link to
			// be deleted, replace it to the link to be added
			for (Lane lane : l2l.getLanes().values()) {
				List<Id<Link>> toLinkIds = lane.getToLinkIds();
				// For the first link: replace the occurance of this link in other lanes
				// information
				if (toLinkIds != null && toLinkIds.contains(LinkId1)) {
					toLinkIds.remove(LinkId1);
					toLinkIds.add(newLinkId);
				}
				// Otherwise, just copy the detail
				if (lane.getToLaneIds() != null) {
					double linkLength = net.getLinks().get(new_l2l.getLinkId()).getLength(); // Obtain the appropriate
																								// length for first
																								// lane.
					LanesUtils.createAndAddLane(new_l2l, factory, lane.getId(), lane.getCapacityVehiclesPerHour(),
							linkLength, lane.getAlignment(), (int) lane.getNumberOfRepresentedLanes(), toLinkIds,
							lane.getToLaneIds());
				} else {
					LanesUtils.createAndAddLane(new_l2l, factory, lane.getId(), lane.getCapacityVehiclesPerHour(),
							laneLength, lane.getAlignment(), (int) lane.getNumberOfRepresentedLanes(), toLinkIds,
							lane.getToLaneIds());		//then the newLink will have incorrect toLaneIds as the org link has diff from node, then the incorrectness will get deleted by the lanes cleaner	JLo
				}
			}

			// Finally, append it to the new Lanes definition
			new_lanes.addLanesToLinkAssignment(new_l2l);
		}

		// This part is intended to correct the signal to the correct link.
		Map<Id<SignalSystem>, SignalSystemData> systemMap = systems.getSignalSystemData();
		for (Id<SignalSystem> SSID : systemMap.keySet()) {
			// Search only for the signal in toNode2 to modify.
			if (SSID.toString().equals(toNodeId2 + "")) {
				SignalSystemData ssd = systemMap.get(SSID);
				Map<Id<Signal>, SignalData> signalMap = ssd.getSignalData();

				// Search for the signal on the link LinkId2 to change it to newLinkId
				for (SignalData sd : signalMap.values()) {
					if (sd.getLinkId().equals(LinkId2)) {
						sd.setLinkId(newLinkId);
					}
				}
			}
		}

		return new_lanes;
		*/
	}

	public static Lanes combine_HK1_HK2(Network net, SignalsData signalsdata, Lanes lanes) {
		Lanes new_lanes = null;

		// Tung Lo Wan Road
		new_lanes = combineLinks(net, signalsdata, lanes, "208000", "208001", "102133", "101520", 1, 50 / 3.6, 300,
				"303075_303074", "206009_206010", "102416_102415", "102415_102417");

		// Causeway Road West Direction
		new_lanes = combineLinks(net, signalsdata, new_lanes, "207990", "206216", "101504", "101503", 3, 50 / 3.6, 500,
				"206216_207951", "101610_101504");

		// Causeway Road East Direction
		new_lanes = combineLinks(net, signalsdata, new_lanes, "101503", "101504", "206216", "207990", 2, 50 / 3.6, 500,
				"101504_101610", "207950_206216");

		// Island East Corridor East Direction
		new_lanes = combineLinks(net, signalsdata, new_lanes, "101570", "101560", "201140", "201137", 3, 80 / 3.6, 650,
				"101560_101564", "101564_101559");

		// Island East Corridor West Direction
		new_lanes = combineLinks(net, signalsdata, new_lanes, "201138", "201141", "101560", "101561", 3, 80 / 3.6, 450,
				"101557_101564", "101564_101560");

		// Victoria Park Road West Direction
		new_lanes = combineLinks(net, signalsdata, new_lanes, "101570", "101571", "209703", "208022", 3, 70 / 3.6, 360);

		// Victoria Park Road East Direction
		new_lanes = combineLinks(net, signalsdata, new_lanes, "208010", "209702", "101586", "105141", 3, 70 / 3.6, 150);

		// Knight Estate Road
		new_lanes = combineLinks(net, signalsdata, new_lanes, "101515", "101514", "206019", "206020", 1, 40 / 3.6, 440,
				"207038_206019");
		new_lanes = combineLinks(net, signalsdata, new_lanes, "206020", "206019", "101514", "101515", 1, 40 / 3.6, 440,
				"206019_207038");
		
		create_twoWay_links(net, new_lanes, "104202", "101669", 1, 30/3.6, 100); //Fixed the peak.

		return new_lanes;
	}

	public static Lanes combine_HK3(Network net, SignalsData signalsdata, Lanes lanes) {
		// Smithfield (with HK1)
		lanes = combineLinks(net, signalsdata, lanes, "101065", "101012", "302999", "302998", 2, 40 / 3.6, 780);
		lanes = combineLinks(net, signalsdata, lanes, "302998", "302999", "101012", "101065", 1, 50 / 3.6, 780);

		// Pok Fu Lam Road (with HK1)
		lanes = combineLinks(net, signalsdata, lanes, "101067", "101072", "302067", "302072", 3, 50 / 3.6, 310);
		lanes = combineLinks(net, signalsdata, lanes, "302072", "302067", "101072", "101067", 2, 50 / 3.6, 310);

		// Victoria Road (with HK1)
		lanes = combineLinks(net, signalsdata, lanes, "101032", "101031", "302031", "302018", 1, 30 / 3.6, 400);
		lanes = combineLinks(net, signalsdata, lanes, "302018", "302031", "101031", "101032", 1, 30 / 3.6, 400);

		// Wong Lai Chung Road (with HK1)
		lanes = combineLinks(net, signalsdata, lanes, "303201", "303200", "100717", "100716", 1, 50 / 3.6, 850);
		lanes = combineLinks(net, signalsdata, lanes, 
				"100716", "100717", "303200", "303201", 1, 40 / 3.6, 850);// Smaller because of uphill

		// Aberdeen Tunnel (with HK1)
		lanes = combineLinks(net, signalsdata, lanes, "101542", "101737", "303008", "303010", 2, 50 / 3.6,
				2135);
		lanes = combineLinks(net, signalsdata, lanes, "303010", "303008", "101737", "101542", 2, 50 / 3.6,
				2135);

		// Tai Tam Road (with HK2)
		create_links(net, "303300", "203171", 2, 40 / 3.6, 2850);
		create_links(net, "203171", "303300", 2, 40 / 3.6, 2850);
		
		net.getLinks().get(Id.createLinkId("303163_303180")).setLength(1100);
		CreateNetworkUtils.removeLinkLane(net, lanes, signalsdata, Id.createLinkId("303180_303163"));
		
		return lanes;
	}

	@SuppressWarnings("unchecked")
	public static Lanes conbine_NT(Network net, SignalsData signalsData, Lanes lanes) {
		Lanes new_lanes = null;
		// Lam Kam Road
		new_lanes = combineLinks(net, signalsData, lanes, "652620", "652621", "879400", "878200", 1, 40 / 3.6, 2980);
		new_lanes = combineLinks(net, signalsData, new_lanes, "878200", "879400", "652621", "652620", 1, 40 / 3.6,
				2980);

		// Tuen Mun Highway
		create_links(net, "879822", "988205", 3, 80 / 3.6, 40);
		create_links(net, "988205", "879822", 3, 80 / 3.6, 40);

		// Tsing Ma Bridge
		create_links(net, "982196", "a91030", 3, 50 / 3.6, 1000);
		create_links(net, "a91030", "982196", 3, 50 / 3.6, 1000);
		net.getLinks().get(Id.createLinkId("982192_982194")).setLength(800);

		// San Tin Highway & Castle Hill Highway
		create_links(net, "659834", "879834", 3, 100 / 3.6, 20);
		create_links(net, "879833", "659833", 3, 100 / 3.6, 20);
		create_links(net, "659832", "879832", 1, 50 / 3.6, 20);
		create_links(net, "879832", "659832", 1, 50 / 3.6, 20);
		create_links(net, "659835", "879835", 1, 50 / 3.6, 20);
		create_links(net, "879835", "659835", 1, 50 / 3.6, 20);

		// Fan Kam Road
		create_links(net, "659214", "879393", 1, 50 / 3.6, 3900);
		create_links(net, "879393", "659214", 1, 50 / 3.6, 3900);

		// Shing Mun Tunnel
		create_links(net, "983228", "651958", 2, 80 / 3.6, 3300);
		CreateNetworkUtils.addNextLinksToLane(new_lanes, net.getLinks().get(Id.createLinkId("983226_983228")), 
				0, 2, Id.createLinkId("983228_651958"));
		CreateNetworkUtils.addNextLinksToLane(new_lanes, net.getLinks().get(Id.createLinkId("983228_651958")), 
				0, 2, Id.createLinkId("651958_651947"));
		
		create_links(net, "651958", "983228", 2, 80 / 3.6, 3200);
		CreateNetworkUtils.addNextLinksToLane(new_lanes, net.getLinks().get(Id.createLinkId("651945_651958")), 
				0, 2, Id.createLinkId("651958_983228"));
		CreateNetworkUtils.addNextLinksToLane(new_lanes, net.getLinks().get(Id.createLinkId("651958_983228")), 
				0, 2, Id.createLinkId("983228_983226"));

		// Tai Lam Tunnel
		create_links(net, "988114", "879520", 3, 80 / 3.6, 1000);
		create_links(net, "879520", "988114", 3, 80 / 3.6, 1000);

		// Route Twisk
		create_links(net, "983205", "879410", 1, 40 / 3.6, 5800);
		create_links(net, "879410", "983205", 1, 40 / 3.6, 5800);

		// Clear Water Bay Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "503043", "503010", "763010", "761333", 2, 70 / 3.6,
				1500);
		new_lanes = combineLinks(net, signalsData, new_lanes, "761333", "763010", "503010", "503043", 2, 70 / 3.6,
				1500);

		// Po Lam Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "502022", "502020", "762020", "761188", 2, 50 / 3.6,
				1500);
		new_lanes = combineLinks(net, signalsData, new_lanes, "761188", "762020", "502020", "502022", 1, 50 / 3.6,
				1500);

		// TKO tunnel
		new_lanes = combineLinks(net, signalsData, new_lanes, "506350", "502000", "762020", "761158", 2, 70 / 3.6,
				1250);
		new_lanes = combineLinks(net, signalsData, new_lanes, "761158", "762001", "502001", "506351", 2, 70 / 3.6, 1250,
				"506321_506521");

		// Tsing Sha Highway
		create_links(net, "656434", "408371", 4, 80 / 3.6, 4000);
		create_links(net, "408371", "656434", 4, 80 / 3.6, 4000);

		// Tai Po Road
		create_links(net, "651952", "408716", 1, 40 / 3.6, 3000);
		create_links(net, "408716", "651952", 1, 40 / 3.6, 3000);

		// Lai King Hill Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "401421", "401558", "983428", "983423", 1, 50 / 3.6, 350,
				"983448_983436", "983436_983448");
		new_lanes = combineLinks(net, signalsData, new_lanes, "983423", "983428", "401558", "401421", 1, 50 / 3.6, 350);

		// Cargo Road
		create_links(net, "401554", "983436", 2, 40 / 3.6, 1400);
		create_links(net, "983436", "401554", 2, 40 / 3.6, 1400);

		// Tsing Kwai Highway
		new_lanes = combineLinks(net, signalsData, new_lanes, "401801", "401681", "983248", "983247", 4, 90 / 3.6,
				1000);
		new_lanes = combineLinks(net, signalsData, new_lanes, "983247", "983248", "401681", "401801", 5, 90 / 3.6,
				1000);

		// Kwai Chung Road
		create_links(net, "401680", "983437", 2, 80 / 3.6, 1300);
		create_links(net, "983121", "401680", 2, 80 / 3.6, 1300);
		CreateNetworkUtils.replaceLinkForLanes(new_lanes, Id.createLinkId("983121_983246"), Id.createLinkId("983121_401680"));

		// Castle Peak Road
		create_links(net, "401559", "983001", 2, 60 / 3.6, 700);
		create_links(net, "983001", "401559", 2, 60 / 3.6, 700);

		// Stone Cutting Island Bridge
		create_links(net, "982224", "408372", 3, 80 / 3.6, 600);
		create_links(net, "408372", "982225", 3, 80 / 3.6, 600);
		
		//Ching Shan Highway
		create_links(net, "876042", "988188", 2, 70 / 3.6, 5);
		create_links(net, "988188", "876042", 2, 70 / 3.6, 5);
		
		//Sai Sha Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "659011", "659013", "761212", "761210", 1, 50 / 3.6,
				210);
		new_lanes = combineLinks(net, signalsData, new_lanes, "761210", "761212", "659013", "659011", 1, 50 / 3.6,
				210);

		return new_lanes;
	}

	public static Lanes conbine_KLN(Network net, SignalsData signalsData, Lanes lanes) {
		Lanes new_lanes = null;
		
		// Lung Cheung Road
		new_lanes = combineLinks(net, signalsData, lanes, "401916", "401667", "502338", "502338", 3, 80 / 3.6, 1500);
		new_lanes = combineLinks(net, signalsData, new_lanes, "502334", "502339", "401667", "401916", 3, 80 / 3.6,
				1500);

		// Boundary Street
		new_lanes = combineLinks(net, signalsData, new_lanes, "401248", "401247", "502268", "502220", 3, 50 / 3.6, 285);
		new_lanes = combineLinks(net, signalsData, new_lanes, "502220", "502268", "401247", "401248", 3, 50 / 3.6, 285);

		//Fa Hui Road
		create_links(net, "401962", "502223", 1, 30 / 3.6, 150);
		CreateNetworkUtils.addNextLinkToLink(new_lanes, Id.createLinkId("401961_401962"), Id.createLinkId("401962_502223"));
		
		// Prince Edward Road West
		new_lanes = combineLinks(net, signalsData, new_lanes, "502218", "502217", "401243", "401242", 5, 50 / 3.6, 65,
				"401242_401243");

		// Argyle Street
		new_lanes = combineLinks(net, signalsData, new_lanes, "401205", "401206", "502064", "509010", 3, 50 / 3.6, 60);
		new_lanes = combineLinks(net, signalsData, new_lanes, "509010", "502064", "401206", "401205", 4, 50 / 3.6, 60);

		// Waterloo Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "401159", "405013", "502027", "502157", 3, 50 / 3.6, 30);
		new_lanes = combineLinks(net, signalsData, new_lanes, "502157", "502027", "405013", "401159", 3, 50 / 3.6, 30);

		// Ho Man Tin Hill Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "508850", "509166", "405015", "405016", 3, 40 / 3.6, 200);
		new_lanes = combineLinks(net, signalsData, new_lanes, "405016", "405015", "509166", "508850", 3, 40 / 3.6, 200);

		// CHT Kowloon
		// From Chatham's Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "401859", "401057", "501072", "501090", 3, 50 / 3.6, 30);

		// Cheong Wan Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "401054", "401056", "501075", "509162", 1, 45 / 3.6, 270);
		new_lanes = combineLinks(net, signalsData, new_lanes, "509162", "501075", "401056", "401054", 2, 45 / 3.6, 270);

		// Hung Hom Bypass
		new_lanes = combineLinks(net, signalsData, new_lanes, "401020", "401992", "508138", "508137", 2, 80 / 3.6, 400);
		new_lanes = combineLinks(net, signalsData, new_lanes, "508137", "508138", "402489", "401020", 3, 80 / 3.6, 400);

		// Shrawsburry Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "501091", "501036", "401058", "408380", 1, 50 / 3.6, 400);

		// CHT
		net.getLinks().get(Id.createLinkId("501035_501226")).setNumberOfLanes(2); //Correct the network.
		net.getLinks().get(Id.createLinkId("501035_501226")).setCapacity(CreateNetworkUtils.laneCapacity * 2);
		create_links(net, "501226", "101375", 2, 50 / 3.6, 100);
		Link CHT_NLink = create_links(net, "101375", "501226", 2, 50 / 3.6, 100);
		CreateNetworkUtils.addNextLinksToLane(new_lanes, net.getLinks().get(Id.createLinkId("101374_101375")), 
				0, 2, CHT_NLink.getId());

		// Chatham's Road North
		new_lanes = combineLinks(net, signalsData, new_lanes, "402408", "401068", "501187", "501128", 3, 55 / 3.6, 100);
		new_lanes = combineLinks(net, signalsData, new_lanes, "501127", "501129", "401079", "402440", 4, 55 / 3.6, 100);
		new_lanes = combineLinks(net, signalsData, new_lanes, "508102", "509164", "402442", "402440", 1, 60 / 3.6, 200);

		// Wille Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "401498", "401497", "509165", "501201", 1, 40 / 3.6, 100);
		new_lanes = combineLinks(net, signalsData, new_lanes, "501201", "509165", "401497", "401498", 1, 40 / 3.6, 100);

		// Chatham's Road North To Princess Road
		new_lanes = combineLinks(net, signalsData, new_lanes, "401076", "401075", "501131", "501175", 1, 60 / 3.6, 250);
		new_lanes = combineLinks(net, signalsData, new_lanes, "501176", "501089", "401074", "401081", 2, 60 / 3.6, 545);

		// Fat Kwong Street
		new_lanes = combineLinks(net, signalsData, new_lanes, "405016", "401497", "509166", "501201", 1, 60 / 3.6, 100);
		new_lanes = combineLinks(net, signalsData, new_lanes, "501201", "509166", "401497", "405016", 2, 60 / 3.6, 100);

		// Lion Rock Tunnel
		create_links(net, "502335", "651880", 2, 70 / 3.6, 100);
		create_links(net, "651880", "502340", 2, 70 / 3.6, 2850);
		new_lanes = combineLinks(net, signalsData, new_lanes, "502336", "502335", "502335", "651880", 2, 50 / 3.6, 2800,
				"502340_502333");
		new_lanes = combineLinks(net, signalsData, new_lanes, "651880", "502340", "502340", "502333", 3, 50 / 3.6,		//this was inputed incorrectly as 2 lanes JLo
				2670);
		CreateNetworkUtils.removeLinkLane(net, new_lanes, signalsData, Id.createLinkId("502337_502338"));
		CreateNetworkUtils.addNextLinkToLink(new_lanes, Id.createLinkId("651611_651880"), Id.createLinkId("651880_502333"));
		CreateNetworkUtils.removeNextLinkFromLink(new_lanes, Id.createLinkId("651611_651880"), Id.createLinkId("651880_651612"));
		
		CreateNetworkUtils.addNextLinksToLane(new_lanes, net.getLinks().get(Id.createLinkId("502336_651880")), 
				0, 2, Id.createLinkId("651880_651612"));

		// Tate's Cairn Tunnel
		create_links(net, "502309", "653210", 2, 70 / 3.6, 3858);
		create_links(net, "653210", "502310", 2, 70 / 3.6, 3600);
		CreateNetworkUtils.addNextLinkToLink(new_lanes, Id.createLinkId("653192_653210"), Id.createLinkId("653210_502310"));

		// WHC
		create_links(net, "401851", "101642", 3, 70 / 3.6, 100);
		create_links(net, "101642", "401851", 3, 70 / 3.6, 100);

		// EHC
		net.getLinks().get(Id.createLinkId("506320_506520")).setNumberOfLanes(2); //Correct the network.
		net.getLinks().get(Id.createLinkId("506320_506520")).setCapacity(CreateNetworkUtils.laneCapacity * 2);
		create_links(net, "506520", "209710", 2, 70 / 3.6, 1600);
		create_links(net, "209709", "506521", 2, 70 / 3.6, 1600);

		return new_lanes;
	}
	
	/**
	 * A function to fix the bugs in the network faced.
	 * @param net
	 * @param lanes
	 */
	@SuppressWarnings("unchecked")
	public static void networkHotFixForKowloonAndNT(Network net, Lanes lanes, SignalsData signalsData) {
		CreateNetworkUtils.removeLinkLane(net, lanes, signalsData, Id.createLinkId("983371_986023"), 
				Id.createLinkId("983374_983371"), Id.createLinkId("983013_983400"), Id.createLinkId("504289_504288"),
				Id.createLinkId("506321_504288"), Id.createLinkId("504288_504289"), Id.createLinkId("506321_504287"),
				Id.createLinkId("504287_504289"), Id.createLinkId("504289_504287"), Id.createLinkId("988001_983244"), 
				Id.createLinkId("875238_875203"), Id.createLinkId("502218_502223"), Id.createLinkId("875027_875052"), 
				Id.createLinkId("653032_653033"), Id.createLinkId("653033_653032"), Id.createLinkId("982063_982095"),
				Id.createLinkId("653285_653286"), Id.createLinkId("653284_653285"), Id.createLinkId("982063_982095"),
				Id.createLinkId("653364_653365"), Id.createLinkId("508195_508190"), Id.createLinkId("508190_508195"),
				Id.createLinkId("875217_876494"), Id.createLinkId("876494_875239"), Id.createLinkId("875239_875218"),
				Id.createLinkId("653107_653275"), Id.createLinkId("653275_653107"), Id.createLinkId("875239_875218"),
				Id.createLinkId("508391_508372"), Id.createLinkId("508372_508391"), Id.createLinkId("875239_875218"),
				Id.createLinkId("501482_501480"), Id.createLinkId("652144_652142"), Id.createLinkId("654108_652144"),
				Id.createLinkId("874150_876730"), Id.createLinkId("876732_874150"), Id.createLinkId("876750_876732"),
				Id.createLinkId("876731_874140"), Id.createLinkId("874140_876733"), Id.createLinkId("876733_876760"),
				Id.createLinkId("876760_876761"), Id.createLinkId("876751_876750"), Id.createLinkId("879871_879868")
				);
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("983241_983243"), Id.createLinkId("983243_988001"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("983083_983391"), Id.createLinkId("983391_983006"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("983392_983391"), Id.createLinkId("983391_983006"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("874130_874120"), Id.createLinkId("874120_879070"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("984021_986038"), Id.createLinkId("986038_986039"));
		create_links(net, "653368", "653365", 1, 40/3.6, 170);
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("653364_653368"), Id.createLinkId("653368_653365"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("653385_653368"), Id.createLinkId("653368_653365"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("653367_653368"), Id.createLinkId("653368_653365"));
		
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("653473_653475"), Id.createLinkId("653475_653476"));
		
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("504234_509685"), Id.createLinkId("509685_504234"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("653074_653075"), Id.createLinkId("653075_653074"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("654052_652100"), Id.createLinkId("652100_654055"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("761015_761186"), Id.createLinkId("761186_761015"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("761332_761302"), Id.createLinkId("761302_761303"));
		
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("983396_983397"), Id.createLinkId("983397_983396"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("876100_876090"), Id.createLinkId("876090_876081"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("876190_876090"), Id.createLinkId("876090_876081"));
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("876190_876090"), Id.createLinkId("876090_876081"));
		
		create_links(net, "983353", "983351", 1, 40/3.6, 200);
		CreateNetworkUtils.replaceLinkForLanes(lanes, Id.createLinkId("983353_983355"), Id.createLinkId("983353_983351"));
		
		CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("761304_761303"), Id.createLinkId("761303_761302"), 1);
		
		for(Link link: net.getLinks().values()) {
			if(link.getLength() <=1) {
				link.setLength(5);
			}else if(link.getFreespeed() <= 5) {
				link.setFreespeed(5);
			}
		}
		
		CreateNetworkUtils.replaceLinkForLanes(lanes, Id.createLinkId("983436_983448"), Id.createLinkId("983436_401554"));
		//CreateNetworkUtils.addNextLinkToLink(lanes, Id.createLinkId("101502_101503"), Id.createLinkId("101503_101502"));
		
		//Remove the dummy Central Kowloon Tunnel
		CreateNetworkUtils.removeLinkLane(net, lanes, signalsData, Id.createLinkId("406211_406210"), 
				Id.createLinkId("404025_406210"), Id.createLinkId("406210_406212"), Id.createLinkId("406213_406212"),
				Id.createLinkId("406212_406214"), Id.createLinkId("406216_405001"), Id.createLinkId("406216_406219"),
				Id.createLinkId("406214_404027"), Id.createLinkId("406214_406216"), Id.createLinkId("406216_406215"));
	}

	public static Link create_links(Network net, String fromNodeId, String toNodeId, int numberOfLanes,
			double FreeSpeed, double Length) {
		NetworkFactory fac = net.getFactory();
		Node fromNode = net.getNodes().get(Id.create(fromNodeId, Node.class));
		Node toNode = net.getNodes().get(Id.create(toNodeId, Node.class));
		Link l = fac.createLink(CreateNetworkUtils.getLinkId(fromNodeId, toNodeId), fromNode, toNode);
		l.setNumberOfLanes(numberOfLanes);
		l.setFreespeed(FreeSpeed); // Conversion from kph to mps
		l.setLength(Length);
		l.setCapacity(CreateNetworkUtils.laneCapacity * numberOfLanes); // This 2000 is determined with reference to the setting of BDTM
		l.getAttributes().putAttribute("type", "43");
		net.addLink(l);
		return l;
	}

	public static void createTunnels(Network network, Lanes lanes) {
		NetworkFactory fac = network.getFactory();
		CoordinateTransformation WGStoSaturn = new WGS84toSaturn();

		// Western Harbour Crossing
		Node whc_HK = network.getNodes().get(Id.createNodeId("101642"));
		Node whc_S = NetworkUtils.createAndAddNode(network, Id.createNodeId("WHCSouth"),
				WGStoSaturn.transform(new Coord(114.1596, 22.3042)));
		Node whc_N = NetworkUtils.createAndAddNode(network, Id.createNodeId("WHCNorth"),
				WGStoSaturn.transform(new Coord(114.1591, 22.3044)));
		Link whc_link = NetworkUtils.createAndAddLink(network, Id.createLinkId("WHC"), whc_N, whc_S, 10, 40 / 3.6,
				CreateNetworkUtils.laneCapacity * 3, 2);
		// whc_link.setAllowedModes(Sets.newHashSet("bus"));
		Link whcNLink = NetworkUtils.createAndAddLink(network, Id.createLinkId("WHCNorth"), whc_HK, whc_N, 1000,
				40 / 3.6, CreateNetworkUtils.laneCapacity * 3, 3);
		Link whcSLink = NetworkUtils.createAndAddLink(network, Id.createLinkId("WHCSouth"), whc_S, whc_HK, 1000,
				40 / 3.6, CreateNetworkUtils.laneCapacity * 3, 3);

		// Cross harbour tunnel
		Node CHT_HK = network.getNodes().get(Id.createNodeId("101375"));
		Node CHT_S = NetworkUtils.createAndAddNode(network, Id.createNodeId("CHTSouth"),
				WGStoSaturn.transform(new Coord(114.1807, 22.3028))); // Southbound
		Node CHT_N = NetworkUtils.createAndAddNode(network, Id.createNodeId("CHTNorth"),
				WGStoSaturn.transform(new Coord(114.1802, 22.3028))); // Northbound
		Link CHT = NetworkUtils.createAndAddLink(network, Id.createLinkId("CHT"), CHT_N, CHT_S, 10, 40 / 3.6,
				CreateNetworkUtils.laneCapacity * 2 * 1.3, 2);
		// CHT.setAllowedModes(Sets.newHashSet("bus"));
		Link CHT_NLink = NetworkUtils.createAndAddLink(network, Id.createLinkId("CHTNorth"), CHT_HK, CHT_N, 1000,
				40 / 3.6, CreateNetworkUtils.laneCapacity * 2 * 1.3, 3);
		Link CHT_SLink = NetworkUtils.createAndAddLink(network, Id.createLinkId("CHTSouth"), CHT_S, CHT_HK, 1000,
				40 / 3.6, CreateNetworkUtils.laneCapacity * 2 * 1.3, 3);
		network.getLinks().get(Id.createLinkId("101374_101375")).setCapacity(CreateNetworkUtils.laneCapacity * 2 * 1.3);
		network.getLinks().get(Id.createLinkId("101373_101374")).setCapacity(CreateNetworkUtils.laneCapacity * 2 * 1.3);
		
		CreateNetworkUtils.addNextLinksToLane(lanes, network.getLinks().get(Id.createLinkId("101374_101375")), 
				0, 2, CHT_NLink.getId());
		for(Lane lane: lanes.getLanesToLinkAssignments().get(Id.createLinkId("101374_101375")).getLanes().values()) {
			lane.setCapacityVehiclesPerHour(lane.getNumberOfRepresentedLanes() * CreateNetworkUtils.laneCapacity * 1.3);
		}
		for(Lane lane: lanes.getLanesToLinkAssignments().get(Id.createLinkId("101373_101374")).getLanes().values()) {
			lane.setCapacityVehiclesPerHour(lane.getNumberOfRepresentedLanes() * CreateNetworkUtils.laneCapacity * 1.3);
		}
		

		// Eastern Harbour Crossing
		Node EHCT_HK1 = network.getNodes().get(Id.createNodeId("209709"));
		Node EHCT_HK2 = network.getNodes().get(Id.createNodeId("209710"));
		Node EHCT_S = NetworkUtils.createAndAddNode(network, Id.createNodeId("EHCSouth"),
				WGStoSaturn.transform(new Coord(114.2333, 22.3005)));
		Node EHCT_N = NetworkUtils.createAndAddNode(network, Id.createNodeId("EHCNorth"),
				WGStoSaturn.transform(new Coord(114.2330, 22.3008)));
		Link EHCT = NetworkUtils.createAndAddLink(network, Id.createLinkId("EHC"), EHCT_N, EHCT_S, 10, 40 / 3.6,
				CreateNetworkUtils.laneCapacity * 2, 2);

		Link EHCT_NLink = NetworkUtils.createAndAddLink(network, Id.createLinkId("EHCNorth"), EHCT_HK1, EHCT_N, 2400,
				40 / 3.6, CreateNetworkUtils.laneCapacity * 2, 3);
		Link EHCT_SLink = NetworkUtils.createAndAddLink(network, Id.createLinkId("EHCSouth"), EHCT_S, EHCT_HK2, 2400,
				40 / 3.6, CreateNetworkUtils.laneCapacity * 2, 3);
	}

	public static void createNodeLinkLane(String coorFile, String datFile, String coordinateSystem, Network net,
			Lanes lanes, SignalsData signalsData, int id) throws IOException {
		NetworkFactory fac = net.getFactory();
		NodeAndLinkCreator.createNodesFromFiles(coorFile, net, fac, id, coordinateSystem);
		NodeAndLinkCreator.createLinksFromFile(datFile, net, fac, id);
		NodeAndLinkCreator.createBufferLinksFromFile(datFile, net, id);
		createSignalAndLaneFromFile(datFile, lanes, net, fac, id, signalsData);
	}

	private static void create_twoWay_links(Network net, Lanes lanes, String fromNodeId, String toNodeId, int numLanes,
			double maxSpeed, double length) {
		Link link1 = create_links(net, fromNodeId, toNodeId, numLanes, maxSpeed, length);
		Link link2 = create_links(net, toNodeId, fromNodeId, numLanes, maxSpeed, length);
		if(lanes != null) { //Add the new created link to the lanes to ensure vehicles can go in.
			for(Link link: net.getNodes().get(Id.createNodeId(fromNodeId)).getInLinks().values()) {
				LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(link.getId());
				if(l2l!=null) {
					for(Lane lane: l2l.getLanes().values()) {
						if(lane.getToLinkIds()!=null)
							lane.addToLinkId(link1.getId());
					}
				}
			}
			for(Link link: net.getNodes().get(Id.createNodeId(toNodeId)).getInLinks().values()) {
				LanesToLinkAssignment l2l = lanes.getLanesToLinkAssignments().get(link.getId());
				if(l2l!=null) {
					for(Lane lane: l2l.getLanes().values()) {
						if(lane.getToLinkIds()!=null)
							lane.addToLinkId(link2.getId());
					}
				}
			}
		}
	}

	public static void createMinibusLink(Network net) {
		// For Collinson Cope
		create_twoWay_links(net, null, "206126", "303180", 1, 40 / 3.6, 220);
		Node collinsonCope1 = NetworkUtils.createAndAddNode(net, Id.createNodeId("310283"), new Coord(43188, 13612));
		Node collinsonCope2 = NetworkUtils.createAndAddNode(net, Id.createNodeId("310284"), new Coord(43566, 13414));
		Node collinsonCope3 = NetworkUtils.createAndAddNode(net, Id.createNodeId("310285"), new Coord(44172, 12575));
		Node collinsonCope4 = NetworkUtils.createAndAddNode(net, Id.createNodeId("310286"), new Coord(44164, 12436));
		create_twoWay_links(net, null,"303180", "310283", 1, 40 / 3.6, 800);
		create_twoWay_links(net, null,"310283", "310284", 1, 40 / 3.6, 800);
		create_twoWay_links(net, null,"310284", "310285", 1, 40 / 3.6, 1400);
		create_twoWay_links(net, null,"310285", "310286", 1, 40 / 3.6, 100);
		net.removeLink(Id.createLinkId("303163_303180"));
		create_links(net, "303180", "303163", 1, 30/3.6, 1100);
		
		// For Chum Hom Kok
		Node chumHomKok1 = NetworkUtils.createAndAddNode(net, Id.createNodeId("310287"), new Coord(38405, 8199));
		Node chumHomKok2 = NetworkUtils.createAndAddNode(net, Id.createNodeId("310288"), new Coord(38390, 8083));
		create_twoWay_links(net, null,"303154", "310287", 1, 40 / 3.6, 1100);
		create_twoWay_links(net, null,"310287", "310288", 1, 40 / 3.6, 100);
	}
	
	public static void outputFiles(String directory, Config config, Scenario scenario) {
		new File(directory).mkdirs();
		// Output network
		new NetworkWriter(scenario.getNetwork()).write(directory + "network.xml"); // Write to a network

		// Output lane
		config.network().setLaneDefinitionsFile(directory + "lane_definitions_v2.0.xml");

		// Set signal definiation files
		ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class)
				.setSignalSystemFile(directory + "signal_systems.xml");
		ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class)
				.setSignalGroupsFile(directory + "signal_groups.xml");
		ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class)
				.setSignalControlFile(directory + "signal_control.xml");

		// Output lanes
		LanesWriter writerDelegate = new LanesWriter(scenario.getLanes());
		writerDelegate.write(config.network().getLaneDefinitionsFile());

		// Output Signals
		SignalsScenarioWriter signalsWriter = new SignalsScenarioWriter();
		signalsWriter.setSignalSystemsOutputFilename(
				ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class)
						.getSignalSystemFile());
		signalsWriter.setSignalGroupsOutputFilename(
				ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class)
						.getSignalGroupsFile());
		signalsWriter.setSignalControlOutputFilename(
				ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class)
						.getSignalControlFile());
		signalsWriter.writeSignalsData(scenario);

		// //output transits
		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(directory + "transitSchedule.xml");
		new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(directory + "transitVehicles.xml");

		// Output config
		/**
		 * String configFile = "output/config.xml"; 
		 * ConfigWriter configWriter = new ConfigWriter(config); configWriter.write(configFile);
		 */
	}
	
	/**
	 * Create MTR, bus, minibus and ferry for HKI scenario
	 * @param lanes Lane Definition
	 * @param net Network
	 * @param scenario Scenario
	 * @param busFareCal Bus fare calculator
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws TransformerException
	 */
	public static void createHKIptAndSave(Scenario scenario, String inputPath, String savePath) 
			throws IOException, ParserConfigurationException, SAXException, TransformerException {
		ZonalFareCalculator busFareCal = new ZonalFareCalculator(scenario.getTransitSchedule());
		CreateMTR.run(scenario, coordinateSystem); // Insert the MTR

		RunBusGTFS.setFilePath(inputPath);
		RunFerryGTFS.setFilePath(inputPath);
		FareCalculatorPTGTFS fareBusGTFS = RunBusGTFS.createBusRoutes(scenario, true);
	    FareCalculatorPTGTFS fareFerryGTFS = RunFerryGTFS.createFerries(scenario, true);
//		Runbus.createCrossHarbourBusRoutes(scenario, busFareCal, true); // Bus that cross harbour
//		Runbus.createHKIBusRoutes(scenario, busFareCal); // Bus inside HKI
		RunMinibus.createHKIMinibusRoutes(scenario, busFareCal); // Minibus

		//CreateFerry.create(net, scenario.getTransitSchedule(), scenario.getTransitVehicles());
		busFareCal.writeXMLV2(savePath+"busFare.xml");
		fareBusGTFS.printJSON(savePath+"busFareGTFS.json");
		fareFerryGTFS.printJSON(savePath+"ferryFareGTFS.json");
		//return busFareCal;
	}

	public static void main(String[] args)
			throws IOException, ParserConfigurationException, SAXException, UncheckedIOException, TransformerException {
		// Create empty config and network
		Config config = ConfigUtils.createConfig();
		// ConfigUtils.loadConfig(config, "data/config.xml");
		Scenario scenario = ScenarioUtils.createMutableScenario(config);
		Network net = scenario.getNetwork();
		NetworkFactory fac = net.getFactory();

		// Setup the config
		config.qsim().setUseLanes(true);
		ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class)
				.setUseSignalSystems(true);
		config.controler().setMobsim("qsim");
		config.controler().setLinkToLinkRoutingEnabled(true);
		config.qsim().setSnapshotStyle(SnapshotStyle.queue);
		config.network().setInputFile("data/network.xml");
		config.travelTimeCalculator().setCalculateLinkToLinkTravelTimes(true);

		SignalsData signalsData = SignalUtils.createSignalsData(
				ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class));

		Lanes lanes = LanesUtils.createLanesContainer();

		createNodeLinkLane("input/HKN coordinate.dat", "input/HKN.dat", coordinateSystem, net, lanes, signalsData, 1);
		createNodeLinkLane("input/HKE coordinate.dat", "input/HKE.dat", coordinateSystem, net, lanes, signalsData, 2);
		createNodeLinkLane("input/HKS coordinate.dat", "input/HKS.dat", coordinateSystem, net, lanes, signalsData, 3);
		createNodeLinkLane("input/K2 coordinate.dat", "input/K2.dat",
		coordinateSystem, net, lanes, signalsData, 5);
		createNodeLinkLane("input/NTE1 coordinate.dat", "input/NTE1.dat",
		coordinateSystem, net, lanes, signalsData, 6);
		createNodeLinkLane("input/NTE2 coordinate.dat", "input/NTE2.dat",
		coordinateSystem, net, lanes, signalsData, 7);
		createNodeLinkLane("input/NTW1 coordinate.dat", "input/NTW1.dat",
		coordinateSystem, net, lanes, signalsData, 8);
		createNodeLinkLane("input/NTW2 coordinate.dat", "input/NTW2.dat",
		coordinateSystem, net, lanes, signalsData, 9);
		createNodeLinkLane("input/NTW3 coordinate.dat", "input/NTW3.dat",
		coordinateSystem, net, lanes, signalsData, 10);
		createNodeLinkLane("input/K1 coordinate.dat", "input/K1.dat",
		coordinateSystem, net, lanes, signalsData, 4);

		lanes = combine_HK1_HK2(net, signalsData, lanes);
		lanes = combine_HK3(net, signalsData, lanes);
		lanes = conbine_NT(net, signalsData, lanes);
		lanes = conbine_KLN(net, signalsData, lanes);

		createTunnels(net, lanes);
		CreateTram.createTram(net, lanes, scenario, true);

		// Clean the Network
		new org.matsim.core.network.algorithms.NetworkCleaner().run(net);
		LaneAndSignals LAS = new LaneAndSignals(lanes, signalsData).cleanLanesAndSignals(config, net);

		((MutableScenario) scenario).setLanes(lanes); // Save the lane back to the scenario at the end

		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, LAS.getSignalsData()); // Add the modified signal to the
																						// scenario at the end.

		PopulationFactory popfac = PopulationUtils.getFactory();

		createHKIptAndSave(scenario, "PTGTFS/","output/");
		// CreateCWB.create(net, scenario.getLanes());

		// Valid the result
		ValidationResult vr = TransitScheduleValidator.validateAll(scenario.getTransitSchedule(), net);
		TransitScheduleValidator.printResult(vr);

		outputFiles("output/", config, scenario);
	}
}
