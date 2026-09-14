package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

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
import ust.hk.praisehk.metamodelcalibration.fixtures.SyntheticNetworks;
import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;

/**
 * PHASE 8 - characterization of the MSA core of {@link CNLSUEModel}: the step-weight sequence
 * ({@code beta}) and the two error/stopping rules.
 *
 * <p><b>Scope:</b> the car-link / no-transit path only (see {@code noTransit()}). Both methods also run
 * a transit loop whose non-finite guard differs from the car loop's (SUE-5); that half is not covered.
 *
 * <p>These two methods are the mathematical heart of the assignment loop:
 * {@code CheckConvergence} appends the residual norm to {@code error}, and {@code UpdateLinkVolume}
 * uses the last two residual norms to advance {@code beta} and then moves every link volume by
 * {@code (1/beta) * (loaded - current)}. They are driven here in that order, as the production loop
 * does, on a one-link and a four-link synthetic network.</p>
 *
 * <p>The step weight is recovered from <b>observable state only</b>: since the applied move is
 * {@code (1/beta) * (new - old)} and both volumes are readable from the network, the test solves for
 * {@code beta} from the volume change instead of trusting the internal list.</p>
 *
 * @see docs/modernization/REVIEW_REQUIRED.md (SUE-1 .. SUE-5)
 */
class CNLSUEModelMSATest {

	private static final String TB = TimeBeans.ONE_HOUR;

	private static final Id<Link> L1 = SyntheticNetworks.L_AB;
	private static final Id<Link> L2 = SyntheticNetworks.L_BD;
	private static final Id<Link> L3 = SyntheticNetworks.L_AC;

	private static final double ALPHA = 1.9; // CNLSUEModel.alphaMSA
	private static final double GAMMA = 0.1; // CNLSUEModel.gammaMSA

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

		AnalyticalModelLink link(Id<Link> id) {
			return (AnalyticalModelLink) net.getLinks().get(id);
		}

		void setVolume(Id<Link> id, double v) {
			AnalyticalModelLink l = link(id);
			l.resetLinkVolume();
			l.addLinkCarVolume(v);
		}

		double volume(Id<Link> id) {
			return link(id).getLinkCarVolume();
		}

		Map<Id<TransitLink>, Double> noTransit() {
			return new LinkedHashMap<>();
		}
	}

	private static Map<Id<Link>, Double> vols(Id<Link> id, double v) {
		LinkedHashMap<Id<Link>, Double> m = new LinkedHashMap<>();
		m.put(id, v);
		return m;
	}

	// ==================================================================
	// the MSA step weight (beta)
	// ==================================================================

	@Test
	@DisplayName("MSA: at counter 1 beta is reset to 1, so the full loaded volume is applied")
	void firstIterationTakesTheFullStep() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());
		r.setVolume(L1, 0.0);

		r.model.CheckConvergence(vols(L1, 1000.0), r.noTransit(), 1.0, TB, 1);
		boolean converged = r.model.UpdateLinkVolume(vols(L1, 1000.0), r.noTransit(), 1, TB);

		assertEquals(1000.0, r.volume(L1), 1e-9, "beta_1 = 1, so volume becomes exactly the target");
		assertFalse(converged, "the step norm is 1000, which is not < the field tolerance of 1");
	}

	@Test
	@DisplayName("ORACLE MSA: a DECREASING residual grows beta by gamma, so the step weight is 1/(1+gamma)")
	void decreasingErrorGrowsBetaByGamma() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());
		r.setVolume(L1, 0.0);

		r.model.CheckConvergence(vols(L1, 1000.0), r.noTransit(), 1.0, TB, 1);
		r.model.UpdateLinkVolume(vols(L1, 1000.0), r.noTransit(), 1, TB);
		assertEquals(1000.0, r.volume(L1), 1e-9);

		// residual |1000 - 1100| = 100 < 1000 -> beta grows by gamma
		r.model.CheckConvergence(vols(L1, 1100.0), r.noTransit(), 1.0, TB, 2);
		r.model.UpdateLinkVolume(vols(L1, 1100.0), r.noTransit(), 2, TB);

		// beta recovered from the volume change alone: move = (new - old) / beta
		double observedBeta = (1100.0 - 1000.0) / (r.volume(L1) - 1000.0);
		assertEquals(1.0 + GAMMA, observedBeta, 1e-9);
		assertEquals(1000.0 + 100.0 / 1.1, r.volume(L1), 1e-9);
	}

	@Test
	@DisplayName("REVIEW_REQUIRED SUE-1: a non-decreasing residual THROWS on the constructor path - "
			+ "generateRoutesAndOD is what seeds consecutiveSUEErrorIncrease")
	void nonDecreasingErrorThrowsWhenTheCounterWasNeverSeeded() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());
		r.setVolume(L1, 0.0);

		r.model.CheckConvergence(vols(L1, 1000.0), r.noTransit(), 1.0, TB, 1);
		r.model.UpdateLinkVolume(vols(L1, 1000.0), r.noTransit(), 1, TB);

		// residual |1000 - 3000| = 2000, which is NOT < 1000 -> the alpha branch
		r.model.CheckConvergence(vols(L1, 3000.0), r.noTransit(), 1.0, TB, 2);

		assertTrue(r.model.getConsecutiveSUEErrorIncrease().isEmpty(),
				"the constructor does not seed this map; generateRoutesAndOD (line 314) is what does");
		double before = r.volume(L1);
		assertThrows(NullPointerException.class,
				() -> r.model.UpdateLinkVolume(vols(L1, 3000.0), r.noTransit(), 2, TB),
				"get(timeBeanId) returns null and unboxing it throws");
		assertEquals(before, r.volume(L1), 1e-9, "the throw happens before any volume is moved");
	}

	@Test
	@DisplayName("ORACLE MSA: with the counter seeded by hand the alpha branch works and grows beta by alpha")
	void nonDecreasingErrorGrowsBetaByAlphaOnceTheCounterIsSeeded() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());
		r.setVolume(L1, 0.0);

		r.model.CheckConvergence(vols(L1, 1000.0), r.noTransit(), 1.0, TB, 1);
		r.model.UpdateLinkVolume(vols(L1, 1000.0), r.noTransit(), 1, TB);

		// this harness bypasses generateRoutesAndOD, so seed what it seeds in production
		r.model.getConsecutiveSUEErrorIncrease().put(TB, 0.0);

		r.model.CheckConvergence(vols(L1, 3000.0), r.noTransit(), 1.0, TB, 2);
		r.model.UpdateLinkVolume(vols(L1, 3000.0), r.noTransit(), 2, TB);

		double observedBeta = (3000.0 - 1000.0) / (r.volume(L1) - 1000.0);
		assertEquals(1.0 + ALPHA, observedBeta, 1e-9);
		assertEquals(1.0, r.model.getConsecutiveSUEErrorIncrease().get(TB), 1e-9,
				"the counter is incremented, not reset");
	}

	@Test
	@DisplayName("ORACLE: the step weight is 1/beta, compared against the FIELD tolerance")
	void updateReturnIsGovernedByTheFieldTolerance() {
		Rig small = new Rig(SyntheticNetworks.twoNodeNetwork());
		small.setVolume(L1, 0.0);
		small.model.CheckConvergence(vols(L1, 0.5), small.noTransit(), 1.0, TB, 1);
		assertTrue(small.model.UpdateLinkVolume(vols(L1, 0.5), small.noTransit(), 1, TB),
				"step norm 0.5 < the default field tolerance 1");

		// UpdateLinkVolume takes no tolerance argument: only the settable field matters
		Rig tight = new Rig(SyntheticNetworks.twoNodeNetwork());
		tight.setVolume(L1, 0.0);
		tight.model.setTollerance(0.1);
		tight.model.CheckConvergence(vols(L1, 0.5), tight.noTransit(), 1.0, TB, 1);
		assertFalse(tight.model.UpdateLinkVolume(vols(L1, 0.5), tight.noTransit(), 1, TB),
				"the same state fails once the field tolerance is lowered to 0.1");
	}

	// ==================================================================
	// the stopping rule
	// ==================================================================

	@Test
	@DisplayName("ORACLE: CheckConvergence passes at a squared-error norm of exactly 1, and fails at 2")
	void convergenceBoundaryIsTheUnitSquaredErrorNorm() {
		Rig r = new Rig(SyntheticNetworks.twoNodeNetwork());

		r.setVolume(L1, 5.0);
		// (5 - 4)^2 = 1 -> norm 1 <= 1
		assertTrue(r.model.CheckConvergence(vols(L1, 4.0), r.noTransit(), 1.0, TB, 1));

		r.setVolume(L1, 5.0);
		// (5 - 3)^2 = 4 -> norm 2; and 4 < 1 is false, so the third disjunct fails too
		assertEquals(2.0, Math.sqrt(Math.pow(5.0 - 3.0, 2)), 1e-12);
		assertFalse(r.model.CheckConvergence(vols(L1, 3.0), r.noTransit(), 1.0, TB, 2));
	}

	@Test
	@DisplayName("REVIEW_REQUIRED SUE-2: the tolerance ARGUMENT alone forces convergence through the sum==0 disjunct")
	void theToleranceArgumentAloneForcesConvergence() {
		Rig strict = new Rig(SyntheticNetworks.twoNodeNetwork());
		strict.setVolume(L1, 5.0);
		assertFalse(strict.model.CheckConvergence(vols(L1, 3.0), strict.noTransit(), 1.0, TB, 1),
				"norm 2 > 1 and no link is below 1, so nothing converges");

		Rig loose = new Rig(SyntheticNetworks.twoNodeNetwork());
		loose.setVolume(L1, 5.0);
		// the relative test is (error / newVolume) * 100 = 4/3*100 = 133%, which 1000 clears
		assertTrue(loose.model.CheckConvergence(vols(L1, 3.0), loose.noTransit(), 1000.0, TB, 1),
				"identical state, converged only because the argument suppresses the relative disjunct");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED SUE-2: the rule fires when EVERY link is below a squared error of 1, "
			+ "even while the norm is above 1 and a link exceeds the relative tolerance")
	void convergesWhenEveryLinkIsBelowOne() {
		Rig r = new Rig(SyntheticNetworks.twoRouteNetwork());
		r.setVolume(L1, 1.4);
		r.setVolume(L2, 1.4);
		r.setVolume(L3, 1.4);

		LinkedHashMap<Id<Link>, Double> loaded = new LinkedHashMap<>();
		loaded.put(L1, 0.5);
		loaded.put(L2, 0.5);
		loaded.put(L3, 0.5);

		// oracle: three squared errors of 0.81 each
		double each = Math.pow(1.4 - 0.5, 2);
		double norm = Math.sqrt(3 * each);
		assertTrue(each < 1.0, "every link must be below the pointwise threshold");
		assertTrue(norm > 1.0, "the norm must exceed 1, or the first disjunct would explain the result");
		assertTrue(each / 0.5 * 100 > 1.0, "a link must breach the relative tolerance, so sum != 0");

		assertTrue(r.model.CheckConvergence(loaded, r.noTransit(), 1.0, TB, 1),
				"only the third disjunct can explain this: linkBelow1 == 3 == every link");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED SUE-3: an UNLOADED link is EXCLUDED from linkBelow1, so the pointwise "
			+ "disjunct can never fire while any link in the time bean carries no flow")
	void unloadedLinksAreExcludedFromThePointwiseDisjunct() {
		// contrast: three loaded links, each below a squared error of 1 -> the pointwise disjunct fires
		Rig threeLoaded = new Rig(SyntheticNetworks.twoRouteNetwork());
		threeLoaded.setVolume(L1, 1.4);
		threeLoaded.setVolume(L2, 1.4);
		threeLoaded.setVolume(L3, 1.4);
		LinkedHashMap<Id<Link>, Double> allLoaded = new LinkedHashMap<>();
		allLoaded.put(L1, 0.5);
		allLoaded.put(L2, 0.5);
		allLoaded.put(L3, 0.5);
		assertTrue(threeLoaded.model.CheckConvergence(allLoaded, threeLoaded.noTransit(), 1.0, TB, 1));

		// now unload ONE link. Its error is forced to 0 by the `linkVolume.get(linkid) == 0` branch,
		// but the `error < 1` increment lives INSIDE the else, so it never reaches linkBelow1 - which
		// can then only ever total 2, never the 3 that the disjunct compares against.
		Rig oneIdle = new Rig(SyntheticNetworks.twoRouteNetwork());
		oneIdle.setVolume(L1, 0.0);
		oneIdle.setVolume(L2, 1.4);
		oneIdle.setVolume(L3, 1.4);
		LinkedHashMap<Id<Link>, Double> withIdle = new LinkedHashMap<>();
		withIdle.put(L1, 0.0);
		withIdle.put(L2, 0.5);
		withIdle.put(L3, 0.5);

		double each = Math.pow(1.4 - 0.5, 2);
		assertTrue(Math.sqrt(2 * each) > 1.0, "the two loaded links already push the norm past 1");
		assertTrue(each / 0.5 * 100 > 1.0, "and they breach the relative tolerance, so sum != 0");

		assertFalse(oneIdle.model.CheckConvergence(withIdle, oneIdle.noTransit(), 1.0, TB, 1),
				"the idle link contributes 0 to the norm but is not counted as 'below 1', so the "
						+ "pointwise disjunct is unreachable and the state is judged NOT converged");
	}

	// ==================================================================
	// numerical robustness
	// ==================================================================

	@Test
	@DisplayName("REVIEW_REQUIRED SUE-4: an infinite squared error throws, but Inf-Inf yields NaN and "
			+ "is reported as CONVERGED, because the 'error == Double.NaN' guard is dead")
	void nanErrorIsSilentlyReportedAsConverged() {
		Rig infinite = new Rig(SyntheticNetworks.twoNodeNetwork());
		infinite.setVolume(L1, 1.0);
		// (1 - Inf)^2 = +Inf, which the infinity guard does catch
		assertThrows(IllegalArgumentException.class,
				() -> infinite.model.CheckConvergence(vols(L1, Double.POSITIVE_INFINITY),
						infinite.noTransit(), 1.0, TB, 1));

		// (Inf - Inf)^2 = NaN. NaN == Double.NaN is ALWAYS false, so no guard fires; every later
		// comparison is false too, so sum stays 0 and the sum==0 disjunct declares convergence.
		Rig nan = new Rig(SyntheticNetworks.twoNodeNetwork());
		nan.setVolume(L1, Double.POSITIVE_INFINITY);
		assertTrue(nan.model.CheckConvergence(vols(L1, Double.POSITIVE_INFINITY), nan.noTransit(),
				1.0, TB, 1), "an unusable NaN residual is indistinguishable from a converged one");
	}
}
