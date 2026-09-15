package analyticalModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;

import differentiation.CentralDifferenceOracle;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;

/**
 * Characterization of the BPR travel-time sensitivity leaf, {@code GradientUtils.getLinkTravelTimeGrad}.
 *
 * <p>This is the first arrow of the derivative chain: link flow -> link travel time. It is the
 * cheapest place to check whether ODEstimation's forward sensitivity propagation actually
 * differentiates the travel-time function it is paired with, because both the function
 * ({@code CNLLink.getLinkTravelTime}) and its claimed derivative are reachable directly.
 *
 * <p>The fixture is a single link with round numbers so that the expected derivative can be
 * written down by hand: length 1000 m, free speed 20 m/s, so free-flow time is exactly 50 s;
 * capacity 2000 veh/h; one-hour time bean, so the capacity scaling factor is exactly 1. The
 * remaining inputs are chosen so that the hand-derived derivative is exact in binary floating
 * point where possible, and the assertions therefore compare against the closed form rather than
 * against a recorded golden value.
 *
 * <p>No file, network or MATLAB access; the link and network are built in memory and every
 * parameter is set explicitly.
 */
class BprDerivativeTest {

	private static final double LENGTH = 1000.0;
	private static final double FREESPEED = 20.0;
	private static final double CAPACITY = 2000.0;
	private static final double LANES = 1.0;
	private static final Tuple<Double, Double> ONE_HOUR = new Tuple<>(0.0, 3600.0);

	private static final double ALPHA = 0.15;
	private static final double BETA = 4.0;
	private static final double CAR_VOLUME = 1000.0;
	private static final double TRANSIT_VOLUME = 500.0;

	private static final double FREE_FLOW_TIME = LENGTH / FREESPEED;

	private static CNLLink link() {
		Network network = NetworkUtils.createNetwork();
		Node from = NetworkUtils.createAndAddNode(network, Id.createNodeId("from"), new Coord(0.0, 0.0));
		Node to = NetworkUtils.createAndAddNode(network, Id.createNodeId("to"), new Coord(LENGTH, 0.0));
		Link link = NetworkUtils.createAndAddLink(network, Id.createLinkId("l1"), from, to, LENGTH, FREESPEED,
				CAPACITY, LANES);
		CNLLink cnl = new CNLLink(link, network);
		cnl.clearLinkCarFlow();
		cnl.addLinkCarVolume(CAR_VOLUME);
		return cnl;
	}

	private static LinkedHashMap<String, Double> params(double capacityMultiplier) {
		LinkedHashMap<String, Double> params = new LinkedHashMap<>();
		params.put(AnalyticalModel.CapacityMultiplierName, capacityMultiplier);
		return params;
	}

	private static LinkedHashMap<String, Double> anaParams() {
		LinkedHashMap<String, Double> anaParams = new LinkedHashMap<>();
		anaParams.put(CNLSUEModel.BPRalphaName, ALPHA);
		anaParams.put(CNLSUEModel.BPRbetaName, BETA);
		return anaParams;
	}

	/** The implemented sensitivity, read from coordinate 0 of the returned vector for a seed of 1. */
	private static double implementedSensitivity(CNLLink link, double capacityMultiplier, double seed) {
		Map<Id<Link>, double[]> linkGradient = new HashMap<>();
		linkGradient.put(link.getId(), new double[] {seed});
		return GradientUtils
				.getLinkTravelTimeGrad(link, anaParams(), linkGradient, capacityMultiplier, ONE_HOUR)[0];
	}

	/**
	 * The implemented local partial, transcribed from the legacy expression, so the test can show
	 * what the code claims the derivative is independently of what it claims to differentiate.
	 */
	private static double implementedLocalPartial(double carVolume, double transitVolume, double capacityMultiplier) {
		double flow = carVolume + transitVolume;
		double cap = CAPACITY * capacityMultiplier;
		return ALPHA * BETA * FREE_FLOW_TIME / Math.pow(cap, BETA) * Math.pow(flow, BETA - 1) / 3600;
	}

	/** The true derivative of the paired travel-time function with respect to car volume. */
	private static double trueLocalPartial(double flow, double capacityMultiplier) {
		double cap = CAPACITY * capacityMultiplier;
		return FREE_FLOW_TIME * ALPHA * BETA * Math.pow(flow, BETA - 1) / Math.pow(cap, BETA);
	}

	// ------------------------------------------------------------------
	// The oracle itself
	// ------------------------------------------------------------------

	@Test
	@DisplayName("the central-difference oracle recovers the derivative of a known function")
	void oracleRecoversTheClosedFormDerivative() {
		DoubleUnaryOperator cube = x -> x * x * x;
		double minimumError = CentralDifferenceOracle.minimumAbsoluteError(cube, 2.0, 12.0);
		assertTrue(minimumError < 1e-7,
				"oracle should reach the closed-form derivative 3x^2 = 12 within 1e-7, best was " + minimumError);
	}

	// ------------------------------------------------------------------
	// What the implementation does (characterization: pins observed behaviour)
	// ------------------------------------------------------------------

	@Test
	@DisplayName("the implemented sensitivity equals the transribed legacy expression, including its 1/3600 factor")
	void implementedSensitivityMatchesTheTranscribedExpression() {
		CNLLink link = link();
		double observed = implementedSensitivity(link, 1.0, 1.0);
		double transcribed = implementedLocalPartial(CAR_VOLUME, 0.0, 1.0);
		assertEquals(transcribed, observed, Math.abs(transcribed) * 1e-12,
				"the value returned by getLinkTravelTimeGrad is exactly the legacy expression");
	}

	@Test
	@DisplayName("the sensitivity is linear in the seed, i.e. it is a Jacobian-vector product (forward mode)")
	void sensitivityIsLinearInTheSeed() {
		CNLLink link = link();
		double unit = implementedSensitivity(link, 1.0, 1.0);
		double scaled = implementedSensitivity(link, 1.0, -7.5);
		assertEquals(-7.5 * unit, scaled, Math.abs(unit) * 1e-12,
				"a forward-mode propagation multiplies the seed by the local partial");
	}

	// ------------------------------------------------------------------
	// Where the implementation disagrees with the function it differentiates
	// ------------------------------------------------------------------

	@Test
	@Disabled("REVIEW_REQUIRED DIFF-1: measured 3600x disagreement. Central difference of the paired "
			+ "CNLLink.getLinkTravelTime gives 1.875e-3 (stable at h=1e-1..1e-3) against a returned sensitivity of "
			+ "5.208e-7. Enable once DIFF-1 is resolved.")
	@DisplayName("REVIEW_REQUIRED DIFF-1: the BPR sensitivity should agree with the finite difference of "
			+ "CNLLink.getLinkTravelTime")
	void implementedSensitivityAgreesWithThePairedTravelTimeFunction() {
		CNLLink link = link();
		double analytic = implementedSensitivity(link, 1.0, 1.0);

		// Perturb the quantity the travel-time function actually reads, and only that quantity.
		DoubleUnaryOperator travelTime = carVolume -> {
			link.clearLinkCarFlow();
			link.addLinkCarVolume(carVolume);
			return link.getLinkTravelTime(ONE_HOUR, params(1.0), anaParams());
		};

		double tolerance = Math.abs(analytic) * 0.01;
		double bestError = CentralDifferenceOracle.minimumAbsoluteError(travelTime, CAR_VOLUME, analytic);
		assertTrue(bestError <= tolerance,
				CentralDifferenceOracle.report("car volume", "link travel time", travelTime, CAR_VOLUME, analytic)
						+ "\n  best finite-difference error " + bestError + " exceeds tolerance " + tolerance);
	}

	@Test
	@Disabled("REVIEW_REQUIRED DIFF-2: measured 8533x disagreement with transit volume present. Central "
			+ "difference gives 9.375e-4 against a returned sensitivity of 1.0986e-7. Enable once DIFF-2 is resolved.")
	@DisplayName("REVIEW_REQUIRED DIFF-2: the BPR sensitivity should account for the capacity multiplier applied "
			+ "to transit volume")
	void implementedSensitivityAccountsForTheCapacityMultiplierOnTransitVolume() {
		double capacityMultiplier = 2.0;
		CNLLink link = link();
		link.addLinkTransitVolume(TRANSIT_VOLUME);

		double analytic = implementedSensitivity(link, capacityMultiplier, 1.0);

		DoubleUnaryOperator travelTime = carVolume -> {
			link.clearLinkCarFlow();
			link.addLinkCarVolume(carVolume);
			return link.getLinkTravelTime(ONE_HOUR, params(capacityMultiplier), anaParams());
		};

		double tolerance = Math.abs(analytic) * 0.01;
		double bestError = CentralDifferenceOracle.minimumAbsoluteError(travelTime, CAR_VOLUME, analytic);
		assertTrue(bestError <= tolerance,
				CentralDifferenceOracle.report("car volume", "link travel time", travelTime, CAR_VOLUME, analytic)
						+ "\n  best finite-difference error " + bestError + " exceeds tolerance " + tolerance);
	}

	// ------------------------------------------------------------------
	// The size of the disagreement, recorded so the diagnosis does not depend on re-deriving it
	// ------------------------------------------------------------------

	@Test
	@DisplayName("characterization: the implemented partial is 1/3600 of the true partial when only car flow is present")
	void implementedPartialIsSmallerByAFactorOf3600() {
		CNLLink link = link();
		double analytic = implementedSensitivity(link, 1.0, 1.0);
		double truePartial = trueLocalPartial(CAR_VOLUME, 1.0);
		assertEquals(3600.0, truePartial / analytic, 1e-6,
				"the ratio between the true and implemented local partial is exactly 3600");
	}
}
