package dynamicTransitRouter.fareCalculators;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.mobsim.qsim.pt.TransitVehicle;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.common.collect.Lists;

import dynamicTransitRouter.DynamicRoutingModule;

public class MTRFareCalculator implements FareCalculator {
	private final HashMap<String, HashMap<String, Double>> fareFromTo = new HashMap<>();
	private final HashMap<String, HashMap<String, Double>> firstClassfareFromTo = new HashMap<>();
	private final Map<Id<TransitStopFacility>, TransitStopFacility> tsFmap;
	private Logger log;
	
	@Inject @Named(DynamicRoutingModule.fareRateName) private double fareFactor = 1.;

	@Inject
	public MTRFareCalculator(@Named("trainFareInput") String input, TransitSchedule ts) throws IOException {
		tsFmap = ts.getFacilities();

		// Iterate through every string
		Reader fare_in = new FileReader(input);
		Iterable<CSVRecord> fareRecord = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(fare_in);

		for (CSVRecord fareLine : fareRecord) {
			String startStation; //There are two versions of mapping
			if(fareLine.isMapped("SRC_STATION_NAME")) {
				startStation = fareLine.get("SRC_STATION_NAME");
			}else startStation = fareLine.get("SRC_Station_Name");
			String endStation = fareLine.get("DEST_STATION_NAME");

			double fare = Double.parseDouble(fareLine.get("OCT_ADT_FARE"));
			if (startStation.equals(endStation)) {
				fare = 3.7; // Charges $3.7 for same station entry and exit
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

		log = Logger.getLogger(MTRFareCalculator.class);
	}
	
	public MTRFareCalculator(@Named("firstClassInput") String firstClassStr, TransitSchedule ts, TransitSchedule ts2) throws IOException {
		this(firstClassStr, ts);
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		String firstStopName = tsFmap.get(fromStopId).getName();
		String nextStopName = tsFmap.get(toStopId).getName();
		try {
			return fareFromTo.get(firstStopName).get(nextStopName) * this.fareFactor;
		} catch (NullPointerException e) {
			throw new NullPointerException("The stop " + fromStopId + " and/or " + toStopId + " is not there.");
		}
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId) {
		return getMinFare(routeId, lineId, fromStopId, fromStopId)  * this.fareFactor;
	}

	@Override
	public List<Double> getFares(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		return Lists.newArrayList(getMinFare(routeId, lineId, fromStopId, toStopId));
	}

	@Override
	public double getFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			int fromOccurence, Id<TransitStopFacility> toStopId, int toOccurence) {
		return getMinFare(routeId, lineId, fromStopId, toStopId) * this.fareFactor;
	}

	@Override
	public void setFareFactor(double fareFactor) {
		this.fareFactor = fareFactor;
		
	}
}
