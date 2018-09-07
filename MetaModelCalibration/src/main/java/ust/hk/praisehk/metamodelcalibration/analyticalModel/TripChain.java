package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;



public abstract class TripChain{
	
	int no_of_trips=0;
	private List<Trip> chain=new ArrayList<>();
	private PopulationFactory popfac=ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation().getFactory();
	private static final Logger logger=Logger.getLogger(TripChain.class);
	
	/**
	 * Creating from a plan
	 * @param plan
	 */
	@SuppressWarnings("unchecked")
	public TripChain(Plan plan, TransitSchedule ts,Scenario scenario){
		//System.out.println();
		Id<Person>PersonId= plan.getPerson().getId();
		List<Leg> leglist=new ArrayList<>();
		List<Activity> activitylist=new ArrayList<>();
		int a=0;
		double pt_traveltime=0;
		double pt_starttime=0;
		int no_of_ptleg=0;
		ArrayList<Activity> ptactivityList=null;
		ArrayList<Leg>ptlegList=null;
		ArrayList<AnalyticalModelTransitRoute> ptlegs=new ArrayList<>();
		for(PlanElement planElement:plan.getPlanElements()){
			if(planElement instanceof Activity) {
				Activity activity=(Activity)planElement;
				if(activity.getType().equals("pt interaction") && a==0) {
					a=1;
					//pt leg started from the last activity
					ptactivityList=new ArrayList<>();
					ptlegList=new ArrayList<>();
					ptactivityList.add(activitylist.get(activitylist.size()-1));
					ptactivityList.add(activity);
					ptlegList.add(leglist.get(leglist.size()-1));
					pt_traveltime+=leglist.get(leglist.size()-1).getTravelTime();
					leglist.remove(leglist.size()-1);
					continue;
					
				}else if (activity.getType().equals("pt interaction") && a==1) {
					//pt activity continuation
					ptactivityList.add(activity);
					continue;
				}else if (!activity.getType().equals("pt interaction") && a==1){
					a=0;
					//pt activity end in this activity
					ptactivityList.add(activity);
					AnalyticalModelTransitRoute ptleg=this.getTransitRoute(ptlegList,ptactivityList,ts,scenario);
					ptlegs.add(ptleg);
					Leg l=popfac.createLeg("pt");
					l.setDepartureTime(ptactivityList.get(0).getEndTime());
					l.setTravelTime(pt_traveltime);
					leglist.add(l);
					pt_traveltime=0;
					activitylist.add(activity);
					continue;
				}else if(!activity.getType().equals("pt interaction") && a==0) {
					//General case
					activitylist.add(activity);
					continue;
				}
				
			}else {
				if(a==0) {
//					Leg ll=(Leg)planElement;
//					if(ll.getMode().equals("transit_walk")) {
//						
//					}
					leglist.add((Leg)planElement);
				}else if(a==1) {
					ptlegList.add((Leg)planElement);
					Leg l=(Leg)planElement;
					pt_traveltime+=l.getTravelTime();
				}
			}
		}
		int pttrip=0;
		for(int i=0;i<activitylist.size()-1;i++){
			Trip trip=this.createBlankTrip();
			trip.setAct1coord(activitylist.get(i).getCoord());
			trip.setAct2coord(activitylist.get(i+1).getCoord());
			trip.setStartTime(activitylist.get(i).getEndTime());
			trip.setEndTime(trip.getStartTime()+leglist.get(i).getTravelTime());
			trip.setMode(leglist.get(i).getMode());
			if(leglist.get(i).getMode().equals("car")){
				trip.setRoute(this.createRoute((leglist.get(i).getRoute())));
				
			}else if(leglist.get(i).getMode().equals("pt")){
				if(ptlegs.size()!=0) {
				trip.setTrRoute(ptlegs.get(pttrip));
				}
				
				pttrip++;
			}
			//System.out.println();
			if(trip.getRoute()==null && trip.getTrRoute()==null && !leglist.get(i).getMode().equals("transit_walk") && !leglist.get(i).getMode().equals("walk")) {
				
				logger.warn("No routes!!! Discarding trip. See if routes are generated properly.");
			}
			
			trip.setPersonId(PersonId);
			chain.add(trip);
		}

	}
	protected ArrayList<Trip> getTrips(){
		return new ArrayList<Trip>(this.chain);
	}
	
	//protected abstract PTleg getPtLeg(ArrayList <Leg> legs,ArrayList<Activity> activities);
	protected Trip createBlankTrip() {
		return new Trip();
	}
	protected abstract AnalyticalModelRoute createRoute(Route r);
	protected abstract AnalyticalModelTransitRoute getTransitRoute(ArrayList<Leg> ptlegList,
			ArrayList<Activity> ptactivityList,TransitSchedule ts,Scenario scenario);
	
}
