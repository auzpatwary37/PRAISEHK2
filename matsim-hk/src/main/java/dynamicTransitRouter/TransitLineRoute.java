package dynamicTransitRouter;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

public class TransitLineRoute {
	private Id<TransitLine> transitLineId;
	private Id<TransitRoute> transitRouteId;
	
	public TransitLineRoute(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId) {
		if(transitLineId==null || transitRouteId==null) {
			throw new IllegalArgumentException("The ids cannot be null!");
		}
		this.transitLineId = transitLineId;
		this.transitRouteId = transitRouteId;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((transitLineId == null) ? 0 : transitLineId.hashCode());
		result = prime * result + ((transitRouteId == null) ? 0 : transitRouteId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TransitLineRoute other = (TransitLineRoute) obj;
		return transitLineId.equals(other.transitLineId) && transitRouteId.equals(other.transitRouteId);
	}
	
	
}
