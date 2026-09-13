package networkFromSaturn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidParameterException;
import java.util.ArrayList;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;

import com.google.common.collect.Sets;

public class NodeAndLinkCreator {
	public static void createNodesFromFiles(String filepath, Network net, NetworkFactory fac, int id,
			String coordinateSystem) throws IOException {
		File cor = new File(filepath);
		System.out.println("We got a file: " + cor);
		System.out.println("Does it exist? " + cor.exists());

		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cor)));
		String line = reader.readLine();

		// Create nodes one by one
		try {
			while (line != null) {
				if (line.charAt(0) != 'C') { // The line that is removed but somehow stored there.
					Coord coord = null;

					// Determine the coordinate to be used
					if (coordinateSystem == "SATURN")
						coord = new Coord(CreateNetworkUtils.get_int(line, 10, 15),
								CreateNetworkUtils.get_int(line, 15, 20));
					else if (coordinateSystem == "HK1980")
						coord = new Coord(CreateNetworkUtils.get_int(line, 10, 15) + 800000,
								CreateNetworkUtils.get_int(line, 15, 20) + 800000);
					else
						throw new InvalidParameterException("coordinateSystem should be either SATURN or HK1980, "
								+ coordinateSystem + " is found");

					Node n = fac.createNode(CreateNetworkUtils.getNodeId(line, 5, 10, id), coord);
					net.addNode(n);
				}
				line = reader.readLine();
			}
		} finally {
			reader.close();
		}
	}

	public static void createLinksFromFile(String filepath, Network net, NetworkFactory fac, int id)
			throws IOException {
		File dat = new File(filepath);
		System.out.println("We got a file: " + dat);
		System.out.println("Does it exist? " + dat.exists());

		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(dat)));
		String line = reader.readLine();

		boolean started = false;
		int LinkLeft = 0; // To record how many links yet to be added
		int linkCount = 0;

		Id<Node> toNodeId = null; // To store the to node Id, a dummy variable is assigned to prevent error
		Node toNode = null; // To store the from nodes later, but a dummy one is created to prevent error.

		while (true) {
			line = reader.readLine(); // Open a new line

			if (line.substring(0, 5).equals("99999"))
				break; // Break when it reaches the end

			if (!started) {
				if (line.substring(0, 5).equals("11111")) {
					started = true;
				}
				continue;
			} else if (line.charAt(0) == '*') {
				continue; //It means deleted record.
			}

			if (!line.substring(0, 5).equals("     ") && !line.substring(0, 5).equals("    0") && LinkLeft == 0) {
				toNodeId = CreateNetworkUtils.getNodeId(line, 0, 5, id);
				toNode = net.getNodes().get(toNodeId);
				LinkLeft = CreateNetworkUtils.get_int(line, 6, 10);
				linkCount = LinkLeft;
			}

			// This part is really constructing links
			else if (LinkLeft != 0) {
				LinkLeft--;

				// 0 is the exception that no lane from fromNode to toNode
				if (line.length() <= 10)
					continue;
				else if (line.substring(11, 15).equals("    ") || CreateNetworkUtils.get_int(line, 11, 15) == 0)
					continue;

				else {
					Id<Node> fromNodeId = CreateNetworkUtils.getNodeId(line, 5, 10, id);
					Node fromNode = net.getNodes().get(fromNodeId);

					Link l = fac.createLink(CreateNetworkUtils.getLinkId(fromNodeId, toNodeId), fromNode, toNode);

					// Notes: Here I used double to be the datatype, despite the raw data should
					// only be int
					l.setNumberOfLanes(CreateNetworkUtils.get_int(line, 11, 15));
					l.setFreespeed(CreateNetworkUtils.get_int(line, 15, 20) / 3.6); // Conversion from kph to mps
					if (line.length() > 24) {
						l.setLength(CreateNetworkUtils.get_int(line, 20, 25));
					}
					l.setCapacity(CreateNetworkUtils.get_int(line, 11, 15) * CreateNetworkUtils.laneCapacity);
					l.getAttributes().putAttribute("type", "43");

					boolean busOnlyLink = true;
					boolean processed = false;
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
						
						int laneCapacity = CreateNetworkUtils.get_int(line, 25 + 10 * curr_pos, 30 + 10 * curr_pos);
						if(laneCapacity > 0) {
							busOnlyLink = false;
							break;
						}else {
							processed = true;
						}
					}
					if(!processed) {
						busOnlyLink = false; //It may not be really false, but it is not specified as a bus only turn.
					}
					if(busOnlyLink) {
						l.setAllowedModes(Sets.newHashSet("bus"));
					}
					
					/**
					 * It is some suggested code to have a better capacity based on Saturn data, but
					 * because of time constraint, it is abandoned.
					 *
					 * if (NodeType!=0){ //No capacity for external nodes try{ l.setCapacity(
					 * get_int(line,26,30) * 1.1 );} //Conversion from pcu/h to veh/h
					 * catch(java.lang.NumberFormatException e){ System.out.println("Error B
					 * occured: " + line); l.setCapacity( get_int(line, 36,40) * 1.1 ); } } else{
					 * try{ l.setCapacity( get_int(line,26,30) * 1.1 ); //Conversion from pcu/h to
					 * veh/h }catch(java.lang.StringIndexOutOfBoundsException e){
					 * System.out.println("Error A occured: " + line); line = reader.readLine();
					 * continue; }
					 * 
					 * }
					 **/
					net.addLink(l);
					if (line.charAt(10) == '*') // skip the next line, which defines more specific link flow data not
												// using in this stage
						line = reader.readLine();
				}
			}
		}
		reader.close();
	}

	public static void createBufferLinksFromFile(String filepath, Network net, int id) throws IOException {
		NetworkFactory fac = net.getFactory();
		File dat = new File(filepath);

		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(dat)));
		String line = reader.readLine();

		boolean started = false;
		int LinkLeft = 0; // To record how many links yet to be added

		while (true) {
			line = reader.readLine(); // Open a new line

			if (started && (line.substring(0, 5).equals("99999") || line.substring(0, 5).equals("44444")))
				break; // Break when it reaches the end

			if (!started) {
				if (line.substring(0, 5).equals("33333")) {
					started = true;
					reader.readLine(); // Skip a line just after 33333
				}
				continue;
			} else if (line.charAt(0) == '*') {
				continue;
			}

			if (line.contains("C") || line.contains("D")) {
				continue; // Ship the zones, that is specified by C
			}

			Id<Node> fromNodeId = CreateNetworkUtils.getNodeId(line, 5, 10, id);
			Node fromNode = net.getNodes().get(fromNodeId);

			Id<Node> toNodeId = CreateNetworkUtils.getNodeId(line, 15, 20, id);
			Node toNode = net.getNodes().get(toNodeId);

			Id<Link> linkId = CreateNetworkUtils.getLinkId(fromNodeId, toNodeId);
			if (net.getLinks().containsKey(linkId)) {
				continue; // Some buffer link may have been created.
			} else {
				// Create a link and add it to network.
				Link l = fac.createLink(linkId, fromNode, toNode);

				// Notes: Here I used double to be the datatype, despite the raw data should
				// only be int
				l.setNumberOfLanes(CreateNetworkUtils.get_int(line, 35, 38));
				l.setFreespeed(40 / 3.6); // Conversion from kph to mps
				l.setLength(CreateNetworkUtils.get_int(line, 41, 45));
				l.setCapacity(CreateNetworkUtils.get_int(line, 35, 38) * CreateNetworkUtils.laneCapacity);
				l.getAttributes().putAttribute("type", "43");
				net.addLink(l);
			}
		}
		reader.close();
	}

}
