package ust.hk.praisehk.metamodelcalibration.transit.fare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * Characterization of the vendored {@link FareLink} grammar.
 *
 * <p>{@code FareLink} was brought into this module from the Hong Kong MATSim fork and is used by
 * {@code MeasurementType} to identify fare observations. It is reproduced verbatim except for the
 * {@code package} declaration, so its behaviour must be pinned before anything depends on it.</p>
 *
 * <p>Grammar (from the class Javadoc):</p>
 * <pre>
 *   NetworkWideFare : type___boardingStop___alightingStop___mode
 *   InVehicleFare   : type___transitLine___transitRoute___boardingStop___alightingStop___mode
 * </pre>
 */
class FareLinkTest {

	private static final String SEP = "___";

	@Test
	@DisplayName("ORACLE: NetworkWideFare description parses into its four fields")
	void parsesNetworkWideFare() {
		FareLink fl = new FareLink("NetworkWideFare" + SEP + "STOP_A" + SEP + "STOP_B" + SEP + "bus");

		assertEquals("NetworkWideFare", fl.getType());
		assertEquals("bus", fl.getMode());
		assertEquals("STOP_A", fl.getBoardingStopFacility().toString());
		assertEquals("STOP_B", fl.getAlightingStopFacility().toString());
		// network-wide fare links must not carry line/route identity
		assertNull(fl.getTransitLine());
		assertNull(fl.getTransitRoute());
	}

	@Test
	@DisplayName("ORACLE: NetworkWideFare round-trips through toString()")
	void networkWideFareRoundTrips() {
		String description = "NetworkWideFare" + SEP + "STOP_A" + SEP + "STOP_B" + SEP + "bus";
		assertEquals(description, new FareLink(description).toString());
	}

	@Test
	@DisplayName("ORACLE: InVehicleFare description parses into its six fields")
	void parsesInVehicleFare() {
		FareLink fl = new FareLink("InVehicleFare" + SEP + "LINE_1" + SEP + "ROUTE_1" + SEP
				+ "STOP_A" + SEP + "STOP_B" + SEP + "bus");

		assertEquals("InVehicleFare", fl.getType());
		assertEquals("bus", fl.getMode());
		assertEquals("LINE_1", fl.getTransitLine().toString());
		assertEquals("ROUTE_1", fl.getTransitRoute().toString());
		assertEquals("STOP_A", fl.getBoardingStopFacility().toString());
		assertEquals("STOP_B", fl.getAlightingStopFacility().toString());
	}

	@Test
	@DisplayName("ORACLE: InVehicleFare round-trips through toString()")
	void inVehicleFareRoundTrips() {
		String description = "InVehicleFare" + SEP + "LINE_1" + SEP + "ROUTE_1" + SEP
				+ "STOP_A" + SEP + "STOP_B" + SEP + "bus";
		assertEquals(description, new FareLink(description).toString());
	}

	@Test
	@DisplayName("CHARACTERIZATION: an unrecognised type token is rejected explicitly")
	void rejectsUnknownType() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> new FareLink("NotAFareType" + SEP + "A" + SEP + "B" + SEP + "bus"));
		assertEquals(true, e.getMessage().startsWith("Unrecognized type!!!"));
	}

	@Test
	@DisplayName("REVIEW_REQUIRED FARE-1: a truncated description throws a raw "
			+ "ArrayIndexOutOfBoundsException instead of a diagnostic error")
	void truncatedDescriptionThrowsAIOOBE() {
		assertThrows(ArrayIndexOutOfBoundsException.class,
				() -> new FareLink("NetworkWideFare" + SEP + "STOP_A"));
		assertThrows(ArrayIndexOutOfBoundsException.class,
				() -> new FareLink("InVehicleFare" + SEP + "LINE_1" + SEP + "ROUTE_1"));
	}

	@Test
	@DisplayName("CHARACTERIZATION: MeasurementType builds a FareLink from a measurement id, so a "
			+ "measurement id that is not a valid fare description throws")
	void measurementIdMustBeAValidFareDescription() {
		// This is exactly what MeasurementType.fareLinkVolume does when the
		// FareLink attribute is absent.
		assertThrows(IllegalArgumentException.class, () -> new FareLink("FL1"));
		// ... and the valid form works.
		assertNotNull(new FareLink("NetworkWideFare" + SEP + "A" + SEP + "B" + SEP + "bus"));
	}

	@Test
	@DisplayName("CHARACTERIZATION: the full constructor validates type/stop combinations")
	void fullConstructorValidates() {
		Id<TransitLine> line = Id.create("LINE_1", TransitLine.class);
		Id<TransitRoute> route = Id.create("ROUTE_1", TransitRoute.class);
		Id<TransitStopFacility> from = Id.create("STOP_A", TransitStopFacility.class);
		Id<TransitStopFacility> to = Id.create("STOP_B", TransitStopFacility.class);

		// valid network-wide form
		FareLink ok = new FareLink(FareLink.NetworkWideFare, null, null, from, to, "bus");
		assertEquals(FareLink.NetworkWideFare, ok.getType());

		// network-wide must not carry line/route
		assertThrows(IllegalArgumentException.class,
				() -> new FareLink(FareLink.NetworkWideFare, line, route, from, to, "bus"));

		// in-vehicle requires line and route
		assertThrows(IllegalArgumentException.class,
				() -> new FareLink(FareLink.InVehicleFare, null, null, from, to, "bus"));

		// mode / type / boarding stop are mandatory
		assertThrows(IllegalArgumentException.class,
				() -> new FareLink(FareLink.NetworkWideFare, null, null, null, to, "bus"));
		assertThrows(IllegalArgumentException.class,
				() -> new FareLink(FareLink.NetworkWideFare, null, null, from, to, null));
	}

	@Test
	@DisplayName("CHARACTERIZATION: the serialisation contract used by the XML writer/reader")
	void serialisationConstantsAreStable() {
		assertEquals("fareLink", FareLink.FareLinkAttributeName);
		assertEquals("fare", FareLink.FareTransactionName);
		assertEquals("NetworkWideFare", FareLink.NetworkWideFare);
		assertEquals("InVehicleFare", FareLink.InVehicleFare);
		assertEquals("___", FareLink.seperator);
	}

	@Test
	@DisplayName("REVIEW_REQUIRED FARE-2: the separator is not escaped, so a stop/line id that "
			+ "contains \"___\" corrupts parsing")
	void separatorIsNotEscaped() {
		// A stop id containing the separator splits into extra tokens; the parser
		// then silently takes the wrong fields rather than rejecting the input.
		FareLink fl = new FareLink("NetworkWideFare" + SEP + "A" + SEP + "B" + SEP + "STOP" + SEP + "X" + SEP + "bus");
		// 6 tokens: type = NetworkWideFare, boarding = A, alighting = B, mode = "STOP"
		// -> the tail "X___bus" is silently discarded.
		assertEquals("NetworkWideFare", fl.getType());
		assertEquals("STOP", fl.getMode());
		assertEquals("B", fl.getAlightingStopFacility().toString());
	}
}
