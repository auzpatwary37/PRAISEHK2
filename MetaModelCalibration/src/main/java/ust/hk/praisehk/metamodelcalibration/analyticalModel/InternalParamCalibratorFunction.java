package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;


import de.xypron.jcobyla.Calcfc;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class InternalParamCalibratorFunction implements Calcfc{
		/**
		 * This class does the internal Parameter calibration	
		 */
			
		private AnalyticalModel sue;
		private LinkedHashMap<String,Tuple<Double,Double>> paramLimit=new LinkedHashMap<>();
		private LinkedHashMap<String,Double> initialParam;
		private Map<Integer,Measurements> simMeasurements;
		private Map<Integer,LinkedHashMap<String,Double>> Parmas;
		private final LinkedHashMap<String,Double> currentParam;
		private final Map<String,Tuple<Double,Double>> timeBean;
		private double optimIter=0;
		private final String fileLoc;
		
		
		private LinkedHashMap<String,Double> getDefaultBPRAnaParam(){
			LinkedHashMap<String,Double> AnalyticalModelInternalParams=new LinkedHashMap<>();
			AnalyticalModelInternalParams.put(CNLSUEModel.LinkMiuName, 0.008);
			AnalyticalModelInternalParams.put(CNLSUEModel.ModeMiuName, 0.01);
			AnalyticalModelInternalParams.put(CNLSUEModel.BPRalphaName, 0.15);
			AnalyticalModelInternalParams.put(CNLSUEModel.BPRbetaName, 4.);
			AnalyticalModelInternalParams.put(CNLSUEModel.TransferalphaName, 0.5);
			AnalyticalModelInternalParams.put(CNLSUEModel.TransferbetaName, 1.);
			return AnalyticalModelInternalParams;
		}
			
		/**
		 * 
		 * @param simData: all simulation measurements
		 * @param parmas all parameters
		 * @param sue analyticalModel
		 * @param initialParam Initial guess for internal parameters
		 * @param currentParamNo The current selected parameter no
		 */
		public InternalParamCalibratorFunction(Map<Integer,Measurements> simData,Map<Integer,LinkedHashMap<String,Double>>params,AnalyticalModel sue, LinkedHashMap<String, Double> initialParam,Integer currentParamNo) {

				this.sue=sue;
				this.fileLoc=sue.getFileLoc()+"Calibration/InternalParamCalibrationDetails.csv";
				this.initialParam=initialParam;
				this.currentParam=params.get(currentParamNo);
				this.simMeasurements=simData;
				this.Parmas=params;
				this.timeBean=simData.get(0).getTimeBean();
				if(initialParam.size()==sue.getAnalyticalModelInternalParams().size()) {
					paramLimit=sue.getAnalyticalModelParamsLimit();
				}else {
					for(Entry<String,Tuple<Double,Double>>e:sue.getAnalyticalModelParamsLimit().entrySet()) {
						if(initialParam.containsKey(e.getKey())) {
							this.paramLimit.put(e.getKey(), e.getValue());
						}
					}
				}
			}

			public LinkedHashMap<String, Tuple<Double, Double>> getParamLimit() {
				return paramLimit;
			}

			@Override
			public double compute(int m, int n, double[] x, double[] c) {
				double[] y=new double[x.length];
				int j=0;
				for(double d:this.initialParam.values()) {
					y[j]=d+d*x[j]/100.;
					j++;
				}
				LinkedHashMap<String,Double> anaParam=scaleUp(y);
				double objective=0;	
				Measurements anaBPRMeasurement=this.simMeasurements.get(0).clone();
				for(int i=0;i<this.simMeasurements.size();i++) {
					LinkedHashMap<String,Double> param=new LinkedHashMap<>(this.Parmas.get(i));
					double weight=1/(1+this.calcEucleadeanDistance(this.currentParam, param));
					Map<String,Map<Id<Link>,Double>> anaCount=this.sue.perFormSUE(param, anaParam);
					Measurements anaMeasurement=this.simMeasurements.get(0).clone();
					anaMeasurement.updateMeasurements(anaCount);
					if(weight==1) {
						Map<String,Map<Id<Link>,Double>> anaBPRCount=this.sue.perFormSUE(param, this.getDefaultBPRAnaParam());
						anaBPRMeasurement.updateMeasurements(anaBPRCount);
					}
					Double BPRObj=0.;
					Double currentObj=0.;
					Measurements simMeasurement=this.simMeasurements.get(i);
					for(Id<Measurement> mId:simMeasurement.getMeasurements().keySet()) {
						for(String s:simMeasurement.getMeasurements().get(mId).getVolumes().keySet()) {
							double simValue=simMeasurement.getMeasurements().get(mId).getVolumes().get(s);
							double anaValue=anaMeasurement.getMeasurements().get(mId).getVolumes().get(s);
							double a=simValue-anaValue;
							objective+=weight*Math.pow(a, 2);
							if(weight==1) {
								BPRObj+=Math.pow((simMeasurement.getMeasurements().get(mId).getVolumes().get(s)-anaBPRMeasurement.getMeasurements().get(mId).getVolumes().get(s)), 2);
								currentObj+=Math.pow(a, 2);
							}
						}
					}

					if(weight==1) {
						this.writeOptimizationDetails(BPRObj, currentObj);
					}
				}

				for(double d:x) {
					objective+=d*d;
				}
				int d=0;
				for(double xi:calcConstrain(x,this.paramLimit)) {
					c[d]=xi;
					d++;
				}
				
				return objective;
			}
			
			public void writeOptimizationDetails(double BPRObj,double currentObj) {
				try {
					FileWriter fw=new FileWriter(new File(fileLoc),true);
					String header="timeStamp,CurrentCalibrationIteration,GapBetweenBPRandSim,GapBetweenCurrentParamAndSim\n";
					if(optimIter==0) {
						fw.append(header);
					}
					fw.append(LocalDateTime.now().toString()+","+this.simMeasurements.size()+","+BPRObj+","+currentObj+"\n");
					fw.flush();
					fw.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			private double calcEucleadeanDistance(LinkedHashMap<String,Double> param1,LinkedHashMap<String,Double>param2) {
				double distance=0;
				for(String s: param1.keySet()) {
					distance+=Math.pow(param1.get(s)-param2.get(s), 2);
				}
				distance=Math.sqrt(distance);
				return distance;
			}
			public double[] calcConstrain(double[] x, LinkedHashMap<String,Tuple<Double,Double>> paramLimit) {
				int noOfConst=2*x.length;
				int j=0;
				int k=0;
				double[] c=new double[noOfConst];
				double[] y=new double[x.length];
				double[] l=new double[x.length];
				double[] u=new double[x.length];
				j=0;
				for(double d:this.initialParam.values()) {
					y[j]=x[j]*d/100.+d;
					j++;
				}
				j=0;
				for(Tuple<Double,Double> t:paramLimit.values()) {
					l[j]=t.getFirst();
					u[j]=t.getSecond();
					c[k]=(y[j]-l[j])*100;
					c[k+1]=(u[j]-y[j])*100;
					if(c[k]<-.00001||c[k+1]<-.00001) {
						System.out.println("Constrains violated!!!");
					}
					k=k+2;
					j++;
				}
				
				return c;
			}
			
			public double[] calcConstrain(double[] x) {
				int noOfConst=2*x.length;
				int j=0;
				int k=0;
				double[] c=new double[noOfConst];
				for(Tuple<Double,Double> t:paramLimit.values()) {
					c[k]=x[j]+100;
					c[k+1]=100-x[j];
					if(c[k]<0||c[k+1]<0) {
						System.out.println("Constrains violated!!!");
					}
					k=k+2;
					j++;
				}
				
				return c;
			}
			
			private LinkedHashMap<String,Double> scaleUp(double[] x){
				LinkedHashMap<String,Double> anaParam=new LinkedHashMap<>();
				int i=0;
				for(String s:this.paramLimit.keySet()) {
					anaParam.put(s, x[i]);
					i++;
				}
				return anaParam;
			}
			public Map<Integer,Measurements> getUpdatedAnaCount() {
				Map<Integer,Measurements> anaMeasurements=new HashMap<>();
				for(int i=0;i<this.simMeasurements.size();i++) {
					anaMeasurements.put(i,this.simMeasurements.get(i).clone());
					Map<String,Map<Id<Link>,Double>> linkFlows=this.sue.perFormSUE(new LinkedHashMap<>(this.Parmas.get(i)));
					anaMeasurements.get(i).updateMeasurements(linkFlows);
				}
				return anaMeasurements;
			}
			
		}


