package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicles;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class SUEModelContTimeSubPop extends SUEModelContTime{

	private ArrayList<String> subPopulationName=new ArrayList<>();
	private ParamReader pReader=new ParamReader("input/subPopParamAndLimit.csv");
	
	
	public SUEModelContTimeSubPop(Map<String, Tuple<Double, Double>> timeBean,ArrayList<String> subPopName) {
		super(timeBean);
		this.subPopulationName=subPopName;
		super.setDefaultParameters(pReader.getDefaultParam());
	}
	
	public SUEModelContTimeSubPop(Map<String, Tuple<Double, Double>> timeBean,ParamReader preader) {
		super(timeBean);
		this.subPopulationName=pReader.getSubPopulationName();
		this.pReader=preader;
		this.Params.clear();
		super.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
		this.setTollerance(0.1);
	}
	
	@Override
	public void generateRoutesAndOD(Population population,Network network,TransitSchedule transitSchedule,
			Scenario scenario,Map<String,FareCalculator> fareCalculator) {
		//System.out.println("");
		this.odPairs=new CNLODpairs(network,population,transitSchedule,scenario,this.getTimeBeans());
		this.scenario=scenario;
//		Config odConfig=ConfigUtils.createConfig();
//		odConfig.network().setInputFile("data/odNetwork.xml");
		
		Network odNetwork=NetworkUtils.readNetwork("data/tpusbNetwork.xml");
		this.getOdPairs().generateODpairsetSubPop(odNetwork);//This network has priority over the constructor network. This allows to use a od pair specific network 
		this.ts=transitSchedule;
		for(String s:this.getTimeBeans().keySet()) {
			this.getNetworks().put(s, new CNLNetwork(network));
		}
		this.performTransitVehicleOverlay(this.networks, this.ts,scenario.getTransitVehicles(), this.Params, this.AnalyticalModelInternalParams, 
				true, true);
		this.odPairs.generateRouteandLinkIncidence(0.,this.individualPtCapacityOnLink,this.individualPtVehicleOnLink);
		for(String s:this.timeBeans.keySet()) {
			Map<Id<TransitLink>,TransitLink>transitLinks=this.odPairs.getTransitLinks(s);
			this.transitLinks.get(s).putAll(this.odPairs.getTransitLinks(s));
			this.transitLinks.get(this.getNextTimeBean(s)).putAll(this.odPairs.getTransitLinks(s));//add all the links to the next time bean as well.
		}
		this.setFareCalculator(fareCalculator);
		this.consecutiveSUEErrorIncrease=0;
		
		for(String timeBeanId:this.getTimeBeans().keySet()) {
			
			this.getDemand().put(timeBeanId, new HashMap<>(this.getOdPairs().getdemand(timeBeanId)));
			for(Id<AnalyticalModelODpair> odId:this.getDemand().get(timeBeanId).keySet()) {
				double totalDemand=this.getDemand().get(timeBeanId).get(odId);
				this.getCarDemand().get(timeBeanId).put(odId, 0.5*totalDemand);
				
				AnalyticalModelODpair odpair=this.getOdPairs().getODpairset().get(odId);
				if(odpair.getSubPopulation().contains("GV")) {
					this.getCarDemand().get(timeBeanId).put(odId, totalDemand);
				}
				//System.out.println();
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
		System.out.println("Demand total = "+agentDemand);
		System.out.println("Total Agent Trips = "+agentTrip);
	
	}
	
	private LinkedHashMap<String,Double>generateSubPopSpecificParam(LinkedHashMap<String,Double>originalparams,String subPopName){
		LinkedHashMap<String,Double> specificParam=new LinkedHashMap<>();
		for(String s:originalparams.keySet()) {
			if((subPopName!=null && s.contains(subPopName))||s.contains("All")) {
				specificParam.put(s.split(" ")[1],originalparams.get(s));
			}
		}
		return specificParam;
	}
	
	@Override
	protected void loadAnalyticalModelInternalPamamsLimit() {
		this.getAnalyticalModelParamsLimit().put("LinkMiu", new Tuple<Double,Double>(0.004,0.008));
		this.getAnalyticalModelParamsLimit().put("ModeMiu", new Tuple<Double,Double>(0.01,0.1));
		this.getAnalyticalModelParamsLimit().put("BPRalpha", new Tuple<Double,Double>(0.10,0.20));
		this.getAnalyticalModelParamsLimit().put("BPRbeta", new Tuple<Double,Double>(3.,5.));
		this.getAnalyticalModelParamsLimit().put("Transferalpha", new Tuple<Double,Double>(0.25,0.75));
		this.getAnalyticalModelParamsLimit().put("Transferbeta", new Tuple<Double,Double>(0.75,1.5));
	}
	
	@Override
	protected Map<String,Map<Id<Link>,Double>> NetworkLoadingCarSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,double counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		String s=this.getOdPairs().getODpairset().get(ODpairId).getSubPopulation();
		LinkedHashMap<String,Double>newParam=null;
		//System.out.println();
		if(s!=null) {
			newParam=this.generateSubPopSpecificParam(params, s);
		}
		return super.NetworkLoadingCarSingleOD(ODpairId, timeBeanId, counter, newParam, anaParams);
	}
	
	@Override
	protected Map<String,Map<Id<TransitLink>,Double>> NetworkLoadingTransitSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,int counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		String s=this.getOdPairs().getODpairset().get(ODpairId).getSubPopulation();
		LinkedHashMap<String,Double>newParam=null;
		if(s!=null) {
			newParam=this.generateSubPopSpecificParam(params, s);
		}
		return super.NetworkLoadingTransitSingleOD(ODpairId, timeBeanId, counter, newParam, anaParams);
	}
	
	@Override
	protected void performModalSplit(LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams) {
		for(String timeBeanId:this.timeBeans.keySet()) {
			for(AnalyticalModelODpair odPair:this.getOdPairs().getODpairset().values()){
				//For GV car proportion is always 1
				if(odPair.getSubPopulation().contains("GV")) {
					double carDemand=this.getDemand().get(timeBeanId).get(odPair.getODpairId());
					this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),carDemand);
					continue;
					// if a phantom trip, car and pt proportion is decided from the simulation and will not be changed
				}else if(odPair.getSubPopulation().contains("trip")) {
					double carDemand=this.getDemand().get(timeBeanId).get(odPair.getODpairId())*odPair.getCarModalSplit();
					this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),carDemand);
					continue;
				}
				double demand=this.getDemand().get(timeBeanId).get(odPair.getODpairId());
				if(demand!=0) { 
					double carUtility=odPair.getExpectedMaximumCarUtility(params, anaParams, timeBeanId);
					double transitUtility=odPair.getExpectedMaximumTransitUtility(params, anaParams, timeBeanId);

					if(carUtility==Double.NEGATIVE_INFINITY||transitUtility==Double.POSITIVE_INFINITY||
							Math.exp(transitUtility*anaParams.get("ModeMiu"))==Double.POSITIVE_INFINITY) {
						this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), 0.0);

					}else if(transitUtility==Double.NEGATIVE_INFINITY||carUtility==Double.POSITIVE_INFINITY
							||Math.exp(carUtility*anaParams.get("ModeMiu"))==Double.POSITIVE_INFINITY) {
						this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), this.getDemand().get(timeBeanId).get(odPair.getODpairId()));
					}else if(carUtility==Double.NEGATIVE_INFINITY && transitUtility==Double.NEGATIVE_INFINITY){
						this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), 0.);
					}else {
						double carProportion=Math.exp(carUtility*anaParams.get("ModeMiu"))/(Math.exp(carUtility*anaParams.get("ModeMiu"))+Math.exp(transitUtility*anaParams.get("ModeMiu")));
						//System.out.println("Car Proportion = "+carProportion);
						Double cardemand=carProportion*this.getDemand().get(timeBeanId).get(odPair.getODpairId());
						if(cardemand==Double.NaN||cardemand==Double.POSITIVE_INFINITY||cardemand==Double.NEGATIVE_INFINITY) {
							throw new IllegalArgumentException("car demand is invalid");
						}
						this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),cardemand);
					}
				}
			}
		}
	}
	@Override
	public Map<String,Map<Id<Link>,Double>> performTransitVehicleOverlay(Map<String,AnalyticalModelNetwork> network, TransitSchedule schedule,Vehicles vehicles,
			LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams,boolean multiplieFactorOfSafety, boolean useConstTransferTime) {
		String s=null;
		LinkedHashMap<String,Double>newParam=params;
		newParam=this.generateSubPopSpecificParam(params, s);
		
		return super.performTransitVehicleOverlay(network, schedule, vehicles, newParam, anaParams, multiplieFactorOfSafety, useConstTransferTime);
	}
	@Override
	protected void collectTravelTime(LinkedHashMap<String,Double> params,LinkedHashMap<String,Double>anaParams) {
		String s=null;
		LinkedHashMap<String,Double>newParam=null;
		newParam=this.generateSubPopSpecificParam(params, s);
		super.collectTravelTime(newParam, anaParams);
	}
	
	@Override
	public Measurements perFormSUE(LinkedHashMap<String, Double> noparams,LinkedHashMap<String,Double> anaParams,Measurements originalMeasurements) {
		LinkedHashMap<String,Double>params=this.pReader.ScaleUp(noparams);
		return super.perFormSUE(params, anaParams,originalMeasurements);
	}
	
	@Override
	public void setDefaultParameters(LinkedHashMap<String,Double> params) {
		super.setDefaultParameters(this.pReader.ScaleUp(params));
	}
}
