package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;


/**
 * This class will hold the essential information required for retrieving the fare information of a fare payment 
 * 
 * for train i.e. MTR this will include only the mode boarding stop and alighting stop
 * 
 * for bus and other transit modes, this class will include the transit line route boarding and alighting stop and 
 * 
 * @author ashraf
 *
 */

//TODO: add any other parameter that is necessary for fare extraction

public class FareLink {
	
	
	
	public static final String FareLinkAttributeName = "fareLink";
	
	/**
	 * Train or any transit mode with network wide payment
	 */
	public static final String NetworkWideFare = "NetworkWideFare";
	
	/**
	 * Any transit mode with in vehicle payment 
	 */
	public static final String InVehicleFare = "InVehicleFare";
	
	
	public static final String seperator = "___";
	
	private final String mode;
	public final String type;
	private Id<TransitLine> transitLine = null;
	private Id<TransitRoute> transitRoute = null;
	private final Id<TransitStopFacility> boardingStopFacility;
	private Id<TransitStopFacility> alightingStopFacility = null;
	
	
	public FareLink(String fareLinkType,Id<TransitLine> transitLine, Id<TransitRoute> transitRoute, Id<TransitStopFacility> boardingStopFacility, Id<TransitStopFacility> alightingStopFacility,String mode) {
		this.mode = mode;
		this.type = fareLinkType;
		this.transitLine = transitLine;
		this.transitRoute = transitRoute;
		this.alightingStopFacility = alightingStopFacility;
		this.boardingStopFacility = boardingStopFacility;
		if(mode == null || type == null || boardingStopFacility == null) {
			throw new IllegalArgumentException("Transit mode, fare link type boarding stop in a fare link cannot be null!!!");
		}else if(this.type.equals(FareLink.InVehicleFare) && (this.transitLine==null || this.transitRoute==null)) {
			throw new IllegalArgumentException("Transit line id or transit route id cannot be null for transit with a vehicle specific fare scheme!!!");
		}else if(type.equals(FareLink.NetworkWideFare) && (this.transitLine!=null || this.transitRoute!=null || this.boardingStopFacility ==null || this.alightingStopFacility == null)) {
			throw new IllegalArgumentException("Transit line id or transit route id cannot be non-null and boarding, alighting stop cannot be null for transit with a network wide fare scheme!!!");
		}
		
	}
	/**
	 * The description is assumed this way
	 * for a network wide fare payment scheme:
	 * 
	 * type+separator+boardingLink+separator+aligtingLink+mode
	 * 
	 * for a vehicle specific fare payment scheme: 
	 * 
	 * type+separator+TransitLine+separator+TransitRoute+Separator+boardingLink+separator+aligtingLink+separator+mode
	 * 
	 * @param FareLinkDescription
	 */
	public FareLink(String fareLinkDescription) {
		String[] part = fareLinkDescription.split(FareLink.seperator);
		this.type = part[0];
		if(type.equals(FareLink.NetworkWideFare)) {
			this.boardingStopFacility = Id.create(part[1], TransitStopFacility.class);
			this.alightingStopFacility = Id.create(part[2], TransitStopFacility.class);
			this.mode = part[3];
		}else if (type.equals(FareLink.InVehicleFare)) {
			this.transitLine = Id.create(part[1], TransitLine.class);
			this.transitRoute = Id.create(part[2],TransitRoute.class);
			this.boardingStopFacility = Id.create(part[3], TransitStopFacility.class);
			this.alightingStopFacility = Id.create(part[4], TransitStopFacility.class);
			this.mode = part[5];
		}else {
			throw new IllegalArgumentException("Unrecognized type!!! Type can be only of two types: "+FareLink.NetworkWideFare+" or "+FareLink.InVehicleFare+". Please use the static string in this class as type keys.");
		}
	}

	public Id<TransitLine> getTransitLine() {
		return transitLine;
	}


	public void setTransitLine(Id<TransitLine> transitLine) {
		this.transitLine = transitLine;
	}


	public Id<TransitRoute> getTransitRoute() {
		return transitRoute;
	}


	public void setTranitRoute(Id<TransitRoute> tranitRoute) {
		this.transitRoute = tranitRoute;
	}


	public String getMode() {
		return mode;
	}


	public Id<TransitStopFacility> getBoardingStopFacility() {
		return boardingStopFacility;
	}


	public Id<TransitStopFacility> getAlightingStopFacility() {
		return alightingStopFacility;
	}

	
	public String getType() {
		return type;
	}
	
	@Override
	public String toString() {
		String s;
		if(type.equals(FareLink.NetworkWideFare)) {
			s =  type+seperator+this.boardingStopFacility+seperator+this.alightingStopFacility+seperator+mode;
		}else {
			s =  type+seperator+this.transitLine+seperator+this.transitRoute+seperator+this.boardingStopFacility+seperator+this.alightingStopFacility+seperator+mode;
		}
		return s;
	}

}
