package core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import analyticalModel.ODDifferentiableSUEModel;
import optimizer.VariableDetails;
import ust.hk.praisehk.metamodelcalibration.transit.fare.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.calibrator.ObjectiveCalculator;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.MTRLinkVolumeInfo;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class ODUtils {
	public static final String originaDestinationDemandVariableName = "ODDemand";
	public static final String originaDestinationSubPopSepcificDemandVariableName = "ODDemandSubPop";
	public static final String originaDestinationSubPopTimeSepcificDemandVariableName = "ODDemandSubPopTime";
	public static final String originDestinationTimeSpecificDemandVariableName = "ODDemandTime";
	public static final String OriginMultiplierVariableName = "originMultiplier";
	public static final String DestinationMultiplierVariableName = "destinationMultiplier";
	public static final String OriginDestinationMultiplierVariableName = "originDestinationMultipler";
	public static final String OriginDestinationSubPopMultiplierVariableName = "originDestinationSubPopMultipler";
	public static final String OriginMultiplierSubPopVaraibleName = "originSubPopMultiplier";
	public static final String DestinationMultiplierSubPopVaraibleName = "destinationSubPopMultiplier";
	public static final String originMultiplierTimeSpecificSubPopVariableName = "originSubTimeMultiplier";
	public static final String originMultiplierTimeSpecificVariableName = "originTimeMultiplier";
	public static final String destinationMultiplierTimeSpecificSubPopVariableName = "destinationSubTimeMultiplier";
	public static final String destinationMultiplierTimeSpecificVariableName = "destinationTimeMultiplier";
	public static final String origindestinationMultiplierTimeSpecificSubPopVariableName = "originDestinationSubTimeMultiplier";
	public static final String origindestinationMultiplierTimeSpecificVariableName = "originDestinationTimeMultiplier";
	public static final String extPlanAttributeName = "extPlans";
	public static final Set<String> keySets = new HashSet<>(Arrays.asList(
			originaDestinationDemandVariableName,
			originaDestinationSubPopSepcificDemandVariableName,
			originaDestinationSubPopTimeSepcificDemandVariableName,
			originDestinationTimeSpecificDemandVariableName,
			OriginMultiplierVariableName,
			DestinationMultiplierVariableName,
			OriginDestinationMultiplierVariableName,
			OriginDestinationSubPopMultiplierVariableName,
			OriginMultiplierSubPopVaraibleName,
			DestinationMultiplierSubPopVaraibleName,
			originMultiplierTimeSpecificSubPopVariableName,
			originMultiplierTimeSpecificVariableName,
			destinationMultiplierTimeSpecificSubPopVariableName,
			destinationMultiplierTimeSpecificVariableName,
			origindestinationMultiplierTimeSpecificSubPopVariableName,
			origindestinationMultiplierTimeSpecificVariableName));
	
	//TODO: take subpopulation as input
	public static String createODMultiplierVariableName(Id<AnalyticalModelODpair> odPairId,String subPopulationName, String type, String timeBeanId) {
		String[] part = odPairId.toString().split("_");
		String originId = part[0];
		String destinationId = part[1];
		String subPopulation = subPopulationName;
		switch (type) {
		case OriginMultiplierVariableName:
			return OriginMultiplierVariableName+"___"+originId;
		case DestinationMultiplierVariableName:
			return DestinationMultiplierVariableName+"___"+destinationId;
		case OriginDestinationMultiplierVariableName:
			return OriginDestinationMultiplierVariableName+"___"+originId+"___"+destinationId;
		case OriginDestinationSubPopMultiplierVariableName:
			return OriginDestinationSubPopMultiplierVariableName+"___"+originId+"___"+destinationId+"___"+subPopulation;
		case OriginMultiplierSubPopVaraibleName:
			return OriginMultiplierSubPopVaraibleName+"___"+originId+"___"+subPopulation;
		case DestinationMultiplierSubPopVaraibleName:
			return DestinationMultiplierSubPopVaraibleName+"___"+destinationId+"___"+subPopulation;
		case originMultiplierTimeSpecificSubPopVariableName:
			return originMultiplierTimeSpecificSubPopVariableName+"___"+originId+"___"+subPopulation+"___"+timeBeanId;
		case destinationMultiplierTimeSpecificSubPopVariableName:
			return destinationMultiplierTimeSpecificSubPopVariableName+"___"+destinationId+"___"+subPopulation+"___"+timeBeanId;
		case origindestinationMultiplierTimeSpecificSubPopVariableName:
			return origindestinationMultiplierTimeSpecificSubPopVariableName+"___"+originId+"___"+destinationId+"___"+subPopulation+"___"+timeBeanId;
		case originaDestinationDemandVariableName:
			return originaDestinationDemandVariableName+"___"+originId+"___"+destinationId;
		case originaDestinationSubPopSepcificDemandVariableName:
			return originaDestinationSubPopSepcificDemandVariableName+"___"+originId+"___"+destinationId+"___"+subPopulation;
		case originaDestinationSubPopTimeSepcificDemandVariableName:
			return originaDestinationSubPopTimeSepcificDemandVariableName+"___"+originId+"___"+destinationId+"___"+subPopulation+"___"+timeBeanId;
		case originDestinationTimeSpecificDemandVariableName:
			return originDestinationTimeSpecificDemandVariableName+"___"+originId+"___"+destinationId+"___"+timeBeanId;
		case originMultiplierTimeSpecificVariableName:
			return originMultiplierTimeSpecificVariableName+"___"+originId+"___"+timeBeanId;
		case destinationMultiplierTimeSpecificVariableName:
			return destinationMultiplierTimeSpecificVariableName+"___"+destinationId+"___"+timeBeanId;
		case origindestinationMultiplierTimeSpecificVariableName:
			return origindestinationMultiplierTimeSpecificVariableName+"___"+originId+"___"+destinationId+"___"+timeBeanId;
		default:
			throw new IllegalArgumentException("Input type: "+type+ "not recognized. Please use the static final string keys in the ODUtils class only.");	
		}
		
	}
	
	public static String createODMultiplierVariableName(String odPairId,String subPopulationName, String type, String timeBeanId) {
		String[] part = odPairId.toString().split("_");
		String originId = part[0];
		String destinationId = part[1];
		String subPopulation = subPopulationName;
		switch (type) {
		case OriginMultiplierVariableName:
			return OriginMultiplierVariableName+"___"+originId;
		case DestinationMultiplierVariableName:
			return DestinationMultiplierVariableName+"___"+destinationId;
		case OriginDestinationMultiplierVariableName:
			return OriginDestinationMultiplierVariableName+"___"+originId+"___"+destinationId;
		case OriginDestinationSubPopMultiplierVariableName:
			return OriginDestinationSubPopMultiplierVariableName+"___"+originId+"___"+destinationId+"___"+subPopulation;
		case OriginMultiplierSubPopVaraibleName:
			return OriginMultiplierSubPopVaraibleName+"___"+originId+"___"+subPopulation;
		case DestinationMultiplierSubPopVaraibleName:
			return DestinationMultiplierSubPopVaraibleName+"___"+destinationId+"___"+subPopulation;
		case originMultiplierTimeSpecificSubPopVariableName:
			return originMultiplierTimeSpecificSubPopVariableName+"___"+originId+"___"+subPopulation+"___"+timeBeanId;
		case destinationMultiplierTimeSpecificSubPopVariableName:
			return destinationMultiplierTimeSpecificSubPopVariableName+"___"+destinationId+"___"+subPopulation+"___"+timeBeanId;
		case origindestinationMultiplierTimeSpecificSubPopVariableName:
			return origindestinationMultiplierTimeSpecificSubPopVariableName+"___"+originId+"___"+destinationId+"___"+subPopulation+"___"+timeBeanId;
		case originaDestinationDemandVariableName:
			return originaDestinationDemandVariableName+"___"+originId+"___"+destinationId;
		case originaDestinationSubPopSepcificDemandVariableName:
			return originaDestinationSubPopSepcificDemandVariableName+"___"+originId+"___"+destinationId+"___"+subPopulation;
		case originaDestinationSubPopTimeSepcificDemandVariableName:
			return originaDestinationSubPopTimeSepcificDemandVariableName+"___"+originId+"___"+destinationId+"___"+subPopulation+"___"+timeBeanId;
		case originDestinationTimeSpecificDemandVariableName:
			return originDestinationTimeSpecificDemandVariableName+"___"+originId+"___"+destinationId+"___"+timeBeanId;
		case originMultiplierTimeSpecificVariableName:
			return originMultiplierTimeSpecificVariableName+"___"+originId+"___"+timeBeanId;
		case destinationMultiplierTimeSpecificVariableName:
			return destinationMultiplierTimeSpecificVariableName+"___"+destinationId+"___"+timeBeanId;
		case origindestinationMultiplierTimeSpecificVariableName:
			return origindestinationMultiplierTimeSpecificVariableName+"___"+originId+"___"+destinationId+"___"+timeBeanId;
		default:
			throw new IllegalArgumentException("Input type: "+type+ "not recognized. Please use the static final string keys in the ODUtils class only.");	
		}
		
	}
	
	public static Map<String,Map<Id<AnalyticalModelODpair>,Double>> applyODPairMultiplier(
			Map<String,Map<Id<AnalyticalModelODpair>,Double>> inputODPairMap, LinkedHashMap<String,Double> variables, 
			Map<Id<AnalyticalModelODpair>,AnalyticalModelODpair>odPairs){
		
		Map<String,Map<Id<AnalyticalModelODpair>,Double>> outDemand = new HashMap<>();
		for(Entry<String, Map<Id<AnalyticalModelODpair>, Double>> timeDemand: inputODPairMap.entrySet()) {
			outDemand.put(timeDemand.getKey(), new HashMap<>()); //Loop through the OD pair map
			Map<Id<AnalyticalModelODpair>, Double> outTimeDemand = outDemand.get(timeDemand.getKey());
			
			for(Entry<Id<AnalyticalModelODpair>, Double> od:timeDemand.getValue().entrySet()) {
			//timeDemand.getValue().entrySet().parallelStream().forEach(od-> {
				Double m = 1.;
				outTimeDemand.put(od.getKey(), od.getValue()); // Initialize the outTime demand based on past demand
				if((m = variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),OriginMultiplierVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),DestinationMultiplierVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),OriginDestinationMultiplierVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),OriginDestinationSubPopMultiplierVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),OriginMultiplierSubPopVaraibleName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),DestinationMultiplierSubPopVaraibleName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),originMultiplierTimeSpecificSubPopVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),destinationMultiplierTimeSpecificSubPopVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),origindestinationMultiplierTimeSpecificSubPopVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),originaDestinationDemandVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),originaDestinationSubPopSepcificDemandVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),originaDestinationSubPopTimeSepcificDemandVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),originDestinationTimeSpecificDemandVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),originMultiplierTimeSpecificVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),destinationMultiplierTimeSpecificVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
				if((m =variables.get(createODMultiplierVariableName(od.getKey(),odPairs.get(od.getKey()).getSubPopulation(),origindestinationMultiplierTimeSpecificVariableName,timeDemand.getKey())))!=null) {
					outTimeDemand.put(od.getKey(), outTimeDemand.get(od.getKey())*m);
				}
		//	});
		
			}
		}
		return outDemand;
	}
	
	/**
	 * This function applies the OD multiplier with the specific demand addition (negative for reduction) of the model.
	 * @param inputODPairMap OD multipliers
	 * @param variables
	 * @param odPairs
	 * @param addition The OD addition map
	 * @return
	 */
	public static Map<String,Map<Id<AnalyticalModelODpair>,Double>> applyODPairMultiplierAndAddition(
			Map<String,Map<Id<AnalyticalModelODpair>,Double>> inputODPairMap,LinkedHashMap<String,Double> variables, 
			Map<Id<AnalyticalModelODpair>,AnalyticalModelODpair>odPairs, 
			Map<String,Map<Id<AnalyticalModelODpair>,Double>> addition, boolean ignoreNegative){
		
		Map<String,Map<Id<AnalyticalModelODpair>,Double>> outDemand = applyODPairMultiplier(inputODPairMap,variables, odPairs);
		for(Entry<String, Map<Id<AnalyticalModelODpair>, Double>> timeDemand: addition.entrySet()) {
			for(Entry<Id<AnalyticalModelODpair>, Double> od:timeDemand.getValue().entrySet()) {
				Map<Id<AnalyticalModelODpair>, Double> outTimeDemand = outDemand.get(timeDemand.getKey());
				if(!outTimeDemand.containsKey(od.getKey())) { //If this sample doesn't contain the key, let it continue
					continue;
				}
				double demand = outTimeDemand.get(od.getKey()) ;
				if(addition.get(timeDemand.getKey()).containsKey(od.getKey())){
					demand += addition.get(timeDemand.getKey()).get(od.getKey());
				}
				if(demand >= 0) {
					outTimeDemand.put(od.getKey(), demand);
				}else {
					if(ignoreNegative) demand = 0;
					else addition.get(timeDemand.getKey()).put(od.getKey(), -outTimeDemand.get(od.getKey())); //Adjust the addition when it goes wrong.
//					throw new RuntimeException("The demand became negative! Check please.");
				}
			}
		}
		return outDemand;
	}
	
	public static Double getMultiplier(String odId, String subPopulation,String timeId, Map<String,Double>variables) {
				Double m = 1.;
				double mm = 1.0;
				if((m = variables.get(createODMultiplierVariableName(odId,subPopulation,OriginMultiplierVariableName,timeId)))!=null) {
					mm = mm*m;
				}
				
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,DestinationMultiplierVariableName,timeId)))!=null) {
					mm = mm*m;				}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,OriginDestinationMultiplierVariableName,timeId)))!=null) {
					mm = mm*m;				}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,OriginDestinationSubPopMultiplierVariableName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,OriginMultiplierSubPopVaraibleName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,DestinationMultiplierSubPopVaraibleName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,originMultiplierTimeSpecificSubPopVariableName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,destinationMultiplierTimeSpecificSubPopVariableName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,origindestinationMultiplierTimeSpecificSubPopVariableName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,originaDestinationDemandVariableName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,originaDestinationSubPopSepcificDemandVariableName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,originaDestinationSubPopTimeSepcificDemandVariableName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,originDestinationTimeSpecificDemandVariableName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,originMultiplierTimeSpecificVariableName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,destinationMultiplierTimeSpecificVariableName,timeId)))!=null) {
					mm = mm*m;						}
				if((m =variables.get(createODMultiplierVariableName(odId,subPopulation,origindestinationMultiplierTimeSpecificVariableName,timeId)))!=null) {
					mm = mm*m;						}
		//	});
		
			
		return mm;
	}
	
	/**
	 * Extract only the od related keys from a set of parameter keys
	 * @param inputVarKeys
	 * @return
	 */
	public static Set<String> extractODVarKeys(Set<String> inputVarKeys){
		Set<String> varKeys = new HashSet<>();
		for(String s:inputVarKeys) {
			for(String aKeys:keySets) {
				if(s.contains(aKeys)) {
					varKeys.add(s);
					break;
				}
			}
		}
		return varKeys;	
	}
	
	/**
	 * check if the var key belongs to the 
	 * multiply this with the demand and divide by the current variable value and then multiply with pr*pm
	 * @param odId
	 * @param timeBeanId
	 * @param varKey
	 * @return
	 */
	public static int ifMatch_1_else_0(Id<AnalyticalModelODpair> odId,String subPopulationName, String timeBeanId,String varKey) {
		if(createODMultiplierVariableName(odId,subPopulationName, varKey.split("___")[0],timeBeanId).equals(varKey)) {
			return 1;
		}
		return 0;
	}
	
	
	public static double calcODObjective(Measurements realMeasurements, Measurements modelMeasurements) {
		return ObjectiveCalculator.calcObjective(realMeasurements, modelMeasurements, ObjectiveCalculator.TypeMeasurementAndTimeSpecific)*0.5;
	}
	
	/**
	 * The only way to parallelize it is to make variable parallelized
	 * TODO: try that
	 * @param realMeasurements
	 * @param modelMeasurements
	 * @param model
	 * @return
	 */
//	public static Map<String, Double> calcODObjectiveGradient(Measurements realMeasurements, Measurements modelMeasurements, ODDifferentiableSUEModel model) {
//		Map<String,Double> outGrad = model.getGradientKeys().stream().collect(Collectors.toMap(kk->kk, kk->0.));
//		realMeasurements.getMeasurements().values().forEach(m->{
//			m.getVolumes().entrySet().forEach(timeId->{
//				double delta = modelMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeId.getKey()) - timeId.getValue();
//				MeasurementType type = m.getMeasurementType();
//				Map<String,Double> grad = new HashMap<>();
//				if(type.equals(MeasurementType.linkVolume)) {
//					List<Id<Link>>links = (List<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName);
//					for(Id<Link>l:links) {
//						//for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//						for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//							grad.compute(gd.getKey(), (k,v)->v==null?gd.getValue()*delta:v+gd.getValue()*delta);
//						}
//					}
//					//grad = model.getLinkGradient().get(timeId.getKey()).get()
//				}else if(type.equals(MeasurementType.fareLinkVolume)) {
//					grad = model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName));
//					for(String k:grad.keySet())grad.put(k,grad.get(k)*delta);
//				}
//				for(String var:model.getGradientKeys()) {
//					outGrad.put(var, outGrad.get(var)+grad.get(var));
//				}
//			});
//		});
//		return outGrad;
//	}
//	
	
	
	public static Map<String, Double> calcODObjectiveGradient(Measurements realMeasurements, Measurements modelMeasurements, ODDifferentiableSUEModel model) {
		//Map<String,Double> outGrad = model.getGradientKeys().stream().collect(Collectors.toMap(kk->kk, kk->0.));
		RealVector outGrad = MatrixUtils.createRealVector(new double[model.getGradientKeys().size()]);
		for(Measurement m:realMeasurements.getMeasurements().values()){
			for(Entry<String, Double> timeId:m.getVolumes().entrySet()){
				double delta = modelMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeId.getKey()) - timeId.getValue();
				MeasurementType type = m.getMeasurementType();
				//Map<String,Double> grad = new HashMap<>();
				RealVector g = MatrixUtils.createRealVector(new double[outGrad.toArray().length]);
				if(type.equals(MeasurementType.linkVolume)) {
					List<Id<Link>>links = (List<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName);
					for(Id<Link>l:links) {
						//for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//						for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//							grad.compute(gd.getKey(), (k,v)->v==null?gd.getValue()*delta:v+gd.getValue()*delta);
//						}
						if(model.getLinkGradient().get(timeId.getKey()).get(l)!=null) {
							g = g.add(MatrixUtils.createRealVector(model.getLinkGradient().get(timeId.getKey()).get(l)).mapMultiplyToSelf(delta));
						}
					}
					//grad = model.getLinkGradient().get(timeId.getKey()).get()
				}else if(type.equals(MeasurementType.fareLinkVolume)) {
//					grad = model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName));
//					for(String k:grad.keySet())grad.put(k,grad.get(k)*delta);
					g = g.add(MatrixUtils.createRealVector(model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName))).mapMultiplyToSelf(delta));
				}else if(type.equals(MeasurementType.fareLinkVolumeCluster)) {
					List<FareLink> fareLinks = (List<FareLink>) m.getAttribute(Measurement.FareLinkClusterAttributeName);
					for(FareLink fl:fareLinks) {
						if(model.getFareLinkGradient().get(timeId.getKey()).get(fl.toString())!=null) {
						g = g.add(MatrixUtils.createRealVector(model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName))).mapMultiplyToSelf(delta));;
						
						}
					}
				}else if(type.equals(MeasurementType.TransitPhysicalLinkVolume)) {
					List<MTRLinkVolumeInfo> linkVolumeInfo = (List<MTRLinkVolumeInfo>) m.getAttribute(Measurement.MTRLineRouteStopLinkInfosName);
					for(MTRLinkVolumeInfo info: linkVolumeInfo) {
						g = g.add(MatrixUtils.createRealVector(model.getTrPassengerOnPhysicalLinkGradient().get(timeId).get(info.linkId).get(CNLTransitDirectLink.calcLineRouteId(info.lineId.toString(), info.routeId.toString()))));
					}
				}
//				for(String var:model.getGradientKeys()) {
//					outGrad.put(var, outGrad.get(var)+grad.get(var));
//				}
				Double sigma = m.getSD().get(timeId.getKey());
				if(sigma == null)sigma = 0.;
				g = g.mapMultiplyToSelf(1/(1+sigma));
				outGrad = outGrad.add(g);
			}
		}
		return model.getGradientArray().getMap(outGrad.toArray());
	}
	
	public static Map<String, Double> calcODGEHObjectiveGradient(Measurements realMeasurements, Measurements modelMeasurements, ODDifferentiableSUEModel model) {
		//Map<String,Double> outGrad = model.getGradientKeys().stream().collect(Collectors.toMap(kk->kk, kk->0.));
		RealVector outGrad = MatrixUtils.createRealVector(new double[model.getGradientKeys().size()]);
		for(Measurement m:realMeasurements.getMeasurements().values()){
			for(Entry<String, Double> timeId:m.getVolumes().entrySet()){
				double delta = modelMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeId.getKey()) - timeId.getValue();
				double deltaSum = modelMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeId.getKey()) + timeId.getValue();
				MeasurementType type = m.getMeasurementType();
				//Map<String,Double> grad = new HashMap<>();
				RealVector g = MatrixUtils.createRealVector(new double[outGrad.toArray().length]);
				if(type.equals(MeasurementType.linkVolume)) {
					List<Id<Link>>links = (List<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName);
					for(Id<Link>l:links) {
						//for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//						for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//							grad.compute(gd.getKey(), (k,v)->v==null?gd.getValue()*delta:v+gd.getValue()*delta);
//						}
						if(model.getLinkGradient().get(timeId.getKey()).get(l)!=null) {
							g = g.add(MatrixUtils.createRealVector(model.getLinkGradient().get(timeId.getKey()).get(l)).mapMultiplyToSelf(delta));
						}
					}
					//grad = model.getLinkGradient().get(timeId.getKey()).get()
				}else if(type.equals(MeasurementType.fareLinkVolume)) {
//					grad = model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName));
//					for(String k:grad.keySet())grad.put(k,grad.get(k)*delta);
					g = g.add(MatrixUtils.createRealVector(model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName))).mapMultiplyToSelf(delta));
				}else if(type.equals(MeasurementType.fareLinkVolumeCluster)) {
					List<FareLink> fareLinks = (List<FareLink>) m.getAttribute(Measurement.FareLinkClusterAttributeName);
					for(FareLink fl:fareLinks) {
						if(model.getFareLinkGradient().get(timeId.getKey()).get(fl.toString())!=null) {
						g = g.add(MatrixUtils.createRealVector(model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName))).mapMultiplyToSelf(delta));;
						
						}
					}
				}else if(type.equals(MeasurementType.TransitPhysicalLinkVolume)) {
					List<MTRLinkVolumeInfo> linkVolumeInfo = (List<MTRLinkVolumeInfo>) m.getAttribute(Measurement.MTRLineRouteStopLinkInfosName);
					for(MTRLinkVolumeInfo info: linkVolumeInfo) {
						g = g.add(MatrixUtils.createRealVector(model.getTrPassengerOnPhysicalLinkGradient().get(timeId).get(info.linkId).get(CNLTransitDirectLink.calcLineRouteId(info.lineId.toString(), info.routeId.toString()))));
					}
				}
//				for(String var:model.getGradientKeys()) {
//					outGrad.put(var, outGrad.get(var)+grad.get(var));
//				}
				Double sigma = m.getSD().get(timeId.getKey());
				if(sigma == null)sigma = 0.;
				double gehMult = 4*timeId.getValue()*timeId.getValue()/((1+sigma*sigma)*deltaSum*deltaSum);
				if(!Double.isNaN(gehMult) && Double.isFinite(gehMult)) {
					g = g.mapMultiplyToSelf(gehMult);
				}
				outGrad = outGrad.add(g);
			}
		}
		return model.getGradientArray().getMap(outGrad.toArray());
	}
	/**
	 * 
	 * @param realMeasurements
	 * @param modelMeasurements this should be the metamodel measurement rather than the model measurement
	 * @param model
	 * @param metamodels
	 * @return
	 */
	public static Map<String, Double> calcMetamodelODObjectiveGradient(Measurements realMeasurements, Measurements modelMeasurements, ODDifferentiableSUEModel model,Map<Id<Measurement>,Map<String,MetaModel>> metamodels, boolean takeMetaModelGrad) {
		//Map<String,Double> outGrad = model.getGradientKeys().stream().collect(Collectors.toMap(kk->kk, kk->0.));
		RealVector outGrad = MatrixUtils.createRealVector(new double[model.getGradientKeys().size()]);
		for(Measurement m:realMeasurements.getMeasurements().values()){
			for(Entry<String, Double> timeId:m.getVolumes().entrySet()){
				double real = timeId.getValue();
				double modelM = modelMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeId.getKey());
				double delta = modelM - real;
				double[] metamodelGrad = new double[model.getGradientKeys().size()];
				double gradMultiplier = 1;
				if(metamodels!=null && takeMetaModelGrad==true) {
					metamodelGrad = metamodels.get(m.getId()).get(timeId.getKey()).getGradientVector();
				
					gradMultiplier = metamodels.get(m.getId()).get(timeId.getKey()).getanaGradMultiplier();
				}
//				if(takeMetaModelGrad==false) {
//					gradMultiplier = 1;
//					metamodelGrad = new double[metamodelGrad.length];
//				}
				MeasurementType type = m.getMeasurementType();
				//Map<String,Double> grad = new HashMap<>();
				RealVector g = MatrixUtils.createRealVector(new double[outGrad.toArray().length]);
				if(type.equals(MeasurementType.linkVolume)) {
					List<Id<Link>>links = (List<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName);
					for(Id<Link>l:links) {
						//for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//						for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//							grad.compute(gd.getKey(), (k,v)->v==null?gd.getValue()*delta:v+gd.getValue()*delta);
//						}
						if(model.getLinkGradient().get(timeId.getKey()).get(l)!=null) {
							g = g.add(MatrixUtils.createRealVector(model.getLinkGradient().get(timeId.getKey()).get(l)).mapMultiplyToSelf(delta)).mapMultiplyToSelf(gradMultiplier);
							
						}
					}
					
					//grad = model.getLinkGradient().get(timeId.getKey()).get()
				}else if(type.equals(MeasurementType.fareLinkVolume)) {
//					grad = model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName));
//					for(String k:grad.keySet())grad.put(k,grad.get(k)*delta);
					g = g.add(MatrixUtils.createRealVector(model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName))).mapMultiplyToSelf(delta)).mapMultiplyToSelf(gradMultiplier);
				}else if(type.equals(MeasurementType.fareLinkVolumeCluster)) {
					List<FareLink> fareLinks = (List<FareLink>) m.getAttribute(Measurement.FareLinkClusterAttributeName);
					for(FareLink fl:fareLinks) {
						if(model.getFareLinkGradient().get(timeId.getKey()).get(fl.toString())!=null) {
							g = g.add(MatrixUtils.createRealVector(model.getFareLinkGradient().get(timeId.getKey()).get(fl.toString())).mapMultiplyToSelf(delta).mapMultiplyToSelf(gradMultiplier));
						}
					}
				}else if(type.equals(MeasurementType.TransitPhysicalLinkVolume)) {
					List<MTRLinkVolumeInfo> linkVolumeInfo = (List<MTRLinkVolumeInfo>) m.getAttribute(Measurement.MTRLineRouteStopLinkInfosName);
					for(MTRLinkVolumeInfo info: linkVolumeInfo) {
						if(model.getTrPassengerOnPhysicalLinkGradient().get(timeId.getKey()).get(info.linkId).get(CNLTransitDirectLink.calcLineRouteId(info.lineId.toString(), info.routeId.toString()))!=null) {
							g = g.add(MatrixUtils.createRealVector(model.getTrPassengerOnPhysicalLinkGradient().get(timeId.getKey()).get(info.linkId).get(CNLTransitDirectLink.calcLineRouteId(info.lineId.toString(), info.routeId.toString()))));
						}
					}
				}
//				for(String var:model.getGradientKeys()) {
//					outGrad.put(var, outGrad.get(var)+grad.get(var));
//				}
				g = g.add(MatrixUtils.createRealVector(metamodelGrad).mapMultiply(delta));
				if(g.isNaN()|| g.isInfinite()) {
					System.out.println("Debug Here!!!");
				}
				
				Double sigma = m.getSD().get(timeId.getKey());
				if(sigma == null)sigma = 0.;
				g = g.mapMultiplyToSelf(1/(1+sigma*sigma));
				outGrad = outGrad.add(g);
				if(outGrad.isNaN()||g.isInfinite()) {
					System.out.println("Debug!!!");
				}
				//outGrad = outGrad.add(g);
			}
		}
		return model.getGradientArray().getMap(outGrad.toArray());
	}
	
	
	public static Map<String, Double> calcMetamodelGEHODObjectiveGradient(Measurements realMeasurements, Measurements modelMeasurements, ODDifferentiableSUEModel model,Map<Id<Measurement>,Map<String,MetaModel>> metamodels, boolean takeMetaModelGrad) {
		//Map<String,Double> outGrad = model.getGradientKeys().stream().collect(Collectors.toMap(kk->kk, kk->0.));
		RealVector outGrad = MatrixUtils.createRealVector(new double[model.getGradientKeys().size()]);
		for(Measurement m:realMeasurements.getMeasurements().values()){
			for(Entry<String, Double> timeId:m.getVolumes().entrySet()){
				double real = timeId.getValue();
				double modelM = modelMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeId.getKey());
				double delta = modelM - real;
				double deltaSum = modelM+real;
				double[] metamodelGrad = new double[model.getGradientKeys().size()];
				double gradMultiplier = 1;
				if(metamodels!=null && takeMetaModelGrad==true) {
					metamodelGrad = metamodels.get(m.getId()).get(timeId.getKey()).getGradientVector();
				
					gradMultiplier = metamodels.get(m.getId()).get(timeId.getKey()).getanaGradMultiplier();
				}
//				if(takeMetaModelGrad==false) {
//					gradMultiplier = 1;
//					metamodelGrad = new double[metamodelGrad.length];
//				}
				MeasurementType type = m.getMeasurementType();
				//Map<String,Double> grad = new HashMap<>();
				RealVector g = MatrixUtils.createRealVector(new double[outGrad.toArray().length]);
				if(type.equals(MeasurementType.linkVolume)) {
					List<Id<Link>>links = (List<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName);
					for(Id<Link>l:links) {
						//for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//						for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//							grad.compute(gd.getKey(), (k,v)->v==null?gd.getValue()*delta:v+gd.getValue()*delta);
//						}
						if(model.getLinkGradient().get(timeId.getKey()).get(l)!=null) {
							g = g.add(MatrixUtils.createRealVector(model.getLinkGradient().get(timeId.getKey()).get(l)).mapMultiplyToSelf(delta)).mapMultiplyToSelf(gradMultiplier);
							
						}
					}
					
					//grad = model.getLinkGradient().get(timeId.getKey()).get()
				}else if(type.equals(MeasurementType.fareLinkVolume)) {
//					grad = model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName));
//					for(String k:grad.keySet())grad.put(k,grad.get(k)*delta);
					g = g.add(MatrixUtils.createRealVector(model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName))).mapMultiplyToSelf(delta)).mapMultiplyToSelf(gradMultiplier);
				}else if(type.equals(MeasurementType.fareLinkVolumeCluster)) {
					List<FareLink> fareLinks = (List<FareLink>) m.getAttribute(Measurement.FareLinkClusterAttributeName);
					for(FareLink fl:fareLinks) {
						if(model.getFareLinkGradient().get(timeId.getKey()).get(fl.toString())!=null) {
							g = g.add(MatrixUtils.createRealVector(model.getFareLinkGradient().get(timeId.getKey()).get(fl.toString())).mapMultiplyToSelf(delta).mapMultiplyToSelf(gradMultiplier));
						}
					}
				}else if(type.equals(MeasurementType.TransitPhysicalLinkVolume)) {
					List<MTRLinkVolumeInfo> linkVolumeInfo = (List<MTRLinkVolumeInfo>) m.getAttribute(Measurement.MTRLineRouteStopLinkInfosName);
					for(MTRLinkVolumeInfo info: linkVolumeInfo) {
						g = g.add(MatrixUtils.createRealVector(model.getTrPassengerOnPhysicalLinkGradient().get(timeId).get(info.linkId).get(CNLTransitDirectLink.calcLineRouteId(info.lineId.toString(), info.routeId.toString()))));
					}
				}
//				for(String var:model.getGradientKeys()) {
//					outGrad.put(var, outGrad.get(var)+grad.get(var));
//				}
				g = g.add(MatrixUtils.createRealVector(metamodelGrad).mapMultiply(delta));
				if(g.isNaN()|| g.isInfinite()) {
					System.out.println("Debug Here!!!");
				}
				Double sigma = m.getSD().get(timeId.getKey());
				if(sigma == null)sigma = 0.;
				double gehMult = 4*timeId.getValue()*timeId.getValue()/((1+sigma*sigma)*deltaSum*deltaSum);
				if(!Double.isNaN(gehMult) && Double.isFinite(gehMult)) {
					g = g.mapMultiplyToSelf(gehMult);
				}
				outGrad = outGrad.add(g);
			}
		}
		return model.getGradientArray().getMap(outGrad.toArray());
	}
	
	/**
	 * The only way to parallelize it is to make variable parallelized
	 * TODO: try that
	 * @param realMeasurements
	 * @param modelMeasurements
	 * @param model
	 * @return
	 */
//	public static Map<String, Double> calcODObjectiveGradient(Measurements realMeasurements, Map<String,VariableDetails> variables, ODDifferentiableSUEModel model) {
//		LinkedHashMap<String,Double> params = new LinkedHashMap<>();
//		variables.values().stream().forEach(v->params.put(v.getVariableName(), v.getCurrentValue()));
//		Measurements modelMeasurements = model.perFormSUE(params, realMeasurements);
//		Map<String,Double> outGrad = model.getGradientKeys().stream().collect(Collectors.toMap(kk->kk, kk->0.));
//		realMeasurements.getMeasurements().values().forEach(m->{
//			m.getVolumes().entrySet().forEach(timeId->{
//				double delta = modelMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeId.getKey()) - timeId.getValue();
//				MeasurementType type = m.getMeasurementType();
//				Map<String,Double> grad = new HashMap<>();
//				if(type.equals(MeasurementType.linkVolume)) {
//					List<Id<Link>>links = (List<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName);
//					for(Id<Link>l:links) {
//						for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//							grad.compute(gd.getKey(), (k,v)->v==null?gd.getValue()*delta:v+gd.getValue()*delta);
//						}
//					}
//					//grad = model.getLinkGradient().get(timeId.getKey()).get()
//				}else if(type.equals(MeasurementType.fareLinkVolume)) {
//					grad = model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName));
//					for(String k:grad.keySet())grad.put(k,grad.get(k)*delta);
//				}
//				for(String var:model.getGradientKeys()) {
//					outGrad.put(var, outGrad.get(var)+grad.get(var));
//				}
//			});
//		});
//		return outGrad;
//	}
	
	
	public static Map<String, Double> calcODObjectiveGradient(Measurements realMeasurements, Map<String,VariableDetails> variables, ODDifferentiableSUEModel model) {
		LinkedHashMap<String,Double> params = new LinkedHashMap<>();
		variables.values().stream().forEach(v->params.put(v.getVariableName(), v.getCurrentValue()));
		Measurements modelMeasurements = model.perFormSUE(params, realMeasurements);
		return ODUtils.calcODObjectiveGradient(realMeasurements, modelMeasurements, model);
//		Map<String,Double> outGrad = model.getGradientKeys().stream().collect(Collectors.toMap(kk->kk, kk->0.));
//		realMeasurements.getMeasurements().values().forEach(m->{
//			m.getVolumes().entrySet().forEach(timeId->{
//				double delta = modelMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeId.getKey()) - timeId.getValue();
//				MeasurementType type = m.getMeasurementType();
//				Map<String,Double> grad = new HashMap<>();
//				if(type.equals(MeasurementType.linkVolume)) {
//					List<Id<Link>>links = (List<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName);
//					for(Id<Link>l:links) {
//						for(Entry<String, Double> gd:model.getLinkGradient().get(timeId.getKey()).get(l).entrySet()){
//							grad.compute(gd.getKey(), (k,v)->v==null?gd.getValue()*delta:v+gd.getValue()*delta);
//						}
//					}
//					//grad = model.getLinkGradient().get(timeId.getKey()).get()
//				}else if(type.equals(MeasurementType.fareLinkVolume)) {
//					grad = model.getFareLinkGradient().get(timeId.getKey()).get(m.getAttribute(Measurement.FareLinkAttributeName));
//					for(String k:grad.keySet())grad.put(k,grad.get(k)*delta);
//				}
//				for(String var:model.getGradientKeys()) {
//					outGrad.put(var, outGrad.get(var)+grad.get(var));
//				}
//			});
//		});
//		return outGrad;
	}
	public static String getTimeId(double time, Map<String,Tuple<Double,Double>>timeBeans) {
		if(time>24*3600) {
			time=time-24*3600;
		}
		String timeId=null;
		for(Entry<String, Tuple<Double, Double>> timeBean:timeBeans.entrySet()) {
			if(time>timeBean.getValue().getFirst() && time<=timeBean.getValue().getSecond()) {
				timeId=timeBean.getKey();
			}
		}
		return timeId;
	}
	public static void main(String[] args) {
		RealVector g = MatrixUtils.createRealVector(new double[] {1, 2, 3});
		g = g.add(g);
		double[] c = g.toArray();
		c = g.add(g).toArray();
		System.out.println(g);
		System.out.println(c);
	}
	
	public static Map<String,Measurements> timeSplitMeasurements(Measurements m){
		Map<String,Measurements> mOuts=new HashMap<>();
		for(String timeId:m.getTimeBean().keySet()) {
			Map<String,Tuple<Double,Double>> singleBeanTimeBean=new HashMap<>();
			singleBeanTimeBean.put(timeId, m.getTimeBean().get(timeId));
			Measurements mt=Measurements.createMeasurements(singleBeanTimeBean);
			mOuts.put(timeId, mt);
			for(Entry<Id<Measurement>, Measurement> d:m.getMeasurements().entrySet()) {
				if(d.getValue().getVolumes().containsKey(timeId)) {
					mt.createAnadAddMeasurement(d.getKey().toString(), d.getValue().getMeasurementType());
					for(Entry<String, Object> attribue:d.getValue().getAttributes().entrySet()) {
						mt.getMeasurements().get(d.getKey()).setAttribute(attribue.getKey(), attribue.getValue());
					}
					mt.getMeasurements().get(d.getKey()).putVolume(timeId, d.getValue().getVolumes().get(timeId));
					mt.getMeasurements().get(d.getKey()).putSD(timeId, d.getValue().getSD().get(timeId));
				}
			}
		}
		
		return mOuts;
	}
}
