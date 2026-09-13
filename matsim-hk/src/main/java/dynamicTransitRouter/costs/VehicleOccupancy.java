package dynamicTransitRouter.costs;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import dynamicTransitRouter.TransitStop;

public interface VehicleOccupancy {

	// Methods
	public int getVehicleSeatRemain(TransitStop stop, Id<TransitLine> lineId, Id<TransitRoute> routeId, double time);
	
	/**
	 * Return the number of standing passengers
	 * @param stop
	 * @param lineId
	 * @param routeId
	 * @param time
	 * @return
	 */
	public int getStandingPassenger(TransitStop stop, Id<TransitLine> lineId, Id<TransitRoute> routeId, double time);
	
	public double getStandingProbability(TransitStop stop, Id<TransitLine> lineId, Id<TransitRoute> routeId, double time);
}
