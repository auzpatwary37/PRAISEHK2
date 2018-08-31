package calibrator;

import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;

import matamodels.MetaModel;
import measurements.Measurement;
import measurements.Measurements;

public interface Calibrator {

	public void createMetaModel(Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>>simGradient,Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>> anaGradient, String MetaModelType);
	
	
	
	/**
	 * This will update sim measurements
	 * As sim measurements can not be generated without sim iteration, This do not take iteration no as input 
	 * Rather will assume the input measurement is the measurement of current iteration
	 * @param m
	 */
	public void updateSimMeasurements(Measurements m);
	
	
	/**
	 * This method will draw a random point within the parameter limit provided.
	 * This random point can have different distributions
	 * @param paramLimit
	 * @return
	 */
	public LinkedHashMap<String,Double> drawRandomPoint(LinkedHashMap<String,Tuple<Double,Double>>paramLimit);
	
	
	/**
	 * This will write down measurements details and comparison between these measurements 
	 */
	public void writeMeasurementComparison(String fileLoc);
	
	/**
	 * This method is useful for internal parameter calibration
	 * It will update all the analytical model measurements at once  
	 * @param anaMeasurements a map of iterationNo vs Analytical model Measurements
	 */
	public void updateAnalyticalMeasurement(Map<Integer,Measurements>anaMeasurements);



}
