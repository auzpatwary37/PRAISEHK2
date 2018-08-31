package calibrator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import analyticalModel.AnalyticalModel;
import matamodels.AnalyticLinearMetaModel;
import matamodels.AnalyticalQuadraticMetaModel;
import matamodels.GradientBaseOptimizedMetaModel;
import matamodels.GradientBasedMetaModel;
import matamodels.GradientOptimizedMetaModel;
import matamodels.LinearMetaModel;
import matamodels.MetaModel;
import matamodels.QuadraticMetaModel;
import measurements.Measurement;
import measurements.Measurements;



public class CalibratorImpl implements Calibrator {

	//Necessary Containers

	private Map<Integer,Measurements> simMeasurements=new HashMap<>();
	private Map<Integer,Measurements> anaMeasurements=new HashMap<>();
	private Map<Id<Measurement>,Map<String,MetaModel>>metaModels=new HashMap<>();
	private Map<Integer,LinkedHashMap<String,Double>>params=new HashMap<>();
	private Map<Id<Measurement>,Map<String,MetaModel>> oldMetaModel=new HashMap<>();
	
	private Measurements calibrationMeasurements;

	private int iterationNo=0;
	private int currentParamNo=0;

	private LinkedHashMap<String,Double> currentParam=new LinkedHashMap<>();
	private LinkedHashMap<String,Double> trialParam=new LinkedHashMap<>();



	private AnalyticalModel sueAssignment;

	private static final Logger logger=Logger.getLogger(CalibratorImpl.class);

	//Trust region parameters
	private String ObjectiveType=ObjectiveCalculator.TypeMeasurementAndTimeSpecific;
	private boolean InternalModelParamCalibration=true;
	private int maxIteration=100;
	private double TrRadius=25;
	private double maxTrRadius=2.5*this.TrRadius;
	private double minTrRadius=0.001;
	private double successiveRejection=0;
	private double maxSuccesiveRejection=4;
	private double minMetaParamChange=.001;
	private double thresholdErrorRatio=.01;
	private String metaModelType=MetaModel.AnalyticalLinearMetaModelName;
	private double trusRegionIncreamentRatio=1.25;
	private double trustRegionDecreamentRatio=0.9;
	private ParamReader pReader;
	private final String fileLoc;
	private final boolean shouldPerformInternalParamCalibration;

	
	public CalibratorImpl(String fileLoc,boolean internalParameterCalibration,ParamReader pReader,double initialTRRadius,int maxSuccessiveRejection) {
		this.fileLoc=fileLoc;
		this.shouldPerformInternalParamCalibration=internalParameterCalibration;
		this.pReader=pReader;
		this.TrRadius=initialTRRadius;
		this.currentParam=pReader.getInitialParam();
		this.currentParamNo=0;
		this.iterationNo=0;
		this.maxSuccesiveRejection=maxSuccessiveRejection;
	}
	
	

	@Override
	public void updateSimMeasurements(Measurements m) {
		this.simMeasurements.put(this.iterationNo, m);
	}

	@Override
	public void updateAnalyticalMeasurement(Map<Integer, Measurements> measurements) {
		if(this.anaMeasurements.size()!=measurements.size()) {
			logger.error("Measurements size must match. Aborting update");
			for(int i:this.anaMeasurements.keySet()) {
				if(measurements.get(i)==null) {
					logger.error("Measurements do not match. This is a fatal error!!!");
					throw new IllegalArgumentException("Measurements do not match. This is a fatal error!!!Calibration will exit.");
				}
				this.anaMeasurements.put(i, measurements.get(i));
			}
		}
	}

	
	/**
	 * The input gradients can be null for non gradient based meatamodels.
	 * The gradient must contain the same measurement IDs and volumes as the calibration measurements
	 * @param simGradient
	 * @param anaGradient
	 * @throws IllegalArgumentException
	 */
	@Override
	public void createMetaModel(Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>>simGradient,Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>> anaGradient, String metaModelType) {
		try {
		if((this.metaModelType.equals(MetaModel.GradientBased_I_MetaModelName)||this.metaModelType.equals(MetaModel.GradientBased_II_MetaModelName)||this.metaModelType.equals(MetaModel.GradientBased_III_MetaModelName))&& (anaGradient==null||simGradient==null)) {
			logger.error("Cannot create gradient based meta-model without gradient. switching to AnalyticalLinear");
			throw new IllegalArgumentException("Gradient cannot be null");

		}
		}catch(Exception e) {
			metaModelType=MetaModel.AnalyticalLinearMetaModelName;
		}
		
		for(Measurement m:this.calibrationMeasurements.getMeasurements().values()) {
			if(this.iterationNo>0) {
				this.oldMetaModel.put(m.getId(), this.metaModels.get(m.getId()));
			}
			this.metaModels.put(m.getId(), new HashMap<String,MetaModel>());
			for(String timeBeanId:m.getVolumes().keySet()) {

				MetaModel metaModel;

				switch(metaModelType) {

				case MetaModel.AnalyticalLinearMetaModelName: metaModel=new AnalyticLinearMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo) ;
				this.metaModelType=metaModelType;

				case MetaModel.AnalyticalQuadraticMetaModelName: metaModel=new AnalyticalQuadraticMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo);
				this.metaModelType=metaModelType;
				
				case MetaModel.LinearMetaModelName: metaModel=new LinearMetaModel(m.getId(), this.simMeasurements, this.params, timeBeanId, this.currentParamNo);
				this.metaModelType=metaModelType;
				
				case MetaModel.QudaraticMetaModelName: metaModel=new QuadraticMetaModel(m.getId(), this.simMeasurements, this.params, timeBeanId, this.currentParamNo) ;
				this.metaModelType=metaModelType;
				
				case MetaModel.GradientBased_I_MetaModelName: metaModel=new GradientBasedMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo,simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId));
				this.metaModelType=metaModelType;
				
				case MetaModel.GradientBased_II_MetaModelName: metaModel=new GradientBaseOptimizedMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo, simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId), this.iterationNo);
				this.metaModelType=metaModelType;
				
				case MetaModel.GradientBased_III_MetaModelName: metaModel=new GradientOptimizedMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo, simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId), this.iterationNo);
				this.metaModelType=metaModelType;
				
				default : metaModel=new AnalyticLinearMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo) ;
				this.metaModelType=MetaModel.AnalyticalLinearMetaModelName;
				
				}
				this.metaModels.get(m.getId()).put(timeBeanId, metaModel);
				
			}
		}
	}


	/**
	 * This is the most important method algorithm of this class
	 * The gradients can be null in case of non-gradient Based metaModel Type
	 */
	public LinkedHashMap<String,Double> generateNewParam(AnalyticalModel sue,Measurements simMeasurements,Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>>simGradient,Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>> anaGradient, String metaModelType) {
		this.updateSimMeasurements(simMeasurements);
		this.sueAssignment=sue;
		Measurements anaMeasurements=this.calibrationMeasurements.clone();
		Map<String,Map<Id<Link>,Double>>linkVolumes= sue.perFormSUE(new LinkedHashMap<>(this.trialParam));
		anaMeasurements.updateMeasurements(linkVolumes);
		this.anaMeasurements.put(this.iterationNo, anaMeasurements);
		this.params.put(this.iterationNo, this.trialParam);
		if(this.iterationNo>0) {
			//Accept or reject the point
			//update successive rejected point
			//Run IterLogger
			//Fix Tr Radius
			this.writeMeasurementComparison(fileLoc);
			double CurrentSimObjective=ObjectiveCalculator.calcObjective(this.calibrationMeasurements, this.simMeasurements.get(this.currentParam), this.ObjectiveType);
			double CurrentMetaModelObjective=ObjectiveCalculator.calcObjective(this.calibrationMeasurements, this.CalcMetaModelPrediction(this.currentParamNo), this.ObjectiveType);
			double trialSimObjective=ObjectiveCalculator.calcObjective(this.calibrationMeasurements, this.simMeasurements.get(this.iterationNo), this.ObjectiveType);
			double trialMetaModelObjective=ObjectiveCalculator.calcObjective(this.calibrationMeasurements, this.CalcMetaModelPrediction(this.iterationNo), this.ObjectiveType);
			double SimObjectiveChange=CurrentSimObjective-trialSimObjective;
			double MetaObjectiveChange=CurrentMetaModelObjective-trialMetaModelObjective;
			double rouk=SimObjectiveChange/MetaObjectiveChange;
			boolean accepted=false;
			if(SimObjectiveChange>0 && rouk>=this.thresholdErrorRatio) {
				this.currentParam=this.trialParam;
				this.currentParamNo=this.iterationNo;
				this.TrRadius=Math.min(this.maxTrRadius, this.trusRegionIncreamentRatio*this.TrRadius);
				this.successiveRejection=0;
				accepted=true;
			}else if(SimObjectiveChange>0 && rouk<this.thresholdErrorRatio){
				this.currentParam=this.trialParam;
				this.currentParamNo=this.iterationNo;
				this.successiveRejection=0;
				accepted=true;
			}else {
				this.successiveRejection++;
				this.TrRadius=Math.max(this.minTrRadius, this.trustRegionDecreamentRatio*this.TrRadius);
				accepted=false;
				
			}
			
			this.interLogger(fileLoc, this.iterationNo, this.currentParamNo, CurrentMetaModelObjective, CurrentSimObjective, trialMetaModelObjective, trialSimObjective, accepted, this.TrRadius, rouk,this.metaModelType, this.trialParam, sue);
			}
		
		//InternalparamCalibration
		if(this.successiveRejection>this.maxSuccesiveRejection && this.shouldPerformInternalParamCalibration==true) {
			Map<Integer,Measurements>newAnaMeasurements=this.sueAssignment.calibrateInternalParams(this.simMeasurements, this.params,this.sueAssignment.getAnalyticalModelInternalParams(), this.currentParamNo);
			this.updateAnalyticalMeasurement(newAnaMeasurements);
		}
			
		
		//Generating metaModels
		
		this.createMetaModel(simGradient, anaGradient, metaModelType);
		
		//Calculating new Point
		if(this.iterationNo>0) {
			if(this.calcAverageMetaParamsChange()<this.minMetaParamChange) {
				trialParam=this.drawRandomPoint(this.pReader.getInitialParamLimit());
			}else {
				AnalyticalModelOptimizer anaOptimizer=new AnalyticalModelOptimizerImpl(sue, this.calibrationMeasurements, this.metaModels, this.currentParam, this.TrRadius, this.pReader.getParamLimit(), metaModelType);
				this.trialParam=anaOptimizer.performOptimization();
			}
			
		}else {
			AnalyticalModelOptimizer anaOptimizer=new AnalyticalModelOptimizerImpl(sue, this.calibrationMeasurements, this.metaModels, this.currentParam, this.TrRadius, this.pReader.getParamLimit(), metaModelType);
			this.trialParam=anaOptimizer.performOptimization();
		}
		
		
		this.iterationNo++;
		return this.trialParam;
	}
	

	@Override
	public LinkedHashMap<String, Double> drawRandomPoint(LinkedHashMap<String, Tuple<Double, Double>> paramLimit) {
		LinkedHashMap<String, Double> randPoint=new LinkedHashMap<>();
		for(String s: paramLimit.keySet()) {
			double l=paramLimit.get(s).getFirst();
			double u=paramLimit.get(s).getSecond();
			randPoint.put(s,l+Math.random()*(u-l));
		}
		
		return randPoint;
	}


	@Override
	public void writeMeasurementComparison(String fileLoc) {
		try {
			FileWriter fw=new FileWriter(new File(fileLoc+"Comparison"+this.iterationNo+".csv"),false);
			fw.append("MeasurementId,timeBeanId,RealCount,currentSimCount,trialSimCount\n");
			for(Measurement m: this.calibrationMeasurements.getMeasurements().values()) {
				for(String timeBean:m.getVolumes().keySet()) {
					
					fw.append(m.getId()+","+timeBean+","+this.calibrationMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBean)+","+
				this.simMeasurements.get(this.currentParamNo).getMeasurements().get(m.getId()).getVolumes().get(timeBean)+","+this.simMeasurements.get(this.iterationNo).getMeasurements().get(m.getId()).getVolumes().get(timeBean)+"\n");
					}
			}
		fw.flush();
		fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	private Measurements CalcMetaModelPrediction(int iterNo) {
		Measurements metaModelMeasurements=this.calibrationMeasurements.clone();
		for(Measurement m: this.calibrationMeasurements.getMeasurements().values()) {
			for(String timeBeanId:m.getVolumes().keySet()) {
				metaModelMeasurements.getMeasurements().get(m.getId()).addVolume(timeBeanId, this.metaModels.get(m.getId()).get(timeBeanId).calcMetaModel(this.anaMeasurements.get(iterNo).getMeasurements().get(m.getId()).getVolumes().get(timeBeanId), this.params.get(iterNo)));
			}
		}
		return metaModelMeasurements;
	}
	
	
	
	private void interLogger(String fileLoc,int IterNo, int currentParamNo, double CurrentAnalyticalObjective,
			double currentSimObjective, double newAnalyticalObjective, double newSimObjective, boolean Accepted,
			double currentTrRadius, double currentrouK,String metaModelType, LinkedHashMap<String, Double> Params,AnalyticalModel sue) {
		
	String sp=",";
	String nl="\n";
	String header="IterNo"+sp+"CurrentParamNo"+sp+"CurrentAnalyticalObjective"
			+sp+"CurrentSimObjective"+sp+"newAnalyticalObjective"+sp+"newSimObjective"+sp+"Accepted"+sp+"TrustRegionRadius"+sp+"rouK"+sp+"MetaModelType"+sp+"Params"+sp+"InternalParams";
	
	
	try {
		FileWriter fw=new FileWriter(fileLoc+"iterLogger.csv");
		fw.append(LocalDateTime.now().toString());
		fw.append(header+nl);
		fw.append(IterNo+sp+currentParamNo+sp+CurrentAnalyticalObjective+
				sp+currentSimObjective+sp+newAnalyticalObjective+sp+newSimObjective+sp+Accepted
				+sp+currentTrRadius+sp+currentrouK+sp+metaModelType);
		for(double d:Params.values()) {
			fw.append(sp+d);
			}
		ArrayList<Double> zz=new ArrayList<>(sue.getAnalyticalModelInternalParams().values());
		for(double d:zz) {
			fw.append(sp+d);
		}
		
		fw.append(nl);
		if(this.iterationNo==1) {
			fw.append("0th sim objective"+sp+ObjectiveCalculator.calcObjective(calibrationMeasurements, this.simMeasurements.get(0), this.ObjectiveType+sp));
			fw.append("0th meta objective"+sp+ObjectiveCalculator.calcObjective(calibrationMeasurements, this.anaMeasurements.get(0), metaModels,this.params.get(0), this.ObjectiveType));
		}
		fw.flush();
		fw.close();
		}catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	}
	
	
	
	
	private double calcAverageMetaParamsChange() {
		boolean comparable=true;
		double z=0;
		int k=0;
		outerloop:
		for(Id<Measurement> m:this.metaModels.keySet()) {
			for(String timeId:this.metaModels.get(m).keySet()) {
				if(!this.oldMetaModel.get(m).get(timeId).getMetaModelName().equals(this.metaModels.get(m).get(timeId).getMetaModelName())) {
					logger.warn("Non comparable metamodel types. Method will exit.");
					comparable=false;
					break outerloop;
					
				}else {
				double[] oldparams=this.metaModels.get(m).get(timeId).getMetaModelParams();
				double[] newParams=this.oldMetaModel.get(m).get(timeId).getMetaModelParams();
				double distance=0;
				for(int i=0;i<oldparams.length;i++) {
					distance+=Math.pow(oldparams[i]-newParams[i],2);
				}
				distance=Math.sqrt(distance);
				z+=distance;
				k++;
				}
				
			}
		}
		z=z/k;
		if(comparable==false) {
			return this.minMetaParamChange+5;// this 5 is arbitrary. ensures greater than min threshold
		}
		return z;
		
	}

}
