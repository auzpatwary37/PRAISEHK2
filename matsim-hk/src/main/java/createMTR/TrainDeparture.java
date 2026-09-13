package createMTR;

public class TrainDeparture implements Comparable<TrainDeparture> {
	int departureTime_s;
	String startStop;
	
	/**
	 * Constructor of the train departure
	 * @param startStop The start stop, in 3 alphabets form.
	 * @param departureTime_s The departure time in second, starts from 00:00.
	 */
	public TrainDeparture(String startStop, int departureTime_s) {
		this.startStop = startStop;
		this.departureTime_s = departureTime_s;
	}
	
	@Override
	public int compareTo(TrainDeparture arg0) {
		return Integer.compare(this.departureTime_s, arg0.departureTime_s);
	}
	
	@Override
	public String toString() {
		return "From "+startStop+" departure at "+departureTime_s;
	}
}
