package ust.hk.praisehk.metamodelcalibration.measurements;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

import transitFareAndHandler.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;

/**
 * PHASE 4 - MeasurementType extraction characterization.
 *
 * <p>Focus: {@code linkVolume} gradient handling and the {@code fareLinkVolume}
 * fallback path, both of which were flagged as suspected legacy defects. These
 * tests capture the ACTUAL behaviour; they do not fix anything.</p>
 *
 * @see docs/modernization/REVIEW_REQUIRED.md (MEAS-3, MEAS-4)
 */
class MeasurementTypeTest {

	private static final String TB1 = "Hour1";

	private static final Id<Link> L1 = Id.createLinkId("L1");
	private static final Id<Link> L2 = Id.createLinkId("L2");

	/**
	 * A syntactically valid fare-link description. FareLink's parser accepts only
	 * {@code NetworkWideFare} or {@code InVehicleFare} as the first token and
	 * throws on anything else (see transitFareAndHandler.FareLink).
	 */
	private static final String FARE_KEY = "NetworkWideFare___STOP_A___STOP_B___bus";

	private static Measurements container() {
		return Measurements.createMeasurements(TimeBeans.singleHour());
	}

	private static Measurement linkVolumeMeasurement(Id<Link>... links) {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		ArrayList<Id<Link>> linkList = new ArrayList<>(Arrays.asList(links));
		m.setAttribute(Measurement.linkListAttributeName, linkList);
		return m;
	}

	private static SUEModelOutput emptyOutput() {
		return new SUEModelOutput(new HashMap<>(), new HashMap<>(), new HashMap<>(),
				new HashMap<>(), new HashMap<>());
	}

	// ------------------------------------------------------------------
	// linkVolume
	// ------------------------------------------------------------------

	@Test
	@DisplayName("linkVolume aggregates link volumes and SUMS the gradient vectors")
	void linkVolumeAggregatesVolumeAndGradient() {
		Measurement m = linkVolumeMeasurement(L1, L2);
		m.putVolume(TB1, 0.);

		Map<String, Map<Id<Link>, Double>> volumes = new HashMap<>();
		volumes.put(TB1, new HashMap<>());
		volumes.get(TB1).put(L1, 100.);
		volumes.get(TB1).put(L2, 50.);

		Map<String, Map<Id<Link>, double[]>> grads = new HashMap<>();
		grads.put(TB1, new HashMap<>());
		grads.get(TB1).put(L1, new double[] { 1., 0. });
		grads.get(TB1).put(L2, new double[] { 0., 1. });

		SUEModelOutput out = emptyOutput();
		out.setLinkVolume(volumes);
		out.setLinkVolumeGrad(grads);

		m.updateMeasurement(out, null, null);

		assertEquals(150., m.getVolume(TB1), 0.);
		Map<String, double[]> grad =
				(Map<String, double[]>) m.getAttribute(Measurement.gradientAttributeName);
		assertNotNull(grad);
		assertArrayEquals(new double[] { 1., 1. }, grad.get(TB1), 1e-12);
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-3: linkVolume logs 'gradients are not present' but "
			+ "then unconditionally calls volumeGrad.toArray() -> NullPointerException")
	void linkVolumeThrowsWhenGradientsAreAbsent() {
		Measurement m = linkVolumeMeasurement(L1);
		m.putVolume(TB1, 0.);

		Map<String, Map<Id<Link>, Double>> volumes = new HashMap<>();
		volumes.put(TB1, new HashMap<>());
		volumes.get(TB1).put(L1, 100.);

		SUEModelOutput out = emptyOutput();
		out.setLinkVolume(volumes);
		out.setLinkVolumeGrad(null); // no gradients at all

		assertThrows(NullPointerException.class, () -> m.updateMeasurement(out, null, null),
				"the 'only updating the volume' path does not actually return");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-3b: when SOME links have gradients and others do "
			+ "not, the missing links are SILENTLY DROPPED from the aggregated gradient "
			+ "(no exception, no warning beyond a stdout print)")
	void linkVolumeSilentlyDropsLinksWithoutGradient() {
		Measurement m = linkVolumeMeasurement(L1, L2);
		m.putVolume(TB1, 0.);

		Map<String, Map<Id<Link>, Double>> volumes = new HashMap<>();
		volumes.put(TB1, new HashMap<>());
		volumes.get(TB1).put(L1, 100.);
		volumes.get(TB1).put(L2, 50.);

		Map<String, Map<Id<Link>, double[]>> grads = new HashMap<>();
		grads.put(TB1, new HashMap<>());
		grads.get(TB1).put(L1, new double[] { 1., 0. });
		// L2 deliberately has no gradient entry

		SUEModelOutput out = emptyOutput();
		out.setLinkVolume(volumes);
		out.setLinkVolumeGrad(grads);

		m.updateMeasurement(out, null, null);

		// Volume is correct (100 + 50) ...
		assertEquals(150., m.getVolume(TB1), 0.);
		// ... but the gradient only reflects L1: L2's contribution vanished.
		Map<String, double[]> grad =
				(Map<String, double[]>) m.getAttribute(Measurement.gradientAttributeName);
		assertNotNull(grad);
		assertArrayEquals(new double[] { 1., 0. }, grad.get(TB1), 1e-12,
				"L2's gradient contribution is silently lost, so the gradient is inconsistent "
						+ "with the aggregated volume");
	}

	@Test
	@DisplayName("CHARACTERIZATION: linkVolume with no link list falls back to the "
			+ "measurement id as the link id")
	void linkVolumeFallsBackToMeasurementIdAsLink() {
		Measurement m = container().createAnadAddMeasurement("L9", MeasurementType.linkVolume);
		// Replace the empty default list with an empty list (still empty).
		m.setAttribute(Measurement.linkListAttributeName, new ArrayList<Id<Link>>());
		m.putVolume(TB1, 0.);

		Map<String, Map<Id<Link>, Double>> volumes = new HashMap<>();
		volumes.put(TB1, new HashMap<>());
		volumes.get(TB1).put(Id.createLinkId("L9"), 77.);

		Map<String, Map<Id<Link>, double[]>> grads = new HashMap<>();
		grads.put(TB1, new HashMap<>());
		grads.get(TB1).put(Id.createLinkId("L9"), new double[] { 1. });

		SUEModelOutput out = emptyOutput();
		out.setLinkVolume(volumes);
		out.setLinkVolumeGrad(grads);

		m.updateMeasurement(out, null, null);

		assertEquals(77., m.getVolume(TB1), 0.);
		assertEquals("L9", ((List<Id<Link>>) m.getAttribute(Measurement.linkListAttributeName))
				.get(0).toString());
	}

	@Test
	@DisplayName("CHARACTERIZATION: linkVolume is a silent no-op when both the measurement "
			+ "volumes and the model output are empty")
	void linkVolumeIsSilentNoOpWhenVolumesEmpty() {
		Measurement m = linkVolumeMeasurement(L1);

		SUEModelOutput out = emptyOutput();
		out.setLinkVolume(new HashMap<>());

		m.updateMeasurement(out, null, null);

		assertTrue(m.getVolumes().isEmpty());
		assertNull(m.getAttribute(Measurement.gradientAttributeName));
	}

	// ------------------------------------------------------------------
	// linkTravelTime
	// ------------------------------------------------------------------

	@Test
	@DisplayName("CHARACTERIZATION: linkTravelTime reads only the FIRST link of the list")
	void linkTravelTimeUsesFirstLinkOnly() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkTravelTime);
		ArrayList<Id<Link>> linkList = new ArrayList<>(Arrays.asList(L1, L2));
		m.setAttribute(Measurement.linkListAttributeName, linkList);
		m.putVolume(TB1, 0.);

		Map<String, Map<Id<Link>, Double>> tt = new HashMap<>();
		tt.put(TB1, new HashMap<>());
		tt.get(TB1).put(L1, 60.);
		tt.get(TB1).put(L2, 999.);

		SUEModelOutput out = emptyOutput();
		out.setLinkTravelTime(tt);

		m.updateMeasurement(out, null, null);
		assertEquals(60., m.getVolume(TB1), 0.);
	}

	@Test
	@DisplayName("CHARACTERIZATION: linkTravelTime leaves the volume at 0 when the link is absent")
	void linkTravelTimeMissingLinkLeavesZero() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkTravelTime);
		m.setAttribute(Measurement.linkListAttributeName, new ArrayList<>(Arrays.asList(L1)));
		m.putVolume(TB1, 123.);

		SUEModelOutput out = emptyOutput();
		out.setLinkTravelTime(new HashMap<>());

		m.updateMeasurement(out, null, null);
		assertEquals(0., m.getVolume(TB1), 0.);
	}

	// ------------------------------------------------------------------
	// fareLinkVolume / fareLinkVolumeCluster
	// ------------------------------------------------------------------

	private static Map<String, Map<String, Map<String, Double>>> maasFlow(String key, double value) {
		Map<String, Map<String, Map<String, Double>>> maas = new HashMap<>();
		maas.put(TB1, new HashMap<>());
		maas.get(TB1).put("noMass", new HashMap<>());
		maas.get(TB1).get("noMass").put(key, value);
		return maas;
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-4: fareLinkVolume builds a fallback map from MaaS "
			+ "output but then re-reads the ORIGINAL container -> fallback is ineffective")
	void fareLinkVolumeFallbackIsIneffective() {
		Measurement m = container().createAnadAddMeasurement("FL1", MeasurementType.fareLinkVolume);
		m.setAttribute(Measurement.FareLinkAttributeName, new FareLink(FARE_KEY));
		m.putVolume(TB1, 0.);
		String key = FARE_KEY;

		SUEModelOutput out = emptyOutput();
		out.setFareLinkVolume(null); // triggers the (broken) fallback
		out.setMaaSSpecificFareLinkFlow(maasFlow(key, 500.));

		m.updateMeasurement(out, null, null);

		assertEquals(0., m.getVolume(TB1), 0.,
				"the MaaS-derived fallback value is never actually used");
	}

	@Test
	@DisplayName("CHARACTERIZATION: fareLinkVolume reads the real container when present")
	void fareLinkVolumeReadsContainerWhenPresent() {
		Measurement m = container().createAnadAddMeasurement("FL1", MeasurementType.fareLinkVolume);
		m.setAttribute(Measurement.FareLinkAttributeName, new FareLink(FARE_KEY));
		m.putVolume(TB1, 0.);
		String key = FARE_KEY;

		Map<String, Map<String, Double>> fareLinkVolume = new HashMap<>();
		fareLinkVolume.put(TB1, new HashMap<>());
		fareLinkVolume.get(TB1).put(key, 321.);

		SUEModelOutput out = emptyOutput();
		out.setFareLinkVolume(fareLinkVolume);

		m.updateMeasurement(out, null, null);
		assertEquals(321., m.getVolume(TB1), 0.);
	}

	@Test
	@DisplayName("CHARACTERIZATION (asymmetry vs fareLinkVolume): fareLinkVolumeCluster "
			+ "DOES install the fallback into the output, so it works")
	void fareLinkVolumeClusterInstallsFallback() {
		Measurement m = container().createAnadAddMeasurement("CL1",
				MeasurementType.fareLinkVolumeCluster);
		FareLink fl = new FareLink(FARE_KEY);
		m.setAttribute(Measurement.FareLinkClusterAttributeName, new ArrayList<>(Arrays.asList(fl)));
		m.putVolume(TB1, 0.);

		SUEModelOutput out = emptyOutput();
		out.setFareLinkVolume(null);
		out.setMaaSSpecificFareLinkFlow(maasFlow(fl.toString(), 500.));

		m.updateMeasurement(out, null, null);

		assertEquals(500., m.getVolume(TB1), 0.,
				"cluster variant repairs the output container, the non-cluster variant does not");
	}

	@Test
	@DisplayName("fareLinkVolumeCluster throws when no fare links are configured")
	void fareLinkVolumeClusterRequiresFareLinks() {
		Measurement m = container().createAnadAddMeasurement("CL1",
				MeasurementType.fareLinkVolumeCluster);
		m.putVolume(TB1, 0.);

		SUEModelOutput out = emptyOutput();
		out.setFareLinkVolume(new HashMap<>());

		assertThrows(IllegalArgumentException.class, () -> m.updateMeasurement(out, null, null));
	}

	// ------------------------------------------------------------------
	// MaaSPacakgeUsage / averagePTOccumpancy
	// ------------------------------------------------------------------

	@Test
	@DisplayName("CHARACTERIZATION: MaaSPacakgeUsage writes to the literal key \"All\" "
			+ "(which no time bean declares)")
	void maasPackageUsageWritesAllLiteralKey() {
		Measurement m = container().createAnadAddMeasurement("MaaS1",
				MeasurementType.MaaSPacakgeUsage);
		m.setAttribute(Measurement.MaaSPackageAttributeName, "pkg1");

		SUEModelOutput out = emptyOutput();
		Map<String, Double> usage = new HashMap<>();
		usage.put("pkg1", 42.);
		out.setMaaSPackageUsage(usage);

		m.updateMeasurement(out, null, null);

		assertEquals(42., m.getVolumes().get("All"), 0.);
		assertNull(m.getSD().get("All"), "the literal key bypasses putVolume, so no SD is set");
	}

	@Test
	@DisplayName("CHARACTERIZATION: averagePTOccumpancy dereferences the occupancy map "
			+ "without a guard -> NullPointerException")
	void averagePtOccupancyThrowsWhenAbsent() {
		Measurement m = container().createAnadAddMeasurement("OCC1",
				MeasurementType.averagePTOccumpancy);
		m.setAttribute(Measurement.linkListAttributeName, new ArrayList<>(Arrays.asList(L1)));
		m.putVolume(TB1, 0.);

		SUEModelOutput out = emptyOutput();
		out.setAveragePtOccupancyOnLink(null);

		assertThrows(NullPointerException.class, () -> m.updateMeasurement(out, null, null));
	}
}
