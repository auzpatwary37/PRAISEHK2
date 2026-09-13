package dynamicTransitRouter;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import dynamicTransitRouter.fareCalculators.FareCalculator;

/**
 * A helper class to store the trips data for PTRecordHandler. The intuition is like a Octopus record.
 * No fare would be charged here, it is just a storage.
 * @author eleead
 *
 */
public class TripsData {
	private final Id<Person> personId;
	private List<TripData> trips;

	private Id<TransitStopFacility> waitingAtStopId;
	private Id<TransitStopFacility> anticipatedDestination; //The anticipated destination, if it is waiting.
	private double startWaitingTime;
	private boolean onVehicle;

	public TripsData(final Id<Person> personId) {
		this.personId = personId;
		this.trips = new LinkedList<TripData>();
	}

	/**
	 * The event that handles an agent start waiting
	 */
	public void startWait(double time, Id<TransitStopFacility> atFacilityId, Id<TransitStopFacility> toFacilityId) {
		if(this.onVehicle) {
			throw new RuntimeException("The agent "+personId.toString()+" has not ended the trip yet!");
		}
		this.anticipatedDestination = toFacilityId;
		this.waitingAtStopId = atFacilityId;
		this.startWaitingTime = time;
	}


	public void startTrip(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> startFacilityId,
			String transportMode, double time) {
		if(this.onVehicle) {
			throw new RuntimeException("The agent "+personId.toString()+" has not ended the trip yet!");
		}
		trips.add(new TripData(lineId, routeId, startFacilityId));
		this.anticipatedDestination = null; // The waiting of vehicle ends.
		this.onVehicle = true;
	}

	public void endTrip(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> endFacilityId,
			double endTime) {
		TripData toBeEdited = trips.get(trips.size() - 1);
		if (toBeEdited.getRouteId().equals(routeId) && toBeEdited.getLineId().equals(lineId)) {
			toBeEdited.tripEnd(endFacilityId, endTime);
			this.onVehicle = false;
		} else {
			throw new IllegalArgumentException("The route ID or the line ID is not consistent!");
		}
	}

	public double getStartWaitingTime() {
		if ( this.anticipatedDestination != null) {
			return startWaitingTime;
		} else {
			throw new RuntimeException("The agent is not waiting, don't have a waiting time.");
		}
	}

//	public Id<TransitStopFacility> getLatestStartFacility() {
//		return trips.get(trips.size() - 1).getStartFacilityId();
//	}
	
	public Id<TransitStopFacility> getWaitingAtStopId(){
		return this.waitingAtStopId;
	}
	
	public Id<TransitStopFacility> getAnticipatedDestination(){
		return this.anticipatedDestination;
	}

	private class TripData {
		private final Id<TransitLine> lineId;
		private final Id<TransitRoute> routeId;
		private final Id<TransitStopFacility> startFacilityId;
		private Id<TransitStopFacility> endFacilityId;
		private double endTime;

		private TripData(final Id<TransitLine> lineId, final Id<TransitRoute> routeId,
				final Id<TransitStopFacility> startFacilityId) {
			if (lineId == null || routeId == null || startFacilityId == null) {
				throw new IllegalArgumentException("The input parameters cannot be null.");
			}
			this.lineId = lineId;
			this.routeId = routeId;
			this.startFacilityId = startFacilityId;
		}

		public void tripEnd(Id<TransitStopFacility> endFacilityId, double endTime) {
			this.endFacilityId = endFacilityId;
			this.endTime = endTime;
		}

		public Id<TransitLine> getLineId() {
			return lineId;
		}

		public Id<TransitRoute> getRouteId() {
			return routeId;
		}

		public Id<TransitStopFacility> getStartFacilityId() {
			return startFacilityId;
		}

		public Id<TransitStopFacility> getEndFacilityId() {
			return endFacilityId;
		}
	}
}
