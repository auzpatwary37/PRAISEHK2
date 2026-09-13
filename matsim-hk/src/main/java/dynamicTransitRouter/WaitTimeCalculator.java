package dynamicTransitRouter;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class WaitTimeCalculator {

	/**
	 * Given the stop facility, line and route waiting, and arrival time, calculate
	 * the expected
	 * 
	 * @param stopFacilityId
	 * @param lineId
	 * @param routeId
	 * @param time
	 * @return
	 */
	public int getExpectedWaitingTime(Id<TransitStopFacility> stopFacilityId, Id<TransitLine> lineId,
			Id<TransitRoute> routeId, double time) {
		return 0;
	}
}
