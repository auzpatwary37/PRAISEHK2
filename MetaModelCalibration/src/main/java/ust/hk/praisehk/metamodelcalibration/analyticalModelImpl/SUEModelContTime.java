package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Lists;

import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpairs;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.InternalParamCalibratorFunction;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
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
	
	/**
	 * Most probably should back propogate instead of front propogation
	 */
		private FileWriter fw;
		private int iterNo=0;
		private Measurements measurementsToUpdate=null;
		private final Logger logger=Logger.getLogger(CNLSUEModel.class);
		private String fileLoc="traget/";
		public String getFileLoc() {
			return fileLoc;
		}

		public void setFileLoc(String fileLoc) {
			this.fileLoc = fileLoc;
		}

		protected int consecutiveSUEErrorIncrease=0;
		protected LinkedHashMap<String,Double> AnalyticalModelInternalParams=new LinkedHashMap<>();
		protected LinkedHashMap<String,Double> Params=new LinkedHashMap<>();
		private LinkedHashMap<String,Tuple<Double,Double>> AnalyticalModelParamsLimit=new LinkedHashMap<>();
		
		
		private double alphaMSA=1.9;//parameter for decreasing MSA step size
		private double gammaMSA=0.1;//parameter for decreasing MSA step size
		
		//other Parameters for the Calibration Process
		private double tollerance=1;
		private double tolleranceLink=1;
		private double factorOfSafetyForTransitScheduling=1.2;
		private double staticWaitingTimeAtStop=20;
		//user input
		
		protected Map<String, Tuple<Double,Double>> timeBeans;
		private List<String> timeBeanOrder=new ArrayList<>();
		
		//MATSim Input
		protected Map<String, AnalyticalModelNetwork> networks=new ConcurrentHashMap<>();
		protected TransitSchedule ts;
		protected Scenario scenario;
		private Population population;
		protected Map<String,FareCalculator> fareCalculator=new HashMap<>();
		
		//Used Containers
		private ArrayList<Double> beta=new ArrayList<>(); //This is related to weighted MSA of the SUE
		private ArrayList<Double> error=new ArrayList<>();
		//private Map<String,ArrayList<Double>> error1=new ConcurrentHashMap<>();//This is related to weighted MSA of the SUE
		
		//TimebeanId vs demands map
		private Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> Demand=new ConcurrentHashMap<>();//Holds ODpair based demand
		private Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> carDemand=new ConcurrentHashMap<>(); 
		protected CNLODpairs odPairs;
		protected Map<String,Map<Id<TransitLink>,TransitLink>> transitLinks=new ConcurrentHashMap<>();
			
		private Population lastPopulation;
	
		//This are needed for output generation 
		
		protected Map<String,Map<Id<Link>,Double>> outputLinkTT=new ConcurrentHashMap<>();
		protected Map<String,Map<Id<TransitLink>,Double>> outputTrLinkTT=new ConcurrentHashMap<>();
		private Map<String,Map<Id<Link>,Double>> totalPtCapacityOnLink=new HashMap<>();
		protected Map<String,Map<String,Double>> individualPtCapacityOnLink=new HashMap<>();//mapping [timeId-(linkId___LineId___RouteId - vehicleSeatingAndStandignCap)
		protected Map<String,Map<String,Double>> individualPtVehicleOnLink=new HashMap<>();//mapping [timeId-(linkId___LineId___RouteId - vehicleTotalPcu)
		protected Map<String,Map<String,Double>>MTRCount=new ConcurrentHashMap<>();
		//All the parameters name
		//They are kept public to make it easily accessible as they are final they can not be modified
		
		
		
		public SUEModelContTime(Map<String, Tuple<Double, Double>> timeBean) {
			this.timeBeans=timeBean;
			this.timeBeanConsistancyChecker();
			this.defaultParameterInitiation(null);
			for(String timeBeanId:this.timeBeans.keySet()) {
				this.Demand.put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
				this.carDemand.put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
				this.transitLinks.put(timeBeanId, new HashMap<Id<TransitLink>, TransitLink>());
				
				//For result recording
				outputLinkTT.put(timeBeanId, new HashMap<>());
				outputTrLinkTT.put(timeBeanId, new HashMap<>());
				this.totalPtCapacityOnLink.put(timeBeanId, new HashMap<>());
				this.MTRCount.put(timeBeanId, new ConcurrentHashMap<>());
			}
			logger.info("Analytical model created successfully.");
			
		}
		
		public void timeBeanConsistancyChecker() {
//			if(this.timeBeans.size()==1) {
//				this.timeBeanOrder.add(this.timeBeans.keySet().toArray()[0]);
//				return;
//			}
			double endBeanEndTime=24*3600;
			Map<String,Tuple<Double,Double>>oldTimeBean=new HashMap<>(this.timeBeans);
			this.timeBeanOrder=new ArrayList<>();
			double lastBeanEndTime=0;
			List<Double> startTimes=new ArrayList<>();
			for(Entry<String, Tuple<Double, Double>> d:this.timeBeans.entrySet()) {
				startTimes.add(d.getValue().getFirst());
			}
			Collections.sort(startTimes);
			int k=0;
			for(double startTime:startTimes) {
				for(Entry<String, Tuple<Double, Double>> d:oldTimeBean.entrySet()) {
					if(d.getValue().getFirst()==startTime) {
						if(lastBeanEndTime!=d.getValue().getFirst() && lastBeanEndTime!=d.getValue().getFirst()-1) {//add another intermediary time bean
							String intermediaryTimeBean=d.getKey()+"_1";
							this.timeBeans.put(intermediaryTimeBean, new Tuple<>(lastBeanEndTime,d.getValue().getFirst()));
							this.timeBeanOrder.add(intermediaryTimeBean);
						}
						this.timeBeanOrder.add(d.getKey());
						lastBeanEndTime=d.getValue().getSecond();
						if(k==startTimes.size()-1 && lastBeanEndTime!=endBeanEndTime) {
							String intermediaryTimeBeanId="end";
							this.timeBeans.put(intermediaryTimeBeanId,new Tuple<>(lastBeanEndTime,endBeanEndTime));
							this.timeBeanOrder.add(intermediaryTimeBeanId);
						}
					}
				}
				k++;
				
			}
			
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
			
			if(this.Params.size()==0) {
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
		 * Reset the total pt capacity on link (except the timeBean) if this function is called in every iteration of the sue
		 */
		
		public Map<String,Map<Id<Link>,Double>> performTransitVehicleOverlay(Map<String,AnalyticalModelNetwork> network, TransitSchedule schedule,Vehicles vehicles,
				LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams,boolean multiplieFactorOfSafety, boolean useConstTransferTime) {
			long starttime=System.currentTimeMillis();
			this.totalPtCapacityOnLink.clear();
			this.individualPtVehicleOnLink.clear();
			this.individualPtCapacityOnLink.clear();
			Map<String,Map<Id<Link>,Double>> linkVolume=new HashMap<>();
			for(String s:this.timeBeans.keySet()) {
				this.totalPtCapacityOnLink.put(s, new HashMap<>());
				this.individualPtCapacityOnLink.put(s, new HashMap<>());
				this.individualPtVehicleOnLink.put(s, new HashMap<>());
				linkVolume.put(s,new HashMap<>());
			}
			for(TransitLine tl:schedule.getTransitLines().values()) {
				for(TransitRoute tr:tl.getRoutes().values()) {
					ArrayList<Id<Link>> links=new ArrayList<>(tr.getRoute().getLinkIds());
					List<Id<Link>> stopLinks=new ArrayList<>();
					for(TransitRouteStop rtst:tr.getStops()) {
						stopLinks.add(rtst.getStopFacility().getLinkId());
					}
					for(Departure d:tr.getDepartures().values()) {
						double time=d.getDepartureTime();
						
						String timeId=this.getTimeId(time);
						
//						if(Double.isNaN(time)) {
//							System.out.println("Debug!!!");
//						}
						
						for(Id<Link> linkId:links) {
							CNLLink link=((CNLLink)network.get(timeId).getLinks().get(linkId));
							if(multiplieFactorOfSafety) {
								time+=this.factorOfSafetyForTransitScheduling*link.getLinkTravelTime(this.timeBeans.get(timeId), params, anaParams);
							}else {
								time+=link.getLinkTravelTime(this.timeBeans.get(timeId), params, anaParams);
							}
							
							if(Double.isNaN(time)) {
								System.out.println("DebugPoint!!!");
							}
							if(stopLinks.contains(linkId)) {
								//time+=tr.getStops().get(k).getDepartureOffset();
								time+=this.staticWaitingTimeAtStop;
								
							}
							//link.addLinkTransitVolume(vehicles.getVehicles().get(d.getVehicleId()).getType().getPcuEquivalents());
							Double oldVolume=linkVolume.get(timeId).get(link.getId());
							try {
							if(oldVolume!=null) {
								//System.out.println();
								linkVolume.get(timeId).put(link.getId(), oldVolume+vehicles.getVehicles().get(d.getVehicleId()).getType().getPcuEquivalents());
							}else {
								Vehicle v=vehicles.getVehicles().get(d.getVehicleId());
								VehicleType vt=v.getType();
								linkVolume.get(timeId).put(link.getId(), vt.getPcuEquivalents());
							}}catch(Exception e) {
								System.out.println(e);
							}
							String key=linkId.toString()+"____"+tl.getId()+"___"+tr.getId();
							VehicleType vt=vehicles.getVehicles().get(d.getVehicleId()).getType();
							VehicleCapacity cap=vt.getCapacity();
							Double oldValue;
							if((oldValue=this.individualPtCapacityOnLink.get(timeId).get(key))!=null) {
								this.individualPtCapacityOnLink.get(timeId).put(key, oldValue+cap.getSeats()+cap.getStandingRoom());
								this.individualPtVehicleOnLink.get(timeId).put(key, this.individualPtVehicleOnLink.get(timeId).get(key)+1.);
							}else {
								this.individualPtCapacityOnLink.get(timeId).put(key,(double) (cap.getSeats()+cap.getStandingRoom()));
								this.individualPtVehicleOnLink.get(timeId).put(key,1.);	
							}
							Double oldCap=this.totalPtCapacityOnLink.get(timeId).get(linkId);
							if(oldCap!=null) {
								this.totalPtCapacityOnLink.get(timeId).put(linkId, oldCap+(cap.getSeats()+cap.getStandingRoom()));
							}else {
								this.totalPtCapacityOnLink.get(timeId).put(linkId, (double) cap.getSeats()+cap.getStandingRoom());
							}
							timeId=this.getTimeId(time);
						}
					}
				}
			}

			logger.info("Completed transit vehicle overlay. Total time = "+(System.currentTimeMillis()-starttime));
			return linkVolume;
		}
		
		
		
		
		
		@Override
		public void generateRoutesAndOD(Population population,Network network,TransitSchedule transitSchedule,
				Scenario scenario,Map<String,FareCalculator> fareCalculator) {
			//this.setLastPopulation(population);
			//System.out.println("");
			this.scenario=scenario;
			this.odPairs=new CNLODpairs(network,population,transitSchedule,scenario,this.timeBeans);
			this.odPairs.generateODpairset();
			this.ts=transitSchedule;
			for(String s:this.timeBeans.keySet()) {
				this.networks.put(s, new CNLNetwork(network));
			}
			this.performTransitVehicleOverlay(this.networks, this.ts,scenario.getTransitVehicles(), this.Params, this.AnalyticalModelInternalParams, 
					true, true);
			this.odPairs.generateRouteandLinkIncidence(0.,this.individualPtCapacityOnLink,this.individualPtVehicleOnLink);
			for(String s:this.timeBeans.keySet()) {
				this.transitLinks.get(s).putAll(this.odPairs.getTransitLinks(s));
				this.transitLinks.get(this.getNextTimeBean(s)).putAll(this.odPairs.getTransitLinks(s));//add all the links to the next time bean as well.
			}
			this.fareCalculator=fareCalculator;
					
			for(String timeBeanId:this.timeBeans.keySet()) {
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
			for(AnalyticalModelRoute rr:routes){
				double u=0;
				CNLRoute r=(CNLRoute)rr;
				if(counter>1) {
					u=r.calcRouteUtility(params, anaParams,this.getNetworks(),this.timeBeans,odpair,timeBeanId,
							this.getNextTimeBean(timeBeanId));
					u=u+Math.log(odpair.getAutoPathSize().get(r.getRouteId()));//adding the path size term
					utility.put(r.getRouteId(), u);
				}else {
					u=r.calcRouteUtility(params, anaParams,this.getNetworks(),this.timeBeans,odpair,timeBeanId,
							this.getNextTimeBean(timeBeanId));
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
				
				linkFlows.put(timeBeanId, new HashMap<>());
				String nextTimeBeanId=this.getNextTimeBean(timeBeanId);
				linkFlows.put(nextTimeBeanId, new HashMap<>());
				for(Id<Link> linkId:odpair.getLinkIncidence().keySet()) {
					linkFlows.get(timeBeanId).put(linkId, 0.);
					linkFlows.get(nextTimeBeanId).put(linkId, 0.);
				}
			
			
			for(Id<Link> linkId:odpair.getLinkIncidence().keySet()){
				for(AnalyticalModelRoute r:odpair.getLinkIncidence().get(linkId)){
					if(this.timeBeans.size()==1) {
						//String timeId=r.getLinkReachTime().get(timeBeanId).get(linkId);
						linkFlows.get(timeBeanId).put(linkId, linkFlows.get(timeBeanId).get(linkId)+routeFlows.get(r.getRouteId()));
					}else {
						double currentTimeProb=odpair.getDepartureTimeDistributions().get(timeBeanId)
								.cumulativeProbability(this.timeBeans.get(timeBeanId).getSecond()-r.getLinkReachTime().get(linkId));
						String nextTimeBean=this.getNextTimeBean(timeBeanId);
						linkFlows.get(timeBeanId).put(linkId, linkFlows.get(timeBeanId).get(linkId)+routeFlows.get(r.getRouteId())*currentTimeProb);
						linkFlows.get(nextTimeBean).put(linkId, linkFlows.get(nextTimeBean).get(linkId)+routeFlows.get(r.getRouteId())*(1-currentTimeProb));
					}
				}
			}

			return linkFlows;
		}
		
		
		
		public String getTimeId(Double time) {
			if(time>24*3600) time=time-24*3600;
			//if(time>24*3600) time=24*3600.;
			if(time==0) time=1.;
			if(time>=24*3600)return this.timeBeanOrder.get(this.timeBeans.size()-1);
			for(Entry<String, Tuple<Double, Double>> s:this.timeBeans.entrySet()) {
				if(time>s.getValue().getFirst() && time<=s.getValue().getSecond()) {
					return s.getKey();
				}
			}
			
			return null;
		}
		
		public String getNextTimeBean(String timeBeanId) {
			int index=this.timeBeanOrder.indexOf(timeBeanId);
			if(index==this.timeBeanOrder.size()-1) {
				return this.timeBeanOrder.get(0);
			}else {
				return this.timeBeanOrder.get(index+1);
			}
		}
		
		/**
		 * This method does transit sue assignment on the transit network on (Total demand-Car Demand)
		 * @param ODpairId
		 * @param timeBeanId
		 * @param anaParams 
		 * @return
		 */
		protected Map<String,Map<Id<TransitLink>,Double>> NetworkLoadingTransitSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,int counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
			
			AnalyticalModelODpair odpair=this.odPairs.getODpairset().get(ODpairId);
			List<AnalyticalModelTransitRoute> routes=odpair.getTrRoutes(timeBeanId);
			
			Map<Id<AnalyticalModelTransitRoute>,Double> routeFlows=new HashMap<>();
			Map<String,Map<Id<TransitLink>,Double>> linkFlows=new HashMap<>();
			
			Map<Id<AnalyticalModelTransitRoute>,Double> utility=new HashMap<>();
			Set<Id<TransitLink>>linksets= new HashSet<>();
			//System.out.println();
			if(routes!=null && routes.size()!=0) {
			for(AnalyticalModelTransitRoute rr:routes){
				CNLTransitRoute r=(CNLTransitRoute)rr;
				linksets.addAll(r.getTransitLinks().keySet());
				double u=0;
				if(counter>1) {
					u=r.calcRouteUtility(params, anaParams,
						this.networks,this.transitLinks.get(timeBeanId),this.fareCalculator,this.timeBeans,odpair,timeBeanId,this.getNextTimeBean(timeBeanId));
					u+=Math.log(odpair.getTrPathSize().get(timeBeanId).get(r.getTrRouteId()));//adding the path size term
					
					if(u==Double.NaN) {
						logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
						throw new IllegalArgumentException("Utility is NAN!!!");
					}
				}else {
					u=r.calcRouteUtility(params, anaParams,
							this.networks,this.transitLinks.get(timeBeanId),this.fareCalculator,this.timeBeans,odpair,timeBeanId,this.getNextTimeBean(timeBeanId));
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
				double totalDemand=this.Demand.get(timeBeanId).get(ODpairId);
				double carDemand=this.carDemand.get(timeBeanId).get(ODpairId);
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
				//System.out.println("number of routes = "+routeFlows.size()+" of "+routes.size()+".");
			}

			}
			
			linkFlows.put(timeBeanId, new HashMap<>());
			String nextTimeBeanid=this.getNextTimeBean(timeBeanId);
			linkFlows.put(nextTimeBeanid, new HashMap<>());
			for(Id<TransitLink> linkId:linksets) {
				linkFlows.get(timeBeanId).put(linkId, 0.);
				linkFlows.get(nextTimeBeanid).put(linkId, 0.);
			}
			
			
			for(Id<TransitLink> linkId:linksets){
				for(AnalyticalModelTransitRoute r:odpair.getTrLinkIncidence().get(linkId)){
					//System.out.println();
					CNLTransitRoute rr=null;
					if((rr=routeContain(routes, r))!=null) {
						//CNLTransitRoute rr=(CNLTransitRoute)r;
						if(this.timeBeans.size()==1) {
							//String timeId=r.getLinkReachTime().get(timeBeanId).get(linkId);
							linkFlows.get(timeBeanId).put(linkId, linkFlows.get(timeBeanId).get(linkId)+routeFlows.get(r.getTrRouteId()));
						}else {
							//System.out.println("Debug1!!k");
							double currentTimeProb=odpair.getDepartureTimeDistributions().get(timeBeanId)
									.cumulativeProbability(this.timeBeans.get(timeBeanId).getSecond()-rr.getInfo().getLinkReachTime().get(linkId));
							String nextTimeBean=this.getNextTimeBean(timeBeanId);
							linkFlows.get(timeBeanId).put(linkId, linkFlows.get(timeBeanId).get(linkId)+routeFlows.get(r.getTrRouteId())*currentTimeProb);
							linkFlows.get(nextTimeBean).put(linkId, linkFlows.get(nextTimeBean).get(linkId)+routeFlows.get(r.getTrRouteId())*(1-currentTimeProb));
						}
					}
				}
			}
			
			return linkFlows;
		}
		public CNLTransitRoute routeContain(List<AnalyticalModelTransitRoute> routesFromOd,AnalyticalModelTransitRoute route) {
			
			for(AnalyticalModelTransitRoute r:routesFromOd) {
				if(r.getTrRouteId().equals(route.getTrRouteId())) {
					//rr=(CNLTransitRoute)r;
					return (CNLTransitRoute)r;
				}
			}
			return null;
		}
		protected Map<String,Map<Id<Link>,Double>> performCarNetworkLoading(String timeBeanId, double counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
			List<Map<String, Map<Id<Link>,Double>>> linkVolumes=Collections.synchronizedList(new ArrayList<>());
			Map<String,Map<Id<Link>,Double>> linkVolume=new HashMap<>();
			//for(AnalyticalModelODpair e:this.odPairs.getODpairset().values()){
			this.getOdPairs().getODpairset().values().parallelStream().forEach((e)->{
				if(e.getRoutes()!=null && this.carDemand.get(timeBeanId).get(e.getODpairId())!=0){
					Map<String,Map<Id<Link>,Double>> ODvolume=this.NetworkLoadingCarSingleOD(e.getODpairId(),timeBeanId,counter,params,anaParams);

					linkVolumes.add(ODvolume);
					//linkVolumes.add(linkVolume);
				}
				//}
			});

//			for(String timeId:this.timeBeans.keySet()) {
//				Map<Id<Link>,Double> linkVolumeInTime= new HashMap<>();
//				linkVolume.put(timeId, linkVolumeInTime);
//				for(Map<String,Map<Id<Link>,Double>> ODvolume:linkVolumes) {
//					if(ODvolume.containsKey(timeId)) {
//						for(Id<Link>linkId:ODvolume.get(timeId).keySet()){
//							if(linkVolumeInTime.containsKey(linkId)){
//								linkVolumeInTime.put(linkId, linkVolumeInTime.get(linkId)+ODvolume.get(timeId).get(linkId));
//							}else{
//								linkVolumeInTime.put(linkId, ODvolume.get(timeId).get(linkId));
//							}
//						}
//					}
//				}
//			}
			
			for(Map<String,Map<Id<Link>,Double>> ODvolume:linkVolumes) {
				for(String timeId:ODvolume.keySet()) {
					if(!linkVolume.containsKey(timeId)) {
						linkVolume.put(timeId, new HashMap<>());
					}
					Map<Id<Link>,Double> innerMap=linkVolume.get(timeId);
					for(Id<Link>linkId:ODvolume.get(timeId).keySet()){
						if(innerMap.containsKey(linkId)){
							innerMap.put(linkId, innerMap.get(linkId)+ODvolume.get(timeId).get(linkId));
						}else{
							innerMap.put(linkId, ODvolume.get(timeId).get(linkId));
						}
					}
				}
			}
			
			
			return linkVolume;
		}

		/**
		 * This method should do the network loading for transit
		 * @param params 
		 * @param anaParams 
		 * @return
		 */
		protected Map<String,Map<Id<TransitLink>,Double>> performTransitNetworkLoading(String timeBeanId,int counter, LinkedHashMap<String, Double> params, LinkedHashMap<String, Double> anaParams){
			List<Map<String,Map<Id<TransitLink>,Double>>> linkVolumes=Collections.synchronizedList(new ArrayList<>());
			Map<String,Map<Id<TransitLink>,Double>>linkVolume=new HashMap<>();
			//for(AnalyticalModelODpair e:this.odPairs.getODpairset().values()){

			this.odPairs.getODpairset().values().parallelStream().forEach((e)->{
				double totalDemand=this.Demand.get(timeBeanId).get(e.getODpairId());
				double carDemand=this.carDemand.get(timeBeanId).get(e.getODpairId());
				if((totalDemand-carDemand)!=0) {
					Map<String,Map <Id<TransitLink>,Double>> ODvolume=this.NetworkLoadingTransitSingleOD(e.getODpairId(),timeBeanId,counter,params,anaParams);
					linkVolumes.add(ODvolume);
				}

				//linkVolumes.add(linkVolume);
				//}
			});

			for(Map<String, Map<Id<TransitLink>, Double>> ODvolume:linkVolumes) {
				for(String timeId:ODvolume.keySet()) {
					if(!linkVolume.containsKey(timeId)) {
						linkVolume.put(timeId, new HashMap<>());
					}
					Map<Id<TransitLink>,Double> innerMap=linkVolume.get(timeId);
					for(Id<TransitLink>linkId:ODvolume.get(timeId).keySet()){
						if(innerMap.containsKey(linkId)){
							innerMap.put(linkId, innerMap.get(linkId)+ODvolume.get(timeId).get(linkId));
						}else{
							innerMap.put(linkId, ODvolume.get(timeId).get(linkId));
						}
					}
				}
			}
			//});
			//System.out.println(linkVolume.size());
			

			return linkVolume;
		}
		
		private void writeDownError(int iteration,double error) {
			try {
				fw.append(iteration+","+error+"\n");
				fw.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		protected boolean UpdateLinkVolume(Map<String,Map<Id<Link>,Double>> linkVolume,Map<String,Map<Id<TransitLink>,Double>> transitlinkVolume,int counter){
			double squareSum=0;
			double flowSum=0;
			double linkSum=0;
			if(counter==1) {
				this.beta.clear();
				//this.error.clear();
				this.beta.add(1.);
			}else {
				if(error.get(counter-1)<error.get(counter-2)) {
					beta.add(beta.get(counter-2)+this.gammaMSA);
				}else {
					this.consecutiveSUEErrorIncrease++;
					beta.add(beta.get(counter-2)+this.alphaMSA);
					
				}
			}
			for(String timeBeanId:this.timeBeans.keySet()) {
				if(linkVolume.containsKey(timeBeanId)) {
				for(Entry<Id<Link>, Double> linkId:linkVolume.get(timeBeanId).entrySet()){
					double newVolume=linkId.getValue();
					double oldVolume=((AnalyticalModelLink) this.getNetworks().get(timeBeanId).getLinks().get(linkId.getKey())).getLinkCarVolume();
					double update;
					double counterPart=1/beta.get(counter-1);
					//counterPart=1./counter;
					update=counterPart*(newVolume-oldVolume);
					if(oldVolume!=0) {
						if(Math.abs(update)/oldVolume*100>this.tolleranceLink) {
							linkSum+=1;
						}
					}
					if(Math.abs(update)>=1) {
						flowSum++;
					}
					squareSum+=update*update;
					((AnalyticalModelLink) this.getNetworks().get(timeBeanId).getLinks().get(linkId.getKey())).addLinkCarVolume(update);
				}
				}
			}
			for(String timeBeanId:this.timeBeans.keySet()) {
				if(transitlinkVolume.containsKey(timeBeanId)) {
				for(Entry<Id<TransitLink>, Double> trlinkId:transitlinkVolume.get(timeBeanId).entrySet()){
					double newVolume=trlinkId.getValue();
					TransitLink trl=this.transitLinks.get(timeBeanId).get(trlinkId.getKey());
					double oldVolume=trl.getPassangerCount();
					double update;
					double counterPart=1/beta.get(counter-1);
				
					update=counterPart*(newVolume-oldVolume);
					if(oldVolume!=0) {
						if(Math.abs(update)/oldVolume*100>this.tolleranceLink) {
							linkSum+=1;
						}
					}
					if(Math.abs(update)>=1) {
						flowSum++;
					}
					squareSum+=update*update;
					this.transitLinks.get(timeBeanId).get(trlinkId.getKey()).addPassanger(update,this.getNetworks().get(timeBeanId));
				}
				}
			}
			squareSum=Math.sqrt(squareSum);
		
			if(squareSum<this.tollerance||linkSum==0||flowSum==0) {
				return true;
				
			}else {
				return false;
			}
		}
		
		protected boolean CheckConvergence(Map<String,Map<Id<Link>,Double>> linkVolume,Map<String,Map<Id<TransitLink>,Double>> transitlinkVolume, double tollerance,int counter){
			double linkBelow1=0;
			double squareSum=0;
			double sum=0;
			double error=0;
			int highFlowLink=0;
			int totalLink=0;
			for(String timeBeanId:this.timeBeans.keySet()) {
				if(linkVolume.containsKey(timeBeanId)) {
					for(Entry<Id<Link>, Double> linkid:linkVolume.get(timeBeanId).entrySet()){
						totalLink++;
						if(linkid.getValue()==0) {
							error=0;
						}else {
							double currentVolume=((AnalyticalModelLink) this.getNetworks().get(timeBeanId).getLinks().get(linkid.getKey())).getLinkCarVolume();
							double newVolume=linkid.getValue();

							if((newVolume-currentVolume)>10) {
								highFlowLink++;
							}
							error=Math.pow((currentVolume-newVolume),2);
							if(error==Double.POSITIVE_INFINITY||error==Double.NEGATIVE_INFINITY) {
								throw new IllegalArgumentException("Error is infinity!!!");
							}
							if(error/newVolume*10>tollerance) {					
								sum+=1;
							}
							if(error<1) {
								linkBelow1++;
							}
						}

						squareSum+=error;
						if(squareSum==Double.POSITIVE_INFINITY||squareSum==Double.NEGATIVE_INFINITY) {
							throw new IllegalArgumentException("error is infinity!!!");
						}
					}
				}
				if(transitlinkVolume.containsKey(timeBeanId)) {
					for(Entry<Id<TransitLink>, Double> transitlinkid:transitlinkVolume.get(timeBeanId).entrySet()){

						totalLink++;
						if(transitlinkid.getValue()==0) {
							error=0;
						}else {
							double currentVolume=this.getTransitLinks().get(timeBeanId).get(transitlinkid.getKey()).getPassangerCount();
							double newVolume=transitlinkid.getValue();
							error=Math.pow((currentVolume-newVolume),2);
							if(error/newVolume*10>tollerance) {

								sum+=1;
							}
							if(error<1) {
								linkBelow1++;
							}
						}
						if(error==Double.NaN||error==Double.NEGATIVE_INFINITY) {
							throw new IllegalArgumentException("Stop!!! There is something wrong!!!");
						}
						squareSum+=error;
					}
				}
			}
			if(squareSum==Double.NaN) {
				System.out.println("WAIT!!!!Problem!!!!!");
			}
			squareSum=Math.sqrt(squareSum);
			if(counter==1) {
				this.error.clear();
			}
			this.error.add(squareSum);
			logger.info("ERROR amount in iter "+counter+" = "+squareSum);
			
			this.writeDownError(counter, squareSum);
			
			//System.out.println("in timeBean Id "+timeBeanId+" No of link not converged = "+sum);
			
//			try {
//				//CNLSUEModel.writeData(timeBeanId+","+counter+","+squareSum+","+sum, this.fileLoc+"ErrorData"+timeBeanId+".csv");
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			
			if (squareSum<=1||sum==0||linkBelow1==totalLink){
				return true;
			}else{
				return false;
			}
			
		}
		
		
		/**
		 * This method perform modal Split
		 * @param params
		 * @param anaParams
		 * @param timeBeanId
		 */
		protected void performModalSplit(LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams) {
			double modeMiu=anaParams.get(CNLSUEModel.ModeMiuName);
			for(String timeBeanId:this.timeBeans.keySet()) {
				for(AnalyticalModelODpair odPair:this.odPairs.getODpairset().values()){
					double demand=this.Demand.get(timeBeanId).get(odPair.getODpairId());
					if(demand!=0) { 
						double carUtility=odPair.getExpectedMaximumCarUtility(params, anaParams, timeBeanId);
						double transitUtility=odPair.getExpectedMaximumTransitUtility(params, anaParams, timeBeanId);

						if(carUtility==Double.NEGATIVE_INFINITY||transitUtility==Double.POSITIVE_INFINITY||
								Math.exp(transitUtility*modeMiu)==Double.POSITIVE_INFINITY) {
							this.carDemand.get(timeBeanId).put(odPair.getODpairId(), 0.0);

						}else if(transitUtility==Double.NEGATIVE_INFINITY||carUtility==Double.POSITIVE_INFINITY
								||Math.exp(carUtility*modeMiu)==Double.POSITIVE_INFINITY) {
							this.carDemand.get(timeBeanId).put(odPair.getODpairId(), this.Demand.get(timeBeanId).get(odPair.getODpairId()));
						}else if(carUtility==Double.NEGATIVE_INFINITY && transitUtility==Double.NEGATIVE_INFINITY){
							this.carDemand.get(timeBeanId).put(odPair.getODpairId(), 0.);
						}else {
							double carProportion=Math.exp(carUtility*modeMiu)/(Math.exp(carUtility*modeMiu)+Math.exp(transitUtility*modeMiu));
							//System.out.println("Car Proportion = "+carProportion);
							Double cardemand=carProportion*this.Demand.get(timeBeanId).get(odPair.getODpairId());
							if(cardemand==Double.NaN||cardemand==Double.POSITIVE_INFINITY||cardemand==Double.NEGATIVE_INFINITY) {
								logger.error("Car Demand is invalid");
								throw new IllegalArgumentException("car demand is invalid");
							}
							this.carDemand.get(timeBeanId).put(odPair.getODpairId(),cardemand);
						}
					}
				}
			}
		}
		
		
		@Override
		public Map<Integer, Measurements> calibrateInternalParams(Map<Integer,Measurements> simMeasurements,Map<Integer,LinkedHashMap<String,Double>>params,LinkedHashMap<String,Double> initialParam,int currentParamNo) {
			
			double[] x=new double[initialParam.size()];

			int j=0;
			for (double d:initialParam.values()) {
				x[j]=1;
				j++;
			}

			InternalParamCalibratorFunction iFunction=new InternalParamCalibratorFunction(simMeasurements,params,this,initialParam,currentParamNo);
			
			//Call the optimization subroutine
			CobylaExitStatus result = Cobyla.findMinimum(iFunction,x.length, x.length*2,
					x,20.,.05 ,3, 100);
			int i=0;
			for(String s:initialParam.keySet()) {
				this.AnalyticalModelInternalParams.put(s, initialParam.get(s)*(x[i]/100+1));
				i++;
			}
			return iFunction.getUpdatedAnaCount();
		}
		
		@Override
		public void clearLinkCarandTransitVolume() {
			for(String timeBeanId:this.timeBeans.keySet()) {
				this.getNetworks().get(timeBeanId).clearLinkVolumesfull();
				this.getNetworks().get(timeBeanId).clearLinkTransitPassangerVolume();
				for(Id<TransitLink> trlinkId:this.getTransitLinks().get(timeBeanId).keySet()) {
					this.getTransitLinks().get(timeBeanId).get(trlinkId).resetLink();
				}
			}
		}
		
		/**
		 * This method performs a Traffic Assignment of a single time Bean
		 * @param params: calibration Parameters
		 * @param anaParams: Analytical model Parameters
		 * @param timeBeanId
		 */
		public void TA(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams) {
			
			
			boolean shouldStop=false;
			
			for(int i=1;i<500;i++) {
				List<Map<String,Map<Id<TransitLink>, Double>>> linkTransitVolumes=Collections.synchronizedList(new ArrayList<>());
				Map<String,Map<Id<Link>, Double>> transitVehicles;
				List<Map<String,Map<Id<Link>,Double>>> linkCarVolumes=Collections.synchronizedList(new ArrayList<>());
				
				Map<String,Map<Id<TransitLink>, Double>> combinedTransitVolumes=new HashMap<>();
				Map<String,Map<Id<Link>,Double>> combinedCarVolumes=new HashMap<>();
				//for(this.car)
				//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
				final int k=i;
				
				transitVehicles=this.performTransitVehicleOverlay(networks, ts, this.scenario.getTransitVehicles(), params, anaParams, false, true);
				//this.timeBeans.keySet().parallelStream().forEach((timeBeanId)->{
				long startTime=System.currentTimeMillis();
				for(String timeBeanId:this.timeBeans.keySet()) {
					linkCarVolumes.add(this.performCarNetworkLoading(timeBeanId,k,params,anaParams));
					linkTransitVolumes.add(this.performTransitNetworkLoading(timeBeanId,k,params,anaParams));
				}
				//});
				long requiredTime=System.currentTimeMillis()-startTime;
				System.out.println("Required time for Assignment = "+requiredTime);
				
				//combine the flows
				startTime=System.currentTimeMillis();
				
				for(String timeId:transitVehicles.keySet()) {
					if(!combinedCarVolumes.containsKey(timeId)) {
						combinedCarVolumes.put(timeId, new HashMap<>());
					}
					Map<Id<Link>,Double> innerMap=combinedCarVolumes.get(timeId);
					for(Entry<Id<Link>, Double> linkVolume:transitVehicles.get(timeId).entrySet()) {
						if(innerMap.containsKey(linkVolume.getKey())) {
							innerMap.put(linkVolume.getKey(), innerMap.get(linkVolume.getKey())+linkVolume.getValue());
						}else{
							innerMap.put(linkVolume.getKey(), linkVolume.getValue());
						}
					}
				}
				
				for(Map<String,Map<Id<Link>,Double>>linkCarVolume:linkCarVolumes) {
					for(String timeId:linkCarVolume.keySet()) {
						if(!combinedCarVolumes.containsKey(timeId)) {
							combinedCarVolumes.put(timeId, new HashMap<>());
						}
						Map<Id<Link>,Double> innerMap=combinedCarVolumes.get(timeId);
						for(Entry<Id<Link>, Double> linkVolume:linkCarVolume.get(timeId).entrySet()) {
							if(innerMap.containsKey(linkVolume.getKey())) {
								innerMap.put(linkVolume.getKey(), innerMap.get(linkVolume.getKey())+linkVolume.getValue());
							}else{
								innerMap.put(linkVolume.getKey(), linkVolume.getValue());
							}
						}
					}
				}
				
				for(Map<String,Map<Id<TransitLink>,Double>>linkTrVolume:linkTransitVolumes) {
					for(String timeId:linkTrVolume.keySet()) {
						if(!combinedTransitVolumes.containsKey(timeId)) {
							combinedTransitVolumes.put(timeId, new HashMap<>());
						}
						Map<Id<TransitLink>,Double> innerMap=combinedTransitVolumes.get(timeId);
						for(Entry<Id<TransitLink>, Double> linkVolume:linkTrVolume.get(timeId).entrySet()) {
							if(innerMap.containsKey(linkVolume.getKey())) {
								innerMap.put(linkVolume.getKey(), innerMap.get(linkVolume.getKey())+linkVolume.getValue());
							}else{
								innerMap.put(linkVolume.getKey(), linkVolume.getValue());
							}
						}
					}
				}
				

				requiredTime=System.currentTimeMillis()-startTime;
				System.out.println("Time required for combining flow = "+requiredTime);
				
				shouldStop=this.CheckConvergence(combinedCarVolumes, combinedTransitVolumes, this.tollerance,i);
				this.UpdateLinkVolume(combinedCarVolumes, combinedTransitVolumes, i);
				
				if(i==1 && shouldStop==true) {
					boolean demandEmpty=true;
					for(String timeBeanId:this.timeBeans.keySet()) {
					for(AnalyticalModelODpair od:this.odPairs.getODpairset().values()) {
						if(od.getDemand().get(timeBeanId)!=0) {
							demandEmpty=false;
							break;
						}
					}
					}
					if(!demandEmpty) {
						System.out.println("The model cannot converge on first iteration!!!");
					}
				}
				if(shouldStop) {
					//collect travel time
					this.collectTravelTime(params, anaParams);
//					//collect travel time for transit
//					for(TransitLink link:this.transitLinks.get(timeBeanId).values()) {
//						if(link instanceof TransitDirectLink) {
//							this.outputTrLinkTT.get(timeBeanId).put(link.getTrLinkId(), 
//									((TransitDirectLink)link).getLinkTravelTime(this.networks.get(timeBeanId),this.timeBeans.get(timeBeanId),
//											params, anaParams));
//						}else {
//							this.outputTrLinkTT.get(timeBeanId).put(link.getTrLinkId(), 
//									((TransitTransferLink)link).getWaitingTime(anaParams,this.networks.get(timeBeanId)));
//						}
//						
//					}
					
					break;
					}
				startTime=System.currentTimeMillis();
				this.performModalSplit(params, anaParams);
				requiredTime=System.currentTimeMillis()-startTime;
				System.out.println("time required for modal split = "+requiredTime);
			}
			
			
		}
		
		protected void collectTravelTime(LinkedHashMap<String,Double> params,LinkedHashMap<String,Double>anaParams) {
			if(this.measurementsToUpdate!=null) {
				List<Measurement>ms= this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.linkTravelTime);
				for(String timeBeanId:this.timeBeans.keySet()) {
				for(Measurement m:ms) {
					if(m.getVolumes().containsKey(timeBeanId)) {
						m.putVolume(timeBeanId, ((CNLLink)this.networks.get(timeBeanId).getLinks().get(((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0))).getLinkTravelTime(this.timeBeans.get(timeBeanId),
						params, anaParams));
					}
				}
				}
			}
		}
		

		public LinkedHashMap<String, Double> getParams() {
			return Params;
		}

		public LinkedHashMap<String, Double> getAnalyticalModelInternalParams() {
			return AnalyticalModelInternalParams;
		}
		
		
		public static void writeData(String s , String fileLoc) throws IOException {
			File file=new File(fileLoc);
			FileWriter fw=new FileWriter(file,true);
			fw.append("\n");
			fw.append(s);
			
			fw.flush();
			fw.close();
		}

		public LinkedHashMap<String, Tuple<Double, Double>> getAnalyticalModelParamsLimit() {
			return AnalyticalModelParamsLimit;
		}

		public Map<String, Tuple<Double, Double>> getTimeBeans() {
			return timeBeans;
		}
		
		@Override 
		public Population getLastPopulation() {
			return lastPopulation;
		}



		public AnalyticalModelODpairs getOdPairs() {
			return odPairs;
		}


		@Override
		public Map <String,AnalyticalModelNetwork> getNetworks() {
			return networks;
		}

		

		public Map<String,Map<Id<TransitLink>,TransitLink>> getTransitLinks() {
			return transitLinks;
		}



		public TransitSchedule getTs() {
			return ts;
		}


		public int getConsecutiveSUEErrorIncrease() {
			return consecutiveSUEErrorIncrease;
		}

		

		public Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> getDemand() {
			return Demand;
		}

		

		public Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> getCarDemand() {
			return carDemand;
		}

		public double getTollerance() {
			return tollerance;
		}

		@Override
		public Measurements perFormSUE(LinkedHashMap<String, Double> params, Measurements originalMeasurements) {
			
			return this.perFormSUE(params,this.AnalyticalModelInternalParams, originalMeasurements);
		}
		
		
		@Override
		public Measurements perFormSUE(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,Measurements originalMeasurements) {
			this.resetCarDemand();
			try {
				this.fw=new FileWriter(new File(this.fileLoc+"Calibration/errorLogger"+iterNo+".csv"));
				this.fw.append("IterationNo,Error\n");
				this.fw.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			this.measurementsToUpdate=originalMeasurements.clone();
			LinkedHashMap<String,Double> inputParams=new LinkedHashMap<>(params);
			LinkedHashMap<String,Double> inputAnaParams=new LinkedHashMap<>(anaParams);
			//Loading missing parameters from the default values		
			
			
			
			//Checking and updating for the parameters 
			for(Entry<String,Double> e:this.Params.entrySet()) {
				if(!inputParams.containsKey(e.getKey())) {
					inputParams.put(e.getKey(), e.getValue());
				}
			}
			
			//Checking and updating for the analytical model parameters
			for(Entry<String,Double> e:this.AnalyticalModelInternalParams.entrySet()) {
				if(!anaParams.containsKey(e.getKey())) {
					anaParams.put(e.getKey(), e.getValue());
				}
			}
			
			this.TA(inputParams, inputAnaParams);

			//Collecting the Link Flows
			Set<Id<Link>> linkList=new HashSet<>();
			for(Measurement m:this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.linkVolume)) {
				for(String timeBeanId:m.getVolumes().keySet()) {
				double count=0;
				for(Id<Link> linkId:(ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)) {
					count+=((AnalyticalModelLink) this.getNetworks().get(timeBeanId).getLinks().get(linkId)).getLinkAADTVolume();
				}
				m.putVolume(timeBeanId, count);
				}
			}
			
			
			
			//collect pt occupancy
			for(Measurement m:this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.averagePTOccumpancy)) {
				for(String timeBeanId:m.getVolumes().keySet()) {
					Id<Link>linkId=((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0);
					double occupancy=((CNLLink)this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkTransitPassenger()/this.totalPtCapacityOnLink.get(timeBeanId).get(linkId);
					m.putVolume(timeBeanId, occupancy);
				}
			}
			
			//collect smartCard Entry
			
			Map<String,Map<String,Double>>entryCount=new HashMap<>();//First string is lineid+routeid+entryStopId second string is volume key
			for(Measurement m:this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.smartCardEntry)) {
				String key=m.getAttribute(Measurement.transitLineAttributeName)+"___"+m.getAttribute(Measurement.transitRouteAttributeName)+"___"+m.getAttribute(Measurement.transitBoardingStopAtrributeName);
				//System.out.println();
				entryCount.put(key, new HashMap<>());
				for(String s:m.getVolumes().keySet()) {
					entryCount.get(key).put(s, 0.);
				}
			}
			
			for(String timeBeanId:this.transitLinks.keySet()) {
				for(TransitLink trl:this.transitLinks.get(timeBeanId).values()) {
					if(trl instanceof TransitDirectLink) {
						TransitDirectLink trdl=(TransitDirectLink)trl;
						String key= trdl.getLineId()+"___"+trdl.getRouteId()+"___"+trdl.getStartStopId();
						if(entryCount.containsKey(key) && entryCount.get(key).containsKey(timeBeanId)) {
							entryCount.get(key).put(timeBeanId, entryCount.get(key).get(timeBeanId)+trl.getPassangerCount());
						}
					}
				}
			}
			
			for(Measurement m:this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.smartCardEntry)) {
				String key=m.getAttribute(Measurement.transitLineAttributeName)+"___"+m.getAttribute(Measurement.transitRouteAttributeName)+"___"+m.getAttribute(Measurement.transitBoardingStopAtrributeName);
				for(String timeBeanId:m.getVolumes().keySet()) {
					m.putVolume(timeBeanId, entryCount.get(key).get(timeBeanId));
				}
			}
			
			//Collect smart card entry and exit
			Map<String,Map<String,Double>>entryAndExitCountBus=new HashMap<>();//First string is lineid+routeid+entryStopId second string is volume key
			Map<String,Map<String,Double>>entryAndExitCountMTR=new HashMap<>();
			for(Measurement m:this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.smartCardEntryAndExit)) {
				String mode=m.getAttribute(Measurement.transitModeAttributeName).toString();
				String key=null;
				if(mode.equals("train")) {
					key=m.getAttribute(Measurement.transitBoardingStopAtrributeName).toString()+"___"+m.getAttribute(Measurement.transitAlightingStopAttributeName).toString()+"___"+m.getAttribute(Measurement.transitModeAttributeName).toString();
					entryAndExitCountMTR.put(key, new HashMap<>());
					for(String s:m.getVolumes().keySet()) {
						entryAndExitCountMTR.get(key).put(s, 0.);
					}
				}else {
					key=m.getAttribute(Measurement.transitBoardingStopAtrributeName).toString()+"___"+m.getAttribute(Measurement.transitAlightingStopAttributeName).toString()+"___"
							+m.getAttribute(Measurement.transitLineAttributeName)+"___"+m.getAttribute(Measurement.transitRouteAttributeName);
					entryAndExitCountBus.put(key, new HashMap<>());
					for(String s:m.getVolumes().keySet()) {
						entryAndExitCountBus.get(key).put(s, 0.);
					}
				}
				               
				
			}
			
			for(String timeBeanId:this.transitLinks.keySet()) {
				for(TransitLink trl:this.transitLinks.get(timeBeanId).values()) {
					if(trl instanceof TransitDirectLink) {
						TransitDirectLink trdl=(TransitDirectLink)trl;
						String key= trdl.getStartStopId()+"___"+trdl.getEndStopId()+"___"+trdl.getLineId()+"___"+trdl.getRouteId();
						if(entryAndExitCountBus.containsKey(key) && entryAndExitCountBus.get(key).containsKey(timeBeanId)) {
							entryAndExitCountBus.get(key).put(timeBeanId, entryAndExitCountBus.get(key).get(timeBeanId)+trl.getPassangerCount());
						}
					}
				}
			}
			
			for(AnalyticalModelODpair odpair:this.odPairs.getODpairset().values()) {
				for(String timeBeanId:this.timeBeans.keySet()) {
					if(odpair.getTrRoutes(timeBeanId)!=null) {
					for(AnalyticalModelTransitRoute tr:odpair.getTrRoutes(timeBeanId)) {
						if(this.Demand.get(timeBeanId).get(odpair.getODpairId())!=0) {
						for(String key:entryAndExitCountMTR.keySet()) {
							if(((CNLTransitRoute)tr).getFareLinks().contains(key)) {
								entryAndExitCountMTR.get(key).put(timeBeanId, entryAndExitCountMTR.get(key).get(timeBeanId)+odpair.getTrRouteFlow().get(timeBeanId).get(tr.getTrRouteId()));
							}
						}
						}
					}
					}
				}
					
			}
			
			for(Measurement m:this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.smartCardEntryAndExit)) {
				String mode=m.getAttribute(Measurement.transitModeAttributeName).toString();
				String key=null;
				if(mode.equals("train")) {
					key=m.getAttribute(Measurement.transitBoardingStopAtrributeName).toString()+"___"+m.getAttribute(Measurement.transitAlightingStopAttributeName).toString()+"___"+m.getAttribute(Measurement.transitModeAttributeName).toString();
					for(String s:m.getVolumes().keySet()) {
						m.putVolume(s, entryAndExitCountMTR.get(key).get(s));
					}
				}else {
					key=m.getAttribute(Measurement.transitBoardingStopAtrributeName).toString()+"___"+m.getAttribute(Measurement.transitAlightingStopAttributeName).toString()+"___"
							+m.getAttribute(Measurement.transitLineAttributeName)+"___"+m.getAttribute(Measurement.transitRouteAttributeName);
					for(String s:m.getVolumes().keySet()) {
						m.putVolume(s,entryAndExitCountBus.get(key).get(s));
					}
				}
			}
			this.iterNo++;
			//new OdInfoWriter("toyScenario/ODInfo/odInfo",this.timeBeans).writeOdInfo(this.getOdPairs(), getDemand(), getCarDemand(), inputParams, inputAnaParams);
			return this.measurementsToUpdate;
		}
}
