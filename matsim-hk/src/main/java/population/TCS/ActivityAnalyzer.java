package population.TCS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
/**
 * 
 * @author Ashraf
 *
 */
public class ActivityAnalyzer {
	private HashMap<String,Tuple<Double,Integer>> averageStartingTimeCalculator=new HashMap<>();
	private HashMap<String,Double>activityDuration=new HashMap<>();
	/**
	 * This function finds the average activity duration for each activity inside a popualtion file
	 * @param population
	 * @return
	 */
	public HashMap<String,Double> getAverageActivityDuration(Population population) {
		HashMap<String,Tuple<Double,Integer>> activities = new HashMap<>(); //total time, average
		for(Person p:population.getPersons().values()) {
			for(PlanElement pe:p.getSelectedPlan().getPlanElements()) {
				if(pe instanceof Activity) {
					Activity a = (Activity)pe;
					
					if(a.getStartTime().seconds()==Double.NEGATIVE_INFINITY && a.getEndTime().seconds()!=Double.NEGATIVE_INFINITY) {
						if(a.getStartTime().seconds()>a.getEndTime().seconds()) {
							a.setEndTime(24*3600);
						}
						double duration=a.getEndTime().seconds() - a.getStartTime().seconds();
						if(duration<0) {
							throw new IllegalArgumentException("duration can not be negative");
						}
						if(activities.containsKey(a.getType())) {
							Tuple<Double,Integer> oldActDetails=activities.get(a.getType());
							Tuple<Double,Integer> newActDetails=new Tuple<>(oldActDetails.getFirst()+duration
									,oldActDetails.getSecond()+1);
							activities.put(a.getType(), newActDetails);
						}else {
							Tuple<Double,Integer> newActDetails=new Tuple<>(duration,1);
							activities.put(a.getType(), newActDetails);
						}
					}
				}
			}
		}
		
		HashMap<String,Double> actDurations = new HashMap<>(); //Make it to better map
		for(String s:activities.keySet()) {
			actDurations.put(s,activities.get(s).getFirst() / activities.get(s).getSecond().doubleValue());
		}
		this.activityDuration=actDurations;
		return actDurations;
	}

	public HashMap<String, Double> getAverageStartingTime(Population population) {
		for(Person p:population.getPersons().values()) {
			for(PlanElement pe:p.getSelectedPlan().getPlanElements()) {
				if(pe instanceof Activity) {
					Activity a=(Activity)pe;
					if(averageStartingTimeCalculator.containsKey(a.getType())&&a.getStartTime().seconds()!=Double.NEGATIVE_INFINITY) {
						Tuple<Double,Integer>oldTuple=this.averageStartingTimeCalculator.get(a.getType());
						Tuple<Double,Integer>newTuple=new Tuple<>(oldTuple.getFirst()+a.getStartTime().seconds(),oldTuple.getSecond()+1);
						this.averageStartingTimeCalculator.put(a.getType(),newTuple);
					}else {
						Tuple<Double,Integer>newTuple=new Tuple<>(a.getStartTime().seconds(),1);
						this.averageStartingTimeCalculator.put(a.getType(),newTuple);
					}
				}
			}
		}
		HashMap<String, Double> averageStartingTime=new HashMap<>();
		for(String s:this.averageStartingTimeCalculator.keySet()) {
			averageStartingTime.put(s, this.averageStartingTimeCalculator.get(s).getFirst()/this.averageStartingTimeCalculator.get(s).getSecond());
		}
		return averageStartingTime;
	}

	/**
	 * This function splits an activity into multiple activity and writes the activityparams on the config file
	 * @param population
	 * @param config
	 * @param activityType
	 * @param timeGapInSecond
	 */
	public static void ActivitySplitter(Population population,Config config, String activityType,Double timeGapInSecond) {
		HashMap<String,Tuple<Double,Double>> activities=new HashMap<>();
		HashMap<String,Integer> activityCounter=new HashMap<>();
		HashMap<String,Double> activityDurationSum=new HashMap<>();
		double startTime=0;
		double endTime=24*3600;
		for(double d=startTime;d<endTime;d=d+timeGapInSecond) {
			activities.put(activityType+"_"+d, new Tuple<>(d,d+timeGapInSecond));
			activityCounter.put(activityType+"_"+d, 0);
			activityDurationSum.put(activityType+"_"+d, 0.);
		}
		//System.out.println("testing");
		for(Person p:population.getPersons().values()) {
			for(PlanElement pe:p.getSelectedPlan().getPlanElements()) {
				if(pe instanceof Activity) {
					Activity a=(Activity)pe;
					if(a.getType().equals(activityType)) {
						for(Tuple<Double,Double>t:activities.values()) {
							if(a.getStartTime().seconds()>=t.getFirst()&&a.getStartTime().seconds()<t.getSecond()&&a.getStartTime().seconds()!=Double.NEGATIVE_INFINITY) {
								a.setType(activityType+"_"+t.getFirst());
								activityCounter.put(activityType+"_"+t.getFirst(),activityCounter.get(activityType+"_"+t.getFirst())+1);
								Double duration=0.;
								if(a.getEndTime().seconds()==Double.NEGATIVE_INFINITY) {
									duration=8*3600.;
								}else if(a.getEndTime().seconds()-a.getStartTime().seconds()>0) {
									duration=a.getEndTime().seconds()-a.getStartTime().seconds();
								}else if((a.getEndTime().seconds()-a.getStartTime().seconds())<0){
									duration=a.getEndTime().seconds()+24*3600-a.getStartTime().seconds();
								}else if(a.getEndTime().seconds()-a.getStartTime().seconds()==0) {
									duration=1.;
								}
								if(duration<0) {
									throw new IllegalArgumentException("duration can not be negative!!!!");
								}
								double currentSum=activityDurationSum.get(activityType+"_"+t.getFirst());
								if(currentSum<0) {
									throw new IllegalArgumentException("Duration cannot be negative!!!");
								}
								activityDurationSum.put(activityType+"_"+t.getFirst(),currentSum+duration);
								break;
							}
						}
					}
				}
			}
		}
		ActivityParams aParams=config.planCalcScore().getActivityParams(activityType);
		for(String s:activityCounter.keySet()) {
			if(activityCounter.get(s)!=0) {
				ActivityParams ap=new ActivityParams(s);
				if(activityDurationSum.get(s)<0) {
					throw new IllegalArgumentException("TypicalDuration can not be negative!!!");
				}
				ap.setTypicalDuration(activityDurationSum.get(s)/activityCounter.get(s));
				ap.setClosingTime(aParams.getClosingTime().seconds());
				ap.setLatestStartTime(activities.get(s).getSecond());
				ap.setOpeningTime(activities.get(s).getFirst());
				config.planCalcScore().addActivityParams(ap);
			}
		}
	}
	
	public static void addActivityPlanParameter(PlanCalcScoreConfigGroup config, ArrayList<String>activityTypes,
			HashMap<String,Double>typicalDurations, HashMap<String,Double> typicalStartingTime, 
			int addedlatestStartTime, int earliestStartTime, int defaultTypicalDuration, 
			int defaultTypicalStartingTime, int defaultOpenningTime){
		
		if(activityTypes==null) {
			activityTypes=new ArrayList<String>();
			for(String s:typicalStartingTime.keySet()) {
				if(!activityTypes.contains(s)) {
					activityTypes.add(s);
				}
			}
			for(String s:typicalDurations.keySet()) {
				if(!activityTypes.contains(s)) {
					activityTypes.add(s);
				}
			}
		}
		for(String s:activityTypes) {
			ActivityParams act = new ActivityParams(s);
			if(typicalDurations.get(s)!=null) {
				act.setTypicalDuration(typicalDurations.get(s));
			}else {
				act.setTypicalDuration(defaultTypicalDuration);
			}
			if(typicalStartingTime.get(s)!=null) {
				act.setLatestStartTime(typicalStartingTime.get(s)+15*60);
				act.setOpeningTime(typicalStartingTime.get(s)-3600);
			}else {
				act.setLatestStartTime(0+defaultTypicalStartingTime);
				act.setOpeningTime(defaultOpenningTime);
			}
			act.setClosingTime(26*3600);
			config.addActivityParams(act);
		}
	}

	public static void main(String[] args) {
		Config config=ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config,"data/FinalHKITCSandGVTCS/config.xml");
		config.plans().setInputFile("data/FinalHKITCSandGVTCS/populationHKI.xml");
		//PopulationReader popreader=new PopulationReader();
		Scenario scenario=ScenarioUtils.loadScenario(config);
		Population population=scenario.getPopulation();
		ActivityAnalyzer.ActivitySplitter(population, config, "Usual place of work", 60*30.);
		new PopulationWriter(population).write("data/FinalHKITCSandGVTCS/populationHKIActivitySplitted.xml");
		new ConfigWriter(config).write("data/FinalHKITCSandGVTCS/configHKISplitted.xml");
	}
}

