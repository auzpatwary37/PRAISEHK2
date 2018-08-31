package analyticalModel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import measurements.Measurements;





public interface AnalyticalModel {
	/**
	 * This method is a pseudo constructor.
	 * As MATSim do not allow passing null constructor in the controller listener, this method is necessary
	 * for setting up useful parameters from the MATSim simulation run.
	 * 
	 * This will set up the OD pairs along with all the routes
	 * 
	 */
	public void generateRoutesAndOD(Population population,Network network,TransitSchedule transitSchedule, Scenario scenario,Map<String,FareCalculator> fareCalculator);
	
	/**
	 * This performs SUE assignment in the network
	 * this can be single or multi-modal Assignment, but it will take calibration parameters and perform a SUE Assignment
	 * using the supplied parameters.
	 */
	public Map<String, Map<Id<Link>, Double>> perFormSUE(LinkedHashMap<String, Double> params);
	
	public Map<String, Map<Id<Link>, Double>> perFormSUE(LinkedHashMap<String, Double> params,LinkedHashMap<String, Double> anaParams);
	
	public void clearLinkCarandTransitVolume();
	
	public abstract Population getLastPopulation();

	public void setDefaultParameters(LinkedHashMap<String, Double> defaultParam);

	public LinkedHashMap<String, Double> getAnalyticalModelInternalParams();

	public Map<String, Tuple<Double, Double>> getTimeBeans();

	public LinkedHashMap<String, Tuple<Double, Double>> getAnalyticalModelParamsLimit();
	
	public static final String MarginalUtilityofTravelCarName="MarginalUtilityofTravelCar";
	public static final String MarginalUtilityofDistanceCarName="MarginalUtilityofDistanceCar";
	public static final String MarginalUtilityofMoneyName="MarginalUtilityofMoney";
	public static final String DistanceBasedMoneyCostCarName="DistanceBasedMoneyCostCar";
	public static final String MarginalUtilityofTravelptName="MarginalUtilityofTravelpt";
	public static final String MarginalUtilityOfDistancePtName="MarginalUtilityOfDistancePt";
	public static final String MarginalUtilityofWaitingName="MarginalUtilityofWaiting";
	public static final String UtilityOfLineSwitchName="UtilityOfLineSwitch";
	public static final String MarginalUtilityOfWalkingName="MarginalUtilityOfWalking";
	public static final String DistanceBasedMoneyCostWalkName="DistanceBasedMoneyCostWalk";
	public static final String ModeConstantPtname="ModeConstantPt";
	public static final String ModeConstantCarName="ModeConstantCar";
	public static final String MarginalUtilityofPerformName="MarginalUtilityofPerform";
	public static final String CapacityMultiplierName="CapacityMultiplier";
	
	public Map<Integer, Measurements> calibrateInternalParams(Map<Integer, Measurements> simMeasurements,
			Map<Integer, LinkedHashMap<String, Double>> params, LinkedHashMap<String, Double> initialParam,
			int currentParamNo);
}
