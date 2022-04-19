package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.VehicleType;

/**
 *
 * @author Ashraf
 *
 */

public abstract class AnalyticalModelLink implements Link{

	/**
	 * This is a wrapper class around the Link Interface of MATSim
	 * the variable link is the Link that the class is wrapped around
	 * the variable LinkCarVolume is the volume of car/car using passenger on the link. (same as the PCU of car is 1)
	 * the variable LinkTransitVolume is the volume of the transit vehicles converted into PCU. 
	 * 		(This is a constant and will not change)
	 * the variable LinkTransitPassengerVolume is the amount of passenger on the transit on that link.(It is not a constant).
	 * link TravelTime is the time needed for crossing the link. 
	 */
	
	
	protected Link link;
	protected double linkCarVolume=0;
	protected double linkTransitVolume=0;
	protected double linkTransitPassenger=0;
	protected double linkTravelTime=0;
	protected double residualCarVolume = 0;
	protected final double residualCarVolumeThreshold;
	
	private double gcRatio=1;
	protected Map<Id<VehicleType>,Double> vehicleSpecificVolume=new HashMap<>(); 
	
	protected Map<Id<VehicleType>,Double> vehicleSpecificResedualVolume=new HashMap<>(); 
	
	protected Set<Id<VehicleType>> transitVehicles=new HashSet<>();
	
	/**
	 * Constructor
	 * @param network 
	 * @param link: The wrapped Link
	 */
	public AnalyticalModelLink(Link link, Network network) {
		Node fromNode = network.getNodes().get(link.getFromNode().getId());
		Node toNode = network.getNodes().get(link.getToNode().getId());
		this.link=network.getFactory().createLink(link.getId(), fromNode, toNode);
		this.link.setCapacity(link.getCapacity());
		this.link.setAllowedModes(link.getAllowedModes());
		this.link.setFreespeed(link.getFreespeed());
		this.link.setLength(link.getLength());
		this.link.setNumberOfLanes(link.getNumberOfLanes());
		this.residualCarVolumeThreshold = this.link.getCapacity();
		
	}
	
	
	/**
	 * -------------------------------Wrapper class functions---------------------------------------------------------------
	 * 
	 */
	
	/**
	 * Calculate the link Travel Time
	 * @return
	 * has to be overridden in the actual class
	 * Can Be BPR 
	 * 
	 */
	
	public abstract double getLinkTravelTime(Tuple<Double,Double> timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams);
	public void resetLinkVolume() {
		this.linkCarVolume=0;
		//this.linkTransitPassenger=0;
	}
	public void addLinkCarVolume(double lVolume) {
		this.linkCarVolume+=lVolume;
	}

	
	public void addLinkTransitVolume(double lVolume) {
		this.linkTransitVolume+=lVolume;
	}
	
	
	public double getLinkCarVolume() {
		return linkCarVolume;
	}
	public double getLinkAADTVolume() {
		return linkCarVolume+linkTransitVolume;
	}

	public double getLinkTransitVolume() {
		return linkTransitVolume;
	}

	
	public double getLinkTransitPassenger() {
		return linkTransitPassenger;
	}

	public void clearTransitPassangerFlow() {
		this.linkTransitPassenger=0;
	}
	
	public void clearLinkCarFlow() {
		this.linkCarVolume=0;
	}
	
	public void clearNANFlow() {
		if(this.linkCarVolume==Double.NaN) {
			this.linkCarVolume=0;
		}else if(this.linkTransitPassenger==Double.NaN) {
			this.linkTransitPassenger=0;
		}
	}
	/**
	 * -----------------------Wrapped Class Functions------------------------------------------------------
	 */
	@Override
	public Coord getCoord() {
		return link.getCoord();
	}

	@Override
	public Id<Link> getId() {
		return link.getId();
	}

	@Override
	public Attributes getAttributes() {
		return link.getAttributes();
	}

	@Override
	public boolean setFromNode(Node node) {
		return link.setFromNode(node);
	}

	@Override
	public boolean setToNode(Node node) {
		return link.setToNode(node);
	}

	@Override
	public Node getToNode() {
		return link.getToNode();
	}

	@Override
	public Node getFromNode() {
		return link.getFromNode();
	}

	@Override
	public double getLength() {
		return link.getLength();
	}

	@Override
	public double getNumberOfLanes() {
		return link.getNumberOfLanes();
	}

	@Override
	public double getNumberOfLanes(double time) {
		return link.getNumberOfLanes(time);
	}

	@Override
	public double getFreespeed() {
		return link.getFreespeed();
	}

	@Override
	public double getFreespeed(double time) {
		return link.getFreespeed(time);
	}

	@Override
	public double getCapacity() {
		return link.getCapacity();
	}

	@Override
	public double getCapacity(double time) {
		return link.getCapacity(time);
	}

	@Override
	public void setFreespeed(double freespeed) {
		link.setFreespeed(freespeed);
	}

	@Override
	public void setLength(double length) {
		link.setLength(length);
		
	}

	@Override
	public void setNumberOfLanes(double lanes) {
		link.setNumberOfLanes(lanes);
	}

	@Override
	public void setCapacity(double capacity) {
		link.setCapacity(capacity);
	}

	@Override
	public void setAllowedModes(Set<String> modes) {
		link.setAllowedModes(modes);
	}

	@Override
	public Set<String> getAllowedModes() {
		return link.getAllowedModes();
	}

	@Override
	public double getFlowCapacityPerSec() {
		return link.getFlowCapacityPerSec();
	}

	@Override
	public double getFlowCapacityPerSec(double time) {
		return link.getFlowCapacityPerSec(time);
	}


	public double getGcRatio() {
		return gcRatio;
	}


	public void setGcRatio(double gcRatio) {
		this.gcRatio = gcRatio;
	}

	public synchronized void addVehicleSpecificVolume(double volume, Id<VehicleType> vt, boolean ifTransitVehicle) {
		if(ifTransitVehicle) {
			this.transitVehicles.add(vt);
		}
		if(!this.vehicleSpecificVolume.containsKey(vt)) {
			this.vehicleSpecificVolume.put(vt, volume);
		}else {
			this.vehicleSpecificVolume.put(vt, this.vehicleSpecificVolume.get(vt)+volume);
		}
	}
	
	public void resetVehicleSpecificVolumeExceptTransit() {
		for(Id<VehicleType> vt:this.vehicleSpecificVolume.keySet()) {
			if(!this.transitVehicles.contains(vt)) {
				this.vehicleSpecificVolume.put(vt, 0.);
			}
		}
	}
	
	
	
	public Map<Id<VehicleType>, Double> getVehicleSpecificVolume() {
		return vehicleSpecificVolume;
	}
	
	
}
