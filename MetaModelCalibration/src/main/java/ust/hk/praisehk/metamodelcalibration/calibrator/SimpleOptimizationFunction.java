package ust.hk.praisehk.metamodelcalibration.calibrator;

import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;



public class SimpleOptimizationFunction extends OptimizationFunction{
	private final String metaModelType;
	private final String type;
	protected SimpleOptimizationFunction(AnalyticalModel sueAssignment, Measurements realData, Map<Id<Measurement>, Map<String, MetaModel>> metaModels,
			LinkedHashMap<String, Double> currentParams, double TrRadius,
			LinkedHashMap<String, Tuple<Double, Double>> paramLimit,String objectiveType,String MetaModelType) {
		super(sueAssignment, realData,metaModels , currentParams, TrRadius, paramLimit);
		this.type=objectiveType;
		this.metaModelType=MetaModelType;
	}

	@Override
	public double compute(int n, int m, double[] x, double[] constrains) {
		LinkedHashMap<String, Double>params=ScaleUp(x);
		Map<String,Map<Id<Link>,Double>> linkVolume=null;
		this.getSUE().clearLinkCarandTransitVolume();
		
		if(!this.metaModelType.equals(MetaModel.LinearMetaModelName) && !this.metaModelType.equals(MetaModel.QudaraticMetaModelName)) {
			linkVolume=this.getSUE().perFormSUE(new LinkedHashMap<>(params));
		}
		double objective=calcMetaModelObjective(linkVolume, params);
		int d=0;
		for(double xi:calcConstrain(x,this.getParamLimit())) {
			constrains[d]=xi;
			d++;
		}		
		return objective;
	}

	@Override
	public double calcMetaModelObjective(Map<String, Map<Id<Link>, Double>> linkVolume,
			LinkedHashMap<String, Double> params) {
		
		Measurements anaMeasurements=this.getRealData().clone();
		if(linkVolume!=null) {
			anaMeasurements.updateMeasurements(linkVolume);
		}
		return this.calcMetaModelObjective(anaMeasurements, params);
	}

	@Override
	public double[] calcConstrain(double[] x, LinkedHashMap<String, Tuple<Double, Double>> paramLimit) {
		int noOfConst=2*x.length+1;
		int j=0;
		int k=0;
		double[] c=new double[noOfConst];
		double[] y=new double[x.length];
		double[] l=new double[x.length];
		double[] u=new double[x.length];
		for(double d:this.getCurrentParams().values()) {
			y[j]=(1+x[j]/100)*d;
			j++;
		}
		j=0;
		for(Tuple<Double,Double> t:paramLimit.values()) {
			l[j]=t.getFirst();
			u[j]=t.getSecond();
			c[k]=y[j]-l[j];
			c[k+1]=u[j]-y[j];
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
		
		return c;
	}

	@Override
	public double calcMetaModelObjective(Measurements anaMeasurements, LinkedHashMap<String, Double> params) {
		double objective=0;
		Measurements metaMeasurements=this.getRealData().clone();
		for(Measurement m:this.getRealData().getMeasurements().values()) {
			for(String timeBean:m.getVolumes().keySet()) {
				double AnaLyticalModelLinkCount=anaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBean);
				MetaModel metaModel=this.getMetaModels().get(m.getId()).get(timeBean);
				metaMeasurements.getMeasurements().get(m.getId()).addVolume(timeBean, metaModel.calcMetaModel(AnaLyticalModelLinkCount, params));
			}
		}
		
		objective=ObjectiveCalculator.calcObjective(this.getRealData(), metaMeasurements,type);
		return objective;
	}
	

}
