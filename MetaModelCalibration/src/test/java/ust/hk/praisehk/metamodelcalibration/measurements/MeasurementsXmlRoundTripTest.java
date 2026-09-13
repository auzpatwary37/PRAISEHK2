package ust.hk.praisehk.metamodelcalibration.measurements;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;

/**
 * PHASE 4 - MeasurementsWriter/MeasurementsReader round-trip characterization.
 *
 * <p>All files are written into a JUnit {@link TempDir}; nothing is written into
 * the source tree (unlike the legacy helper).</p>
 */
class MeasurementsXmlRoundTripTest {

	private static final String TB1 = "Hour1";
	private static final Id<Link> L1 = Id.createLinkId("L1");
	private static final Id<Link> L2 = Id.createLinkId("L2");

	@Test
	@DisplayName("linkVolume round-trips volumes, SD, link list and gradients")
	void linkVolumeRoundTrip(@TempDir Path dir) {
		Measurements m = Measurements.createMeasurements(TimeBeans.twoHours());
		Measurement x = m.createAnadAddMeasurement("m1", MeasurementType.linkVolume);
		x.setAttribute(Measurement.linkListAttributeName, new ArrayList<>(Arrays.asList(L1, L2)));
		x.putVolume(TB1, 100.);
		x.putSD(TB1, 5.);
		Map<String, double[]> grad = new HashMap<>();
		grad.put(TB1, new double[] { 1., 2. });
		x.setAttribute(Measurement.gradientAttributeName, grad);

		Path xml = dir.resolve("measurements.xml");
		new MeasurementsWriter(m).write(xml.toString());
		assertTrue(Files.exists(xml), "writer must produce a file");

		Measurements read = new MeasurementsReader().readMeasurements(xml.toString());
		Measurement r = read.getMeasurements().get(Id.create("m1", Measurement.class));

		assertNotNull(r);
		assertEquals(100., r.getVolume(TB1), 1e-9);
		assertEquals(5., r.getSD().get(TB1), 1e-9);

		List<Id<Link>> links = (List<Id<Link>>) r.getAttribute(Measurement.linkListAttributeName);
		assertNotNull(links);
		assertEquals(2, links.size());
		assertEquals("L1", links.get(0).toString());

		Map<String, double[]> readGrad =
				(Map<String, double[]>) r.getAttribute(Measurement.gradientAttributeName);
		assertNotNull(readGrad);
		assertArrayEquals(new double[] { 1., 2. }, readGrad.get(TB1), 1e-12);
	}

	@Test
	@DisplayName("CHARACTERIZATION: the time-bean block is written before the measurements, "
			+ "so the reader's single-pass SAX handler can bind volumes")
	void timeBeansAreWrittenFirst(@TempDir Path dir) throws Exception {
		Measurements m = Measurements.createMeasurements(TimeBeans.singleHour());
		m.createAnadAddMeasurement("m1", MeasurementType.linkVolume).putVolume(TB1, 1.);

		Path xml = dir.resolve("m.xml");
		new MeasurementsWriter(m).write(xml.toString());

		String content = new String(Files.readAllBytes(xml));
		assertTrue(content.indexOf("timeBeans") < content.indexOf("MeasurementId"),
				"timeBeans must precede Measurement elements");
	}

	@Test
	@DisplayName("smartCardEntry line/route/boarding-stop attributes round-trip")
	void smartCardEntryRoundTrip(@TempDir Path dir) {
		Measurements m = Measurements.createMeasurements(TimeBeans.singleHour());
		Measurement x = m.createAnadAddMeasurement("sc1", MeasurementType.smartCardEntry);
		x.setAttribute(Measurement.transitLineAttributeName,
				Id.create("LINE_1", org.matsim.pt.transitSchedule.api.TransitLine.class));
		x.setAttribute(Measurement.transitRouteAttributeName,
				Id.create("ROUTE_1", org.matsim.pt.transitSchedule.api.TransitRoute.class));
		x.setAttribute(Measurement.transitBoardingStopAtrributeName, "STOP_A");

		Path xml = dir.resolve("sc.xml");
		new MeasurementsWriter(m).write(xml.toString());
		Measurements read = new MeasurementsReader().readMeasurements(xml.toString());

		Measurement r = read.getMeasurements().get(Id.create("sc1", Measurement.class));
		assertNotNull(r);
		assertEquals("LINE_1", r.getAttribute(Measurement.transitLineAttributeName).toString());
		assertEquals("ROUTE_1", r.getAttribute(Measurement.transitRouteAttributeName).toString());
		assertEquals("STOP_A", r.getAttribute(Measurement.transitBoardingStopAtrributeName));
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MEAS-5: the Variables attribute cannot be serialized - "
			+ "the writer emits numeric XML attribute names and silently writes NO FILE")
	void variablesAttributeCannotBeSerialized(@TempDir Path dir) throws Exception {
		Measurements m = Measurements.createMeasurements(TimeBeans.singleHour());
		m.createAnadAddMeasurement("m1", MeasurementType.linkVolume).putVolume(TB1, 1.);
		m.setAttribute(Measurements.variablesAttributeName, Arrays.asList("theta1", "theta2"));

		Path xml = dir.resolve("vars.xml");
		new MeasurementsWriter(m).write(xml.toString());

		// MeasurementsWriter sets XML attributes named "0", "1", ... which are not
		// valid XML Names. The resulting DOMException is swallowed by the writer's
		// catch-all, so no file is produced at all.
		assertFalse(Files.exists(xml),
				"legacy writer produced no file when a Variables attribute is present");

		// The reader consequently returns null rather than an empty container.
		Measurements read = new MeasurementsReader().readMeasurements(xml.toString());
		assertNull(read, "legacy reader returns null for the missing file");
	}

	@Test
	@DisplayName("CHARACTERIZATION: the writer swallows ALL exceptions silently, so a "
			+ "failed write leaves no diagnostics")
	void writerSwallowsExceptionsSilently(@TempDir Path dir) {
		Measurements m = Measurements.createMeasurements(TimeBeans.singleHour());
		m.createAnadAddMeasurement("m1", MeasurementType.linkVolume).putVolume(TB1, 1.);

		// A directory path (not a file) makes the transform fail.
		Path notAFile = dir.resolve("subdir");
		notAFile.toFile().mkdirs();

		// No exception escapes, and no file is produced.
		new MeasurementsWriter(m).write(notAFile.toString());
		assertTrue(notAFile.toFile().isDirectory());
	}
}
