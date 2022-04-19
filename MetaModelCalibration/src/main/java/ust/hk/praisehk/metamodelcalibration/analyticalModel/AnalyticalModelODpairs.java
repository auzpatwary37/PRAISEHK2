package ust.hk.praisehk.metamodelcalibration.analyticalModel;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Lists;



public abstract class AnalyticalModelODpairs {
	
	private Config config=ConfigUtils.createConfig();
	private Scenario scenario;
	protected final Network network;
	private Population population;
	private Map<Id<AnalyticalModelODpair>,AnalyticalModelODpair> ODpairset=new HashMap<>();
	private Map<Id<AnalyticalModelODpair>,Double> ODdemand=new HashMap<>();
	//private Map<Id<AnalyticalModelODpair>,Double> ODdemandperhour=new HashMap<>();
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
	public AnalyticalModelODpairs(Network network, Population population,Map<String,Tuple<Double,Double>> timeBean,Scenario scenario){
		this.network=network;
		this.population=population;
		this.timeBean=timeBean;
		this.scenario=scenario;
	}
	@SuppressWarnings("unchecked")
	public void generateODpairset(Network odNetwork){
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
//			double pcu=1;
//			Vehicle v=this.scenario.getVehicles().getVehicles().get(Id.createVehicleId(trip.getPersonId().toString()));
//			if(v!=null) {
//				pcu=v.getType().getPcuEquivalents();
//			}
//			trip.setCarPCU(pcu);
			if(trip.getRoute()!=null ||trip.getTrRoute()!=null) {
				Id<AnalyticalModelODpair> ODId=trip.generateODpairId(odNetwork);
				if (ODpairset.containsKey(ODId)){
					ODpairset.get(ODId).addtrip(trip);
				}else{
					AnalyticalModelODpair odpair=this.getNewODPair(ODId, trip.getOriginNode(),trip.getDestinationNode(),network,this.timeBean);
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
		//this.population = null;
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
		this.ODdemand = new HashMap<>();
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
	

	public Map<Id<AnalyticalModelODpair>, AnalyticalModelODpair> getODpairset() {
		return ODpairset;
	}
	
	public void generateRouteandLinkIncidence(double routePercentage){
		this.ODpairset.keySet().parallelStream().forEach(odpairId->{
			ODpairset.get(odpairId).generateRoutes(routePercentage);
			ODpairset.get(odpairId).generateTRRoutes(routePercentage);
			ODpairset.get(odpairId).generateLinkIncidence();
			ODpairset.get(odpairId).generateTimeBasedTransitRoutes();
			ODpairset.get(odpairId).calcAutoRoutePathSize();
			ODpairset.get(odpairId).calcTransitRoutePathSize();
			//this.ODpairset.get(odpairId).generateDepartureTimeDistribution();
			
		});
	}
	
	public void generateRouteandLinkIncidence(double routePercentage,Map<String,Map<String,Double>>capacity,Map<String,Map<String,Double>>vehicleCount){
		for (Id<AnalyticalModelODpair> odpairId:ODpairset.keySet()){
			ODpairset.get(odpairId).generateRoutes(routePercentage);
			ODpairset.get(odpairId).generateTRRoutes(routePercentage);
			ODpairset.get(odpairId).generateLinkIncidence();
			ODpairset.get(odpairId).generateTimeBasedTransitRoutes(capacity,vehicleCount);
			ODpairset.get(odpairId).calcAutoRoutePathSize();
			ODpairset.get(odpairId).calcTransitRoutePathSize();
			//this.ODpairset.get(odpairId).generateDepartureTimeDistribution();
		}
	}
	
	
	public void resetDemand() {
		for(AnalyticalModelODpair odpair: this.ODpairset.values()) {
			odpair.resetDemand();
		}
		
	}
	protected abstract TripChain getNewTripChain(Plan plan);
	protected AnalyticalModelODpair getNewODPair(Id<AnalyticalModelODpair> odId,Node oNode,Node dNode, Network network,Map<String, Tuple<Double,Double>> timeBean2) {
		return new AnalyticalModelODpair(odId,oNode,dNode,network,timeBean2);
	}
	protected AnalyticalModelODpair getNewODPair(Id<AnalyticalModelODpair> odId, Node oNode,Node dNode, Network network,Map<String, Tuple<Double,Double>> timeBean2,String subPopName) {
		return new AnalyticalModelODpair(odId, oNode,dNode,network,timeBean2,subPopName);
	}
	public Map<String, Tuple<Double,Double>> getTimeBean() {
		return timeBean;
	}
	
	public abstract Map<Id<TransitLink>, TransitLink> getTransitLinks(String timeBeanId);


	public void generateODpairsetSubPop(Network odNetwork){
		if(odNetwork==null) {
			odNetwork=this.network;
		}
		ArrayList<Trip> trips=new ArrayList<>();
		
		/**
		 * Experimental Parallel
		 */
		Map<Id<AnalyticalModelODpair>,Set<AnalyticalModelODpair>> pureODMap=new HashMap<>();
		boolean multiThread=true;
		if(multiThread==true) {
			ArrayList<tripsCreatorFromPlan> threadrun=new ArrayList<>();
			List<List<Person>> personList=Lists.partition(new ArrayList<Person>(this.population.getPersons().values()), (int)(this.population.getPersons().values().size()/16));
			Thread[] threads=new Thread[personList.size()];
			for(int i=0;i<personList.size();i++) {
				threadrun.add(new tripsCreatorFromPlan(personList.get(i),this));
				//threadrun.get(i).setPersonsAttributes(this.population.getPersonAttributes());
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
			for (Person person:population.getPersons().values()){
				TripChain tripchain=this.getNewTripChain(person.getSelectedPlan());
				String s=PopulationUtils.getSubpopulation(person);
				for(Trip t:(ArrayList<Trip>)tripchain.getTrips()) {
					t.setSubPopulationName(s);
				}
				trips.addAll( tripchain.getTrips());
			}
		}
		System.out.println("Number of Trips = "+trips.size());
		double tripsWithoutRoute=0;
		for (Trip trip:trips){
			double pcu=1.0;
			Vehicle v=this.scenario.getVehicles().getVehicles().get(Id.createVehicleId(trip.getPersonId().toString()));
			
			if(v!=null) {
				pcu=v.getType().getPcuEquivalents();
			//	pcu=0.7;// Delete it for later sceanrios
				
				trip.setVehicleType(v.getType().getId());
			}
			trip.setCarPCU(pcu);
			if(trip.getRoute()!=null ||trip.getTrRoute()!=null) {
				Id<AnalyticalModelODpair> ODId=trip.generateODpairId(odNetwork);
				Id<AnalyticalModelODpair> pureODId=trip.generateODpairIdWithoutSubPop(odNetwork);
				if (ODpairset.containsKey(ODId)){
					ODpairset.get(ODId).addtrip(trip);
				}else{
					AnalyticalModelODpair odpair=this.getNewODPair(ODId, trip.getOriginNode(),trip.getDestinationNode(),this.network,this.timeBean,trip.getSubPopulationName());
					odpair.addtrip(trip);
					ODpairset.put(trip.generateODpairId(odNetwork), odpair);
					if(pureODMap.containsKey(pureODId)) {
						pureODMap.get(pureODId).add(odpair);
					}else {
						pureODMap.put(pureODId,new HashSet<>());
						pureODMap.get(pureODId).add(odpair);
					}
				}
				for(AnalyticalModelODpair od:pureODMap.get(pureODId)) {
					od.addRoute(trip);
				}
			}else {
				tripsWithoutRoute++;
			}
		}
		System.out.println("no of trips withoutRoutes = "+tripsWithoutRoute);
		//this.population = null;
	}
	/**
	 * Apply this just after creation of the od pair.
	 * Before route generation 
	 * DO NOT APPLY THIS AFTER ROUTE GENERATION
	 */
	public void generateOdSpecificRouteKeys() {
		for(AnalyticalModelODpair odpair:this.ODpairset.values()) {
			odpair.generateOdSpecificRouteKeys();
		}
	}
	
	

	/**
	 * This function will do path sharing between the same od pair sub population 
	 */
	public void sharePathbetweenSubPop() {
		Map<String,Set<AnalyticalModelRoute>>routes=new HashMap<>();
		Map<String,Set<AnalyticalModelTransitRoute>>trroutes=new HashMap<>();
		
		
		for(AnalyticalModelODpair od:this.ODpairset.values()) {
			String[] baseString = od.getODpairId().toString().split("_");
			String odid=baseString[0]+"_"+baseString[1];
			if(!routes.containsKey(odid)) {
				routes.put(odid, new HashSet<>());
				
			}
			
			if(!trroutes.containsKey(odid)) {
				trroutes.put(odid, new HashSet<>());
				
			}
			
			  
		}
	}
	
}



class tripsCreatorFromPlan implements Runnable {
	private List<Person> Persons;
	AnalyticalModelODpairs odPairs;
	//private ObjectAttributes personsAttributes=null;
	private ArrayList<Trip> trips=new ArrayList<>();
	public tripsCreatorFromPlan(List<Person> persons,AnalyticalModelODpairs odPairs) {
		this.Persons=persons;
		this.odPairs=odPairs;
	}
	
//	public ObjectAttributes getPersonsAttributes() {
//		return personsAttributes;
//	}

//	public void setPersonsAttributes(ObjectAttributes personsAttributes) {
//		this.personsAttributes = personsAttributes;
//	}
	
	@Override
	public void run() {
		if(PopulationUtils.getSubpopulation(this.Persons.get(0))==null) {
			for(Person p:this.Persons) {
				TripChain tripchain=this.odPairs.getNewTripChain(p.getSelectedPlan());
				trips.addAll( tripchain.getTrips());
			}
		}else {
			for(Person p:this.Persons) {
				TripChain tripchain=this.odPairs.getNewTripChain(p.getSelectedPlan());
				String s=PopulationUtils.getSubpopulation(p);
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
