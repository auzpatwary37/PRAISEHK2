package ust.hk.praisehk.metamodelcalibration.measurements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;

/**
 * PHASE 4 - Measurements container characterization.
 *
 * <p>Uses {@link TempDir} so that no test writes into the source tree. The
 * legacy {@code MeasurementsReaderWriterTest/MeasurementCreator} helper is NOT a
 * runnable test (its class name does not match the surefire pattern) and it
 * writes to {@code src/main/resources}; it is excluded and documented instead.</p>
 */
class MeasurementsTest {

	private static final String TB1 = "Hour1";
	private static final String TB2 = "Hour2";

	@Test
	@DisplayName("measurementsByType is pre-populated with EVERY MeasurementType")
	void byTypePrePopulatedForAllTypes() {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		for (MeasurementType type : MeasurementType.values()) {
			assertNotNull(m.getMeasurementsByType().get(type),
					"missing bucket for " + type);
			assertTrue(m.getMeasurementsByType().get(type).isEmpty());
		}
	}

	@Test
	@DisplayName("createAnadAddMeasurement registers the measurement in both indices")
	void addRegistersInBothIndices() {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		Measurement created = m.createAnadAddMeasurement("m1", MeasurementType.linkVolume);

		assertEquals(created, m.getMeasurements().get(Id.create("m1", Measurement.class)));
		assertEquals(1, m.getMeasurementsByType().get(MeasurementType.linkVolume).size());
	}

	@Test
	@DisplayName("addMeasurement with a differently-typed instance routes to its own bucket")
	void addRoutesToItsOwnBucket() {
		Measurements source = Measurements.createMeasurements(TimeBeans.twoHours());
		Measurement tt = source.createAnadAddMeasurement("tt1", MeasurementType.linkTravelTime);

		Measurements target = Measurements.createMeasurements(TimeBeans.twoHours());
		target.addMeasurement(tt.clone());

		assertEquals(1, target.getMeasurementsByType().get(MeasurementType.linkTravelTime).size());
		assertTrue(target.getMeasurementsByType().get(MeasurementType.linkVolume).isEmpty());
	}

	@Test
	@DisplayName("clone() produces independent Measurement objects")
	void cloneIsDeepForMeasurements() {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		m.createAnadAddMeasurement("m1", MeasurementType.linkVolume).putVolume(TB1, 100.);

		Measurements c = m.clone();
		assertNotSame(m.getMeasurements().get(Id.create("m1", Measurement.class)),
				c.getMeasurements().get(Id.create("m1", Measurement.class)));

		c.getMeasurements().get(Id.create("m1", Measurement.class)).putVolume(TB1, 7.);
		assertEquals(100., m.getMeasurements().get(Id.create("m1", Measurement.class)).getVolume(TB1), 0.);
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-2: clone() does NOT copy the container attributes")
	void cloneDoesNotCopyContainerAttributes() {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		m.setAttribute(Measurements.variablesAttributeName, java.util.Arrays.asList("theta1"));

		Measurements c = m.clone();
		assertNull(c.getAttribute(Measurements.variablesAttributeName),
				"legacy clone drops container-level attributes");
	}

	@Test
	void applyFactorScalesVolumesAndSd() {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		Measurement x = m.createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		x.putVolume(TB1, 100.);
		x.putSD(TB1, 10.);

		m.applyFactor(2.5);

		assertEquals(250., x.getVolume(TB1), 1e-9);
		assertEquals(25., x.getSD().get(TB1), 1e-9);
	}

	@Test
	void resetMeasurementsZeroesVolumes() {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		Measurement x = m.createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		x.putVolume(TB1, 100.);
		x.putVolume(TB2, 200.);

		m.resetMeasurements();

		assertEquals(0., x.getVolume(TB1), 0.);
		assertEquals(0., x.getVolume(TB2), 0.);
	}

	@Test
	void removeMeasurementRemovesFromBothIndices() {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		m.createAnadAddMeasurement("m1", MeasurementType.linkVolume);

		m.removeMeasurement(Id.create("m1", Measurement.class));

		assertNull(m.getMeasurements().get(Id.create("m1", Measurement.class)));
		assertNull(m.getMeasurementsByType().get(MeasurementType.linkVolume),
				"emptied bucket is removed entirely");
	}

	@Test
	@DisplayName("CHARACTERIZATION: removeMeasurementsByType REMOVES the type key, so a "
			+ "later getMeasurementsByType().get(type) is null (NPE for consumers)")
	void removeMeasurementsByTypeRemovesBucketKey() {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		m.createAnadAddMeasurement("m1", MeasurementType.linkVolume);

		m.removeMeasurementsByType(MeasurementType.linkVolume);

		assertNull(m.getMeasurementsByType().get(MeasurementType.linkVolume),
				"legacy removes the bucket instead of leaving it empty");
	}

	@Test
	void addRedundantTimeBeanAddsOnlyMissingBeans() {
		Measurements m = Measurements.createMeasurements(TimeBeans.singleHour());
		java.util.Map<String, org.matsim.core.utils.collections.Tuple<Double, Double>> extra =
				new java.util.HashMap<>();
		extra.put(TB2, new org.matsim.core.utils.collections.Tuple<>(3600., 7200.));
		extra.put("Hour1", new org.matsim.core.utils.collections.Tuple<>(0., 9999.));

		m.addRedundantTimeBean(extra);

		assertEquals(2, m.getTimeBean().size());
		assertEquals(3600., m.getTimeBean().get(TB2).getFirst(), 0.);
		// Existing bean keeps its ORIGINAL value.
		assertEquals(3600., m.getTimeBean().get("Hour1").getSecond(), 0.);
	}

	// ------------------------------------------------------------------
	// CSV round trip
	// ------------------------------------------------------------------

	@Test
	@DisplayName("writeCSVMeasurements emits the documented header and one row per (m,tb)")
	void writeCsvHeaderAndRows(@TempDir Path dir) throws IOException {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		Measurement x = m.createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		x.putVolume(TB1, 100.);
		x.putVolume(TB2, 250.);

		Path csv = dir.resolve("measurements.csv");
		m.writeCSVMeasurements(csv.toString());

		List<String> lines = Files.readAllLines(csv);
		assertEquals("MeasurementId,timeId,Count,Type,ifForValidation", lines.get(0));
		assertEquals(3, lines.size());
		// Volume iteration order is ConcurrentHashMap order, so assert as a set.
		assertTrue(lines.contains("m1,Hour1,100.0,linkVolume,0"));
		assertTrue(lines.contains("m1,Hour2,250.0,linkVolume,0"));
	}

	@Test
	@DisplayName("updateMeasurementsFromFile reads back volumes for existing time beans")
	void updateFromFileRoundTrip(@TempDir Path dir) throws IOException {
		Measurements written = Measurements.createMeasurements(TimeBeans.twoHours());
		Measurement x = written.createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		x.putVolume(TB1, 100.);
		x.putVolume(TB2, 250.);

		Path csv = dir.resolve("measurements.csv");
		written.writeCSVMeasurements(csv.toString());

		Measurements read = Measurements.createMeasurements(TimeBeans.twoHours());
		read.updateMeasurementsFromFile(csv.toString());

		Measurement r = read.getMeasurements().get(Id.create("m1", Measurement.class));
		assertNotNull(r);
		assertEquals(100., r.getVolume(TB1), 0.);
		assertEquals(250., r.getVolume(TB2), 0.);
	}

	@Test
	@DisplayName("CHARACTERIZATION: updateMeasurementsFromFile THROWS on a file whose "
			+ "first data row is malformed (raw split(',') + valueOf + parseDouble)")
	void updateFromFileIsBrittle(@TempDir Path dir) throws IOException {
		Path csv = dir.resolve("bad.csv");
		Files.write(csv, java.util.Arrays.asList(
				"MeasurementId,timeId,Count,Type,ifForValidation",
				"m1")); // missing columns

		Measurements read = Measurements.createMeasurements(TimeBeans.twoHours());
		org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
				() -> read.updateMeasurementsFromFile(csv.toString()));
	}
}
