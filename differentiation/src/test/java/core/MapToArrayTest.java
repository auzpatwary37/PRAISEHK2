package core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Characterization of {@code core.MapToArray}, the variable-ordering abstraction that turns a
 * parameter map into a gradient coordinate vector and back.
 *
 * <p>A gradient vector is meaningless without its coordinate mapping: component {@code i} must
 * always mean the same variable. These tests pin what the legacy class guarantees, which is less
 * than it appears to:
 *
 * <ul>
 *   <li>the coordinate order is the <em>source map's iteration order</em>, so a
 *       {@code LinkedHashMap} gives insertion order while a {@code HashMap} gives hash order;</li>
 *   <li>a variable absent from the map is silently written as {@code 0.0} rather than rejected -
 *       the size check is present but commented out - so a missing variable is indistinguishable
 *       from a variable with zero sensitivity (REVIEW_REQUIRED MAP-1);</li>
 *   <li>a map carrying <em>extra</em> variables is silently accepted, and the extra variables are
 *       dropped.</li>
 * </ul>
 *
 * <p>Nothing here needs a file, the network, MATLAB or randomness.
 */
class MapToArrayTest {

	private static final List<String> ORDER = List.of("theta_A", "theta_B", "theta_C");

	private static LinkedHashMap<String, Double> orderedMap(double... values) {
		LinkedHashMap<String, Double> map = new LinkedHashMap<>();
		for (int i = 0; i < ORDER.size(); i++) {
			map.put(ORDER.get(i), values[i]);
		}
		return map;
	}

	// ------------------------------------------------------------------
	// Ordering
	// ------------------------------------------------------------------

	@Test
	@DisplayName("the coordinate order is the source map's iteration order (LinkedHashMap: insertion order)")
	void coordinateOrderFollowsTheSourceMapIterationOrder() {
		MapToArray<String> mta = new MapToArray<>("order", orderedMap(1.0, 2.0, 3.0));
		assertEquals(ORDER, mta.getKeySet(),
				"a LinkedHashMap source yields its insertion order, which is what makes gradient "
						+ "coordinates reproducible");
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MAP-2: a HashMap source fixes an arbitrary hash order as the gradient coordinates")
	void coordinateOrderIsHashOrderForAHashMapSource() {
		HashMap<String, Double> map = new HashMap<>(orderedMap(1.0, 2.0, 3.0));
		MapToArray<String> mta = new MapToArray<>("hash", map);

		assertEquals(new ArrayList<>(map.keySet()), mta.getKeySet(),
				"the class adopts the source map's key order verbatim, so any caller passing a HashMap "
						+ "is silently choosing hash order as the semantics of its gradient coordinates");
		assertTrue(mta.getKeySet().containsAll(ORDER), "all variables are still present, only reordered");
	}

	@Test
	@DisplayName("reconstructing from the exposed key set preserves the order exactly (serialization round trip)")
	void reconstructionFromTheKeySetPreservesOrder() {
		MapToArray<String> original = new MapToArray<>("order", orderedMap(1.0, 2.0, 3.0));
		MapToArray<String> rebuilt = new MapToArray<>("order", original.getKeySet());

		assertEquals(original.getKeySet(), rebuilt.getKeySet(), "the list constructor preserves order");
		assertEquals(original.getMatrix(orderedMap(1.0, 2.0, 3.0)).length,
				rebuilt.getMatrix(orderedMap(1.0, 2.0, 3.0)).length);
	}

	// ------------------------------------------------------------------
	// name -> index -> gradient coordinate
	// ------------------------------------------------------------------

	@Test
	@DisplayName("values are placed by parameter name, not by map position")
	void valuesArePlacedByNameNotByPosition() {
		MapToArray<String> mta = new MapToArray<>("order", orderedMap(0.0, 0.0, 0.0));

		LinkedHashMap<String, Double> shuffled = new LinkedHashMap<>();
		shuffled.put("theta_C", 30.0);
		shuffled.put("theta_A", 10.0);
		shuffled.put("theta_B", 20.0);

		double[] coordinates = mta.getMatrix(shuffled);
		assertEquals(10.0, coordinates[0], 0.0, "theta_A is coordinate 0");
		assertEquals(20.0, coordinates[1], 0.0, "theta_B is coordinate 1");
		assertEquals(30.0, coordinates[2], 0.0, "theta_C is coordinate 2");
	}

	@Test
	@DisplayName("a value round trip through getMatrix and getMap preserves every coordinate")
	void valueRoundTripPreservesCoordinates() {
		MapToArray<String> mta = new MapToArray<>("order", orderedMap(0.0, 0.0, 0.0));
		double[] coordinates = mta.getMatrix(orderedMap(1.5, -2.5, 3.5));

		Map<String, Double> back = mta.getMap(coordinates);
		assertEquals(1.5, back.get("theta_A"), 0.0);
		assertEquals(-2.5, back.get("theta_B"), 0.0);
		assertEquals(3.5, back.get("theta_C"), 0.0);
	}

	// ------------------------------------------------------------------
	// Missing and extra variables
	// ------------------------------------------------------------------

	@Test
	@DisplayName("REVIEW_REQUIRED MAP-1: a variable absent from the map is silently written as 0.0")
	void absentVariableIsSilentlyWrittenAsZero() {
		MapToArray<String> mta = new MapToArray<>("order", orderedMap(0.0, 0.0, 0.0));

		LinkedHashMap<String, Double> missingOne = new LinkedHashMap<>();
		missingOne.put("theta_A", 7.0);
		missingOne.put("theta_C", 9.0);

		double[] coordinates = mta.getMatrix(missingOne);
		assertEquals(7.0, coordinates[0], 0.0);
		assertEquals(0.0, coordinates[1], 0.0,
				"theta_B is silently zero: a missing variable is indistinguishable from a zero sensitivity");
		assertEquals(9.0, coordinates[2], 0.0);
	}

	@Test
	@DisplayName("REVIEW_REQUIRED MAP-1: an entirely empty map produces an all-zero gradient, not an error")
	void emptyMapProducesAnAllZeroGradient() {
		MapToArray<String> mta = new MapToArray<>("order", orderedMap(0.0, 0.0, 0.0));
		double[] coordinates = mta.getMatrix(new LinkedHashMap<>());

		assertEquals(3, coordinates.length);
		for (double c : coordinates) {
			assertEquals(0.0, c, 0.0, "every coordinate silently defaults to zero");
		}
	}

	@Test
	@DisplayName("extra variables in the map are silently dropped")
	void extraVariablesAreSilentlyDropped() {
		MapToArray<String> mta = new MapToArray<>("order", orderedMap(0.0, 0.0, 0.0));

		LinkedHashMap<String, Double> withExtra = orderedMap(1.0, 2.0, 3.0);
		withExtra.put("theta_UNKNOWN", 99.0);

		double[] coordinates = mta.getMatrix(withExtra);
		assertEquals(3, coordinates.length, "the coordinate vector is sized by the key set, not by the input map");
		assertEquals(1.0, coordinates[0], 0.0);
	}

	@Test
	@DisplayName("a map that is not a Map<String,Double> is rejected rather than coerced")
	void nonDoubleValuedMapIsRejected() {
		MapToArray<String> mta = new MapToArray<>("order", orderedMap(0.0, 0.0, 0.0));
		Map<String, Object> wrongType = new LinkedHashMap<>();
		wrongType.put("theta_A", "not a number");

		assertThrows(ClassCastException.class, () -> mta.getMatrix((Map) wrongType));
	}

	@Test
	@DisplayName("getMap returns a plain HashMap, so the coordinate order does not survive that direction")
	void getMapDoesNotPreserveCoordinateOrder() {
		MapToArray<String> mta = new MapToArray<>("order", orderedMap(0.0, 0.0, 0.0));
		Map<String, Double> back = mta.getMap(new double[] {1.0, 2.0, 3.0});

		assertInstanceOf(HashMap.class, back,
				"the returned container is a HashMap, so a caller that iterates it re-introduces the "
						+ "arbitrary-order problem MAP-2 describes");
		assertEquals(3, back.size());
	}
}
