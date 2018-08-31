package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import dynamicTransitRouter.fareCalculators.FareCalculator;





/**
 * 
 * @author Ashraf
 *
 * @param <anaNet> Type of network that has been used
 */

public interface AnalyticalModelTransitRoute{
	
	/**
	 * calculates the route utility
	 * @param params
	 * @return
	 */
	public abstract double calcRouteUtility(LinkedHashMap<String,Double> params,
			LinkedHashMap<String,Double> AnaParam,AnalyticalModelNetwork network,Map<String,FareCalculator> farecalc,Tuple<Double,Double>timeBean);
	/**
	 * Calculates the route fare
	 * @param fc
	 * @return
	 */
	public abstract double getFare(TransitSchedule ts,Map<String,FareCalculator> farecalc);
	
	/**
	 * Calculates the route travel Time (Only direct Link Travel Times are taken)
	 * @param network
	 * @return
	 */
	public abstract double calcRouteTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams);
	/**
	 * returns the route total walking distance 
	 * @return
	 */
	public abstract double getRouteWalkingDistance();
	/**
	 * Calculates and returns the route waiting time
	 * @return
	 */
	public abstract double getRouteWaitingTime(LinkedHashMap<String,Double> anaParams,AnalyticalModelNetwork network);

	public abstract Id<AnalyticalModelTransitRoute> getTrRouteId();
	
	public abstract ArrayList<Id<TransitLink>> getTrLinkIds();
	
	public Map<String, Double> getRouteCapacity();
	
	public void calcCapacityHeadway(Map<String, Tuple<Double, Double>> timeBean,String timeBeanId);
	
	public abstract AnalyticalModelTransitRoute cloneRoute();
}
