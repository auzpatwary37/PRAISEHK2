package calibrator;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import analyticalModel.AnalyticalModel;
import de.xypron.jcobyla.Calcfc;
import matamodels.MetaModel;
import measurements.Measurement;
import measurements.Measurements;


public abstract class OptimizationFunction implements Calcfc  {

	private AnalyticalModel SUE;
	private final int numberOfVariables;
	private final double trustRegionRadius;
	private final LinkedHashMap<String,Double> currentParams;
	private final Map<String,Tuple<Double,Double>> timeBean;
	private final Measurements RealData;
	private final Map<Id<Measurement>,Map<String,MetaModel>> metaModels;
	private double[] hessian;
	private final LinkedHashMap<String,Tuple<Double,Double>> paramLimit;
	
	protected OptimizationFunction(AnalyticalModel sueAssignment, Measurements calibrationMeasurements, Map<Id<Measurement>,Map<String,MetaModel>> metaModels,
		LinkedHashMap<String,Double> currentParams, double TrRadius,
		LinkedHashMap<String,Tuple<Double,Double>> paramLimit) {
		this.SUE=sueAssignment;
		this.numberOfVariables=currentParams.values().size();
		this.trustRegionRadius=TrRadius;
		this.currentParams=currentParams;
		this.timeBean=calibrationMeasurements.getTimeBean();
		this.RealData=calibrationMeasurements;
		this.metaModels=metaModels;
		this.paramLimit=paramLimit;
	}
	
	public void setHessian(double[] hessian) {
		this.hessian = hessian;
	}

	public abstract double calcMetaModelObjective(Map<String,Map<Id<Link>,Double>> linkVolume, LinkedHashMap<String,Double> params);
	
	public abstract double calcMetaModelObjective(Measurements anaMeasurements, LinkedHashMap<String,Double> params);
	
	
	public abstract double[] calcConstrain(double[] x, LinkedHashMap<String,Tuple<Double,Double>> paramLimit);
	
	protected LinkedHashMap<String,Double> ScaleUp(double[] x) {
		LinkedHashMap<String,Double> params=new LinkedHashMap<>();
		int j=0;
		for(String s:this.currentParams.keySet()) {
			params.put(s, (1+x[j]/100)*this.currentParams.get(s));
			j++;
		}

		return params;
	}

	public AnalyticalModel getSUE() {
		return SUE;
	}

	public int getNumberOfVariables() {
		return numberOfVariables;
	}

	public double getTrustRegionRadius() {
		return trustRegionRadius;
	}

	public LinkedHashMap<String, Double> getCurrentParams() {
		return currentParams;
	}

	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return timeBean;
	}

	public Measurements getRealData() {
		return RealData;
	}

	public Map<Id<Measurement>, Map<String, MetaModel>> getMetaModels() {
		return metaModels;
	}

	public double[] getHessian() {
		return hessian;
	}

	public LinkedHashMap<String, Tuple<Double, Double>> getParamLimit() {
		return paramLimit;
	}

}
