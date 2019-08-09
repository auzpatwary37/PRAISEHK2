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
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;


/**
 * This is a multi-subPopulation implementation of the AnalyticalModel CNLSUEModel
 * 
 * @author h
 *
 */
public class CNLSUEModelSubPop extends CNLSUEModel{

	private ArrayList<String> subPopulationName=new ArrayList<>();
	private ParamReader pReader=new ParamReader("input/subPopParamAndLimit.csv");
	
	public CNLSUEModelSubPop(Map<String, Tuple<Double, Double>> timeBean,ArrayList<String> subPopName) {
		super(timeBean);
		this.subPopulationName=subPopName;
		super.setDefaultParameters(pReader.getDefaultParam());
	}
	
	public CNLSUEModelSubPop(Map<String, Tuple<Double, Double>> timeBean,ParamReader preader) {
		super(timeBean);
		this.subPopulationName=pReader.getSubPopulationName();
		this.pReader=preader;
		super.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
		this.setTollerance(0.1);
	}
	
	@Override
	public void generateRoutesAndOD(Population population,Network network,TransitSchedule transitSchedule,
			Scenario scenario,Map<String,FareCalculator> fareCalculator) {
		this.setLastPopulation(population);
		//System.out.println("");
		this.setOdPairs(new CNLODpairs(network,population,transitSchedule,scenario,this.getTimeBeans()));
//		Config odConfig=ConfigUtils.createConfig();
//		odConfig.network().setInputFile("data/odNetwork.xml");
		
		Network odNetwork=NetworkUtils.readNetwork("data/odNetwork.xml");
		this.getOdPairs().generateODpairsetSubPop(null);//This network has priority over the constructor network. This allows to use a od pair specific network 
		this.getOdPairs().generateRouteandLinkIncidence(0.);
		for(String s:this.getTimeBeans().keySet()) {
			this.getNetworks().put(s, new CNLNetwork(network));
			this.performTransitVehicleOverlay(this.getNetworks().get(s),
					transitSchedule,scenario.getTransitVehicles(),s);
			this.getTransitLinks().put(s,this.getOdPairs().getTransitLinks(s));
		}
		this.setFareCalculator(fareCalculator);
		
		
		
		
		this.setTs(transitSchedule);
		for(String timeBeanId:this.getTimeBeans().keySet()) {
			this.getConsecutiveSUEErrorIncrease().put(timeBeanId, 0.);
			this.getDemand().put(timeBeanId, new HashMap<>(this.getOdPairs().getdemand(timeBeanId)));
			for(Id<AnalyticalModelODpair> odId:this.getDemand().get(timeBeanId).keySet()) {
				double totalDemand=this.getDemand().get(timeBeanId).get(odId);
				this.getCarDemand().get(timeBeanId).put(odId, 0.5*totalDemand);
				if(this.getOdPairs().getODpairset().get(odId).getSubPopulation().contains("GV")) {
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
			if(s.contains(subPopName)) {
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
	protected HashMap<Id<Link>,Double> NetworkLoadingCarSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,double counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		String s=this.getOdPairs().getODpairset().get(ODpairId).getSubPopulation();
		LinkedHashMap<String,Double>newParam=params;
		if(s!=null) {
			newParam=this.generateSubPopSpecificParam(params, s);
		}
		return super.NetworkLoadingCarSingleOD(ODpairId, timeBeanId, counter, newParam, anaParams);
	}
	
	@Override
	protected HashMap<Id<TransitLink>,Double> NetworkLoadingTransitSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,int counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		String s=this.getOdPairs().getODpairset().get(ODpairId).getSubPopulation();
		LinkedHashMap<String,Double>newParam=params;
		if(s!=null) {
			newParam=this.generateSubPopSpecificParam(params, s);
		}
		return super.NetworkLoadingTransitSingleOD(ODpairId, timeBeanId, counter, newParam, anaParams);
	}
	
	@Override
	protected void performModalSplit(LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams,String timeBeanId) {
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
				Double cardemand=Math.exp(carUtility*anaParams.get("ModeMiu"))/(Math.exp(carUtility*anaParams.get("ModeMiu"))+Math.exp(transitUtility*anaParams.get("ModeMiu")))*this.getDemand().get(timeBeanId).get(odPair.getODpairId());
				if(cardemand==Double.NaN||cardemand==Double.POSITIVE_INFINITY||cardemand==Double.NEGATIVE_INFINITY) {
					throw new IllegalArgumentException("car demand is invalid");
				}
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),cardemand);
			}
		}
		}
	}
	
	
	
	@Override
	public SUEModelOutput perFormSUE(LinkedHashMap<String, Double> noparams,LinkedHashMap<String,Double> anaParams) {
		LinkedHashMap<String,Double>params=this.pReader.ScaleUp(noparams);
		return super.perFormSUE(params, anaParams);
	}
	
	@Override
	public void setDefaultParameters(LinkedHashMap<String,Double> params) {
		super.setDefaultParameters(this.pReader.ScaleUp(params));
	}
	
}
