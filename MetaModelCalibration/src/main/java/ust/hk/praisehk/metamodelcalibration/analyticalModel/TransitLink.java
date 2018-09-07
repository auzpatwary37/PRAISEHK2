package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
/**
 * 
 * @author Ashraf Zaman
 *	
 *	 
 */

public abstract class TransitLink{
	
	
	
	protected String startStopId;
	protected String endStopId;
	protected Id<Link> startingLinkId;
	protected Id<Link> endingLinkId;
	
	
	
	protected double passangerCount=0;
	protected double travelTime=0;
	
	public TransitLink(String startStopId, String endStopId, Id<Link> startLinkId,Id<Link> endLinkId) {
		this.startingLinkId=startLinkId;
		this.endingLinkId=endLinkId;
		this.startStopId=startStopId;
		this.endStopId=endStopId;
	}
	
	
	public void resetLink() {
		this.passangerCount=0;
		this.travelTime=0;
	}
	
	
	
	public double getPassangerCount() {
		return this.passangerCount;
	}
	
	public abstract void addPassanger(double d,AnalyticalModelNetwork Network);
	
	
	
	//--------Getter and Setter---------------
	public String getStartStopId() {
		return startStopId;
	}
	
	public String getEndStopId() {
		return endStopId;
	}
	
	public Id<Link> getStartingLinkId() {
		return startingLinkId;
	}
	
	public Id<Link> getEndingLinkId() {
		return endingLinkId;
	}
	


	public abstract Id<TransitLink> getTrLinkId();
	
	
}
