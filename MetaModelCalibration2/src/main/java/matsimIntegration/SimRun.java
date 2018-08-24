package matsimIntegration;

import java.util.LinkedHashMap;

import org.matsim.core.config.Config;

import analyticalModel.AnalyticalModel;


/**
 * 
 * @author Ashraf
 *
 */
public interface SimRun {
	
	public void run(AnalyticalModel sue,Config configIn,LinkedHashMap<String,Double> params,boolean generateOd,String threadNo,MeasurementsStorage storage);
	
	
}
