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

	//----------------------GetterSetters--------------------------------------------------------------

	/**
	 * This Trust Region Radius is important for gradient calculation
	 * @return
	 */
	public double getTrRadius();


	public String getObjectiveType();


	public double getMaxTrRadius();


	public double getMinTrRadius();


	public double getSuccessiveRejection();


	public double getMaxSuccesiveRejection();


	public double getMinMetaParamChange();


	public double getThresholdErrorRatio();


	public double getTrusRegionIncreamentRatio();


	public double getTrustRegionDecreamentRatio();


	public String getFileLoc();


	public boolean isShouldPerformInternalParamCalibration();


	public int getCurrentParamNo();


	public LinkedHashMap<String, Double> getCurrentParam();


	public void setObjectiveType(String objectiveType);


	public void setMaxTrRadius(double maxTrRadius);


	public void setMinTrRadius(double minTrRadius);


	public void setMinMetaParamChange(double minMetaParamChange);


	public void setThresholdErrorRatio(double thresholdErrorRatio);


	public void setTrusRegionIncreamentRatio(double trusRegionIncreamentRatio);


	public void setTrustRegionDecreamentRatio(double trustRegionDecreamentRatio);



}
