package analyticalModelImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.utils.collections.Tuple;

import analyticalModel.AnalyticalModelNetwork;
import analyticalModel.AnalyticalModelRoute;



public class CNLRoute implements AnalyticalModelRoute{

	private final Id<AnalyticalModelRoute> routeId;
	private double travelTime=0;
	private double distanceTravelled=0;
	private ArrayList<Id<Link>>links=new ArrayList<>();
	private double RouteUtility=0;
	
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
	
	
	
	
}
