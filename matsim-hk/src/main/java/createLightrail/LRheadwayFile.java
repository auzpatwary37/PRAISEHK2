/**
 * 
 */
package createLightrail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.matsim.core.utils.collections.Tuple;

/**
 * @author JLo
 *
 */
public class LRheadwayFile {
	private ArrayList<LRroutesVehiclesData> LRroutesVehicles = new ArrayList<>();
	private ArrayList<LRheadwayData> LRroutesHeadway = new ArrayList<>();
	private double VehicleAmountMultiplier = 2;
	
	/**
	 * includes a VehicleAmountMultiplier to allow for easier departure scheduling
	 * since no exact/detail/accurate schedule available
	 * @param ref
	 * @return
	 */
	public HashMap<String, Tuple<Double, Double>> getVehicles() {
		HashMap<String, Tuple<Double, Double>> output = new HashMap<String, Tuple<Double, Double>>();
		for(LRroutesVehiclesData Temp: LRroutesVehicles) {
			output.put(Temp.getRef(), new Tuple<Double, Double>(Temp.getsinglecarnum()*VehicleAmountMultiplier, Temp.getdoublecarnum()*VehicleAmountMultiplier));
		}
		return output;
	}
	
	public LRheadwayData getHeadwayData(String id) {
		for(LRheadwayData Temp: LRroutesHeadway)
			if(id.equals(Temp.getId()))
				return Temp;
		return null;
	}
	
}

class LRroutesVehiclesData {
	private String ref;
	private double singlecar;
	private double doublecar;
	
	public String getRef() {
		return ref;
	}
	
	public double getsinglecarnum() {
		return singlecar;
	}
	
	public double getdoublecarnum() {
		return doublecar;
	}
}

class LRheadwayData {
	private String id;
	private String ref;
	private String starttime;
	private String endtime;
	private ArrayList<LRheadway> headways = new ArrayList<>();
	
	public String getId() {
		return id;
	}
	
	public String getRef() {
		return ref;
	}
	
	/**
	 * @return time in seconds from 0000, format of departures
	 */
	public double getStartTime() {
		return LRtimeconvertor.convert(starttime);
	}
	
	/**
	 * @return time in seconds from 0000, format of departures
	 */
	public double getEndTime() {
		return LRtimeconvertor.convert(endtime);
	}
	
	public ArrayList<LRheadway> getHeadways(){
		return headways;
	}
}

class LRheadway {
	private String from;
	private String to;
	private double headway;
	
	public double getFrom() {
		return LRtimeconvertor.convert(from);
	}
	
	public double getTo() {
		return LRtimeconvertor.convert(to);
	}
	
	/**
	 * @return headway in minutes
	 */
	public double getHeadway() {
		return headway;
	}
	
}

class LRtimeconvertor {
	/**
	 * converts time to seconds from 0000, format of departures
	 * and if time after 0000 to 0400, add 24 hr to it
	 * @return
	 */
	public static double convert(String time) {
		Pattern pattern = Pattern.compile("\\d\\d\\d\\d");
		Matcher matcher = pattern.matcher(time);
		matcher.find();
		String matched = matcher.group();
		//because of how the sim runs, hr 0-4 is same/next day
		double hour = Double.parseDouble(matched.substring(0, 2));
		if(hour<4)
			hour += 24;
		return (hour*60+Double.parseDouble(matched.substring(2, 4)))*60;
	}
}