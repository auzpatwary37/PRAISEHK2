package ust.hk.praisehk.metamodelcalibration.calibrator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class SingleClusteredMultiObjectiveOptimizationFunction extends SimpleOptimizationFunction{
private final Set<MeasurementType> objKeys;
	
	
	protected SingleClusteredMultiObjectiveOptimizationFunction(AnalyticalModel sueAssignment, Measurements realData,
			Map<Id<Measurement>, Map<String, MetaModel>> metaModels, LinkedHashMap<String, Double> currentParams,
			double TrRadius, LinkedHashMap<String, Tuple<Double, Double>> paramLimit, String objectiveType,
			String MetaModelType, ParamReader pReader, int currentIterNo, String fileLoc, Set<MeasurementType> objKey) {
		super(sueAssignment, realData, metaModels, currentParams, TrRadius, paramLimit, objectiveType, MetaModelType, pReader,
				currentIterNo, fileLoc);
		this.objKeys = objKey;
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
		
		objective=ObjectiveCalculator.calcObjective(this.getRealData(), metaMeasurements,type,objKeys);
		if(objective<minObj) minObj = objective;
		return objective;
	}
}
