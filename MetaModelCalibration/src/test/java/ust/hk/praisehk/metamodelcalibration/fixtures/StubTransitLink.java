package ust.hk.praisehk.metamodelcalibration.fixtures;

import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;

/**
 * Minimal deterministic {@link TransitLink} double.
 *
 * <p>The real subclasses ({@code CNLTransitDirectLink}, {@code CNLTransitTransferLink}) need a whole
 * {@code TransitSchedule} to construct and carry headways, capacities and per-line passenger
 * bookkeeping. Both consumers of a {@code TransitLink} in the SUE loop touch only two members:
 * {@link #getPassangerCount()} (read by {@code CheckConvergence}) and
 * {@link #addPassanger(double, AnalyticalModelNetwork)} (called by {@code UpdateLinkVolume}, which
 * accumulates). This stub implements exactly those two, so the loop's transit half can be driven
 * without a schedule.</p>
 *
 * <p>It deliberately does <b>not</b> model line-route passenger propagation onto the car links: the
 * real {@code addPassanger} also writes transit volume onto its incident car links, and that side
 * effect is out of scope for the convergence characterization.</p>
 */
public final class StubTransitLink extends TransitLink {

	private final Id<TransitLink> trLinkId;

	public StubTransitLink(String id) {
		super("start", "end", null, null);
		this.trLinkId = Id.create(id, TransitLink.class);
	}

	@Override
	public void addPassanger(double d, AnalyticalModelNetwork Network) {
		this.passangerCount += d;
	}

	@Override
	public Id<TransitLink> getTrLinkId() {
		return this.trLinkId;
	}

	/** Test seam: sets the observable state that {@code CheckConvergence} reads. */
	public void setPassangerCount(double count) {
		this.passangerCount = count;
	}
}
