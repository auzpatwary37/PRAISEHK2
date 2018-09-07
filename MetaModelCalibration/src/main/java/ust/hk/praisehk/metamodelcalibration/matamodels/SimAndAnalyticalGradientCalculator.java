package ust.hk.praisehk.metamodelcalibration.matamodels;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.MeasurementsStorage;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SimRun;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;


/**
 * TODO: Make this class generic 
 * Now it is CNLSUE specific
 * @author Ashraf
 *
 */
public class SimAndAnalyticalGradientCalculator {
	
	public static final String SPSAMethodName="SPSA";
	public static final String FDMethodName="FD";
	
	
	private MeasurementsStorage storage;
	private Config config;
	private SimRun simRun;
	private Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>> simGradient=new HashMap<>();
	private Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>> anaGradient=new HashMap<>();
	private LinkedHashMap<String,Double> originalGrad=new LinkedHashMap<>();
	private LinkedHashMap<String,Double> currentParam;
	private LinkedHashMap<String,Double> originalParam;
	private double c=0.1;
	private int currentIterCounter;
	public SimAndAnalyticalGradientCalculator(Config config,MeasurementsStorage storage,SimRun simRun,
			LinkedHashMap<String,Double>atParam, double c,String gradientCalculationMethod,LinkedHashMap<String,Double>OriginalParam,int currentIterCounter,boolean runParallel) {
		this.storage=storage;
		this.currentParam=atParam;
		this.simRun=simRun;
		if(c!=0) {
			this.c=c;
		}
		this.currentIterCounter=currentIterCounter;
		this.config=config;
		this.originalParam=OriginalParam;
		if(gradientCalculationMethod.equals(this.SPSAMethodName)) {
			this.calcSPSAGradient(runParallel);
		}else if(gradientCalculationMethod.equals(this.FDMethodName)){
			this.calcFDGradient(runParallel);
		}
	}
	
	private void calcSPSAGradient(boolean runParallel) {
		//LinkedHashMap<String,Double>gHat=new LinkedHashMap<>();
		double[] delta = generateBernouilliDistribution(this.currentParam.size());
		LinkedHashMap<String,Double> thetaMinus = new LinkedHashMap<>();
		LinkedHashMap<String,Double> thetaPlus = new LinkedHashMap<>();
		int i=0;
		for(String s:this.currentParam.keySet()) {
			thetaMinus.put(s,this.currentParam.get(s)-this.c*delta[i]); //delta in {-1, 1}
			thetaPlus.put(s,this.currentParam.get(s)+c*delta[i]);
			i++;
		}
		Measurements simLinkCountPlus;
		Measurements simLinkCountMinus;
		Measurements anaLinkCountPlus;
		Measurements anaLinkCountMinus;
		
		if(runParallel) {
			Thread[] threads=new Thread[2];
			simandAnaRunRunnable[] functionEvals=new simandAnaRunRunnable[2];
			functionEvals[0]=new simandAnaRunRunnable(this.config, this.simRun, thetaPlus,this.originalParam,currentIterCounter+"_thread0",this.storage);
			functionEvals[1]=new simandAnaRunRunnable(this.config, this.simRun, thetaMinus,this.originalParam,currentIterCounter+"_thread1",this.storage);
			
			threads[0]=new Thread(functionEvals[0]);
			threads[1]=new Thread(functionEvals[1]);
			threads[0].start();
			threads[1].start();
			
			try {
				threads[0].join();
				threads[1].join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			simLinkCountPlus=functionEvals[0].getSimCount();
			simLinkCountMinus=functionEvals[1].getSimCount();
			anaLinkCountPlus=functionEvals[0].getAnaCount();
			anaLinkCountMinus=functionEvals[1].getAnaCount();
			
		}else {
			CNLSUEModel sue=new CNLSUEModel(this.storage.getTimeBean());
			sue.setDefaultParameters(this.originalParam);
			simRun.run(sue, this.config, thetaPlus, true,currentIterCounter+"_thread0",this.storage);
			simLinkCountPlus=this.storage.getSimMeasurement(thetaPlus);
			anaLinkCountPlus=simLinkCountPlus.clone();
			anaLinkCountPlus.updateMeasurements(sue.perFormSUE(thetaPlus));
			sue=new CNLSUEModel(this.storage.getTimeBean());
			sue.setDefaultParameters(originalParam);
			simRun.run(sue, this.config, thetaMinus, true,currentIterCounter+"_thread1",this.storage);
			simLinkCountMinus=this.storage.getSimMeasurement(thetaMinus);
			anaLinkCountMinus=simLinkCountMinus.clone();
			anaLinkCountMinus.updateMeasurements(sue.perFormSUE(thetaMinus));
		}
		//time
		for(Measurement m:this.storage.getCalibrationMeasurements().getMeasurements().values()) {
			this.simGradient.put(m.getId(),new HashMap<String, LinkedHashMap<String, Double>>());
			this.anaGradient.put(m.getId(), new HashMap<String, LinkedHashMap<String, Double>>());
			//linkId
			for(String timeBeanId:simLinkCountPlus.getMeasurements().get(m.getId()).getVolumes().keySet()) {
				this.simGradient.get(m.getId()).put(timeBeanId, new LinkedHashMap<String,Double>());
				this.anaGradient.get(m.getId()).put(timeBeanId, new LinkedHashMap<String,Double>());
				i=0;
				//params
				for(String paramName:this.currentParam.keySet()) {
					double gHatSim=(simLinkCountPlus.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)-simLinkCountMinus.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId))/(2*c*delta[i]);
					double gHatAna=(anaLinkCountPlus.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)-anaLinkCountMinus.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId))/(2*c*delta[i]);
					this.simGradient.get(m.getId()).get(timeBeanId).put(paramName, gHatSim);
					this.anaGradient.get(m.getId()).get(timeBeanId).put(paramName, gHatAna);
					i++;
					if(this.originalGrad.get(paramName)==null) {
						this.originalGrad.put(paramName, gHatSim*(this.storage.getSimMeasurement(this.currentParam).getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)-this.storage.getCalibrationMeasurements().getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)));
					}else {
						this.originalGrad.put(paramName, this.originalGrad.get(paramName)+gHatSim*(this.storage.getSimMeasurement(this.currentParam).getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)-this.storage.getCalibrationMeasurements().getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)));
					}		
				}
			}
		}
	}
	
	
	

	
	public LinkedHashMap<String, Double> getOriginalGrad() {
		return originalGrad;
	}

	private static double[]  generateBernouilliDistribution(int size) {
		double[] delta = new double[size];
		Random rand = new Random();
		for(int i = 0; i<size; i++) {
			delta[i] = (2 * (Math.round(rand.nextDouble())))-1;
		}
		return delta;
	}

	
	
	private void calcFDGradient(boolean runParallel) {
		
		//TODO: calculate original gradient similar to SPSA technique
		 	double h = 0;
		 	Measurements simLinkCountPlus;
			Measurements simLinkCountMinus;
			Measurements anaLinkCountPlus;
			Measurements anaLinkCountMinus;
	        LinkedHashMap<String,Double> p = new LinkedHashMap<>(this.currentParam);
	        int i=0;
	        for (String s:this.currentParam.keySet()) {
	            h = c* Math.abs(this.currentParam.get(s));
	            p.put(s, this.currentParam.get(s)+h);
	            LinkedHashMap<String,Double>pPlus=new LinkedHashMap<>(p);
	            p.put(s, this.currentParam.get(s)-h);
	            LinkedHashMap<String,Double>pMinus=new LinkedHashMap<>(p);
	            if(runParallel!=true) {
	            	AnalyticalModel sue=new CNLSUEModel(this.storage.getTimeBean());
	    			sue.setDefaultParameters(originalParam);
	            	simRun.run(sue, this.config, pPlus, true,currentIterCounter+s+"_thread0",storage);
	    			
	    			simLinkCountPlus=this.storage.getSimMeasurement(pPlus);
	    			anaLinkCountPlus=simLinkCountPlus.clone();
	    			anaLinkCountPlus.updateMeasurements(sue.perFormSUE(pPlus));
	    			sue=new CNLSUEModel(this.storage.getTimeBean());
	    			sue.setDefaultParameters(originalParam);
	    			simRun.run(sue, this.config, pMinus, true,currentIterCounter+s+"_thread0",storage);
	    			simLinkCountMinus=storage.getSimMeasurement(pMinus);
	    			anaLinkCountMinus=simLinkCountMinus.clone();
	    			anaLinkCountMinus.updateMeasurements(sue.perFormSUE(pMinus));
	            }else {
	            	Thread[] threads=new Thread[2];
	    			simandAnaRunRunnable[] functionEvals=new simandAnaRunRunnable[2];
	    			functionEvals[0]=new simandAnaRunRunnable(this.config, this.simRun, pPlus,this.originalParam,currentIterCounter+s+"_thread0",storage);
	    			functionEvals[1]=new simandAnaRunRunnable(this.config, this.simRun, pMinus,this.originalParam,currentIterCounter+s+"_thread1",storage);
	    			
	    			threads[0]=new Thread(functionEvals[0]);
	    			threads[1]=new Thread(functionEvals[1]);
	    			threads[0].start();
	    			threads[1].start();
	    			
	    			try {
	    				threads[0].join();
	    				threads[1].join();
	    			} catch (InterruptedException e) {
	    				// TODO Auto-generated catch block
	    				e.printStackTrace();
	    			}
	    			simLinkCountPlus=functionEvals[0].getSimCount();
	    			simLinkCountMinus=functionEvals[1].getSimCount();
	    			anaLinkCountPlus=functionEvals[0].getAnaCount();
	    			anaLinkCountMinus=functionEvals[1].getAnaCount();
	            }
	            p.put(s, currentParam.get(s));
	            
	            for(Measurement m:simLinkCountPlus.getMeasurements().values()) {
	            	if(i==0) {
	            		this.simGradient.put(m.getId(), new HashMap<String, LinkedHashMap<String, Double>>());
	            		this.anaGradient.put(m.getId(), new HashMap<String, LinkedHashMap<String, Double>>());
	            	}
	            	for(String timeBeanId:simLinkCountPlus.getMeasurements().get(m.getId()).getVolumes().keySet()) {
	            		if(i==0) {
		            		this.simGradient.get(m.getId()).put(timeBeanId, new LinkedHashMap<String,Double>());
		            		this.anaGradient.get(m.getId()).put(timeBeanId, new LinkedHashMap<String,Double>());
		            	}
	            		double simGrad=(simLinkCountPlus.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId) - simLinkCountMinus.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)) / (2.0 * h);
	            		double anaGrad=(anaLinkCountPlus.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId) - anaLinkCountMinus.getMeasurements().get(m.getId()).getVolumes().get(timeBeanId)) / (2.0 * h);
	            		this.simGradient.get(m.getId()).get(timeBeanId).put(s,simGrad);
	            		this.anaGradient.get(m.getId()).get(timeBeanId).put(s, anaGrad);
	            	}
	            }
	            i++;
	        }
	       
	    }

	public Map<Id<Measurement>, Map<String, LinkedHashMap<String, Double>>> getSimGradient() {
		return simGradient;
	}

	public Map<Id<Measurement>, Map<String, LinkedHashMap<String, Double>>> getAnaGradient() {
		return anaGradient;
	}
	
	public void gradientLogger(String fileLoc) {
		FileWriter fw;
		try {
			fw = new FileWriter(new File(fileLoc));
		
		fw.append("current Point,");
		for(String s:this.currentParam.keySet()) {
			fw.append(","+s);
		}
		fw.append("\n");
		for(double d:this.currentParam.values()) {
			fw.append(","+d);
		}
		fw.append("\n");
		fw.append("LinkId,timeBeanId,");
		for(String s:this.currentParam.keySet()) {
			fw.append("AnaGradient"+s+",");
		}
		for(String s:this.currentParam.keySet()) {
			fw.append("SimGradient"+s+",");
		}
		fw.append("\n");
		for(Id<Measurement> mId:this.simGradient.keySet()) {
			for(String timeBeanId:this.simGradient.get(mId).keySet()) {
				fw.append(mId+","+timeBeanId+",");
				for(String ss:this.currentParam.keySet()) {
					fw.append(this.anaGradient.get(mId).get(timeBeanId).get(ss)+",");
				}
				for(String ss:this.currentParam.keySet()) {
					fw.append(this.simGradient.get(mId).get(timeBeanId).get(ss)+",");
				}
				fw.append("\n");
			}
		}
		fw.flush();
		fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
}



class simandAnaRunRunnable implements Runnable{
	private SimRun simRun;
	private Config config;
	private LinkedHashMap<String,Double> atParam;
	private AnalyticalModel sue;
	private Measurements anaCount;
	private Measurements simCount;
	private String threadNo;
	private MeasurementsStorage storage;
	
	public simandAnaRunRunnable(Config config,SimRun simRun,LinkedHashMap<String,Double>atParam,LinkedHashMap<String,Double> originalParam,String threadNo,MeasurementsStorage storage){
		this.storage=storage;
		this.simRun=simRun;
		this.config=config;
		this.atParam=atParam;
		this.sue=new CNLSUEModel(this.storage.getTimeBean());
		this.sue.setDefaultParameters(originalParam);
		this.threadNo=threadNo;
		
	}
	@Override
	public void run() {
		simRun.run(sue, this.config, this.atParam, true,threadNo,storage);
		this.simCount=this.storage.getSimMeasurement(atParam);
		this.anaCount=this.simCount.clone();
		this.anaCount.updateMeasurements(sue.perFormSUE(atParam));
		}
	public Measurements getAnaCount() {
		return anaCount;
	}
	public Measurements getSimCount() {
		return simCount;
	}
	
	
}
