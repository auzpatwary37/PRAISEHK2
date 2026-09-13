/**
 * 
 */
package createPTGTFS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A generic route record for PT data extracted from GTFS
 * @author JLo
 *
 */
public class DataRoutePT{
	private String route_short_name;
	private String route_operator;
	private HashMap<String, TreeMap<Integer, String>> stopsMap;		//relies on the sorted nature of this map to create TR in a sequential manner
	private ConcurrentHashMap<String, ConcurrentHashMap<String, Tuple<Double, ArrayList<Integer>>>> stopsOccMap;	//this is created on run and stopsMap is used for saving and loading, variables: routeIdDir stopID minFare(from that stop) seqOfStop
	private TreeMap<String, String> tripIdMap;	//key is trip_Id; value is XXXX_X (routeId;direction) and key for stopsMap, fareMap, TRIdMap
	private TreeMap<String, Id<TransitRoute>> TRIdMap;	//Key is XXXX_X (routeId;direction)
	private final ConcurrentHashMap<String, DataFarePT> fareMap;
	private HashSet<String> routeIdSet;			//format XXXX
	private int vicCreatationNumber;	//just to keep track of number of buses are created for making id
	
	public DataRoutePT(String routeName, String routeOperator, String routeId) {
		this.route_short_name = routeName;
		this.route_operator = routeOperator;
		this.stopsMap = new HashMap<String, TreeMap<Integer, String>>();
		this.tripIdMap = new TreeMap<String, String>();
		this.fareMap = new ConcurrentHashMap<String, DataFarePT>();
		this.routeIdSet = new HashSet<String>();
		this.routeIdSet.add(routeId);
		this.vicCreatationNumber = 0;
		this.TRIdMap = new TreeMap<String, Id<TransitRoute>>();
	}
	
	/**
	 * Route number only
	 * @return
	 */
	public String getName() {
		return this.route_short_name;
	}
	
	/**
	 * In form of (this.route_operator+"_"+this.route_short_name)
	 * @return
	 */
	public String getId() {
		return (this.route_operator+"_"+this.route_short_name);
	}
	
	public String getOperator() {
		return this.route_operator;
	}
	
	public HashSet<String> getRouteIds() {
		return this.routeIdSet;
	}
	
	/**
	 * <b>DO NOT rely on the sequential numbering of the key, it may skip due to consecutive duplicate stops
	 * @return
	 */
	public HashMap<String, TreeMap<Integer, String>> getStopsMap() {
		return this.stopsMap;
	}
	
	public ConcurrentHashMap<String,Tuple<Double,ArrayList<Integer>>> getStopsOccMap(String routeIdDir) {
		return this.stopsOccMap.get(routeIdDir);
	}
	
	public TreeMap<String, String> getTripIdMap() {
		return this.tripIdMap;
	}
	
	public ConcurrentHashMap<String, DataFarePT> getFareMap() {
		return this.fareMap;
	}
	
	public TreeMap<String, Id<TransitRoute>> getTRIdMap() {
		return this.TRIdMap;
	}
	
	public boolean contains_tripId(String tripId) {
		return this.tripIdMap.containsKey(tripId);
	}
	
	public void addTripId(String trip_id) {
		Matcher matcher = Pattern.compile("\\d+-\\d").matcher(trip_id);
		matcher.find();
		this.tripIdMap.put(trip_id, matcher.group());
	}
	
	public void putStopId(String trip_id, int stop_sequence, String stop_id) {
		String mapping = this.tripIdMap.get(trip_id);
		//check for key corr to trip_id
		if(!this.stopsMap.containsKey(mapping))
			this.stopsMap.put(mapping, new TreeMap<Integer, String>());
		//check for seq key
		if(!this.stopsMap.get(mapping).containsKey(Integer.valueOf(stop_sequence)))
			this.stopsMap.get(mapping).put(stop_sequence, stop_id);
	}
	
	public void putTRId(String smallRouteKey, Id<TransitRoute> TRId) {
		this.TRIdMap.put(smallRouteKey, TRId);
	}
	
	public Id<TransitRoute> getTRId(String smallRouteKey) {
		return this.TRIdMap.get(smallRouteKey);
	}
	
	public boolean containsPTDataFare(String smallRouteKey) {
		if(this.fareMap.containsKey(smallRouteKey))
			return true;
		else
			return false;
	}
	
	public DataFarePT getOrCreatePTDataFare(String smallRouteKey) {
		if(!this.fareMap.containsKey(smallRouteKey))
			this.fareMap.put(smallRouteKey, new DataFarePT(smallRouteKey));
		
		return this.fareMap.get(smallRouteKey);
	}
	
	public DataFarePT getPTDataFare(String smallRouteKey) {
		return this.fareMap.get(smallRouteKey);
	}
	
	public int addBusNum() {
		this.vicCreatationNumber++;
		return this.vicCreatationNumber;
	}
	
	@Deprecated
	public void cleanStopsMap() {
		Iterator<Entry<String, TreeMap<Integer, String>>> itr = this.stopsMap.entrySet().iterator();
		HashSet<String> toRemove = new HashSet<String>();
		while(itr.hasNext()) {
			Entry<String, TreeMap<Integer, String>> toCheck = itr.next();
			if(toRemove.contains(toCheck.getKey())) {
				itr.remove();
				continue;
			}
			//compare the stops list
			for(Entry<String, TreeMap<Integer, String>> e: this.stopsMap.entrySet())
				if(toCheck.getValue().equals(e.getValue())) {
					toRemove.add(e.getKey());
				}
			//replaces tripId mapping to first occurring stops key
			for(Entry<String, String> e2: this.tripIdMap.entrySet())
				if(toRemove.contains(e2.getValue()))
					e2.setValue(toCheck.getKey());
		}
	}
	
	/**
	 * removes consecutive duplicate stops without renumbering the stop seq<br>
	 * such that hopefully the fare will be fine?
	 */
	public void cleanDuplicateStops() {
		for(TreeMap<Integer, String> stopList: this.stopsMap.values()) {
			Iterator<Entry<Integer,String>> itr = stopList.entrySet().iterator();
			String prev = itr.next().getValue();
			while(itr.hasNext()) {
				Entry<Integer,String> e = itr.next();
				if(prev.equals(e.getValue())) {
					itr.remove();
					continue;
				}
				prev = e.getValue();
			}
		}
	}
	
	/**
	 * converts stopsMap which contains inefficient treeMap to stopsOccMap for quicker access
	 */
	public void optimiseStopsMapForFareCal() {
		this.stopsOccMap = new ConcurrentHashMap<String, ConcurrentHashMap<String, Tuple<Double, ArrayList<Integer>>>>();
		for(Entry<String, TreeMap<Integer, String>> stopsMapEntry: this.stopsMap.entrySet()) {
			ConcurrentHashMap<String, Tuple<Double, ArrayList<Integer>>> stopOccMap = new ConcurrentHashMap<String, Tuple<Double, ArrayList<Integer>>>();
			for(Entry<Integer, String> stopEntry: stopsMapEntry.getValue().entrySet()) {
				if(stopOccMap.containsKey(stopEntry.getValue())) {
					stopOccMap.get(stopEntry.getValue()).getSecond().add(stopEntry.getKey());
				} else {
					ArrayList<Integer> occList = new ArrayList<Integer>();
					occList.add(stopEntry.getKey());
					double minFare = this.getPTDataFare(stopsMapEntry.getKey()).getMinFare(stopEntry.getValue());
					stopOccMap.put(stopEntry.getValue(), new Tuple<Double, ArrayList<Integer>>(minFare, occList));
				}
			}
			this.stopsOccMap.put(stopsMapEntry.getKey(), stopOccMap);
		}
	}
	
	public HashMap<String, TreeSet<String>> invertTripIdMap() {
		HashMap<String, TreeSet<String>> output = new HashMap<String, TreeSet<String>>();
		for(Entry<String,String> tripIdEntry: this.tripIdMap.entrySet()) {
			if(!output.containsKey(tripIdEntry.getValue()))
				output.put(tripIdEntry.getValue(), new TreeSet<String>());
			output.get(tripIdEntry.getValue()).add(tripIdEntry.getKey());
		}
		
		return output;
	}
	
	//useless?
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o instanceof DataRoutePT)
			if(((DataRoutePT) o).getName().equals(route_short_name) && ((DataRoutePT) o).getOperator().equals(route_operator))
				return true;
		return false;
	}

}
