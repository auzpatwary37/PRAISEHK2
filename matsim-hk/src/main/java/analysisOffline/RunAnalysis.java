package analysisOffline;

import java.io.IOException;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;

public class RunAnalysis {
	private final static String dir = "C:/Users/eleead/Desktop/Simulation results/Car only/CWB with road pricing 2/";
	private final static String configFile = dir + "output_config.xml";
	private static final String eventsFile = dir + "output_events.xml";

	public static void main(String[] args) throws IOException {
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, configFile);

		Scenario scenario = ScenarioUtils.loadScenario(config);

		EventsManager eventsManager = EventsUtils.createEventsManager();
		ODAnalysisModule odModule = new ODAnalysisModule(scenario, eventsManager);

		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
		matsimEventsReader.readFile(eventsFile);

		odModule.writeInfo(dir);
	}
}
