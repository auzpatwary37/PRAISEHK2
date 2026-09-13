/**
 * 
 */
package createPTGTFS;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeSet;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

/**
 * @author JLo
 *
 */
public class PTDeparturesBuilder {
	
	private static final double splitTime = 4*60*60;
	private static final double midNight = 24*60*60;
	
	private TransitSchedule TS;
	private Vehicles TV;
	private VehicleType vt;
	private HashMap<String, DataFreqPT> PTDataFreqMap;
	
	public PTDeparturesBuilder(Scenario scenario, VehicleType vt, HashMap<String, DataFreqPT> PTDataFreqMap) {
		this.TS = scenario.getTransitSchedule();
		this.TV = scenario.getTransitVehicles();
		this.vt = vt;
		this.PTDataFreqMap = PTDataFreqMap;
	}
	
	public void makeDepartures(DataRoutePT bigRoutes, TransitRoute TR, TreeSet<String> tripIds, String routeId) {
		for(String TTripId: tripIds) {
			//is a range
			if(PTDataFreqMap.containsKey(TTripId)) {
				DataFreqPT freq = PTDataFreqMap.get(TTripId);
				
				//put to local memory to waste memory and easier read/type
				double startTime = freq.getStartTime();
				double endTime = freq.getEndTime();
				double headway = freq.getHeadway();
				
				if((startTime<splitTime) || (endTime>midNight)) {
					//stop supporting 24hr range where it is not 0000-2400 due to RIRO ruining everything
					if(startTime<splitTime)
						startTime+=midNight;
					double dummyStartTime = startTime;
					
					//finds the departure after 2400 if headway trip crosses midNight
					while(dummyStartTime<midNight) {
						dummyStartTime+=headway;
					}
					
					//GTFS specifies past trip freq startTime past 2400 endtime will be time+2400
					//builds the first occ
					if(endTime<midNight)
						makeHeadway(bigRoutes, TR, routeId, dummyStartTime-midNight, endTime, headway);
					else
						makeHeadway(bigRoutes, TR, routeId, dummyStartTime-midNight, endTime-midNight, headway);
					
					//builds second occ
					if(endTime<splitTime)
						makeHeadway(bigRoutes, TR, routeId, startTime, endTime+midNight, headway);
					else if(endTime>splitTime && endTime<midNight)
						makeHeadway(bigRoutes, TR, routeId, startTime, splitTime+midNight, headway);
					else if(endTime>splitTime+midNight)
						makeHeadway(bigRoutes, TR, routeId, startTime, splitTime+midNight, headway);
					else if(endTime>midNight && endTime<midNight+splitTime)
						makeHeadway(bigRoutes, TR, routeId, startTime, endTime, headway);
					else
						makeHeadway(bigRoutes, TR, routeId, startTime, midNight, headway);
				} else {
					makeHeadway(bigRoutes, TR, routeId, startTime, endTime, headway);
				}
			} else {	//is just single time
				int size = TTripId.length();
				double time = (Double.parseDouble(TTripId.substring(size-4, size-2))*60+Double.parseDouble(TTripId.substring(size-2, size)))*60;
				
				if(time>=splitTime && time<=midNight)
					make1Departure(bigRoutes, TR, routeId, time);
				else if(time<splitTime) {
					make1Departure(bigRoutes, TR, routeId, time);
					make1Departure(bigRoutes, TR, routeId, time+midNight);
				} else if(time>=midNight && time<=midNight+splitTime) {
					make1Departure(bigRoutes, TR, routeId, time);
					make1Departure(bigRoutes, TR, routeId, time-midNight);
				} else if(time>=midNight+splitTime)
					make1Departure(bigRoutes, TR, routeId, time-midNight);
			}
		}
	}
	
	private void makeHeadway(DataRoutePT bigRoutes, TransitRoute TR, String routeId, double startTime, double endTime, double Headway) {
		double time = startTime;
		
		while(time<=endTime) {
			make1Departure(bigRoutes, TR, routeId, time);
			time+=Headway;
		}
	}
	
	private void make1Departure(DataRoutePT bigRoutes, TransitRoute TR, String routeId, double time) {
		Departure d = TS.getFactory().createDeparture(Id.create(routeId+Math.round(time), Departure.class), time);
		Vehicle Tvic = TV.getFactory().createVehicle(Id.createVehicleId(routeId+"_"+bigRoutes.addBusNum()), vt);
		TV.addVehicle(Tvic);
		d.setVehicleId(Tvic.getId());
		if(!TR.getDepartures().containsKey(d.getId()))	//checks for same id such that won't create duplicate departures at the same time
			TR.addDeparture(d);
	}
	
	public void makeDepartureFromTimes(DataRoutePT bigRoutes, TransitRoute TR, String routeId, Collection<Integer> departureTimes) {
		//Given the departure times, create the departures
		for(int time: departureTimes) {
			if(time>=splitTime && time<=midNight)
				make1Departure(bigRoutes, TR, routeId, time);
			else if(time<splitTime) {
				make1Departure(bigRoutes, TR, routeId, time);
				make1Departure(bigRoutes, TR, routeId, time+midNight);
			} else if(time>=midNight && time<=midNight+splitTime) {
				make1Departure(bigRoutes, TR, routeId, time);
				make1Departure(bigRoutes, TR, routeId, time-midNight);
			} else if(time>=midNight+splitTime)
				make1Departure(bigRoutes, TR, routeId, time-midNight);
		}
	}
}
