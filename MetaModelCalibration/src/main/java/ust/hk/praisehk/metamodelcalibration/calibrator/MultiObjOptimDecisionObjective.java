package ust.hk.praisehk.metamodelcalibration.calibrator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class MultiObjOptimDecisionObjective extends SimpleOptimizationFunction{
	private final Map<MeasurementType,Double> bestObj;
	private final Map<MeasurementType,Double> currentObj;
	private final int calibrationParamSize;
	private final int objectiveSize;
	private final int constrainNo;
	
	protected MultiObjOptimDecisionObjective(AnalyticalModel sueAssignment, Measurements realData,
			Map<Id<Measurement>, Map<String, MetaModel>> metaModels, LinkedHashMap<String, Double> currentParams,
			double TrRadius, LinkedHashMap<String, Tuple<Double, Double>> paramLimit, String objectiveType,
			String MetaModelType, ParamReader pReader, int currentIterNo, String fileLoc,Map<MeasurementType, Double> bestObjectives,
			Map<MeasurementType, Double> currentObjectives) {
		super(sueAssignment, realData, metaModels, currentParams, TrRadius, paramLimit, objectiveType, MetaModelType, pReader,
				currentIterNo, fileLoc);
		this.bestObj = bestObjectives;
		this.currentObj = currentObjectives;
		this.calibrationParamSize = currentParams.size();
		this.objectiveSize = bestObj.size();
		this.constrainNo = this.calibrationParamSize*2+1+this.objectiveSize;
	}
	
	@Override
	public double compute(int n, int m, double[] x, double[] constrains) {
		double t = x[x.length-1];
		constrains = this.calcConstrain(x, super.getParamLimit());
		if(this.minObj<t) minObj = t;
		return t;
	}
	
	

	@Override
	public double[] calcConstrain(double[] x, LinkedHashMap<String, Tuple<Double, Double>> paramLimit) {
		double t = x[x.length-1];
		int j=0;
		int k=0;
		LinkedHashMap<String,Double> param = new LinkedHashMap<>();
		double[] c=new double[this.constrainNo];
		double[] y=new double[x.length-1];
		double[] l=new double[x.length-1];
		double[] u=new double[x.length-1];
		for(double d:this.getCurrentParams().values()) {
			y[j]=(1+x[j]/100)*d;
			j++;
		}
		j=0;
		for(Entry<String, Tuple<Double, Double>> tt:paramLimit.entrySet()) {
			Tuple<Double, Double> a = tt.getValue();
			l[j]=a.getFirst();
			u[j]=a.getSecond();
			c[k]=y[j]-l[j];
			c[k+1]=u[j]-y[j];
			param.put(tt.getKey(),y[j]);
			k=k+2;
			j++;
		}
		j=0;
		double trustRegionConst=0;
		for(double d:this.getCurrentParams().values()) {
			trustRegionConst+=Math.pow(d*x[j]*this.getHessian()[j]/100,2);
			j++;
		}
		
		
		c[k]=this.getTrustRegionRadius()-Math.sqrt(trustRegionConst);
		
		
		Measurements anaMeasurements=null;
		if(!this.metaModelType.equals(MetaModel.LinearMetaModelName) && !this.metaModelType.equals(MetaModel.QudaraticMetaModelName)) {
			anaMeasurements=this.getSUE().perFormSUE(this.pReader.ScaleUp(new LinkedHashMap<>(param)),this.getRealData());
		}
		if(anaMeasurements==null) {
			anaMeasurements=this.getRealData().clone();
		}
		Measurements metaMeasurements=this.getRealData().clone();
		for(Measurement m:this.getRealData().getMeasurements().values()) {
			for(String timeBean:m.getVolumes().keySet()) {
				double AnaLyticalModelLinkCount=anaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBean);
				MetaModel metaModel=this.getMetaModels().get(m.getId()).get(timeBean);
				metaMeasurements.getMeasurements().get(m.getId()).putVolume(timeBean, metaModel.calcMetaModel(AnaLyticalModelLinkCount, param));
			}
		}
		
		Map<MeasurementType,Double> obj = ObjectiveCalculator.calcMultiObjective(this.getRealData(), metaMeasurements, super.type);
		
		for(Entry<MeasurementType, Double> d:obj.entrySet()) {
			k++;
			double curr = currentObj.get(d.getKey());
			double bst = this.bestObj.get(d.getKey());
			c[k] = curr+t*(curr-bst)-d.getValue();
		}
		
		return c;
	}

	public int getCalibrationParamSize() {
		return calibrationParamSize;
	}

	public int getConstrainNo() {
		return constrainNo;
	}
	
}
