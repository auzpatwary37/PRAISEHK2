package analyticalModelImpl;

import java.util.ArrayList;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Route;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import analyticalModel.AnalyticalModelRoute;
import analyticalModel.AnalyticalModelTransitRoute;
import analyticalModel.Trip;
import analyticalModel.TripChain;


/**
 * 
 * @author Ashraf
 *
 */

public class CNLTripChain extends TripChain{

	public CNLTripChain(Plan plan, TransitSchedule ts,Scenario scenario) {
		super(plan, ts,scenario);
		
	}

	@Override
	protected AnalyticalModelRoute createRoute(Route r) {
		
		return new CNLRoute(r);
	}

	@Override
	protected AnalyticalModelTransitRoute getTransitRoute(ArrayList<Leg> ptlegList, ArrayList<Activity> ptactivityList, TransitSchedule ts,Scenario scenario) {
		return new CNLTransitRoute(ptlegList,ptactivityList,ts,scenario);
	}


	
}
