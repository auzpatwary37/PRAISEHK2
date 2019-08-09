package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

public class SUEModelOutput {
	
	private Map<String,Map<Id<Link>,Double>> linkVolume;
	private Map<String,Map<Id<TransitLink>,Double>> linkTransitVolume;
	
	private Map<String,Map<Id<Link>,Double>> linkTravelTime;
	private Map<String,Map<Id<TransitLink>,Double>>trLinkTravelTime;
	
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
	
}
