package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.fixtures.FixtureA_TrivialLink;
import ust.hk.praisehk.metamodelcalibration.fixtures.Params;
import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;

/**
 * PHASE 2 - mathematical leaf test: CNLLink link-performance (BPR).
 *
 * <p>Two kinds of test are present, as required by the mission:</p>
 * <ol>
 *   <li><b>independent oracle</b> - hand-calculated
 *       {@code t = t0*(1 + alpha*(v/c)^beta)} values;</li>
 *   <li><b>characterization</b> - the actual legacy behaviour, including the
 *       {@code train} branch, captured without changing the formula.</li>
 * </ol>
 *
 * <p>Nothing in {@code CNLLink} is modified by this test.</p>
 *
 * @see docs/modernization/TEST_MATRIX.md
 * @see docs/modernization/REVIEW_REQUIRED.md (LINK-1: train branch units)
 */
class CNLLinkTravelTimeTest {

	private static final double EPS = 1e-9;

	private static Tuple<Double, Double> oneHour() {
		return TimeBeans.singleHour().get(TimeBeans.ONE_HOUR);
	}

	@Test
	@DisplayName("ORACLE: zero flow gives exactly free-flow travel time")
	void freeFlowTravelTimeAtZeroFlow() {
		CNLLink link = FixtureA_TrivialLink.cNLLink();
		double tt = link.getLinkTravelTime(oneHour(), Params.carOnly(),
				Params.analyticalInternalTextbook());
		assertEquals(FixtureA_TrivialLink.FREE_FLOW_TRAVEL_TIME, tt, EPS);
	}

	@Test
	@DisplayName("ORACLE: BPR at v=900, c=1800, alpha=0.15, beta=4")
	void bprTravelTimeAtHalfCapacity() {
		CNLLink link = FixtureA_TrivialLink.cNLLink();
		link.addLinkCarVolume(FixtureA_TrivialLink.CAR_VOLUME);

		double tt = link.getLinkTravelTime(oneHour(), Params.carOnly(),
				Params.analyticalInternalTextbook());

		// 50 * (1 + 0.15 * (900/1800)^4) = 50 * 1.009375 = 50.46875
		assertEquals(50.46875, tt, EPS);
		assertEquals(
				FixtureA_TrivialLink.expectedBprTravelTime(900., 0.15, 4., 1.),
				tt, EPS);
	}

	@Test
	@DisplayName("ORACLE: congestion grows monotonically and exceeds free-flow time")
	void travelTimeIncreasesWithVolume() {
		CNLLink link = FixtureA_TrivialLink.cNLLink();
		double previous = link.getLinkTravelTime(oneHour(), Params.carOnly(),
				Params.analyticalInternalTextbook());
		for (double volume : new double[] { 100., 500., 900., 1800., 3600. }) {
			link.clearLinkCarFlow();
			link.addLinkCarVolume(volume);
			double tt = link.getLinkTravelTime(oneHour(), Params.carOnly(),
					Params.analyticalInternalTextbook());
			assertTrue(tt > previous, "travel time must increase with volume");
			previous = tt;
		}
	}

	@Test
	@DisplayName("ORACLE: transit volume enters the BPR load via CapacityMultiplier")
	void transitVolumeContributesToPcuLoad() {
		CNLLink link = FixtureA_TrivialLink.cNLLink();
		link.addLinkCarVolume(900.);
		link.addLinkTransitVolume(1800.);

		double tt = link.getLinkTravelTime(oneHour(), Params.carOnly(1.),
				Params.analyticalInternalTextbook());

		// load = 900 + 1800*1 = 2700 ; 2700/1800 = 1.5
		// 50 * (1 + 0.15*1.5^4) = 50 * 1.759375 = 87.96875
		assertEquals(87.96875, tt, EPS);
	}

	@Test
	@DisplayName("ORACLE: residual car volume enters the BPR load unscaled")
	void residualVolumeContributesToPcuLoad() {
		CNLLink link = FixtureA_TrivialLink.cNLLink();
		link.setResidualCarVolume(900.);

		double tt = link.getLinkTravelTime(oneHour(), Params.carOnly(),
				Params.analyticalInternalTextbook());
		assertEquals(50.46875, tt, EPS);
	}

	@Test
	@DisplayName("CHARACTERIZATION: CapacityMultiplier scales both load and capacity")
	void capacityMultiplierScalesLoadAndCapacity() {
		CNLLink link = FixtureA_TrivialLink.cNLLink();
		link.addLinkCarVolume(900.);

		// Multiplier 0.5 halves capacity to 900 -> v/c = 1 -> t = 50*(1+0.15) = 57.5
		double tt = link.getLinkTravelTime(oneHour(), Params.carOnly(0.5),
				Params.analyticalInternalTextbook());
		assertEquals(57.5, tt, EPS);
	}

	@Test
	@DisplayName("ORACLE: gcRatio scales effective capacity")
	void gcRatioScalesCapacity() {
		CNLLink link = FixtureA_TrivialLink.cNLLink();
		link.setGcRatio(0.5);
		link.addLinkCarVolume(900.);

		// capacity = 1800 * 1(hour) * 1(mult) * 0.5 = 900 -> v/c = 1 -> 57.5
		double tt = link.getLinkTravelTime(oneHour(), Params.carOnly(),
				Params.analyticalInternalTextbook());
		assertEquals(57.5, tt, EPS);
	}

	@Test
	@DisplayName("ORACLE: BPR alpha/beta come from the analytical internal params")
	void bprExponentsComeFromAnalyticalParams() {
		CNLLink link = FixtureA_TrivialLink.cNLLink();
		link.addLinkCarVolume(900.);

		double tt = link.getLinkTravelTime(oneHour(), Params.carOnly(),
				Params.analyticalInternal(0.10, 2.0));
		// 50 * (1 + 0.10*0.5^2) = 50 * 1.025 = 51.25
		assertEquals(51.25, tt, EPS);
	}

	@Test
	@DisplayName("CHARACTERIZATION (REVIEW_REQUIRED LINK-1): train links use a different, "
			+ "flow-independent formula with a 3.6x unit discontinuity")
	void trainLinkUsesSeparateBranch() {
		LinkedHashMap<String, Double> params = Params.carOnly();
		LinkedHashMap<String, Double> anaParams = Params.analyticalInternalTextbook();

		CNLLink carLink = new CNLLink(
				FixtureA_TrivialLink.network().getLinks().get(FixtureA_TrivialLink.LINK_ID),
				FixtureA_TrivialLink.network());
		carLink.addLinkCarVolume(900.);
		double carTime = carLink.getLinkTravelTime(oneHour(), params, anaParams);

		CNLLink trainLink = new CNLLink(
				FixtureA_TrivialLink.network(new HashSet<>(Arrays.asList("train")))
						.getLinks().get(FixtureA_TrivialLink.LINK_ID),
				FixtureA_TrivialLink.network(new HashSet<>(Arrays.asList("train"))));
		trainLink.addLinkCarVolume(900.);
		double trainTime = trainLink.getLinkTravelTime(oneHour(), params, anaParams);

		// Car branch: length/freespeed = 1000/20 = 50 s.
		assertEquals(50.46875, carTime, EPS);
		// Train branch: length/(freespeed*1000/3600) = 1000/5.5555... = 180 s.
		// The train branch ignores volume, capacity, alpha and beta entirely.
		assertEquals(180., trainTime, EPS);
		// Documented discontinuity: the same physical link yields 3.6x the
		// free-flow time purely because of the mode test. See REVIEW_REQUIRED LINK-1.
		assertEquals(3.6, trainTime / (FixtureA_TrivialLink.LENGTH / FixtureA_TrivialLink.FREESPEED), EPS);
	}
}
