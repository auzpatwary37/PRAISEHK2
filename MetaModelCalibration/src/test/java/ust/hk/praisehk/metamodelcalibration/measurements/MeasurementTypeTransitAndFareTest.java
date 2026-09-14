package ust.hk.praisehk.metamodelcalibration.measurements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.fixtures.TimeBeans;
import ust.hk.praisehk.metamodelcalibration.transit.fare.FareLink;

/**
 * PHASE 4 (continued) - characterization of the {@link MeasurementType} constants that PR 1 left
 * uncovered: {@code TransitPhysicalLinkVolume}, {@code maasSpecificFareLinkVolume},
 * {@code smartCardEntry} and {@code smartCardEntryAndExit}, plus {@link MTRLinkVolumeInfo} parsing.
 *
 * <p>Legacy behaviour is recorded, not corrected. Suspected defects are cross-referenced to
 * {@code docs/modernization/REVIEW_REQUIRED.md}.</p>
 *
 * @see docs/modernization/TEST_MATRIX.md
 */
class MeasurementTypeTransitAndFareTest {

	private static final String TB1 = "Hour1";
	private static final Id<Link> L1 = Id.createLinkId("L1");
	private static final Id<TransitLine> LINE_1 = Id.create("LINE_1", TransitLine.class);
	private static final Id<TransitLine> LINE_2 = Id.create("LINE_2", TransitLine.class);
	private static final Id<TransitRoute> ROUTE_1 = Id.create("ROUTE_1", TransitRoute.class);
	private static final Id<TransitStopFacility> STOP_1 = Id.create("STOP_1", TransitStopFacility.class);

	/** Valid fare-link description: type___boardingStop___alightingStop___mode */
	private static final String FARE_KEY = "NetworkWideFare___STOP_A___STOP_B___bus";
	private static final String FARE_KEY_2 = "NetworkWideFare___STOP_A___STOP_B___train";

	private static Measurements container() {
		return Measurements.createMeasurements(TimeBeans.singleHour());
	}

	private static SUEModelOutput emptyOutput() {
		return new SUEModelOutput(new HashMap<>(), new HashMap<>(), new HashMap<>(),
				new HashMap<>(), new HashMap<>());
	}

	private static Map<String, Map<Id<Link>, Map<String, Double>>> trainCount(
			String lineRouteKey, double value) {
		Map<String, Map<Id<Link>, Map<String, Double>>> trainCount = new HashMap<>();
		trainCount.put(TB1, new HashMap<>());
		trainCount.get(TB1).put(L1, new HashMap<>());
		trainCount.get(TB1).get(L1).put(lineRouteKey, value);
		return trainCount;
	}

	// ==================================================================
	// TransitPhysicalLinkVolume
	// ==================================================================

	@Nested
	@DisplayName("TransitPhysicalLinkVolume")
	class TransitPhysicalLinkVolumeTests {

		private Measurement measurement(MTRLinkVolumeInfo... infos) {
			Measurement m = container().createAnadAddMeasurement("MTR1",
					MeasurementType.TransitPhysicalLinkVolume);
			m.setAttribute(Measurement.MTRLineRouteStopLinkInfosName,
					new ArrayList<>(Arrays.asList(infos)));
			m.putVolume(TB1, 0.);
			return m;
		}

		private MTRLinkVolumeInfo info(Id<TransitLine> lineId) {
			return new MTRLinkVolumeInfo(STOP_1, ROUTE_1, L1, lineId);
		}

		@Test
		@DisplayName("ORACLE: the line/route key convention is exactly lineId + \"_\" + routeId")
		void lineRouteKeyConvention() {
			// Pinned independently of the extractor below, so that a change to this convention
			// is reported as a failure rather than silently tracked by the extractor's lookup.
			assertEquals("LINE_1_ROUTE_1", CNLTransitDirectLink.calcLineRouteId("LINE_1", "ROUTE_1"));
		}

		@Test
		@DisplayName("ORACLE: the volume is the SUM of train counts over the configured line/route/link infos")
		void sumsTrainCountsOverInfos() {
			Measurement m = measurement(info(LINE_1), info(LINE_2));

			Map<String, Map<Id<Link>, Map<String, Double>>> counts = new HashMap<>();
			counts.put(TB1, new HashMap<>());
			counts.get(TB1).put(L1, new HashMap<>());
			// Keys are LITERALS, not produced by CNLTransitDirectLink.calcLineRouteId: this is an
			// independent oracle, so it must not move with the production convention under test.
			counts.get(TB1).get(L1).put("LINE_1_ROUTE_1", 30.);
			counts.get(TB1).get(L1).put("LINE_2_ROUTE_1", 20.);

			SUEModelOutput out = emptyOutput();
			out.setTrainCount(counts);

			m.updateMeasurement(out, null, null);

			// hand-computed: 30 + 20
			assertEquals(50., m.getVolume(TB1), 0.);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED MEAS-10: updateMeasurement is NOT idempotent - it ADDS to the "
				+ "existing volume, so a second call double-counts")
		void isNotIdempotent() {
			Measurement m = measurement(info(LINE_1));

			SUEModelOutput out = emptyOutput();
			out.setTrainCount(trainCount("LINE_1_ROUTE_1", 30.));

			m.updateMeasurement(out, null, null);
			assertEquals(30., m.getVolume(TB1), 0.);

			// The extractor accumulates into m.getVolumes() instead of replacing it.
			m.updateMeasurement(out, null, null);
			assertEquals(60., m.getVolume(TB1), 0.);
		}

		@Test
		@DisplayName("CHARACTERIZATION: a line/route absent from the model output contributes nothing")
		void unknownLineRouteContributesNothing() {
			Measurement m = measurement(info(LINE_1), info(LINE_2));

			SUEModelOutput out = emptyOutput();
			// only LINE_1 is present
			out.setTrainCount(trainCount("LINE_1_ROUTE_1", 30.));

			m.updateMeasurement(out, null, null);
			assertEquals(30., m.getVolume(TB1), 0.);
		}

		@Test
		@DisplayName("CHARACTERIZATION: a missing MTR info attribute is dereferenced without a guard")
		void missingAttributeThrows() {
			Measurement m = container().createAnadAddMeasurement("MTR1",
					MeasurementType.TransitPhysicalLinkVolume);
			m.setAttribute(Measurement.MTRLineRouteStopLinkInfosName, null);

			SUEModelOutput out = emptyOutput();
			out.setTrainCount(trainCount("LINE_1_ROUTE_1", 30.));

			assertThrows(NullPointerException.class, () -> m.updateMeasurement(out, null, null));
		}

		@Test
		@DisplayName("CHARACTERIZATION: a missing train-count map is dereferenced without a guard")
		void missingTrainCountThrows() {
			Measurement m = measurement(info(LINE_1));

			SUEModelOutput out = emptyOutput();
			out.setTrainCount(null);

			assertThrows(NullPointerException.class, () -> m.updateMeasurement(out, null, null));
		}

		@Test
		@DisplayName("the MTR info list survives an XML writer/reader round trip")
		void roundTripsThroughXml(@TempDir Path dir) throws IOException {
			Measurements container = container();
			Measurement m = container.createAnadAddMeasurement("MTR1",
					MeasurementType.TransitPhysicalLinkVolume);
			m.setAttribute(Measurement.MTRLineRouteStopLinkInfosName,
					new ArrayList<>(Arrays.asList(info(LINE_1), info(LINE_2))));
			m.putVolume(TB1, 0.);

			Path xml = dir.resolve("mtr.xml");
			new MeasurementsWriter(container).write(xml.toString());
			Measurements read = new MeasurementsReader().readMeasurements(xml.toString());

			List<MTRLinkVolumeInfo> infos = (List<MTRLinkVolumeInfo>) read
					.getMeasurements().get(Id.create("MTR1", Measurement.class))
					.getAttribute(Measurement.MTRLineRouteStopLinkInfosName);
			assertNotNull(infos);
			assertEquals(2, infos.size());
			assertEquals("LINE_1", infos.get(0).lineId.toString());
			assertEquals("LINE_2", infos.get(1).lineId.toString());
			assertEquals("L1", infos.get(0).linkId.toString());
		}
	}

	// ==================================================================
	// MTRLinkVolumeInfo
	// ==================================================================

	@Nested
	@DisplayName("MTRLinkVolumeInfo")
	class MtrLinkVolumeInfoTests {

		@Test
		@DisplayName("ORACLE: toString/parse round trip over the '___' grammar")
		void roundTrips() {
			MTRLinkVolumeInfo info = new MTRLinkVolumeInfo(STOP_1, ROUTE_1, L1, LINE_1);
			String s = info.toString();
			assertEquals("LINE_1___ROUTE_1___STOP_1___L1", s);

			MTRLinkVolumeInfo parsed = new MTRLinkVolumeInfo(s);
			assertEquals("LINE_1", parsed.lineId.toString());
			assertEquals("ROUTE_1", parsed.routeId.toString());
			assertEquals("STOP_1", parsed.stopId.toString());
			assertEquals("L1", parsed.linkId.toString());
		}

		@Test
		@DisplayName("REVIEW_REQUIRED MTR-1: a truncated description throws a raw "
				+ "ArrayIndexOutOfBoundsException")
		void truncatedDescriptionThrowsAIOOBE() {
			assertThrows(ArrayIndexOutOfBoundsException.class,
					() -> new MTRLinkVolumeInfo("LINE_1___ROUTE_1"));
		}
	}

	// ==================================================================
	// maasSpecificFareLinkVolume
	// ==================================================================

	@Nested
	@DisplayName("maasSpecificFareLinkVolume")
	class MaasSpecificFareLinkVolumeTests {

		private Measurement measurement() {
			Measurement m = container().createAnadAddMeasurement("MAAS1",
					MeasurementType.maasSpecificFareLinkVolume);
			m.setAttribute(Measurement.FareLinkAttributeName, new FareLink(FARE_KEY));
			m.setAttribute(Measurement.MaaSPackageAttributeName, "pkg1");
			m.putVolume(TB1, 0.);
			return m;
		}

		private Map<String, Map<String, Map<String, Double>>> maasFlow(String maas, double value) {
			Map<String, Map<String, Map<String, Double>>> flow = new HashMap<>();
			flow.put(TB1, new HashMap<>());
			flow.get(TB1).put(maas, new HashMap<>());
			flow.get(TB1).get(maas).put(FARE_KEY, value);
			return flow;
		}

		@Test
		@DisplayName("ORACLE: reads MaaSSpecificFareLinkFlow[timeBean][maasPackage][fareLink]")
		void readsMaasSpecificFlow() {
			Measurement m = measurement();

			SUEModelOutput out = emptyOutput();
			out.setMaaSSpecificFareLinkFlow(maasFlow("pkg1", 250.));

			m.updateMeasurement(out, null, null);
			assertEquals(250., m.getVolume(TB1), 0.);
		}

		@Test
		@DisplayName("CHARACTERIZATION: unlike fareLinkVolume, this variant reads the CORRECT container")
		void correctContainerIsUsed() {
			// Contrast with REVIEW_REQUIRED MEAS-4: fareLinkVolume builds a fallback map and
			// then re-reads the (null) original, so it yields 0. This variant has no fallback
			// and reads getMaaSSpecificFareLinkFlow() directly, so it yields the real value.
			Measurement m = measurement();
			SUEModelOutput out = emptyOutput();
			out.setMaaSSpecificFareLinkFlow(maasFlow("pkg1", 250.));
			m.updateMeasurement(out, null, null);
			assertEquals(250., m.getVolume(TB1), 0.);
		}

		@Test
		@DisplayName("CHARACTERIZATION: an unknown MaaS package silently yields 0")
		void unknownPackageYieldsZero() {
			Measurement m = measurement();

			SUEModelOutput out = emptyOutput();
			out.setMaaSSpecificFareLinkFlow(maasFlow("someOtherPackage", 250.));

			m.updateMeasurement(out, null, null);
			assertEquals(0., m.getVolume(TB1), 0.);
		}

		@Test
		@DisplayName("CHARACTERIZATION: a missing MaaS attribute is dereferenced -> NullPointerException")
		void missingMaasAttributeThrows() {
			Measurement m = measurement();
			m.setAttribute(Measurement.MaaSPackageAttributeName, null);

			SUEModelOutput out = emptyOutput();
			out.setMaaSSpecificFareLinkFlow(maasFlow("pkg1", 250.));

			assertThrows(NullPointerException.class, () -> m.updateMeasurement(out, null, null));
		}

		@Test
		@DisplayName("CHARACTERIZATION: an EMPTY volume map dereferences getFareLinkVolume() while "
				+ "initialising, so a null FareLinkVolume is a NullPointerException")
		void emptyVolumesThrowsWhenFareLinkVolumeIsNull() {
			Measurement m = container().createAnadAddMeasurement("MAAS1",
					MeasurementType.maasSpecificFareLinkVolume);
			m.setAttribute(Measurement.FareLinkAttributeName, new FareLink(FARE_KEY));
			m.setAttribute(Measurement.MaaSPackageAttributeName, "pkg1");
			// deliberately no putVolume -> m.getVolumes().size() == 0

			SUEModelOutput out = emptyOutput();
			out.setFareLinkVolume(null);
			out.setMaaSSpecificFareLinkFlow(maasFlow("pkg1", 250.));

			assertThrows(NullPointerException.class, () -> m.updateMeasurement(out, null, null));
		}

		@Test
		@DisplayName("the FareLink and MaaS package attributes survive an XML round trip")
		void roundTripsThroughXml(@TempDir Path dir) throws IOException {
			Measurements container = container();
			Measurement m = container.createAnadAddMeasurement("MAAS1",
					MeasurementType.maasSpecificFareLinkVolume);
			m.setAttribute(Measurement.FareLinkAttributeName, new FareLink(FARE_KEY));
			m.setAttribute(Measurement.MaaSPackageAttributeName, "pkg1");
			m.putVolume(TB1, 0.);

			Path xml = dir.resolve("maas.xml");
			new MeasurementsWriter(container).write(xml.toString());
			Measurements read = new MeasurementsReader().readMeasurements(xml.toString());

			Measurement r = read.getMeasurements().get(Id.create("MAAS1", Measurement.class));
			assertNotNull(r);
			assertEquals("pkg1", r.getAttribute(Measurement.MaaSPackageAttributeName).toString());
			assertEquals(FARE_KEY, r.getAttribute(Measurement.FareLinkAttributeName).toString());
		}
	}

	// ==================================================================
	// smartCardEntry / smartCardEntryAndExit
	// ==================================================================

	@Nested
	@DisplayName("smart card types")
	class SmartCardTests {

		@Test
		@DisplayName("CHARACTERIZATION: smartCardEntry.updateMeasurement is a NO-OP")
		void smartCardEntryUpdateIsNoOp() {
			Measurement m = container().createAnadAddMeasurement("SC1", MeasurementType.smartCardEntry);
			m.putVolume(TB1, 123.);

			m.updateMeasurement(emptyOutput(), null, null);

			// the extractor body is empty, so the pre-existing volume is untouched
			assertEquals(123., m.getVolume(TB1), 0.);
		}

		@Test
		@DisplayName("CHARACTERIZATION: smartCardEntryAndExit.updateMeasurement is a NO-OP")
		void smartCardEntryAndExitUpdateIsNoOp() {
			Measurement m = container().createAnadAddMeasurement("SC2",
					MeasurementType.smartCardEntryAndExit);
			m.putVolume(TB1, 456.);

			m.updateMeasurement(emptyOutput(), null, null);

			assertEquals(456., m.getVolume(TB1), 0.);
		}

		@Test
		@DisplayName("smartCardEntry: LineId / RouteId / BoardingStop survive an XML round trip")
		void smartCardEntryRoundTrip(@TempDir Path dir) throws IOException {
			Measurements container = container();
			Measurement m = container.createAnadAddMeasurement("SC1", MeasurementType.smartCardEntry);
			m.setAttribute(Measurement.transitLineAttributeName, Id.create("LINE_1", TransitLine.class));
			m.setAttribute(Measurement.transitRouteAttributeName, Id.create("ROUTE_1", TransitRoute.class));
			m.setAttribute(Measurement.transitBoardingStopAtrributeName, "STOP_A");

			Path xml = dir.resolve("scentry.xml");
			new MeasurementsWriter(container).write(xml.toString());
			Measurements read = new MeasurementsReader().readMeasurements(xml.toString());

			Measurement r = read.getMeasurements().get(Id.create("SC1", Measurement.class));
			assertNotNull(r);
			assertEquals("LINE_1", r.getAttribute(Measurement.transitLineAttributeName).toString());
			assertEquals("ROUTE_1", r.getAttribute(Measurement.transitRouteAttributeName).toString());
			assertEquals("STOP_A", r.getAttribute(Measurement.transitBoardingStopAtrributeName));
		}

		@Test
		@DisplayName("REVIEW_REQUIRED MEAS-14: the writer's generic attribute loop is DEAD CODE, so "
				+ "smartCardEntry's ifForValidation flag is silently dropped on write")
		void smartCardEntryValidationFlagDoesNotRoundTrip(@TempDir Path dir) throws IOException {
			Measurements container = container();
			Measurement m = container.createAnadAddMeasurement("SC1", MeasurementType.smartCardEntry);
			m.setAttribute(Measurement.transitLineAttributeName, Id.create("LINE_1", TransitLine.class));
			m.setAttribute(Measurement.transitRouteAttributeName, Id.create("ROUTE_1", TransitRoute.class));
			m.setAttribute(Measurement.transitBoardingStopAtrributeName, "STOP_A");
			m.setAttribute("ifForValidation", "true");

			Path xml = dir.resolve("scentry-flag.xml");
			new MeasurementsWriter(container).write(xml.toString());

			// The flag is never written: MeasurementsWriter guards the attribute copy with
			// `measurement.getAttribute(s) == null`, but DOM Element.getAttribute() returns ""
			// (never null) for an absent attribute, so the loop body never runs. Any measurement
			// attribute that writeAttribute() does not set explicitly is therefore lost.
			String content = new String(Files.readAllBytes(xml));
			assertFalse(content.contains("ifForValidation"),
					"the flag is expected to be absent from the XML, proving the loop is dead");

			Measurements read = new MeasurementsReader().readMeasurements(xml.toString());
			Measurement r = read.getMeasurements().get(Id.create("SC1", Measurement.class));
			assertNotNull(r);
			assertNull(r.getAttribute("ifForValidation"),
					"and therefore it cannot come back on read");
		}

		@Test
		@DisplayName("smartCardEntryAndExit: non-train carries line/route through XML; train omits them")
		void smartCardEntryAndExitRoundTrip(@TempDir Path dir) throws IOException {
			Measurements m = container();

			Measurement bus = m.createAnadAddMeasurement("BUS1", MeasurementType.smartCardEntryAndExit);
			bus.setAttribute(Measurement.transitBoardingStopAtrributeName, "STOP_A");
			bus.setAttribute(Measurement.transitAlightingStopAttributeName, "STOP_B");
			bus.setAttribute(Measurement.transitModeAttributeName, "bus");
			bus.setAttribute(Measurement.transitLineAttributeName, "LINE_1");
			bus.setAttribute(Measurement.transitRouteAttributeName, "ROUTE_1");

			Measurement train = m.createAnadAddMeasurement("TRAIN1", MeasurementType.smartCardEntryAndExit);
			train.setAttribute(Measurement.transitBoardingStopAtrributeName, "STOP_C");
			train.setAttribute(Measurement.transitAlightingStopAttributeName, "STOP_D");
			train.setAttribute(Measurement.transitModeAttributeName, "train");

			Path xml = dir.resolve("sc.xml");
			new MeasurementsWriter(m).write(xml.toString());
			Measurements read = new MeasurementsReader().readMeasurements(xml.toString());

			Measurement rBus = read.getMeasurements().get(Id.create("BUS1", Measurement.class));
			assertNotNull(rBus);
			assertEquals("bus", rBus.getAttribute(Measurement.transitModeAttributeName));
			assertEquals("STOP_A", rBus.getAttribute(Measurement.transitBoardingStopAtrributeName));
			assertEquals("STOP_B", rBus.getAttribute(Measurement.transitAlightingStopAttributeName));
			assertEquals("LINE_1", rBus.getAttribute(Measurement.transitLineAttributeName));
			assertEquals("ROUTE_1", rBus.getAttribute(Measurement.transitRouteAttributeName));

			Measurement rTrain = read.getMeasurements().get(Id.create("TRAIN1", Measurement.class));
			assertNotNull(rTrain);
			assertEquals("train", rTrain.getAttribute(Measurement.transitModeAttributeName));
			assertEquals("STOP_C", rTrain.getAttribute(Measurement.transitBoardingStopAtrributeName));
			assertEquals("STOP_D", rTrain.getAttribute(Measurement.transitAlightingStopAttributeName));
			// The writer/reader deliberately omit line and route for network-wide (train) fares.
			assertNull(rTrain.getAttribute(Measurement.transitLineAttributeName));
			assertNull(rTrain.getAttribute(Measurement.transitRouteAttributeName));
		}
	}

	// ==================================================================
	// fare-link / MaaS serialization
	// ==================================================================

	@Nested
	@DisplayName("fare-link serialization")
	class FareLinkSerializationTests {

		@Test
		@DisplayName("fareLinkVolume: the FareLink attribute survives an XML round trip")
		void fareLinkVolumeRoundTrip(@TempDir Path dir) throws IOException {
			Measurements container = container();
			Measurement m = container.createAnadAddMeasurement("FL1", MeasurementType.fareLinkVolume);
			m.setAttribute(Measurement.FareLinkAttributeName, new FareLink(FARE_KEY));
			m.putVolume(TB1, 0.);

			Path xml = dir.resolve("fl.xml");
			new MeasurementsWriter(container).write(xml.toString());
			Measurements read = new MeasurementsReader().readMeasurements(xml.toString());

			Measurement r = read.getMeasurements().get(Id.create("FL1", Measurement.class));
			assertNotNull(r);
			assertEquals(FARE_KEY, r.getAttribute(Measurement.FareLinkAttributeName).toString());
		}

		@Test
		@DisplayName("fareLinkVolumeCluster: the comma-joined cluster survives, including the "
				+ "bracket/space clean-up in parseAttribute")
		void fareLinkVolumeClusterRoundTrip(@TempDir Path dir) throws IOException {
			Measurements container = container();
			Measurement m = container.createAnadAddMeasurement("CL1",
					MeasurementType.fareLinkVolumeCluster);
			m.setAttribute(Measurement.FareLinkClusterAttributeName,
					new ArrayList<>(Arrays.asList(new FareLink(FARE_KEY), new FareLink(FARE_KEY_2))));
			m.putVolume(TB1, 0.);

			Path xml = dir.resolve("cl.xml");
			new MeasurementsWriter(container).write(xml.toString());
			Measurements read = new MeasurementsReader().readMeasurements(xml.toString());

			Measurement r = read.getMeasurements().get(Id.create("CL1", Measurement.class));
			assertNotNull(r);
			List<FareLink> readLinks =
					(List<FareLink>) r.getAttribute(Measurement.FareLinkClusterAttributeName);
			assertNotNull(readLinks);
			assertEquals(2, readLinks.size());
			assertEquals(FARE_KEY, readLinks.get(0).toString());
			assertEquals(FARE_KEY_2, readLinks.get(1).toString());
		}

		@Test
		@DisplayName("REVIEW_REQUIRED MEAS-8b: MaaSPacakgeUsage.parseAttribute wraps the package name in "
				+ "a FareLink, so a plain package name cannot be deserialized")
		void maasPackageNameCannotRoundTrip(@TempDir Path dir) throws IOException {
			Measurements container = container();
			Measurement m = container.createAnadAddMeasurement("MP1", MeasurementType.MaaSPacakgeUsage);
			// updateMeasurement stores a plain package key, so this is what the writer emits...
			m.setAttribute(Measurement.MaaSPackageAttributeName, "pkg1");

			Path xml = dir.resolve("mp.xml");
			new MeasurementsWriter(container).write(xml.toString());
			assertTrue(Files.exists(xml));

			// ...but parseAttribute feeds it to `new FareLink(...)`, which rejects anything that is
			// not a "type___...___mode" description. The type therefore cannot round trip.
			assertThrows(IllegalArgumentException.class,
					() -> new MeasurementsReader().readMeasurements(xml.toString()));
		}
	}

	@Test
	@DisplayName("CHARACTERIZATION: TransitPhysicalLinkVolume writeAttribute emits the info list as "
			+ "comma-joined '___' records")
	void transitPhysicalLinkVolumeWriteAttributeFormat(@TempDir Path dir) throws IOException {
		Measurements container = container();
		Measurement m = container.createAnadAddMeasurement("MTR1",
				MeasurementType.TransitPhysicalLinkVolume);
		MTRLinkVolumeInfo info = new MTRLinkVolumeInfo(STOP_1, ROUTE_1, L1, LINE_1);
		m.setAttribute(Measurement.MTRLineRouteStopLinkInfosName,
				new ArrayList<>(Arrays.asList(info)));
		m.putVolume(TB1, 0.);

		Path xml = dir.resolve("fmt.xml");
		new MeasurementsWriter(container).write(xml.toString());

		String content = new String(Files.readAllBytes(xml));
		assertTrue(content.contains("LINE_1___ROUTE_1___STOP_1___L1"),
				"expected the '___' record in the written attribute");
	}
}
