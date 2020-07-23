package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.math3.random.AbstractRandomGenerator;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.VehicleType;
import com.google.inject.Inject;

import ust.hk.praisehk.metamodelcalibration.Utils.TruncatedNormal;

/**
 * This is a self sufficient implementation of OD pair class.
 * Basically this class is a container which holds information for each OD pair
 * @author h
 *
 */

public class AnalyticalModelODpair {
	
	
	private final Network network;
	private double agentCARCounter=0;
	private double agentTrCounter=0;
	private double averagePCU=1;
	private double ExpectedMaximumCarUtility;
	private double ExpectedMaximumTransitUtility;
	private List<Id<Person>> personIdList=new ArrayList<>();
	
	private final Node onode;
	private final Node dnode;
	private final Coord ocoord;
	private final Coord dcoord;
	private double expansionFactor;
	private Map<String,Double> demand=new HashMap<>();
	private Map <Id<VehicleType>,VehicleType> vt;
	private Coord[] c;
	private final Id<AnalyticalModelODpair> ODpairId;
	private LinkedHashMap<Id<AnalyticalModelRoute>, Integer> routeset=new LinkedHashMap<>();
	private Map<Id<AnalyticalModelRoute>,AnalyticalModelRoute> RoutesWithDescription=new HashMap<>();
	private ArrayList<AnalyticalModelRoute> finalRoutes;
	private Map<Id<Link>,ArrayList<AnalyticalModelRoute>> linkIncidence=null;
	private Map<Id<TransitLink>,ArrayList<AnalyticalModelTransitRoute>> trLinkIncidence=null;
	private Map<Id<Link>,ArrayList<AnalyticalModelTransitRoute>> trPhysiscalLinkIncidence=null;
	private double routePercentage=5.0;
	private double originParkingCharge=0;
	private double destinationParkingCharge=0;
	private ActivityFacility OriginFacility;
	private ActivityFacility DestinationFacility;
	private Map<Id<AnalyticalModelTransitRoute>,AnalyticalModelTransitRoute> Transitroutes=new HashMap<>();
	private Map<Id<AnalyticalModelTransitRoute>, Integer> transitRouteCounter=new HashMap<>();
	private ArrayList<AnalyticalModelTransitRoute> finalTrRoutes;
	private Map<String,HashMap<Id<AnalyticalModelRoute>,Double>> RouteUtility=new ConcurrentHashMap<>();
	private Map<String,HashMap<Id<AnalyticalModelRoute>,Double>> RouteFlow=new ConcurrentHashMap<>();
	private Map<String,HashMap<Id<AnalyticalModelTransitRoute>, Double>> TrRouteUtility=new ConcurrentHashMap<>();
	private Map<String,HashMap<Id<AnalyticalModelTransitRoute>, Double>> TrRouteFlow=new ConcurrentHashMap<>();
	private final Map<String, Tuple<Double,Double>>timeBean;
	private Map<String, ArrayList<AnalyticalModelTransitRoute>> timeBasedTransitRoutes=new HashMap<>();
	private String subPopulation;

	private Map<String,Map<Id<VehicleType>,Double>> vehicleSpecificDemand=new HashMap<>();
	
	private int minRoute=5;
	private Map<Id<AnalyticalModelRoute>,Double> autoPathSize;
	private Map<String,Map<Id<AnalyticalModelTransitRoute>,Double>>trPathSize;
	private Map<String,Double> median=new HashMap<>();
	private Map<String,List<Double>>startTimes=new HashMap<>();
	//private Map<String,Double>startTimeSum=new HashMap<>();
	
	public boolean shouldUseDistibutionInDepartureTime=true;
	private Map<String,TruncatedNormal> departureTimeDistributions=new HashMap<>();
	//TODO:Shift Node Based Coordinates to FacilityBased Coordinates
	
	/**
	 * This will give the modal split from MATSim
	 * @return
	 */
	public double getCarModalSplit() {
		return (double)this.agentCARCounter/(this.agentTrCounter+this.agentCARCounter);
	}
	public Map<String, Map<Id<VehicleType>, Double>> getVehicleSpecificDemand() {
		return vehicleSpecificDemand;
	}
	
	/**
	 * This will give the origin Activity Facility
	 * Will be used in future expansion.
	 * No Setters for the Activity Facilities as they will be taken in the constructor
	 * @return
	 */
	public ActivityFacility getOriginFacility() {
		OriginFacility=ScenarioUtils.createScenario(ConfigUtils.createConfig()).getActivityFacilities().getFactory().createActivityFacility(
				Id.create(this.onode.getId().toString(), ActivityFacility.class),this.ocoord);
		return OriginFacility;
	}


	/**
	 * This will give the Destination Activity Facility
	 * Will be used in future expansion.
	 * @return
	 */
	public ActivityFacility getDestinationFacility() {
		DestinationFacility=ScenarioUtils.createScenario(ConfigUtils.createConfig()).getActivityFacilities().getFactory().createActivityFacility(
				Id.create(this.dnode.getId().toString(), ActivityFacility.class),this.dcoord);
		return DestinationFacility;
	}
	

	public Map<String, HashMap<Id<AnalyticalModelRoute>, Double>> getRouteFlow() {
		return RouteFlow;
	}

	public Map<String, HashMap<Id<AnalyticalModelTransitRoute>, Double>> getTrRouteFlow() {
		return TrRouteFlow;
	}

	@Inject
	/**
	 * Constructor
	 * TODO: Shift towards Facility based constructor
	 * @param onode
	 * @param dnode
	 * @param network
	 */
	public AnalyticalModelODpair(Node onode,Node dnode, Network network,Map<String, Tuple<Double, Double>> timeBean2){
		this.network=network;
		this.ocoord=onode.getCoord();
		this.dcoord=dnode.getCoord();
		this.onode=onode;
		this.dnode=dnode;
		this.expansionFactor=1;
		for(String s:timeBean2.keySet()){
			this.demand.put(s,0.);
			this.vehicleSpecificDemand.put(s, new HashMap<>());
			}
		ODpairId=Id.create(onode.getId().toString()+"_"+dnode.getId().toString(), AnalyticalModelODpair.class);
		this.timeBean=timeBean2;
		for(String timeBeanId:this.timeBean.keySet()) {
			this.RouteUtility.put(timeBeanId, new HashMap<Id<AnalyticalModelRoute>, Double>());
			this.TrRouteUtility.put(timeBeanId, new HashMap<Id<AnalyticalModelTransitRoute>, Double>());
			
			this.RouteFlow.put(timeBeanId, new HashMap<Id<AnalyticalModelRoute>, Double>());
			this.TrRouteFlow.put(timeBeanId, new HashMap<Id<AnalyticalModelTransitRoute>, Double>());
			
			this.median.put(timeBeanId, 0.);
			this.startTimes.put(timeBeanId, new ArrayList<>());
		}
		
	}
	
	public AnalyticalModelODpair(Node onode,Node dnode, Network network,Map<String, Tuple<Double, Double>> timeBean2,String subPopulation){
		this.network=network;
		this.ocoord=onode.getCoord();
		this.dcoord=dnode.getCoord();
		this.onode=onode;
		this.dnode=dnode;
		this.expansionFactor=1;
		for(String s:timeBean2.keySet()){
			this.demand.put(s,0.);
			this.vehicleSpecificDemand.put(s, new HashMap<>());}
		ODpairId=Id.create(onode.getId().toString()+"_"+dnode.getId().toString()+"_"+subPopulation, AnalyticalModelODpair.class);
		this.timeBean=timeBean2;
		for(String timeBeanId:this.timeBean.keySet()) {
			this.RouteFlow.put(timeBeanId, new HashMap<Id<AnalyticalModelRoute>, Double>());
			this.TrRouteFlow.put(timeBeanId, new HashMap<Id<AnalyticalModelTransitRoute>, Double>());
			
			this.RouteUtility.put(timeBeanId, new HashMap<Id<AnalyticalModelRoute>, Double>());
			this.TrRouteUtility.put(timeBeanId, new HashMap<Id<AnalyticalModelTransitRoute>, Double>());
			
			this.median.put(timeBeanId, 0.);
			this.startTimes.put(timeBeanId, new ArrayList<>());
		
		}
		this.subPopulation=subPopulation;
		
		
	}
	
	public void updateAveragePCU(double pcu) {
		double totalPCU=this.averagePCU*(this.agentCARCounter-1)+pcu;
		this.averagePCU=totalPCU/this.agentCARCounter;
		
	}
	
	public String getSubPopulation() {
		return subPopulation;
	}

	/**
	 * Return specific timeBeanId OD demand
	 * @param timeBeanId
	 * @return
	 */
	public double getSpecificPeriodODDemand(String timeBeanId){
		
		return this.demand.get(timeBeanId);
	}
	
	/**
	 * Get Origin Node
	 * @return
	 */
	public Node getOriginNode() {
		return onode;
	}


	/**
	 * Gives destination Ndoe
	 * @return
	 */
	public Node getDestinationNode() {
		return dnode;
	}


	/**
	 * Give od pair id
	 * @return
	 */
	public Id<AnalyticalModelODpair> getODpairId() {
		return ODpairId;
	}

	

	public Map<String, TruncatedNormal> getDepartureTimeDistributions() {
		return departureTimeDistributions;
	}

	public Coord[] getODCoord(){
		c[0]=ocoord;
		c[1]=dcoord;
		return c;
	}
	public void resetDemand() {
		for(String i:this.demand.keySet()) {
			this.demand.put(i,0.);
		}
		this.agentCARCounter=0;
		this.agentTrCounter=0;
		this.ExpectedMaximumCarUtility=0;
		this.ExpectedMaximumTransitUtility=0;
		this.RouteUtility.clear();
		this.TrRouteUtility.clear();
	}
	
	public void generateDepartureTimeDistribution() {
		
		for(String timeId:this.timeBean.keySet()) {
			if(this.startTimes.get(timeId).size()>0) {
			double[] startTimes=new double[this.startTimes.get(timeId).size()];
			int i=0;
			double sum=0;
			for(Double d:this.startTimes.get(timeId)) {
				startTimes[i]=(double)d;
				sum+=d;
				i++;
			}
			double mean=sum/(i);
			double sd=new StandardDeviation().evaluate(startTimes);
			if(sd==0) {
				sd=1;
			}
			this.departureTimeDistributions.put(timeId, new TruncatedNormal(new JDKRandomGenerator(), 
					mean, sd, this.timeBean.get(timeId).getFirst(), this.timeBean.get(timeId).getSecond()));
		}
		}
	}

	/**
	 * 
	 * adding trips generated from a population file
	 * @param trip 
	 */

	public void addtrip(Trip trip){
		String timeId=null;
		Integer i=0;
		if(trip.getStartTime()>24*3600) {
			trip.setStartTime(trip.getStartTime()-24*3600);
		}
		for(String t:this.timeBean.keySet()) {
			if(trip.getStartTime()>=this.timeBean.get(t).getFirst() && trip.getStartTime()<this.timeBean.get(t).getSecond()) {
				timeId=t;
			}
			
		}
		
		
		
		if(trip.getRoute()!=null && timeId!=null){
			
			this.startTimes.get(timeId).add(trip.getStartTime());
//			Double oldVolume=this.startTimeSum.get(timeId);
//			if(oldVolume!=null) {
//				this.startTimeSum.put(timeId, oldVolume+trip.getStartTime());
//			}else {
//				this.startTimeSum.put(timeId, trip.getStartTime());
//			}
			//this.median.put(timeId, this.startTimeSum.get(timeId)/(this.demand.get(timeId)+1));
			
			//demand.put(timeId, demand.get(timeId)+trip.getCarPCU());//TODO: how to fix it??
			demand.put(timeId, demand.get(timeId)+1);//TODO: how to fix it??
			//Add the demand to the vehicleType demand 
			if(this.vehicleSpecificDemand.get(timeId).containsKey(trip.getVehicleType())) {
				this.vehicleSpecificDemand.get(timeId).put(trip.getVehicleType(),this.vehicleSpecificDemand.get(timeId).get(trip.getVehicleType())+1);
			}else {
				this.vehicleSpecificDemand.get(timeId).put(trip.getVehicleType(),1.);
			}
			
			this.agentCARCounter+=1;
			this.updateAveragePCU(trip.getCarPCU());
			
			
			
			if(!routeset.containsKey(trip.getRoute().getRouteId())){//A new route 
				routeset.put(trip.getRoute().getRouteId(),1);
				this.RoutesWithDescription.put(trip.getRoute().getRouteId(),trip.getRoute());
				//this.RoutesWithDescription.get(trip.getRoute().getRouteDescription()).addPerson(trip.getPersonId());
				//this.no_of_occurance.put(trip.getRouteId(), 1);

			}else{ //not a new route
				this.routeset.put(trip.getRoute().getRouteId(), routeset.get(trip.getRoute().getRouteId())+1);
				//this.RoutesWithDescription.get(trip.getRoute().getRouteDescription()).addPerson(trip.getPersonId());
			}
		}else if(trip.getTrRoute()!=null && timeId!=null) {
//			if(demand.get(timeId)==null) {
//				System.out.println();
//			}
			this.startTimes.get(timeId).add(trip.getStartTime());
			//demand.put(timeId, demand.get(timeId)+trip.getCarPCU());
			demand.put(timeId, demand.get(timeId)+1);
			this.agentTrCounter++;
			if(!this.Transitroutes.containsKey(trip.getTrRoute().getTrRouteId())) {
				this.Transitroutes.put(trip.getTrRoute().getTrRouteId(),trip.getTrRoute());
				this.transitRouteCounter.put(trip.getTrRoute().getTrRouteId(), 1);
			}else {
				this.transitRouteCounter.put(trip.getTrRoute().getTrRouteId(),this.transitRouteCounter.get(trip.getTrRoute().getTrRouteId())+ 1);
			}
		}else {
			this.personIdList.add(trip.getPersonId());
		}
		
		
	}
	
	
//	private void updateMedian(String timeId) {
//		Collections.sort(this.startTimes.get(timeId));
//		int size=this.startTimes.get(timeId).size();
//		if(size%2==0) {
//			this.median.put(timeId, 0.5*(this.startTimes.get(timeId).get(size/2)+this.startTimes.get(timeId).get(size/2+1)));
//		}else {
//			this.median.put(timeId, this.startTimes.get(timeId).get((size+1)/2));
//		}
//	}
	
	public void addRoute(Trip trip) {
		String timeId=null;
		Integer i=0;
		if(trip.getStartTime()>24*3600) {
			trip.setStartTime(trip.getStartTime()-24*3600);
		}
		for(String t:this.timeBean.keySet()) {
			if(trip.getStartTime()>=this.timeBean.get(t).getFirst() && trip.getStartTime()<this.timeBean.get(t).getSecond()) {
				timeId=t;
			}
			
		}
		if(trip.getRoute()!=null && timeId!=null){
			demand.put(timeId, demand.get(timeId)+1);//TODO: how to fix it??
			this.agentCARCounter+=1;
			this.updateAveragePCU(trip.getCarPCU());

			if(!routeset.containsKey(trip.getRoute().getRouteId())){//A new route 
				routeset.put(trip.getRoute().getRouteId(),1);
				this.RoutesWithDescription.put(trip.getRoute().getRouteId(),trip.getRoute());
				//this.RoutesWithDescription.get(trip.getRoute().getRouteDescription()).addPerson(trip.getPersonId());
				//this.no_of_occurance.put(trip.getRouteId(), 1);

			}else{ //not a new route
				this.routeset.put(trip.getRoute().getRouteId(), routeset.get(trip.getRoute().getRouteId())+1);
				//this.RoutesWithDescription.get(trip.getRoute().getRouteDescription()).addPerson(trip.getPersonId());
			}
		}else if(trip.getTrRoute()!=null && timeId!=null) {
			//			if(demand.get(timeId)==null) {
			//				System.out.println();
			//			}
			this.startTimes.get(timeId).add(trip.getStartTime());
			demand.put(timeId, demand.get(timeId)+trip.getCarPCU());
			this.agentTrCounter++;
			if(!this.Transitroutes.containsKey(trip.getTrRoute().getTrRouteId())) {
				this.Transitroutes.put(trip.getTrRoute().getTrRouteId(),trip.getTrRoute());
				this.transitRouteCounter.put(trip.getTrRoute().getTrRouteId(), 1);
			}else {
				this.transitRouteCounter.put(trip.getTrRoute().getTrRouteId(),this.transitRouteCounter.get(trip.getTrRoute().getTrRouteId())+ 1);
			}
		}else {
			this.personIdList.add(trip.getPersonId());
		}
	}
	
	public Double getMedian(String timeId) {
		return median.get(timeId);
	}

	/**
	 * This will return the full route Set
	 * @return
	 */
	public LinkedHashMap<Id<AnalyticalModelRoute>, Integer> getRouteset() {
		return routeset;
	}

	
	/**
	 * For future expansion 
	 * @return
	 */
	public double getExpansionFactor() {
		return expansionFactor;
	}
	
	/**
	 * For future expansion 
	 * @return
	 */
	public void setExpansionFactor(double expansionFactor) {
		this.expansionFactor = expansionFactor;
	}

	/**
	 * Gives the demand ArrayList<double demand> index are the hour
	 * @return
	 */
	public Map<String,Double> getDemand() {
		return demand;
	}
	
	
	public void generateRoutes(double routePercentage) {
		if(this.agentCARCounter!=0) {
			this.routePercentage=routePercentage;
			this.finalRoutes=new ArrayList<>();
			for(Entry<Id<AnalyticalModelRoute>, Integer> e:routeset.entrySet()) {
				if(((double)e.getValue()/(double)this.agentCARCounter*100)>routePercentage) {
					this.finalRoutes.add(this.RoutesWithDescription.get(e.getKey()));
					for(String timeBeanId:this.timeBean.keySet()) {
						this.RouteUtility.get(timeBeanId).put(e.getKey(), 0.0);
					}
				}
			}
			if(finalRoutes.size()<=this.minRoute && finalRoutes.size()<=this.routeset.size()) {
				ArrayList<Integer> tripCount=new ArrayList<Integer>(this.routeset.values());
				Collections.sort(tripCount);
				Collections.reverse(tripCount);
				if(routeset.size()<=this.minRoute) {
					for(AnalyticalModelRoute r:this.RoutesWithDescription.values()) {
						if(!finalRoutes.contains(r)) {
							this.finalRoutes.add(r);
						}
					}
				}else {
					for(java.util.Map.Entry<Id<AnalyticalModelRoute>, Integer> e:routeset.entrySet()) {
						if(e.getValue()>tripCount.get(this.minRoute-1)) {
							this.finalRoutes.add(this.RoutesWithDescription.get(e.getKey()));
							for(String timeBeanId:this.timeBean.keySet()) {
								this.RouteUtility.get(timeBeanId).put(e.getKey(), 0.0);
							}
						}
					}
					
				}
			}

		}
	}
	
	
	//TODO: giving more routes than possible
	public void generateTRRoutes(double routePercentage) {
		for(String timeBean:this.timeBean.keySet()) {
			this.TrRouteUtility.put(timeBean,new HashMap<Id<AnalyticalModelTransitRoute>,Double>());
		}
		if(this.agentTrCounter!=0) {
			finalTrRoutes=new ArrayList<>();
			for(Entry<Id<AnalyticalModelTransitRoute>, Integer> e:this.transitRouteCounter.entrySet()) {
				if(((double)e.getValue()/(double)this.agentTrCounter*100)>routePercentage||this.transitRouteCounter.size()<=this.minRoute) {//maybe this should be and instead of or??
					this.finalTrRoutes.add(this.Transitroutes.get(e.getKey()));
					for(String timeBeanId:this.timeBean.keySet()) {
						AnalyticalModelTransitRoute tr=this.Transitroutes.get(e.getKey());
						tr.calcCapacityHeadway(this.timeBean, timeBeanId);
//						if((double)tr.getRouteCapacity().get(timeBeanId)!=0) {
//							this.TrRouteUtility.get(timeBeanId).put(tr.getTrRouteId(), 0.0);
//						}
					}
				}
			}
//			if(finalTrRoutes.size()<=this.minRoute && finalTrRoutes.size()<=this.transitRouteCounter.size()) {
//				ArrayList<Integer> tripCount=new ArrayList<Integer>(this.transitRouteCounter.values());
//				Collections.sort(tripCount);
//				Collections.reverse(tripCount);
//				if(transitRouteCounter.size()<=this.minRoute) {
//					this.finalTrRoutes.addAll(this.Transitroutes.values());
//				}else {
//					for(Entry<Id<AnalyticalModelTransitRoute>, Integer> e:this.transitRouteCounter.entrySet()) {
//						if(e.getValue()>tripCount.get(this.minRoute-1)) {
//							this.finalTrRoutes.add(this.Transitroutes.get(e.getKey()));
//							for(String timeBeanId:this.timeBean.keySet()) {
//								AnalyticalModelTransitRoute tr=this.Transitroutes.get(e.getKey());
//								tr.calcCapacityHeadway(this.timeBean, timeBeanId);
//							}
//						}
//					}
//					
//				}
//			}
			
		}
		//System.out.println();
		
	}
	
	public void updateRouteUtility(Id<AnalyticalModelRoute> routeId, double utility,String timeBeanId) {
		this.RouteUtility.get(timeBeanId).put(routeId, utility);
	}
	public void updateTrRouteUtility(Id<AnalyticalModelTransitRoute> id, double utility,String timeBeanId) {
		this.TrRouteUtility.get(timeBeanId).put(id, utility);
	}
	
	
	
	public HashMap<Id<AnalyticalModelRoute>, Double> getRouteUtility(String timeBeanId) {
		return RouteUtility.get(timeBeanId);
	}

	public HashMap<Id<AnalyticalModelTransitRoute>, Double> getTrRouteUtility(String timeBeanId) {
		return TrRouteUtility.get(timeBeanId);
	}
	


	public double getExpectedMaximumCarUtility(LinkedHashMap<String,Double> params,LinkedHashMap<String,Double> anaParams,String timeBeanId) {
		if(this.RouteUtility.get(timeBeanId).size()==0) {
			return Double.NEGATIVE_INFINITY;
		}
		double logsum=0;
		for(double utility:this.RouteUtility.get(timeBeanId).values()) {
			logsum+=Math.exp(utility);
		}
		
		this.ExpectedMaximumCarUtility=1*Math.log(logsum);
		return ExpectedMaximumCarUtility/anaParams.get("LinkMiu");
	}

	public double getExpectedMaximumTransitUtility(LinkedHashMap<String,Double> params,LinkedHashMap<String,Double> anaParams,String timeBeanId) {
		if(this.TrRouteUtility.get(timeBeanId).size()==0) {
			return Double.NEGATIVE_INFINITY;
		}
		double logsum=0;
		for(double utility:this.TrRouteUtility.get(timeBeanId).values()) {
			logsum+=Math.exp(utility);
			
		}
		this.ExpectedMaximumTransitUtility=1*Math.log(logsum);
		return ExpectedMaximumTransitUtility/anaParams.get("LinkMiu");
	}

	/**
	 * This is another very important function. equivalent of  Delta matrix production
	 */
	public void generateLinkIncidence(){
		if (finalRoutes==null){
			this.generateRoutes(this.routePercentage);
			
		}

		this.linkIncidence=new HashMap<>();
		this.trLinkIncidence=new HashMap<>();
		this.trPhysiscalLinkIncidence=new HashMap<>();
		if(this.finalRoutes!=null) {
			for(AnalyticalModelRoute route:finalRoutes){
				ArrayList<Id<Link>>linkIds=route.getLinkIds();
				for(Id<Link> linkId: linkIds){
					if(this.linkIncidence.containsKey(linkId)){
						this.linkIncidence.get(linkId).add(route);
					}else{
						ArrayList<AnalyticalModelRoute> routeList=new ArrayList<> ();
						routeList.add(route);
						this.linkIncidence.put(linkId, routeList);
					}
				}
			}
		}
		
		if(finalTrRoutes==null) {
			this.generateTRRoutes(routePercentage);
		}
		if(this.finalTrRoutes!=null) {
			for(AnalyticalModelTransitRoute route:finalTrRoutes){
				ArrayList<Id<TransitLink>>linkIds=route.getTrLinkIds();
				for(Id<TransitLink> trlinkId: linkIds){
					if(this.trLinkIncidence.containsKey(trlinkId)){
						this.trLinkIncidence.get(trlinkId).add(route);
					}else{
						ArrayList<AnalyticalModelTransitRoute> routeList=new ArrayList<> ();
						routeList.add(route);
						this.trLinkIncidence.put(trlinkId, routeList);
					}
				}
				List<Id<Link>>linkIDs=route.getPhysicalLinks();
				
				for(Id<Link> linkId: linkIDs){
					if(this.trPhysiscalLinkIncidence.containsKey(linkId)){
						this.trPhysiscalLinkIncidence.get(linkId).add(route);
					}else{
						ArrayList<AnalyticalModelTransitRoute> routeList=new ArrayList<> ();
						routeList.add(route);
						this.trPhysiscalLinkIncidence.put(linkId, routeList);
					}
				}
				
			}	
		}
		//System.out.println();
		
		if((this.finalRoutes!=null ||this.finalTrRoutes!=null)&&(this.routeset.size()!=0 && this.finalRoutes.size()==0)||(this.transitRouteCounter.size()!=0 && this.finalTrRoutes.size()==0)) {
			throw new IllegalArgumentException("Stop!!! No Routes Were Created!!!");
		}
	}
	
	
//CAUTION: This method can be used only after using the method getRoutes(int no_of_Routes)
	//as the variable finalRoutes is generated in that method which is the basic output of this method.

	public double getRoutePercentage() {
		return routePercentage;
	}

	public void setRoutePercentage(double routePercentage) {
		this.routePercentage = routePercentage;
	}

	
	/**
	 * This returns extracted most used car routes
	 * @return
	 */

	public ArrayList<AnalyticalModelRoute> getRoutes() {
		return finalRoutes;
	}
	
	/**
	 * This method returns the most used transit routes
	 * clones the routes
	 * @return
	 */
	public ArrayList<AnalyticalModelTransitRoute> getTrRoutes(String timeBeanId) {
		if(this.timeBasedTransitRoutes.size()==0) {
			this.generateTimeBasedTransitRoutes();
		}
		return this.timeBasedTransitRoutes.get(timeBeanId);
	}
	public ArrayList<AnalyticalModelTransitRoute> getTrRoutes(){
		return this.finalTrRoutes;
	}
	

	public Map<Id<Link>,ArrayList<AnalyticalModelRoute>> getLinkIncidence(){
		if(this.linkIncidence==null) {
			this.generateLinkIncidence();
		}
		return linkIncidence;
		
	}
	
	public Map<Id<TransitLink>,ArrayList<AnalyticalModelTransitRoute>> getTrLinkIncidence(){
		if(this.trLinkIncidence==null) {
			this.generateLinkIncidence();
		}
		
		return trLinkIncidence;
		
	}
	
	public double getAveragePCU() {
		return averagePCU;
	}
	
	public double getOriginParkingCharge() {
		return originParkingCharge;
	}

	public void setOriginParkingCharge(double originParkingCharge) {
		this.originParkingCharge = originParkingCharge;
	}

	public double getDestinationParkingCharge() {
		return destinationParkingCharge;
	}

	public void setDestinationParkingCharge(double destinationParkingCharge) {
		this.destinationParkingCharge = destinationParkingCharge;
	}

	public double getParkingCharge(){
		return (this.originParkingCharge+this.destinationParkingCharge)/2;
	}

	public Map<String, Tuple<Double,Double>> getTimeBean() {
		return timeBean;
	}

	public void generateTimeBasedTransitRoutes() {
		if(this.finalTrRoutes!=null) {
			for(String timeBeanId:timeBean.keySet()) {
			ArrayList<AnalyticalModelTransitRoute> timeBasedTrRoutes=new ArrayList<>();
			for(AnalyticalModelTransitRoute tr:this.finalTrRoutes) {
				AnalyticalModelTransitRoute trnew=tr.cloneRoute();
				trnew.calcCapacityHeadway(timeBean, timeBeanId);
				if((Double)tr.getRouteCapacity().get(timeBeanId)!=0) {
					timeBasedTrRoutes.add(trnew);
				}
				
			}
			this.timeBasedTransitRoutes.put(timeBeanId,timeBasedTrRoutes);
		}
	}
	}
	
	public void generateTimeBasedTransitRoutes(Map<String,Map<String,Double>>capacity,Map<String,Map<String,Double>>vehicleCount) {
		if(this.finalTrRoutes!=null) {
			for(String timeBeanId:timeBean.keySet()) {
			ArrayList<AnalyticalModelTransitRoute> timeBasedTrRoutes=new ArrayList<>();
			for(AnalyticalModelTransitRoute tr:this.finalTrRoutes) {
				AnalyticalModelTransitRoute trnew=tr.cloneRoute();
				trnew.calcCapacityHeadway(timeBean, timeBeanId);
				if((Double)tr.getRouteCapacity().get(timeBeanId)!=0) {
					timeBasedTrRoutes.add(trnew);
				}
				
			}
			this.timeBasedTransitRoutes.put(timeBeanId,timeBasedTrRoutes);
		}
	}
	}

	public double getAgentCounter() {
		return agentCARCounter+this.agentTrCounter;
	}
	
	public void calcAutoRoutePathSize() {
		Map<Id<AnalyticalModelRoute>,Double> autoPathSize=new HashMap<>();
		if(this.finalRoutes!=null) {
		for(AnalyticalModelRoute r:this.finalRoutes) {
			double ps=0;
			for(Id<Link> lId:r.getLinkIds()) {
				double length=this.network.getLinks().get(lId).getLength();
				ps+=length/(r.getRouteDistance()*this.linkIncidence.get(lId).size());
			}
			if(Double.isInfinite(ps)) {
				System.out.println("PathSize infinie");
			}
				
			autoPathSize.put(r.getRouteId(),ps);
		}
		}
		this.autoPathSize=autoPathSize;
	}
	
	public void calcTransitRoutePathSize() {
		Map<String,Map<Id<AnalyticalModelTransitRoute>,Double>>trPathSize=new HashMap<>();
		for(String timeBeanId:this.timeBean.keySet()) {
			trPathSize.put(timeBeanId, new HashMap<>());
			if(this.getTrRoutes(timeBeanId)!=null) {
			for(AnalyticalModelTransitRoute anaTr:this.getTrRoutes(timeBeanId)) {
				double ps=0;
				double routeDistance=anaTr.getRouteDistance(network);
				for(Id<Link> linkId:anaTr.getPhysicalLinks()) {
					Link link=this.network.getLinks().get(linkId);
					ps+=link.getLength()/(routeDistance*this.trPhysiscalLinkIncidence.get(linkId).size());
				}
				if(Double.isInfinite(ps)) {
					System.out.println("PathSize infinite");
				}
				trPathSize.get(timeBeanId).put(anaTr.getTrRouteId(),ps);
			}
			}
		}
		this.trPathSize=trPathSize;
	}

	public Map<Id<AnalyticalModelRoute>, Double> getAutoPathSize() {
		return autoPathSize;
	}

	public Map<String, Map<Id<AnalyticalModelTransitRoute>, Double>> getTrPathSize() {
		return trPathSize;
	}
	// apply this just after creation of the od pair. DO NOT APPLY THIS AFTER ROUTE GENERATION 
	public void generateOdSpecificRouteKeys() {
		Map<Id<AnalyticalModelRoute>,AnalyticalModelRoute> newRoutesWithDescription = new HashMap<>();
		int routeNo = 0;
		for(Entry<Id<AnalyticalModelRoute>, AnalyticalModelRoute> r:this.RoutesWithDescription.entrySet()) {
			r.getValue().updateToOdBasedId(this.ODpairId, routeNo);
			newRoutesWithDescription.put(r.getValue().getRouteId(), r.getValue());
			this.routeset.put(r.getValue().getRouteId(), this.routeset.get(r.getKey()));
			this.routeset.remove(r.getKey());
			routeNo++;
		}
		this.RoutesWithDescription = newRoutesWithDescription;
		Map<Id<AnalyticalModelTransitRoute>,AnalyticalModelTransitRoute> newTrRoutesWithDescription = new HashMap<>();
		routeNo = 0;
		for(Entry<Id<AnalyticalModelTransitRoute>, AnalyticalModelTransitRoute> tr:this.Transitroutes.entrySet()) {
			tr.getValue().updateToOdBasedId(this.ODpairId, routeNo);
			newTrRoutesWithDescription.put(tr.getValue().getTrRouteId(),tr.getValue());
			this.transitRouteCounter.put(tr.getValue().getTrRouteId(), this.transitRouteCounter.get(tr.getKey()));
			this.transitRouteCounter.remove(tr.getKey());
			routeNo++;
		}
		this.Transitroutes = newTrRoutesWithDescription;
	}
	
	
}
