package ust.hk.praisehk.metamodelcalibration.calibrator;

import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public interface Calibrator {	
	
	/**
	 * This will write down measurements details and comparison between these measurements 
	 */
	public void writeMeasurementComparison(String fileLoc);
	
	
	/**
	 * This is the most important method algorithm of this class
	 * The gradients can be null in case of non-gradient Based metaModel Type
	 */
	public LinkedHashMap<String, Double> generateNewParam(AnalyticalModel sue, Measurements simMeasurements,
			Map<Id<Measurement>, Map<String, LinkedHashMap<String, Double>>> simGradient,
			Map<Id<Measurement>, Map<String, LinkedHashMap<String, Double>>> anaGradient, String metaModelType);



}
