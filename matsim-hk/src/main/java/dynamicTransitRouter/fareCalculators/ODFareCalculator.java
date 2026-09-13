package dynamicTransitRouter.fareCalculators;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.common.collect.Lists;

public class ODFareCalculator implements FareCalculator {
	
	private Map<Id<TransitStopFacility>, Map<Id<TransitStopFacility>, Double>> ODfares = new HashMap<>();
	private double fareFactor = 1.;
	
	public void addODFare(Id<TransitStopFacility> fromStopId, Id<TransitStopFacility> toStopId, double price) {
		if(ODfares.get(fromStopId) == null) {
			Map<Id<TransitStopFacility>, Double> fareMap = new HashMap<>();
			fareMap.put(toStopId, price);
			ODfares.put(fromStopId, fareMap);
		}else {
			ODfares.get(fromStopId).put(toStopId, price);
		}
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		return ODfares.get(fromStopId).get(toStopId) * fareFactor;
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId) {
		return Collections.max(ODfares.get(fromStopId).values());
	}

	@Override
	public List<Double> getFares(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		return Lists.newArrayList(ODfares.get(fromStopId).get(toStopId) * fareFactor);
	}

	@Override
	public double getFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			int fromOccurence, Id<TransitStopFacility> toStopId, int toOccurence) {
		return ODfares.get(fromStopId).get(toStopId) * fareFactor;
	}

	@Override
	public void setFareFactor(double fareFactor) {
		this.fareFactor = fareFactor;
	}

}
