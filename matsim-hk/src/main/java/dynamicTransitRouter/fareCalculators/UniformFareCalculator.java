package dynamicTransitRouter.fareCalculators;

import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.common.collect.Lists;

/**
 * No fare will be charged for this class
 * 
 * @author eleead
 *
 */
public class UniformFareCalculator implements FareCalculator {

	private double fare;
	private double fareFactor = 1.;

	public UniformFareCalculator(double fare) {
		this.fare = fare;
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		return fare * this.fareFactor;
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId) {
		return fare * this.fareFactor;
	}

	@Override
	public List<Double> getFares(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		return Lists.newArrayList(fare);
	}

	@Override
	public double getFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			int fromOccurence, Id<TransitStopFacility> toStopId, int toOccurence) {
		return fare * this.fareFactor;
	}

	@Override
	public void setFareFactor(double fareFactor) {
		this.fareFactor = fareFactor;
	}

}
