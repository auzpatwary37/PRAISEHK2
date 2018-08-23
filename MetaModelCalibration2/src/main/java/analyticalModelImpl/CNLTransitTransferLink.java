package analyticalModelImpl;

import java.util.HashMap;
import java.util.LinkedHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import analyticalModel.AnalyticalModelNetwork;
import analyticalModel.TransitLink;
import analyticalModel.TransitTransferLink;



/**
 * 
 * @author Ashraf
 *
 */
public class CNLTransitTransferLink extends TransitTransferLink {
	private double headway=0;
	private double capacity;
	private double currentOnboardPassenger=0;	
	private final Id<TransitLink> trLinkId; 
	private CNLTransitDirectLink nextdLink;
	
	public CNLTransitTransferLink(String startStopId, String endStopId, 
			Id<Link> startLinkId, Id<Link> endLinkId,TransitSchedule ts,
			CNLTransitDirectLink dlink) {
		super(startStopId, endStopId, startLinkId, endLinkId);
		this.nextdLink=dlink;
		if(dlink!=null) {
			this.trLinkId=Id.create(dlink.getLineId().replaceAll("\\s+", "")+"_"+dlink.getRouteId().replaceAll("\\s+", "")+
					"_"+dlink.getStartStopId().replaceAll("\\s+", ""),TransitLink.class);
		}else {
			this.trLinkId=Id.create("Destination",TransitLink.class);
		}
	}
	

	

	
	/**
	 * the network is not needed that much for this function
	 */
	@Override
	public void addPassanger(double d, AnalyticalModelNetwork Network) {
		this.passangerCount+=d;
	}

	/**
	 * this method calculates waiting time depending on the following formulae
	 * 
	 * waiting time=alpha/Frequency+1/Frequency*((PassengerTryingToBoard+PassengeronBoard)/(Frequency*Capacity))^beta
	 * for default value of alpha and beta, BPR function alpha beta has been used. 
	 * 
	 * returns 0 if this transfer link is the last one of the trip 
	 */
	@Override
	public double getWaitingTime(LinkedHashMap<String,Double> anaParams,AnalyticalModelNetwork network) {
		
		if(this.nextdLink!=null) {
			headway=this.nextdLink.getHeadway();
			capacity=this.nextdLink.getCapacity();
			double noOfVehicles=this.nextdLink.getFrequency();
			currentOnboardPassenger=((CNLLink)network.getLinks().get(this.nextdLink.getLinkList().get(0)))
				.getTransitPassengerVolume(this.nextdLink.getLineId()+"_"+this.nextdLink.getRouteId());
			this.waitingTime=headway*anaParams.get(CNLSUEModel.TransferalphaName)+
					headway*Math.pow((this.passangerCount+this.currentOnboardPassenger)/(capacity*noOfVehicles),anaParams.get(CNLSUEModel.TransferbetaName));
			if(this.waitingTime==Double.NaN||this.waitingTime==Double.POSITIVE_INFINITY) {
				return this.waitingTime=86400;
			}
			return this.waitingTime;
			
		}else {
			return 0;
		}
	}





	@Override
	public Id<TransitLink> getTrLinkId() {
		
		return this.trLinkId;
	}
	
	public CNLTransitTransferLink cloneLink(CNLTransitTransferLink tl,CNLTransitDirectLink dlink) {
		return new CNLTransitTransferLink(tl.getStartStopId(),tl.getEndStopId(),tl.getStartingLinkId(),tl.getEndingLinkId(),null,dlink);
	}





	protected CNLTransitDirectLink getNextdLink() {
		return nextdLink;
	}
}
