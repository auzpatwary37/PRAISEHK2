package calibrator;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.AnalyticalModels.AnalyticalModel;
import ust.hk.praisehk.Counts.CountData;
import ust.hk.praisehk.MetaModels.MetaModel;

public class SimpleOptimizationFunction extends OptimizationFunction{

	protected SimpleOptimizationFunction(AnalyticalModel sueAssignment, CountData countData,
			LinkedHashMap<String, Double> currentParams, double TrRadius,
			HashMap<String, Tuple<Double, Double>> timeBean, LinkedHashMap<String, Tuple<Double, Double>> paramLimit) {
		super(sueAssignment, countData, currentParams, TrRadius, timeBean, paramLimit);
		
	}

	@Override
	public double compute(int n, int m, double[] x, double[] constrains) {
		LinkedHashMap<String, Double>params=ScaleUp(x);
		HashMap<String,HashMap<Id<Link>,Double>> linkVolume=null;
		this.getSUE().clearLinkCarandTransitVolume();
		linkVolume=this.getSUE().perFormSUE(new LinkedHashMap<>(params));
		double objective=calcMetaModelObjective(linkVolume, params);
		int d=0;
		for(double xi:calcConstrain(x,this.getParamLimit())) {
			constrains[d]=xi;
			d++;
		}		
		return objective;
	}

	@Override
	public double calcMetaModelObjective(Map<String, HashMap<Id<Link>, Double>> linkVolume,
			LinkedHashMap<String, Double> params) {
		double objective=0;
		for(String timeBeanId:this.getTimeBean().keySet()) {
			for(Id<Link> linkId:this.getRealData().get(timeBeanId).keySet()) {
				double RealLinkCount=this.getRealData().get(timeBeanId).get(linkId);
				double AnaLyticalModelLinkCount=linkVolume.get(timeBeanId).get(linkId);
				MetaModel metaModel=this.getMetaModels().get(timeBeanId).get(linkId);
				objective+=Math.pow(RealLinkCount-metaModel.calcMetaModel(AnaLyticalModelLinkCount, params),2);
			}
		}
		return objective;
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
	

}
