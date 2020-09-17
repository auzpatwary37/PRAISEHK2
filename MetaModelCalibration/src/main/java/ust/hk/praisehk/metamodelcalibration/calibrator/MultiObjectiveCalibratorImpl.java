package ust.hk.praisehk.metamodelcalibration.calibrator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.SimAndAnalyticalGradientCalculator;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsWriter;

public class MultiObjectiveCalibratorImpl extends CalibratorImpl{
	
	
	public MultiObjectiveCalibratorImpl(Measurements calibrationMeasurements,String fileLoc,ParamReader pReader,double initialTRRadius,int maxSuccessiveRejection) {
		super(calibrationMeasurements,fileLoc,false,pReader,initialTRRadius,maxSuccessiveRejection);
		//super.setObjectiveType(ObjectiveCalculator.TypeMultiObjective);
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
			String s="0th Objective Value = "+ObjectiveCalculator.calcMultiObjective(calibrationMeasurements, simMeasurements,this.ObjectiveType);
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
			double CurrentSimObjective=Collections.max(ObjectiveCalculator.calcMultiObjective(this.calibrationMeasurements, this.simMeasurements.get(this.currentParamNo),this.ObjectiveType).values());
			double CurrentMetaModelObjective=Collections.max(ObjectiveCalculator.calcMultiObjective(this.calibrationMeasurements, this.CalcMetaModelPrediction(this.currentParamNo),this.ObjectiveType).values());
			double trialSimObjective=Collections.max(ObjectiveCalculator.calcMultiObjective(this.calibrationMeasurements, this.simMeasurements.get(this.iterationNo),this.ObjectiveType).values());
			double trialMetaModelObjective=Collections.max(ObjectiveCalculator.calcMultiObjective(this.calibrationMeasurements, this.CalcMetaModelPrediction(this.iterationNo),this.ObjectiveType).values());
			double SimObjectiveChange=CurrentSimObjective-trialSimObjective;
			double MetaObjectiveChange=CurrentMetaModelObjective-trialMetaModelObjective;
			double rouk=SimObjectiveChange/MetaObjectiveChange;
			if(MetaObjectiveChange==0)rouk = 0;
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
				Map<MeasurementType,Double> currObj = ObjectiveCalculator.calcMultiObjective(this.calibrationMeasurements, this.simMeasurements.get(this.currentParamNo),this.ObjectiveType);
				Map<MeasurementType,Double> bstObj = new HashMap<>();
 				for(MeasurementType mType:this.calibrationMeasurements.getMeasurementsByType().keySet()) {
 					if(this.calibrationMeasurements.getMeasurementsByType().get(mType).isEmpty())continue;
					AnalyticalModelOptimizer anaOptimizer=new AnalyticalModelOptimizerImpl(new SingleMultiObjectiveFunction(sue, this.calibrationMeasurements, this.metaModels, this.currentParam, this.TrRadius, this.pReader.getInitialParamLimit(),this.ObjectiveType ,metaModelType,this.pReader, this.iterationNo,this.fileLoc,mType));
					anaOptimizer.setOptimizerType(AnalyticalModelOptimizer.TROptimizerName);
					this.trialParam=anaOptimizer.performOptimization();
					bstObj.put(mType, anaOptimizer.getOptimizationFunction().getMinObj());
				}
 				MultiObjOptimDecisionObjective optimFunc = new MultiObjOptimDecisionObjective(sue, this.calibrationMeasurements, 
 						this.metaModels, this.currentParam, this.TrRadius, this.pReader.getInitialParamLimit(),
 						this.ObjectiveType ,metaModelType,this.pReader, this.iterationNo,this.fileLoc,
 						bstObj, currObj); 
 				double[] x=new double[optimFunc.getCalibrationParamSize()+1];

 				for (int j=0;j<x.length;j++) {
 					x[j]=1;
 					j++;
 				}

 				
				//Call the optimization subroutine
				CobylaExitStatus result = Cobyla.findMinimum(optimFunc,x.length, optimFunc.getConstrainNo(),
						x,10,.01 ,3, 1500);
				for(int i=0;i<x.length;i++)System.out.println(x[i]);
				this.trialParam=optimFunc.ScaleUp(x);
			
			}
			
		}else {
			Map<MeasurementType,Double> currObj = ObjectiveCalculator.calcMultiObjective(this.calibrationMeasurements, this.simMeasurements.get(this.currentParamNo),this.ObjectiveType);
			Map<MeasurementType,Double> bstObj = new HashMap<>();
				for(MeasurementType mType:this.calibrationMeasurements.getMeasurementsByType().keySet()) {
					if(this.calibrationMeasurements.getMeasurementsByType().get(mType).isEmpty())continue;
					AnalyticalModelOptimizer anaOptimizer=new AnalyticalModelOptimizerImpl(new SingleMultiObjectiveFunction(sue, this.calibrationMeasurements, this.metaModels, this.currentParam, this.TrRadius, this.pReader.getInitialParamLimit(),this.ObjectiveType ,metaModelType,this.pReader, this.iterationNo,this.fileLoc,mType));
					anaOptimizer.setOptimizerType(AnalyticalModelOptimizer.TROptimizerName);
					this.trialParam=anaOptimizer.performOptimization();
					bstObj.put(mType, anaOptimizer.getOptimizationFunction().getMinObj());
				}
				MultiObjOptimDecisionObjective optimFunc = new MultiObjOptimDecisionObjective(sue, this.calibrationMeasurements, 
						this.metaModels, this.currentParam, this.TrRadius, this.pReader.getInitialParamLimit(),
						this.ObjectiveType ,metaModelType,this.pReader, this.iterationNo,this.fileLoc,
						bstObj, currObj); 
				double[] x=new double[optimFunc.getCalibrationParamSize()+1];

				for (int j=0;j<x.length;j++) {
					x[j]=1;
					j++;
				}

				
			//Call the optimization subroutine
			CobylaExitStatus result = Cobyla.findMinimum(optimFunc,x.length, optimFunc.getConstrainNo(),
					x,10,.01 ,3, 1500);
			for(int i=0;i<x.length;i++)System.out.println(x[i]);
			this.trialParam=optimFunc.ScaleUp(x);
		}
		
		
		this.iterationNo++;
		WriteParam(this.fileLoc+"param"+this.iterationNo+".csv",this.trialParam);
		return this.trialParam;
	}
	
}
