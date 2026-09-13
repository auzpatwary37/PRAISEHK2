/**
 * 
 */
package createPTGTFS;

/**
 * @author JLo
 *
 */
public class DataFreqPT {
	private String trip_id;
	private double startTime;
	private double endTime;
	private double headway;
	
	public DataFreqPT(String trip_id, String start_time, String end_time, String headway) {
		this.trip_id = trip_id;
		this.headway = Double.parseDouble(headway);
		this.startTime = (Double.parseDouble(start_time.substring(0, 2))*60+Double.parseDouble(start_time.substring(3, 5)))*60+Double.parseDouble(start_time.substring(6));
		this.endTime = (Double.parseDouble(end_time.substring(0, 2))*60+Double.parseDouble(end_time.substring(3, 5)))*60+Double.parseDouble(end_time.substring(6));
	}
	
	public String getTripId() {
		return this.trip_id;
	}
	
	public double getStartTime() {
		return this.startTime;
	}
	
	public double getEndTime() {
		return this.endTime;
	}
	
	public double getHeadway() {
		return this.headway;
	}
}