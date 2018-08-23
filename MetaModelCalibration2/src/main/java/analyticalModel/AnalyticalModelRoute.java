package analyticalModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

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
	public abstract double getTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams);
	
	/**
	 * This one gives the total route distance 
	 * For distance based money cost
	 * @return
	 */
	public abstract double getRouteDistance();
	
	/**
	 * This calculates the route utility of the specific routes
	 * params are the linked Hash map containing all the parameter values
	 * @param parmas
	 * @return
	 */
	public abstract double calcRouteUtility(LinkedHashMap<String,Double> parmas,LinkedHashMap<String,Double> anaParam,AnalyticalModelNetwork network,Tuple<Double,Double>timeBean);
	
	/**
	 * This is for link toll or other moneytery cost
	 * @return
	 */
	public abstract double getOtherMoneyCost();
	public abstract String getRouteDescription();
	public abstract Id<AnalyticalModelRoute> getRouteId();
	public abstract ArrayList<Id<Link>> getLinkIds();
	
}
