package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.VehicleType;



public abstract class AnalyticalModelNetwork implements Network{

	/**
	 *	A class to hold the modifiable network with analytical model links  
	 */
	protected Network network=ScenarioUtils.createScenario(ConfigUtils.createConfig()).getNetwork();
	/**
	 * Time slice start and end time
	 */
	
	public Network getNetwork() {
		return this.network;
	}
	/**
	 * this static method clones and create a new Node (MATSim Node interface) without altering the original node
	 * @param node
	 * @return new created node
	 */
	public static Node cloneNode(Node node,NetworkFactory netfac){
		//netfac = NetworkUtils.createNetwork().getFactory();
		Node outNode=netfac.createNode(node.getId(), node.getCoord());
		return outNode;
	}
	/**
	 * This method is here to add the transit vehicle overlay in each link 
	 * to consider the congestion effect created by Transit Vehicles
	 * @param ts
	 */
	public abstract void addLinkTransitVolume(TransitSchedule ts);
	/**
	 * a Convenient method to create the network
	 */
	public abstract AnalyticalModelNetwork createNetwork(Network network);
	
	/**
	 * this method will clean the link NAN volumes of the network
	 */
	public abstract void clearLinkNANVolumes();
	/**
	 * this method will clean all the link volumes except the constant link transit vehicle volumes
	 */
	public abstract void clearLinkVolumesfull();
	
	/**
	 * for Cleaning the link car volume 
	 */
	public abstract void clearLinkCarVolumes();
	/**
	 * Clean the link transit passenger volume
	 */
	public abstract void clearLinkTransitPassangerVolume();
	
	public abstract Map<Id<Link>,Map<Id<VehicleType>,Double>> generateLinkVolumeProfile();
	
	
	public abstract void clearVehicleSpecificVolumeExceptTransit();
	
	/**
	 * ---------------------------wrapped Class Functions--------------------------------------------
	 * 
	 */


	
	@Override
	public Attributes getAttributes() {
		return this.network.getAttributes();
	}

	@Override
	public NetworkFactory getFactory() {
		return this.network.getFactory();
	}

	@Override
	public Map<Id<Node>, ? extends Node> getNodes() {
		return this.network.getNodes();
	}

	@Override
	public Map<Id<Link>,? extends Link> getLinks(){
		return this.network.getLinks();
	}

	@Override
	public double getCapacityPeriod() {
		return this.network.getCapacityPeriod();
	}

	@Override
	public double getEffectiveLaneWidth() {
		return this.network.getEffectiveLaneWidth();
	}

	@Override
	public void addNode(Node nn) {
		this.network.addNode(nn);
	}

	

	@Override
	public Node removeNode(Id<Node> nodeId) {
		return this.network.removeNode(nodeId);
	}

	@Override
	public Link removeLink(Id<Link> linkId) {
		return this.network.removeLink(linkId);
	}

	@Override
	public void setCapacityPeriod(double capPeriod) {
		this.network.setCapacityPeriod(capPeriod);
	}

	@Override
	public void setEffectiveCellSize(double effectiveCellSize) {
		this.setEffectiveCellSize(effectiveCellSize);
	}

	@Override
	public void setEffectiveLaneWidth(double effectiveLaneWidth) {
		this.network.setEffectiveLaneWidth(effectiveLaneWidth);
	}

	@Override
	public void setName(String name) {
		this.network.setName(name);
	}

	@Override
	public String getName() {
		return this.network.getName();
	}

	@Override
	public double getEffectiveCellSize() {
		return this.network.getEffectiveCellSize();
	}

	@Override
	public void addLink(Link ll) {
		this.network.addLink(ll);		
	}


}
