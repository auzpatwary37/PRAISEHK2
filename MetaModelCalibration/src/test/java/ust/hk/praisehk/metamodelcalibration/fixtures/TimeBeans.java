package ust.hk.praisehk.metamodelcalibration.fixtures;

import java.util.HashMap;
import java.util.Map;

import org.matsim.core.utils.collections.Tuple;

/**
 * Canonical, deterministic time beans used by all synthetic fixtures.
 *
 * <p>These deliberately mirror {@code ParamReader.getDefaultTimeBean()} so that
 * fixtures exercise the same time-bean identifiers as production code, without
 * requiring the Hong Kong production dataset.</p>
 */
public final class TimeBeans {

	private TimeBeans() {
	}

	public static final String BEFORE_MORNING_PEAK = "BeforeMorningPeak";
	public static final String MORNING_PEAK = "MorningPeak";
	public static final String AFTER_MORNING_PEAK = "AfterMorningPeak";
	public static final String EVENING_PEAK = "EveningPeak";
	public static final String AFTER_EVENING_PEAK = "AfterEveningPeak";

	/** One hour, [0, 3600). Capacity scaling in CNLLink divides by 3600. */
	public static final String ONE_HOUR = "Hour1";

	public static Map<String, Tuple<Double, Double>> singleHour() {
		Map<String, Tuple<Double, Double>> timeBean = new HashMap<>();
		timeBean.put(ONE_HOUR, new Tuple<Double, Double>(0., 3600.));
		return timeBean;
	}

	public static Map<String, Tuple<Double, Double>> twoHours() {
		Map<String, Tuple<Double, Double>> timeBean = new HashMap<>();
		timeBean.put("Hour1", new Tuple<Double, Double>(0., 3600.));
		timeBean.put("Hour2", new Tuple<Double, Double>(3600., 7200.));
		return timeBean;
	}

	public static Map<String, Tuple<Double, Double>> fivePeriods() {
		Map<String, Tuple<Double, Double>> timeBean = new HashMap<>();
		timeBean.put(BEFORE_MORNING_PEAK, new Tuple<Double, Double>(0., 25200.));
		timeBean.put(MORNING_PEAK, new Tuple<Double, Double>(25200., 36000.));
		timeBean.put(AFTER_MORNING_PEAK, new Tuple<Double, Double>(36000., 57600.));
		timeBean.put(EVENING_PEAK, new Tuple<Double, Double>(57600., 72000.));
		timeBean.put(AFTER_EVENING_PEAK, new Tuple<Double, Double>(72000., 86400.));
		return timeBean;
	}
}
