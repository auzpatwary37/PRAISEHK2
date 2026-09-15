package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.fixtures.SyntheticNetworks;
import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;

/**
 * PHASE 8c - the <b>lifecycle contract</b> of {@link CNLSUEModel}: what state exists after
 * construction, and which of the two assignment entry points is safe to call on which path.
 *
 * <p>The class has two ways in. {@code new CNLSUEModel(timeBeans)} establishes the per-time-bean
 * containers; {@code generateRoutesAndOD(...)} is a separate call that installs the network, the
 * routes, the demand and the MSA counter. SUE-1 already pinned one consequence of that split - the
 * {@code alpha} branch reads a counter only {@code generateRoutesAndOD} seeds. This class states the
 * split itself, because a "canonical SUE behaviour" claim has to include which calls are legal.</p>
 *
 * <p><b>Observed through behaviour, not internals:</b> {@code beta}, {@code error} and {@code error1}
 * have no accessors, so every assertion here is on something a caller can see - a thrown exception, the
 * returned convergence verdict, or the presence of a container that a public getter exposes.</p>
 *
 * <p>The four facts pinned: the constructor does <b>not</b> install a network; an unloaded model
 * reports <b>converged</b>; the transit container exists but is empty; and {@code UpdateLinkVolume} is
 * not standalone - it reads the residual history {@code CheckConvergence} appends, whose length tracks
 * the counters driven because counter 1 resets it.</p>
 *
 * @see docs/modernization/REVIEW_REQUIRED.md (SUE-1, SUE-7)
 */
class CNLSUEModelLifecycleTest {

	private static final String TB = TimeBeans.ONE_HOUR;

	private static CNLSUEModel freshModel() {
		LinkedHashMap<String, Tuple<Double, Double>> timeBeans = new LinkedHashMap<>();
		timeBeans.put(TB, new Tuple<>(0.0, 3600.0));
		return new CNLSUEModel(timeBeans);
	}

	private static Map<Id<Link>, Double> noCar() {
		return new LinkedHashMap<>();
	}

	private static Map<Id<TransitLink>, Double> noTransit() {
		return new LinkedHashMap<>();
	}

	@Test
	@DisplayName("LIFECYCLE: the constructor does NOT establish a network, so a LOADED car link throws - "
			+ "generateRoutesAndOD is the only thing that populates `networks`")
	void constructorDoesNotEstablishTheNetwork() {
		CNLSUEModel model = freshModel();
		assertTrue(model.getNetworks().isEmpty(), "no network is registered by the constructor");

		LinkedHashMap<Id<Link>, Double> loaded = new LinkedHashMap<>();
		loaded.put(SyntheticNetworks.L_AB, 1000.0);

		assertThrows(NullPointerException.class,
				() -> model.CheckConvergence(loaded, noTransit(), 1.0, TB, 1),
				"the car loop dereferences networks.get(timeBeanId), which is null on this path - "
						+ "so the forward model is not self-contained after construction");
	}

	@Test
	@DisplayName("LIFECYCLE: with nothing loaded the model reports CONVERGED - an un-run assignment is "
			+ "indistinguishable, from the outside, from a solved one")
	void emptyModelReportsConverged() {
		CNLSUEModel model = freshModel();

		// no link breaches the tolerance because there are no links, so the `sum == 0` disjunct fires
		assertTrue(model.CheckConvergence(noCar(), noTransit(), 1.0, TB, 1),
				"an empty state reads as converged through the sum==0 disjunct");
		assertTrue(model.UpdateLinkVolume(noCar(), noTransit(), 1, TB),
				"and the update agrees, because its step norm is 0");
	}

	@Test
	@DisplayName("LIFECYCLE: the transit container IS created by the constructor but left EMPTY, so the "
			+ "transit loop is inert rather than null on the constructor path")
	void transitContainerExistsButIsEmpty() {
		CNLSUEModel model = freshModel();

		assertNotNull(model.getTransitLinks().get(TB),
				"the constructor registers an (empty) transit map per time bean");
		assertTrue(model.getTransitLinks().get(TB).isEmpty());

		assertDoesNotThrow(() -> model.CheckConvergence(noCar(), noTransit(), 1.0, TB, 1),
				"the transit half needs no generateRoutesAndOD to be safe, only to be non-empty");
	}

	@Test
	@DisplayName("LIFECYCLE: UpdateLinkVolume is NOT standalone - it reads the residual history that "
			+ "CheckConvergence appends, and counter 1 resets that history")
	void updateReadsTheResidualHistoryTheCheckAppends() {
		CNLSUEModel model = freshModel();

		// counter 2 reads error.get(1) and error.get(0); nothing has appended a residual yet
		assertThrows(IndexOutOfBoundsException.class,
				() -> model.UpdateLinkVolume(noCar(), noTransit(), 2, TB),
				"the update must be driven after the check, in production order");

		// one check at counter 1 leaves exactly one entry, so counter 2 STILL overruns: the check
		// clears the history at counter 1, so its length is exactly the number of counters driven
		model.CheckConvergence(noCar(), noTransit(), 1.0, TB, 1);
		assertThrows(IndexOutOfBoundsException.class,
				() -> model.UpdateLinkVolume(noCar(), noTransit(), 2, TB),
				"counter 1 resets the history rather than appending to it");
	}
}
