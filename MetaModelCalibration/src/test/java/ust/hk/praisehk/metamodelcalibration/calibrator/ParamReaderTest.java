package ust.hk.praisehk.metamodelcalibration.calibrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.collections.Tuple;

/**
 * PHASE 5 - {@link ParamReader} characterization.
 *
 * <p>{@code ParamReader} is treated as a domain component, not CSV utility code. Its internal maps are
 * keyed by the CSV <b>Code</b> column, not by the parameter name, and its {@code Scale*} methods move
 * between "code" and "subPopulation name" formats — both are load-bearing for every downstream
 * calibration call, so they are pinned here before any redesign.</p>
 *
 * <p>All files are written into a JUnit {@link TempDir}; no hard-coded absolute paths. The one
 * CWD-relative behaviour (the silent fallback) is characterized explicitly and guarded by an
 * assumption — see {@code PARAM-1} in {@code docs/modernization/REVIEW_REQUIRED.md}.</p>
 */
class ParamReaderTest {

	private static final String HEADER =
			"SubPopulation,Parameter Name,id,Lower Limit,UpperLimit,CurretValue,Code,IncludeIninitialParam";

	private static Path csv(Path dir, String... rows) throws IOException {
		return write(dir, "params.csv", rows);
	}

	private static Path write(Path dir, String name, String... rows) throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		lines.addAll(Arrays.asList(rows));
		Path p = dir.resolve(name);
		Files.write(p, lines);
		return p;
	}

	/** subPop,paramName,id,lower,upper,current,code,include */
	private static String row(String subPop, String name, double lower, double upper, double current,
			String code, String include) {
		return String.join(",", subPop, name, "id_" + name, String.valueOf(lower),
				String.valueOf(upper), String.valueOf(current), code, include);
	}

	private static ParamReader reader(Path dir, String... rows) throws IOException {
		return new ParamReader(csv(dir, rows).toString());
	}

	// ==================================================================
	// Construction, bounds, codes, inclusion
	// ==================================================================

	@Nested
	@DisplayName("parsing")
	class Parsing {

		@Test
		@DisplayName("ORACLE: bounds, current value and inclusion are read from the documented columns")
		void readsBoundsValueAndInclusion(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir,
					row("", "MuCar", -240, -160, -200, "1", "TRUE"),
					row("", "MuMoney", 0.8, 1.2, 1.0, "3", "FALSE"));

			assertEquals(2, r.getDefaultParam().size());
			// NOTE: the maps are keyed by the CODE column, not by the parameter name.
			assertEquals(-200.0, r.getDefaultParam().get("1"), 0.);
			assertEquals(1.0, r.getDefaultParam().get("3"), 0.);

			assertEquals(-240.0, r.getParamLimit().get("1").getFirst(), 0.);
			assertEquals(-160.0, r.getParamLimit().get("1").getSecond(), 0.);

			assertEquals(2, r.getParamName().size());
			assertTrue(r.getParamName().contains("MuCar"));

			// only the TRUE row is in the initial parameter set
			assertEquals(1, r.getInitialParam().size());
			assertEquals(-200.0, r.getInitialParam().get("1"), 0.);
			assertFalse(r.getInitialParam().containsKey("3"));
			assertTrue(r.getInitialParamLimit().containsKey("1"));
			assertFalse(r.getInitialParamLimit().containsKey("3"));
		}

		@Test
		@DisplayName("CHARACTERIZATION: an empty sub-population is excluded and the id becomes the parameter name")
		void emptySubPopulationIsExcludedAndIdIsTheParamName(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir, row("", "MuCar", -240, -160, -200, "1", "TRUE"));

			assertTrue(r.getSubPopulationName().isEmpty());
			// ParamNoCode is only observable through ScaleUp: paramId -> value
			LinkedHashMap<String, Double> scaled = r.ScaleUp(map("1", -200.0));
			assertEquals(1, scaled.size());
			assertEquals(-200.0, scaled.get("MuCar"), 0.);
		}

		@Test
		@DisplayName("CHARACTERIZATION: \"All\" is excluded from the sub-population list but is still parseable")
		void allIsExcludedFromSubPopulations(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir,
					row("All", "CapacityMultiplier", 0.5, 1.5, 1.0, "14", "FALSE"),
					row("person_GV", "MuCar", -240, -160, -200, "23", "TRUE"));

			assertEquals(1, r.getSubPopulationName().size());
			assertEquals("person_GV", r.getSubPopulationName().get(0));

			// sub-population rows are keyed "subPop paramName"
			assertEquals(-200.0, r.ScaleUp(map("23", -200.0)).get("person_GV MuCar"), 0.);
			// "All" rows are keyed "All paramName"
			assertEquals(1.0, r.ScaleUp(map("14", 1.0)).get("All CapacityMultiplier"), 0.);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-4: a duplicated code is LAST-WINS for values and bounds, but the "
				+ "initial set keeps the FIRST row's value and bounds")
		void duplicateCodeInconsistency(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir,
					row("", "A", 0, 10, 5, "7", "TRUE"),
					row("", "B", 100, 200, 150, "7", "FALSE"));

			// values and bounds: overwritten by the later row
			assertEquals(150.0, r.getDefaultParam().get("7"), 0.);
			assertEquals(100.0, r.getParamLimit().get("7").getFirst(), 0.);
			assertEquals(200.0, r.getParamLimit().get("7").getSecond(), 0.);

			// the initial set is NOT updated by the later row: it still holds the first value...
			assertEquals(5.0, r.getInitialParam().get("7"), 0.);
			// ...and the bounds captured at the time of the first row.
			assertEquals(0.0, r.getInitialParamLimit().get("7").getFirst(), 0.);
			assertEquals(10.0, r.getInitialParamLimit().get("7").getSecond(), 0.);

			// both parameter names are appended, and both map to the same code
			assertEquals(Arrays.asList("A", "B"), r.getParamName());
			assertEquals(150.0, r.ScaleUp(map("7", 150.0)).get("B"), 0.);
			assertEquals(150.0, r.ScaleUp(map("7", 150.0)).get("A"), 0.);
		}

		@Test
		@DisplayName("CHARACTERIZATION: a duplicate whose LATER row is included takes the later value and bounds")
		void duplicateCodeLaterRowIncluded(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir,
					row("", "A", 0, 10, 5, "7", "FALSE"),
					row("", "B", 100, 200, 150, "7", "TRUE"));

			assertEquals(150.0, r.getInitialParam().get("7"), 0.);
			assertEquals(100.0, r.getInitialParamLimit().get("7").getFirst(), 0.);
			assertEquals(200.0, r.getInitialParamLimit().get("7").getSecond(), 0.);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-2: the CSV `id` column is computed and then discarded")
		void idColumnIsIgnored(@TempDir Path dir) throws IOException {
			// row() writes "id_<name>" into the id column; the parsed paramId is rebuilt from
			// subPopulation + parameter name, so the id column never affects anything.
			ParamReader r = reader(dir, row("p", "MuCar", -240, -160, -200, "1", "TRUE"));
			assertEquals("p MuCar", r.ScaleUp(map("1", -200.0)).keySet().iterator().next());
		}
	}

	// ==================================================================
	// Malformed input
	// ==================================================================

	@Nested
	@DisplayName("malformed input")
	class Malformed {

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-2: a row with too few columns throws ArrayIndexOutOfBoundsException")
		void tooFewColumnsThrows(@TempDir Path dir) throws IOException {
			Path p = csv(dir, ",A,id,0,10");
			assertThrows(ArrayIndexOutOfBoundsException.class, () -> new ParamReader(p.toString()));
		}

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-2: a non-numeric value throws NumberFormatException")
		void nonNumericThrows(@TempDir Path dir) throws IOException {
			Path p = csv(dir, ",A,id,0,10,notANumber,7,TRUE");
			assertThrows(NumberFormatException.class, () -> new ParamReader(p.toString()));
		}

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-2: a trailing empty include-flag column is dropped and throws")
		void trailingEmptyIncludeFlagThrows(@TempDir Path dir) throws IOException {
			Path p = csv(dir, ",A,id,0,10,5,7,");
			assertThrows(ArrayIndexOutOfBoundsException.class, () -> new ParamReader(p.toString()));
		}

		@Test
		@DisplayName("CHARACTERIZATION: the first line is ALWAYS discarded as a header, with no validation")
		void firstLineIsAlwaysDiscarded(@TempDir Path dir) throws IOException {
			// A file with no header: its only data row is silently eaten as the header.
			Path p = dir.resolve("noheader.csv");
			Files.write(p, Arrays.asList(",A,id,0,10,5,7,TRUE"));

			ParamReader r = new ParamReader(p.toString());
			assertTrue(r.getDefaultParam().isEmpty());
			assertTrue(r.getParamName().isEmpty());
		}

		@Test
		@DisplayName("CHARACTERIZATION: an empty file yields empty maps and no exception")
		void emptyFileIsAccepted(@TempDir Path dir) throws IOException {
			Path p = dir.resolve("empty.csv");
			Files.write(p, new ArrayList<String>());

			ParamReader r = new ParamReader(p.toString());
			assertTrue(r.getDefaultParam().isEmpty());
			assertTrue(r.getParamLimit().isEmpty());
			assertTrue(r.getInitialParam().isEmpty());
		}
	}

	// ==================================================================
	// Missing file fallback
	// ==================================================================

	@Nested
	@DisplayName("missing file")
	class MissingFile {

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-1: a missing parameter file SILENTLY falls back to a "
				+ "CWD-relative default, loading the shipped CSV instead")
		void missingFileSilentlyFallsBack(@TempDir Path dir) {
			Path requested = dir.resolve("does-not-exist.csv");
			ParamReader r = new ParamReader(requested.toString());

			// The fallback path is a *relative* path, so its resolution depends on the process CWD.
			assertEquals("src/main/resources/paramReaderTrial1.csv", r.getDefaultFileLoc());

			assumeTrue(Files.exists(Path.of(r.getDefaultFileLoc())),
					"relative default not resolvable from the current working directory (see PARAM-1)");

			// No exception, no warning: the reader quietly loads the bundled sample parameters.
			assertFalse(r.getDefaultParam().isEmpty());
			assertTrue(r.getParamLimit().containsKey("1"));
			assertTrue(r.getParamLimit().containsKey("14"));
			// paramReaderTrial1.csv leaves the SubPopulation column empty on every row, so the
			// sub-population list stays empty too.
			assertTrue(r.getSubPopulationName().isEmpty());
		}
	}

	// ==================================================================
	// ScaleUp / ScaleDown / ScaleUpLimit
	// ==================================================================

	@Nested
	@DisplayName("ScaleUp / ScaleDown / ScaleUpLimit")
	class Scaling {

		private ParamReader reader(Path dir) throws IOException {
			return ParamReaderTest.this.reader(dir,
					row("", "MuCar", -240, -160, -200, "1", "TRUE"),
					row("", "MuMoney", 0.8, 1.2, 1.0, "3", "FALSE"));
		}

		@Test
		@DisplayName("ORACLE: ScaleUp maps code -> value into paramName -> value")
		void scaleUp(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir);
			LinkedHashMap<String, Double> out = r.ScaleUp(map("1", -180.0));

			assertEquals(1, out.size());
			assertEquals(-180.0, out.get("MuCar"), 0.);
		}

		@Test
		@DisplayName("ORACLE: ScaleDown is the inverse for code-form names")
		void scaleDown(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir);
			LinkedHashMap<String, Double> out = r.ScaleDown(map("MuCar", -180.0));

			assertEquals(1, out.size());
			assertEquals(-180.0, out.get("1"), 0.);
		}

		@Test
		@DisplayName("CHARACTERIZATION: ScaleUp omits codes absent from the input (it does not default them)")
		void scaleUpOmitsAbsentCodes(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir);
			LinkedHashMap<String, Double> out = r.ScaleUp(map("1", -180.0));

			assertEquals(1, out.size());
			assertFalse(out.containsKey("MuMoney"));
		}

		@Test
		@DisplayName("CHARACTERIZATION: an already-scaled input is returned unchanged (warn path)")
		void scaleUpAlreadyScaledIsIdentity(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir);
			LinkedHashMap<String, Double> in = map("MuCar", -180.0, "MuMoney", 1.0);
			LinkedHashMap<String, Double> out = r.ScaleUp(in);

			assertEquals(in, out);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-6: an unrecognised input throws unless unknown parameters are allowed")
		void scaleUpUnknownInput(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir);

			assertThrows(IllegalArgumentException.class, () -> r.ScaleUp(map("nonsense", 1.0)));

			r.setAllowUnkownParamaeterWhileScalingUp(true);
			LinkedHashMap<String, Double> out = r.ScaleUp(map("1", -180.0, "nonsense", 1.0));
			// the known code is scaled and the unknown key is passed through
			assertEquals(-180.0, out.get("MuCar"), 0.);
			assertEquals(1.0, out.get("nonsense"), 0.);
		}

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-5: ScaleDown returns the input unchanged when NOTHING overlaps")
		void scaleDownNoOverlapReturnsInput(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir);
			LinkedHashMap<String, Double> in = map("totallyUnknown", 1.0);

			assertEquals(in, r.ScaleDown(in));
		}

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-5: ScaleDown with a PARTIAL overlap emits a null key")
		void scaleDownPartialOverlapEmitsNullKey(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir);
			LinkedHashMap<String, Double> out = r.ScaleDown(map("MuCar", -180.0, "totallyUnknown", 1.0));

			// every key is mapped through ParamNoCode, so non-parameter keys become a null key
			assertTrue(out.containsKey(null), "expected a null key from the unmapped entry");
			assertEquals(-180.0, out.get("1"), 0.);
		}

		@Test
		@DisplayName("ORACLE: ScaleUpLimit maps code -> bounds into paramName -> bounds")
		void scaleUpLimit(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir);

			LinkedHashMap<String, Tuple<Double, Double>> in = new LinkedHashMap<>();
			in.put("1", new Tuple<>(-240.0, -160.0));
			LinkedHashMap<String, Tuple<Double, Double>> out = r.ScaleUpLimit(in);

			assertEquals(1, out.size());
			assertEquals(-240.0, out.get("MuCar").getFirst(), 0.);
			assertEquals(-160.0, out.get("MuCar").getSecond(), 0.);
		}
	}

	// ==================================================================
	// Sub-population extraction
	// ==================================================================

	@Nested
	@DisplayName("generateSubPopSpecificParam")
	class SubPopExtraction {

		@Test
		@DisplayName("ORACLE: keeps entries matching the sub-population or \"All\", stripping the prefix")
		void extractsMatchingEntries(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir, row("person_GV", "MuCar", -240, -160, -200, "23", "TRUE"));

			LinkedHashMap<String, Double> in = map(
					"person_GV MuCar", -200.0,
					"All CapacityMultiplier", 1.5,
					"person_TCS MuCar", -111.0);

			LinkedHashMap<String, Double> out = r.generateSubPopSpecificParam(in, "person_GV");

			assertEquals(2, out.size());
			assertEquals(-200.0, out.get("MuCar"), 0.);
			assertEquals(1.5, out.get("CapacityMultiplier"), 0.);
			assertFalse(out.containsKey("person_TCS MuCar"));
		}

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-5: a matching key with NO space throws ArrayIndexOutOfBoundsException")
		void matchingKeyWithoutSpaceThrows(@TempDir Path dir) throws IOException {
			ParamReader r = reader(dir, row("person_GV", "MuCar", -240, -160, -200, "23", "TRUE"));

			assertThrows(ArrayIndexOutOfBoundsException.class,
					() -> r.generateSubPopSpecificParam(map("All", 1.0), "person_GV"));
		}
	}

	// ==================================================================
	// SetParamToConfig
	// ==================================================================

	@Nested
	@DisplayName("SetParamToConfig")
	class SetParamToConfigTests {

		/** Every name the no-sub-population branch of SetParamToConfig dereferences. */
		private Path fullCsv(Path dir) throws IOException {
			return csv(dir,
					row("", "MarginalUtilityofTravelCar", -240, -160, -200, "1", "TRUE"),
					row("", "MarginalUtilityofDistanceCar", -0.009, -0.006, -0.0075, "2", "TRUE"),
					row("", "MarginalUtilityofMoney", 0.8, 1.2, 1.0, "3", "TRUE"),
					row("", "DistanceBasedMoneyCostCar", 0, 0, 0, "4", "TRUE"),
					row("", "MarginalUtilityofTravelpt", -240, -160, -200, "5", "TRUE"),
					row("", "MarginalUtilityOfDistancePt", -1.2e-4, -8e-5, -1e-4, "6", "TRUE"),
					row("", "MarginalUtilityofWaiting", -7.2, -4.8, -6.0, "7", "TRUE"),
					row("", "UtilityOfLineSwitch", -6, -4, -5, "8", "TRUE"),
					row("", "MarginalUtilityOfWalking", -240, -160, -200, "9", "TRUE"),
					row("", "DistanceBasedMoneyCostWalk", -0.006, -0.004, -0.005, "10", "TRUE"),
					row("", "ModeConstantPt", 0, 0, 0, "11", "TRUE"),
					row("", "ModeConstantCar", 0, 0, 0, "12", "TRUE"),
					row("", "MarginalUtilityofPerform", 80, 120, 100, "13", "TRUE"),
					row("", "CapacityMultiplier", 0.5, 1.5, 1.25, "14", "TRUE"));
		}

		@Test
		@DisplayName("REVIEW_REQUIRED PARAM-3: SetParamToConfig writes config_Intermediate.xml into the "
				+ "CWD and reloads it, and the values do reach the Config")
		void writesConfigToCwdAndAppliesValues(@TempDir Path dir) throws IOException {
			ParamReader r = new ParamReader(fullCsv(dir).toString());
			Path sideEffect = Path.of("config_Intermediate.xml");
			assertFalse(Files.exists(sideEffect), "precondition: no stray file before the call");

			try {
				Config out = r.SetParamToConfig(ConfigUtils.createConfig(), new LinkedHashMap<>());

				// The disk round trip is observable: the file really is written to the CWD.
				assertTrue(Files.exists(sideEffect),
						"legacy behaviour: a CWD-relative config_Intermediate.xml is written");

				// And the values do flow through ScaleDown -> defaults -> ScaleUp into the Config.
				assertEquals(1.25, out.qsim().getFlowCapFactor(), 0.);
			} finally {
				Files.deleteIfExists(sideEffect);
			}
		}
	}

	// ==================================================================
	// getDefaultTimeBean
	// ==================================================================

	@Nested
	@DisplayName("getDefaultTimeBean")
	class DefaultTimeBean {

		@Test
		@DisplayName("ORACLE: the five canonical Hong Kong periods and their boundaries")
		void fiveCanonicalPeriods() {
			Map<String, Tuple<Double, Double>> tb = ParamReader.getDefaultTimeBean();

			assertEquals(5, tb.size());
			assertEquals(0.0, tb.get("BeforeMorningPeak").getFirst(), 0.);
			assertEquals(25200.0, tb.get("BeforeMorningPeak").getSecond(), 0.);
			assertEquals(25200.0, tb.get("MorningPeak").getFirst(), 0.);
			assertEquals(36000.0, tb.get("MorningPeak").getSecond(), 0.);
			assertEquals(36000.0, tb.get("AfterMorningPeak").getFirst(), 0.);
			assertEquals(57600.0, tb.get("AfterMorningPeak").getSecond(), 0.);
			assertEquals(57600.0, tb.get("EveningPeak").getFirst(), 0.);
			assertEquals(72000.0, tb.get("EveningPeak").getSecond(), 0.);
			assertEquals(72000.0, tb.get("AfterEveningPeak").getFirst(), 0.);
			assertEquals(86400.0, tb.get("AfterEveningPeak").getSecond(), 0.);
		}
	}

	// ------------------------------------------------------------------

	private static LinkedHashMap<String, Double> map(Object... kv) {
		LinkedHashMap<String, Double> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], (Double) kv[i + 1]);
		}
		return m;
	}
}
