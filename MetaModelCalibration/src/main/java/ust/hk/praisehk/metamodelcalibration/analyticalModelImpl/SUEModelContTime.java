package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.Vehicles;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class SUEModelContTime implements AnalyticalModel{
	/**
	 * This is a simple and upgraded version of the SUE. 
	 * With better Modularity.
	 * 
	 * 
	 * The model is very simplified. 
	 * As link performance still BPR will be used. 
	 * 
	 * TODO:Fixing the alpha and beta will require special thinking.
	 * One meta-model calibration style can be used to fix 
	 * 
	 */
		private Measurements measurementsToUpdate=null;
		private final Logger logger=Logger.getLogger(CNLSUEModel.class);
		private String fileLoc="traget/";
		public String getFileLoc() {
			return fileLoc;
		}

		public void setFileLoc(String fileLoc) {
			this.fileLoc = fileLoc;
		}

		private Map<String,Double> consecutiveSUEErrorIncrease=new ConcurrentHashMap<>();
		private LinkedHashMap<String,Double> AnalyticalModelInternalParams=new LinkedHashMap<>();
		private LinkedHashMap<String,Double> Params=new LinkedHashMap<>();
		private LinkedHashMap<String,Tuple<Double,Double>> AnalyticalModelParamsLimit=new LinkedHashMap<>();
		
		
		private double alphaMSA=1.9;//parameter for decreasing MSA step size
		private double gammaMSA=.1;//parameter for decreasing MSA step size
		
		//other Parameters for the Calibration Process
		private double tollerance=1;
		private double tolleranceLink=1;
		//user input
	
		private Map<String, Tuple<Double,Double>> timeBeans;
		
		//MATSim Input
		private Map<String, AnalyticalModelNetwork> networks=new ConcurrentHashMap<>();
		private TransitSchedule ts;
		private Scenario scenario;
		private Population population;
		protected Map<String,FareCalculator> fareCalculator=new HashMap<>();
		
		//Used Containers
		private Map<String,ArrayList<Double>> beta=new ConcurrentHashMap<>(); //This is related to weighted MSA of the SUE
		private Map<String,ArrayList<Double>> error=new ConcurrentHashMap<>();
		private Map<String,ArrayList<Double>> error1=new ConcurrentHashMap<>();//This is related to weighted MSA of the SUE
		
		//TimebeanId vs demands map
		private Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> Demand=new ConcurrentHashMap<>();//Holds ODpair based demand
		private Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> carDemand=new ConcurrentHashMap<>(); 
		private CNLODpairs odPairs;
		private Map<String,Map<Id<TransitLink>,TransitLink>> transitLinks=new ConcurrentHashMap<>();
			
		private Population lastPopulation;
	
		//This are needed for output generation 
		
		protected Map<String,Map<Id<Link>,Double>> outputLinkTT=new ConcurrentHashMap<>();
		protected Map<String,Map<Id<TransitLink>,Double>> outputTrLinkTT=new ConcurrentHashMap<>();
		private Map<String,Map<Id<Link>,Double>> totalPtCapacityOnLink=new HashMap<>();
		protected Map<String,Map<String,Double>>MTRCount=new ConcurrentHashMap<>();
		//All the parameters name
		//They are kept public to make it easily accessible as they are final they can not be modified
		
		
		
		public SUEModelContTime(Map<String, Tuple<Double, Double>> timeBean) {
			this.timeBeans=timeBean;
			this.defaultParameterInitiation(null);
			for(String timeBeanId:this.timeBeans.keySet()) {
				this.Demand.put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
				this.carDemand.put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
				this.transitLinks.put(timeBeanId, new HashMap<Id<TransitLink>, TransitLink>());
				this.beta.put(timeBeanId, new ArrayList<Double>());
				this.error.put(timeBeanId, new ArrayList<Double>());
				this.error1.put(timeBeanId, new ArrayList<Double>());
				
				//For result recording
				outputLinkTT.put(timeBeanId, new HashMap<>());
				outputTrLinkTT.put(timeBeanId, new HashMap<>());
				this.totalPtCapacityOnLink.put(timeBeanId, new HashMap<>());
				this.MTRCount.put(timeBeanId, new ConcurrentHashMap<>());
			}
			logger.info("Analytical model created successfully.");
			
		}
		/**
		 * This method loads default values to all the parameters 
		 * Including the internal parameters
		 */
		private void defaultParameterInitiation(Config config){
			//Loads the Internal default parameters 
			
			this.AnalyticalModelInternalParams.put(CNLSUEModel.LinkMiuName, 0.008);
			this.AnalyticalModelInternalParams.put(CNLSUEModel.ModeMiuName, 0.01);
			this.AnalyticalModelInternalParams.put(CNLSUEModel.BPRalphaName, 0.15);
			this.AnalyticalModelInternalParams.put(CNLSUEModel.BPRbetaName, 4.);
			this.AnalyticalModelInternalParams.put(CNLSUEModel.TransferalphaName, 0.5);
			this.AnalyticalModelInternalParams.put(CNLSUEModel.TransferbetaName, 1.);
			this.loadAnalyticalModelInternalPamamsLimit();
			
			//Loads the External default Parameters
			if(config==null) {
				config=ConfigUtils.createConfig();
			}
			

			this.Params.put(CNLSUEModel.MarginalUtilityofTravelCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfTraveling());
			this.Params.put(CNLSUEModel.MarginalUtilityofDistanceCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfDistance());
			this.Params.put(CNLSUEModel.MarginalUtilityofMoneyName,config.planCalcScore().getMarginalUtilityOfMoney());
			this.Params.put(CNLSUEModel.DistanceBasedMoneyCostCarName,config.planCalcScore().getOrCreateModeParams("car").getMonetaryDistanceRate());
			this.Params.put(CNLSUEModel.MarginalUtilityofTravelptName, config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfTraveling());
			this.Params.put(CNLSUEModel.MarginalUtilityOfDistancePtName, config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfDistance());
			this.Params.put(CNLSUEModel.MarginalUtilityofWaitingName,config.planCalcScore().getMarginalUtlOfWaitingPt_utils_hr());
			this.Params.put(CNLSUEModel.UtilityOfLineSwitchName,config.planCalcScore().getUtilityOfLineSwitch());
			this.Params.put(CNLSUEModel.MarginalUtilityOfWalkingName, config.planCalcScore().getOrCreateModeParams("walk").getMarginalUtilityOfTraveling());
			this.Params.put(CNLSUEModel.DistanceBasedMoneyCostWalkName, config.planCalcScore().getOrCreateModeParams("walk").getMonetaryDistanceRate());
			this.Params.put(CNLSUEModel.ModeConstantPtname,config.planCalcScore().getOrCreateModeParams("pt").getConstant());
			this.Params.put(CNLSUEModel.ModeConstantCarName,config.planCalcScore().getOrCreateModeParams("car").getConstant());
			this.Params.put(CNLSUEModel.MarginalUtilityofPerformName, config.planCalcScore().getPerforming_utils_hr());
			this.Params.put(CNLSUEModel.CapacityMultiplierName, 1.0);
		}
		
		public void setDefaultParameters(LinkedHashMap<String,Double> params) {
			for(String s:params.keySet()) {
				this.Params.put(s, params.get(s));
			}
		}
		
		protected void loadAnalyticalModelInternalPamamsLimit() {
			this.AnalyticalModelParamsLimit.put(CNLSUEModel.LinkMiuName, new Tuple<Double,Double>(0.0075,0.25));
			this.AnalyticalModelParamsLimit.put(CNLSUEModel.ModeMiuName, new Tuple<Double,Double>(0.01,0.5));
			this.AnalyticalModelParamsLimit.put(CNLSUEModel.BPRalphaName, new Tuple<Double,Double>(0.10,0.20));
			this.AnalyticalModelParamsLimit.put(CNLSUEModel.BPRbetaName, new Tuple<Double,Double>(3.,5.));
			this.AnalyticalModelParamsLimit.put(CNLSUEModel.TransferalphaName, new Tuple<Double,Double>(0.25,0.75));
			this.AnalyticalModelParamsLimit.put(CNLSUEModel.TransferbetaName, new Tuple<Double,Double>(0.75,1.5));
		}
		
		/**
		 * This method overlays transit vehicles on the road network
		 * @param network
		 * @param Schedule
		 */
		public void performTransitVehicleOverlay(AnalyticalModelNetwork network, TransitSchedule schedule,Vehicles vehicles,String timeBeanId) {
			for(TransitLine tl:schedule.getTransitLines().values()) {
				for(TransitRoute tr:tl.getRoutes().values()) {
					ArrayList<Id<Link>> links=new ArrayList<>(tr.getRoute().getLinkIds());
					for(Departure d:tr.getDepartures().values()) {
						if(d.getDepartureTime()>this.timeBeans.get(timeBeanId).getFirst() && d.getDepartureTime()<=this.timeBeans.get(timeBeanId).getSecond()) {
							for(Id<Link> linkId:links) {
								((CNLLink)network.getLinks().get(linkId)).addLinkTransitVolume(vehicles.getVehicles().get(d.getVehicleId()).getType().getPcuEquivalents());
								Double oldCap=this.totalPtCapacityOnLink.get(timeBeanId).get(linkId);
								VehicleCapacity cap=vehicles.getVehicles().get(d.getVehicleId()).getType().getCapacity();
								if(oldCap!=null) {
									this.totalPtCapacityOnLink.get(timeBeanId).put(linkId, oldCap+(cap.getSeats()+cap.getStandingRoom()));
								}else {
									this.totalPtCapacityOnLink.get(timeBeanId).put(linkId, (double) cap.getSeats()+cap.getStandingRoom());
								}
								}
						}
					}
				}
			}
			logger.info("Completed transit vehicle overlay.");
		}
		
		
		/**
		 * This method overlays transit vehicles on the road network
		 * @param network
		 * @param Schedule
		 */
		public void performTransitVehicleOverlay(Map<String,AnalyticalModelNetwork> network, TransitSchedule schedule,Vehicles vehicles,String timeBeanId,
				LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams,double counterBuffer) {
			for(TransitLine tl:schedule.getTransitLines().values()) {
				for(TransitRoute tr:tl.getRoutes().values()) {
					ArrayList<Id<Link>> links=new ArrayList<>(tr.getRoute().getLinkIds());
					for(Departure d:tr.getDepartures().values()) {
						double time=d.getDepartureTime();
						String timeId=timeBeanId;
						if(d.getDepartureTime()>this.timeBeans.get(timeBeanId).getFirst() && d.getDepartureTime()<=this.timeBeans.get(timeBeanId).getSecond()) {
							for(Id<Link> linkId:links) {
								CNLLink link=((CNLLink)network.get(timeId).getLinks().get(linkId));
								time+=link.getLinkTravelTime(this.timeBeans.get(timeId), params, anaParams);
								link.addLinkTransitVolume(vehicles.getVehicles().get(d.getVehicleId()).getType().getPcuEquivalents());
								timeId=this.getTimeId(time);
								Double oldCap=this.totalPtCapacityOnLink.get(timeBeanId).get(linkId);
								VehicleCapacity cap=vehicles.getVehicles().get(d.getVehicleId()).getType().getCapacity();
								if(oldCap!=null) {
									this.totalPtCapacityOnLink.get(timeBeanId).put(linkId, oldCap+(cap.getSeats()+cap.getStandingRoom()));
								}else {
									this.totalPtCapacityOnLink.get(timeBeanId).put(linkId, (double) cap.getSeats()+cap.getStandingRoom());
								}
								}
						}
					}
				}
			}
			logger.info("Completed transit vehicle overlay.");
		}
		
		@Override
		public void generateRoutesAndOD(Population population,Network network,TransitSchedule transitSchedule,
				Scenario scenario,Map<String,FareCalculator> fareCalculator) {
			//this.setLastPopulation(population);
			//System.out.println("");
			this.odPairs=new CNLODpairs(network,population,transitSchedule,scenario,this.timeBeans);
			this.odPairs.generateODpairset();
			this.odPairs.generateRouteandLinkIncidence(0.);
			for(String s:this.timeBeans.keySet()) {
				this.getNetworks().put(s, new CNLNetwork(network));
				this.performTransitVehicleOverlay(this.getNetworks().get(s),
						transitSchedule,scenario.getTransitVehicles(),s);
				this.transitLinks.put(s,this.odPairs.getTransitLinks(s));
			}
			this.fareCalculator=fareCalculator;
			
			
			this.carDemand.size();
			
			this.ts=transitSchedule;
			for(String timeBeanId:this.timeBeans.keySet()) {
				this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
				this.Demand.put(timeBeanId, new HashMap<>(this.odPairs.getdemand(timeBeanId)));
				for(Id<AnalyticalModelODpair> odId:this.Demand.get(timeBeanId).keySet()) {
					double totalDemand=this.Demand.get(timeBeanId).get(odId);
					this.carDemand.get(timeBeanId).put(odId, 0.5*totalDemand);
				}
				logger.info("Startig from 0.5 auto and transit ratio");
				if(this.Demand.get(timeBeanId).size()!=this.carDemand.get(timeBeanId).size()) {
					logger.error("carDemand and total demand do not have same no of OD pair. This should not happen. Please check");
				}
			}
			
			int agentTrip=0;
			int matsimTrip=0;
			int agentDemand=0;
			for(AnalyticalModelODpair odPair:this.odPairs.getODpairset().values()) {
				agentTrip+=odPair.getAgentCounter();
				for(String s:odPair.getTimeBean().keySet()) {
					agentDemand+=odPair.getDemand().get(s);
				}
				
			}
			logger.info("Demand total = "+agentDemand);
			logger.info("Total Agent Trips = "+agentTrip);
		
		}
		
		public Map<String, FareCalculator> getFareCalculator() {
			return fareCalculator;
		}

		public void setFareCalculator(Map<String, FareCalculator> farecalc) {
			this.fareCalculator = farecalc;
		}
		public void setMSAAlpha(double alpha) {
			this.alphaMSA = alpha;
		}
		public void setMSAGamma(double gamma) {
			this.gammaMSA = gamma;
		}
		public void setTollerance(double tollerance) {
			this.tollerance = tollerance;
		}
		/**
		 * This method resets all the car demand 
		 */
		private void resetCarDemand() {
			
				
			for(String timeId:this.timeBeans.keySet()) {
				this.carDemand.put(timeId, new HashMap<Id<AnalyticalModelODpair>, Double>());
				for(Id<AnalyticalModelODpair> o:this.Demand.get(timeId).keySet()) {
					this.carDemand.get(timeId).put(o, this.Demand.get(timeId).get(o)*0.5);
				}

			}
		}
		
		/**
		 * This method does single OD network loading of only car demand.
		 * 
		 * @param ODpairId
		 * @param anaParams 
		 * @return
		 */
		
		protected Map<String,Map<Id<Link>,Double>> NetworkLoadingCarSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,double counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
			
			AnalyticalModelODpair odpair=this.odPairs.getODpairset().get(ODpairId);
			List<AnalyticalModelRoute> routes=odpair.getRoutes();
			Map<Id<AnalyticalModelRoute>,Double> routeFlows=new HashMap<>();
			Map<String,Map<Id<Link>,Double>> linkFlows=new HashMap<>();
			
			Map<Id<AnalyticalModelRoute>,Double> utility=new HashMap<>();
			for(AnalyticalModelRoute r:routes){
				double u=0;
				
				if(counter>1) {
					u=r.calcRouteUtility(params, anaParams,this.getNetworks(),this.timeBeans,timeBeanId,odpair.getMedian(timeBeanId));
					u=u+Math.log(odpair.getAutoPathSize().get(r.getRouteId()));//adding the path size term
					utility.put(r.getRouteId(), u);
				}else {
					u=0;
					utility.put(r.getRouteId(), u);
				}
				odpair.updateRouteUtility(r.getRouteId(), u,timeBeanId);
				
				if(u>300||u<-300) {
					logger.error("utility is either too small or too large. Increase or decrease the link miu accordingly. The utility is "+u+" for route "+r.getRouteId());
				}
			}
			
			//This is the route flow split
			for(AnalyticalModelRoute r:routes){
				double u=utility.get(r.getRouteId());
				double demand=this.carDemand.get(timeBeanId).get(ODpairId);
				double totalUtility=0;
				for(double d:utility.values()) {
					totalUtility+=Math.exp(d-u);
				}
				double flow=1/totalUtility*demand;
				//For testing purpose, can be removed later
				if(flow==Double.NaN||flow==Double.POSITIVE_INFINITY) {
					logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
					throw new IllegalArgumentException("Wait!!!!Error!!!!");
				}
				routeFlows.put(r.getRouteId(),flow);
				odpair.getRouteFlow().get(timeBeanId).put(r.getRouteId(), flow);	
			}
			for(String s:this.timeBeans.keySet()) {
				linkFlows.put(s, new HashMap<>());
				for(Id<Link> linkId:odpair.getLinkIncidence().keySet()) {
					linkFlows.get(s).put(linkId, 0.);
				}
			}
			
			for(Id<Link> linkId:odpair.getLinkIncidence().keySet()){
				for(AnalyticalModelRoute r:odpair.getLinkIncidence().get(linkId)){
					String timeId=r.getLinkReachTime().get(timeBeanId).get(linkId);
					linkFlows.get(timeId).put(linkId, linkFlows.get(timeId).get(linkId)+routeFlows.get(r.getRouteId()));
				}
			}

			return linkFlows;
		}
		
		public String getTimeId(Double time) {
			if(time>24*3600) time=time-24*3600;
			if(time==0) time=1.;
			for(Entry<String, Tuple<Double, Double>> s:this.timeBeans.entrySet()) {
				if(time>s.getValue().getFirst() && time<=s.getValue().getSecond()) {
					return s.getKey();
				}
			}
			return null;
		}
		
		/**
		 * This method does transit sue assignment on the transit network on (Total demand-Car Demand)
		 * @param ODpairId
		 * @param timeBeanId
		 * @param anaParams 
		 * @return
		 */
		protected HashMap<Id<TransitLink>,Double> NetworkLoadingTransitSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,int counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
			
			AnalyticalModelODpair odpair=this.odPairs.getODpairset().get(ODpairId);
			List<AnalyticalModelTransitRoute> routes=odpair.getTrRoutes(timeBeanId);
			
			HashMap<Id<AnalyticalModelTransitRoute>,Double> routeFlows=new HashMap<>();
			HashMap<Id<TransitLink>,Double> linkFlows=new HashMap<>();
			
			HashMap<Id<AnalyticalModelTransitRoute>,Double> utility=new HashMap<>();
			
			if(routes!=null && routes.size()!=0) {
			for(AnalyticalModelTransitRoute r:routes){
				double u=0;
				if(counter>1) {
					u=r.calcRouteUtility(params, anaParams,
						this.getNetworks().get(timeBeanId),this.fareCalculator,this.timeBeans.get(timeBeanId));
					u+=Math.log(odpair.getTrPathSize().get(timeBeanId).get(r.getTrRouteId()));//adding the path size term
					
					if(u==Double.NaN) {
						logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
						throw new IllegalArgumentException("Utility is NAN!!!");
					}
				}else {
					u=0;
				}
				if(u>300) {
					logger.warn("STOP!!!Utility is too large >300");
				}
				odpair.updateTrRouteUtility(r.getTrRouteId(), u,timeBeanId);
				utility.put(r.getTrRouteId(), u);
				//totalUtility+=Math.exp(u);
			}
//			if(totalUtility==0) {
//				logger.warn("STopp!!!! Total utility in the OD pair is zero. This can happen if there is no transit route in that OD pair.");
//			}
			for(AnalyticalModelTransitRoute r:routes){
				double totalDemand=this.getDemand().get(timeBeanId).get(ODpairId);
				double carDemand=this.getCarDemand().get(timeBeanId).get(ODpairId);
				double q=(totalDemand-carDemand);
				if(q<0) {
					throw new IllegalArgumentException("Stop!!! transit demand is negative!!!");
				}
				double u=utility.get(r.getTrRouteId());
				double totalUtility=0;
				for(double d:utility.values()) {
					totalUtility+=Math.exp(d-u);
				}
				
				double flow=q/totalUtility;
				if(Double.isNaN(flow)||flow==Double.POSITIVE_INFINITY||flow==Double.NEGATIVE_INFINITY) {
					logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
					throw new IllegalArgumentException("Error!!!!");
				}
				routeFlows.put(r.getTrRouteId(),flow);
				odpair.getTrRouteFlow().get(timeBeanId).put(r.getTrRouteId(), flow);		
			}

			}
			
			Set<Id<TransitLink>>linksets=getOdPairs().getODpairset().get(ODpairId).getTrLinkIncidence().keySet();
			for(Id<TransitLink> linkId:linksets){
				if(this.getTransitLinks().get(timeBeanId).containsKey(linkId)) {
				double linkflow=0;
				ArrayList<AnalyticalModelTransitRoute>incidence=getOdPairs().getODpairset().get(ODpairId).getTrLinkIncidence().get(linkId);
				for(AnalyticalModelTransitRoute r:incidence){
					List<AnalyticalModelTransitRoute> routesFromOd=routes;
					
					if(CNLSUEModel.routeContain(routesFromOd, r)) {
					linkflow+=routeFlows.get(r.getTrRouteId());
					}
					if(Double.isNaN(linkflow)) {
						logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
						throw new IllegalArgumentException("Stop!!!");
					}
				}
				linkFlows.put(linkId,linkflow);
				}
			}
			return linkFlows;
		}
		
}
