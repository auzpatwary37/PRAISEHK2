package dynamicTransitRouter.fareCalculators;

import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public interface FareCalculator {
	/**
	 * Obtain the minimum fare, given the route and direction(line), with from and
	 * to Id Notes that even if the people aboard and alight in the same station, it
	 * would charge the minimum fare.
	 * 
	 * @param routeId
	 * @param lineId
	 * @param fromStopId
	 * @param toStopId
	 * @return
	 */
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId);

	/**
	 * Minimum fare (granted right to alight from the bus in any stops) given only
	 * the boarding stop.
	 * 
	 * @param routeId
	 * @param lineId
	 * @param fromStopId
	 * @return
	 */
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId);

	/**
	 * Get all possible fares from a stop to a stop. <br>
	 * due to route may repeat 2 stops so there may be more than 1 fare based on at which point of the route the bus is at
	 * 
	 * @param routeId
	 * @param lineId
	 * @param fromStopId
	 * @param toStopId
	 * @return
	 */
	public List<Double> getFares(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId);

	/**
	 * Get the actual charge, given the occurrence of the stop facility. <br>
	 * due to route may repeat 2 stops so there may be more than 1 fare based on at which point of the route the bus is at
	 * 
	 * @param routeId
	 * @param lineId
	 * @param fromStopId
	 * @param fromOccurence	- Specifies which occurrence of the fromStopId it requests, starts from 0
	 * @param toStopId
	 * @param toOccurence	- Specifies which occurrence of the toStopId it requests, starts from 0
	 * @return
	 */
	public double getFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			int fromOccurence, Id<TransitStopFacility> toStopId, int toOccurence);
	
	/**
	 * Setting the fare factor for each fare calculator
	 */
	public void setFareFactor(double fareFactor);
}
