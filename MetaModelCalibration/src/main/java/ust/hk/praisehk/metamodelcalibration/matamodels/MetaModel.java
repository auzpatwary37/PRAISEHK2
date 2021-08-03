package ust.hk.praisehk.metamodelcalibration.matamodels;

import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;

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
	
	public String getMetaModelName();
	
	public static final String LinearMetaModelName="Linear";
	public static final String AnalyticalLinearMetaModelName="AnalyticalLinear";
	public static final String QudaraticMetaModelName="Quadratic";
	public static final String AnalyticalQuadraticMetaModelName="AnalyticalQuadratic";
	public static final String GradientBased_I_MetaModelName="GD-I";
	public static final String GradientBased_II_MetaModelName="GD-II";
	public static final String GradientBased_III_MetaModelName="GD-III";
	
	public Id<Measurement> getMeasurementId();
	
	/**
	 * This function is currently implemented for analytical linear metamodel.
	 * There is no point implementing this model for the generic linear and quadratic metamodels.
	 * The gradient based metamodels and the quadratic metamodels however can benefit. The function is not implemented for those metamodels 
	 * as of now. //Aug21, Ashraf. 
	 * @return
	 */
	public double[] getGradientVector();
	
	
	/**
	 * This function is currently implemented for analytical linear metamodel.
	 * There is no point implementing this model for the generic linear and quadratic metamodels.
	 * The gradient based metamodels and the quadratic metamodels however can benefit. The function is not implemented for those metamodels 
	 * as of now. //Aug21, Ashraf. 
	 * @return
	 */
	public Double getanaGradMultiplier();
}
