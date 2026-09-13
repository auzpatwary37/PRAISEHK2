package ust.hk.praisehk.metamodelcalibration.fixtures;

import java.util.LinkedHashMap;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;

/**
 * Canonical parameter vectors for the synthetic fixtures.
 *
 * <p>Values are copied from {@code src/main/resources/paramReaderTrial1.csv}
 * where a parameter appears there, so that fixtures exercise realistic
 * magnitudes without depending on the production CSV file on disk.</p>
 */
public final class Params {

	private Params() {
	}

	/** Road-only parameters with {@code CapacityMultiplier = 1}. */
	public static LinkedHashMap<String, Double> carOnly() {
		LinkedHashMap<String, Double> p = new LinkedHashMap<>();
		p.put(AnalyticalModel.MarginalUtilityofTravelCarName, -200.);
		p.put(AnalyticalModel.MarginalUtilityofDistanceCarName, -0.0075);
		p.put(AnalyticalModel.MarginalUtilityofMoneyName, 1.);
		p.put(AnalyticalModel.DistanceBasedMoneyCostCarName, 0.);
		p.put(AnalyticalModel.ModeConstantCarName, 0.);
		p.put(AnalyticalModel.MarginalUtilityofPerformName, 100.);
		p.put(AnalyticalModel.CapacityMultiplierName, 1.);
		return p;
	}

	/** Car parameters with an explicit capacity multiplier. */
	public static LinkedHashMap<String, Double> carOnly(double capacityMultiplier) {
		LinkedHashMap<String, Double> p = carOnly();
		p.put(AnalyticalModel.CapacityMultiplierName, capacityMultiplier);
		return p;
	}

	/**
	 * Analytical-model internal parameters. These are the parameters calibrated
	 * by {@code AnalyticalModel.calibrateInternalParams} and are deliberately
	 * kept separate from the calibration decision variables.
	 */
	public static LinkedHashMap<String, Double> analyticalInternal(double bprAlpha, double bprBeta) {
		LinkedHashMap<String, Double> a = new LinkedHashMap<>();
		a.put(CNLSUEModel.BPRalphaName, bprAlpha);
		a.put(CNLSUEModel.BPRbetaName, bprBeta);
		a.put(CNLSUEModel.LinkMiuName, 1.);
		a.put(CNLSUEModel.ModeMiuName, 1.);
		a.put(CNLSUEModel.TransferalphaName, 0.);
		a.put(CNLSUEModel.TransferbetaName, 0.);
		return a;
	}

	/** Textbook BPR exponents used by the hand-calculated oracle tests. */
	public static LinkedHashMap<String, Double> analyticalInternalTextbook() {
		return analyticalInternal(0.15, 4.0);
	}
}
