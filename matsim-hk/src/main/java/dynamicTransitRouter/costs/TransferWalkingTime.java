package dynamicTransitRouter.costs;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;

import javax.inject.Named;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.inject.Inject;

public class TransferWalkingTime {
	
	private final static HashMap<String,HashMap<String,Double>> timeFromTo = new HashMap<>();
	
	/**
	 * Read the CSV file and create the instance
	 * @param walkTimePath
	 * @throws IOException
	 */
	@Inject
	public TransferWalkingTime(@Named("WalkTimeInput") String walkTimePath)
			throws IOException {
		if(!timeFromTo.isEmpty()) {
			return; //If it is already initialized, no need to read once more.
		}
		
		if(!walkTimePath.equals("")) { //Only if the walk time path is defined
			Reader fare_in = new FileReader(walkTimePath);
			Iterable<CSVRecord> timeRecord = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(fare_in);
			for (CSVRecord timeLine : timeRecord) {
				String startStation = timeLine.get("from");
				String endStation = timeLine.get("to");
	
				double time = Double.parseDouble(timeLine.get("Time (Minutes)")) * 60;
			
				if (timeFromTo.containsKey(startStation)) {
					HashMap<String, Double> timeTo = timeFromTo.get(startStation);
					timeTo.put(endStation, time);
					timeFromTo.put(startStation, timeTo);
				} else {
					HashMap<String, Double> timeTo = new HashMap<>();
					timeTo.put(endStation, time);
					timeFromTo.put(startStation, timeTo);
				}
			}
		}
	}
	
	/**
	 * This function returns the walking time between two stop facilities
	 * @param fromFacilityId
	 * @param toFacilityId
	 * @return
	 */
	public static double getWalkingTime(Id<TransitStopFacility> fromFacilityId,
			Id<TransitStopFacility> toFacilityId) {
		if(timeFromTo.containsKey(fromFacilityId.toString())) {
			HashMap<String, Double> timeTo = timeFromTo.get(fromFacilityId.toString());
			if(timeTo.get(toFacilityId.toString())!= null) {
				return timeTo.get(toFacilityId.toString());
			}else {
				return 2 * 60; //If there is from, there isn't to, then should need to change floor (e.g. ADM, NOP, MOK)
			}
		}
		return -1;
	}
}
