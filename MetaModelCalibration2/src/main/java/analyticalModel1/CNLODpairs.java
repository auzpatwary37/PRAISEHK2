package analyticalModel1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import analyticalModel.AnalyticalModelODpair;
import analyticalModel.AnalyticalModelODpairs;
import analyticalModel.AnalyticalModelTransitRoute;
import analyticalModel.TransitLink;
import analyticalModel.TripChain;



public class CNLODpairs extends AnalyticalModelODpairs{

	private final Scenario scenario;
	private final TransitSchedule ts;
	
	public CNLODpairs(Network network, Population population,TransitSchedule ts, Scenario scenario,Map<String, Tuple<Double, Double>> timeBeans) {
		super(network, population,timeBeans);
		this.scenario=scenario;
		this.ts=ts;
	}
	
	public CNLODpairs(String networkFileLoc,String populationFileLoc,TransitSchedule ts, Scenario scenario,HashMap<String,Tuple<Double,Double>> timeBean) {
		super(populationFileLoc,networkFileLoc,timeBean);
		this.scenario=scenario;
		this.ts=ts;
	
	}

	@Override
	protected TripChain getNewTripChain(Plan plan) {
		return new CNLTripChain(plan,this.ts,this.scenario);
		
	}

	
	@Override
	public Map<Id<TransitLink>, TransitLink> getTransitLinks(Map<String,Tuple<Double,Double>> timeBean,String timeBeanId){
		Map<Id<TransitLink>,TransitLink> transitLinks=new HashMap<>();
		for(AnalyticalModelODpair odPair:this.getODpairset().values()) {
			if(odPair.getTrRoutes(timeBean,timeBeanId)!=null && odPair.getTrRoutes(timeBean,timeBeanId).size()!=0) {
				for(AnalyticalModelTransitRoute tr:odPair.getTrRoutes(timeBean,timeBeanId)) {
					transitLinks.putAll(((CNLTransitRoute)tr).getTransitLinks(timeBean,timeBeanId));
				}
			}
		}
		return transitLinks;
	}

	
	
}
