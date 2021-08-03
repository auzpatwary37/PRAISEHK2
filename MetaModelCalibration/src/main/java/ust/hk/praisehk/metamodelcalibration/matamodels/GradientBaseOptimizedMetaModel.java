package ust.hk.praisehk.metamodelcalibration.matamodels;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;

import de.xypron.jcobyla.Calcfc;
import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class GradientBaseOptimizedMetaModel extends MetaModelImpl{
		private static final double c=1.0;
		//private double[] metaParams;
		private double[] metaParamShouldBe;
		private LinkedHashMap<String,Double> currentParam;
		private String timeBeanId;
		private boolean addRidgePenalty=false;
		private HashMap<Integer,Double> analyticalData=new HashMap<>();
		private double ridgeCoefficient=1;
		private int currentSimIter=0;
		
		/**
		 * This is experimental currently set to 1
		 */
		double iterBasedIncreasingCost=0;
		
		/**
		 * This constructor also calibrates or in this case calculates the meta-model.
		 * The format of the meta-model is 
		 * 
		 * ym=m0+anaModelCount+mT*Param
		 * 
		 * the calibration would be as follows
		 * 
		 * obj=(ySim-ym)^2+m^2+IterBasedWeight(delta(AnaModel)+m-delta(Sim))^2
		 * 
		 
		/**
		 * This constructor do not call the simulation run implicitly.
		 * rather taking the already calculated gradients and preparing the metamodel.
		 * @param SimData
		 * @param AnalyticalData
		 * @param paramsToCalibrate
		 * @param timeBeanId
		 * @param counter
		 * @param SimGradient
		 * @param anaGradient
		 */
		public GradientBaseOptimizedMetaModel(Id<Measurement>measurementId,Map<Integer,Measurements> SimData, Map<Integer,Measurements> AnalyticalData,
				Map<Integer, LinkedHashMap<String, Double>> paramsToCalibrate,String timeBeanId, int currentParamNo,LinkedHashMap<String,Double>SimGradient,
				LinkedHashMap<String,Double>anaGradient,int currentIter) {
			super(measurementId, SimData, paramsToCalibrate, timeBeanId, currentParamNo);
			this.currentParam=paramsToCalibrate.get(currentParamNo);
			this.noOfMetaModelParams=paramsToCalibrate.get(currentParamNo).size()+1;
			this.MetaModelParams=new double[paramsToCalibrate.get(currentParamNo).size()+1];
			
			this.metaParamShouldBe=new double[this.MetaModelParams.length];
			this.timeBeanId=timeBeanId;
			this.currentSimIter=currentIter;
			this.iterBasedIncreasingCost=this.currentSimIter/(1+this.currentSimIter)*c;
			for(Entry<Integer,Measurements> e:AnalyticalData.entrySet()) {
				this.analyticalData.put(e.getKey(),e.getValue().getMeasurements().get(this.measurementId).getVolumes().get(timeBeanId));
				
			}
			
			//this.currentParam=currentParam;
			this.metaParamShouldBe[0]=SimData.get(currentParamNo).getMeasurements().get(this.measurementId).getVolumes().get(timeBeanId)-AnalyticalData.get(currentParamNo).getMeasurements().get(this.measurementId).getVolumes().get(timeBeanId);
			
			
			int i=1;
			
			for(String s:SimGradient.keySet()) {
				metaParamShouldBe[0]+=(SimGradient.get(s)-anaGradient.get(s))*currentParam.get(s);
				metaParamShouldBe[i]=SimGradient.get(s)-anaGradient.get(s);
				i++;
			}
			this.calibrateMetaModel(currentParamNo);
		}
		@Override
		public double calcMetaModel(double analyticalModelPart, LinkedHashMap<String, Double> param) {
			double objective=this.MetaModelParams[0];
			int i=1;
			for(String s:param.keySet()) {
				objective+=this.MetaModelParams[i]*param.get(s);
				i++;
			}
			objective+=analyticalModelPart;
			return objective;
		}
		
		@Override
		public void calibrateMetaModel(final int counter) {
			Calcfc optimization=new Calcfc() {

				@Override
				public double compute(int m, int n, double[] x, double[] constrains) {
					double objective=0;
					MetaModelParams=x;
					for(int i:params.keySet()) {
						objective+=Math.pow(calcMetaModel(analyticalData.get(i), params.get(i))-simData.get(i),2)*calcEuclDistanceBasedWeight(params, i,counter);
					}
					if(addRidgePenalty==true) {
						for(double d:x) {
							objective+=d*d*ridgeCoefficient;
						}
					}
					double metaParamPart=0;
//					for(int i=0;i<MetaModelParams.length;i++) {
//						metaParamPart+=Math.pow(MetaModelParams[i]-metaParamShouldBe[i], 2);
//					}
					iterBasedIncreasingCost=1;
					objective+=metaParamPart*iterBasedIncreasingCost;
					return objective;
				}
				
			};
			double[] x=new double[this.noOfMetaModelParams]; 
			for(int i=0;i<this.noOfMetaModelParams;i++) {
				x[i]=0;
			}
		    CobylaExitStatus result = Cobyla.findMinimum(optimization, this.noOfMetaModelParams, 0, x,0.5,Math.pow(10, -6) ,0, 1500);
		    this.MetaModelParams=x;
			
		}

		@Override
		public String getTimeBeanId() {
			return timeBeanId;
		}

		@Override
		public double[] getMetaModelParams() {
			return MetaModelParams;
		}

		@Override
		public String getMetaModelName() {
			return this.GradientBased_II_MetaModelName;
		}
		@Override
		public double[] getGradientVector() {
			// TODO Auto-generated method stub
			return null;
		}
		@Override
		public Double getanaGradMultiplier() {
			// TODO Auto-generated method stub
			return null;
		}
	}

