package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.Vehicles;

import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitFareAndHandler.FareLink;
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
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SignalFlowReductionGenerator;
import ust.hk.praisehk.metamodelcalibration.measurements.MTRLinkVolumeInfo;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

/**
 * This model now should have the ability to handle sub population 
 * No need for a extended class with sub population feature 
 * @author ashraf
 *
 */
public class CNLSUEModel implements AnalyticalModel{
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
		private Map<Id<AnalyticalModelODpair>,Map<String,String>> odMultiplierId=null;
		private boolean emptyMeasurements=false;
		private Measurements measurementsToUpdate=null;
		private static final Logger logger = Logger.getLogger(CNLSUEModel.class);
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
		private double tollerance= 1;
		private double tolleranceLink=1;
		//user input
	
		private Map<String, Tuple<Double,Double>> timeBeans;
		
		//MATSim Input
		private Map<String, AnalyticalModelNetwork> networks=new ConcurrentHashMap<>();
		protected TransitSchedule ts;
		protected Scenario scenario;
		private Population population;
		protected Map<String,FareCalculator> fareCalculator=new HashMap<>();
		
		//Used Containers
		private Map<String,ArrayList<Double>> beta=new ConcurrentHashMap<>(); //This is related to weighted MSA of the SUE
		private Map<String,ArrayList<Double>> error=new ConcurrentHashMap<>();
		private Map<String,ArrayList<Double>> error1=new ConcurrentHashMap<>();//This is related to weighted MSA of the SUE
		
		//TimebeanId vs demands map
		private Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> Demand=new ConcurrentHashMap<>();//Holds ODpair based demand
		private Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> carDemand=new ConcurrentHashMap<>(); 
		protected CNLODpairs odPairs;
		private Map<String,Map<Id<TransitLink>,TransitLink>> transitLinks=new ConcurrentHashMap<>();
			
		private Population lastPopulation;
	
		//This are needed for output generation 
		
		protected Map<String,Map<Id<Link>,Double>> outputLinkTT=new ConcurrentHashMap<>();
		protected Map<String,Map<Id<TransitLink>,Double>> outputTrLinkTT=new ConcurrentHashMap<>();
		private Map<String,Map<Id<Link>,Double>> totalPtCapacityOnLink=new HashMap<>();
		protected Map<String,Map<String,Double>>MTRCount=new ConcurrentHashMap<>();
		//All the parameters name
		//They are kept public to make it easily accessible as they are final they can not be modified
		
		
		
		public static final String BPRalphaName="BPRalpha";
		public static final String BPRbetaName="BPRbeta";
		public static final String LinkMiuName="LinkMiu";
		public static final String ModeMiuName="ModeMiu";
		public static final String TransferalphaName="Transferalpha";
		public static final String TransferbetaName="Transferbeta";

		
	public CNLSUEModel(Map<String, Tuple<Double, Double>> timeBean) {
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
	
	
	

	public Map<Id<AnalyticalModelODpair>, Map<String, String>> getOdMultiplierId() {
		return odMultiplierId;
	}

	public void setOdMultiplierId(Map<Id<AnalyticalModelODpair>, Map<String, String>> odMultiplierId) {
		this.odMultiplierId = odMultiplierId;
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
//		if(config==null) {
//			config=ConfigUtils.createConfig();
//		}
//		
//
//		this.Params.put(CNLSUEModel.MarginalUtilityofTravelCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfTraveling());
//		this.Params.put(CNLSUEModel.MarginalUtilityofDistanceCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfDistance());
//		this.Params.put(CNLSUEModel.MarginalUtilityofMoneyName,config.planCalcScore().getMarginalUtilityOfMoney());
//		this.Params.put(CNLSUEModel.DistanceBasedMoneyCostCarName,config.planCalcScore().getOrCreateModeParams("car").getMonetaryDistanceRate());
//		this.Params.put(CNLSUEModel.MarginalUtilityofTravelptName, config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfTraveling());
//		this.Params.put(CNLSUEModel.MarginalUtilityOfDistancePtName, config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfDistance());
//		this.Params.put(CNLSUEModel.MarginalUtilityofWaitingName,config.planCalcScore().getMarginalUtlOfWaitingPt_utils_hr());
//		this.Params.put(CNLSUEModel.UtilityOfLineSwitchName,config.planCalcScore().getUtilityOfLineSwitch());
//		this.Params.put(CNLSUEModel.MarginalUtilityOfWalkingName, config.planCalcScore().getOrCreateModeParams("walk").getMarginalUtilityOfTraveling());
//		this.Params.put(CNLSUEModel.DistanceBasedMoneyCostWalkName, config.planCalcScore().getOrCreateModeParams("walk").getMonetaryDistanceRate());
//		this.Params.put(CNLSUEModel.ModeConstantPtname,config.planCalcScore().getOrCreateModeParams("pt").getConstant());
//		this.Params.put(CNLSUEModel.ModeConstantCarName,config.planCalcScore().getOrCreateModeParams("car").getConstant());
//		this.Params.put(CNLSUEModel.MarginalUtilityofPerformName, config.planCalcScore().getPerforming_utils_hr());
//		this.Params.put(CNLSUEModel.CapacityMultiplierName, 1.0);
	}
	
	private LinkedHashMap<String,Double> handleBasicParams(LinkedHashMap<String,Double> params, String subPopulation, Config config){
		LinkedHashMap<String,Double> newParams = new LinkedHashMap<>();
		// Handle the original params first
		for(String s:params.keySet()) {
			if(subPopulation!=null && (s.contains(subPopulation)||s.contains("All"))) {
				newParams.put(s.split(" ")[1],params.get(s));
			}else if (subPopulation == null) {
				newParams.put(s, params.get(s));
			}
		}
		ScoringParameters scParam = new ScoringParameters.Builder(config.planCalcScore(), config.planCalcScore().getScoringParameters(subPopulation), config.scenario()).build();
		
		newParams.compute(CNLSUEModel.MarginalUtilityofTravelCarName,(k,v)->v==null?scParam.modeParams.get("car").marginalUtilityOfTraveling_s*3600:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofDistanceCarName, (k,v)->v==null?scParam.modeParams.get("car").marginalUtilityOfDistance_m:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofMoneyName, (k,v)->v==null?scParam.marginalUtilityOfMoney:v);
		newParams.compute(CNLSUEModel.DistanceBasedMoneyCostCarName, (k,v)->v==null?scParam.modeParams.get("car").monetaryDistanceCostRate:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofTravelptName, (k,v)->v==null?scParam.modeParams.get("pt").marginalUtilityOfTraveling_s*3600:v);
		newParams.compute(CNLSUEModel.MarginalUtilityOfDistancePtName, (k,v)->v==null?scParam.modeParams.get("pt").marginalUtilityOfDistance_m:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofWaitingName, (k,v)->v==null?scParam.marginalUtilityOfWaitingPt_s*3600:v);
		newParams.compute(CNLSUEModel.UtilityOfLineSwitchName, (k,v)->v==null?scParam.utilityOfLineSwitch:v);
		newParams.compute(CNLSUEModel.MarginalUtilityOfWalkingName, (k,v)->v==null?scParam.modeParams.get("walk").marginalUtilityOfTraveling_s*3600:v);
		newParams.compute(CNLSUEModel.DistanceBasedMoneyCostWalkName, (k,v)->v==null?scParam.modeParams.get("walk").monetaryDistanceCostRate:v);
		newParams.compute(CNLSUEModel.ModeConstantCarName, (k,v)->v==null?scParam.modeParams.get("car").constant:v);
		newParams.compute(CNLSUEModel.ModeConstantPtname, (k,v)->v==null?scParam.modeParams.get("pt").constant:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofPerformName, (k,v)->v==null?scParam.marginalUtilityOfPerforming_s*3600:v);
		
		newParams.compute(CNLSUEModel.CapacityMultiplierName, (k,v)->v==null?config.qsim().getFlowCapFactor():v);
		
		return newParams;
	}
	
	public void setDefaultParameters(LinkedHashMap<String,Double> params) {
		for(String s:params.keySet()) {
			this.Params.put(s, params.get(s));
		}
	}
	
	
	protected void loadAnalyticalModelInternalPamamsLimit() {
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.LinkMiuName, new Tuple<Double,Double>(0.0075,0.25));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.ModeMiuName, new Tuple<Double,Double>(0.01,0.5));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.BPRalphaName, new Tuple<Double,Double>(0.10,4.));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.BPRbetaName, new Tuple<Double,Double>(1.,15.));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.TransferalphaName, new Tuple<Double,Double>(0.25,5.));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.TransferbetaName, new Tuple<Double,Double>(0.75,4.));
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
	
	
	
	
	
	@Override
	public void generateRoutesAndOD(Population population,Network odNetwork,TransitSchedule transitSchedule,
			Scenario scenario,Map<String,FareCalculator> fareCalculator) {
		this.setLastPopulation(population);
		this.scenario = scenario;
		//System.out.println("");
		this.setOdPairs(new CNLODpairs(scenario.getNetwork(),population,transitSchedule,scenario,this.timeBeans));
		//Network odNetwork=NetworkUtils.readNetwork("data/tpusbNetwork.xml");
		//Network odNetwork = null;
		this.getOdPairs().generateODpairset(odNetwork);
		this.getOdPairs().generateRouteandLinkIncidence(0.);
		SignalFlowReductionGenerator sg=new SignalFlowReductionGenerator(scenario);
		for(String s:this.timeBeans.keySet()) {
			this.networks.put(s, new CNLNetwork(scenario.getNetwork(),sg));
			this.performTransitVehicleOverlay(this.getNetworks().get(s),
					transitSchedule,scenario.getTransitVehicles(),s);
			this.transitLinks.put(s,this.getOdPairs().getTransitLinks(s));
			System.out.println("No of active signal link = "+sg.activeGc);
			sg.activeGc=0;
		}
		this.fareCalculator=fareCalculator;
		this.ts = transitSchedule;
	
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.getConsecutiveSUEErrorIncrease().put(timeBeanId, 0.);
			this.getDemand().put(timeBeanId, new HashMap<>(this.getOdPairs().getdemand(timeBeanId)));
			for(Id<AnalyticalModelODpair> odId:this.getDemand().get(timeBeanId).keySet()) {
				double totalDemand=this.getDemand().get(timeBeanId).get(odId);
				if(this.odPairs.getODpairset().get(odId).getTrRoutes()!=null && this.odPairs.getODpairset().get(odId).getRoutes()!=null) {
					this.getCarDemand().get(timeBeanId).put(odId, 0.5*totalDemand);
				}else if(this.odPairs.getODpairset().get(odId).getTrRoutes()==null) {
					this.getCarDemand().get(timeBeanId).put(odId, 1.*totalDemand);
				}else if(this.odPairs.getODpairset().get(odId).getRoutes()==null) {
					this.getCarDemand().get(timeBeanId).put(odId, 0.*totalDemand);
				}
				AnalyticalModelODpair odpair=this.getOdPairs().getODpairset().get(odId);
				if(odpair.getSubPopulation()!=null && odpair.getSubPopulation().contains("GV")) {
					this.getCarDemand().get(timeBeanId).put(odId, totalDemand);
				}
			}
			
			logger.info("Startig from 0.5 auto and transit ratio");
			if(this.getDemand().get(timeBeanId).size()!=this.carDemand.get(timeBeanId).size()) {
				logger.error("carDemand and total demand do not have same no of OD pair. This should not happen. Please check");
			}
		}
		
		int agentTrip=0;
		int matsimTrip=0;
		int agentDemand=0;
		for(AnalyticalModelODpair odPair:this.getOdPairs().getODpairset().values()) {
			agentTrip+=odPair.getAgentCounter();
			for(String s:odPair.getTimeBean().keySet()) {
				agentDemand+=odPair.getDemand().get(s);
			}
			
		}
		logger.info("Demand total = "+agentDemand);
		logger.info("Total Agent Trips = "+agentTrip);
	
	}
	
	/**
	 * This method has three part.
	 * 
	 * The parameter inputed must be in ParamName-Value format.
	 * The paramter name should include only the parameters that are present int the default Param
	 * 
	 * 1. Modal Split.
	 * 2. SUE assignment.
	 * 3. SUE Transit Assignment. 
	 */
//	@Override
//	public SUEModelOutput perFormSUE(LinkedHashMap<String, Double> params) {
//		if(!(this.Params.keySet()).containsAll(params.keySet())) {
//			logger.error("The parameters key do not match with the default parameter keys. Invalid Parameter!! Did you send the wrong parameter format?");
//			throw new IllegalArgumentException("The parameters key do not match with the default parameter keys. Invalid Parameter!! Did you send the wrong parameter format?");
//		}
//		return this.perFormSUE(params, this.AnalyticalModelInternalParams);
//	}
	
	@Override
	public Measurements perFormSUE(LinkedHashMap<String, Double> params,Measurements originalMeasurements) {
		if(!(this.Params.keySet()).containsAll(params.keySet())) {
			logger.error("The parameters key do not match with the default parameter keys. Invalid Parameter!! Did you send the wrong parameter format?");
			//throw new IllegalArgumentException("The parameters key do not match with the default parameter keys. Invalid Parameter!! Did you send the wrong parameter format?");
		}
		return this.perFormSUE(params, this.AnalyticalModelInternalParams,originalMeasurements);
	}
	
	/**
	 * This is the same method and does the same task as perform SUE, but takes the internal Parameters as an input too.
	 * This will be used for the internal parameters calibration internally
	 * @param params
	 * @return
	 */
//	@Override
//	public SUEModelOutput perFormSUE(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams) {
//		this.resetCarDemand();
//		
//		LinkedHashMap<String,Double> inputParams=new LinkedHashMap<>(params);
//		LinkedHashMap<String,Double> inputAnaParams=new LinkedHashMap<>(anaParams);
//		//Loading missing parameters from the default values		
//		Map<String,Map<Id<Link>,Double>> outputLinkFlow=new HashMap<>();
//		
//		Map<String,Map<Id<TransitLink>,Double>> outputTrLinkFlow=new HashMap<>();
//		
//		
//		
//		//Checking and updating for the parameters 
//		for(Entry<String,Double> e:this.Params.entrySet()) {
//			if(!params.containsKey(e.getKey())) {
//				params.put(e.getKey(), e.getValue());
//			}
//		}
//		
//		//Checking and updating for the analytical model parameters
//		for(Entry<String,Double> e:this.AnalyticalModelInternalParams.entrySet()) {
//			if(!anaParams.containsKey(e.getKey())) {
//				anaParams.put(e.getKey(), e.getValue());
//			}
//		}
//		
//		//Creating different threads for different time beans
//		Thread[] threads=new Thread[this.timeBeans.size()];
//		int i=0;
//		for(String timeBeanId:this.timeBeans.keySet()) {
//			threads[i]=new Thread(new SUERunnable(this,timeBeanId,params,anaParams),timeBeanId);
//			i++;
//			outputLinkFlow.put(timeBeanId, new HashMap<Id<Link>, Double>());
//			outputLinkTT.put(timeBeanId, new HashMap<Id<Link>, Double>());
//			outputTrLinkFlow.put(timeBeanId, new HashMap<Id<TransitLink>, Double>());
//			outputTrLinkTT.put(timeBeanId, new HashMap<Id<TransitLink>, Double>());
//		}
//		//Starting the Threads
//		for(i=0;i<this.timeBeans.size();i++) {
//			threads[i].start();
//		}
//		
//		//joining the threads
//		for(i=0;i<this.timeBeans.size();i++) {
//			try {
//				threads[i].join();
//			} catch (InterruptedException e1) {
//				e1.printStackTrace();
//			}
//		}
//		
//		//Collecting the Link Flows
//		for(String timeBeanId:this.timeBeans.keySet()) {
//			for(Id<Link> linkId:this.getNetworks().get(timeBeanId).getLinks().keySet()) {
//				outputLinkFlow.get(timeBeanId).put(linkId, 
//						((AnalyticalModelLink) this.getNetworks().get(timeBeanId).getLinks().get(linkId)).getLinkAADTVolume());
//			}
//		}
//		
//		//Collecting the Link Transit 
//		for(String timeBeanId:this.timeBeans.keySet()) {
//			for(Id<TransitLink> linkId:this.transitLinks.get(timeBeanId).keySet()) {
//				outputTrLinkFlow.get(timeBeanId).put(linkId, 
//						(this.transitLinks.get(timeBeanId).get(linkId).getPassangerCount()));
//			}
//		}
//		
//		//collect pt occupancy
//		Map<String, Map<Id<Link>, Double>> averagePtOccupancyOnLink=new HashMap<>();
//		for(String timeBeanId:this.timeBeans.keySet()) {
//			averagePtOccupancyOnLink.put(timeBeanId, new HashMap<>());
//			for(Id<Link>linkId:this.totalPtCapacityOnLink.get(timeBeanId).keySet()) {
//				double occupancy=((CNLLink)this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkTransitPassenger()/this.totalPtCapacityOnLink.get(timeBeanId).get(linkId);
//				averagePtOccupancyOnLink.get(timeBeanId).put(linkId, occupancy);
//			}
//		}
//		
//		SUEModelOutput out=new SUEModelOutput(outputLinkFlow, outputTrLinkFlow, this.outputLinkTT, this.outputTrLinkTT);
//		out.setAveragePtOccupancyOnLink(averagePtOccupancyOnLink);
//		//new OdInfoWriter("toyScenario/ODInfo/odInfo",this.timeBeans).writeOdInfo(this.getOdPairs(), getDemand(), getCarDemand(), inputParams, inputAnaParams);
//		return out;
//	}
	
	@Override
	public Measurements perFormSUE(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,Measurements originalMeasurements) {
		this.resetCarDemand();
		if(originalMeasurements==null) {
			this.emptyMeasurements=true;
			this.measurementsToUpdate=Measurements.createMeasurements(this.timeBeans);
		}else {
			this.measurementsToUpdate=originalMeasurements.clone();
			this.measurementsToUpdate.resetMeasurements();
		}


		//Checking and updating for the parameters 
		for(Entry<String,Double> e:this.Params.entrySet()) {
			if(!params.containsKey(e.getKey())) {
				params.put(e.getKey(), e.getValue());
			}
		}

		//Checking and updating for the analytical model parameters
		for(Entry<String,Double> e:this.AnalyticalModelInternalParams.entrySet()) {
			if(!anaParams.containsKey(e.getKey())) {
				anaParams.put(e.getKey(), e.getValue());
			}
		}

		//Creating different threads for different time beans
		Thread[] threads=new Thread[this.timeBeans.size()];
		int i=0;
		for(String timeBeanId:this.timeBeans.keySet()) {
			threads[i]=new Thread(new SUERunnable(this,timeBeanId,params,anaParams),timeBeanId);
			i++;
		}
		//Starting the Threads
		for(i=0;i<this.timeBeans.size();i++) {
			threads[i].start();
		}

		//joining the threads
		for(i=0;i<this.timeBeans.size();i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}

		//Collecting the Link Flows
	
		if(this.emptyMeasurements==true) {
			for(String timeBeanId:this.timeBeans.keySet()) {
				double count=0;
				for(Link link:this.networks.get(timeBeanId).getLinks().values()) {
					if(!link.getAllowedModes().contains("train") && !link.getId().toString().contains("stop")) {
					count=((AnalyticalModelLink) link).getLinkAADTVolume();
					Id<Measurement> mId=Id.create(link.getId().toString(), Measurement.class);
					Measurement m=null;
					if((m=this.measurementsToUpdate.getMeasurements().get(mId))==null) {
						this.measurementsToUpdate.createAnadAddMeasurement(mId.toString(), MeasurementType.linkVolume);
						m=this.measurementsToUpdate.getMeasurements().get(mId);
						ArrayList<Id<Link>> linkList=new ArrayList<>();
						linkList.add(link.getId());
						m.setAttribute(Measurement.linkListAttributeName, linkList);
					}
					m.putVolume(timeBeanId, count);
					}
				}

			}

		}else {
			for(Measurement m:this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.linkVolume)) {
				for(String timeBeanId:m.getVolumes().keySet()) {
					double count=0;
					for(Id<Link> linkId:(ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)) {
						count+=((AnalyticalModelLink) this.getNetworks().get(timeBeanId).getLinks().get(linkId)).getLinkAADTVolume();
					}
					m.putVolume(timeBeanId, count);
				}
			}
		}
		
		//Collect the train link count
		if(!this.emptyMeasurements) {
			//Create the transit passenger count link
			Map<String, Map<Id<Link>,Map<String,Double>>> timeBinTrPassengerCount = new HashMap<>();
			
			for(String timeBeanId:this.timeBeans.keySet()) {
				Map<Id<Link>,Map<String,Double>> trPassengerCount = new HashMap<>();
				this.networks.get(timeBeanId).getLinks().entrySet().forEach(l->{
						if(l.getValue().getAllowedModes().contains("train")) {
							CNLLink ll = (CNLLink)l.getValue();
							trPassengerCount.put(l.getKey(),ll.getTransitPassengerVolumes());
						}
					});
				timeBinTrPassengerCount.put(timeBeanId, trPassengerCount); 
				
			}
			
			for(Measurement m: this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.TransitPhysicalLinkVolume)) {
				for(MTRLinkVolumeInfo s:(List<MTRLinkVolumeInfo>)m.getAttribute(Measurement.MTRLineRouteStopLinkInfosName)) {
					m.getVolumes().entrySet().forEach(v->{
						if(timeBinTrPassengerCount.get(v.getKey()).get(s.linkId).get(CNLTransitDirectLink.calcLineRouteId(s.lineId.toString(), s.routeId.toString()))!=null) {
							v.setValue(v.getValue()+timeBinTrPassengerCount.get(v.getKey()).get(s.linkId).get(CNLTransitDirectLink.calcLineRouteId(s.lineId.toString(), s.routeId.toString())));
						}
					});
				}
			}
		}else {
			throw new IllegalArgumentException("Not implemented");
		}


		//For now shut down for null Measurements
		//collect pt occupancy
		for(Measurement m:this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.averagePTOccumpancy)) {
			for(String timeBeanId:m.getVolumes().keySet()) {
				Id<Link>linkId=((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0);
				double occupancy=((CNLLink)this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkTransitPassenger()/this.totalPtCapacityOnLink.get(timeBeanId).get(linkId);
				m.putVolume(timeBeanId, occupancy);
			}
		}

		//collect smartCard Entry
		if(this.emptyMeasurements==false) {
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
		}else {
			for(String timeBeanId:this.transitLinks.keySet()) {
				for(TransitLink trl:this.transitLinks.get(timeBeanId).values()) {
					if(trl instanceof TransitDirectLink) {
						TransitDirectLink trdl=(TransitDirectLink)trl;
						String key= trdl.getLineId()+"___"+trdl.getRouteId()+"___"+trdl.getStartStopId();
						Id<TransitLine> lineId=Id.create(trdl.getLineId(),TransitLine.class);
						Id<TransitRoute>routeId=Id.create(trdl.getRouteId(),TransitRoute.class);
						String mode=this.ts.getTransitLines().get(lineId).getRoutes().get(routeId).getTransportMode();
						Id<Measurement>mId=Id.create(key, Measurement.class);
						Measurement m=null;
						if((m=this.measurementsToUpdate.getMeasurements().get(mId))==null) {
							this.measurementsToUpdate.createAnadAddMeasurement(key, MeasurementType.smartCardEntry);
							m=this.measurementsToUpdate.getMeasurements().get(mId);
							m.setAttribute(Measurement.transitLineAttributeName, trdl.getLineId());
							m.setAttribute(Measurement.transitRouteAttributeName, trdl.getRouteId());
							m.setAttribute(Measurement.transitBoardingStopAtrributeName, trdl.getStartStopId());
							m.setAttribute(Measurement.transitModeAttributeName, mode);
						}
						Double oldVolume=null;
						if((oldVolume=m.getVolumes().get(timeBeanId))==null) {
							m.putVolume(timeBeanId, trl.getPassangerCount());
						}else {
							m.putVolume(timeBeanId, oldVolume+trl.getPassangerCount());
						}
					}
				}
			}
		}

		//Collect smart card entry and exit through farelink
		if(this.emptyMeasurements==false) {
			for(AnalyticalModelODpair odpair:this.odPairs.getODpairset().values()) {
				for(String timeBeanId:this.timeBeans.keySet()) {
					if(odpair.getTrRoutes(timeBeanId)!=null && this.Demand.get(timeBeanId).get(odpair.getODpairId())!=0) {
						for(AnalyticalModelTransitRoute tr:odpair.getTrRoutes(timeBeanId)) {
							for(FareLink fl:((CNLTransitRoute)tr).getFareLinks()) {
								Id<Measurement> mId=Id.create(fl.toString(), Measurement.class);
								Measurement m=this.measurementsToUpdate.getMeasurements().get(mId);
								if(m!=null && m.getVolumes().containsKey(timeBeanId)) {
									m.putVolume(timeBeanId, m.getVolumes().get(timeBeanId)+odpair.getTrRouteFlow().get(timeBeanId).get(tr.getTrRouteId()));
								}

							}
						}
					}
				}
			}
		}else {

			for(AnalyticalModelODpair odpair:this.odPairs.getODpairset().values()) {
				for(String timeBeanId:this.timeBeans.keySet()) {
					if(odpair.getTrRoutes(timeBeanId)!=null && this.Demand.get(timeBeanId).get(odpair.getODpairId())!=0) {
						for(AnalyticalModelTransitRoute tr:odpair.getTrRoutes(timeBeanId)) {
							for(FareLink fl:((CNLTransitRoute)tr).getFareLinks()) {
								Id<Measurement> mId=Id.create(fl.toString(), Measurement.class);
								Measurement m=null;
								if((m=this.measurementsToUpdate.getMeasurements().get(mId))==null) {
									this.measurementsToUpdate.createAnadAddMeasurement(mId.toString(), MeasurementType.smartCardEntryAndExit);
									m=this.measurementsToUpdate.getMeasurements().get(mId);
									m.setAttribute(Measurement.FareLinkAttributeName, fl);
								}
								if(m.getVolumes().containsKey(timeBeanId)) {
									m.putVolume(timeBeanId, m.getVolumes().get(timeBeanId)+odpair.getTrRouteFlow().get(timeBeanId).get(tr.getTrRouteId()));
								}else {
									m.putVolume(timeBeanId, odpair.getTrRouteFlow().get(timeBeanId).get(tr.getTrRouteId()));
								}

							}
						}
					}
				}
			}
		}




		//new OdInfoWriter("toyScenario/ODInfo/odInfo",this.timeBeans).writeOdInfo(this.getOdPairs(), getDemand(), getCarDemand(), inputParams, inputAnaParams);
		return this.measurementsToUpdate;
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
			for(Id<AnalyticalModelODpair> o:this.getDemand().get(timeId).keySet()) {
				this.getCarDemand().get(timeId).put(o, this.getDemand().get(timeId).get(o)*0.5);
			}

		}
	}
	
	public static String getODtoODMultiplierId(String odId,String timeId) {
		String oTPU=odId.split("_")[0].substring(0,3);
		String dTPU=odId.split("_")[1].substring(0,3);
		
		return oTPU+"_"+dTPU+"_"+timeId+"_"+"ODMultiplier";
	}
	/**
	 * This method does single OD network loading of only car demand.
	 * 
	 * @param ODpairId
	 * @param anaParams 
	 * @return
	 */
	
	protected HashMap<Id<Link>,Double> NetworkLoadingCarSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,double counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		
		AnalyticalModelODpair odpair=this.getOdPairs().getODpairset().get(ODpairId);
		List<AnalyticalModelRoute> routes=odpair.getRoutes();
		HashMap<Id<AnalyticalModelRoute>,Double> routeFlows=new HashMap<>();
		HashMap<Id<Link>,Double> linkFlows=new HashMap<>();
		
		this.handleBasicParams(params, odpair.getSubPopulation(), this.scenario.getConfig());
		//double totalUtility=0;
		
		//Calculating route utility for all car routes inside one OD pair.
		
		//HashMap<Id<AnalyticalModelRoute>,Double> oldUtility=new HashMap<>();
		HashMap<Id<AnalyticalModelRoute>,Double> utility=new HashMap<>();
		for(AnalyticalModelRoute r:routes){
			double u=0;
			
			if(counter>1) {
				u=r.calcRouteUtility(params, anaParams,this.getNetworks().get(timeBeanId),this.timeBeans.get(timeBeanId));
				u=u+Math.log(odpair.getAutoPathSize().get(r.getRouteId()));//adding the path size term
				utility.put(r.getRouteId(), u);
				//oldUtility.put(r.getRouteId(),this.getOdPairs().getODpairset().get(ODpairId).getRouteUtility(timeBeanId).get(r.getRouteId()));
			}else {
				u=0;
				utility.put(r.getRouteId(), u);
			}
			//oldUtility.put(r.getRouteId(),this.odPairs.getODpairset().get(ODpairId).getRouteUtility(timeBeanId).get(r.getRouteId()));
			odpair.updateRouteUtility(r.getRouteId(), u,timeBeanId);
			
			//This Check is to make sure the exp(utility) do not go to infinity.
			if(u>300||u<-300) {
				logger.error("utility is either too small or too large. Increase or decrease the link miu accordingly. The utility is "+u+" for route "+r.getRouteId());
				//throw new IllegalArgumentException("stop!!!");
			}
			//totalUtility+=Math.exp(u);
		}
//		if(routes.size()>1 && counter>2 ) {
//			//&& (error.get(timeBeanId).get((int)(counter-2))>error.get(timeBeanId).get((int)(counter-3)))
//			System.out.println("Testing!!!");
//			for(CNLRoute r:routes) {
//				double diff=(newUtility.get(r.getRouteId())-oldUtility.get(r.getRouteId()));
//				if(Math.pow(diff,2)>0.00002){
//					
//					System.out.println(diff);
//				}
//			}
//		}
		//If total utility is zero, then there should not be any route. For testing purpose, can be removed later 
//		if(totalUtility==0) {
//			logger.error("utility is zero. Please check.");
//			throw new IllegalArgumentException("Stop!!!!");
//		}
		
		
		//This is the route flow split
		for(AnalyticalModelRoute r:routes){
			double u=utility.get(r.getRouteId());
			double demand=this.getCarDemand().get(timeBeanId).get(ODpairId);
			String id=null;
//			if(this.odMultiplierId==null) {
//				id=CNLSUEModel.getODtoODMultiplierId(odpair.getODpairId().toString(),timeBeanId);
//			}else {
//				id=this.odMultiplierId.get(odpair.getODpairId()).get(timeBeanId);
//			}
//			if(params.containsKey(id)) {
//				demand=demand*params.get(id);
//			}
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
		for(Id<Link> linkId:getOdPairs().getODpairset().get(ODpairId).getLinkIncidence().keySet()){
			double linkflow=0;
			for(AnalyticalModelRoute r:getOdPairs().getODpairset().get(ODpairId).getLinkIncidence().get(linkId)){
				linkflow+=routeFlows.get(r.getRouteId());
			}
			linkFlows.put(linkId,linkflow);
		}
//		if(this.consecutiveSUEErrorIncrease.get(timeBeanId)>=3) {
//			throw new IllegalArgumentException("Errors are worsenning...!!!");
//		}
		return linkFlows;
	}
	
	
	/**
	 * This method does transit sue assignment on the transit network on (Total demand-Car Demand)
	 * @param ODpairId
	 * @param timeBeanId
	 * @param anaParams 
	 * @return
	 */
	protected HashMap<Id<TransitLink>,Double> NetworkLoadingTransitSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,int counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		
		AnalyticalModelODpair odpair=this.getOdPairs().getODpairset().get(ODpairId);
		List<AnalyticalModelTransitRoute> routes=odpair.getTrRoutes(timeBeanId);
		
		HashMap<Id<AnalyticalModelTransitRoute>,Double> routeFlows=new HashMap<>();
		HashMap<Id<TransitLink>,Double> linkFlows=new HashMap<>();
		
		HashMap<Id<AnalyticalModelTransitRoute>,Double> utility=new HashMap<>();
		
		this.handleBasicParams(params, odpair.getSubPopulation(), this.scenario.getConfig());
		
		if(routes!=null && routes.size()!=0) {
		for(AnalyticalModelTransitRoute r:routes){
			double u=0;
			if(counter>1) {
				u=r.calcRouteUtility(params, anaParams,
					this.getNetworks().get(timeBeanId),this.transitLinks.get(timeBeanId),this.fareCalculator,null,this.timeBeans.get(timeBeanId));
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
//		if(totalUtility==0) {
//			logger.warn("STopp!!!! Total utility in the OD pair is zero. This can happen if there is no transit route in that OD pair.");
//		}
		for(AnalyticalModelTransitRoute r:routes){
			double totalDemand=this.getDemand().get(timeBeanId).get(ODpairId);
			double carDemand=this.getCarDemand().get(timeBeanId).get(ODpairId);
			double q=(totalDemand-carDemand);
//			String id=null;
//			if(this.odMultiplierId==null) {
//				id=CNLSUEModel.getODtoODMultiplierId(odpair.getODpairId().toString(),timeBeanId);
//			}else {
//				id=this.odMultiplierId.get(odpair.getODpairId()).get(timeBeanId);
//			}
//			if(params.containsKey(id)) {
//				double d=params.get(id);
//				q=q*d;
//			}
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
	
	
	public static boolean routeContain(List<AnalyticalModelTransitRoute> routesFromOd,AnalyticalModelTransitRoute route) {
		
		for(AnalyticalModelTransitRoute r:routesFromOd) {
			if(r.getTrRouteId().equals(route.getTrRouteId())) {
				route=r;
				return true;
			}
		}
		return false;
	}
	/**
	 * This method should do the network loading for car
	 * @param anaParams 
	 * @return
	 */
	protected Map<Id<Link>,Double> performCarNetworkLoading(String timeBeanId, double counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		Map<Id<Link>,Double> linkVolume=new HashMap<>();
		boolean multiThreading =true;
		if(multiThreading==true) {
			List<Map<Id<Link>, Double>> linkVolumes=Collections.synchronizedList(new ArrayList<>());
			
			this.odPairs.getODpairset().values().parallelStream().forEach(odpair->{
			//for(AnalyticalModelODpair odpair: this.odPairs.getODpairset().values()) {
				if(odpair.getRoutes()!=null && this.carDemand.get(timeBeanId).get(odpair.getODpairId())!=0) {
					linkVolumes.add(this.NetworkLoadingCarSingleOD(odpair.getODpairId(),timeBeanId,counter,params,anaParams));
				}
			});
			//}
//			List<List<AnalyticalModelODpair>>odpairLists= Lists.partition(new ArrayList<>(this.odPairs.getODpairset().values()),Runtime.getRuntime().availableProcessors()-2);
//			Thread[] threads=new Thread[odpairLists.size()];
//			CarNetworkLoadingRunnable[] carnls=new CarNetworkLoadingRunnable[odpairLists.size()];
//			int i=0;
//			for(List<AnalyticalModelODpair> odpairList:odpairLists) {
//				carnls[i]=new CarNetworkLoadingRunnable(timeBeanId,(int)counter,params,anaParams,odpairList);
//				threads[i]=new Thread(carnls[i]);
//				threads[i].start();
//				i++;
//			}
//
//			for(i=0;i<threads.length;i++) {
//				try {
//					threads[i].join();
//				} catch (InterruptedException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
//			}
			for(Map<Id<Link>,Double>lv:linkVolumes) {
				for(Entry<Id<Link>, Double> d:lv.entrySet()) {
					if(linkVolume.containsKey(d.getKey())) {
						linkVolume.put(d.getKey(), linkVolume.get(d.getKey())+d.getValue());
					}else {
						linkVolume.put(d.getKey(), d.getValue());
					}
				}
			}
//			for(i=0;i<carnls.length;i++) {
//				for(Entry<Id<Link>,Double>link:carnls[i].getLinkVolume().entrySet()) {
//					if(linkVolume.containsKey(link.getKey())){
//						linkVolume.put(link.getKey(), linkVolume.get(link.getKey())+link.getValue());
//					}else{
//						linkVolume.put(link.getKey(), link.getValue());
//					}
//				}
//			}
		}else {
			for(AnalyticalModelODpair e:this.getOdPairs().getODpairset().values()){
				//this.getOdPairs().getODpairset().values().parallelStream().forEach((e)->{
				if(e.getRoutes()!=null && this.getCarDemand().get(timeBeanId).get(e.getODpairId())!=0) {
					HashMap <Id<Link>,Double> ODvolume=this.NetworkLoadingCarSingleOD(e.getODpairId(),timeBeanId,counter,params,anaParams);
					for(Id<Link>linkId:ODvolume.keySet()){
						if(linkVolume.containsKey(linkId)){
							linkVolume.put(linkId, linkVolume.get(linkId)+ODvolume.get(linkId));
						}else{
							linkVolume.put(linkId, ODvolume.get(linkId));
						}
					}
				}

				//});
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
	protected Map<Id<TransitLink>,Double> performTransitNetworkLoading(String timeBeanId,int counter, LinkedHashMap<String, Double> params, LinkedHashMap<String, Double> anaParams){
		Map<Id<TransitLink>,Double> linkVolume=new ConcurrentHashMap<>();
		boolean multiThreading =true;
		if(multiThreading==true) {
			
			List<Map<Id<TransitLink>, Double>> linkTransitVolumes=Collections.synchronizedList(new ArrayList<>());
			
			this.odPairs.getODpairset().values().parallelStream().forEach(odpair->{
				double totalDemand=this.getDemand().get(timeBeanId).get(odpair.getODpairId());
				double carDemand=this.getCarDemand().get(timeBeanId).get(odpair.getODpairId());
				if((totalDemand-carDemand)!=0) {
					linkTransitVolumes.add(this.NetworkLoadingTransitSingleOD(odpair.getODpairId(),timeBeanId,counter,params,anaParams));
				}
			});	
			
			for(Map<Id<TransitLink>, Double> lv:linkTransitVolumes) {
				for(Entry<Id<TransitLink>, Double> d:lv.entrySet()) {
					if(linkVolume.containsKey(d.getKey())) {
						linkVolume.put(d.getKey(), linkVolume.get(d.getKey())+d.getValue());
					}else {
						linkVolume.put(d.getKey(), d.getValue());
					}
				}
			}
//			List<List<AnalyticalModelODpair>>odpairLists= Lists.partition(new ArrayList<>(this.odPairs.getODpairset().values()),Runtime.getRuntime().availableProcessors()-2);
//			Thread[] threads=new Thread[odpairLists.size()];
//			TransitNetworkLoadingRunnable[] transitnls=new TransitNetworkLoadingRunnable[odpairLists.size()];
//			int i=0;
//			for(List<AnalyticalModelODpair> odpairList:odpairLists) {
//				transitnls[i]=new TransitNetworkLoadingRunnable(timeBeanId,(int)counter,params,anaParams,odpairList);
//				threads[i]=new Thread(transitnls[i]);
//				threads[i].start();
//				i++;
//			}
//
//			for(i=0;i<threads.length;i++) {
//				try {
//					threads[i].join();
//				} catch (InterruptedException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
//			}
//
//			for(i=0;i<transitnls.length;i++) {
//				for(Entry<Id<TransitLink>,Double>link:transitnls[i].getLinkVolume().entrySet()) {
//					if(linkVolume.containsKey(link.getKey())){
//						linkVolume.put(link.getKey(), linkVolume.get(link.getKey())+link.getValue());
//					}else{
//						linkVolume.put(link.getKey(), link.getValue());
//					}
//				}
//			}
		}else {

			for(AnalyticalModelODpair e:this.getOdPairs().getODpairset().values()){
				//this.odPairs.getODpairset().values().parallelStream().forEach((e)->{
				double totalDemand=this.getDemand().get(timeBeanId).get(e.getODpairId());
				double carDemand=this.getCarDemand().get(timeBeanId).get(e.getODpairId());
				if((totalDemand-carDemand)!=0) {
					HashMap <Id<TransitLink>,Double> ODvolume=this.NetworkLoadingTransitSingleOD(e.getODpairId(),timeBeanId,counter,params,anaParams);
					for(Id<TransitLink> linkId:ODvolume.keySet()){
						if(linkVolume.containsKey(linkId)){
							linkVolume.put(linkId, linkVolume.get(linkId)+ODvolume.get(linkId));
						}else{
							linkVolume.put(linkId, ODvolume.get(linkId));
						}
					}
				}
			}
		}
		//});
		//System.out.println(linkVolume.size());
		return linkVolume;
	}
	private class CarNetworkLoadingRunnable implements Runnable{
		private final String timeBeanId;
		private List<AnalyticalModelODpair> odpairs;
		private Map<Id<Link>,Double> linkVolume=new HashMap<>();
		private final LinkedHashMap<String,Double> params;
		private final LinkedHashMap<String,Double> anaParams;
		private final int counter;
		
		public CarNetworkLoadingRunnable(String timeBeanId,int counter, LinkedHashMap<String, Double> params, LinkedHashMap<String, Double> anaParams,List<AnalyticalModelODpair> odpairs) {
			this.counter=counter;
			this.params=params;
			this.anaParams=anaParams;
			this.timeBeanId=timeBeanId;
			this.odpairs=odpairs;
		}
		
		@Override
		public void run() {
			for(AnalyticalModelODpair e:this.odpairs){
				//this.getOdPairs().getODpairset().values().parallelStream().forEach((e)->{
				if(e.getRoutes()!=null && CNLSUEModel.this.getCarDemand().get(timeBeanId).get(e.getODpairId())!=0) {
					HashMap <Id<Link>,Double> ODvolume=CNLSUEModel.this.NetworkLoadingCarSingleOD(e.getODpairId(),timeBeanId,counter,params,anaParams);
					for(Id<Link>linkId:ODvolume.keySet()){
						if(linkVolume.containsKey(linkId)){
							linkVolume.put(linkId, linkVolume.get(linkId)+ODvolume.get(linkId));
						}else{
							linkVolume.put(linkId, ODvolume.get(linkId));
						}
					}
				}

				//});
			}
		}

		public Map<Id<Link>, Double> getLinkVolume() {
			return linkVolume;
		}
		
	}
	
	
	private class TransitNetworkLoadingRunnable implements Runnable{
		private final String timeBeanId;
		private List<AnalyticalModelODpair> odpairs;
		private Map<Id<TransitLink>,Double> linkVolume=new HashMap<>();
		private final LinkedHashMap<String,Double> params;
		private final LinkedHashMap<String,Double> anaParams;
		private final int counter;
		
		public TransitNetworkLoadingRunnable(String timeBeanId,int counter, LinkedHashMap<String, Double> params, LinkedHashMap<String, Double> anaParams,List<AnalyticalModelODpair> odpairs) {
			this.counter=counter;
			this.params=params;
			this.anaParams=anaParams;
			this.timeBeanId=timeBeanId;
			this.odpairs=odpairs;
		}
		
		@Override
		public void run() {
			for(AnalyticalModelODpair e:this.odpairs){
				//this.odPairs.getODpairset().values().parallelStream().forEach((e)->{
					double totalDemand=CNLSUEModel.this.getDemand().get(timeBeanId).get(e.getODpairId());
					double carDemand=CNLSUEModel.this.getCarDemand().get(timeBeanId).get(e.getODpairId());
					if((totalDemand-carDemand)!=0) {
						Map <Id<TransitLink>,Double> ODvolume=CNLSUEModel.this.NetworkLoadingTransitSingleOD(e.getODpairId(),timeBeanId,counter,params,anaParams);
						for(Id<TransitLink> linkId:ODvolume.keySet()){
							if(linkVolume.containsKey(linkId)){
								linkVolume.put(linkId, linkVolume.get(linkId)+ODvolume.get(linkId));
							}else{
								linkVolume.put(linkId, ODvolume.get(linkId));
							}
						}
					}
				}
		}

		public Map<Id<TransitLink>, Double> getLinkVolume() {
			return linkVolume;
		}
		
	}
	/**
	 * This method updates the linkCarVolume and linkTransitVolume obtained using MSA 
	 * @param linkVolume - Calculated link volume
	 * @param transitlinkVolume - Calculated transit volume
	 * @param counter - current counter in MSA loop
	 * @param timeBeanId - the specific time Bean Id for which the SUE is performed
	 */

	@SuppressWarnings("unchecked")
	protected boolean UpdateLinkVolume(Map<Id<Link>,Double> linkVolume,Map<Id<TransitLink>,Double> transitlinkVolume,int counter,String timeBeanId){
		double squareSum=0;
		double flowSum=0;
		double linkSum=0;
		if(counter==1) {
			this.beta.get(timeBeanId).clear();
			//this.error.clear();
			this.beta.get(timeBeanId).add(1.);
		}else {
			if(error.get(timeBeanId).get(counter-1)<error.get(timeBeanId).get(counter-2)) {
				beta.get(timeBeanId).add(beta.get(timeBeanId).get(counter-2)+this.gammaMSA);
			}else {
				this.getConsecutiveSUEErrorIncrease().put(timeBeanId, this.getConsecutiveSUEErrorIncrease().get(timeBeanId)+1);
				beta.get(timeBeanId).add(beta.get(timeBeanId).get(counter-2)+this.alphaMSA);
				
			}
		}
		
		for(Id<Link> linkId:linkVolume.keySet()){
			double newVolume=linkVolume.get(linkId);
			double oldVolume=((AnalyticalModelLink) this.getNetworks().get(timeBeanId).getLinks().get(linkId)).getLinkCarVolume();
			flowSum+=oldVolume;
			double update;
			double counterPart=1/beta.get(timeBeanId).get(counter-1);
			//counterPart=1./counter;
			update=counterPart*(newVolume-oldVolume);
			if(oldVolume!=0) {
				if(Math.abs(update)/oldVolume*100>this.tolleranceLink) {
					linkSum+=1;
				}
			}
			squareSum+=update*update;
			((AnalyticalModelLink) this.getNetworks().get(timeBeanId).getLinks().get(linkId)).addLinkCarVolume(update);
		}
		for(Id<TransitLink> trlinkId:transitlinkVolume.keySet()){
			//System.out.println("testing");
			double newVolume=transitlinkVolume.get(trlinkId);
			TransitLink trl=this.getTransitLinks().get(timeBeanId).get(trlinkId);
			double oldVolume=trl.getPassangerCount();
			double update;
			double counterPart=1/beta.get(timeBeanId).get(counter-1);
			
			update=counterPart*(newVolume-oldVolume);
			if(oldVolume!=0) {
				if(Math.abs(update)/oldVolume*100>this.tolleranceLink) {
					linkSum+=1;
				}
				
			}
			squareSum+=update*update;
			this.getTransitLinks().get(timeBeanId).get(trlinkId).addPassanger(update,this.getNetworks().get(timeBeanId));
		
		}
		squareSum=Math.sqrt(squareSum);
		if(counter==1) {
			this.error1.get(timeBeanId).clear();
		}
		error1.get(timeBeanId).add(squareSum);
		
		if(squareSum<this.tollerance) {
			return true;
			
		}else {
			return false;
		}
	}
	
	/**
	 * This method will check for the convergence and also create the error term required for MSA
	 * @param linkVolume
	 * @param tollerance
	 * @return
	 */
	protected boolean CheckConvergence(Map<Id<Link>,Double> linkVolume,Map<Id<TransitLink>,Double> transitlinkVolume, double tollerance,String timeBeanId,int counter){
		double linkBelow1=0;
		double squareSum=0;
		double sum=0;
		double error=0;
		for(Id<Link> linkid:linkVolume.keySet()){
			if(linkVolume.get(linkid)==0) {
				error=0;
			}else {
				double currentVolume=((AnalyticalModelLink) this.getNetworks().get(timeBeanId).getLinks().get(linkid)).getLinkCarVolume();
				double newVolume=linkVolume.get(linkid);
				error=Math.pow((currentVolume-newVolume),2);
				if(error==Double.POSITIVE_INFINITY||error==Double.NEGATIVE_INFINITY) {
					throw new IllegalArgumentException("Error is infinity!!!");
				}
				if(error/newVolume*100>tollerance) {					
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
		for(Id<TransitLink> transitlinkid:transitlinkVolume.keySet()){
			if(transitlinkVolume.get(transitlinkid)==0) {
				error=0;
			}else {
				double currentVolume=this.getTransitLinks().get(timeBeanId).get(transitlinkid).getPassangerCount();
				double newVolume=transitlinkVolume.get(transitlinkid);
				error=Math.pow((currentVolume-newVolume),2);
				if(error/newVolume*100>tollerance) {

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
		if(squareSum==Double.NaN) {
			System.out.println("WAIT!!!!Problem!!!!!");
		}
		squareSum=Math.sqrt(squareSum);
		if(counter==1) {
			this.error.get(timeBeanId).clear();
		}
		this.error.get(timeBeanId).add(squareSum);
		logger.info("ERROR amount for "+timeBeanId+" = "+squareSum);
		//System.out.println("in timeBean Id "+timeBeanId+" No of link not converged = "+sum);
		
//		try {
//			//CNLSUEModel.writeData(timeBeanId+","+counter+","+squareSum+","+sum, this.fileLoc+"ErrorData"+timeBeanId+".csv");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		if (squareSum<=1||sum==0||linkBelow1==linkVolume.size()+transitlinkVolume.size()){
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
	protected void performModalSplit(LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams,String timeBeanId) {
		double modeMiu=anaParams.get(CNLSUEModel.ModeMiuName);
		for(AnalyticalModelODpair odPair:this.getOdPairs().getODpairset().values()){
			//For GV car proportion is always 1
			if(odPair.getSubPopulation()!=null && odPair.getSubPopulation().contains("GV")) {
				double carDemand=this.getDemand().get(timeBeanId).get(odPair.getODpairId());
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),carDemand);
				continue;
			// if a phantom trip, car and pt proportion is decided from the simulation and will not be changed
			}else if(odPair.getSubPopulation()!=null && odPair.getSubPopulation().contains("trip")) {
				double carDemand=this.getDemand().get(timeBeanId).get(odPair.getODpairId())*odPair.getCarModalSplit();
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),carDemand);
				continue;
			}
			double demand=this.getDemand().get(timeBeanId).get(odPair.getODpairId());
			if(demand!=0) { 
			double carUtility=odPair.getExpectedMaximumCarUtility(params, anaParams, timeBeanId);
			double transitUtility=odPair.getExpectedMaximumTransitUtility(params, anaParams, timeBeanId);
			
			if(carUtility==Double.NEGATIVE_INFINITY||transitUtility==Double.POSITIVE_INFINITY||
					Math.exp(transitUtility*modeMiu)==Double.POSITIVE_INFINITY) {
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), 0.0);
				
			}else if(transitUtility==Double.NEGATIVE_INFINITY||carUtility==Double.POSITIVE_INFINITY
					||Math.exp(carUtility*modeMiu)==Double.POSITIVE_INFINITY) {
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), this.getDemand().get(timeBeanId).get(odPair.getODpairId()));
			}else if(carUtility==Double.NEGATIVE_INFINITY && transitUtility==Double.NEGATIVE_INFINITY){
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), 0.);
			}else {
				double carProportion=Math.exp(carUtility*modeMiu)/(Math.exp(carUtility*modeMiu)+Math.exp(transitUtility*modeMiu));
				//System.out.println("Car Proportion = "+carProportion);
				Double cardemand=Math.exp(carUtility*modeMiu)/(Math.exp(carUtility*modeMiu)+Math.exp(transitUtility*modeMiu))*this.getDemand().get(timeBeanId).get(odPair.getODpairId());
				if(cardemand==Double.NaN||cardemand==Double.POSITIVE_INFINITY||cardemand==Double.NEGATIVE_INFINITY) {
					logger.error("Car Demand is invalid");
					throw new IllegalArgumentException("car demand is invalid");
				}
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),cardemand);
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
	
	
	/**
	 * This method performs a Traffic Assignment of a single time Bean
	 * @param params: calibration Parameters
	 * @param anaParams: Analytical model Parameters
	 * @param timeBeanId
	 */
	public void singleTimeBeanTA(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,String timeBeanId) {
		Map<Id<TransitLink>, Double> linkTransitVolume;
		Map<Id<Link>,Double> linkCarVolume;
		boolean shouldStop=false;
		
		for(int i=1;i<500;i++) {
			//for(this.car)
			//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
			linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params,anaParams);
			linkTransitVolume=this.performTransitNetworkLoading(timeBeanId,i,params,anaParams);
			shouldStop=this.CheckConvergence(linkCarVolume, linkTransitVolume, this.tollerance, timeBeanId,i);
			this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
			if(i==1 && shouldStop==true) {
				boolean demandEmpty=true;
				for(AnalyticalModelODpair od:this.getOdPairs().getODpairset().values()) {
					if(od.getDemand().get(timeBeanId)!=0) {
						demandEmpty=false;
						break;
					}
				}
				if(!demandEmpty) {
					System.out.println("The model cannot converge on first iteration!!!");
				}
			}
			if(shouldStop) {
				//collect travel time
				if(this.measurementsToUpdate!=null) {
					List<Measurement>ms= this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.linkTravelTime);
					for(Measurement m:ms) {
						if(m.getVolumes().containsKey(timeBeanId)) {
							m.putVolume(timeBeanId, ((CNLLink)this.networks.get(timeBeanId).getLinks().get(((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0))).getLinkTravelTime(this.timeBeans.get(timeBeanId),
							params, anaParams));
						}
					}
				}
//				//collect travel time for transit
//				for(TransitLink link:this.transitLinks.get(timeBeanId).values()) {
//					if(link instanceof TransitDirectLink) {
//						this.outputTrLinkTT.get(timeBeanId).put(link.getTrLinkId(), 
//								((TransitDirectLink)link).getLinkTravelTime(this.networks.get(timeBeanId),this.timeBeans.get(timeBeanId),
//										params, anaParams));
//					}else {
//						this.outputTrLinkTT.get(timeBeanId).put(link.getTrLinkId(), 
//								((TransitTransferLink)link).getWaitingTime(anaParams,this.networks.get(timeBeanId)));
//					}
//					
//				}
				
				break;
				}
			this.performModalSplit(params, anaParams, timeBeanId);
			
		}
		
		
	}
	
	@Deprecated
	public void singleTimeBeanTAModeOut(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,String timeBeanId) {
		HashMap<Integer,HashMap<Id<TransitLink>, Double>> linkTransitVolumeIteration;
		HashMap<Integer,HashMap<Id<Link>,Double>> linkCarVolumeIteration;
		
		
		Map<Id<TransitLink>, Double> linkTransitVolume;
		Map<Id<Link>,Double> linkCarVolume;
		
		boolean shouldStop=false;
		for(int j=0;j<1;j++) {
			for(int i=1;i<500;i++) {
				//for(this.car)
				//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
				linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params,anaParams);
				linkTransitVolume=this.performTransitNetworkLoading(timeBeanId,i,params,anaParams);
				shouldStop=this.CheckConvergence(linkCarVolume, linkTransitVolume, this.tolleranceLink, timeBeanId,i);
				this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
				if(shouldStop) {break;}
				//this.performModalSplit(params, anaParams, timeBeanId);

			}
			this.performModalSplit(params, anaParams, timeBeanId);
		}
		
	}
	@Deprecated
	public void singleTimeBeanTAOut(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,String timeBeanId) {
		Map<Id<TransitLink>, Double> linkTransitVolume=new HashMap<>();
		Map<Id<Link>,Double> linkCarVolume=new HashMap<>();
		boolean shouldStop=false;
		for(int j=0;j<1;j++) {
			
			for(int i=1;i<5000;i++) {
				//for(this.car)
				//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
				linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params,anaParams);
				this.CheckConvergence(linkCarVolume, linkTransitVolume, this.getTollerance(), timeBeanId,i);
				shouldStop=this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
				
				if(shouldStop) {
					break;
					}
				//this.performModalSplit(params, anaParams, timeBeanId);

			}
			for(int i=1;i<1;i++) {
				linkTransitVolume=this.performTransitNetworkLoading(timeBeanId,i,params,anaParams);
				shouldStop=this.CheckConvergence(linkCarVolume, linkTransitVolume, this.getTollerance(), timeBeanId,i);
				this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
				if(shouldStop) {break;}
			}
			this.performModalSplit(params, anaParams, timeBeanId);
		}
		
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

	public void setLastPopulation(Population lastPopulation) {
		this.lastPopulation = lastPopulation;
	}

	public AnalyticalModelODpairs getOdPairs() {
		return odPairs;
	}

	public void setOdPairs(AnalyticalModelODpairs odPairs) {
		this.odPairs = (CNLODpairs) odPairs;
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

	public void setTs(TransitSchedule ts) {
		this.ts = ts;
	}

	public Map<String,Double> getConsecutiveSUEErrorIncrease() {
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

	
}

/**
 * For multi-threaded SUE assignment in different time-bean
 * @author Ashraf
 *
 */
class SUERunnable implements Runnable{

	private CNLSUEModel Model;
	private LinkedHashMap<String,Double> params;
	private LinkedHashMap<String, Double> internalParams;
	private final String timeBeanId;
	public SUERunnable(CNLSUEModel CNLMod,String timeBeanId,LinkedHashMap<String,Double> params,LinkedHashMap<String,Double>IntParams) {
		this.Model=CNLMod;
		this.timeBeanId=timeBeanId;
		this.params=params;
		this.internalParams=IntParams;
	}
	
	/**
	 * this method will do the single time bean assignment 
	 */
	
	@Override
	public void run() {
		this.Model.singleTimeBeanTA(params, internalParams, timeBeanId);
		//this.Model.singleTimeBeanTAModeOut(params, internalParams, timeBeanId);
	}
	
}

