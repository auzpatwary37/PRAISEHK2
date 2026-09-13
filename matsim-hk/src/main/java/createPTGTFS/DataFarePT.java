/**
 * 
 */
package createPTGTFS;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.core.utils.collections.Tuple;

/**
 * A generic fare data container for PT data extracted from GTFS
 * @author JLo
 *
 */
public class DataFarePT {
	private final String smallRouteKey;
	private final ConcurrentHashMap<String, FareDataGTFS> fareMap;
	
	public DataFarePT(String smallRouteKey) {
		this.smallRouteKey = smallRouteKey;
		this.fareMap = new ConcurrentHashMap<String, FareDataGTFS>();
	}
	
	//methods for accessing data
	
	public String getKey() {
		return this.smallRouteKey;
	}
	
	public ConcurrentHashMap<String, FareDataGTFS> getFareMap() {
		return this.fareMap;
	}
	
	//methods for inputing data
	
	public boolean newFareDataGTFS(String fare_id, String fromStopId, String toStopId) {
		if(this.fareMap.containsKey(fare_id))
			return false;
		else {
			FareDataGTFS temp = new FareDataGTFS(fare_id, fromStopId, toStopId);
			this.fareMap.put(fare_id, temp);
			return true;
		}
	}
	
	public boolean addFare(String fare_id, double fare) {
		if(this.fareMap.containsKey(fare_id)) {
			this.fareMap.get(fare_id).addFare(fare);
			return true;
		} else
			return false;
	}
	
	//methods for FareCalculator
	
	public double getFareByFareId(String fare_id) {
		return this.fareMap.get(fare_id).getFare();
	}
	
	@Deprecated
	public ArrayList<Double> getFares(String fromStopId, String toStopId){
		ArrayList<Double> output = new ArrayList<Double>();
		for(FareDataGTFS temp: this.fareMap.values()) {
			if(temp.equals(fromStopId, toStopId))
				output.add(temp.getFare());
		}
		return output;
	}
	
	public double getMinFare(String fromStopId) {
		//intentionally leaves possibility of max value to see if last stop of a line is still letting people get on board
		double min = Double.MAX_VALUE;
		for(FareDataGTFS temp: this.fareMap.values()) {
			if(temp.isFromStop(fromStopId))
				if(temp.getFare()<min)
					min = temp.getFare();
		}
		return min;
	}
	
	/**
	 * Checks exsiting fareData has the same fare, excludes lastStop(no record)
	 * @return Tuple First: boolean hasSingleFare; Second: (<i>First? theSingleFare : 0.0</i>)
	 */
	public Tuple<Boolean, Double> hasSingleFare() {
		Iterator<FareDataGTFS> fareItr = this.fareMap.values().iterator();
		double thisFare = fareItr.next().getFare();
		while(fareItr.hasNext()) {
			if(thisFare != fareItr.next().getFare())
				return new Tuple<Boolean,Double>(false,0.0);
		}
		return new Tuple<Boolean,Double>(true,thisFare);
	}
	
	/**
	 * Checks fareData of fromStopId if has the same fare, last stop or no rec will retrun MAX_VALUE
	 * @return Tuple First: boolean hasSingleFare; Second: (<i>First? theSingleFare : 0.0</i>)
	 */
	public Tuple<Boolean, Double> stopHasSingleFare(String fromStopId) {
		Iterator<FareDataGTFS> fareItr = this.fareMap.values().iterator();
		double thisFare = Double.MAX_VALUE;
		//catch the first occ of isFromStop
		while(fareItr.hasNext()) {
			FareDataGTFS thisStop = fareItr.next();
			if(thisStop.isFromStop(fromStopId)) {
				thisFare = thisStop.getFare();
				break;
			}
		}
		//check all others with thisFare
		while(fareItr.hasNext()) {
			FareDataGTFS thisStop = fareItr.next();
			if(thisStop.isFromStop(fromStopId))
				if(thisFare != thisStop.getFare())
					return new Tuple<Boolean,Double>(false,0.0);
		}
		return new Tuple<Boolean,Double>(true,thisFare);
	}
	
}

class FareDataGTFS {
	//removed fare_id as unused and save save
	private final int fromStopSeq;
	private final int toStopSeq;
	private final String fromStopId;
	private final String toStopId;
	private double fare;
	
	public FareDataGTFS(String fare_id, String fromStopId, String toStopId) {
		this.fromStopId = fromStopId;
		this.toStopId = toStopId;
		int i = fare_id.indexOf("-");	//finds the "-" between the small route id
		i = fare_id.indexOf("-", i+1);	//finds the "-" after the small route id and should be the one before the from stop seq
		int i2 = fare_id.indexOf("-", i+1);
		fromStopSeq = Integer.parseInt(fare_id.substring(i+1, i2));
		toStopSeq = Integer.parseInt(fare_id.substring(i2+1));
	}
	
	public void addFare(double fare) {
		this.fare = fare;
	}
	
	public double getFare() {
		return this.fare;
	}
	
	@Deprecated
	public boolean equals(String fromStopId, String toStopId) {
		return (this.fromStopId.equals(fromStopId) && this.toStopId.equals(toStopId));
	}
	
	public boolean isFromStop(String fromStopId) {
		return this.fromStopId.equals(fromStopId);
	}
	
	public int getFromStopSeq() {
		return this.fromStopSeq;
	}
	
	public int getToStopSeq() {
		return this.toStopSeq;
	}
}
