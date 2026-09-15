package ust.hk.praisehk.metamodelcalibration.measurements;
import static org.junit.jupiter.api.Assertions.assertSame;
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

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-18: Measurements.clone() ALIASES the container time-bean map")
	void cloneAliasesTheContainerTimeBeanMap() {
		Measurements original = Measurements.createMeasurements(TimeBeans.singleHour());
		Measurements clone = original.clone();

		assertSame(original.getTimeBean(), clone.getTimeBean(),
				"the clone is constructed with the same map instance, not a copy");

		java.util.Map<String, org.matsim.core.utils.collections.Tuple<Double, Double>> extra =
				new java.util.HashMap<>();
		extra.put(TB1, new org.matsim.core.utils.collections.Tuple<>(0., 3600.));
		extra.put(TB2, new org.matsim.core.utils.collections.Tuple<>(3600., 7200.));

		clone.addRedundantTimeBean(extra);

		assertTrue(original.getTimeBean().containsKey(TB2),
				"mutating the clone's time beans mutates the original container");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-18: the cloned container therefore DIVERGES from its own children, "
			+ "because each cloned Measurement holds a private copy")
	void clonedContainerDivergesFromItsChildren() {
		Measurements original = Measurements.createMeasurements(TimeBeans.singleHour());
		original.createAnadAddMeasurement("m1", MeasurementType.linkVolume).putVolume(TB1, 1.);
		Measurements clone = original.clone();

		java.util.Map<String, org.matsim.core.utils.collections.Tuple<Double, Double>> extra =
				new java.util.HashMap<>();
		extra.put(TB1, new org.matsim.core.utils.collections.Tuple<>(0., 3600.));
		extra.put(TB2, new org.matsim.core.utils.collections.Tuple<>(3600., 7200.));
		clone.addRedundantTimeBean(extra);

		Measurement clonedChild = clone.getMeasurements().get(Id.create("m1", Measurement.class));

		assertEquals(2, clone.getTimeBean().size(), "the container now declares two time beans");
		assertEquals(1, clonedChild.getTimeBean().size(),
				"but the cloned child still has its own single-bean copy");
		assertFalse(clonedChild.getTimeBean().containsKey(TB2));

		// consequence: a volume for the newly declared bean is silently ignored on the child
		clonedChild.putVolume(TB2, 99.);
		assertNull(clonedChild.getVolumes().get(TB2),
				"the container and its children disagree about which time beans exist");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-19: the CSV writer rewrites ',' to '__' in the measurement id and "
			+ "the reader never restores it")
	void csvRewritesCommaInMeasurementId(@TempDir Path dir) throws IOException {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		m.createAnadAddMeasurement("a,b", MeasurementType.linkVolume).putVolume(TB1, 5.);

		Path csv = dir.resolve("commas.csv");
		m.writeCSVMeasurements(csv.toString());
		assertTrue(Files.readAllLines(csv).get(1).startsWith("a__b,"), "the id is rewritten on write");

		Measurements read = Measurements.createMeasurements(TimeBeans.twoHours());
		read.updateMeasurementsFromFile(csv.toString());

		assertNotNull(read.getMeasurements().get(Id.create("a__b", Measurement.class)),
				"it comes back under the REWRITTEN id");
		assertNull(read.getMeasurements().get(Id.create("a,b", Measurement.class)),
				"the original id is never restored: the identity is silently changed (MEAS-19)");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-20: ifForValidation is written as the fifth column but ignored "
			+ "on read")
	void csvDropsIfForValidation(@TempDir Path dir) throws IOException {
		Measurements m = Measurements.createMeasurements(TimeBeans.singleHour());
		Measurement x = m.createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		x.setAttribute("ifForValidation", "true");
		x.putVolume(TB1, 7.);

		Path csv = dir.resolve("validation.csv");
		m.writeCSVMeasurements(csv.toString());
		assertTrue(Files.readAllLines(csv).get(1).endsWith(",true"), "the flag IS written");

		Measurements read = Measurements.createMeasurements(TimeBeans.singleHour());
		read.updateMeasurementsFromFile(csv.toString());
		assertNull(read.getMeasurements().get(Id.create("m1", Measurement.class))
				.getAttribute("ifForValidation"), "but it is never read back (MEAS-20)");
	}

	@Test
	@DisplayName("CHARACTERIZATION: the CSV type column is honoured for a NEW measurement, but an "
			+ "EXISTING measurement keeps its original type")
	void csvTypeHandling(@TempDir Path dir) throws IOException {
		Measurements m = Measurements.createMeasurements(TimeBeans.singleHour());
		m.createAnadAddMeasurement("tt1", MeasurementType.linkTravelTime).putVolume(TB1, 3.);

		Path csv = dir.resolve("type.csv");
		m.writeCSVMeasurements(csv.toString());

		Measurements fresh = Measurements.createMeasurements(TimeBeans.singleHour());
		fresh.updateMeasurementsFromFile(csv.toString());
		assertEquals(MeasurementType.linkTravelTime,
				fresh.getMeasurements().get(Id.create("tt1", Measurement.class)).getMeasurementType(),
				"a new measurement takes the type from the file");

		Measurements existing = Measurements.createMeasurements(TimeBeans.singleHour());
		existing.createAnadAddMeasurement("tt1", MeasurementType.linkVolume);
		existing.updateMeasurementsFromFile(csv.toString());
		assertEquals(MeasurementType.linkVolume,
				existing.getMeasurements().get(Id.create("tt1", Measurement.class)).getMeasurementType(),
				"an existing measurement keeps its own type: the file does not override it");
	}

	@Test
	@DisplayName("CHARACTERIZATION: a multi-time-bean CSV round trip preserves id, times, volumes and type")
	void csvRoundTripAllColumns(@TempDir Path dir) throws IOException {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		Measurement x = m.createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		x.putVolume(TB1, 11.);
		x.putVolume(TB2, 22.);

		Path csv = dir.resolve("multi.csv");
		m.writeCSVMeasurements(csv.toString());
		assertEquals(3, Files.readAllLines(csv).size(), "header + one row per time bean");

		Measurements read = Measurements.createMeasurements(TimeBeans.twoHours());
		read.updateMeasurementsFromFile(csv.toString());

		Measurement r = read.getMeasurements().get(Id.create("m1", Measurement.class));
		assertNotNull(r);
		assertEquals(MeasurementType.linkVolume, r.getMeasurementType());
		assertEquals(11., r.getVolume(TB1), 0.);
		assertEquals(22., r.getVolume(TB2), 0.);
	}

}
