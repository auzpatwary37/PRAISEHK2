package dynamicTransitRouter.costs;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import dynamicTransitRouter.TransitStop;

public interface StopStopTime {
	public double expectedStopStopTime(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId,
			TransitStop fromTransitStop, TransitStop toTransitStop, double time);
}
