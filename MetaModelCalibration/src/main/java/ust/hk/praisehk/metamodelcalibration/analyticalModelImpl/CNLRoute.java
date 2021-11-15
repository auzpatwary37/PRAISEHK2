package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.Utils.TruncatedNormal;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;



public class CNLRoute implements AnalyticalModelRoute{

	private Id<AnalyticalModelRoute> routeId;
	private double travelTime=0;
	private double distanceTravelled=0;
	private ArrayList<Id<Link>>links=new ArrayList<>();
	private double RouteUtility=0;
	private Map<Id<Link>,Double> linkReachTime=new HashMap<>();
	private final Id<AnalyticalModelRoute> oldRouteId;
	public static final String routeIdSubscript = "_r_";
	private List<PlanElement> planElements;
	
	public CNLRoute(Route r) {
		String[] part=r.getRouteDescription().split(" ");
		for(String s:part) {
			links.add(Id.createLinkId(s.trim()));
			}
		this.distanceTravelled=r.getDistance();
		this.routeId=Id.create(r.getRouteDescription(), AnalyticalModelRoute.class);
		this.oldRouteId = Id.create(r.getRouteDescription(), AnalyticalModelRoute.class);
	}
	
	public CNLRoute(Id<AnalyticalModelRoute> routeId,ArrayList<Id<Link>> linkList,double dist, List<PlanElement>pe) {
		this.links = new ArrayList<>(linkList);
		this.distanceTravelled=dist;
		this.routeId=Id.create(routeId.toString(), AnalyticalModelRoute.class);
		this.oldRouteId = Id.create(routeId.toString(), AnalyticalModelRoute.class);
		this.planElements = pe;
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
	
	public double getTravelTime(AnalyticalModelODpair odpair,Map<String,AnalyticalModelNetwork> network,String timeBeanId,String nextTimeBeanId,Map<String,Tuple<Double,Double>> timeBean,LinkedHashMap<String,Double> params,LinkedHashMap<String,Double>anaParams) {
		this.travelTime=0;
		int linkCounter=0;
		for(Id<Link> lId:this.links) {
			linkCounter++;
			this.linkReachTime.put(lId,this.travelTime);
			double tt1=((CNLLink)network.get(timeBeanId).getLinks().get(lId)).getLinkTravelTime(timeBean.get(timeBeanId),params,anaParams);
			if(linkCounter>1) {
				double tt2=((CNLLink)network.get(nextTimeBeanId).getLinks().get(lId)).getLinkTravelTime(timeBean.get(nextTimeBeanId),params,anaParams);
				double p1=odpair.getDepartureTimeDistributions().get(timeBeanId)
						.cumulativeProbability(timeBean.get(timeBeanId).getSecond()-this.linkReachTime.get(lId));
				this.travelTime+=tt1*p1+tt2*(1-p1);
			}else {
				this.travelTime+=tt1;
			}
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
	
	
	
	public double calcRouteUtility(LinkedHashMap<String, Double> parmas,LinkedHashMap<String, Double> anaParmas,Map<String,AnalyticalModelNetwork> network,
			Map<String,Tuple<Double,Double>>timeBean,AnalyticalModelODpair odpair,String timeBeanId,String nextTimeBeanId) {
		
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
				this.getTravelTime(odpair,network,timeBeanId,nextTimeBeanId,timeBean,parmas,anaParmas)*MUTravelTime+
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



	@Override
	public void updateToOdBasedId(Id<AnalyticalModelODpair> odId, int routeNumber) {
		this.routeId = Id.create(odId.toString()+routeIdSubscript+routeNumber, AnalyticalModelRoute.class);
	}



	@Override
	public Id<AnalyticalModelRoute> getOldRouteId() {
		
		return this.oldRouteId;
	}
	
	
	@Override
	public AnalyticalModelRoute clone() {
		return new CNLRoute(this.routeId,this.links,this.distanceTravelled,this.planElements);
	}

	@Override
	public void setPlanElements(List<PlanElement> pes) {
		this.planElements = pes;
		
	}

	@Override
	public List<PlanElement> getPlanElements() {
		return this.planElements;
	}
}
