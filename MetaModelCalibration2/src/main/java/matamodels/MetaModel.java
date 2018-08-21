package matamodels;

import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * @author Ashraf
 */

public interface MetaModel {
	
	
	
	/**
	 * This will calculate the meta-model output
	 * @param
	 * AnalyticalModelPart: if there is an analytical model attached with the meta model, if not 0
	 * param: contains the parameters to be calibrated.
	 */
	public double calcMetaModel(double analyticalModelPart,LinkedHashMap<String,Double> param);
	
	
	
	/**
	 * @return hour
	 */
	public String getTimeBeanId();

	
	/**
	 * Method for extracting the calibrated MetaModel Parameters
	 * @return
	 */
	public double[] getMetaModelParams();
	
}
