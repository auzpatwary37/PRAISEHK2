package calibrator;

import java.util.LinkedHashMap;
import java.util.Map;


import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;

import analyticalModel.AnalyticalModel;
import matamodels.AnalyticLinearMetaModel;
import matamodels.AnalyticalQuadraticMetaModel;
import matamodels.GradientBaseOptimizedMetaModel;
import matamodels.GradientBasedMetaModel;
import matamodels.GradientOptimizedMetaModel;
import matamodels.LinearMetaModel;
import matamodels.MetaModel;
import matamodels.QuadraticMetaModel;
import measurements.Measurement;
import measurements.Measurements;

public class CalibratorImpl implements Calibrator {

	//Necessary Containers

	private Map<Integer,Measurements> simMeasurements;
	private Map<Integer,Measurements> anaMeasurements;
	private Map<Id<Measurement>,Map<String,MetaModel>>metaModels;
	private Map<Integer,LinkedHashMap<String,Double>>params;

	private Measurements calibrationMeasurements;

	private int iterationNo=0;
	private int currentParamNo=0;

	private LinkedHashMap<String,Double> currentParam=new LinkedHashMap<>();
	private LinkedHashMap<String,Double> trialParam=new LinkedHashMap<>();



	private AnalyticalModel sueAssignment;

	private static final Logger logger=Logger.getLogger(CalibratorImpl.class);

	//Trust region parameters

	private int maxIteration=100;
	private double initialTrRadius=25;
	private double maxTrRadius=2.5*this.initialTrRadius;
	private double minTrRadius=0.001;
	private double maxSuccesiveRejection=4;
	private double minMetaParamChange=.001;
	private double thresholdErrorRatio=.01;
	private String metaModelType=MetaModel.AnalyticalLinearMetaModelName;
	private double trusRegionIncreamentRatio=1.25;
	private double trustRegionDecreamentRatio=0.9;


	public void resetIteration() {
		this.iterationNo=0;
	}


	public void updateAnalyticalModel(AnalyticalModel SUE) {
		this.sueAssignment=SUE;
	}

	public void updateSimMeasurement(Measurements m) {
		this.simMeasurements.put(this.iterationNo, m);
	}

	public void updateAnaMeasurement(Measurements m) {
		this.anaMeasurements.put(this.iterationNo, m);
	}

	public void updateMetaModelType(String type) {
		switch(type) {

		case MetaModel.AnalyticalLinearMetaModelName: this.metaModelType=type ;

		case MetaModel.AnalyticalQuadraticMetaModelName: this.metaModelType=type;

		case MetaModel.LinearMetaModelName: this.metaModelType=type;

		case MetaModel.QudaraticMetaModelName: this.metaModelType=type;

		case MetaModel.GradientBased_I_MetaModelName: this.metaModelType=type;

		case MetaModel.GradientBased_II_MetaModelName: this.metaModelType=type;

		case MetaModel.GradientBased_III_MetaModelName: this.metaModelType=type;

		default : this.metaModelType=MetaModel.AnalyticalLinearMetaModelName;

		}
	}

	/**
	 * The input gradients can be null for non gradient based meatamodels.
	 * The gradient must contain the same measurement IDs and volumes as the calibration measurements
	 * @param simGradient
	 * @param anaGradient
	 * @throws IllegalArgumentException
	 */
	private void createMetaModel(Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>>simGradient,Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>> anaGradient) throws IllegalArgumentException {

		if((this.metaModelType.equals(MetaModel.GradientBased_I_MetaModelName)||this.metaModelType.equals(MetaModel.GradientBased_II_MetaModelName)||this.metaModelType.equals(MetaModel.GradientBased_III_MetaModelName))&& (anaGradient==null||simGradient==null)) {
			logger.error("Cannot create gradient based meta-model without gradient. switching to AnalyticalLinear");
			throw new IllegalArgumentException("Gradient cannot be null");

		}

		for(Measurement m:this.calibrationMeasurements.getMeasurements().values()) {
			for(String timeBeanId:m.getVolumes().keySet()) {

				MetaModel metaModel;

				switch(this.metaModelType) {

				case MetaModel.AnalyticalLinearMetaModelName: metaModel=new AnalyticLinearMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo) ;

				case MetaModel.AnalyticalQuadraticMetaModelName: metaModel=new AnalyticalQuadraticMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo);

				case MetaModel.LinearMetaModelName: metaModel=new LinearMetaModel(m.getId(), this.simMeasurements, this.params, timeBeanId, this.currentParamNo);

				case MetaModel.QudaraticMetaModelName: metaModel=new QuadraticMetaModel(m.getId(), this.simMeasurements, this.params, timeBeanId, this.currentParamNo) ;

				case MetaModel.GradientBased_I_MetaModelName: metaModel=new GradientBasedMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo,simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId));

				case MetaModel.GradientBased_II_MetaModelName: metaModel=new GradientBaseOptimizedMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo, simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId), this.iterationNo);

				case MetaModel.GradientBased_III_MetaModelName: metaModel=new GradientOptimizedMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo, simGradient.get(m.getId()).get(timeBeanId), anaGradient.get(m.getId()).get(timeBeanId), this.iterationNo);

				default : metaModel=new AnalyticLinearMetaModel(m.getId(), this.simMeasurements, this.anaMeasurements, this.params, timeBeanId, this.currentParamNo) ;


				}
			}
		}
	}
}
