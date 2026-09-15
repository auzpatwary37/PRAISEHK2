package analyticalModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

import core.MapToArray;
import core.ODUtils;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitTransferLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLODpairs;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitTransferLink;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SignalFlowReductionGenerator;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.transit.fare.FareCalculator;
import ust.hk.praisehk.metamodelcalibration.transit.fare.FareLink;

public class ODDifferentiableSUEModel {
private static final Logger logger = Logger.getLogger(ODDifferentiableSUEModel.class);

private Map<String,Double> consecutiveSUEErrorIncrease=new ConcurrentHashMap<>();
private LinkedHashMap<String,Double> AnalyticalModelInternalParams=new LinkedHashMap<>();
private LinkedHashMap<String,Double> Params=new LinkedHashMap<>();
private LinkedHashMap<String,Tuple<Double,Double>> AnalyticalModelParamsLimit=new LinkedHashMap<>();
private boolean intiializeGradient = true;
private SUEModelOutput flow = null;

private double alphaMSA=1.9;//parameter for decreasing MSA step size
private double gammaMSA=.1;//parameter for decreasing MSA step size

//other Parameters for the Calibration Process
private double tolerance= 1;
private double toleranceLink=1;
//user input
public boolean calcvehicleSpecificRouteFlow=false;

private Map<String, Tuple<Double,Double>> timeBeans;

//MATSim Input
private Map<String, AnalyticalModelNetwork> networks=new ConcurrentHashMap<>();
private TransitSchedule ts;
private Config config;
private Scenario scenario;
private Population population;
protected Map<String,FareCalculator> fareCalculator=new HashMap<>();

//Used Containers
private Map<String,ArrayList<Double>> beta=new ConcurrentHashMap<>(); //This is related to weighted MSA of the SUE
private Map<String,ArrayList<Double>> error=new ConcurrentHashMap<>();
private Map<String,ArrayList<Double>> error1=new ConcurrentHashMap<>();//This is related to weighted MSA of the SUE

//TimebeanId vs demands map
private Map<String,Map<Id<AnalyticalModelODpair>,Double>> Demand=new HashMap<>();//Holds ODpair based demand
private Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> carDemand=new ConcurrentHashMap<>(); 
private CNLODpairs odPairs;
private Map<String,Map<Id<TransitLink>,TransitLink>> transitLinks=new ConcurrentHashMap<>();
	
private Population lastPopulation;

private Map<String,Map<String,Double>> suPopSpecificParam = new ConcurrentHashMap<>();
//Internal database for the utility, mode choice and utility

private Map<String,Map<Id<AnalyticalModelRoute>,Double>> routeUtilities = new HashMap<>();
private Map<String,Map<Id<AnalyticalModelTransitRoute>,Double>> trRouteUtilities = new HashMap<>();
private Map<String,Map<Id<AnalyticalModelODpair>,Double>> expectedMaximumCarUtility = new HashMap<>();
private Map<String,Map<Id<AnalyticalModelODpair>,Double>> expectedMaximumTrUtility = new HashMap<>();

private Map<String,Map<Id<AnalyticalModelRoute>,Double>> routeProb = new HashMap<>();
private Map<String,Map<Id<AnalyticalModelTransitRoute>,Double>> trRouteProb = new HashMap<>();

private Map<String,Map<Id<AnalyticalModelRoute>,Double>> routeFlow = new HashMap<>();
private Map<String,Map<Id<AnalyticalModelTransitRoute>,Double>> trRouteFlow = new HashMap<>();

private Map<String,Map<Id<AnalyticalModelODpair>,Double>> carProbability = new HashMap<>();
private double linkGradL1NormThreshold = 1000;
//The gradient containers
//time->id->varKey->grad
private Map<Id<AnalyticalModelODpair>,List<Id<AnalyticalModelRoute>>> routeODIncidence = new HashMap<>();
private Map<String,Map<Id<AnalyticalModelODpair>,List<Id<AnalyticalModelTransitRoute>>>> trRouteODIncidence = new HashMap<>();
private Map<Id<Link>, List<Id<AnalyticalModelRoute>>> linkIncidenceMatrix = new HashMap<>();
private Map<String,Map<Id<TransitLink>, List<Id<AnalyticalModelTransitRoute>>>> trLinkIncidenceMatrix = new HashMap<>();
private Map<String,Map<String,List<Id<AnalyticalModelTransitRoute>>>> fareLinkincidenceMatrix = new HashMap<>();
private Set<String> gradientKeys;

//private Map<String,Map<Id<Link>,Map<String,Double>>> linkGradient = new HashMap<>();
private Map<String,Map<Id<Link>,double[]>> linkGradient = new HashMap<>();

//private Map<String,Map<Id<Link>,Map<String,Double>>> linkTTGradient = new HashMap<>();
private Map<String,Map<Id<Link>,double[]>> linkTTGradient = new HashMap<>();

//private Map<String,Map<Id<TransitLink>,Map<String,Double>>> trLinkGradient = new HashMap<>();
private Map<String,Map<Id<TransitLink>,double[]>> trLinkGradient = new HashMap<>();


//private Map<String,Map<Id<TransitLink>,Map<String,Double>>> trLinkTTGradient = new HashMap<>();
private Map<String,Map<Id<TransitLink>,double[]>> trLinkTTGradient = new HashMap<>();

//private Map<String,Map<Id<AnalyticalModelRoute>,Map<String,Double>>> routeFlowGradient = new HashMap<>();
//private Map<String,Map<Id<AnalyticalModelTransitRoute>,Map<String,Double>>> trRouteFlowGradient = new HashMap<>();

private Map<String,Map<Id<AnalyticalModelRoute>,double[]>> routeFlowGradient = new HashMap<>();
private Map<String,Map<Id<AnalyticalModelTransitRoute>,double[]>> trRouteFlowGradient = new HashMap<>();

//private Map<String,Map<String,Map<String,Double>>> fareLinkGradient = new HashMap<>();

private Map<String,Map<String,double[]>> fareLinkGradient = new HashMap<>();

private Map<String,Map<Id<Link>,Map<String,double[]>>> trPassengerOnPhysicalLinkGradient = new HashMap<>();

private MapToArray<String> gradientArray;

private Map<String,Map<Id<Link>,Double>> linkVolumeUpdate = new HashMap<>();
private Map<String,Map<Id<TransitLink>,Double>> linkTrVolumeUpdate = new HashMap<>();

private Map<String,Map<Id<AnalyticalModelODpair>,double[]>>odParameterIncidence = new HashMap<>();
private Map<String,Boolean> ifODParameterIncidence = new HashMap<>();
//This are needed for output generation 

private double[] gradMultiplier;
private boolean ifGradMultiply = false;
private double maxAbsGrad = .2;
private double minAbsGrad = 0.01;
private double maxAbsL1Norm = 1e150;
private double minAbsL1Norm = 1e-150;

protected Map<String,Map<Id<Link>,Double>> outputLinkTT=new ConcurrentHashMap<>();
protected Map<String,Map<Id<TransitLink>,Double>> outputTrLinkTT=new ConcurrentHashMap<>();
private Map<String,Map<Id<Link>,Double>> totalPtCapacityOnLink=new HashMap<>();
protected Map<String,Map<String,Double>>MTRCount=new ConcurrentHashMap<>();
//All the parameters name
//They are kept public to make it easily accessible as they are final they can not be modified

private boolean emptyMeasurements;

private Measurements measurementsToUpdate;

private boolean calculateGradient = true;

private Map<String,Map<Id<Link>,Double>>linkUpdate = new HashMap<>();
private Map<String,Map<Id<TransitLink>,Double>>trLinkUpdate = new HashMap<>();

private Map<String, Map<Id<AnalyticalModelTransitRoute>, List<Tuple<Id<AnalyticalModelODpair>, Double>>>> routeOdFactor = new HashMap<>(); //This map stores the transitRouteFlow increase per unit passenger of OD pair
private Map<String, Map<Id<AnalyticalModelODpair>,List<Tuple<Id<Link>, Double>>>> ODLinkSensitivity = new HashMap<>();


public static final String BPRalphaName="BPRalpha";
public static final String BPRbetaName="BPRbeta";
public static final String LinkMiuName="LinkMiu";
public static final String ModeMiuName="ModeMiu";
public static final String TransferalphaName="Transferalpha";
public static final String TransferbetaName="Transferbeta";

private Map<String,Map<Id<AnalyticalModelODpair>,Double>>originalDemand = new HashMap<>();

public boolean isCalculateGradient() {
	return calculateGradient;
}

public void setCalculateGradient(boolean calculateGradient) {
	this.calculateGradient = calculateGradient;
}

private void defaultParameterInitiation(Config config){
	this.AnalyticalModelInternalParams.put(CNLSUEModel.LinkMiuName,0.1);
	this.AnalyticalModelInternalParams.put(CNLSUEModel.ModeMiuName, 0.1);
	this.AnalyticalModelInternalParams.put(CNLSUEModel.BPRalphaName, 0.15);
	this.AnalyticalModelInternalParams.put(CNLSUEModel.BPRbetaName, 4.);
	this.AnalyticalModelInternalParams.put(CNLSUEModel.TransferalphaName, 0.5);
	this.AnalyticalModelInternalParams.put(CNLSUEModel.TransferbetaName, 2.);
	this.loadAnalyticalModelInternalPamamsLimit();
	this.Params.put(CNLSUEModel.CapacityMultiplierName, 1.0);
}

protected void loadAnalyticalModelInternalPamamsLimit() {
	this.AnalyticalModelParamsLimit.put(CNLSUEModel.LinkMiuName, new Tuple<Double,Double>(0.0075,0.25));
	this.AnalyticalModelParamsLimit.put(CNLSUEModel.ModeMiuName, new Tuple<Double,Double>(0.01,0.5));
	this.AnalyticalModelParamsLimit.put(CNLSUEModel.BPRalphaName, new Tuple<Double,Double>(0.10,4.));
	this.AnalyticalModelParamsLimit.put(CNLSUEModel.BPRbetaName, new Tuple<Double,Double>(1.,15.));
	this.AnalyticalModelParamsLimit.put(CNLSUEModel.TransferalphaName, new Tuple<Double,Double>(0.25,5.));
	this.AnalyticalModelParamsLimit.put(CNLSUEModel.TransferbetaName, new Tuple<Double,Double>(0.75,4.));
	
}

public ODDifferentiableSUEModel(Map<String, Tuple<Double, Double>> timeBean,Config config) {
	this.timeBeans=timeBean;
	//this.defaultParameterInitiation(null);
	for(String timeId:this.timeBeans.keySet()) {
		this.transitLinks.put(timeId, new HashMap<Id<TransitLink>, TransitLink>());
		this.Demand.put(timeId, this.originalDemand.get(timeId));
		
		this.carDemand.put(timeId, new HashMap<Id<AnalyticalModelODpair>, Double>());
		this.carProbability.put(timeId, new ConcurrentHashMap<>());
		this.routeUtilities.put(timeId,new ConcurrentHashMap<>());
		this.trRouteUtilities.put(timeId, new ConcurrentHashMap<>());
		this.routeFlow.put(timeId, new ConcurrentHashMap<>());
		this.trRouteFlow.put(timeId, new ConcurrentHashMap<>());
		this.routeProb.put(timeId, new ConcurrentHashMap<>());
		this.trRouteProb.put(timeId, new ConcurrentHashMap<>());
		this.expectedMaximumCarUtility.put(timeId, new ConcurrentHashMap<>());
		this.expectedMaximumTrUtility.put(timeId, new ConcurrentHashMap<>());
		this.error.put(timeId, new ArrayList<>());
		this.beta.put(timeId, new ArrayList<>());
		this.error1.put(timeId, new ArrayList<>());
		
		//For result recording
		outputLinkTT.put(timeId, new HashMap<>());
		outputTrLinkTT.put(timeId, new HashMap<>());
		this.totalPtCapacityOnLink.put(timeId, new HashMap<>());
		this.MTRCount.put(timeId, new ConcurrentHashMap<>());
		this.routeOdFactor.put(timeId,  new ConcurrentHashMap<>());
		this.ODLinkSensitivity.put(timeId, new ConcurrentHashMap<>());
	}
	if(config==null) config = ConfigUtils.createConfig();
	this.defaultParameterInitiation(config);
	this.config = config;
	logger.info("Model created.");
}

/**
 * This method overlays transit vehicles on the road network
 * @param network
 * @param Schedule
 */
public void performTransitVehicleOverlay(AnalyticalModelNetwork network, TransitSchedule schedule,Vehicles vehicles,String timeBeanId) {
	for(TransitLine tl:schedule.getTransitLines().values()) {
		for(TransitRoute tr:tl.getRoutes().values()) {
			ArrayList<Id<Link>> links=new ArrayList<>();
			links.add(tr.getRoute().getStartLinkId());
			links.addAll(tr.getRoute().getLinkIds());
			links.add(tr.getRoute().getEndLinkId());
			for(Departure d:tr.getDepartures().values()) {
				if(d.getDepartureTime()>this.timeBeans.get(timeBeanId).getFirst() && d.getDepartureTime()<=this.timeBeans.get(timeBeanId).getSecond()) {
					for(Id<Link> linkId:links) {
						CNLLink link=((CNLLink)network.getLinks().get(linkId));
						VehicleType vt=vehicles.getVehicles().get(d.getVehicleId()).getType();
						link.addLinkTransitVolume(vt.getPcuEquivalents());
						if(this.calcvehicleSpecificRouteFlow)link.addVehicleSpecificVolume(1, vt.getId(), true);
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
 * This function identifies and counts the special cases of the od pair
 * @param timeBeanId
 * @param ODWithNoModeChoice
 * @param ODWithNoCarRouteChoice
 * @param ODWithNoPTRouteChoice
 * @param ODWithNoChoice
 * @param odpair
 */
private static void identifySpecialCases(String timeBeanId, Map<String,Integer> ODWithNoModeChoice, 
		Map<String,Integer> ODWithNoCarRouteChoice, Map<String,Integer> ODWithNoPTRouteChoice, 
		Map<String,Integer> ODWithNoChoice, AnalyticalModelODpair odpair) {
	//OD pairs with one
	if(odpair.getRoutes()!=null && odpair.getRoutes().size()<2) {
		ODWithNoCarRouteChoice.compute(timeBeanId,(k,v)->v==null?1:v+1);
	}
	//OD pairs with only one TR route (but can have some auto routes)
	if(odpair.getTrRoutes(timeBeanId)!=null && odpair.getTrRoutes(timeBeanId).size()<2) {
		ODWithNoPTRouteChoice.compute(timeBeanId,(k,v)->v==null?1:v+1);
	}
	//OD pairs with no mode choice
	if((odpair.getSubPopulation()!=null && odpair.getSubPopulation().contains("trip"))||
			(odpair.getRoutes()==null && odpair.getTrRoutes(timeBeanId)!=null && odpair.getTrRoutes(timeBeanId).size()!=0 )||
			(odpair.getRoutes()!=null && odpair.getRoutes().size()!=0 && odpair.getTrRoutes(timeBeanId)==null)) {
		ODWithNoModeChoice.compute(timeBeanId, (k,v)->v==null?1:v+1);
	}
	//OD pairs with only one tr route and no auto route; or only one auto route and no tr route
	if((odpair.getRoutes()==null && odpair.getTrRoutes(timeBeanId)!=null && odpair.getTrRoutes(timeBeanId).size()<2)||
			odpair.getTrRoutes(timeBeanId)==null && odpair.getRoutes()!=null &&odpair.getRoutes().size()<2) {
		ODWithNoChoice.compute(timeBeanId, (k,v)->v==null?1:v+1);
	}
}

public void generateRoutesAndOD(Population population,Network network,Network odNetwork, TransitSchedule transitSchedule,
		Scenario scenario,Map<String,FareCalculator> fareCalculator) {
//	for(Node n: odNetwork.getNodes().values()) {
//		n.setCoord(new Coord(n.getCoord().getX() + 800000, n.getCoord().getY() + 800000));
//	}	
	
	this.scenario = scenario;
	this.config = scenario.getConfig();
	this.population = population;
	
	//System.out.println("");
	this.odPairs = new CNLODpairs(network,population,transitSchedule,scenario,this.timeBeans);
//	Config odConfig=ConfigUtils.createConfig();
//	odConfig.network().setInputFile("data/odNetwork.xml");

	//This is for creating ODpairs based on TPUSBs
	this.odPairs.generateODpairsetSubPop(odNetwork);//This network has priority over the constructor network. This allows to use a od pair specific network 
	this.odPairs.generateOdSpecificRouteKeys();
	this.odPairs.generateRouteandLinkIncidence(0.);
	
	Map<String,Integer> ODWithNoModeChoice = new HashMap<>();
	Map<String,Integer> ODWithNoCarRouteChoice = new HashMap<>();
	Map<String,Integer> ODWithNoPTRouteChoice = new HashMap<>();
	Map<String,Integer> ODWithNoChoice = new HashMap<>();
	
	SignalFlowReductionGenerator sg=new SignalFlowReductionGenerator(scenario);
	for(String s:this.timeBeans.keySet()) {
		this.networks.put(s, new CNLNetwork(network,sg));
		this.performTransitVehicleOverlay(this.networks.get(s),
				transitSchedule,scenario.getTransitVehicles(),s);
		this.transitLinks.put(s,this.odPairs.getTransitLinks(s));
	}
	this.fareCalculator = fareCalculator;
	this.ts = transitSchedule;
	//this.population.getPersons().values().forEach(p->p.getPlans().clear());
	for(String timeBeanId:this.timeBeans.keySet()) {
		this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
		this.Demand.put(timeBeanId, new HashMap<>(this.odPairs.getdemand(timeBeanId)));
		for(Id<AnalyticalModelODpair> odId:this.Demand.get(timeBeanId).keySet()) {
			double totalDemand=this.Demand.get(timeBeanId).get(odId);
			AnalyticalModelODpair odpair = this.odPairs.getODpairset().get(odId);
			this.carDemand.get(timeBeanId).put(odId, 0.5*totalDemand);
			this.carProbability.get(timeBeanId).put(odId, 0.5);
			
			identifySpecialCases(timeBeanId, ODWithNoModeChoice, ODWithNoCarRouteChoice, 
					ODWithNoPTRouteChoice, ODWithNoChoice, odpair);

			if(odpair.getSubPopulation()!= null && odpair.getSubPopulation().contains("GV")) {
				this.carDemand.get(timeBeanId).put(odId, totalDemand); 
				this.carProbability.get(timeBeanId).put(odId, 1.0);
				//ODWithNoModeChoice.compute(timeBeanId, (k,v)->v==null?1:v+1);
			}
			//System.out.println();
		}
		
	}
	
	logger.info("Total OD Pairs = "+ this.odPairs.getODpairset().size());
	logger.info("OD with no choice = " + ODWithNoChoice.toString());
	logger.info("OD with no mode choice = " + ODWithNoModeChoice.toString());
	logger.info("OD with no auto route choice = " + ODWithNoCarRouteChoice.toString());
	logger.info("OD with no PT route choice = "+ ODWithNoPTRouteChoice.toString());
	
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
	this.createLinkRouteIncidence();
}

private void createLinkRouteIncidence(){
	this.timeBeans.keySet().forEach(t->{
		this.trLinkIncidenceMatrix.put(t, new HashMap<>());
		this.fareLinkincidenceMatrix.put(t, new HashMap<>());
		this.odPairs.getTransitLinks(t).keySet().forEach(linkId->{
			this.trLinkIncidenceMatrix.get(t).put(linkId, new ArrayList<>());
		});
	});
	this.odPairs.getODpairset().entrySet().forEach(od->{
		od.getValue().getLinkIncidence().entrySet().forEach(linkInd->{
			List<Id<AnalyticalModelRoute>> routeIds = new ArrayList<>();
			linkInd.getValue().forEach(r->{
				routeIds.add(r.getRouteId());
				
			});
			if(this.linkIncidenceMatrix.containsKey(linkInd.getKey())) {
				this.linkIncidenceMatrix.get(linkInd.getKey()).addAll(routeIds);
			}else {
				this.linkIncidenceMatrix.put(linkInd.getKey(), routeIds);
			}
			List<Id<AnalyticalModelRoute>>routes = new ArrayList<>();
			od.getValue().getRoutes().forEach(r->routes.add(r.getRouteId()));
			this.routeODIncidence.put(od.getKey(), routes);
		});
		this.timeBeans.keySet().forEach(t->{
			Map<Id<TransitLink>,List<Id<AnalyticalModelTransitRoute>>> timeRoutes = this.trLinkIncidenceMatrix.get(t);
			Map<String, List<Id<AnalyticalModelTransitRoute>>> fareLinkTimeRoutes = this.fareLinkincidenceMatrix.get(t);
			od.getValue().getTrLinkIncidence().entrySet().forEach(linkInd->{				
				if(timeRoutes.containsKey(linkInd.getKey())) {
					List<Id<AnalyticalModelTransitRoute>> routeIds = new ArrayList<>();
					linkInd.getValue().forEach(r->{
						routeIds.add(r.getTrRouteId());
					});
					timeRoutes.get(linkInd.getKey()).addAll(routeIds);
				}
				
			});
			this.trRouteODIncidence.compute(t, (k,v)->v==null?v=new HashMap<>():v);
			this.trRouteODIncidence.get(t).put(od.getKey(), new ArrayList<>());
			if(od.getValue().getTrRoutes(t)!=null) {
			od.getValue().getTrRoutes(t).forEach(r->{
				r.getFareLinks().forEach(fl->{
					if(!fareLinkTimeRoutes.containsKey(fl.toString())){
						fareLinkTimeRoutes.put(fl.toString(), new ArrayList<>());	
					}
					fareLinkTimeRoutes.get(fl.toString()).add(r.getTrRouteId());
				});
				this.trRouteODIncidence.get(t).get(od.getKey()).add(r.getTrRouteId());
			});
			}
		
		});
	});
	
}
/**
 * This will not deal with any param containing sub population name or All
 * This hampers in time so currently is turned off
 * @param params
 * @param subPopulation
 * @param config
 * @return
 */
private LinkedHashMap<String,Double> handleBasicParams(LinkedHashMap<String,Double> oldparams, String subPopulation, Config config){
	LinkedHashMap<String,Double> params = new LinkedHashMap<>(oldparams);
	// Handle the original params first
//	for(String s:params.keySet()) {
//		if(subPopulation!=null && (s.contains(subPopulation)||s.contains("All"))) {
//			newParams.put(s.split(" ")[1],params.get(s));
//		}else if (subPopulation == null) {
//			newParams.put(s, params.get(s));
//		}else if(subPopulation!=null) {//this will allow the unknown param to enter
//			newParams.put(s, params.get(s));
//		}
//	}
	if(subPopulation == null) subPopulation = "";
	if(!this.suPopSpecificParam.containsKey(subPopulation)) {
		LinkedHashMap<String,Double> newParams = new LinkedHashMap<>();
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
		newParams.compute(CNLSUEModel.StandingUtilityName, (k,v)->v==null?scParam.modeParams.get("standing").marginalUtilityOfTraveling_s*3600:v);
		newParams.compute(CNLSUEModel.MarginalUtilityofTravelMetroName, (k,v)->v==null?scParam.modeParams.get("metro").marginalUtilityOfTraveling_s*3600:v);
		
		newParams.compute(CNLSUEModel.CapacityMultiplierName, (k,v)->v==null?config.qsim().getFlowCapFactor():v);
		this.suPopSpecificParam.put(subPopulation, newParams);
		
	}
	this.suPopSpecificParam.get(subPopulation).entrySet().forEach(pp->{
		params.compute(pp.getKey(), (k,v)->v==null?pp.getValue():v);
	});
	return params;
}

public Measurements perFormSUE(LinkedHashMap<String, Double> params,Measurements originalMeasurements) {
	this.resetCarDemand();
	this.Demand = ODUtils.applyODPairMultiplier(this.Demand, params,this.odPairs.getODpairset());
	for(String timeBeanId:this.timeBeans.keySet()) {
		this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
		//this.Demand.put(timeBeanId, new HashMap<>(this.odPairs.getdemand(timeBeanId)));
		for(Id<AnalyticalModelODpair> odId:this.Demand.get(timeBeanId).keySet()) {
			double totalDemand=this.Demand.get(timeBeanId).get(odId);
			AnalyticalModelODpair odpair = this.odPairs.getODpairset().get(odId);
			
			if(odpair.getRoutes()!=null && odpair.getTrRoutes(timeBeanId)!=null) {
				this.carDemand.get(timeBeanId).put(odId, 0.5*totalDemand);
				this.carProbability.get(timeBeanId).put(odId, 0.5);
			}else if ( (odpair.getSubPopulation()!= null && odpair.getSubPopulation().contains("GV"))||(odpair.getRoutes()!=null && odpair.getTrRoutes(timeBeanId)==null)) {
				this.carDemand.get(timeBeanId).put(odId, 1.0*totalDemand);
				this.carProbability.get(timeBeanId).put(odId, 1.0);
			}else if(odpair.getRoutes()==null && odpair.getTrRoutes(timeBeanId)!=null) {
				this.carDemand.get(timeBeanId).put(odId, 0*totalDemand);
				this.carProbability.get(timeBeanId).put(odId, 0.);
			}
		}
			//System.out.println();
		//}
		
	}
	return this.performAssignment(params, this.AnalyticalModelInternalParams,originalMeasurements);
}

public Measurements perFormSUEWithAddition(LinkedHashMap<String, Double> params,Measurements originalMeasurements, 
		Map<String, Map<Id<AnalyticalModelODpair>, Double>> additionMap, boolean ignoreNegative) {
	this.resetCarDemand();
	this.Demand = ODUtils.applyODPairMultiplierAndAddition(this.Demand, params,this.odPairs.getODpairset(), 
			additionMap, ignoreNegative);
	logger.info("Sum of demand is " + this.Demand.entrySet().stream()
				.flatMap( m -> m.getValue().entrySet().stream()).mapToDouble(m -> m.getValue()).sum());
	for(String timeBeanId:this.timeBeans.keySet()) {
		this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
		
		//Adjust the car demand
		for(Id<AnalyticalModelODpair> odId:this.Demand.get(timeBeanId).keySet()) {
			double totalDemand=this.Demand.get(timeBeanId).get(odId);
			AnalyticalModelODpair odpair = this.odPairs.getODpairset().get(odId);
			if(this.odPairs.getODpairset().get(odId).getRoutes()!=null && this.odPairs.getODpairset().get(odId).getTrRoutes(timeBeanId)!=null) {
				this.carDemand.get(timeBeanId).put(odId, 0.5*totalDemand);
				this.carProbability.get(timeBeanId).put(odId, 0.5);
			}else if (odpair.getSubPopulation().contains("GV")||(this.odPairs.getODpairset().get(odId).getRoutes()!=null && this.odPairs.getODpairset().get(odId).getTrRoutes(timeBeanId)==null)) {
				this.carDemand.get(timeBeanId).put(odId, 1.0*totalDemand);
				this.carProbability.get(timeBeanId).put(odId, 1.0);
			}else if(this.odPairs.getODpairset().get(odId).getRoutes()==null && this.odPairs.getODpairset().get(odId).getTrRoutes(timeBeanId)!=null) {
				this.carDemand.get(timeBeanId).put(odId, 0*totalDemand);
				this.carProbability.get(timeBeanId).put(odId, 0.);
			}
			if(odpair.getSubPopulation()!= null && odpair.getSubPopulation().contains("GV")) {
				this.carDemand.get(timeBeanId).put(odId, totalDemand); 
				this.carProbability.get(timeBeanId).put(odId, 1.0);
			}
		}
	}
	//throw new RuntimeException("Break it!");
	return this.performAssignment(params, this.AnalyticalModelInternalParams,originalMeasurements);
}

public Measurements perFormSUEByDemandMap(LinkedHashMap<String, Double> params,Measurements originalMeasurements, 
		Map<String, Map<Id<AnalyticalModelODpair>, Double>> demandMap) {
	this.resetCarDemand();
	for(String timeBean: this.Demand.keySet()) {
		for(Id<AnalyticalModelODpair> odPair: this.Demand.get(timeBean).keySet()) {
			if(demandMap.get(timeBean).containsKey(odPair)) {
				this.Demand.get(timeBean).put(odPair, demandMap.get(timeBean).get(odPair));
			}else {
				this.Demand.get(timeBean).put(odPair, 0.);
			}
		}
	}
	logger.info("Sum of demand is " + this.Demand.entrySet().stream()
			.flatMap( m -> m.getValue().entrySet().stream()).mapToDouble(m -> m.getValue()).sum());
	for(String timeBeanId:this.timeBeans.keySet()) {
		this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
		
		//Adjust the car demand
		for(Id<AnalyticalModelODpair> odId:this.Demand.get(timeBeanId).keySet()) {
			double totalDemand=this.Demand.get(timeBeanId).get(odId);
			AnalyticalModelODpair odpair = this.odPairs.getODpairset().get(odId);
			if(this.odPairs.getODpairset().get(odId).getRoutes()!=null && this.odPairs.getODpairset().get(odId).getTrRoutes(timeBeanId)!=null) {
				this.carDemand.get(timeBeanId).put(odId, 0.5*totalDemand);
				this.carProbability.get(timeBeanId).put(odId, 0.5);
			}else if (odpair.getSubPopulation().contains("GV")||(this.odPairs.getODpairset().get(odId).getRoutes()!=null && this.odPairs.getODpairset().get(odId).getTrRoutes(timeBeanId)==null)) {
				this.carDemand.get(timeBeanId).put(odId, 1.0*totalDemand);
				this.carProbability.get(timeBeanId).put(odId, 1.0);
			}else if(this.odPairs.getODpairset().get(odId).getRoutes()==null && this.odPairs.getODpairset().get(odId).getTrRoutes(timeBeanId)!=null) {
				this.carDemand.get(timeBeanId).put(odId, 0*totalDemand);
				this.carProbability.get(timeBeanId).put(odId, 0.);
			}
			if(odpair.getSubPopulation().contains("GV")) {
				this.carDemand.get(timeBeanId).put(odId, totalDemand); 
				this.carProbability.get(timeBeanId).put(odId, 1.0);
			}
		}
	}
	
	//throw new RuntimeException("Break it!");
	return this.performAssignment(params, this.AnalyticalModelInternalParams,originalMeasurements);
}

public Measurements perFormSUE(LinkedHashMap<String, Double> params,Measurements originalMeasurements,boolean calcGrad) {
	this.Demand = ODUtils.applyODPairMultiplier(this.Demand, params,this.odPairs.getODpairset());
	for(String timeBeanId:this.timeBeans.keySet()) {
		this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
		//this.Demand.put(timeBeanId, new HashMap<>(this.odPairs.getdemand(timeBeanId)));
		for(Id<AnalyticalModelODpair> odId:this.Demand.get(timeBeanId).keySet()) {
			double totalDemand=this.Demand.get(timeBeanId).get(odId);
			AnalyticalModelODpair odpair = this.odPairs.getODpairset().get(odId);
			this.carDemand.get(timeBeanId).put(odId, 0.5*totalDemand);
			
			if(odpair.getSubPopulation().contains("GV")) {
				this.carDemand.get(timeBeanId).put(odId, totalDemand); 
			}
			//System.out.println();
		}
		
	}
	return this.performAssignment(params, this.AnalyticalModelInternalParams,originalMeasurements,calcGrad);
}

private Measurements performAssignment(LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams, Measurements originalMeasurements) {
	Measurements measurementsToUpdate = null;
	
	SUEModelOutput flow = this.performAssignment(params, anaParams);
	this.flow = flow;
	if(originalMeasurements==null) {//for now we just add the fare link and link volume for a null measurements
		this.emptyMeasurements=true;
		measurementsToUpdate=Measurements.createMeasurements(this.timeBeans);
		//create and insert link volume measurement
		for(Entry<String, Map<Id<Link>, Double>> timeFlow:flow.getLinkVolume().entrySet()) {
			for(Entry<Id<Link>, Double> link:timeFlow.getValue().entrySet()) {
				Id<Measurement> mid = Id.create(link.getKey().toString(), Measurement.class);
				if(measurementsToUpdate.getMeasurements().containsKey(mid)) {
					measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
				}else {
					measurementsToUpdate.createAnadAddMeasurement(mid.toString(), MeasurementType.linkVolume);
					List<Id<Link>> links = new ArrayList<>();
					links.add(link.getKey());
					measurementsToUpdate.getMeasurements().get(mid).setAttribute(Measurement.linkListAttributeName, links);
					measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
				}
			}
		}
		
		for(Entry<String, Map<String, Double>> timeFlow:flow.getFareLinkVolume().entrySet()) {
			for(Entry<String, Double> link:timeFlow.getValue().entrySet()) {
				Id<Measurement> mid = Id.create(link.getKey().toString(), Measurement.class);
				if(measurementsToUpdate.getMeasurements().containsKey(mid)) {
					measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
				}else {
					measurementsToUpdate.createAnadAddMeasurement(mid.toString(), MeasurementType.fareLinkVolume);
					measurementsToUpdate.getMeasurements().get(mid).setAttribute(Measurement.FareLinkAttributeName, new FareLink(link.getKey()));
					measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
				}
			}
		}
	}else {
		measurementsToUpdate=originalMeasurements.clone();
		measurementsToUpdate.resetMeasurements();
		measurementsToUpdate.updateMeasurements(flow, null, null);
	}
	return measurementsToUpdate;
}

private Measurements performAssignment(LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams, Measurements originalMeasurements,boolean calcGrad) {
	Measurements measurementsToUpdate = null;
	
	SUEModelOutput flow = this.performAssignment(params, anaParams,calcGrad);
	this.flow = flow;
	if(originalMeasurements==null) {//for now we just add the fare link and link volume for a null measurements
		this.emptyMeasurements=true;
		measurementsToUpdate=Measurements.createMeasurements(this.timeBeans);
		//create and insert link volume measurement
		for(Entry<String, Map<Id<Link>, Double>> timeFlow:flow.getLinkVolume().entrySet()) {
			for(Entry<Id<Link>, Double> link:timeFlow.getValue().entrySet()) {
				Id<Measurement> mid = Id.create(link.getKey().toString(), Measurement.class);
				if(measurementsToUpdate.getMeasurements().containsKey(mid)) {
					measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
				}else {
					measurementsToUpdate.createAnadAddMeasurement(mid.toString(), MeasurementType.linkVolume);
					List<Id<Link>> links = new ArrayList<>();
					links.add(link.getKey());
					measurementsToUpdate.getMeasurements().get(mid).setAttribute(Measurement.linkListAttributeName, links);
					measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
				}
			}
		}
		
		for(Entry<String, Map<String, Double>> timeFlow:flow.getFareLinkVolume().entrySet()) {
			for(Entry<String, Double> link:timeFlow.getValue().entrySet()) {
				Id<Measurement> mid = Id.create(link.getKey().toString(), Measurement.class);
				if(measurementsToUpdate.getMeasurements().containsKey(mid)) {
					measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
				}else {
					measurementsToUpdate.createAnadAddMeasurement(mid.toString(), MeasurementType.fareLinkVolume);
					measurementsToUpdate.getMeasurements().get(mid).setAttribute(Measurement.FareLinkAttributeName, new FareLink(link.getKey()));
					measurementsToUpdate.getMeasurements().get(mid).putVolume(timeFlow.getKey(), link.getValue());
				}
			}
		}
	}else {
		measurementsToUpdate=originalMeasurements.clone();
		measurementsToUpdate.resetMeasurements();
		measurementsToUpdate.updateMeasurements(flow, null, null);
	}
	return measurementsToUpdate;
}

public SUEModelOutput getFlow() {
	return flow;
}

private SUEModelOutput performAssignment( LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams) {
	SUEModelOutput flow = new SUEModelOutput(new HashMap<>(),new HashMap<>(),new HashMap<>(),new HashMap<>(),new HashMap<>());
	flow.setTrainCount(new HashMap<>());
	flow.setTrainTransfers(new HashMap<>());
	//this.resetCarDemand();
	for(String timeId:this.timeBeans.keySet()) {
		SUEModelOutput flowOut = this.singleTimeBeanTA(params, anaParams, timeId);
		
		flow.getLinkVolume().putAll(flowOut.getLinkVolume());
		flow.getLinkTravelTime().putAll(flowOut.getLinkTravelTime());
		flow.getLinkTransitVolume().putAll(flowOut.getLinkTransitVolume());
		flow.getTrLinkTravelTime().putAll(flowOut.getTrLinkTravelTime());
		flow.getFareLinkVolume().putAll(flowOut.getFareLinkVolume());
		flow.getTrainCount().putAll(flowOut.getTrainCount());
		flow.getTrainTransfers().putAll(flowOut.getTrainTransfers());
	}
	return flow;
}

private SUEModelOutput performAssignment( LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams,boolean calcGradient) {
	SUEModelOutput flow = new SUEModelOutput(new HashMap<>(),new HashMap<>(),new HashMap<>(),new HashMap<>(),new HashMap<>());
	flow.setTrainCount(new HashMap<>());
	flow.setTrainTransfers(new HashMap<>());
	//this.resetCarDemand();
	for(String timeId:this.timeBeans.keySet()) {
		SUEModelOutput flowOut = this.singleTimeBeanTA(params, anaParams, timeId, calcGradient);
		
		flow.getLinkVolume().putAll(flowOut.getLinkVolume());
		flow.getLinkTravelTime().putAll(flowOut.getLinkTravelTime());
		flow.getLinkTransitVolume().putAll(flowOut.getLinkTransitVolume());
		flow.getTrLinkTravelTime().putAll(flowOut.getTrLinkTravelTime());
		flow.getFareLinkVolume().putAll(flowOut.getFareLinkVolume());
		flow.getTrainCount().putAll(flowOut.getTrainCount());
		flow.getTrainTransfers().putAll(flowOut.getTrainTransfers());
	}
	return flow;
}

public Map<String, AnalyticalModelNetwork> getNetworks() {
	return networks;
}

public Map<String, Map<Id<TransitLink>, TransitLink>> getTransitLinks() {
	return transitLinks;
}

/**
 * This is the single time bean traffic assignment for everything
 * @param params
 * @param anaParams
 * @param timeBeanId
 * @param calcGrad
 * @return
 */
public SUEModelOutput singleTimeBeanTA(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,
		String timeBeanId) {
	Map<Id<TransitLink>, Double> linkTransitVolume =null;
	Map<Id<Link>,Double> linkCarVolume = null;
	Map<String,Map<String,Double>> fareLinkVolume = new HashMap<>();
	Map<Id<Link>,Map<String,Double>> trPassengerCount = new HashMap<>();
	Map<String,Double> trainTransfers = new HashMap<>();
	fareLinkVolume.put(timeBeanId, new HashMap<>());
	boolean shouldStop=false;
//	boolean firstTimeGradCalc = true;
	int maxIteration = 70;
	for(int i=1;i<maxIteration;i++) {
		long time1 = System.currentTimeMillis();
		//for(this.car)
		//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
		linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params,anaParams);
		linkTransitVolume=this.performTransitNetworkLoading(timeBeanId,i,params,anaParams);
		logger.info("Finished network loading.");
		//System.out.println("GB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024*1024));
		shouldStop=this.CheckConvergence(linkCarVolume, linkTransitVolume, this.tolerance, timeBeanId,i);
		///the beta should already be calculated by this line. 
//		double updateRatio = 1/this.beta.get(timeBeanId).get(i-1);
//		if(updateRatio>0.20 || i<20) {
//		//	if(firstTimeGradCalc) {
//				this.caclulateGradient(timeBeanId, i, params, anaParams,true);
//				firstTimeGradCalc = false;
//			}else {
		if(this.calculateGradient  == true) {
			long time = System.currentTimeMillis();
			this.caclulateGradient(timeBeanId, i, params, anaParams);
			logger.info("Finished gradient Calculation in "+(System.currentTimeMillis()-time)*0.001+" seconds.");
		}
		//	}
//		}
		this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
		if(i==1 && shouldStop==true) {
			boolean demandEmpty=true;
			for(AnalyticalModelODpair od:this.odPairs.getODpairset().values()) {
				if(od.getDemand().get(timeBeanId)!=0) {
					demandEmpty=false;
					break;
				}
			}
			if(!demandEmpty) {
				logger.warn("The model cannot converge on first iteration!!!");
			}
		}
		
		if(shouldStop || i == maxIteration-1) {
//			//collect travel time
//			if(this.measurementsToUpdate!=null) {
//				List<Measurement>ms= this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.linkTravelTime);
//				for(Measurement m:ms) {
//					if(m.getVolumes().containsKey(timeBeanId)) {
//						m.putVolume(timeBeanId, ((CNLLink)this.networks.get(timeBeanId).getLinks().get(((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0))).getLinkTravelTime(this.timeBeans.get(timeBeanId),
//						params, anaParams));
//					}
//				}
//			}
//			//collect travel time for transit
//			for(TransitLink link:this.transitLinks.get(timeBeanId).values()) {
//				if(link instanceof TransitDirectLink) {
//					this.outputTrLinkTT.get(timeBeanId).put(link.getTrLinkId(), 
//							((TransitDirectLink)link).getLinkTravelTime(this.networks.get(timeBeanId),this.timeBeans.get(timeBeanId),
//									params, anaParams));
//				}else {
//					this.outputTrLinkTT.get(timeBeanId).put(link.getTrLinkId(), 
//							((TransitTransferLink)link).getWaitingTime(anaParams,this.networks.get(timeBeanId)));
//				}
//				
//			}
			this.networks.entrySet().forEach(net->{
				net.getValue().getLinks().entrySet().forEach(l->{
					if(l.getValue().getAllowedModes().contains("train")) {
						CNLLink ll = (CNLLink)l.getValue();
						trPassengerCount.put(l.getKey(),ll.getTransitPassengerVolumes());
					}
				});
			});
			this.odPairs.getODpairset().entrySet().forEach(od->{
				if(this.Demand.get(timeBeanId).get(od.getKey())-this.carDemand.get(timeBeanId).get(od.getKey())!=0 && od.getValue().getTrRoutes()!=null) {
					od.getValue().getTrRoutes().forEach(tr->{
						String fromStop = null;
						String toStop = null;
						String currentMode = null;
						String nextMode = null;
						for(int ii = 0;ii<=tr.getTransitDirectLinks().size();ii++){
							if(ii==tr.getTransitDirectLinks().size()) {
								nextMode = null;
							}else {
								TransitDirectLink trd = tr.getTransitDirectLinks().get(ii);
								Id<TransitLine>lineId = Id.create(trd.getLineId(),TransitLine.class);
								Id<TransitRoute> routeId = Id.create(trd.getRouteId(),TransitRoute.class);
								nextMode = this.ts.getTransitLines().get(lineId).getRoutes().get(routeId).getTransportMode();
							}
							
							
							if((currentMode!=null && currentMode.equals("train"))||(nextMode!=null && nextMode.equals("train"))) {
								if(nextMode!=null && nextMode.equals("train")) {
									toStop = tr.getTransitDirectLinks().get(ii).getStartStopId();
								}else {
									toStop =null;
								}
								if(currentMode!=null && currentMode.equals("train")) {
									fromStop = tr.getTransitDirectLinks().get(ii-1).getEndStopId();
								}else {
									fromStop = null;
								}
//								System.out.println(tr.getTrRouteId());
//								System.out.println(this.Demand.get(timeBeanId).get(od.getKey()));
//								System.out.println();
								Double volume = this.trRouteFlow.get(timeBeanId).get(tr.getTrRouteId());
								
								if(volume!=null)trainTransfers.compute(fromStop+"___"+toStop, (k,v)->v==null?volume:v+volume);
							}
							currentMode = nextMode;
						}
					});
				}
			});
			this.fareLinkincidenceMatrix.get(timeBeanId).entrySet().stream().forEach(fl->{
				double flow = 0;
				for(Id<AnalyticalModelTransitRoute> trRoute:fl.getValue()){
					if(this.trRouteFlow.get(timeBeanId).containsKey(trRoute)) {
						double thisRouteFlow = this.trRouteFlow.get(timeBeanId).get(trRoute);
						if(thisRouteFlow < 0) {
							throw new IllegalArgumentException("The route flow cannot be less than 0!");
						}
					flow += thisRouteFlow;
					}
				}
				fareLinkVolume.get(timeBeanId).put(fl.getKey(),flow);
			});
			if(this.ifGradMultiply) {
				this.scaleBackGradients();
			}
			break;
			
			}
		this.performModalSplit(params, anaParams, timeBeanId);
		
		logger.info("Total Network Loading Time is "+ (System.currentTimeMillis()-time1)*0.001 +" seconds");
		
	}
	Map<String,Map<Id<Link>,Double>>linkVolume = new HashMap<>();
	linkVolume.put(timeBeanId, linkCarVolume);
	
	Map<String,Map<Id<TransitLink>,Double>>linkTrVolume = new HashMap<>();
	linkTrVolume.put(timeBeanId, linkTransitVolume);
	this.networks.get(timeBeanId).getLinks().entrySet().forEach(l->{
		linkVolume.get(timeBeanId).put(l.getKey(), ((CNLLink)l.getValue()).getLinkAADTVolume());
	});
	SUEModelOutput flow = new SUEModelOutput(linkVolume, linkTrVolume, outputLinkTT, outputTrLinkTT, fareLinkVolume);
	flow.setTrainCount(new HashMap<>());
	flow.getTrainCount().put(timeBeanId, trPassengerCount);
	flow.setTrainTransfers(new HashMap<>());
	flow.getTrainTransfers().put(timeBeanId, trainTransfers);
	return flow;
	
}

public Map<String, Tuple<Double, Double>> getTimeBeans() {
	return timeBeans;
}

public Map<String, Map<Id<AnalyticalModelODpair>, Double>> getCarProbability() {
	return carProbability;
}


public SUEModelOutput singleTimeBeanTA(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,
		String timeBeanId, boolean calcGrad) {
	Map<Id<TransitLink>, Double> linkTransitVolume =null;
	Map<Id<Link>,Double> linkCarVolume = null;
	Map<String,Map<String,Double>> fareLinkVolume = new HashMap<>();
	fareLinkVolume.put(timeBeanId, new HashMap<>());
	Map<Id<Link>,Map<String,Double>> trPassengerCount = new HashMap<>();
	Map<String,Double> trainTransfers = new HashMap<>();
	boolean shouldStop=false;
	int maxIteration = 70;
//	boolean firstTimeGradCalc = true;
	for(int i=1; i<maxIteration; i++) {
		long time1 = System.currentTimeMillis();
		//for(this.car)
		//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
		linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params,anaParams);
		linkTransitVolume=this.performTransitNetworkLoading(timeBeanId,i,params,anaParams);
		logger.info("Finished network loading.");
		//System.out.println("GB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024*1024));
		shouldStop=this.CheckConvergence(linkCarVolume, linkTransitVolume, this.tolerance, timeBeanId,i);
		///the beta should already be calculated by this line. 
//		double updateRatio = 1/this.beta.get(timeBeanId).get(i-1);
//		if(updateRatio>0.20 || i<20) {
//		//	if(firstTimeGradCalc) {
//				this.caclulateGradient(timeBeanId, i, params, anaParams,true);
//				firstTimeGradCalc = false;
//			}else {
		if(calcGrad) {
			long time = System.currentTimeMillis();
			this.caclulateGradient(timeBeanId, i, params, anaParams);
			logger.info("Calculated gradient in " + (System.currentTimeMillis()-time) * 0.001+" seconds.");
		}
		//	}
//		}
		logger.info("Finished gradient Calculation");
		
		this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
		if(i==1 && shouldStop==true) {
			boolean demandEmpty=true;
			for(AnalyticalModelODpair od:this.odPairs.getODpairset().values()) {
				if(od.getDemand().get(timeBeanId)!=0) {
					demandEmpty=false;
					break;
				}
			}
			if(!demandEmpty) {
				logger.warn("The model cannot converge on first iteration!!!");
			}
		}
		
		if(shouldStop || i == maxIteration-1) {
//			//collect travel time
//			if(this.measurementsToUpdate!=null) {
//				List<Measurement>ms= this.measurementsToUpdate.getMeasurementsByType().get(MeasurementType.linkTravelTime);
//				for(Measurement m:ms) {
//					if(m.getVolumes().containsKey(timeBeanId)) {
//						m.putVolume(timeBeanId, ((CNLLink)this.networks.get(timeBeanId).getLinks().get(((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0))).getLinkTravelTime(this.timeBeans.get(timeBeanId),
//						params, anaParams));
//					}
//				}
//			}
//			//collect travel time for transit
//			for(TransitLink link:this.transitLinks.get(timeBeanId).values()) {
//				if(link instanceof TransitDirectLink) {
//					this.outputTrLinkTT.get(timeBeanId).put(link.getTrLinkId(), 
//							((TransitDirectLink)link).getLinkTravelTime(this.networks.get(timeBeanId),this.timeBeans.get(timeBeanId),
//									params, anaParams));
//				}else {
//					this.outputTrLinkTT.get(timeBeanId).put(link.getTrLinkId(), 
//							((TransitTransferLink)link).getWaitingTime(anaParams,this.networks.get(timeBeanId)));
//				}
//				
//			}
			
			
			this.networks.entrySet().forEach(net->{
				net.getValue().getLinks().entrySet().forEach(l->{
					if(l.getValue().getAllowedModes().contains("train")) {
						CNLLink ll = (CNLLink)l.getValue();
						trPassengerCount.put(l.getKey(),ll.getTransitPassengerVolumes());
					}
				});
			});
			this.odPairs.getODpairset().entrySet().forEach(od->{
				if(this.carDemand.get(timeBeanId).get(od.getKey())!=1) {
					od.getValue().getTrRoutes().forEach(tr->{
						String fromStop = null;
						String toStop = null;
						for(int ii = 0;ii<=tr.getTransitDirectLinks().size();ii++){
							TransitDirectLink trd = tr.getTransitDirectLinks().get(ii);
							Id<TransitLine>lineId = Id.create(trd.getLineId(),TransitLine.class);
							Id<TransitRoute> routeId = Id.create(trd.getRouteId(),TransitRoute.class);
							if(this.ts.getTransitLines().get(lineId).getRoutes().get(routeId).getTransportMode().equals("train")) {
								if(ii==tr.getTransitDirectLinks().size()) {
									toStop =null;
								}else {
									toStop = trd.getStartStopId();
								}
								if(ii !=0)fromStop = tr.getTransitDirectLinks().get(ii-1).getEndStopId();
								double volume = this.trRouteFlow.get(timeBeanId).get(tr.getTrRouteId());
								trainTransfers.compute(fromStop+"___"+toStop, (k,v)->v==null?volume:v+volume);
							}
						}
					});
				}
			});
			
			this.fareLinkincidenceMatrix.get(timeBeanId).entrySet().stream().forEach(fl->{
				double flow = 0;
				for(Id<AnalyticalModelTransitRoute> trRoute:fl.getValue()){
					flow+=this.trRouteFlow.get(timeBeanId).get(trRoute);
				}
				fareLinkVolume.get(timeBeanId).put(fl.getKey(),flow);
			});
			this.scaleBackGradients();
			break;
			
			}
		this.performModalSplit(params, anaParams, timeBeanId);
		
		logger.info("Performed an assignment loop in "+(System.currentTimeMillis()-time1)*0.001 +" seconds");
	}
	Map<String,Map<Id<Link>,Double>>linkVolume = new HashMap<>();
	linkVolume.put(timeBeanId, linkCarVolume);
	this.networks.entrySet().stream().forEach(e->{
		e.getValue().getLinks().entrySet().forEach(l->{
			linkVolume.get(e.getKey()).put(l.getKey(), ((CNLLink)l.getValue()).getLinkAADTVolume());
		});
	});
	Map<String,Map<Id<TransitLink>,Double>>linkTrVolume = new HashMap<>();
	linkTrVolume.put(timeBeanId, linkTransitVolume);
	SUEModelOutput flow = new SUEModelOutput(linkVolume, linkTrVolume, outputLinkTT, outputTrLinkTT, fareLinkVolume);
	flow.setTrainCount(new HashMap<>());
	flow.getTrainCount().put(timeBeanId, trPassengerCount);
	flow.setTrainTransfers(new HashMap<>());
	flow.getTrainTransfers().put(timeBeanId, trainTransfers);
	return flow;
	
}

protected HashMap<Id<Link>,Double> NetworkLoadingCarSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,
		double counter,LinkedHashMap<String,Double> oparams, LinkedHashMap<String, Double> anaParams){
	
	AnalyticalModelODpair odpair=this.odPairs.getODpairset().get(ODpairId);
	String subPopulation = odpair.getSubPopulation();
	
	
	List<AnalyticalModelRoute> routes=odpair.getRoutes();
	Map<Id<AnalyticalModelRoute>,Double> routeFlows=new HashMap<>();
	HashMap<Id<Link>,Double> linkFlows=new HashMap<>();
	
	LinkedHashMap<String,Double> params = this.handleBasicParams(oparams, subPopulation, this.config);
	
	//double totalUtility=0;
	
	//Calculating route utility for all car routes inside one OD pair.
	
	//HashMap<Id<AnalyticalModelRoute>,Double> oldUtility=new HashMap<>();
	Map<Id<AnalyticalModelRoute>,Double> utility=this.routeUtilities.get(timeBeanId);
	double maxUtil = Double.NEGATIVE_INFINITY;
	double denominator = 0;
	double expectedMaxUtil = 0;
	for(AnalyticalModelRoute r:routes){
		double u=0;
		
		u=r.calcRouteUtility(params, anaParams,this.networks.get(timeBeanId),this.timeBeans.get(timeBeanId));
		u=u+Math.log(odpair.getAutoPathSize().get(r.getRouteId()));//adding the path size term
		if(maxUtil<u)maxUtil = u;
		utility.put(r.getRouteId(), u);
		
		//odpair.updateRouteUtility(r.getRouteId(), u,timeBeanId);// Not sure if this is needed
		
		//This Check is to make sure the exp(utility) do not go to infinity.
//		if(u>300||u<-300) {
//			logger.error("utility is either too small or too large. Increase or decrease the link miu accordingly. The utility is "+u+" for route "+r.getRouteId());
//			//throw new IllegalArgumentException("stop!!!");
//		}
//		if(u==0) {
//			logger.debug("util is zero!!!");
//		}
	}
	
	for(AnalyticalModelRoute r:routes){
		denominator += Math.exp(anaParams.get(CNLSUEModel.LinkMiuName)*(utility.get(r.getRouteId())-maxUtil));
		expectedMaxUtil+=Math.exp((utility.get(r.getRouteId())-maxUtil)*anaParams.get(CNLSUEModel.LinkMiuName));
	}
	double emu = maxUtil+1/anaParams.get(CNLSUEModel.LinkMiuName)*Math.log(expectedMaxUtil);
	this.expectedMaximumCarUtility.get(timeBeanId).put(ODpairId,emu);	
	//This is the route flow split
	
	for(AnalyticalModelRoute r:routes){
		double u=utility.get(r.getRouteId());
		double demand = this.carDemand.get(timeBeanId).get(ODpairId);
		double prob = Math.exp(anaParams.get(CNLSUEModel.LinkMiuName)*(u-maxUtil))/denominator;
		this.routeProb.get(timeBeanId).put(r.getRouteId(), prob);
		double flow=prob*demand*odpair.getAveragePCU();
		//For testing purpose, can be removed later
		if(flow==Double.NaN||flow==Double.POSITIVE_INFINITY) {
			logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
			throw new IllegalArgumentException("Wait!!!!Error!!!!");
		}
		routeFlows.put(r.getRouteId(),flow);
		odpair.getRouteFlow().get(timeBeanId).put(r.getRouteId(), flow);
		this.routeFlow.get(timeBeanId).put(r.getRouteId(), flow);
	}
	for(Id<Link> linkId:this.odPairs.getODpairset().get(ODpairId).getLinkIncidence().keySet()){
		double linkflow=0;
		for(AnalyticalModelRoute r:this.odPairs.getODpairset().get(ODpairId).getLinkIncidence().get(linkId)){
			linkflow+=routeFlows.get(r.getRouteId());
		}
		linkFlows.put(linkId,linkflow);
	}
//	if(this.consecutiveSUEErrorIncrease.get(timeBeanId)>=3) {
//		throw new IllegalArgumentException("Errors are worsenning...!!!");
//	}
	return linkFlows;
}


public Map<String, Map<Id<AnalyticalModelRoute>, Double>> getRouteProb() {
	return routeProb;
}

public Map<String, Map<Id<AnalyticalModelTransitRoute>, Double>> getTrRouteProb() {
	return trRouteProb;
}

/**
 * This function obtains the fare link to OD pairs matrix from the two matrices created before.
 * @param timeBean
 * @return The fareLink OD Matrix
 */
public Map<String, Map<Id<AnalyticalModelODpair>, Double>> getFareLinkODMatrix(String timeBean){
	Map<String, Map<Id<AnalyticalModelODpair>, Double>> fareLinkODMatrix = new HashMap<>();
	
	for(Entry<String, List<Id<AnalyticalModelTransitRoute>>> fLAndLinks: fareLinkincidenceMatrix.get(timeBean).entrySet()) {
		String fareLinkIndex = fLAndLinks.getKey();
		if(!fareLinkODMatrix.containsKey(fareLinkIndex)) {
			fareLinkODMatrix.put(fareLinkIndex, new HashMap<>());
		}
		Map<Id<AnalyticalModelODpair>, Double> ODMatrix = fareLinkODMatrix.get(fareLinkIndex);
		for(Id<AnalyticalModelTransitRoute> trRoute: fLAndLinks.getValue()) {
			if(routeOdFactor.get(timeBean).containsKey(trRoute)) { //There are some route without flow factor because all passengers take car
				for(Tuple<Id<AnalyticalModelODpair>, Double> odSensitivity: routeOdFactor.get(timeBean).get(trRoute)) {
					double addValue = odSensitivity.getSecond();
					if(ODMatrix.containsKey(odSensitivity.getFirst())){
						addValue += ODMatrix.get(odSensitivity.getFirst());
					}
					ODMatrix.put(odSensitivity.getFirst(), addValue);
				}
			}
		}
	}
	
	return fareLinkODMatrix;
}

public Map<Id<AnalyticalModelODpair>, List<Tuple<Id<Link>, Double>>> getODLinkSensitivity(String timeBean){
	return this.ODLinkSensitivity.get(timeBean);
}

public Config getConfig() {
	return config;
}

public void setConfig(Config config) {
	this.config = config;
}

/**
 * This method does transit sue assignment on the transit network on (Total demand-Car Demand)
 * @param ODpairId
 * @param timeBeanId
 * @param anaParams 
 * @return
 */
protected HashMap<Id<TransitLink>,Double> NetworkLoadingTransitSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,int counter,LinkedHashMap<String,Double> oparams, LinkedHashMap<String, Double> anaParams){
	
	AnalyticalModelODpair odpair=this.odPairs.getODpairset().get(ODpairId);
	List<AnalyticalModelTransitRoute> routes=odpair.getTrRoutes(timeBeanId);
//	if(odpair.getODpairId().toString().equals("227014.0_624041.0_person_TCSwithoutCar")) {
//		logger.debug("debug here.");
//	}
	HashMap<Id<AnalyticalModelTransitRoute>,Double> routeFlows=new HashMap<>();
	HashMap<Id<TransitLink>,Double> linkFlows=new HashMap<>();
	String subPopulation = odpair.getSubPopulation();
	LinkedHashMap<String,Double> params = this.handleBasicParams(oparams, subPopulation, this.config);
	Map<Id<AnalyticalModelTransitRoute>,Double> utility=this.trRouteUtilities.get(timeBeanId);
	double expectedMaxUtil = 0;
	if(routes!=null && routes.size()!=0) {
		double maxUtil = Double.NEGATIVE_INFINITY;
		double denominator = 0;
		for(AnalyticalModelTransitRoute r:routes){
			double u=0;

			u=r.calcRouteUtility(params, anaParams,
					this.networks.get(timeBeanId),this.transitLinks.get(timeBeanId),this.fareCalculator,null,this.timeBeans.get(timeBeanId));
			u+=Math.log(odpair.getTrPathSize().get(timeBeanId).get(r.getTrRouteId()));//adding the path size term
			if(maxUtil<u) {
				maxUtil = u;
			}
			
			if(u==Double.NaN||u>0) {
				logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
				throw new IllegalArgumentException("Utility is NAN!!!");
			}

//			if(u>300) {
//				logger.warn("STOP!!!Utility is too large >300");
//				u=r.calcRouteUtility(params, anaParams,
//						this.networks.get(timeBeanId),this.transitLinks.get(timeBeanId),this.fareCalculator,null,this.timeBeans.get(timeBeanId));
//			
//			}
			//odpair.updateTrRouteUtility(r.getTrRouteId(), u,timeBeanId);
			String[] part = r.getTrRouteId().toString().split("_");
			String odId = part[0]+"_"+part[1]+"_"+part[2]+"_"+part[3];
			if(!odpair.getODpairId().toString().equals(odId)) {
				logger.debug("Route do not belong to odpair");
			}
			if(utility.containsKey(r.getTrRouteId())) {
				logger.debug("duplicate route key!!!");
//				String[] part = r.getTrRouteId().toString().split("_");
//				String odId = part[0]+"_"+part[1]+"_"+part[2]+"_"+part[3];
//				if(!odpair.getODpairId().toString().equals(odId))
			}
			utility.put(r.getTrRouteId(), u);
			if(u==0) {
				logger.debug("util is zero!!!");
			}
		}
		for(AnalyticalModelTransitRoute r:routes){
			denominator += Math.exp(anaParams.get(CNLSUEModel.LinkMiuName)*(utility.get(r.getTrRouteId())-maxUtil));
			expectedMaxUtil+=Math.exp((utility.get(r.getTrRouteId())-maxUtil)*anaParams.get(CNLSUEModel.LinkMiuName));
		}
		double emu = maxUtil+1/anaParams.get(CNLSUEModel.LinkMiuName)*Math.log(expectedMaxUtil);
		if(emu>0) {
			//logger.debug("");
		}
		this.expectedMaximumTrUtility.get(timeBeanId).put(ODpairId, emu);
		
		for(AnalyticalModelTransitRoute r:routes){
			double totalDemand=this.Demand.get(timeBeanId).get(ODpairId);
			double carDemand=this.carDemand.get(timeBeanId).get(ODpairId);
			double q=(totalDemand-carDemand);
			double u=utility.get(r.getTrRouteId());
			double prob = Math.exp(anaParams.get(CNLSUEModel.LinkMiuName)*(u-maxUtil))/denominator;
			this.trRouteProb.get(timeBeanId).put(r.getTrRouteId(), prob);
			double flow=q*prob;
			if(Double.isNaN(flow)||flow==Double.POSITIVE_INFINITY||flow==Double.NEGATIVE_INFINITY||u>0) {
				logger.error("The flow is NAN!. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
				double a = utility.get(r.getTrRouteId());
				double uu = r.calcRouteUtility(params, anaParams,
						this.networks.get(timeBeanId),this.transitLinks.get(timeBeanId),this.fareCalculator,null,this.timeBeans.get(timeBeanId));
				u+=Math.log(odpair.getTrPathSize().get(timeBeanId).get(r.getTrRouteId()));//adding the path size term
				
				throw new IllegalArgumentException("Error!!!!");
			}
			routeFlows.put(r.getTrRouteId(),flow);
			odpair.getTrRouteFlow().get(timeBeanId).put(r.getTrRouteId(), flow);
			
			if(flow < 0) {
				throw new IllegalArgumentException("The flow is less than 0!");
			}
			
			this.trRouteFlow.get(timeBeanId).put(r.getTrRouteId(), flow);
			
			// Insert the route OD factors
			if( !this.routeOdFactor.get(timeBeanId).containsKey(r.getTrRouteId()) ) {
				this.routeOdFactor.get(timeBeanId).put(r.getTrRouteId(), new ArrayList<>());
			}
			this.routeOdFactor.get(timeBeanId).get(r.getTrRouteId()).add(new Tuple<>(ODpairId, flow/totalDemand));
			
			// Insert the OD link sensitivity
			if(! this.ODLinkSensitivity.get(timeBeanId).containsKey(ODpairId)) {
				this.ODLinkSensitivity.get(timeBeanId).put(ODpairId, new ArrayList<>());
			}
			for(Id<Link> routePhysicalLink: r.getPhysicalLinks()) {
				ODLinkSensitivity.get(timeBeanId).get(ODpairId).add(new Tuple<>(routePhysicalLink, flow/totalDemand));
			}
		}
	}
	
	Set<Id<TransitLink>>linksets=this.odPairs.getODpairset().get(ODpairId).getTrLinkIncidence().keySet();
	for(Id<TransitLink> linkId:linksets){
		if(this.transitLinks.get(timeBeanId).containsKey(linkId)) {
		double linkflow=0;
		List<AnalyticalModelTransitRoute>incidence=this.odPairs.getODpairset().get(ODpairId).getTrLinkIncidence().get(linkId);
		for(AnalyticalModelTransitRoute r:incidence){
			List<AnalyticalModelTransitRoute> routesFromOd=routes;
			
			if(routeContain(routesFromOd, r)) {
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
protected Map<Id<Link>,Double> performCarNetworkLoading(String timeBeanId, double counter,
		LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
	Map<Id<Link>,Double> linkVolume=new HashMap<>();
	boolean multiThreading =true;
	if(multiThreading==true) {
		List<Map<Id<Link>, Double>> linkVolumes=Collections.synchronizedList(new ArrayList<>());
		
		this.odPairs.getODpairset().values().parallelStream().forEach(odpair->{
			if(odpair.getRoutes()!=null ) {//&& this.carDemand.get(timeBeanId).get(odpair.getODpairId())!=0
				linkVolumes.add(this.NetworkLoadingCarSingleOD(odpair.getODpairId(),timeBeanId,counter,params,anaParams));
			}else if(odpair.getRoutes() != null){
				for(AnalyticalModelRoute r:odpair.getRoutes()){
					this.routeProb.get(timeBeanId).put(r.getRouteId(), 0.);
					odpair.getRouteFlow().get(timeBeanId).put(r.getRouteId(), 0.);
					this.routeFlow.get(timeBeanId).put(r.getRouteId(), 0.);
				}
			}
		});
		
		for(Map<Id<Link>,Double>lv:linkVolumes) {
			for(Entry<Id<Link>, Double> d:lv.entrySet()) {
				if(linkVolume.containsKey(d.getKey())) {
					linkVolume.put(d.getKey(), linkVolume.get(d.getKey())+d.getValue());
				}else {
					linkVolume.put(d.getKey(), d.getValue());
				}
			}
		}

	}else {
		for(AnalyticalModelODpair e:this.odPairs.getODpairset().values()){
			if(e.getRoutes()!=null ) {//&& this.carDemand.get(timeBeanId).get(e.getODpairId())!=0
				HashMap <Id<Link>,Double> ODvolume=this.NetworkLoadingCarSingleOD(e.getODpairId(),timeBeanId,counter,params,anaParams);
				for(Id<Link>linkId:ODvolume.keySet()){
					if(linkVolume.containsKey(linkId)){
						linkVolume.put(linkId, linkVolume.get(linkId)+ODvolume.get(linkId));
					}else{
						linkVolume.put(linkId, ODvolume.get(linkId));
					}
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
protected Map<Id<TransitLink>,Double> performTransitNetworkLoading(String timeBeanId,int counter, LinkedHashMap<String, Double> params, LinkedHashMap<String, Double> anaParams){
	Map<Id<TransitLink>,Double> linkVolume=new ConcurrentHashMap<>();
	boolean multiThreading =true;
	this.routeOdFactor.put(timeBeanId, new ConcurrentHashMap<>()); //Clear the OD factor for this loading
	this.ODLinkSensitivity.put(timeBeanId, new ConcurrentHashMap<>());
	if(multiThreading==true) {
		
		List<Map<Id<TransitLink>, Double>> linkTransitVolumes=Collections.synchronizedList(new ArrayList<>());
		
		this.odPairs.getODpairset().values().parallelStream().forEach(odpair->{
//			if(odpair.getODpairId().toString().equals("227014.0_624041.0_person_TCSwithoutCar")) {
//				logger.debug("debug here.");
//			}
			double totalDemand=this.Demand.get(timeBeanId).get(odpair.getODpairId());
			double carDemand=this.carDemand.get(timeBeanId).get(odpair.getODpairId());
			if(odpair.getTrRoutes(timeBeanId)!=null ) {//&& (totalDemand-carDemand)!=0
				linkTransitVolumes.add(this.NetworkLoadingTransitSingleOD(odpair.getODpairId(),timeBeanId,counter,params,anaParams));
			}else {
				if(odpair.getTrRoutes(timeBeanId) != null) {
					for(AnalyticalModelTransitRoute r: odpair.getTrRoutes(timeBeanId)) {
						this.trRouteFlow.get(timeBeanId).put(r.getTrRouteId(), 0.);
						this.trRouteProb.get(timeBeanId).put(r.getTrRouteId(), 0.);
					}
				}
			}
//			if((totalDemand-carDemand)==0 && odpair.getTrRoutes(timeBeanId)!=null) {
//				logger.debug("dimension mismatch");
//			}
			
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
	}else {

		for(AnalyticalModelODpair od:this.odPairs.getODpairset().values()){
			//this.odPairs.getODpairset().values().parallelStream().forEach((e)->{
			double totalDemand=this.Demand.get(timeBeanId).get(od.getODpairId());
			double carDemand=this.carDemand.get(timeBeanId).get(od.getODpairId());
			if(od.getTrRoutes(timeBeanId)!=null) {//totalDemand-carDemand)!=0
				HashMap <Id<TransitLink>,Double> ODvolume=this.NetworkLoadingTransitSingleOD(od.getODpairId(),timeBeanId,counter,params,anaParams);
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

/**
 * This method updates the linkCarVolume and linkTransitVolume obtained using MSA 
 * @param linkVolume - Calculated link volume
 * @param transitlinkVolume - Calculated transit volume
 * @param counter - current counter in MSA loop
 * @param timeBeanId - the specific time Bean Id for which the SUE is performed
 */

protected void UpdateLinkVolume(Map<Id<Link>,Double> linkVolume,Map<Id<TransitLink>,Double> transitlinkVolume,int counter,String timeBeanId){
	for(Id<Link> linkId:linkVolume.keySet()) {
		double counterPart=1/beta.get(timeBeanId).get(counter-1);
		//counterPart=1./counter;
		double update=counterPart*this.linkVolumeUpdate.get(timeBeanId).get(linkId);
		((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkId)).addLinkCarVolume(update);
		if(((CNLLink)this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkCarVolume()<0) {
			logger.debug("Negative Flow!!!");
		}
	}
	for(Id<TransitLink> trlinkId:transitlinkVolume.keySet()){
		//counterPart=1./counter;
		double counterPart=1/beta.get(timeBeanId).get(counter-1);
		double update=counterPart*this.linkTrVolumeUpdate.get(timeBeanId).get(trlinkId);
		this.transitLinks.get(timeBeanId).get(trlinkId).addPassanger(update,this.networks.get(timeBeanId));
		if(this.transitLinks.get(timeBeanId).get(trlinkId).getPassangerCount()<0) {
			logger.debug("Negative Flow!!!");
		}
	}
}

/**
 * This method will check for the convergence and also create the error term required for MSA
 * @param linkVolume
 * @param tolerance
 * @return
 */
protected boolean CheckConvergence(Map<Id<Link>,Double> linkVolume,Map<Id<TransitLink>,Double> transitlinkVolume, double tolerance,String timeBeanId,int counter){
	double linkAbove1=0;
	double squareSum=0;
	double sum=0;
	double error=0;
	this.linkVolumeUpdate.put(timeBeanId, new HashMap<>());
	this.linkTrVolumeUpdate.put(timeBeanId, new HashMap<>());
	for(Id<Link> linkid:linkVolume.keySet()){
//		if(linkVolume.get(linkid)==0) {
//			error=0;
//		}else {
			double currentVolume=((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkid)).getLinkCarVolume();
			double newVolume=linkVolume.get(linkid);
			this.linkVolumeUpdate.get(timeBeanId).put(linkid, newVolume - currentVolume);
			error=Math.pow((currentVolume-newVolume),2);
			if(error==Double.POSITIVE_INFINITY||error==Double.NEGATIVE_INFINITY) {
				throw new IllegalArgumentException("Error is infinity!!!");
			}
			if(newVolume != 0 && error/(newVolume+.00000001)*100>tolerance) {					
				sum+=1;
			}
			if(error>1) {
				linkAbove1++;
			}
		//}
		
		squareSum+=error;
		if(squareSum==Double.POSITIVE_INFINITY||squareSum==Double.NEGATIVE_INFINITY) {
			throw new IllegalArgumentException("error is infinity!!!");
		}
	}
	for(Id<TransitLink> transitlinkid:transitlinkVolume.keySet()){
//		if(transitlinkVolume.get(transitlinkid)==0) {
//			error=0;
//		}else {
			double currentVolume=this.transitLinks.get(timeBeanId).get(transitlinkid).getPassangerCount();
			double newVolume=transitlinkVolume.get(transitlinkid);
			this.linkTrVolumeUpdate.get(timeBeanId).put(transitlinkid, newVolume-currentVolume);
			error=Math.pow((currentVolume-newVolume),2);
			if(newVolume!=0 && error/(newVolume+.0000001)*100>tolerance) {

				sum+=1;
			}
			if(error>1) {
				linkAbove1++;
			}
		//}
		if(error==Double.NaN||error==Double.NEGATIVE_INFINITY) {
			throw new IllegalArgumentException("Stop!!! There is something wrong!!!");
		}
		squareSum+=error;
	}
	if(squareSum==Double.NaN) {
		System.out.println("WAIT!!!!Problem!!!!!");
	}
	squareSum=Math.sqrt(squareSum);
	if(counter==1) {String id=null;
		this.error.get(timeBeanId).clear();
	}
	this.error.get(timeBeanId).add(squareSum);
	logger.info("ERROR amount for "+timeBeanId+" at SUE iteration "+counter+" = "+squareSum);
	//System.out.println("in timeBean Id "+timeBeanId+" No of link not converged = "+sum);
	
//	try {
//		//CNLSUEModel.writeData(timeBeanId+","+counter+","+squareSum+","+sum, this.fileLoc+"ErrorData"+timeBeanId+".csv");
//	} catch (IOException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
	
	if(counter==1) {
		this.beta.get(timeBeanId).clear();
		//this.error.clear();
		this.beta.get(timeBeanId).add(1.);
	}else {
		if(this.error.get(timeBeanId).get(counter-1)<this.error.get(timeBeanId).get(counter-2)) {
			beta.get(timeBeanId).add(beta.get(timeBeanId).get(counter-2)+this.gammaMSA);
		}else {
			this.consecutiveSUEErrorIncrease.put(timeBeanId, this.consecutiveSUEErrorIncrease.get(timeBeanId)+1);
			beta.get(timeBeanId).add(beta.get(timeBeanId).get(counter-2)+this.alphaMSA);
			
		}
	}
	
	if (squareSum<=1||sum==0||linkAbove1==0){
		return true;
	}else{
		return false;
	}
	
	
	
}

private double getOptimizedAlpha(int counter) {
	double alpha = 1/(1+counter);
	for(int i=0;i<10;i++) {
		
	}
	return alpha;
	
}

/**
 * This method perform modal Split
 * @param params
 * @param anaParams
 * @param timeBeanId
 */
protected void performModalSplit(LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams,String timeBeanId) {
	double modeMiu=anaParams.get(CNLSUEModel.ModeMiuName);
	double carD = 0;
	double totalD = 0;
	for(AnalyticalModelODpair odPair:this.odPairs.getODpairset().values()){
		//For GV car proportion is always 1
		if(odPair.getSubPopulation()!=null && odPair.getSubPopulation().contains("GV")) {
			double carDemand=this.Demand.get(timeBeanId).get(odPair.getODpairId());
			this.carDemand.get(timeBeanId).put(odPair.getODpairId(),carDemand);
			
			this.carProbability.get(timeBeanId).put(odPair.getODpairId(), 1.);
			//carD+=carDemand;
			//totalD+=carDemand;
			continue;
		}
		// if a phantom trip, car and pt proportion is decided from the simulation and will not be changed
//		}else if(odPair.getSubPopulation()!=null && odPair.getSubPopulation().contains("trip")) {
//			double carDemand=this.Demand.get(timeBeanId).get(odPair.getODpairId())*odPair.getCarModalSplit();
//			if(Double.isNaN(carDemand)|| Double.isInfinite(carDemand)) {
//				//logger.info("car demand nan");
//			}else {
//				//carD+=carDemand;
//				//
//			}
//			this.carDemand.get(timeBeanId).put(odPair.getODpairId(),carDemand);
//			this.carProbability.get(timeBeanId).put(odPair.getODpairId(), odPair.getCarModalSplit());
//			//totalD+=this.Demand.get(timeBeanId).get(odPair.getODpairId());
//			continue;
//		}
		double demand=this.Demand.get(timeBeanId).get(odPair.getODpairId());
		if(demand!=0) { 
			
		Double carUtility=this.expectedMaximumCarUtility.get(timeBeanId).get(odPair.getODpairId());
		Double transitUtility=this.expectedMaximumTrUtility.get(timeBeanId).get(odPair.getODpairId());
		if(carUtility==null)carUtility = Double.NEGATIVE_INFINITY;
		if(transitUtility==null)transitUtility = Double.NEGATIVE_INFINITY;
		if(carUtility==Double.NEGATIVE_INFINITY||transitUtility==Double.POSITIVE_INFINITY||
				Math.exp(transitUtility*modeMiu)==Double.POSITIVE_INFINITY) {
			this.carDemand.get(timeBeanId).put(odPair.getODpairId(), 0.0);
			this.carProbability.get(timeBeanId).put(odPair.getODpairId(), 0.);
			totalD += demand;
		}else if(transitUtility==Double.NEGATIVE_INFINITY||carUtility==Double.POSITIVE_INFINITY
				||Math.exp(carUtility*modeMiu)==Double.POSITIVE_INFINITY) {
			this.carDemand.get(timeBeanId).put(odPair.getODpairId(), this.Demand.get(timeBeanId).get(odPair.getODpairId()));
			this.carProbability.get(timeBeanId).put(odPair.getODpairId(), 1.);
			carD+=demand;
			totalD+=demand;
		}else if(carUtility==Double.NEGATIVE_INFINITY && transitUtility==Double.NEGATIVE_INFINITY){
			this.carDemand.get(timeBeanId).put(odPair.getODpairId(), 0.);
			this.carProbability.get(timeBeanId).put(odPair.getODpairId(), 0.);
		}else {
			double maxUtil = Math.max(carUtility, transitUtility);
			double carProportion=Math.exp((carUtility-maxUtil)*modeMiu)/(Math.exp((carUtility-maxUtil)*modeMiu)+Math.exp((transitUtility-maxUtil)*modeMiu));
			this.carProbability.get(timeBeanId).put(odPair.getODpairId(), carProportion);
			//System.out.println("Car Proportion = "+carProportion);
			Double cardemand=carProportion*this.Demand.get(timeBeanId).get(odPair.getODpairId());
			if(cardemand==Double.NaN||cardemand==Double.POSITIVE_INFINITY||cardemand==Double.NEGATIVE_INFINITY) {
				logger.error("Car Demand is invalid");
				throw new IllegalArgumentException("car demand is invalid");
			}
			this.carDemand.get(timeBeanId).put(odPair.getODpairId(),cardemand);
			carD+=cardemand;
			totalD+=demand;
		}
	}else {
		this.carDemand.get(timeBeanId).put(odPair.getODpairId(),0.);
		this.carProbability.get(timeBeanId).put(odPair.getODpairId(), 0.);
	}
	}
	logger.info("carProportion = " + carD/totalD);
}



public Map<String, Map<Id<AnalyticalModelODpair>, Double>> getDemand() {
	return Demand;
}

/**
 * This function will initialize all gradients with zero
 * @param Oparams
 * Be very careful while using the function as it uses all the incidences, which are not ready until the first iteration. The updating can be avoided though.
 * 
 * TODO: Should I use ConcurrentHashMap instead of HashMap?
 * TODO: Should I use parallelStream instead of Stream?
 */
public void initializeGradients(LinkedHashMap<String,Double> Oparams) {
//	Map<String,Double> zeroGrad = new HashMap<>();
	this.gradientKeys = ODUtils.extractODVarKeys(Oparams.keySet());
	this.gradientArray = new MapToArray<String>("Gradient",this.gradientKeys);
//	this.gradientKeys.forEach(k->{
//		zeroGrad.put(k, 0.);
//	});
	this.gradMultiplier = MatrixUtils.createRealVector(new double[this.gradientKeys.size()]).mapAdd(1.).toArray();
	for(String timeId:this.timeBeans.keySet()) {
		this.linkGradient.put(timeId, new HashMap<>());
		//this.linkGradient.get(timeId).putAll(this.networks.get(timeId).getLinks().keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
		this.linkGradient.get(timeId).putAll(this.linkIncidenceMatrix.keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new double[this.gradientKeys.size()])));
		
		this.linkTTGradient.put(timeId, new HashMap<>());
		//this.linkTTGradient.get(timeId).putAll(this.networks.get(timeId).getLinks().keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
		this.linkTTGradient.get(timeId).putAll(this.linkIncidenceMatrix.keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new double[this.gradientKeys.size()])));
		
		this.trLinkGradient.put(timeId, new HashMap<>());
		//this.trLinkGradient.get(timeId).putAll(this.transitLinks.get(timeId).keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
		this.trLinkGradient.get(timeId).putAll(this.transitLinks.get(timeId).keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new double[this.gradientKeys.size()])));
		
		this.trLinkTTGradient.put(timeId, new HashMap<>());
		//this.trLinkTTGradient.get(timeId).putAll(this.transitLinks.get(timeId).keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
		this.trLinkTTGradient.get(timeId).putAll(this.transitLinks.get(timeId).keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new double[this.gradientKeys.size()])));
		
		this.routeFlowGradient.put(timeId, new HashMap<>());
		//this.routeFlowGradient.get(timeId).putAll(this.routeProb.get(timeId).keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
		this.routeFlowGradient.get(timeId).putAll(this.routeProb.get(timeId).keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new double[this.gradientKeys.size()])));
		
		this.trRouteFlowGradient.put(timeId, new HashMap<>());
		//this.trRouteFlowGradient.get(timeId).putAll(this.trRouteProb.get(timeId).keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
		this.trRouteFlowGradient.get(timeId).putAll(this.trRouteProb.get(timeId).keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new double[this.gradientKeys.size()])));
		
		
		this.fareLinkGradient.put(timeId, new HashMap<>());
		//this.fareLinkGradient.get(timeId).putAll(this.fareLinkincidenceMatrix.get(timeId).keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new HashMap<>(zeroGrad))));
		this.fareLinkGradient.get(timeId).putAll(this.fareLinkincidenceMatrix.get(timeId).keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new double[this.gradientKeys.size()])));
		
		this.odParameterIncidence.put(timeId, new HashMap<>());
		this.odParameterIncidence.get(timeId).putAll(this.odPairs.getODpairset().keySet().parallelStream().collect(Collectors.toMap(kk->kk, kk->new double[this.gradientKeys.size()])));
		this.ifODParameterIncidence.put(timeId, true);
		initializeTrPassengerOnPhysicalLinkGradient(timeId);
	}
	this.intiializeGradient = false;
	this.linkGradL1NormThreshold = this.gradientKeys.size()*3600;
	logger.info("Finished initializing gradients");
}

/**
 * This function initialize the Tr passenger gradient
 * @param timeId
 */
private void initializeTrPassengerOnPhysicalLinkGradient(String timeId) {
	this.trPassengerOnPhysicalLinkGradient.put(timeId, new HashMap<>());
	//for(Id<Link> l:this.networks.get(timeId).getLinks().keySet())this.trPassengerOnPhysicalLinkGradient.get(timeId).put(l,new ConcurrentHashMap<>());
	for(TransitLink l:this.transitLinks.get(timeId).values()) {
		if(l instanceof CNLTransitDirectLink) {
			CNLTransitDirectLink ll = (CNLTransitDirectLink)l;
			String lrId = CNLTransitDirectLink.calcLineRouteId(ll.getLineId(), ll.getRouteId());
			ll.getLinkList().forEach(lll->{
				if(this.networks.get(timeId).getLinks().get(lll).getAllowedModes().contains("train") && !this.trPassengerOnPhysicalLinkGradient.get(timeId).containsKey(lll))this.trPassengerOnPhysicalLinkGradient.get(timeId).put(lll,new ConcurrentHashMap<>());
				if(this.trPassengerOnPhysicalLinkGradient.get(timeId).get(lll)!=null && !this.trPassengerOnPhysicalLinkGradient.get(timeId).get(lll).containsKey(lrId))this.trPassengerOnPhysicalLinkGradient.get(timeId).get(lll).put(lrId, new double[this.gradientKeys.size()]);
			});
		}
	}
}

/**
 * This is a huge step forward. This class can calculate gradient using backpropagation
 * Still not checked
 * @param population
 * @param counter
 * @param Oparams
 * @param anaParam
 */
public void caclulateGradient(String timeId, int counter, LinkedHashMap<String,Double> oparams, LinkedHashMap<String,Double>anaParam) {
	this.caclulateGradient(timeId, counter, oparams, anaParam, false);
}

/**
 * Get the car route flow gradients (Equation 48)
 * @param od
 * @param params
 * @param timeId
 * @return
 */
public Tuple<RealVector, Map<Id<AnalyticalModelRoute>,double[]>> getCarRouteGrads( AnalyticalModelODpair od, 
		LinkedHashMap<String,Double> params, String timeId) {
	Map<Id<AnalyticalModelRoute>,double[]> routeUGrad = new HashMap<>();//Route gradient
	
	//	double carUGrad = 0;
	
	//Calculate the route gradient from the gradient vector
	RealVector carUGradient = MatrixUtils.createRealVector(new double[this.gradientKeys.size()]);
	if( od.getRoutes()!=null) {
		for(AnalyticalModelRoute route: od.getRoutes()) {
	//		double uGradient = 0;
			RealVector uGrad = MatrixUtils.createRealVector(new double[this.gradientKeys.size()]);
			for(Id<Link>linkId: route.getLinkIds()) { //The route gradient is the sum of all links
	//			uGradient+=this.linkTTGradient.get(timeId).get(linkId).get(var);
				uGrad = uGrad.add(MatrixUtils.createRealVector(this.linkTTGradient.get(timeId).get(linkId)));
			}
			//uGradient*=(params.get(CNLSUEModel.MarginalUtilityofTravelCarName)-params.get(CNLSUEModel.MarginalUtilityofPerformName));
			uGrad.mapMultiplyToSelf((params.get(CNLSUEModel.MarginalUtilityofTravelCarName)-params.get(CNLSUEModel.MarginalUtilityofPerformName)));
			//if(Double.isNaN(uGradient))logger.debug("Debug point. Gradient is NAN");
	//		routeUGradient.put(route.getRouteId(), uGradient);
			routeUGrad.put(route.getRouteId(), uGrad.toArray());
	//		carUGrad+=this.routeProb.get(timeId).get(route.getRouteId())*uGradient;
			carUGradient = carUGradient.add(uGrad.mapMultiply(this.routeProb.get(timeId).get(route.getRouteId())));
		}
		if(od.getRoutes().size()!=routeUGrad.size()) {
			logger.debug("route size mismatch!!!");
		}
	}
	return new Tuple<RealVector, Map<Id<AnalyticalModelRoute>,double[]>>(carUGradient, routeUGrad);
}

/**
 * Get the transit route gradients parameters (Equation (65)
 * @param od
 * @param params
 * @param timeId
 * @return A vector and route gradients
 */
public Tuple<RealVector, Map<Id<AnalyticalModelTransitRoute>,double[]>> getTrRouteGrads( AnalyticalModelODpair od, 
		LinkedHashMap<String,Double> params, String timeId) {
	//	Map<Id<AnalyticalModelTransitRoute>,Double> trRouteUGradient = new HashMap<>();	
	Map<Id<AnalyticalModelTransitRoute>,double[]> trRouteUGrad = new HashMap<>();
	//	double trUGradient = 0;
	RealVector trUtGrad = MatrixUtils.createRealVector(new double[this.gradientKeys.size()]);
	if(od.getTrRoutes(timeId)!=null) {
		for(AnalyticalModelTransitRoute trRoute : od.getTrRoutes(timeId)) {
			
	//				double routeGradientDlink = 0;
			RealVector routeGradDlink = MatrixUtils.createRealVector(new double[this.gradientKeys.size()]);
	//				double routeGradientTRLink = 0;
			RealVector routeGradTRLink = MatrixUtils.createRealVector(new double[this.gradientKeys.size()]);
			for(TransitDirectLink dlink:trRoute.getTransitDirectLinks()) {
	//					routeGradientDlink += this.trLinkTTGradient.get(timeId).get(dlink.getTrLinkId()).get(var);
				RealVector temp = MatrixUtils.createRealVector(this.trLinkTTGradient.get(timeId).get(dlink.getTrLinkId()));
//				if(temp.getMaxValue() > 9999 || temp.getMinValue() < -9999) {
//					System.nanoTime();
//				}
				routeGradDlink = routeGradDlink.add(temp);
			}
	//				routeGradientDlink*=params.get(CNLSUEModel.MarginalUtilityofTravelptName)-params.get(CNLSUEModel.MarginalUtilityofPerformName);
			routeGradDlink.mapMultiplyToSelf(params.get(CNLSUEModel.MarginalUtilityofTravelptName)-params.get(CNLSUEModel.MarginalUtilityofPerformName));
			for(TransitTransferLink trlink:trRoute.getTransitTransferLinks()) {
	//					routeGradientTRLink += this.trLinkTTGradient.get(timeId).get(trlink.getTrLinkId()).get(var);
				RealVector temp = MatrixUtils.createRealVector(this.trLinkTTGradient.get(timeId).get(trlink.getTrLinkId()));
//				if(temp.getMaxValue() > 9999 || temp.getMinValue() < -9999) {
//					System.nanoTime();
//				}
				routeGradTRLink = routeGradTRLink.add(temp);
			}
	//				routeGradientTRLink*=params.get(CNLSUEModel.MarginalUtilityofWaitingName)-params.get(CNLSUEModel.MarginalUtilityofPerformName);
			routeGradTRLink.mapMultiplyToSelf(params.get(CNLSUEModel.MarginalUtilityofWaitingName)-params.get(CNLSUEModel.MarginalUtilityofPerformName));
	//				double grad = routeGradientDlink+routeGradientTRLink;
			RealVector g = routeGradDlink.add(routeGradTRLink).map(new Clip(-500,500));
			
	//				if(Double.isNaN(grad))
	//					logger.debug("Debug point. Gradient is NAN");
	//				trRouteUGradient.put(trRoute.getTrRouteId(), grad);
			trRouteUGrad.put(trRoute.getTrRouteId(), g.toArray());
				
		//				trUGradient += this.trRouteProb.get(timeId).get(trRoute.getTrRouteId())*grad;
			double trProb = 0.;
			if(this.trRouteProb.get(timeId).containsKey(trRoute.getTrRouteId())) {
				trProb = this.trRouteProb.get(timeId).get(trRoute.getTrRouteId());
			}
			trUtGrad = trUtGrad.add(g.mapMultiply(trProb));
		}
	}
	return new Tuple<RealVector, Map<Id<AnalyticalModelTransitRoute>,double[]>>(trUtGrad, trRouteUGrad);
}

/**
 * This is a huge step forward. This class can calculate gradient using backpropagation
 * Still not checked
 * @param population
 * @param counter
 * @param Oparams
 * @param anaParam
 */
public void caclulateGradient(String timeId, int counter, LinkedHashMap<String,Double> oparams, 
		LinkedHashMap<String,Double> anaParam, boolean useUnitUpdateWeight) {
	//RealVector p = null;
	
	if(this.intiializeGradient) {//maybe its better to do it once in the generate od pair and then not do it again 
		this.initializeGradients(oparams);
		//System.out.println("GB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024*1024));
		
	}else {
		initializeTrPassengerOnPhysicalLinkGradient(timeId); //Reset the TR passengers on physical link
		//Calculate the auto link flow travel time gradients
		this.linkTTGradient.get(timeId).entrySet().parallelStream().forEach(linkGradientMap-> {
			//for(Entry<Id<Link>,Map<String,Double>> linkGradientMap:timeMap.getValue().entrySet()) {
			CNLLink link = (CNLLink) this.networks.get(timeId).getLinks().get(linkGradientMap.getKey());
			double[] g = GradientUtils.getLinkTravelTimeGrad(link, anaParam, linkGradient.get(timeId), 
					this.Params.get(CNLSUEModel.CapacityMultiplierName), this.timeBeans.get(timeId));
			this.linkTTGradient.get(timeId).put(link.getId(),g);

			//}
			
		});
		
		//Calculate the transit link flow transit link travel time gradient
		this.trLinkTTGradient.get(timeId).entrySet().parallelStream().forEach(linkGradientMap->{	
			TransitLink link = this.transitLinks.get(timeId).get(linkGradientMap.getKey());
			//for(Entry<String, Double> var:linkGradientMap.getValue().entrySet()) {
			Tuple<Id<TransitLink>, double[]> mapTuple = GradientUtils.getTransitLinkTravelTimeGrad(link, 
					linkTTGradient.get(timeId), anaParam,
					trLinkGradient.get(timeId), this.gradientKeys.size(), this.networks.get(timeId));

			this.trLinkTTGradient.get(timeId).put(mapTuple.getFirst(), mapTuple.getSecond());
			//}
		});
	}
	
	//For each OD pairs
	this.odPairs.getODpairset().entrySet().parallelStream().forEach(od->{
//		for(String var:this.gradientKeys) {
		//Calculate the auto route utility gradient first
		LinkedHashMap<String,Double> params = this.handleBasicParams(oparams, od.getValue().getSubPopulation(), this.config);
//			Map<Id<AnalyticalModelRoute>,Double> routeUGradient = new HashMap<>();
		
		//Get the tuples of auto route gradients (Eq. (63))
		Tuple<RealVector, Map<Id<AnalyticalModelRoute>,double[]>> cTuple = getCarRouteGrads( 
				od.getValue(), params, timeId);
		Map<Id<AnalyticalModelRoute>,double[]> routeUGrad = cTuple.getSecond(); //Route utility gradient
		RealVector carUGradient = cTuple.getFirst();
		
		//Get the tuples of TR route gradients (Eq. (65))
		Tuple<RealVector, Map<Id<AnalyticalModelTransitRoute>,double[]>> tTuple = getTrRouteGrads( 
				od.getValue(), params, timeId);
		Map<Id<AnalyticalModelTransitRoute>,double[]> trRouteUGrad = tTuple.getSecond();
		RealVector trUtGrad = tTuple.getFirst();
		
		//Calculate auto route flow gradient (Equation (55))
		double pm = 0;
		if(this.carProbability.get(timeId).get(od.getKey())!=null) {
			pm = this.carProbability.get(timeId).get(od.getKey()); //Mode split portion of car
		}
//			double modeConst = pm*carUGrad+(1-pm)*trUGradient;
		RealVector modeC = carUGradient.mapMultiply(pm).add(trUtGrad.mapMultiply(1-pm)); //Eqaution (58)
		if(modeC.getNorm()>20000) {
			logger.debug("Too High mode const");
		}
		double d = this.Demand.get(timeId).get(od.getKey()); //Demand of this OD pair (q_od,t)
		//Map<String,Double> odInc = new HashMap<>();
//			for(Entry<Id<AnalyticalModelRoute>, Double> rId:routeUGradient.entrySet()) {
		int nonZeroGrad = 0;
		
		for(Entry<Id<AnalyticalModelRoute>, double[]> rId:routeUGrad.entrySet()) {
			double pr = this.routeProb.get(timeId).get(rId.getKey()); // P_{od,t,r}
	//		double term0 = pm*d*pr*anaParam.get(CNLSUEModel.LinkMiuName)*(rId.getValue()-carUGrad);
			//t0 and t1 implicitly considered the utility cal in (57)
			RealVector t0 = MatrixUtils.createRealVector(rId.getValue()).subtract(carUGradient).mapMultiply(pm*d*pr*anaParam.get(CNLSUEModel.LinkMiuName));
	//		double term1 = pr*d*pm*anaParam.get(CNLSUEModel.ModeMiuName)*(carUGrad-modeConst);
			RealVector t1 = carUGradient.subtract(modeC).mapMultiply(pr*d*pm*anaParam.get(CNLSUEModel.ModeMiuName));
	//		Map<String,Double> tt2 = new HashMap<>();
			
			if(this.ifODParameterIncidence.get(timeId)) {
				Map<String,Double> oi = new HashMap<>();
				double sum = 0;
				for(String var1:this.gradientKeys) {
					double term2 = ODUtils.ifMatch_1_else_0(od.getKey(),this.odPairs.getODpairset().get(od.getKey()).getSubPopulation(), timeId, var1);
					oi.put(var1, term2);
					sum+=term2;
				}
				if(sum == 0) {
					logger.debug("No variable found!!!");
				}
				this.odParameterIncidence.get(timeId).put(od.getKey(), this.gradientArray.getMatrix(oi));
			}
			RealVector odInc = MatrixUtils.createRealVector(this.odParameterIncidence.get(timeId).get(od.getKey()));
			if(odInc.getL1Norm()==0) {
				logger.debug("OD incidence can not be zero!!!");
			}
			RealVector p = this.gradientArray.getRealVector(oparams);
			RealVector tt2 = odInc.ebeDivide(p).mapMultiply(pr*pm*d).ebeDivide(MatrixUtils.createRealVector(this.gradMultiplier));
//				for(String var1:this.gradientKeys) {
//					//double term2 = pr*pm*ODUtils.ifMatch_1_else_0(od.getKey(),this.odPairs.getODpairset().get(od.getKey()).getSubPopulation(), timeId, var1)*d/params.get(var1);
//					double term2 = pr*pm*ODUtils.ifMatch_1_else_0(od.getKey(),this.odPairs.getODpairset().get(od.getKey()).getSubPopulation(), timeId, var1)*d/params.get(var1);
//					tt2.put(var1, term2);
//				}
//				if(tt2.size()!=this.gradientKeys.size()) {
//					System.out.println();
//				}
//				if(this.gradientArray.getKeySet().size()!=this.gradientKeys.size()) {
//					System.out.println();
//				}
//				double grad = term0 + term1 + term2;
			//RealVector g = t0.add(t1).add(this.gradientArray.getRealVector(tt2));
			RealVector g = t0.add(t1).add(tt2);
			if(g.getL1Norm()!=0)nonZeroGrad++;
//				this.routeFlowGradient.get(timeId).get(rId.getKey()).put(var, grad);
			if(g.isInfinite()||g.isNaN()) {
				logger.error("Nan or infinite gradient!!! Check");
			}
//				System.out.println(odInc.getL1Norm());
//				System.out.println(tt2.getL1Norm());
			if(d!=0 && tt2.getL1Norm()==0) {
				logger.debug("Problem!!!");
			}
			g = g.mapMultiplyToSelf(od.getValue().getAveragePCU());
			this.routeFlowGradient.get(timeId).put(rId.getKey(), g.toArray());
		}
		
		// Calculate the transit flow gradient
		for(Entry<Id<AnalyticalModelTransitRoute>, double[]> trUGrad: trRouteUGrad.entrySet()) {
			double pr = 0.;
			if(this.trRouteProb.get(timeId).containsKey(trUGrad.getKey())) {
				pr = this.trRouteProb.get(timeId).get(trUGrad.getKey());
			}
//				double term0 = (1-pm)*d*pr*anaParam.get(CNLSUEModel.LinkMiuName)*(trUGrad.getValue()-trUGradient);
			RealVector t0 = trUtGrad.subtract(MatrixUtils.createRealVector(trUGrad.getValue())).mapMultiplyToSelf((1-pm)*d*pr*anaParam.get(CNLSUEModel.LinkMiuName));
//				double term1 = pr*d*(1-pm)*anaParam.get(CNLSUEModel.ModeMiuName)*(trUGradient-modeConst);
			RealVector t1 = trUtGrad.subtract(modeC).mapMultiplyToSelf(pr*d*(1-pm)*anaParam.get(CNLSUEModel.ModeMiuName));
//				double term2 = pr*(1-pm)*ODUtils.ifMatch_1_else_0(od.getKey(),this.odPairs.getODpairset().get(od.getKey()).getSubPopulation(), timeId, var)*d/params.get(var);
//				double grad = term0 + term1 + term2;
			//Map<String,Double> tt2 = new HashMap<>();
//				for(String var1:this.gradientKeys) {
//					//double term2 = pr*pm*ODUtils.ifMatch_1_else_0(od.getKey(),this.odPairs.getODpairset().get(od.getKey()).getSubPopulation(), timeId, var1)*d/params.get(var1);
//					double term2 = pr*(1-pm)*ODUtils.ifMatch_1_else_0(od.getKey(),this.odPairs.getODpairset().get(od.getKey()).getSubPopulation(), timeId, var1)*d/params.get(var1);
//					tt2.put(var1, term2);
//				}
			if(this.ifODParameterIncidence.get(timeId)) {
				Map<String,Double> oi = new HashMap<>();
				double sum = 0;
				for(String var1:this.gradientKeys) {
					double term2 = ODUtils.ifMatch_1_else_0(od.getKey(),this.odPairs.getODpairset().get(od.getKey()).getSubPopulation(), timeId, var1);
					oi.put(var1, term2);
					sum+=term2;
				}
				if(sum == 0) {
					logger.debug("No variable found!!!");
				}
				this.odParameterIncidence.get(timeId).put(od.getKey(), this.gradientArray.getMatrix(oi));
			}
			RealVector odInc = MatrixUtils.createRealVector(this.odParameterIncidence.get(timeId).get(od.getKey()));
			RealVector p = this.gradientArray.getRealVector(oparams);
			RealVector tt2 = odInc.ebeDivide(p).mapMultiply(pr*(1-pm)*d).ebeDivide(MatrixUtils.createRealVector(this.gradMultiplier));
			//RealVector g = t0.add(t1).add(this.gradientArray.getRealVector(tt2));
			RealVector g = t0.add(t1).add(tt2);
			if(g.getL1Norm()!=0)nonZeroGrad++;
			
			g.map(new Clip(-500, 500));
//				this.trRouteFlowGradient.get(timeId).get(trUGrad.getKey()).put(var, grad);
			this.trRouteFlowGradient.get(timeId).put(trUGrad.getKey(), g.toArray());
//				System.out.println(odInc.getL1Norm());
//				System.out.println(tt2.getL1Norm());
			if(d!=0 && tt2.getL1Norm()== 0 && pm!=1 && pr!=0) {
				logger.debug("Problem!!!");
				System.out.println(odInc.getL1Norm());
			}
		}
		
//		}
		//this.applyODBasedGradeintClipping(routeUGrad.keySet(), trRouteUGrad.keySet(), od.getKey(), timeId);
	
	//Here is the end of OD
	});
	if(this.ifGradMultiply && (counter == 1||counter%10==0)) {//For a newly started TA cycle, both t0 and t1 terms in transit and car routes are zero. 
		//As a result, that is used to derive the gradient multiplier. The multiplier can be directly applied to the route and tr route gradient as well for the same reason
		// However, at later stage the old gradient is added with the new gradient. So, to make the basis of the two gradient same, the multiplier has to be added at the variable gradient source. 
		//In this model, which is vector tt2 in lines 1344 and 1380. Ashraf, Dec20.
		double[] m = new double[this.gradientKeys.size()];
		for(Entry<String, Map<Id<AnalyticalModelRoute>, double[]>> t:this.routeFlowGradient.entrySet()){
			for(double[]d:t.getValue().values())m =findAbsMax(m,d);
		}
		
		for(Entry<String, Map<Id<AnalyticalModelTransitRoute>, double[]>> t:this.trRouteFlowGradient.entrySet()){
			for(double[]d:t.getValue().values())m =findAbsMax(m,d);
		}
		
		if(counter == 1) {
			for(int i = 0;i<m.length;i++) {
				if(m[i]!=0 && (m[i]>this.maxAbsGrad||m[i]<this.minAbsGrad)) {
					this.gradMultiplier[i] =2*m[i]/(this.maxAbsGrad+this.minAbsGrad);
					if(Double.isInfinite(2*m[i]/(this.maxAbsGrad+this.minAbsGrad))||Double.isNaN(2*m[i]/(this.maxAbsGrad+this.minAbsGrad))||2*m[i]/(this.maxAbsGrad+this.minAbsGrad)==0) {
						logger.debug("multiplier is either infinite, nan or 0!!!");
					}
				}
			}
			this.routeFlowGradient.values().forEach(t->{
				t.values().forEach(d->{
					for(int i = 0;i<d.length;i++)d[i]=d[i]/this.gradMultiplier[i];
				});
			});
			
			this.trRouteFlowGradient.values().forEach(t->{
				t.values().forEach(d->{
					for(int i = 0;i<d.length;i++)d[i]=d[i]/this.gradMultiplier[i];
				});
			});
		}else {
			double norm = 0;
			for(double d:m)norm+=Math.abs(d);
			if(Double.isInfinite(norm)) {
				logger.debug("max grad norm infinity!!!");
			}
			if(norm>this.maxAbsL1Norm) {
				this.scaleBackGradients();
				this.gradMultiplier = MatrixUtils.createRealVector(new double[m.length]).mapAdd(1).mapMultiply(norm/this.maxAbsL1Norm).toArray();
			}
			this.scaleGradients();
		}

	}
	this.ifODParameterIncidence.put(timeId, false); //Ensure the OD parameter Incidence is false
	
	//finally the link volume and MaaSPackage usage gradient update	
	//TODO: See if we can transform this forEach to map
	this.linkGradient.get(timeId).entrySet().parallelStream().forEach(linkId->{
		this.linkGradient.get(timeId).put(linkId.getKey(),GradientUtils.getLinkFlowGrad(linkId, 
				this.beta.get(timeId).get(counter-1), this.gradientKeys.size(), 
				this.linkIncidenceMatrix.get(linkId.getKey()), this.routeFlowGradient.get(timeId), useUnitUpdateWeight));
	});
//	double totR = 0;
//	Set<Id<AnalyticalModelTransitRoute>> rIds = new HashSet<>();
//	for(AnalyticalModelODpair od:this.odPairs.getODpairset().values()) {
//		if(od.getTrRoutes(timeId)!=null) {
//			totR+=od.getTrRoutes(timeId).size();
//			od.getTrRoutes(timeId).stream().forEach(r->rIds.add(r.getTrRouteId()));
//		}
//	}
//	System.out.println("transit routes = "+totR);
//	System.out.println("unique transit routes = "+ rIds.size());
	
	this.trLinkGradient.get(timeId).entrySet().parallelStream().forEach(linkId->{	
//		for(String var:this.gradientKeys) {
//			this.trLinkGradient.get(timeId).get(linkId.getKey()).put(var, gradUpdate);
		RealVector gradU = GradientUtils.getTrLinkVolumeGrad(linkId, this.gradientKeys.size(), 
				this.trLinkIncidenceMatrix.get(timeId).get(linkId.getKey()), this.trRouteFlowGradient.get(timeId), 
				this.beta.get(timeId).get(counter-1), useUnitUpdateWeight);

		this.trLinkGradient.get(timeId).put(linkId.getKey(), gradU.toArray());
			
		//For Transit Direct Link, also process the trPassengerOnPhysicalLinkGradient
		//This part is for directly calculating the gradient for the flow
		if(this.transitLinks.get(timeId).get(linkId.getKey()) instanceof TransitDirectLink) {
			CNLTransitDirectLink link = (CNLTransitDirectLink) this.transitLinks.get(timeId).get(linkId.getKey());
			String lrId = CNLTransitDirectLink.calcLineRouteId(link.getLineId(), link.getRouteId());
			link.getLinkList().forEach(l->{
				//if(!this.trPassengerOnPhysicalLinkGradient.get(timeId).containsKey(l))this.trPassengerOnPhysicalLinkGradient.get(timeId).put(l, new HashMap<>());
				//if(!this.trPassengerOnPhysicalLinkGradient.get(timeId).get(l).containsKey(lrId))this.trPassengerOnPhysicalLinkGradient.get(timeId).get(l).put(lrId, new double[this.gradientKeys.size()]);
//					if(this.trPassengerOnPhysicalLinkGradient.get(timeId).get(l).get(lrId)==null) {
//						logger.debug("Why NUll??!!");
//					System.out.print(lrId);
//					}
				if(this.trPassengerOnPhysicalLinkGradient.get(timeId).get(l)!=null) {
					Map<String, double[]> trPassengerLinkGrad = this.trPassengerOnPhysicalLinkGradient.get(timeId).get(l);
					RealVector beforeArray = MatrixUtils.createRealVector(trPassengerLinkGrad.get(lrId));
					RealVector resultantArray = beforeArray.add(gradU);
					trPassengerLinkGrad.put(lrId, resultantArray.toArray());
//					if(link.getTrLinkId().toString().equals("TML_SUWDown_TML_HUHDown_TML_TML-WKS_TUM") && l.toString().equals("TML_TKWDown")) {
//						System.nanoTime();
//					}
					
				}
			});
		}
			
//		}
	});
	//Update the gradient of farelinks by the route flow gradient
	this.fareLinkGradient.get(timeId).entrySet().parallelStream().forEach(linkId->{
//		for(String var:this.gradientKeys) {
//			double grad = 0;
		RealVector g = MatrixUtils.createRealVector(new double[this.gradientKeys.size()]);
		for(Id<AnalyticalModelTransitRoute>r:this.fareLinkincidenceMatrix.get(timeId).get(linkId.getKey())) {
//				grad+=this.trRouteFlowGradient.get(timeId).get(r).get(var);
			if(this.trRouteFlowGradient.get(timeId).get(r)!=null) {
				g = g.add(MatrixUtils.createRealVector(this.trRouteFlowGradient.get(timeId).get(r)));
			}
		}
//			this.fareLinkGradient.get(timeId).get(linkId.getKey()).put(var, grad);
		this.fareLinkGradient.get(timeId).put(linkId.getKey(), g.toArray());
//		}
	});
	logger.info("Finished Calulating Gradient.");
	logger.info("Total OD pairs = "+this.odPairs.getODpairset().size());
}




public Scenario getScenario() {
	return scenario;
}

public void setScenario(Scenario scenario) {
	this.scenario = scenario;
}

public Map<String, Map<Id<Link>, Map<String, double[]>>> getTrPassengerOnPhysicalLinkGradient() {
	return trPassengerOnPhysicalLinkGradient;
}

/**
 * This is the same method and does the same task as perform SUE, but takes the internal Parameters as an input too.
 * This will be used for the internal parameters calibration internally
 * @param params
 * @return
 */
//@Override
//public SUEModelOutput perFormSUE(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams) {
//	this.resetCarDemand();
//	
//	LinkedHashMap<String,Double> inputParams=new LinkedHashMap<>(params);
//	LinkedHashMap<String,Double> inputAnaParams=new LinkedHashMap<>(anaParams);
//	//Loading missing parameters from the default values		
//	Map<String,Map<Id<Link>,Double>> outputLinkFlow=new HashMap<>();
//	
//	Map<String,Map<Id<TransitLink>,Double>> outputTrLinkFlow=new HashMap<>();
//	
//	
//	
//	//Checking and updating for the parameters 
//	for(Entry<String,Double> e:this.Params.entrySet()) {
//		if(!params.containsKey(e.getKey())) {
//			params.put(e.getKey(), e.getValue());
//		}
//	}
//	
//	//Checking and updating for the analytical model parameters
//	for(Entry<String,Double> e:this.AnalyticalModelInternalParams.entrySet()) {
//		if(!anaParams.containsKey(e.getKey())) {
//			anaParams.put(e.getKey(), e.getValue());
//		}
//	}
//	
//	//Creating different threads for different time beans
//	Thread[] threads=new Thread[this.timeBeans.size()];
//	int i=0;
//	for(String timeBeanId:this.timeBeans.keySet()) {
//		threads[i]=new Thread(new SUERunnable(this,timeBeanId,params,anaParams),timeBeanId);
//		i++;
//		outputLinkFlow.put(timeBeanId, new HashMap<Id<Link>, Double>());
//		outputLinkTT.put(timeBeanId, new HashMap<Id<Link>, Double>());
//		outputTrLinkFlow.put(timeBeanId, new HashMap<Id<TransitLink>, Double>());
//		outputTrLinkTT.put(timeBeanId, new HashMap<Id<TransitLink>, Double>());
//	}
//	//Starting the Threads
//	for(i=0;i<this.timeBeans.size();i++) {
//		threads[i].start();
//	}
//	
//	//joining the threads
//	for(i=0;i<this.timeBeans.size();i++) {
//		try {
//			threads[i].join();
//		} catch (InterruptedException e1) {
//			e1.printStackTrace();
//		}
//	}
//	
//	//Collecting the Link Flows
//	for(String timeBeanId:this.timeBeans.keySet()) {
//		for(Id<Link> linkId:this.getNetworks().get(timeBeanId).getLinks().keySet()) {
//			outputLinkFlow.get(timeBeanId).put(linkId, 
//					((AnalyticalModelLink) this.getNetworks().get(timeBeanId).getLinks().get(linkId)).getLinkAADTVolume());
//		}
//	}
//	
//	//Collecting the Link Transit 
//	for(String timeBeanId:this.timeBeans.keySet()) {
//		for(Id<TransitLink> linkId:this.transitLinks.get(timeBeanId).keySet()) {
//			outputTrLinkFlow.get(timeBeanId).put(linkId, 
//					(this.transitLinks.get(timeBeanId).get(linkId).getPassangerCount()));
//		}
//	}
//	
//	//collect pt occupancy
//	Map<String, Map<Id<Link>, Double>> averagePtOccupancyOnLink=new HashMap<>();
//	for(String timeBeanId:this.timeBeans.keySet()) {
//		averagePtOccupancyOnLink.put(timeBeanId, new HashMap<>());
//		for(Id<Link>linkId:this.totalPtCapacityOnLink.get(timeBeanId).keySet()) {
//			double occupancy=((CNLLink)this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkTransitPassenger()/this.totalPtCapacityOnLink.get(timeBeanId).get(linkId);
//			averagePtOccupancyOnLink.get(timeBeanId).put(linkId, occupancy);
//		}
//	}
//	
//	SUEModelOutput out=new SUEModelOutput(outputLinkFlow, outputTrLinkFlow, this.outputLinkTT, this.outputTrLinkTT);
//	out.setAveragePtOccupancyOnLink(averagePtOccupancyOnLink);
//	//new OdInfoWriter("toyScenario/ODInfo/odInfo",this.timeBeans).writeOdInfo(this.getOdPairs(), getDemand(), getCarDemand(), inputParams, inputAnaParams);
//	return out;
//}

private void resetCarDemand() {
	
	
	for(String timeId:this.timeBeans.keySet()) {
		this.Demand.put(timeId, this.odPairs.getdemand(timeId));
		this.carDemand.put(timeId, new HashMap<Id<AnalyticalModelODpair>, Double>());
		this.routeUtilities.put(timeId,new ConcurrentHashMap<>());
		this.trRouteUtilities.put(timeId, new ConcurrentHashMap<>());
		this.routeFlow.put(timeId, new ConcurrentHashMap<>());
		this.trRouteFlow.put(timeId, new ConcurrentHashMap<>());
		this.routeProb.put(timeId, new ConcurrentHashMap<>());
		this.trRouteProb.put(timeId, new ConcurrentHashMap<>());
		this.expectedMaximumCarUtility.put(timeId, new ConcurrentHashMap<>());
		this.expectedMaximumTrUtility.put(timeId, new ConcurrentHashMap<>());
		this.routeOdFactor.put(timeId,  new ConcurrentHashMap<>());
		this.ODLinkSensitivity.put(timeId, new ConcurrentHashMap<>());
		for(Entry<String, AnalyticalModelNetwork> n:this.networks.entrySet()) {
			n.getValue().getLinks().values().forEach(l->{
				((CNLLink)l).clearLinkCarFlow();
				((CNLLink)l).clearTransitPassangerFlow();
			});
		}
		for(Entry<String, Map<Id<TransitLink>, TransitLink>> lSet:this.transitLinks.entrySet()) {
			lSet.getValue().values().forEach(l->{
				l.resetLink();
			});
		}
		this.transitLinks.entrySet().forEach(e->{
			e.getValue().entrySet().forEach(ee->{
				ee.getValue().resetLink();
			});
		});
		this.intiializeGradient = true;
		for(Id<AnalyticalModelODpair> o:this.Demand.get(timeId).keySet()) {
			double totalDemand=this.Demand.get(timeId).get(o);
			AnalyticalModelODpair odpair = this.odPairs.getODpairset().get(o);
			
			this.carDemand.get(timeId).put(o, 0.5*totalDemand);
			this.carProbability.get(timeId).put(o, 0.5);
			
			if(odpair.getSubPopulation() != null && odpair.getSubPopulation().contains("GV")) {
				this.carDemand.get(timeId).put(o, totalDemand); 
				this.carProbability.get(timeId).put(o, 1.0);
			}
			}
		
		System.out.println();

	}
}

@Deprecated
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
	for(String timeBeanId:this.timeBeans.keySet()) {
		this.singleTimeBeanTA(params, anaParams, timeBeanId);
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
					count+=((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkAADTVolume();
				}
				m.putVolume(timeBeanId, count);
			}
		}
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
							if(m.getVolumes().containsKey(timeBeanId)) {
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

//public Map<String, Map<Id<Link>, Map<String, Double>>> getLinkGradient() {
public Map<String, Map<Id<Link>, double[]>> getLinkGradient() {
	return linkGradient;
}



//public Map<String, Map<Id<TransitLink>, Map<String, Double>>> getTrLinkGradient() {
public Map<String, Map<Id<TransitLink>, double[]>> getTrLinkGradient() {
	return trLinkGradient;
}



//public Map<String, Map<String, Map<String, Double>>> getFareLinkGradient() {
public Map<String, Map<String, double[]>> getFareLinkGradient() {
	return fareLinkGradient;
}

public Set<String> getGradientKeys() {
	return gradientKeys;
}

public CNLODpairs getOdPairs() {
	return odPairs;
}

public void setOdPairs(CNLODpairs odPairs) {
	this.odPairs = odPairs;
}

public MapToArray<String> getGradientArray() {
	return gradientArray;
}


private void applyODBasedGradeintClipping(Map<Id<AnalyticalModelRoute>,double[]>routeGrad,Map<Id<AnalyticalModelTransitRoute>,double[]>trRouteGrad,Id<AnalyticalModelODpair> odId,String timeBeanId) {
	RealVector delta = MatrixUtils.createRealVector(this.odParameterIncidence.get(timeBeanId).get(odId));
	RealVector norm = MatrixUtils.createRealVector(new double[delta.toArray().length]);
	for(double[] g:routeGrad.values())norm = norm.add(MatrixUtils.createRealVector(g).map(k->Math.abs(k)));
	for(double[] g:trRouteGrad.values())norm = norm.add(MatrixUtils.createRealVector(g).map(k->Math.abs(k)));
	RealVector demand  =  delta.mapAdd(2).mapMultiply(this.Demand.get(timeBeanId).get(odId));
	RealVector multiplier = demand.ebeDivide(norm);
	RealVector m = demand.subtract(norm).map(k->k>0?1:0).ebeMultiply(multiplier).map(k->k==0?1:k);
	multiplier = multiplier.ebeDivide(m);
	
	for(Entry<Id<AnalyticalModelRoute>,double[]> g:routeGrad.entrySet())g.setValue(MatrixUtils.createRealVector(g.getValue()).ebeMultiply(multiplier).toArray());
	for(Entry<Id<AnalyticalModelTransitRoute>,double[]> g:trRouteGrad.entrySet())g.setValue(MatrixUtils.createRealVector(g.getValue()).ebeMultiply(multiplier).toArray());
}

private void applyODBasedGradeintClipping(Set<Id<AnalyticalModelRoute>>routeIds,Set<Id<AnalyticalModelTransitRoute>>trRouteIds,Id<AnalyticalModelODpair> odId,String timeBeanId) {
	RealVector delta = MatrixUtils.createRealVector(this.odParameterIncidence.get(timeBeanId).get(odId));
	RealVector norm = MatrixUtils.createRealVector(new double[delta.toArray().length]);
	for(Id<AnalyticalModelRoute> rId:routeIds)norm = norm.add(MatrixUtils.createRealVector(this.routeFlowGradient.get(timeBeanId).get(rId)).map(k->Math.abs(k)));
	for(Id<AnalyticalModelTransitRoute> rId:trRouteIds)norm = norm.add(MatrixUtils.createRealVector(this.trRouteFlowGradient.get(timeBeanId).get(rId)).map(k->Math.abs(k)));
	RealVector demand  =  delta.mapAdd(2).mapMultiply(this.Demand.get(timeBeanId).get(odId));
	RealVector multiplier = demand.ebeDivide(norm);
	RealVector m = demand.subtract(norm).map(k->k>0?1:0).ebeMultiply(multiplier).map(k->k==0?1:k);
	multiplier = multiplier.ebeDivide(m);
	for(Id<AnalyticalModelRoute> rId:routeIds)this.routeFlowGradient.get(timeBeanId).put(rId, MatrixUtils.createRealVector(this.routeFlowGradient.get(timeBeanId).get(rId)).ebeMultiply(multiplier).toArray());
	for(Id<AnalyticalModelTransitRoute> rId:trRouteIds)this.trRouteFlowGradient.get(timeBeanId).put(rId, MatrixUtils.createRealVector(this.trRouteFlowGradient.get(timeBeanId).get(rId)).ebeMultiply(multiplier).toArray());
}

private void applyVerticalGradeintClipping(Set<Id<AnalyticalModelRoute>>routeIds,Set<Id<AnalyticalModelTransitRoute>>trRouteIds,Id<AnalyticalModelODpair> odId,String timeBeanId) {
	RealVector delta = MatrixUtils.createRealVector(this.odParameterIncidence.get(timeBeanId).get(odId));
	RealVector norm = MatrixUtils.createRealVector(new double[delta.toArray().length]);
	for(Id<AnalyticalModelRoute> rId:routeIds)norm = norm.add(MatrixUtils.createRealVector(this.routeFlowGradient.get(timeBeanId).get(rId)).map(k->Math.abs(k)));
	for(Id<AnalyticalModelTransitRoute> rId:trRouteIds)norm = norm.add(MatrixUtils.createRealVector(this.trRouteFlowGradient.get(timeBeanId).get(rId)).map(k->Math.abs(k)));
	RealVector demand  =  delta.mapAdd(2).mapMultiply(this.Demand.get(timeBeanId).get(odId));
	RealVector multiplier = demand.ebeDivide(norm);
	RealVector m = demand.subtract(norm).map(k->k>0?1:0).ebeMultiply(multiplier).map(k->k==0?1:k);
	multiplier = multiplier.ebeDivide(m);
	for(Id<AnalyticalModelRoute> rId:routeIds)this.routeFlowGradient.get(timeBeanId).put(rId, MatrixUtils.createRealVector(this.routeFlowGradient.get(timeBeanId).get(rId)).ebeMultiply(multiplier).toArray());
	for(Id<AnalyticalModelTransitRoute> rId:trRouteIds)this.trRouteFlowGradient.get(timeBeanId).put(rId, MatrixUtils.createRealVector(this.trRouteFlowGradient.get(timeBeanId).get(rId)).ebeMultiply(multiplier).toArray());
}

public static double[] findAbsMax(double[]a,double[]b){
	double[] out = new double[a.length];
	 for(int i = 0;i<a.length;i++) {
		out[i] = Double.max(Math.abs(a[i]),Math.abs(b[i]));
	}
	return out; 
}

private void scaleBackGradients() {
	RealVector m = MatrixUtils.createRealVector(this.gradMultiplier);
	this.routeFlowGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeMultiply(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.trRouteFlowGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeMultiply(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.linkGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeMultiply(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.trLinkGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeMultiply(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.linkTTGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeMultiply(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.trLinkTTGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeMultiply(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.fareLinkGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeMultiply(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
}

private void scaleGradients() {
	RealVector m = MatrixUtils.createRealVector(this.gradMultiplier);
	this.routeFlowGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeDivide(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.trRouteFlowGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeDivide(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.linkGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeDivide(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.trLinkGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeDivide(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.linkTTGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeDivide(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.trLinkTTGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeDivide(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
	
	this.fareLinkGradient.entrySet().forEach(t->{
		t.getValue().entrySet().forEach(d->{
			RealVector dd = MatrixUtils.createRealVector(d.getValue()).ebeDivide(m); 
			if(dd.isInfinite()||dd.isNaN()) {
				logger.debug("gradient infinite or nan!!!");
			}
			d.setValue(dd.toArray());
//			for(int i = 0;i<d.length;i++)
//				d[i] = d[i]*this.gradMultiplier[i];
		});
	});
}
}
