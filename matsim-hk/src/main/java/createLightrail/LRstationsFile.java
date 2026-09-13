/**
 * 
 */
package createLightrail;

import java.util.ArrayList;

/**
 * @author JLo
 *
 */
public class LRstationsFile {
	private ArrayList<LRstationData> LRstations = new ArrayList<>();
	
	public ArrayList<LRstationData> getLRstations(){
		return LRstations;
	}
	
	public LRstationData getLRstationData(String Id) {
		for(LRstationData Temp: LRstations)
			if(Temp.getId().equals(Id))
				return Temp;
		return null;
	}
}

class LRstationData {
	private String id;
	private double lat;
	private double lon;
	private Stags Stags;
	
	public String getId() {
		return id;
	}
	
	public double getlat() {
		return lat;
	}
	
	public double getlon() {
		return lon;
	}
	
	public String getName() {
		return Stags.getName();
	}
	
	public String getref() {
		return Stags.getref();
	}
	
}

class Stags {
	private String name;
	private String ref;
	
	public String getName() {
		return name;
	}
	
	public String getref() {
		return ref;
	}
	
}
