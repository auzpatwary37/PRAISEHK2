package ust.hk.praisehk.metamodelcalibration.calibrator;

import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.SimAndAnalyticalGradientCalculator;
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
	 * 
	 * This method keeps the gradient calculation inside the calibrator.
	 * More general, will be implemented as main method for next paramter calculation
	 */
	public LinkedHashMap<String, Double> generateNewParam(AnalyticalModel sue, Measurements simMeasurements,
			SimAndAnalyticalGradientCalculator gradFactory, String metaModelType);
	
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

	public void setTrRadius(double trRadius);


	

}
