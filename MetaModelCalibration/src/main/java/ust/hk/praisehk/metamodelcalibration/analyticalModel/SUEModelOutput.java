package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class SUEModelOutput {
	
	private Map<String,Map<Id<Link>,Double>> linkVolume;
	private Map<String,Map<Id<TransitLink>,Double>> linkTransitVolume;
	
	private Map<String,Map<Id<Link>,Double>> linkTravelTime;
	private Map<String,Map<Id<TransitLink>,Double>>trLinkTravelTime;
	
	private Map<String,Map<Id<Link>,Double>> averagePtOccupancyOnLink;
	
	//private Map<String,Map<Id<TransitStopFacility>,Double>> smartCardEntry;
	
	private Map<String,Map<String,Double>>smartCardEntryAndExit;
	
	public SUEModelOutput(Map<String,Map<Id<Link>,Double>> linkVolume,Map<String,Map<Id<TransitLink>,Double>> linkTransitVolume,Map<String,Map<Id<Link>,Double>> linkTravelTime,Map<String,Map<Id<TransitLink>,Double>>trLinkTravelTime) {
		this.linkVolume=linkVolume;
		this.linkTransitVolume=linkTransitVolume;
		this.linkTravelTime=linkTravelTime;
		this.trLinkTravelTime=trLinkTravelTime;
	}

	public Map<String, Map<Id<Link>, Double>> getLinkVolume() {
		return linkVolume;
	}

	public Map<String, Map<Id<TransitLink>, Double>> getLinkTransitVolume() {
		return linkTransitVolume;
	}

	public Map<String, Map<Id<Link>, Double>> getLinkTravelTime() {
		return linkTravelTime;
	}

	public Map<String, Map<Id<TransitLink>, Double>> getTrLinkTravelTime() {
		return trLinkTravelTime;
	}

	public Map<String, Map<Id<Link>, Double>> getAveragePtOccupancyOnLink() {
		return averagePtOccupancyOnLink;
	}

	public void setAveragePtOccupancyOnLink(Map<String, Map<Id<Link>, Double>> averagePtOccupancyOnLink) {
		this.averagePtOccupancyOnLink = averagePtOccupancyOnLink;
	}

	

	public Map<String, Map<String, Double>> getSmartCardEntryAndExit() {
		return smartCardEntryAndExit;
	}

	public void setSmartCardEntryAndExit(Map<String, Map<String, Double>> smartCardEntryAndExit) {
		this.smartCardEntryAndExit = smartCardEntryAndExit;
	}
	
	
}
