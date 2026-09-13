/**
 * 
 */
package dynamicTransitRouter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * This class is designed for MTR, avoid simultaneous bus trips, as well as for interchange discount
 * Otherwise, like in the transitRouterImplTest, it will be throw an exception
 * 
 * Moreover, it can count the discount.
 * 
 * @author eleead
 *
 */
public class RouteHelper implements Cloneable {
	private HashSet<Id<TransitLine>> tsLineTook;	//changed to kepp track of all lines JLo
	public TransitStop entryStop;
	
	private HashSet<Id<TransitStopFacility>> trainStationsVisited; 	//For MTR/LR (train/LR) only
	public Id<TransitStopFacility> entryStationId;

	public String lastMode;
	public Id<TransitLine> lastTransitLineId;
	public Id<TransitRoute> lastTransitRouteId;
	public int transfers;  //Number of transfers between transit lines (excluding mtr lines)
	public int numberOfTransferLinksPassed;
	public List<Link> transferLinkPassed = new ArrayList<>();
	
	//For transfer discount accounting
	private double totalTripDiscount;
	private double unrealisedFare;
	public double lastFare;
	private double thisFare;
	
	public double lastStartTime;
	public double lastEndTime;
	Set<TransitLineRoute> transitLineRouteForbidden = new HashSet<>();
	private boolean firstClass;
	
	private boolean isSitting; //If the agent is sitting, we assume they sit until the end
	
	public final static String MTRMode = "train";
	public final static String firstClassMode = "trainfirstClass";
	public final static String LRMode = "LR";
	public final static String TramMode = "tram";
	public final static String BusMode = "bus";
	public final static String MinibusMode = "minibus";
	public final static String FerryMode = "ferry";
	
	public final static String firstClassName = "NSX";
	
	//private Logger log = Logger.getLogger(RouteHelper.class);
	

	/**
	 * Add a new route helper
	 * @param lineId
	 * @param startStop
	 * @param transportMode Transport mode in the first stop
	 * @param time Time at the first stop
	 */
	public RouteHelper(Id<TransitLine> lineId, TransitStop startStop, String transportMode, double time) {
		this.entryStationId = startStop.getFacilityId();
		this.entryStop = startStop;
		this.trainStationsVisited = new HashSet<Id<TransitStopFacility>>();
		if (transportMode.equals(MTRMode) || transportMode.equals(LRMode)) {
			this.addStation(startStop, lineId.toString().equals(firstClassName));
		}
		if(lineId.toString().equals("NSX")) {
			this.firstClass = true;
		}
		
		this.tsLineTook = new HashSet<Id<TransitLine>>();
		this.tsLineTook.add(lineId);	
		this.lastStartTime = time;
		
		this.transfers = 0;
		this.totalTripDiscount = 0;
		this.unrealisedFare = 0;
		this.thisFare = 0;
		
	}
	
	/**
	 * Empty constructor for clone method
	 */
	private RouteHelper() {}

	/**
	 * Copies Id and creates new list/set with same elements
	 * And make this clear it is cloning something
	 */
	@Override
	public RouteHelper clone() {
		RouteHelper output = new RouteHelper();
		
		output.entryStationId = this.entryStationId;
		output.entryStop = this.entryStop;
		output.trainStationsVisited = Sets.newHashSet(this.trainStationsVisited);
		output.tsLineTook = Sets.newHashSet(this.tsLineTook);
		output.totalTripDiscount = this.totalTripDiscount;
		output.unrealisedFare = this.unrealisedFare;
		output.transfers = this.transfers;
		
		output.lastMode = this.lastMode;
		output.lastTransitLineId = this.lastTransitLineId;
		output.lastTransitRouteId = this.lastTransitRouteId;
		output.numberOfTransferLinksPassed = this.numberOfTransferLinksPassed;
		output.lastStartTime = this.lastStartTime;
		output.lastEndTime = this.lastEndTime;
		output.transferLinkPassed = Lists.newArrayList(this.transferLinkPassed);
		output.firstClass = this.firstClass;
		
		return output;
	}

	/**
	 * Save the mode for the egress link.
	 */
	public void egress(String mode, Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId, double time) {
		this.lastMode = mode;
		this.lastEndTime = time;
		this.lastTransitLineId = transitLineId;
		this.lastTransitRouteId = transitRouteId;
		this.numberOfTransferLinksPassed = 0; //Reset the number of transfer links
		this.transferLinkPassed.clear();
		
		Set<TransitLineRoute> transferRouteSet = TransitRouterFareDynamicImpl.
				lineRouteRelationMap.get(new TransitLineRoute(transitLineId, transitRouteId));
		if (transferRouteSet!=null){
			this.transitLineRouteForbidden.addAll(transferRouteSet);
		}
	}

	public void setEntryStation(TransitStop stop) {
		this.entryStationId = stop.getFacilityId();
		this.entryStop = stop;
	}

	/**
	 * This function is called whenever change a new line <b>(Not within MTR/LR system)</b>
	 */
	public void changeLine(Id<TransitLine> transitLineId, TransitStop startStation, String transportMode, double time) {
		this.lastStartTime = time;	
		this.transfers++; //Add one transfer
		this.setEntryStation(startStation);
		if(!transportMode.equals(MTRMode) && !transportMode.equals(LRMode))
			this.tsLineTook.add(transitLineId);
		
		this.totalTripDiscount = 0;
		this.unrealisedFare = 0;
		this.lastFare = this.thisFare;
		this.thisFare = 0.0;
	}
	
	/**
	 * Call for changing lines within a fare system (MTR/LR)
	 * FIXME not sure if this should be implemented as it would limit in system routing
	 * @param transitLineId
	 * @param time
	 * @return
	 */
	@Deprecated
	public void inSysChange(Id<TransitLine> transitLineId, double time) {
		this.tsLineTook.add(transitLineId);
	}
	
	/**
	 * Check if MTR/LR station is already visited
	 */
	public boolean isStationVisited(Id<TransitStopFacility> stationId) {
		return trainStationsVisited.contains(stationId);
	}
	
	/**
	 * For transfer discount accounting, to store the fare/fareDiff charged<br>
	 * Eventually becomes lastFare
	 */
	public void addChargedFare(double fare) {
		this.thisFare += fare;
	}
	
	/**
	 * <dt>Stores the stuff in case:</dt>
	 * <li> For LR: the final fare exceeds the discount amount allowed</li>
	 * <li> For Others: Not all discount realised</li><br>
	 */
	public void storeDiscountAndUnrealisedFare(double totalDiscount, double unrealisedFare) {
		this.totalTripDiscount = totalDiscount;
		this.unrealisedFare = unrealisedFare;
	}
	
	public void addUnrealizedDiscount(double additionalDiscount) {
		this.totalTripDiscount += additionalDiscount;
	}
	
	/**
	 * <b>LR ONLY</b><br>
	 * Checks if the transfer discount (if existed) still applies<br>
	 * Applicable iff fare less than discount or else nothing<br>
	 * realise the uncharged fare if the transfer discount is exceeded
	 */
	public double realiseUnrealisedLRFare(double fareDiff) {
		if(this.totalTripDiscount>0) {
			this.unrealisedFare += fareDiff;
			if(this.unrealisedFare<this.totalTripDiscount)
				return 0.0;
			else {
				//discount only applies to fare less than the discount amount
				this.totalTripDiscount = 0;
				return this.unrealisedFare;
			}
		} else
			return fareDiff;
	}
	
	/**
	 * <b>NOT FOR LR</b><br>
	 * Checks if the transfer discount is fully applied<br>
	 * Realise the transfer discount that have yet to be applied<br>
	 * NOTE: unrealisedFare in code should instead be interpreted as regularFare
	 */
	public double realiseUnrealisedDiscount(double fareDiff) {
		if(this.totalTripDiscount == 0) {
			return fareDiff;
		} else /*if(this.tempRealizedTranferDiscount>0)*/ {	
			this.unrealisedFare += fareDiff;
			if(this.unrealisedFare<this.totalTripDiscount) //If the discount still higher than fare so far
				return 0.0;
			else { //else, charge the remaining fare to be paid
				this.unrealisedFare -= this.totalTripDiscount;
				this.totalTripDiscount = 0;
				return this.unrealisedFare;
			}
		}
	}

	/**
	 * Checks if a the same line is used prev already<br>
	 * All modes inclusive
	 */
	public boolean isLineTook(Id<TransitLine> lineId) {
		return tsLineTook.contains(lineId);
	}
	
	/**
	 * This function is for adding a station to a set, so that the trip routing would not revisit a station
	 */
	public void addStation(TransitStop visitedStation, boolean boardingFirstClass) {
		this.firstClass = boardingFirstClass;
		this.trainStationsVisited.add(visitedStation.getFacilityId());
	}

	/**
	 * The MTR code is upper case char in 4 to 6 element, as determined in the rules in {@link createMTR.CreateMTR}<br>
	 * The LR code is 3 digits & starts with prefix "LR" {@link createLRnetwork.CreateLRNetwork}
	 * DEBUG no need to check this 
	 */
	@Deprecated
	private static String extractMTRLRStationCode(String idString) {
		String output;
		//determining & extracting
		if(idString.startsWith("LR"))
			output = idString.substring(2, 5);
		else
			output = idString.substring(4, 7);
		//checking
		if(!(output.matches("[A-Z]{3}+") || output.matches("\\d{3}+")))
			throw new IllegalArgumentException("The MTR/LR station code is wrong!");
		return output;
	}
	
	public boolean isFirstClass() {
		return this.firstClass;
	}
	
	/**
	 * Set the state of the agent as sitting (sitting = true)
	 */
	public void setSitting() {
		this.isSitting = true;
	}
	
	/**
	 * Set the state of the agent as standing (sitting = false)
	 */
	public void setStanding() {
		this.isSitting = false;
	}
	
	/**
	 * Enquire if the agent is sitting or not
	 * @return
	 */
	public boolean isSitting() {
		return this.isSitting;
	}

}