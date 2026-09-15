package ust.hk.praisehk.metamodelcalibration.measurements;
import org.matsim.api.core.v01.Coord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;

/**
 * PHASE 4 - Measurement characterization.
 *
 * <p>Captures the legacy container semantics (volume/SD insertion, cloning,
 * attribute sharing) before any redesign. No production code is changed.</p>
 */
class MeasurementTest {

	private static final String TB1 = "Hour1";
	private static final String TB2 = "Hour2";

	private static Measurements container() {
		return Measurements.createMeasurements(TimeBeans.twoHours());
	}

	@Test
	@DisplayName("putVolume on a declared time bean stores the value and auto-inserts SD=0")
	void putVolumeStoresValueAndDefaultSd() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		m.putVolume(TB1, 123.);
		assertEquals(123., m.getVolume(TB1), 0.);
		assertEquals(0., m.getSD().get(TB1), 0.);
	}

	@Test
	@DisplayName("CHARACTERIZATION: putVolume with an UNDECLARED time bean is silently "
			+ "ignored (error logged, value discarded)")
	void putVolumeIgnoresUnknownTimeBean() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		m.putVolume("NoSuchBean", 999.);

		assertNull(m.getVolumes().get("NoSuchBean"));
		assertTrue(m.getVolumes().isEmpty());
	}

	@Test
	@DisplayName("CHARACTERIZATION: putSD with an UNDECLARED time bean is ignored")
	void putSdIgnoresUnknownTimeBean() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		m.putSD("NoSuchBean", 5.);
		assertNull(m.getSD().get("NoSuchBean"));
	}

	@Test
	@DisplayName("putVolume is REPLACING, not additive (documented in the source)")
	void putVolumeReplaces() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		m.putVolume(TB1, 100.);
		m.putVolume(TB1, 250.);
		assertEquals(250., m.getVolume(TB1), 0.);
	}

	@Test
	@DisplayName("getVolume for an unset time bean returns null (no defaulting)")
	void getVolumeUnsetIsNull() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		assertNull(m.getVolumes().get(TB2));
		assertNotNull(m.getVolumes());
	}

	@Test
	@DisplayName("a new Measurement starts with an empty (non-null) link list attribute")
	void newMeasurementHasEmptyLinkList() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		Object links = m.getAttribute(Measurement.linkListAttributeName);
		assertNotNull(links);
		assertTrue(((List<?>) links).isEmpty());
	}

	@Test
	@DisplayName("clone deep-copies volumes and SD, and copies the current attributes")
	void cloneCopiesVolumesAndSd() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		m.putVolume(TB1, 100.);
		m.putVolume(TB2, 200.);
		m.putSD(TB1, 7.);

		Measurement c = m.clone();

		assertNotSame(m, c);
		assertEquals(m.getId(), c.getId());
		assertEquals(100., c.getVolume(TB1), 0.);
		assertEquals(200., c.getVolume(TB2), 0.);
		assertEquals(7., c.getSD().get(TB1), 0.);

		// Mutating the clone must not affect the original (volumes/SD are copies).
		c.putVolume(TB1, 555.);
		assertEquals(100., m.getVolume(TB1), 0.);
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-1: clone() copies attributes SHALLOWLY - the link "
			+ "list object is shared between the original and the clone")
	void cloneSharesAttributeObjects() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		ArrayList<Id<Link>> links = new ArrayList<>();
		links.add(Id.createLinkId("L1"));
		m.setAttribute(Measurement.linkListAttributeName, links);

		Measurement c = m.clone();

		assertSame(m.getAttribute(Measurement.linkListAttributeName),
				c.getAttribute(Measurement.linkListAttributeName),
				"legacy clone shares the attribute object");

		// Mutating the shared list is visible through BOTH measurements.
		((List<Id<Link>>) c.getAttribute(Measurement.linkListAttributeName))
				.add(Id.createLinkId("L2"));
		assertEquals(2, ((List<?>) m.getAttribute(Measurement.linkListAttributeName)).size());
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-17: Measurement.clone() does NOT copy the coordinate")
	void cloneDropsCoord() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		m.setCoord(new Coord(1234.5, 6789.0));

		Measurement c = m.clone();

		assertNotNull(m.getCoord(), "precondition: the original has a coordinate");
		assertNull(c.getCoord(),
				"coord is observable state, and clone() silently drops it (MEAS-17)");
	}

	@Test
	@DisplayName("CHARACTERIZATION: Measurement.clone() DOES copy the declared time-bean map")
	void cloneCopiesTheTimeBeanMap() {
		Measurement m = container().createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		m.putVolume(TB1, 5.);

		Measurement c = m.clone();

		assertNotSame(m.getTimeBean(), c.getTimeBean(), "the child gets its own copy");
		assertEquals(m.getTimeBean().size(), c.getTimeBean().size());
		assertEquals(m.getTimeBean().keySet(), c.getTimeBean().keySet());
		assertTrue(c.getTimeBean().containsKey(TB1));
	}

}
