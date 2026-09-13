package population;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import population.TCS.ActivityAnalyzer;
import population.TCS.TCSExtractor;
import population.GVTCS.GVTCSConverter;
/**
 * 
 * @author ashraf
 *
 */
public class CreateTCSandGVTCSPopulation {
	private static final boolean hkiOnly=true; //True indicates only consider trips in HKI
	private static final double weightFactorgvtcs=1.0;
	private static final double weightFactorTCS=1.0;
	//private static Double tripPerson=0.;
	//private static Double personPerson=0.;
	
	public static void main(String[] args) throws IOException {
		/**
		 * TCS Database
		 */
		//Load the database into the scenario
		Double tripPerson=0.;
		Double personPerson=0.;
		Config config=ConfigUtils.createConfig();
		Population population = PopulationUtils.createPopulation(config);
		Vehicles vehicles = VehicleUtils.createVehiclesContainer();
		
		HashMap<Double,String> activityDetailsTCS = TCSExtractor.fillPopulationAndVehicleByTCS(population, vehicles, 
				tripPerson, personPerson, weightFactorTCS, hkiOnly);
		System.out.println("Ended create normal vehicle, Creating GV");
		
		System.out.println("tripPerson after TCS = "+tripPerson);
		System.out.println("personPerson after TCS = "+personPerson);
		/**
		 * GVTCS Database
		 */
		HashMap<Double,String> activityDetailsgvtcs = GVTCSConverter.fillPopulationAndVehicleByGVTCS(population, vehicles, 
				tripPerson, personPerson, weightFactorgvtcs, hkiOnly);
		
		System.out.println("tripPerson after GVTCS = "+tripPerson);
		System.out.println("personPerson after GVTCS = "+personPerson);
		
		//Do the activity analyses
		ActivityAnalyzer ac = new ActivityAnalyzer();
		HashMap<String,Double> activityDuration= ac.getAverageActivityDuration(population);
		HashMap<String,Double> activityStartTime = ac.getAverageStartingTime(population);
		ArrayList<String> activityTypes = new ArrayList<>();
		activityTypes.addAll(activityDetailsTCS.values());
		activityTypes.addAll(activityDetailsgvtcs.values());
		
		//TODO: Fix the repeated addition
		ActivityAnalyzer.addActivityPlanParameter(config.planCalcScore(), activityTypes, activityDuration, activityStartTime, 
				15*60, 30*60, 8*60*60, 15*60, 0);
		
		for(String s : activityTypes) {
			if(activityDuration.containsKey(s)) {
				if(activityDuration.get(s)==0) {
					activityDuration.put(s, 1.0);
				}
				PopGenUtils.addActivityPlanParameter(config.planCalcScore(), s, activityDuration.get(s).intValue());
			}else {
				PopGenUtils.addActivityPlanParameter(config.planCalcScore(), s, 8*60*60);
			}
		}
		ActivityAnalyzer.ActivitySplitter(population, config, "Usual place of work", 60*30.);
		
		PopGenUtils.writeFiles("output/FinalHKITCSandGVTCS/", config, population, vehicles);
		System.out.println("total Population = "+population.getPersons().size());
		System.out.println("total Vehicles = "+vehicles.getVehicles().size());
	}
}


