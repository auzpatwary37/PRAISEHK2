package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.LinkedHashMap;

import org.matsim.core.config.Config;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;


/**
 * 
 * @author Ashraf
 *
 */
public interface SimRun {
	
	public Measurements run(AnalyticalModel sue,Config configIn,LinkedHashMap<String,Double> params,boolean generateOd,String threadNo,MeasurementsStorage storage);

	public Measurements getOutputMeasurements();
	
	
}
