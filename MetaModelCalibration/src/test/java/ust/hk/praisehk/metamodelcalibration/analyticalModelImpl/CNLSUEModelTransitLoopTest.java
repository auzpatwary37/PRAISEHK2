package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.fixtures.StubTransitLink;
import ust.hk.praisehk.metamodelcalibration.fixtures.SyntheticNetworks;
import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;

/**
 * PHASE 8b - characterization of the <b>transit half</b> of {@code CNLSUEModel}'s MSA loop, which
 * {@link CNLSUEModelMSATest} deliberately leaves uncovered (SUE-5).
 *
 * <p><b>Why this is separate:</b> {@code CheckConvergence} runs two loops over the same body shape,
 * one for car links and one for transit links, and they do <b>not</b> agree on how a non-finite
 * residual is handled. The car loop guards with
 * {@code error == POSITIVE_INFINITY || error == NEGATIVE_INFINITY} <i>inside</i> the loaded branch and
 * throws; the transit loop guards with {@code error == Double.NaN || error == NEGATIVE_INFINITY}
 * <i>outside</i> the branch. {@code NaN == NaN} is always false, and a square is never
 * {@code NEGATIVE_INFINITY}, so <b>neither transit guard can ever fire</b>.</p>
 *
 * <p>The observable consequence is the point of this class: the same non-finite residual throws for a
 * car link and is silently absorbed - in one case even reported as <i>converged</i> - for a transit
 * link. The car side is asserted here only as the contrast that makes the asymmetry visible.</p>
 *
 * <p>Each rig drives {@code CheckConvergence} then {@code UpdateLinkVolume} in production order and
 * reads back only observable state (passenger counts, the returned convergence verdict).</p>
 *
 * @see docs/modernization/REVIEW_REQUIRED.md (SUE-1 .. SUE-6)
 */
class CNLSUEModelTransitLoopTest {

	private static final String TB = TimeBeans.ONE_HOUR;

	private static final Id<TransitLink> TR1 = Id.create("TR1", TransitLink.class);

	private static final Id<Link> L1 = SyntheticNetworks.L_AB;

	private static final class Rig {
		final CNLSUEModel model;
		final CNLNetwork net;

		Rig(Network matsimNetwork) {
			LinkedHashMap<String, Tuple<Double, Double>> timeBeans = new LinkedHashMap<>();
			timeBeans.put(TB, new Tuple<>(0.0, 3600.0));
			this.model = new CNLSUEModel(timeBeans);
			this.net = SyntheticNetworks.cNLNetwork(matsimNetwork);
			this.model.getNetworks().put(TB, this.net);
		}

		/**
		 * Installs a stub transit link into the live map the constructor already created, and returns
		 * the <b>same</b> instance on later calls - the link must survive so its state can be read back.
		 */
		StubTransitLink transit(Id<TransitLink> id) {
			Map<Id<TransitLink>, TransitLink> links = this.model.getTransitLinks().get(TB);
			TransitLink existing = links.get(id);
			if (existing instanceof StubTransitLink) {
				return (StubTransitLink) existing;
			}
			StubTransitLink link = new StubTransitLink(id.toString());
			links.put(id, link);
			return link;
		}

		void setCarVolume(Id<Link> id, double v) {
			AnalyticalModelLink l = (AnalyticalModelLink) net.getLinks().get(id);
			l.resetLinkVolume();
			l.addLinkCarVolume(v);
		}

		double carVolume(Id<Link> id) {
			return ((AnalyticalModelLink) net.getLinks().get(id)).getLinkCarVolume();
		}
	}

	private static Map<Id<Link>, Double> carVols(Id<Link> id, double v) {
		LinkedHashMap<Id<Link>, Double> m = new LinkedHashMap<>();
		m.put(id, v);
		return m;
	}

	private static Map<Id<TransitLink>, Double> trVols(Id<TransitLink> id, double v) {
		LinkedHashMap<Id<TransitLink>, Double> m = new LinkedHashMap<>();
		m.put(id, v);
		return m;
	}

	/** The car loop is skipped entirely, so the transit disjuncts decide alone. */
	private static Map<Id<Link>, Double> noCar() {
		return new LinkedHashMap<>();
	}

	// ==================================================================
	// the asymmetry: the same non-finite residual, two verdicts
	// ==================================================================

	@Test
	@DisplayName("REVIEW_REQUIRED SUE-5: a +Infinity residual THROWS for a car link but is silently "
			+ "absorbed for a transit link, because the transit guard can never fire")
	void infiniteErrorThrowsForCarAndIsAbsorbedForTransit() {
		// car: the guard inside the loaded branch catches the +Inf squared error
		Rig car = new Rig(SyntheticNetworks.twoNodeNetwork());
		car.setCarVolume(L1, 1.0);
		assertThrows(IllegalArgumentException.class,
				() -> car.model.CheckConvergence(carVols(L1, Double.POSITIVE_INFINITY), new LinkedHashMap<>(),
						1.0, TB, 1),
				"(1 - Inf)^2 = +Inf, and the car loop throws on it");

		// transit: identical +Inf residual, but NaN == NaN is false and a square is never -Inf,
		// so the guard placed outside the branch does not fire
		Rig transit = new Rig(SyntheticNetworks.twoNodeNetwork());
		transit.transit(TR1).setPassangerCount(1.0);
		assertDoesNotThrow(
				() -> transit.model.CheckConvergence(noCar(), trVols(TR1, Double.POSITIVE_INFINITY),
						1.0, TB, 1),
				"the transit half of the loop has no reachable non-finite guard");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED SUE-5: an infinite transit residual is read as NOT converged, not as an "
			+ "error - the loop reports a bad state as merely unfinished")
	void infiniteTransitErrorAgainstAFiniteTargetIsNotConverged() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());
		r.transit(TR1).setPassangerCount(Double.POSITIVE_INFINITY);

		// (Inf - 5)^2 = +Inf; +Inf / 5 * 100 = +Inf, which breaches any finite tolerance, so sum = 1
		boolean converged = r.model.CheckConvergence(noCar(), trVols(TR1, 5.0), 1.0, TB, 1);

		assertFalse(converged,
				"the residual is +Inf, so neither the unit-norm nor the sum==0 disjunct can fire");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED SUE-5: an infinite transit residual against an INFINITE target is "
			+ "reported as CONVERGED, because Inf/Inf is NaN and the sum==0 disjunct then fires")
	void infiniteTransitErrorAgainstAnInfiniteTargetIsReportedAsConverged() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());
		r.transit(TR1).setPassangerCount(1.0);

		// (1 - Inf)^2 = +Inf, so the residual IS infinite and the unit-norm disjunct cannot fire.
		// But the middle test is error/newVolume*100 = Inf/Inf*100 = NaN, so `sum` never increments,
		// and the `sum == 0` disjunct ("no link breached the tolerance") declares convergence.
		boolean converged = r.model.CheckConvergence(noCar(), trVols(TR1, Double.POSITIVE_INFINITY),
				1.0, TB, 1);

		assertTrue(converged,
				"an infinite residual is indistinguishable from a converged one whenever the target "
						+ "volume is also infinite");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED SUE-5 / SUE-4: Inf-Inf yields a NaN transit residual, which is also "
			+ "reported as CONVERGED")
	void nanTransitErrorIsReportedAsConverged() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());
		r.transit(TR1).setPassangerCount(Double.POSITIVE_INFINITY);

		// (Inf - Inf)^2 = NaN. NaN == Double.NaN is always false, every comparison is false, so `sum`
		// stays 0 and `squareSum` is NaN - the same dead-guard shape as the car path in SUE-4.
		boolean converged = r.model.CheckConvergence(noCar(), trVols(TR1, Double.POSITIVE_INFINITY),
				1.0, TB, 1);

		assertTrue(converged, "a NaN residual is reported as converged on the transit path too");
	}

	// ==================================================================
	// the transit half of the update
	// ==================================================================

	@Test
	@DisplayName("ORACLE: the transit half of UpdateLinkVolume applies the same 1/beta step to the "
			+ "passenger count, and its return is governed by the step norm alone")
	void transitUpdateAppliesTheMsaStepWeight() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());
		r.transit(TR1).setPassangerCount(0.0);

		r.model.CheckConvergence(noCar(), trVols(TR1, 500.0), 1.0, TB, 1);
		boolean converged = r.model.UpdateLinkVolume(noCar(), trVols(TR1, 500.0), 1, TB);

		// counter 1 resets beta to 1, so the full (loaded - current) step is taken
		assertEquals(500.0, r.transit(TR1).getPassangerCount(), 1e-9);
		assertFalse(converged, "the step norm is 500, which is not < the field tolerance of 1");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED SUE-5: the transit update advances the passenger count by "
			+ "(loaded - current)/beta on a later iteration, recovering beta from the movement alone")
	void transitUpdateOnALaterIterationUsesTheGrownBeta() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());
		r.transit(TR1).setPassangerCount(0.0);

		// iteration 1: beta resets to 1, full step -> count 500
		r.model.CheckConvergence(noCar(), trVols(TR1, 500.0), 1.0, TB, 1);
		r.model.UpdateLinkVolume(noCar(), trVols(TR1, 500.0), 1, TB);
		assertEquals(500.0, r.transit(TR1).getPassangerCount(), 1e-9);

		// iteration 2: residual |500 - 600| = 100 < 500, so beta grows by gammaMSA to 1.1
		r.model.CheckConvergence(noCar(), trVols(TR1, 600.0), 1.0, TB, 2);
		r.model.UpdateLinkVolume(noCar(), trVols(TR1, 600.0), 2, TB);

		double observedBeta = (600.0 - 500.0) / (r.transit(TR1).getPassangerCount() - 500.0);
		assertEquals(1.1, observedBeta, 1e-9, "the transit update takes the same 1/beta step weight");
		assertEquals(500.0 + 100.0 / 1.1, r.transit(TR1).getPassangerCount(), 1e-9);
	}

	@Test
	@DisplayName("CHARACTERIZATION: the transit branch is skipped entirely when the loaded transit map "
			+ "is empty, so a car-only time bean is unaffected")
	void emptyTransitMapLeavesTheCarPathAlone() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());
		r.setCarVolume(L1, 0.0);

		r.model.CheckConvergence(carVols(L1, 1000.0), new LinkedHashMap<>(), 1.0, TB, 1);
		assertFalse(r.model.UpdateLinkVolume(carVols(L1, 1000.0), new LinkedHashMap<>(), 1, TB));
		assertEquals(1000.0, r.carVolume(L1), 1e-9, "the car path still takes its full first step");
	}
}
