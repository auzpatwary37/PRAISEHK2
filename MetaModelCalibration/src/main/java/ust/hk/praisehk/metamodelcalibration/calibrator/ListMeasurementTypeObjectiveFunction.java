package ust.hk.praisehk.metamodelcalibration.calibrator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class ListMeasurementTypeObjectiveFunction extends SimpleOptimizationFunction{
private final List<MeasurementType> objKeys;
	
	
	protected ListMeasurementTypeObjectiveFunction(AnalyticalModel sueAssignment, Measurements realData,
			Map<Id<Measurement>, Map<String, MetaModel>> metaModels, LinkedHashMap<String, Double> currentParams,
			double TrRadius, LinkedHashMap<String, Tuple<Double, Double>> paramLimit, String objectiveType,
			String MetaModelType, ParamReader pReader, int currentIterNo, String fileLoc, List<MeasurementType> objKeys) {
		super(sueAssignment, realData, metaModels, currentParams, TrRadius, paramLimit, objectiveType, MetaModelType, pReader,
				currentIterNo, fileLoc);
		this.objKeys = objKeys;
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public double calcMetaModelObjective(Measurements anaMeasurements, LinkedHashMap<String, Double> params) {
		double objective=0;
		if(anaMeasurements==null) {
			anaMeasurements=this.getRealData().clone();
		}
		Measurements metaMeasurements=this.getRealData().clone();
		for(Measurement m:this.getRealData().getMeasurements().values()) {
			for(String timeBean:m.getVolumes().keySet()) {
				double AnaLyticalModelLinkCount=anaMeasurements.getMeasurements().get(m.getId()).getVolumes().get(timeBean);
				MetaModel metaModel=this.getMetaModels().get(m.getId()).get(timeBean);
				metaMeasurements.getMeasurements().get(m.getId()).putVolume(timeBean, metaModel.calcMetaModel(AnaLyticalModelLinkCount, params));
			}
		}
		for(Entry<MeasurementType, Double> d:ObjectiveCalculator.calcMultiObjective(this.getRealData(), metaMeasurements,type).entrySet())
		if(objKeys.contains(d.getKey()))objective+=d.getValue();
		if(objective<minObj) minObj = objective;
		return objective;
	}


}
