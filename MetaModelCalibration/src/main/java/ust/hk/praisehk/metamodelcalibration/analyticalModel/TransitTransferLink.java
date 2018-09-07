package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.LinkedHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;


/**
 * 
 * @author Ashraf
 *
 * @param <anaNet>Type of network used.
 * must extend AnalyticalModelNetwork
 */
public abstract class TransitTransferLink extends TransitLink{
	
	/**
	 *	this is basically a transit walk link
	 * 
	 * @param startStopId
	 * @param endStopId
	 * these two parameters are not present and actually not necessary 
	 * and can or will be null in case of journey start or end
	 * 
	 * Some additional information is necessary
	 * the next transit line and route Id if not the end of the trip.
	 * Most possibly would be easier to collect the information from the route. 
	 * 
	 * TODO: Make the Trip and TripChain generic to any type of analytical model
	 * 		 Rewrite the plan to route collection algorithm specially.
	 * 
	 * @param startLinkId
	 * @param endLinkId
	 * this mark the start link and end link Id 
	 */
	
	public TransitTransferLink(String startStopId, String endStopId, Id<Link> startLinkId, Id<Link> endLinkId) {
		super(startStopId, endStopId, startLinkId, endLinkId);
		
	}

	//transfer link parameters
	
	/**
	 * walk distance is part of transit route and will input from the plan
	 */
	protected double walkDistance=0;
	protected double waitingTime=0;
	
	
	//getter and setter
	
	
	public double getWalkDistance() {
		return walkDistance;
	}
	public void setWalkDistance(double walkDistance) {
		this.walkDistance = walkDistance;
	}
	
	
	//-----------------------------
	
	/**
	 *
	 * this method calculates waiting time depending on the following formulae
	 * 
	 * waiting time=alpha/Frequency+1/Frequency*((PassengerTryingToBoard+PassengeronBoard)/(Frequency*Capacity))^beta
	 * for default value of alpha and beta, BPR function alpha beta has been used. 
	 * returns 0 if this transfer link is the last one of the trip 
	 * @return: waiting time in seconds
	 * 
	 */
	public abstract double getWaitingTime(LinkedHashMap<String,Double> anaParams,AnalyticalModelNetwork Network);
	
	
	
		
}
