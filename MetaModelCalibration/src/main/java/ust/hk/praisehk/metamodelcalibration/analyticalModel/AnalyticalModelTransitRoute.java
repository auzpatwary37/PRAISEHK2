package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitFareAndHandler.FareLink;





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
	public double calcRouteUtility(LinkedHashMap<String,Double> params,
			LinkedHashMap<String,Double> AnaParam,AnalyticalModelNetwork network,Map<Id<TransitLink>,TransitLink>transitLinks, Map<String,FareCalculator> farecalc,Map<String,Object> AdditionalDataContainer, Tuple<Double,Double>timeBean);

	
	/**
	 * Calculates the route fare
	 * @param fc
	 * @return
	 */
	public double getFare(TransitSchedule ts,Map<String,FareCalculator> farecalc, Map<String,Object> AdditionalDataContainer);
	
	/**
	 * Calculates the route travel Time (Only direct Link Travel Times are taken)
	 * @param network
	 * @return
	 */
	public double calcRouteTravelTime(AnalyticalModelNetwork network,Map<Id<TransitLink>,TransitLink>transitLinks, Tuple<Double,Double>timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams);
	/**
	 * returns the route total walking distance 
	 * @return
	 */
	public double getRouteWalkingDistance();
	/**
	 * Calculates and returns the route waiting time
	 * @return
	 */
	public double getRouteWaitingTime(LinkedHashMap<String,Double> params,LinkedHashMap<String,Double> anaParams,AnalyticalModelNetwork network, Map<Id<TransitLink>,TransitLink>transitLinks);

	public Id<AnalyticalModelTransitRoute> getTrRouteId();
	
	public ArrayList<Id<TransitLink>> getTrLinkIds();
	
	public Map<String, Double> getRouteCapacity();
	
	public void calcCapacityHeadway(Map<String, Tuple<Double, Double>> timeBean,String timeBeanId);
	
	public AnalyticalModelTransitRoute cloneRoute();
	
	public double getRouteDistance(Network network);
	
	/**
	 * Use this with proper caution. This do not hold proper information about the link volumes as the link volume is only updated exogenously. 
	 * @return
	 */
	public Map<Id<TransitLink>, TransitLink> getTransitLinks();
	
	public List<TransitDirectLink> getTransitDirectLinks();
	public List<TransitTransferLink> getTransitTransferLinks();
	
	public List<Id<Link>> getPhysicalLinks();
	
	public List<FareLink> getFareLinks();
	
	public void updateToOdBasedId(Id<AnalyticalModelODpair> odId, int routeNumber);
	public Id<AnalyticalModelTransitRoute> getOldTrRouteId();
	public void setPlanElements(List<PlanElement> pes);
	public List<PlanElement> getPlanElements();
}
