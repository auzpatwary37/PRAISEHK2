/**
 * 
 */
package createPTGTFS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A generic Container for PT data extracted from GTFS<br>
 * A new Container should be instanced for each type of PT by the specific Reader
 * @author JLo
 *
 */
public class DataContainerPT {
	private ConcurrentHashMap<String, DataRoutePT> PTDataRouteMap;
	private HashMap<String, DataFreqPT> PTDataFreqMap;
	private HashMap<String, DataStopPT> PTDataStopMap;
	private HashSet<String> service_idSet;
	
	public DataContainerPT() {
		this.PTDataRouteMap = new ConcurrentHashMap<String, DataRoutePT>();
		this.PTDataFreqMap = new HashMap<String, DataFreqPT>();
		this.PTDataStopMap = new HashMap<String, DataStopPT>();
		this.service_idSet = new HashSet<String>();
	}
	
	public ConcurrentHashMap<String, DataRoutePT> getPTDataRouteMap() {
		return this.PTDataRouteMap;
	}
	
	public HashMap<String, DataFreqPT> getPTDataFreqMap() {
		return this.PTDataFreqMap;
	}
	
	public HashMap<String, DataStopPT> getPTDataStopMap() {
		return this.PTDataStopMap;
	}
	
	public HashSet<String> getServiceIdSet() {
		return this.service_idSet;
	}
	
	public DataRoutePT getPTDataRouteByroute_id(String route_id) {
		for(DataRoutePT thing: this.PTDataRouteMap.values())
			if(thing.getRouteIds().contains(route_id))
				return thing;
		return null;
	}
	
	public DataRoutePT getPTDataRouteBytrip_id(String trip_id) {
		for(DataRoutePT thing: this.PTDataRouteMap.values())
			if(thing.contains_tripId(trip_id))
				return thing;
		return null;
	}
	
	/**
	 * @param o
	 * @return true if successful <br>
	 * 		   false if map contains same key
	 */
	public boolean addPTDataRoute(DataRoutePT o) {
		if(this.PTDataRouteMap.containsKey(o.getId()))
			return false;
		else {
			this.PTDataRouteMap.put(o.getId(), o);
			return true;
		}
	}
	
	/**
	 * @param o
	 * @return true if successful <br>
	 * 		   false if map contains same key
	 */
	public boolean addPTDataFreq(DataFreqPT o) {
		if(this.PTDataFreqMap.containsKey(o.getTripId()))
			return false;
		else {
			this.PTDataFreqMap.put(o.getTripId(), o);
			return true;
		}
	}
	
	/**
	 * @param o
	 * @return true if successful <br>
	 * 		   false if map contains same key
	 */
	public boolean addPTDataStop(DataStopPT o) {
		if(this.PTDataStopMap.containsKey(o.getStopId()))
			return false;
		else {
			this.PTDataStopMap.put(o.getStopId(), o);
			return true;
		}
	}
}


