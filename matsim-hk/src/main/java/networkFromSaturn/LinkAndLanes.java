package networkFromSaturn;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.lanes.Lane;

/**
 * It is a help class to store the information of signal. The signal is
 * specified by id, and its link, lane, and direction is
 * store(fromNode-->toNode)
 * 
 * @author eleead
 *
 */
public class LinkAndLanes {
	private List<Id<Signal>> signal_list;
	private List<Id<Link>> link_list;
	private List<Id<Lane>> lane_list;
	private List<Id<Node>> from_node;
	private List<Id<Node>> to_node;

	public LinkAndLanes() {
		this.link_list = new ArrayList<Id<Link>>();
		this.lane_list = new ArrayList<Id<Lane>>();
		this.signal_list = new ArrayList<Id<Signal>>();
		this.from_node = new ArrayList<Id<Node>>();
		this.to_node = new ArrayList<Id<Node>>();
	}

	public void add(Id<Link> linkId, Id<Lane> laneId, Id<Signal> signalId, Id<Node> fromNodeId, Id<Node>... toNodeIds) {
		for (Id<Node> toNodeId : toNodeIds) {
			this.link_list.add(linkId);
			this.lane_list.add(laneId);
			this.from_node.add(fromNodeId);
			this.to_node.add(toNodeId);
			this.signal_list.add(signalId);
		}
	}

	public void clear() {
		this.link_list.clear();
		this.lane_list.clear();
		this.from_node.clear();
		this.to_node.clear();
		this.signal_list.clear();
	}

	public List<Id<Link>> get_link_IDs(Id<Node> fromNodeId, Id<Node> toNodeId) {
		List<Id<Link>> linkIds = new ArrayList<Id<Link>>();
		for (int i = 0; i < this.link_list.size(); i++) {
			if (this.from_node.get(i).equals(fromNodeId) && this.to_node.get(i).equals(toNodeId)) {
				linkIds.add(this.link_list.get(i));
			}
		}
		return linkIds;
	}

	public List<Id<Link>> get_link_IDs(Id<Node> fromNodeId) {
		List<Id<Link>> linkIds = new ArrayList<Id<Link>>();
		for (int i = 0; i < this.link_list.size(); i++) {
			if (this.from_node.get(i).equals(fromNodeId)) {
				linkIds.add(this.link_list.get(i));
			}
		}
		return linkIds;
	}

	public List<Id<Lane>> get_lane_IDs(Id<Node> fromNodeId, Id<Node> toNodeId) {
		List<Id<Lane>> laneIds = new ArrayList<Id<Lane>>();
		for (int i = 0; i < this.lane_list.size(); i++) {
			if (this.from_node.get(i).equals(fromNodeId) && this.to_node.get(i).equals(toNodeId)) {
				laneIds.add(this.lane_list.get(i));
			}
		}
		return laneIds;
	}

	public List<Id<Lane>> get_lane_IDs(Id<Node> fromNodeId) {
		List<Id<Lane>> laneIds = new ArrayList<Id<Lane>>();
		for (int i = 0; i < this.lane_list.size(); i++) {
			if (this.from_node.get(i).equals(fromNodeId)) {
				laneIds.add(this.lane_list.get(i));
			}
		}
		return laneIds;
	}

	public List<Id<Signal>> get_signal_IDs(Id<Node> fromNodeId, Id<Node> toNodeId) {
		List<Id<Signal>> signalIds = new ArrayList<Id<Signal>>();
		for (int i = 0; i < this.signal_list.size(); i++) {
			if (this.from_node.get(i).equals(fromNodeId) && this.to_node.get(i).equals(toNodeId)) {
				signalIds.add(this.signal_list.get(i));
			}
		}
		return signalIds;
	}

	public List<Id<Signal>> get_signal_IDs(Id<Node> fromNodeId) {
		List<Id<Signal>> signalIds = new ArrayList<Id<Signal>>();
		for (int i = 0; i < this.signal_list.size(); i++) {
			if (from_node.get(i).equals(fromNodeId)) {
				signalIds.add(this.signal_list.get(i));
			}
		}
		return signalIds;
	}

	public List<Id<Signal>> get_signal_IDs() {
		return this.signal_list;
	}
}
