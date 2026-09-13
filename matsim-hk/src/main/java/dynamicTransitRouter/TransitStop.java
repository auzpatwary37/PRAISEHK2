package dynamicTransitRouter;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * A help class to distinguish the stop that the route is currently in
 * 
 * @author eleead
 *
 */
public class TransitStop {
	private final Id<TransitStopFacility> facilityId;
	private final int occurrence; // The number of occurrence of this stop in the route.
	private Id<Link> linkId;
	// private final double departureTime;

	/**
	 * 
	 * @param trs TransitRouteStop
	 * @param occurrence The occurrence, starts from 0.
	 */
	public TransitStop(TransitRouteStop trs, int occurrence) {
		this(trs.getStopFacility().getId(), trs.getStopFacility().getLinkId(), occurrence);
	}

	public TransitStop(Id<TransitStopFacility> facilityId, Id<Link> linkId, int occurrence) {
		this.facilityId = facilityId;
		// this.departureTime = departureTime;
		this.occurrence = occurrence;
		this.linkId = linkId;
	}

	public Id<TransitStopFacility> getFacilityId() {
		return this.facilityId;
	}
	
	public Id<Link> getLinkId(){
		return this.linkId;
	}

	public int getOccurrence() {
		return this.occurrence;
	}

	public int hashCode() {
		return 41 * facilityId.hashCode() + occurrence;
	}

	public boolean equals(Object other) {
		TransitStop others = (TransitStop) other;
		return others.facilityId.equals(this.facilityId) && others.occurrence == this.occurrence;
	}

	public String toString() {
		return "Facility Id: " + facilityId.toString() + " occurence " + occurrence;
	}
}
