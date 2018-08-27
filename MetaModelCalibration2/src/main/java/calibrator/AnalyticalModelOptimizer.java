package calibrator;

import java.util.HashMap;
import java.util.LinkedHashMap;

import ust.hk.praisehk.AnalyticalModels.AnalyticalModel;


/**
 * This is an interface for all the analytical model calibrator.
 * 
 * A class has to be written extending the abstract class OptimizationFunction 
 * which is an extension of the Optimfn class of JCOBYLA 
 * The new class has added abstract methods necessary for the optimization
 * 
 * @author Ashraf
 *	The type of SUE model that will be used.
 * @param <anaModel>
 */
public interface AnalyticalModelOptimizer{
	/**
	 * this method performs the optimization of the analytical+metaModel with the real data
	 * @return
	 */
	public LinkedHashMap<String,Double> performOptimization();
	/**
	 * As the Jcobyla takes only double[] as input of parameters,
	 * this method will convert the double to the LinkedHashMap<String,Double> as the input of the 
	 * performSUE method of the Analytical Model
	 * @param x
	 * @return
	 */
	
	public OptimizationFunction getOptimizationFunction();
}
