package dynamicTransitRouter.costs;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import dynamicTransitRouter.TransitStop;

public interface WaitingTime {
	public double expectedWaitingTime(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId,
			TransitStop transitStop, double time);
}
