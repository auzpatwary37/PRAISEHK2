package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;



/**
 * 
 * @author Ashraf
 *
 */

public class CNLTransitDirectLink extends TransitDirectLink{
	private Scenario scenario;
	public CNLTransitDirectLink(String startStopId, String endStopId, Id<Link> startLinkId, Id<Link> endLinkId,
			TransitSchedule ts, String lineId, String routeId, Scenario scenario) {
		super(startStopId, endStopId, startLinkId, endLinkId, ts, lineId, routeId);
		this.scenario=scenario;
		this.TrLinkId=Id.create(startStopId.replaceAll("\\s+","")+"_"+endStopId.replaceAll("\\s+","")+"_"+
		lineId.replaceAll("\\s+","")+"_"+routeId.replaceAll("\\s+",""),TransitLink.class);
		
	}
	public CNLTransitDirectLink(String RouteDescription, Id<Link> startLinkId, Id<Link> endLinkId,
			TransitSchedule ts,Scenario scenario){
		this(RouteDescription.split("===")[1].trim(), RouteDescription.split("===")[4].trim(), 
				startLinkId, endLinkId, ts, RouteDescription.split("===")[2].trim(), 
				RouteDescription.split("===")[3].trim(),scenario);
	}
	

	//capacity of that link (60 by default)
	protected double capacity=60;
	protected double frequency=1;
	//time difference between two vehicles in second. default 300s
	protected double headway=300;
	
	private final Id<TransitLink> TrLinkId;
	
	/**
	 * calculates the link travel time 
	 */
	@Override
	public double getLinkTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams) {
		double travelTime=0;
		for(Id<Link> lId:this.linkList) {
			travelTime+=((AnalyticalModelLink)network.getLinks().get(lId)).getLinkTravelTime(timeBean,params,anaParams);
		}
		
		return travelTime;
	}
	@Override
	public void addPassanger(double d,AnalyticalModelNetwork network) {
		this.passangerCount+=d;
		for(Id<Link> clId:this.linkList) {
			((CNLLink)network.getLinks().get(clId)).addTransitPassengerVolume(calcLineRouteId(lineId, routeId), this.TrLinkId, d);
		}
	}
	public static String calcLineRouteId(String lineId,String routeId) {
		return lineId+"___"+routeId;
	}
	public double getCapacity() {
		return capacity;
	}
	public double getHeadway() {
		return headway;
	}
	@Override
	public Id<TransitLink> getTrLinkId() {
		return TrLinkId;
	}
	
	public void calcCapacityAndHeadway(Map<String, Tuple<Double, Double>> timeBeans,String timeBeanId) {
		Map<Id<Departure>,Departure>Departures= ts.getTransitLines().get(Id.create(lineId, TransitLine.class)).
				getRoutes().get(Id.create(routeId, TransitRoute.class)).getDepartures();
		int noofVehicle=0;
		this.capacity =0;
		for(Departure d:Departures.values()) {
			
			
			double time=d.getDepartureTime();
			if(time==0)time++;
			if(time>timeBeans.get(timeBeanId).getFirst() && time<=timeBeans.get(timeBeanId).getSecond()) {
				noofVehicle++;
				Id<Vehicle> vehicleId=d.getVehicleId();
				VehicleType vt = scenario.getTransitVehicles().getVehicles().get(vehicleId).getType();
				this.capacity+=vt.getCapacity().getSeats()+
						vt.getCapacity().getStandingRoom();
			}
		}
		this.frequency=noofVehicle;
		if(noofVehicle==0) {
			capacity=0;
			headway=timeBeans.get(timeBeanId).getSecond()-timeBeans.get(timeBeanId).getFirst();
		}else {
			this.capacity=this.capacity/noofVehicle;
			this.headway=(timeBeans.get(timeBeanId).getSecond()-timeBeans.get(timeBeanId).getFirst())/noofVehicle;
		}
		if(capacity==0) {
			System.out.println("No Capacity!!!");
		}
	}
	
	public void calcCapacityAndHeadway(Map<String,Map<String,Double>>vehicleCount,Map<String,Map<String,Double>>capacity,
			Map<String, Tuple<Double, Double>> timeBeans,String timeBeanId) {
		String key=this.linkList.get(0).toString()+"___"+this.lineId.toString()+"___"+this.routeId.toString();
		this.capacity=capacity.get(timeBeanId).get(key);
		this.headway=(timeBeans.get(timeBeanId).getSecond()-timeBeans.get(timeBeanId).getSecond())/(Double)vehicleCount.get(timeBeanId).get(key);
	}
	
	public CNLTransitDirectLink cloneLink(CNLTransitDirectLink tL) {
		return new CNLTransitDirectLink(tL.getStartStopId(),tL.getEndStopId(),tL.getStartingLinkId(),tL.getEndingLinkId(),tL.getTs(),tL.getLineId(),tL.getRouteId(),tL.getScenario());
		
	}
	protected Scenario getScenario() {
		return scenario;
	}
	public double getFrequency() {
		return frequency;
	}
	@Override
	public double getPhysicalDistance(Network network) {
		double distance=0;
		for(Id<Link> linkId:this.linkList) {
			distance+=network.getLinks().get(linkId).getLength();
		}
		return distance;
	}

}
