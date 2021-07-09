package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;

/**
 * 
 * @author Ashraf
 *
 */
public class CNLLink extends AnalyticalModelLink{
	
	/**
	 * a new parameter is required to store the link passenger volume by route and line Id 
	 * a HashMap<String, double> Has to be created with the String lineId_routeId as key and passenger volume as value 
	 */
	
	private ConcurrentHashMap<String, Double> TransitMapping=new ConcurrentHashMap<>();
	private ConcurrentHashMap<String,Set<Id<TransitLink>>> transitDirectLinks = new ConcurrentHashMap<>();
	
	private double alpha=0.15;
	private double beta=4;
	
	/**
	 * 
	 * @return: value of alpha in the BPR function
	 */
	public double getAlpha() {
		return alpha;
	}

	/**
	 * Set the value of alpha in the BPR function
	 * @param alpha
	 */
	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}
	
	/**
	 * 
	 * @return: value of beta in the BPR function 
	 */

	public double getBeta() {
		return beta;
	}

	/**
	 * Set the value of beta in the BPR function
	 * @param beta
	 */
	public void setBeta(double beta) {
		this.beta = beta;
	}


	/**
	 * Constructor
	 * @param link
	 * @param network 
	 */
	public CNLLink(Link link, Network network) {
		super(link,network);
	}
	
	@Override
	public void clearTransitPassangerFlow() {
		this.linkTransitPassenger=0;
		this.TransitMapping.clear();
	}
	
	
	/**
	 * employs BPR travel time function
	 */
	public double getLinkTravelTime(Tuple<Double,Double> timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams) {
		if(!this.link.getAllowedModes().contains("train")) {
		double totalpcu=super.getLinkCarVolume()+super.getLinkTransitVolume()*params.get(CNLSUEModel.CapacityMultiplierName);
		double capacity=super.getCapacity()*(timeBean.getSecond()-timeBean.getFirst())/3600*params.get(CNLSUEModel.CapacityMultiplierName)*this.getGcRatio();
		double freeflowTime=super.getLength()/super.getFreespeed();
		double linkTravelTime=freeflowTime*(1+anaParams.get(CNLSUEModel.BPRalphaName)*Math.pow(totalpcu/capacity, anaParams.get(CNLSUEModel.BPRbetaName)));
//		if(linkTravelTime>2*3600 || Double.isNaN(linkTravelTime)) {
//			System.out.println("Travel time too high or nan");
//		}
		return linkTravelTime;
		}else {
			linkTravelTime=this.link.getLength()/(this.link.getFreespeed()*1000/(3600));
			if(Double.isNaN(linkTravelTime)) {
				System.out.println("TravelTimeNan");
			}
			return linkTravelTime;
		}
	}
	
	/**
	 * Use this function to store transit line and route specific passenger count 
	 * @param lineId_routeId
	 * @param volume
	 */
	//TODO: make this method additive and add zero passenger count during the transit vehicle loading 
	public void addTransitPassengerVolume(String lineId_routeId, Id<TransitLink> linkId, double volume) {
		this.linkTransitPassenger+=volume;
		if(this.TransitMapping.containsKey(lineId_routeId)) {
			this.TransitMapping.put(lineId_routeId, this.TransitMapping.get(lineId_routeId)+ volume);
			this.transitDirectLinks.get(lineId_routeId).add(linkId);
		}else {
			this.TransitMapping.put(lineId_routeId, volume);
			this.transitDirectLinks.put(lineId_routeId, Collections.synchronizedSet(new HashSet<>()));
			this.transitDirectLinks.get(lineId_routeId).add(linkId);
		}
		
		
	}
	
	
	public double getTransitPassengerVolume(String lineId_routeId) {
		if(this.TransitMapping.containsKey(lineId_routeId)) {
			return this.TransitMapping.get(lineId_routeId);
		}else {
			return 0;
		}
	}
	
	public Set<Id<TransitLink>> getTransitDirectLinks(String lineId_routeId) {
		if(this.transitDirectLinks.containsKey(lineId_routeId)) {
			return this.transitDirectLinks.get(lineId_routeId);
		}else {
			return new HashSet<>();
		}
	}
}
