package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
/**
 * 
 * @author Ashraf
 *
 * @param <anaNet> type of network used
 * must extend AnalyticalModelNetwork
 */
public interface AnalyticalModelRoute{
	
	/**
	 * This gives the travel time of the route 
	 * @return
	 */
	public double getTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams);
	
	
//	public abstract double getTravelTime(Map<String,AnalyticalModelNetwork> network,Map<String,Tuple<Double,Double>> timeBean,String timeBeanId,
//			double startTime, LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams);
//	
	/**
	 * This one gives the total route distance 
	 * For distance based money cost
	 * @return
	 */
	public double getRouteDistance();
	
	/**
	 * This calculates the route utility of the specific routes
	 * params are the linked Hash map containing all the parameter values
	 * @param parmas
	 * @return
	 */
	public double calcRouteUtility(LinkedHashMap<String,Double> parmas,LinkedHashMap<String,Double> anaParam,AnalyticalModelNetwork network,Tuple<Double,Double>timeBean);
	
	/**
	 * This is for link toll or other moneytery cost
	 * @return
	 */
	public double getOtherMoneyCost();
	public String getRouteDescription();
	public Id<AnalyticalModelRoute> getRouteId();
	public ArrayList<Id<Link>> getLinkIds();
	public Map<Id<Link>, Double> getLinkReachTime(); 
	
	public static String getTimeId(Double time,Map<String,Tuple<Double,Double>>timeBeans) {
		if(time>24*3600) time=time-24*3600;
		if(time==0) time=1.;
		for(Entry<String, Tuple<Double, Double>> s:timeBeans.entrySet()) {
			if(time>s.getValue().getFirst() && time<=s.getValue().getSecond()) {
				return s.getKey();
			}
		}
		return null;
	}

	public abstract void updateToOdBasedId(Id<AnalyticalModelODpair> odId, int routeNumber);
	public Id<AnalyticalModelRoute> getOldRouteId();

//	double calcRouteUtility(LinkedHashMap<String, Double> parmas, LinkedHashMap<String, Double> anaParmas,
//			Map<String, AnalyticalModelNetwork> networks, Map<String, Tuple<Double, Double>> timeBean,
//			String timeBeanId, double startTime);
	public AnalyticalModelRoute clone();
}
