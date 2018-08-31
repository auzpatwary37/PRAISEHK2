package analyticalModel;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.ObjectAttributes;

import com.google.common.collect.Lists;



public abstract class AnalyticalModelODpairs {
	
	private Config config=ConfigUtils.createConfig();
	private Scenario scenario;
	protected final Network network;
	private Population population;
	private Map<Id<AnalyticalModelODpair>,AnalyticalModelODpair> ODpairset=new HashMap<>();
	private Map<Id<AnalyticalModelODpair>,Double> ODdemand=new HashMap<>();
	private Map<Id<AnalyticalModelODpair>,Double> ODdemandperhour=new HashMap<>();
	private final Map<String,Tuple<Double,Double>> timeBean;
	
	
	public AnalyticalModelODpairs(String populationFileLocation, String networkFileLocation,HashMap<String,Tuple<Double,Double>> timeBean){
		
		config.network().setInputFile(networkFileLocation);
		config.plans().setInputFile(populationFileLocation);
		scenario=ScenarioUtils.loadScenario(config);
		network=scenario.getNetwork();
		population=scenario.getPopulation();
		this.timeBean=timeBean;
	}
	//Constructor to create from network and population file
	public AnalyticalModelODpairs(Network network, Population population,Map<String,Tuple<Double,Double>> timeBean){
		this.network=network;
		this.population=population;
		this.timeBean=timeBean;
	}
	@SuppressWarnings("unchecked")
	public void generateODpairset(){
		ArrayList<Trip> trips=new ArrayList<>();
		
		/**
		 * Experimental Parallel
		 */
		boolean multiThread=true;
		
		if(multiThread==true) {
			ArrayList<tripsCreatorFromPlan> threadrun=new ArrayList<>();
			List<List<Person>> personList=Lists.partition(new ArrayList<Person>(this.population.getPersons().values()), (int)(this.population.getPersons().values().size()/16));
			Thread[] threads=new Thread[personList.size()];
			for(int i=0;i<personList.size();i++) {
				threadrun.add(new tripsCreatorFromPlan(personList.get(i),this));
				threads[i]=new Thread(threadrun.get(i));
			}
			for(int i=0;i<personList.size();i++) {
				threads[i].start();
			}

			for(int i=0;i<personList.size();i++) {
				try {
					threads[i].join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			for(tripsCreatorFromPlan t:threadrun) {
				trips.addAll((ArrayList<Trip>)t.getTrips());
			}
		}else {
			for (Id<Person> personId:population.getPersons().keySet()){
				TripChain tripchain=this.getNewTripChain(population.getPersons().get(personId).getSelectedPlan());
				trips.addAll( tripchain.getTrips());
			}
		}
		double tripsWithoutRoute=0;
		for (Trip trip:trips){
			if(trip.getRoute()!=null ||trip.getTrRoute()!=null) {
				Id<AnalyticalModelODpair> ODId=trip.generateODpairId(network);
				if (ODpairset.containsKey(ODId)){
					ODpairset.get(ODId).addtrip(trip);
				}else{
					AnalyticalModelODpair odpair=this.getNewODPair(trip.getOriginNode(),trip.getDestinationNode(),network,this.timeBean);
					odpair.addtrip(trip);
					ODpairset.put(trip.generateODpairId(network), odpair);
				}
			}else {
				if(!trip.getMode().equals("transit_walk")) {
					//throw new IllegalArgumentException("WAit");
				}
				tripsWithoutRoute++;
			}
		}
		System.out.println("no of trips withoutRoutes = "+tripsWithoutRoute);
	}

	public HashMap <Id<AnalyticalModelODpair>,ActivityFacility> getOriginActivityFacilitie(){
		HashMap<Id<AnalyticalModelODpair>,ActivityFacility> Ofacilities=new HashMap<>();
		for (Id<AnalyticalModelODpair> odpairId:this.ODpairset.keySet()) {
			Ofacilities.put(odpairId, this.ODpairset.get(odpairId).getOriginFacility());
		}
		
		return Ofacilities;
	}
	public HashMap<Id<AnalyticalModelODpair>,ActivityFacility> getDestinationFacilitie(){
		HashMap<Id<AnalyticalModelODpair>,ActivityFacility> Dfacilities=new HashMap<>();
		for (Id<AnalyticalModelODpair> odpairId:this.ODpairset.keySet()) {
			Dfacilities.put(odpairId, this.ODpairset.get(odpairId).getDestinationFacility());
		}
		
		return Dfacilities;
	}
	public Map<Id<AnalyticalModelODpair>,Double> getdemand(String timeBeanId){
		for(Id<AnalyticalModelODpair> ODpairId:ODpairset.keySet()){
			this.ODdemand.put(ODpairId, ODpairset.get(ODpairId).getSpecificPeriodODDemand(timeBeanId));
		}
		return ODdemand;
	}


	public Network getNetwork() {
		return network;
	}

	public Population getPopulation() {
		return population;
	}
	
	public Map<Id<Link>,ArrayList<AnalyticalModelRoute>> getLinkIncidence(String ODpairId){
		this.ODpairset.get(ODpairId).generateLinkIncidence();
		return this.ODpairset.get(ODpairId).getLinkIncidence();
	}
	public Map<Id<AnalyticalModelODpair>, AnalyticalModelODpair> getODpairset() {
		return ODpairset;
	}
	
	public void generateRouteandLinkIncidence(double routePercentage){
		for (Id<AnalyticalModelODpair> odpairId:ODpairset.keySet()){
			ODpairset.get(odpairId).generateRoutes(routePercentage);
			ODpairset.get(odpairId).generateTRRoutes(routePercentage);
			ODpairset.get(odpairId).generateLinkIncidence();
		}
	}
	
	
	public void resetDemand() {
		for(AnalyticalModelODpair odpair: this.ODpairset.values()) {
			odpair.resetDemand();
		}
		
	}
	protected abstract TripChain getNewTripChain(Plan plan);
	protected AnalyticalModelODpair getNewODPair(Node oNode,Node dNode, Network network,Map<String, Tuple<Double,Double>> timeBean2) {
		return new AnalyticalModelODpair(oNode,dNode,network,timeBean2);
	}
	protected AnalyticalModelODpair getNewODPair(Node oNode,Node dNode, Network network,Map<String, Tuple<Double,Double>> timeBean2,String subPopName) {
		return new AnalyticalModelODpair(oNode,dNode,network,timeBean2,subPopName);
	}
	public Map<String, Tuple<Double,Double>> getTimeBean() {
		return timeBean;
	}
	
	public abstract Map<Id<TransitLink>, TransitLink> getTransitLinks(Map<String,Tuple<Double,Double>> timeBean,String timeBeanId);


	public void generateODpairsetSubPop(Network odNetwork){
		if(odNetwork==null) {
			odNetwork=this.network;
		}
		ArrayList<Trip> trips=new ArrayList<>();
		
		/**
		 * Experimental Parallel
		 */
		boolean multiThread=true;
		if(multiThread==true) {
			ArrayList<tripsCreatorFromPlan> threadrun=new ArrayList<>();
			List<List<Person>> personList=Lists.partition(new ArrayList<Person>(this.population.getPersons().values()), (int)(this.population.getPersons().values().size()/16));
			Thread[] threads=new Thread[personList.size()];
			for(int i=0;i<personList.size();i++) {
				threadrun.add(new tripsCreatorFromPlan(personList.get(i),this));
				threadrun.get(i).setPersonsAttributes(this.population.getPersonAttributes());
				threads[i]=new Thread(threadrun.get(i));
			}
			for(int i=0;i<personList.size();i++) {
				threads[i].start();
			}

			for(int i=0;i<personList.size();i++) {
				try {
					threads[i].join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			for(tripsCreatorFromPlan t:threadrun) {
				trips.addAll((ArrayList<Trip>)t.getTrips());
			}
		}else {
			for (Id<Person> personId:population.getPersons().keySet()){
				TripChain tripchain=this.getNewTripChain(population.getPersons().get(personId).getSelectedPlan());
				String s=(String) this.population.getPersonAttributes().getAttribute(personId.toString(), "SUBPOP_ATTRIB_NAME");
				for(Trip t:(ArrayList<Trip>)tripchain.getTrips()) {
					t.setSubPopulationName(s);
				}
				trips.addAll( tripchain.getTrips());
			}
		}
		double tripsWithoutRoute=0;
		for (Trip trip:trips){
			if(trip.getRoute()!=null ||trip.getTrRoute()!=null) {
				Id<AnalyticalModelODpair> ODId=trip.generateODpairId(odNetwork);
				if (ODpairset.containsKey(ODId)){
					ODpairset.get(ODId).addtrip(trip);
				}else{
					AnalyticalModelODpair odpair=this.getNewODPair(trip.getOriginNode(),trip.getDestinationNode(),odNetwork,this.timeBean,trip.getSubPopulationName());
					odpair.addtrip(trip);
					ODpairset.put(trip.generateODpairId(odNetwork), odpair);
				}
			}else {
				tripsWithoutRoute++;
			}
		}
		System.out.println("no of trips withoutRoutes = "+tripsWithoutRoute);
	}
	
}



class tripsCreatorFromPlan implements Runnable {
	private List<Person> Persons;
	AnalyticalModelODpairs odPairs;
	private ObjectAttributes personsAttributes=null;
	private ArrayList<Trip> trips=new ArrayList<>();
	public tripsCreatorFromPlan(List<Person> persons,AnalyticalModelODpairs odPairs) {
		this.Persons=persons;
		this.odPairs=odPairs;
	}
	
	public ObjectAttributes getPersonsAttributes() {
		return personsAttributes;
	}

	public void setPersonsAttributes(ObjectAttributes personsAttributes) {
		this.personsAttributes = personsAttributes;
	}
	
	@Override
	public void run() {
		if(personsAttributes==null) {
			for(Person p:this.Persons) {
				TripChain tripchain=this.odPairs.getNewTripChain(p.getSelectedPlan());
				trips.addAll( tripchain.getTrips());
			}
		}else {
			for(Person p:this.Persons) {
				TripChain tripchain=this.odPairs.getNewTripChain(p.getSelectedPlan());
				String s=(String) this.personsAttributes.getAttribute(p.getId().toString(), "SUBPOP_ATTRIB_NAME");
				for(Trip t:(ArrayList<Trip>)tripchain.getTrips()) {
					t.setSubPopulationName(s);
				}
				trips.addAll( tripchain.getTrips());
			}
			
		}
	}
	
	public ArrayList<Trip> getTrips(){
		return this.trips;
	}
	
	
}
