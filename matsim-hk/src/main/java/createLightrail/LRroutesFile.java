/**
 * 
 */
package createLightrail;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author JLo
 *
 */
class LRroutesFile {
	private ArrayList<LRroute> LRroutes = new ArrayList<>();
	
	public ArrayList<LRroute> getLRroutes() {
		
		return LRroutes;
	}
	
	/**
	 * !!!this is in relation Id, not MTR route number!!!
	 * @param Id
	 * @return
	 */
	public LRroute getLRroute(String Id) {
		for(LRroute Temp: LRroutes)
			if(Temp.getId().equals(Id))
				return Temp;
		return null;
	}
	
	/**
	 * !!!this is in MTR route number!!!
	 * @param Id
	 * @return
	 */
	public boolean isCircular(String ref) {
		int i=0;
		for(LRroute temp: LRroutes)
			if(temp.getRouteNumber().equals(ref))
				i++;
		if(i==1)
			return true;
		else if(i==2)
			return false;
		else
			throw new IllegalArgumentException(ref+" does not exist, or there are more than 2 directions with the same route number???");
	}
	
	public ArrayList<LRroute> getLRroutesByRef(String Ref) {
		ArrayList<LRroute> output = new ArrayList<LRroute>();
		for(LRroute Temp: LRroutes)
			if(Temp.getRouteNumber().equals(Ref))
				output.add(Temp);
		return output;
	}
	
	public ArrayList<String> getLRroutesIdByRef(String Ref) {
		ArrayList<String> output = new ArrayList<String>();
		for(LRroute Temp: LRroutes)
			if(Temp.getRouteNumber().equals(Ref))
				output.add(Temp.getId());
		return output;
	}
}

class LRroute {
	private String id;
	private ArrayList<member> members = new ArrayList<>();
	private Rtags Rtags;
	
	/**
	 * !!!this is in node id, NOT MTR 3 digit code!!!
	 * @return
	 */
	public ArrayList<String> getStopsRef(){
		ArrayList<String> stops = new ArrayList<>();
		for(member Temp: members)
			if(Temp.getRole().equals("stop"))
				stops.add(Temp.getRef());
		
		//check route length
		if(stops.size()<2)
			throw new IllegalArgumentException(getName()+" TO: "+getTo()+" has less than 2 stops!!!");
		
		return stops;
	}
	
	public String getRouteNumber() {
		return Rtags.getRouteNumber();
	}
	
	public String getFrom() {
		return Rtags.getfrom();
	}
	
	public String getTo() {
		return Rtags.getto();
	}
	
	public String getName() {
		return Rtags.getName();
	}
	
	public String getId() {
		return id;
	}
	
	/**
	 * data input as minutes
	 * @return
	 */
	public double getRunTime() {
		return Rtags.getRunTime();
	}
	
	/**
	 * In seconds of runtime/(numofstops-1)
	 * @return
	 */
	public double avgTravelTimeBetweenStations() {
		double runtime = getRunTime();
		int numofstops = getStopsRef().size();
		return Math.floor(runtime*60/(numofstops-1))+1;
	}
}

class member {
	private String ref;
	private String role;
	
	public String getRole() {
		return role;
	}
	
	public String getRef() {
		return ref;
	}
}

class Rtags {
	private String from;
	private String name;
	private String ref;
	private String to;
	private String runtime;
	
	public String getfrom() {
		Pattern pattern = Pattern.compile("(\\p{Alpha}+\\p{Space})+");
		Matcher matcher = pattern.matcher(from);
		matcher.find();
		return from.substring(matcher.start());	//some how the pattern doesn't really work?? so did this as it can only reliably find the first work string
	}
	
	public String getto() {
		Pattern pattern = Pattern.compile("(\\p{Alpha}+\\p{Space})+");
		Matcher matcher = pattern.matcher(to);
		matcher.find();
		return to.substring(matcher.start());
	}
	
	public String getRouteNumber() {
		return ref;
	}
	
	public String getName() {
		return name;
	}
	
	public double getRunTime() {
		return Double.parseDouble(runtime);
	}
	
}
