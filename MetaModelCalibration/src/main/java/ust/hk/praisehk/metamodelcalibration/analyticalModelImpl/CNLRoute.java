package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.Utils.TruncatedNormal;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;



public class CNLRoute implements AnalyticalModelRoute{

	private final Id<AnalyticalModelRoute> routeId;
	private double travelTime=0;
	private double distanceTravelled=0;
	private ArrayList<Id<Link>>links=new ArrayList<>();
	private double RouteUtility=0;
	private Map<Id<Link>,Double> linkReachTime=new HashMap<>();
	
	
	public CNLRoute(Route r) {
		String[] part=r.getRouteDescription().split(" ");
		for(String s:part) {
			links.add(Id.createLinkId(s.trim()));
			}
		this.distanceTravelled=r.getDistance();
		this.routeId=Id.create(r.getRouteDescription(), AnalyticalModelRoute.class);
	}
	
	
	
	@Override
	public double getTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,LinkedHashMap<String,Double> params,LinkedHashMap<String,Double>anaParams) {
		this.travelTime=0;
		for(Id<Link> lId:this.links) {
			this.linkReachTime.put(lId,this.travelTime);
			this.travelTime+=((CNLLink)network.getLinks().get(lId)).getLinkTravelTime(timeBean,params,anaParams);
		}
		return this.travelTime;
	}
	@Override
	public double getRouteDistance() {
		
		return this.distanceTravelled;
	}
	
	/**
	 * This is one of the most important and tricky function
	 * Takes all the parameters as input and calculates the route utility
	 * 
	 * The current utility function: 
	 * Will be designed later
	 *  
	 */
	
	@Override
	public double calcRouteUtility(LinkedHashMap<String, Double> parmas,LinkedHashMap<String, Double> anaParmas,AnalyticalModelNetwork network,Tuple<Double,Double>timeBean) {
		
		double MUTravelTime=parmas.get(CNLSUEModel.MarginalUtilityofTravelCarName)/3600.0-parmas.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double ModeConstant;
		if(parmas.get(CNLSUEModel.ModeConstantCarName)==null) {
			ModeConstant=0;
		}else {
			ModeConstant=parmas.get(CNLSUEModel.ModeConstantCarName);
		}
		Double MUMoney=parmas.get(CNLSUEModel.MarginalUtilityofMoneyName);
		if(MUMoney==null) {
			MUMoney=1.;
		}
		Double DistanceBasedMoneyCostCar=parmas.get(CNLSUEModel.DistanceBasedMoneyCostCarName);
		if(DistanceBasedMoneyCostCar==null) {
			DistanceBasedMoneyCostCar=0.;
		}
		double MUDistanceCar=parmas.get(CNLSUEModel.MarginalUtilityofDistanceCarName);
		
		this.RouteUtility=ModeConstant+
				this.getTravelTime(network,timeBean,parmas,anaParmas)*MUTravelTime+
				(MUDistanceCar+MUMoney*DistanceBasedMoneyCostCar)*this.getRouteDistance();
				
 		return this.RouteUtility*anaParmas.get(CNLSUEModel.LinkMiuName);
	}
	@Override
	public double getOtherMoneyCost() {
		// TODO This method is for future expansion
		return 0;
	}



	@Override
	public String getRouteDescription() {
		
		return this.routeId.toString();
	}

	@Override
	public Id<AnalyticalModelRoute> getRouteId(){
		return this.routeId;
	}

	@Override
	public ArrayList<Id<Link>> getLinkIds() {
		return this.links;
	}



//	@Override
//	public double getTravelTime(Map<String, AnalyticalModelNetwork> networks,
//			Map<String, Tuple<Double, Double>> timeBean, String timeBeanId, double startTime,
//			LinkedHashMap<String, Double> params, LinkedHashMap<String, Double> anaParams) {
//		this.travelTime=0;
//		String routeTimeId=AnalyticalModelRoute.getTimeId(startTime, timeBean);
//		this.linkReachTime.put(routeTimeId, new HashMap<>());
//		for(Id<Link> lId:this.links) {
//			String timeId=AnalyticalModelRoute.getTimeId(startTime, timeBean);
//			double linkTravelTime=((CNLLink)networks.get(timeId).getLinks().get(lId)).getLinkTravelTime(timeBean.get(timeId),params,anaParams);
//			this.travelTime+=linkTravelTime;
//			startTime+=linkTravelTime;
//			this.linkReachTime.get(routeTimeId).put(lId, timeId);
//		}
//		return this.travelTime;
//	}

//	@Override
//	public double calcRouteUtility(LinkedHashMap<String, Double> parmas,LinkedHashMap<String, Double> anaParmas,
//			Map<String,AnalyticalModelNetwork> networks,Map<String,Tuple<Double,Double>>timeBean,String timeBeanId,double startTime) {
//		
//		double MUTravelTime=parmas.get(CNLSUEModel.MarginalUtilityofTravelCarName)/3600.0-parmas.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
//		double ModeConstant;
//		if(parmas.get(CNLSUEModel.ModeConstantCarName)==null) {
//			ModeConstant=0;
//		}else {
//			ModeConstant=parmas.get(CNLSUEModel.ModeConstantCarName);
//		}
//		Double MUMoney=parmas.get(CNLSUEModel.MarginalUtilityofMoneyName);
//		if(MUMoney==null) {
//			MUMoney=1.;
//		}
//		Double DistanceBasedMoneyCostCar=parmas.get(CNLSUEModel.DistanceBasedMoneyCostCarName);
//		if(DistanceBasedMoneyCostCar==null) {
//			DistanceBasedMoneyCostCar=0.;
//		}
//		double MUDistanceCar=parmas.get(CNLSUEModel.MarginalUtilityofDistanceCarName);
//		
//		this.RouteUtility=ModeConstant+
//				this.getTravelTime(networks,timeBean,timeBeanId,startTime,parmas,anaParmas)*MUTravelTime+
//				(MUDistanceCar+MUMoney*DistanceBasedMoneyCostCar)*this.getRouteDistance();
//				
// 		return this.RouteUtility*anaParmas.get(CNLSUEModel.LinkMiuName);
//	}

	@Override
	public Map<Id<Link>, Double> getLinkReachTime() {
		return this.linkReachTime;
	}
	
	
	
	
}
