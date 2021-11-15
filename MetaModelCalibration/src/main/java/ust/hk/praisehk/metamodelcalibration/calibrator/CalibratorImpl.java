package ust.hk.praisehk.metamodelcalibration.calibrator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.AnalyticLinearMetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.AnalyticalQuadraticMetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.GradientBaseOptimizedMetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.GradientBasedMetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.GradientOptimizedMetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.LinearMetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.QuadraticMetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.SimAndAnalyticalGradientCalculator;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsReader;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsWriter;

public class CalibratorImpl implements Calibrator {

	//Necessary Containers

	protected Map<Integer,Measurements> simMeasurements=new HashMap<>();
	protected Map<Integer,Measurements> anaMeasurements=new HashMap<>();
	protected Map<Id<Measurement>,Map<String,MetaModel>>metaModels=new ConcurrentHashMap<>();
	protected Map<Integer,LinkedHashMap<String,Double>>params=new HashMap<>();
	private Map<Id<Measurement>,Map<String,MetaModel>> oldMetaModel=new HashMap<>();
	protected Map<Id<Measurement>, Map<String, LinkedHashMap<String, Double>>> currentSimGradient;
	protected Map<Id<Measurement>, Map<String, LinkedHashMap<String, Double>>> currentAnaGradient; 
	
	protected Measurements calibrationMeasurements;

	protected int iterationNo=0;
	protected int currentParamNo=0;

	protected LinkedHashMap<String,Double> currentParam;
	protected LinkedHashMap<String,Double> trialParam;
	private String OptimzerName=AnalyticalModelOptimizer.TROptimizerName;


	protected AnalyticalModel sueAssignment;

	private static final Logger logger=Logger.getLogger(CalibratorImpl.class);

	//Trust region parameters
	protected String ObjectiveType=ObjectiveCalculator.TypeMeasurementAndTimeSpecific;
	protected double TrRadius=25;
	


	protected double maxTrRadius=2.5*this.TrRadius;
	protected double minTrRadius=0.001;
	protected double successiveRejection=0;
	protected double maxSuccesiveRejection=4;
	protected double minMetaParamChange=.001;
	protected double thresholdErrorRatio=.01;
	protected String metaModelType=MetaModel.AnalyticalLinearMetaModelName;
	protected double trusRegionIncreamentRatio=1.25;
	protected double trustRegionDecreamentRatio=0.9;
	protected ParamReader pReader;
	protected final String fileLoc;
	protected final boolean shouldPerformInternalParamCalibration;
	
	
	public CalibratorImpl(Measurements calibrationMeasurements,String fileLoc,boolean internalParameterCalibration,
			ParamReader pReader, double initialTRRadius,int maxSuccessiveRejection) {
		this.fileLoc=fileLoc;
		this.shouldPerformInternalParamCalibration=internalParameterCalibration;
		this.pReader=pReader;
		this.TrRadius=initialTRRadius;
		if(pReader!=null) {
			this.currentParam=new LinkedHashMap<>(pReader.getInitialParam());
			this.trialParam=new LinkedHashMap<>(this.currentParam);
		}
		this.currentParamNo=0;
		this.iterationNo=0;
		this.maxSuccesiveRejection=maxSuccessiveRejection;
		this.calibrationMeasurements=calibrationMeasurements;
	}
	
	

	/**
	 * This will update sim measurements
	 * As sim measurements can not be generated without sim iteration, This do not take iteration no as input 
	 * Rather will assume the input measurement is the measurement of current iteration
	 * @param m
	 */
	protected void updateSimMeasurements(Measurements m) {
		this.simMeasurements.put(this.iterationNo, m);
	}

	/**
	 * This method is useful for internal parameter calibration
	 * It will update all the analytical model measurements at once  
	 * @param anaMeasurements a map of iterationNo vs Analytical model Measurements
	 */
	protected void updateAnalyticalMeasurement(Map<Integer, Measurements> measurements) {
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

	
	public String getOptimzerName() {
		return OptimzerName;
	}



	public void setOptimzerName(String optimzerName) {
		OptimzerName = optimzerName;
	}



	/**
	 * The input gradients can be null for non gradient based meatamodels.
	 * The gradient must contain the same measurement IDs and volumes as the calibration measurements
	 * @param simGradient
	 * @param anaGradient
	 * @throws IllegalArgumentException
	 */
	protected void createMetaModel(Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>>simGradient,Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>> anaGradient, final String metaModelType) {
		try {
		if((this.metaModelType.equals(MetaModel.GradientBased_I_MetaModelName)||this.metaModelType.equals(MetaModel.GradientBased_II_MetaModelName)||this.metaModelType.equals(MetaModel.GradientBased_III_MetaModelName))&& (anaGradient==null||simGradient==null)) {
			logger.error("Cannot create gradient based meta-model without gradient. switching to AnalyticalLinear");
			throw new IllegalArgumentException("Gradient cannot be null");

		}
		}catch(Exception e) {
			//metaModelType=MetaModel.AnalyticalLinearMetaModelName;
			System.out.print(e);
		}
		this.metaModelType=metaModelType;
		//for(Measurement m:this.calibrationMeasurements.getMeasurements().values()) {
		this.calibrationMeasurements.getMeasurements().values().parallelStream().forEach(m->{
			if(this.iterationNo>0) {
				this.oldMetaModel.put(m.getId(), this.metaModels.get(m.getId()));
			}
			this.metaModels.put(m.getId(), new HashMap<String,MetaModel>());
			
			for(String timeBeanId:m.getVolumes().keySet()) {

				MetaModel metaModel;
				
				switch(metaModelType) {

				case MetaModel.AnalyticalLinearMetaModelName: metaModel=new AnalyticLinearMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo) ;
				
				break;

				case MetaModel.AnalyticalQuadraticMetaModelName: metaModel=new AnalyticalQuadraticMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo);
				//this.metaModelType=metaModelType;
				break;
				
				case MetaModel.LinearMetaModelName: metaModel=new LinearMetaModel(m.getId(), this.simMeasurements, this.params, timeBeanId, this.currentParamNo);
				//this.metaModelType=metaModelType;
				break;
				
				case MetaModel.QudaraticMetaModelName: metaModel=new QuadraticMetaModel(m.getId(), this.simMeasurements, this.params, timeBeanId, this.currentParamNo) ;
				//this.metaModelType=metaModelType;
				break;
				
				case MetaModel.GradientBased_I_MetaModelName: metaModel=new GradientBasedMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo,simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId));
				//this.metaModelType=metaModelType;
				break;
				
				case MetaModel.GradientBased_II_MetaModelName: metaModel=new GradientBaseOptimizedMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo, simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId), this.iterationNo);
				//this.metaModelType=metaModelType;
				break;
				
				
				case MetaModel.GradientBased_III_MetaModelName: metaModel=new GradientOptimizedMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo, simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId), this.iterationNo);
				//this.metaModelType=metaModelType;
				break;
				
				default : metaModel=new AnalyticLinearMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo) ;
				//this.metaModelType=MetaModel.AnalyticalLinearMetaModelName;
				
				}
				if(this.metaModels.get(m.getId())==null) {
					logger.debug("Measurements not present");
				}
				this.metaModels.get(m.getId()).put(timeBeanId, metaModel);
				
			}
		//}
		});
	}

	
	/**
	 * The input gradients can be null for non gradient based meatamodels.
	 * The gradient must contain the same measurement IDs and volumes as the calibration measurements
	 * @param simGradient
	 * @param anaGradient
	 * @throws IllegalArgumentException
	 */
	protected static Map<Id<Measurement>,Map<String,MetaModel>> createMetaModel(Measurements calibrationMeasurements,Map<Integer,Measurements>simMeasurements,
			Map<Integer,Measurements>anaMeasurements,Map<Id<Measurement>,
			Map<String,LinkedHashMap<String,Double>>>simGradient,Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>> anaGradient, 
			Map<Integer,LinkedHashMap<String,Double>>params,int currentParamNo,int iterationNo,final String metaModelType) {
		try {
		if((metaModelType.equals(MetaModel.GradientBased_I_MetaModelName)||metaModelType.equals(MetaModel.GradientBased_II_MetaModelName)||metaModelType.equals(MetaModel.GradientBased_III_MetaModelName))&& (anaGradient==null||simGradient==null)) {
			logger.error("Cannot create gradient based meta-model without gradient. switching to AnalyticalLinear");
			throw new IllegalArgumentException("Gradient cannot be null");

		}
		}catch(Exception e) {
			//metaModelType=MetaModel.AnalyticalLinearMetaModelName;
			System.out.print(e);
		}
		Map<Id<Measurement>,Map<String,MetaModel>>metaModels = new HashMap<>();
		//this.metaModelType=metaModelType;
		//for(Measurement m:this.calibrationMeasurements.getMeasurements().values()) {
		calibrationMeasurements.getMeasurements().values().parallelStream().forEach(m->{
//			if(this.iterationNo>0) {
//				this.oldMetaModel.put(m.getId(), this.metaModels.get(m.getId()));
//			}
			metaModels.put(m.getId(), new HashMap<String,MetaModel>());
			
			for(String timeBeanId:m.getVolumes().keySet()) {

				MetaModel metaModel;
				
				switch(metaModelType) {

				case MetaModel.AnalyticalLinearMetaModelName: metaModel=new AnalyticLinearMetaModel(m.getId(), simMeasurements, anaMeasurements, params, timeBeanId, currentParamNo) ;
				
				break;

				case MetaModel.AnalyticalQuadraticMetaModelName: metaModel=new AnalyticalQuadraticMetaModel(m.getId(), simMeasurements, anaMeasurements, params, timeBeanId, currentParamNo);
				//this.metaModelType=metaModelType;
				break;
				
				case MetaModel.LinearMetaModelName: metaModel=new LinearMetaModel(m.getId(), simMeasurements,params, timeBeanId, currentParamNo);
				//this.metaModelType=metaModelType;
				break;
				
				case MetaModel.QudaraticMetaModelName: metaModel=new QuadraticMetaModel(m.getId(), simMeasurements, params, timeBeanId,currentParamNo) ;
				//this.metaModelType=metaModelType;
				break;
				
				case MetaModel.GradientBased_I_MetaModelName: metaModel=new GradientBasedMetaModel(m.getId(), simMeasurements, anaMeasurements,params, timeBeanId, currentParamNo,simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId));
				//this.metaModelType=metaModelType;
				break;
				
				case MetaModel.GradientBased_II_MetaModelName: metaModel=new GradientBaseOptimizedMetaModel(m.getId(), simMeasurements, anaMeasurements, params, timeBeanId,currentParamNo, simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId), iterationNo);
				//this.metaModelType=metaModelType;
				break;
				
				
				case MetaModel.GradientBased_III_MetaModelName: metaModel=new GradientOptimizedMetaModel(m.getId(), simMeasurements, anaMeasurements,params, timeBeanId, currentParamNo, simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId), iterationNo);
				//this.metaModelType=metaModelType;
				break;
				
				default : metaModel=new AnalyticLinearMetaModel(m.getId(), simMeasurements, anaMeasurements,params, timeBeanId,currentParamNo) ;
				//this.metaModelType=MetaModel.AnalyticalLinearMetaModelName;
				
				}
				if(metaModels.get(m.getId())==null) {
					logger.debug("Measurements not present");
				}
				metaModels.get(m.getId()).put(timeBeanId, metaModel);
				
			}
		//}
		});
		return metaModels;
	}

	
	@Override
	public LinkedHashMap<String,Double> generateNewParam(AnalyticalModel sue,Measurements simMeasurements,SimAndAnalyticalGradientCalculator gradFactory, String metaModelType) {
		this.updateSimMeasurements(simMeasurements);
		this.sueAssignment=sue;
		Measurements anaMeasurements=null;
		if(!metaModelType.equals(MetaModel.LinearMetaModelName)&&!metaModelType.equals(MetaModel.QudaraticMetaModelName)) {
			anaMeasurements= sue.perFormSUE(this.pReader.ScaleUp(this.trialParam),this.calibrationMeasurements.clone());
		}else {
			anaMeasurements=simMeasurements.clone();
		}
		new MeasurementsWriter(anaMeasurements).write(this.fileLoc+"anaMeasurement"+this.iterationNo+".xml");
		new MeasurementsWriter(simMeasurements).write(this.fileLoc+"simMeasurement"+this.iterationNo+".xml");
		if(this.iterationNo==0) {
			WriteParam(this.fileLoc+"param"+this.iterationNo+".csv",this.trialParam);
		}
		
		this.anaMeasurements.put(this.iterationNo, anaMeasurements);
		this.params.put(this.iterationNo, this.trialParam);
		boolean accepted=true;
		if(this.iterationNo==0) {
			String s="0th Objective Value = "+ObjectiveCalculator.calcObjective(calibrationMeasurements, simMeasurements, this.ObjectiveType);
			System.out.println(s);
			try {
				FileWriter fw=new FileWriter(new File(fileLoc+"0thSimObjective.txt"));
				fw.append(s);
				fw.flush();
				fw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if(this.iterationNo>0) {
			//Accept or reject the point
			//update successive rejected point
			//Run IterLogger
			//Fix Tr Radius
			this.writeMeasurementComparison(fileLoc);
			double CurrentSimObjective=ObjectiveCalculator.calcObjective(this.calibrationMeasurements, this.simMeasurements.get(this.currentParamNo), this.ObjectiveType);
			double CurrentMetaModelObjective=ObjectiveCalculator.calcObjective(this.calibrationMeasurements, this.CalcMetaModelPrediction(this.currentParamNo), this.ObjectiveType);
			double trialSimObjective=ObjectiveCalculator.calcObjective(this.calibrationMeasurements, this.simMeasurements.get(this.iterationNo), this.ObjectiveType);
			double trialMetaModelObjective=ObjectiveCalculator.calcObjective(this.calibrationMeasurements, this.CalcMetaModelPrediction(this.iterationNo), this.ObjectiveType);
			double SimObjectiveChange=CurrentSimObjective-trialSimObjective;
			double MetaObjectiveChange=CurrentMetaModelObjective-trialMetaModelObjective;
			double rouk=SimObjectiveChange/MetaObjectiveChange;
			accepted=false;
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
		if(this.successiveRejection>=this.maxSuccesiveRejection && this.shouldPerformInternalParamCalibration==true) {
			
			Map<Integer,LinkedHashMap<String,Double>> scaledParam=new HashMap<>();
			for(int i:this.params.keySet()) {
				scaledParam.put(i, this.pReader.ScaleUp(this.params.get(i)));
			}
			
			Map<Integer,Measurements>newAnaMeasurements=this.sueAssignment.calibrateInternalParams(this.simMeasurements, scaledParam,this.sueAssignment.getAnalyticalModelInternalParams(), this.currentParamNo);
			this.updateAnalyticalMeasurement(newAnaMeasurements);
			this.successiveRejection=0;
		}
			
		//Generating Gradient if necessary
		if(metaModelType.equals(MetaModel.GradientBased_I_MetaModelName)||metaModelType.equals(MetaModel.GradientBased_II_MetaModelName)||metaModelType.equals(MetaModel.GradientBased_III_MetaModelName)) {
		    if(accepted==true) {
		    	gradFactory.calcGradient(this.currentParam);
		    	this.currentSimGradient=gradFactory.getSimGradient();
		    	this.currentAnaGradient=gradFactory.getAnaGradient();
		    }
		}else {
			this.currentAnaGradient=null;
			this.currentSimGradient=null;
		}
		//Generating metaModels
		
		this.createMetaModel(this.currentSimGradient, this.currentAnaGradient, metaModelType);
		
		//Calculating new Point
		if(this.iterationNo>0) {
			if(this.calcAverageMetaParamsChange()<this.minMetaParamChange && !metaModelType.equals(MetaModel.GradientBased_I_MetaModelName)) {
				trialParam=this.drawRandomPoint(this.pReader.getInitialParamLimit());
			}else {
				AnalyticalModelOptimizer anaOptimizer=new AnalyticalModelOptimizerImpl(sue, this.calibrationMeasurements, this.metaModels, this.currentParam, this.TrRadius, this.pReader.getInitialParamLimit(),this.ObjectiveType ,metaModelType,this.pReader, this.iterationNo,this.fileLoc);
				anaOptimizer.setOptimizerType(AnalyticalModelOptimizer.TROptimizerName);
				this.trialParam=anaOptimizer.performOptimization();
			}
			
		}else {
			AnalyticalModelOptimizer anaOptimizer=new AnalyticalModelOptimizerImpl(sue, this.calibrationMeasurements, this.metaModels, this.currentParam, this.TrRadius, this.pReader.getInitialParamLimit(),this.ObjectiveType, metaModelType,this.pReader,this.iterationNo, this.fileLoc);
			this.trialParam=anaOptimizer.performOptimization();
		}
		
		
		this.iterationNo++;
		WriteParam(this.fileLoc+"param"+this.iterationNo+".csv",this.trialParam);
		return this.trialParam;
	}

	protected LinkedHashMap<String, Double> drawRandomPoint(LinkedHashMap<String, Tuple<Double, Double>> paramLimit) {
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
					
					fw.append(m.getId().toString().replace(",", "___")+","+timeBean+","+this.calibrationMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBean)+","+
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
	
	
	protected Measurements CalcMetaModelPrediction(int iterNo) {
		Measurements metaModelMeasurements=this.calibrationMeasurements.clone();
		for(Measurement m: this.calibrationMeasurements.getMeasurements().values()) {
			for(String timeBeanId:m.getVolumes().keySet()) {
				metaModelMeasurements.getMeasurements().get(m.getId()).putVolume(timeBeanId, this.metaModels.get(m.getId()).get(timeBeanId).calcMetaModel(this.anaMeasurements.get(iterNo).getMeasurements().get(m.getId()).getVolumes().get(timeBeanId), this.params.get(iterNo)));
			}
		}
		return metaModelMeasurements;
	}
	
	public static Measurements CalcMetaModelPrediction(int iterNo, Map<Id<Measurement>,Map<String,MetaModel>>metaModels, Measurements calibrationMeasurements, Map<Integer,Measurements>anaMeasurements, Map<Integer,LinkedHashMap<String,Double>>params) {
		Measurements metaModelMeasurements = calibrationMeasurements.clone();
		for(Measurement m: calibrationMeasurements.getMeasurements().values()) {
			for(String timeBeanId:m.getVolumes().keySet()) {
				metaModelMeasurements.getMeasurements().get(m.getId()).putVolume(timeBeanId, metaModels.get(m.getId()).get(timeBeanId).calcMetaModel(anaMeasurements.get(iterNo).getMeasurements().get(m.getId()).getVolumes().get(timeBeanId),params.get(iterNo)));
			}
		}
		return metaModelMeasurements;
	}
	
	public static void writeCurrentInfo(String folderLoc, Measurements calibrationMeasurements,Map<Integer,Measurements>simMeasurements, Map<Integer,Measurements>anaMeasurements, Map<Integer,LinkedHashMap<String,Double>>params) {
		File file = new File(folderLoc);
		if(!file.exists() || !file.isDirectory())file.mkdir();
		try {
			FileWriter fw = new FileWriter(new File(file.getPath()+"/params.csv"));
			fw.append("Iter");
			params.get(0).keySet().forEach(k->{
				try {
					fw.append(","+k);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
			fw.append("\n");
			new MeasurementsWriter(calibrationMeasurements).write(file.getPath()+"/original.xml");
		for(Entry<Integer, Measurements> i: simMeasurements.entrySet()) {
			new MeasurementsWriter(i.getValue()).write(file.getPath()+"/sim_"+i.getKey()+".xml");
			new MeasurementsWriter(anaMeasurements.get(i.getKey())).write(file.getPath()+"/ana_"+i.getKey()+".xml");
			fw.append(Integer.toString(i.getKey()));
			for(double d:params.get(i.getKey()).values()) {
				fw.append(","+d);
			}
			fw.append("\n");
		}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	class calibratorInfo{
		private Map<Integer, Measurements>simMeasurements = new HashMap<>();
		private Map<Integer,Measurements> anaMeasurements = new HashMap<>();
		private Map<Integer,LinkedHashMap<String,Double>> params = new HashMap<>();
		private Measurements originalMeasurements;
		
		public calibratorInfo(String folderLoc) {
			File file = new File(folderLoc);
			if(!file.isDirectory())throw new IllegalArgumentException("Path is not a directory!!!");
			this.originalMeasurements = new MeasurementsReader().readMeasurements(file.getPath()+"/original.xml");
			params = new HashMap<>();
			List<String> paramNames = new ArrayList<>();
			try {
				BufferedReader bf = new BufferedReader(new FileReader(new File(file.getPath()+"/params.csv")));
				String header = bf.readLine();
				String[] partHeader = header.split(",");
				String line = null;
				while((line = bf.readLine())!=null) {
					String[] part = line.split(",");
					int i = Integer.parseInt(part[0]);
					Measurements sim = new MeasurementsReader().readMeasurements(file.getPath()+"/sim_"+i+".xml");
					Measurements ana = new MeasurementsReader().readMeasurements(file.getPath()+"/ana_"+i+".xml");
					this.simMeasurements.put(i, sim);
					this.anaMeasurements.put(i, ana);
					LinkedHashMap<String,Double> param = new LinkedHashMap<>();
					for(int j = 1;j<part.length;j++) {
						param.put(partHeader[j], Double.parseDouble(part[j]));
					}
				}
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}

		public Map<Integer, Measurements> getSimMeasurements() {
			return simMeasurements;
		}

		public Map<Integer, Measurements> getAnaMeasurements() {
			return anaMeasurements;
		}

		public Map<Integer, LinkedHashMap<String, Double>> getParams() {
			return params;
		}

		public Measurements getOriginalMeasurements() {
			return originalMeasurements;
		}
		
		
	}
	
	protected void interLogger(String fileLoc,int IterNo, int currentParamNo, double CurrentAnalyticalObjective,
			double currentSimObjective, double newAnalyticalObjective, double newSimObjective, boolean Accepted,
			double currentTrRadius, double currentrouK,String metaModelType, LinkedHashMap<String, Double> Params,AnalyticalModel sue) {
		
	String sp=",";
	String nl="\n";
	String header="time"+sp+"IterNo"+sp+"CurrentParamNo"+sp+"CurrentAnalyticalObjective"
			+sp+"CurrentSimObjective"+sp+"newAnalyticalObjective"+sp+"newSimObjective"+sp+"Accepted"+sp+"TrustRegionRadius"+sp+"rouK"+sp+"MetaModelType"+sp+"Params"+sp+"InternalParams";
	
	
	try {
		FileWriter fw=new FileWriter(fileLoc+"iterLogger.csv",true);
		
		if(this.iterationNo==1) {
			fw.append(header+nl);
		}
		fw.append(LocalDateTime.now().toString()+sp);
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
			fw.append("0th sim objective"+sp+ObjectiveCalculator.calcObjective(calibrationMeasurements, this.simMeasurements.get(0), this.ObjectiveType+sp)+"\n");
			fw.append("0th meta objective"+sp+ObjectiveCalculator.calcObjective(calibrationMeasurements, this.anaMeasurements.get(0), metaModels,this.params.get(0), this.ObjectiveType)+"\n");
		}
		fw.flush();
		fw.close();
		}catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	}
	
	
	
	
	protected double calcAverageMetaParamsChange() {
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

	//-----------------------------Getter Setter------------------------------------------------------

	@Override
	public double getTrRadius() {
		return TrRadius;
	}


	@Override	
	public String getObjectiveType() {
		return ObjectiveType;
	}


	@Override
	public double getMaxTrRadius() {
		return maxTrRadius;
	}


	@Override
	public double getMinTrRadius() {
		return minTrRadius;
	}


	@Override
	public double getSuccessiveRejection() {
		return successiveRejection;
	}


	@Override
	public double getMaxSuccesiveRejection() {
		return maxSuccesiveRejection;
	}


	@Override
	public double getMinMetaParamChange() {
		return minMetaParamChange;
	}


	@Override
	public double getThresholdErrorRatio() {
		return thresholdErrorRatio;
	}


	@Override
	public double getTrusRegionIncreamentRatio() {
		return trusRegionIncreamentRatio;
	}


	@Override
	public double getTrustRegionDecreamentRatio() {
		return trustRegionDecreamentRatio;
	}


	@Override
	public String getFileLoc() {
		return fileLoc;
	}


	@Override
	public boolean isShouldPerformInternalParamCalibration() {
		return shouldPerformInternalParamCalibration;
	}


	@Override
	public int getCurrentParamNo() {
		return currentParamNo;
	}


	@Override
	public LinkedHashMap<String, Double> getCurrentParam() {
		return currentParam;
	}


	@Override
	public void setObjectiveType(String objectiveType) {
		ObjectiveType = objectiveType;
	}


	@Override
	public void setMaxTrRadius(double maxTrRadius) {
		this.maxTrRadius = maxTrRadius;
	}


	@Override
	public void setMinTrRadius(double minTrRadius) {
		this.minTrRadius = minTrRadius;
	}


	@Override
	public void setMinMetaParamChange(double minMetaParamChange) {
		this.minMetaParamChange = minMetaParamChange;
	}


	@Override
	public void setThresholdErrorRatio(double thresholdErrorRatio) {
		this.thresholdErrorRatio = thresholdErrorRatio;
	}


	@Override
	public void setTrusRegionIncreamentRatio(double trusRegionIncreamentRatio) {
		this.trusRegionIncreamentRatio = trusRegionIncreamentRatio;
	}


	@Override
	public void setTrustRegionDecreamentRatio(double trustRegionDecreamentRatio) {
		this.trustRegionDecreamentRatio = trustRegionDecreamentRatio;
	}
	
	public void setTrRadius(double trRadius) {
		if(this.iterationNo==0) {
			TrRadius = trRadius;
		}
	}
	
	
	public static void WriteParam(String fileLoc, LinkedHashMap<String,Double>param) {
		FileWriter fw;
		try {
			fw = new FileWriter(new File(fileLoc),true);
		
		for(String s:param.keySet()) {
			fw.append(s+","+param.get(s)+"\n");
		}
		fw.flush();
		fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static LinkedHashMap<String,Double> ReadParam(String fileLoc) {
		LinkedHashMap<String,Double>param=new LinkedHashMap<>();
		try {
			BufferedReader bf=new BufferedReader(new FileReader(new File(fileLoc)));
			String line;
			while((line=bf.readLine())!=null) {
				String[] part=line.split(",");
				param.put(part[0],Double.parseDouble(part[1]));
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return param;
	}
	public CalibratorImpl(int iterPerformed,int lastSelectedParam,String filelocOldFiles,Measurements calibrationMeasurements,String fileLoc,boolean internalParameterCalibration,ParamReader pReader,double initialTRRadius,int maxSuccessiveRejection) {
		this.fileLoc=fileLoc;
		this.shouldPerformInternalParamCalibration=internalParameterCalibration;
		this.pReader=pReader;
		this.TrRadius=initialTRRadius;
		if(pReader!=null)this.currentParam=new LinkedHashMap<>(pReader.getInitialParam());
		this.trialParam=new LinkedHashMap<>(this.currentParam);
		this.currentParamNo=0;
		this.iterationNo=0;
		this.maxSuccesiveRejection=maxSuccessiveRejection;
		this.calibrationMeasurements=calibrationMeasurements;
		if(filelocOldFiles==null) {
			filelocOldFiles=fileLoc;
		}
		for(int i=0;i<=iterPerformed;i++) {
			
			this.anaMeasurements.put(i,new MeasurementsReader().readMeasurements(filelocOldFiles+"anaMeasurements"+i+".xml"));
			this.simMeasurements.put(i,new MeasurementsReader().readMeasurements(filelocOldFiles+"simMeasurements"+i+".xml"));
			this.params.put(i, ReadParam(filelocOldFiles+"param"+i+".csv"));
			if(i==lastSelectedParam) {
				this.currentParamNo=lastSelectedParam;
				this.trialParam=ReadParam(filelocOldFiles+"param"+i+".csv");
				this.currentParam=this.params.get(i);
			}else {
				this.trialParam=ReadParam(filelocOldFiles+"param"+i+".csv");
			}
			this.createMetaModel(null, null, MetaModel.AnalyticalLinearMetaModelName);
			this.iterationNo++;
		}
	}
}
