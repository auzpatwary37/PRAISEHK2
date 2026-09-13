package dynamicTransitRouter.fareCalculators;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;

import javax.inject.Named;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

import dynamicTransitRouter.DynamicRoutingModule;

public class LRFareCalculator implements FareCalculator {
	
	private double minFare = Double.MAX_VALUE; //The minimum fare for traveling ONE station.
	private final HashMap<String, HashMap<String, Double>> fareFromTo = new HashMap<>();
	@Inject @Named(DynamicRoutingModule.fareRateName) private double fareFactor = 1.;
	
	private String processStationId(String stationId) {
		if(stationId.length()==1) {
			return "00"+stationId;
		}else if(stationId.length()==2) {
			return "0"+stationId;
		}else if(stationId.length()==3) {
			return stationId;
		}else {
			throw new IllegalArgumentException("The stationID is off wrong length!");
		}
	}
	
	@Inject
	public LRFareCalculator(@Named("LRFareInput") String farePath) throws IOException {
		Reader fare_in = new FileReader(farePath);
		Iterable<CSVRecord> fareRecord = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(fare_in);

		for (CSVRecord fareLine : fareRecord) {
			String startStation = processStationId(fareLine.get("from_station_id"));
			String endStation = processStationId(fareLine.get("to_station_id"));

			double fare = Double.parseDouble(fareLine.get("fare_octo_adult"));
			if(fare < minFare && fare!=0) {
				minFare = fare; //Obtain the minimum fare.
			}

			if (fareFromTo.containsKey(startStation)) {
				HashMap<String, Double> fareTo = fareFromTo.get(startStation);
				fareTo.put(endStation, fare);
				fareFromTo.put(startStation, fareTo);
			} else {
				HashMap<String, Double> fareTo = new HashMap<>();
				fareTo.put(endStation, fare);
				fareFromTo.put(startStation, fareTo);
			}
		}
		
		//Modify the hashMap to make the same station travel the minimum fare.
		for(String startStion: fareFromTo.keySet()) {
			fareFromTo.get(startStion).put(startStion, minFare);
		}
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		return minFare  * this.fareFactor; //The minimum fare is 4.7
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId) {
		return minFare  * this.fareFactor; //The minimum fare is 4.7
	}

	@Override
	public List<Double> getFares(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		return Lists.newArrayList(getFare(routeId, lineId, fromStopId, 0, toStopId, 0));
	}

	@Override
	public double getFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			int fromOccurence, Id<TransitStopFacility> toStopId, int toOccurence) {
		String fromStopIdString = fromStopId.toString().substring(2, 5);
		String toStopIdString = toStopId.toString().substring(2, 5);
		return fareFromTo.get(fromStopIdString).get(toStopIdString) * this.fareFactor;
	}

	@Override
	public void setFareFactor(double fareFactor) {
		this.fareFactor = fareFactor;
	}

}
