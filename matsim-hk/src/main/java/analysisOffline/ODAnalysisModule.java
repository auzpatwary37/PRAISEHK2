package analysisOffline;

import java.io.IOException;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;

public class ODAnalysisModule {

	private ODHandler handler;
	private final EventsManager eventsManager;

	public ODAnalysisModule(final Scenario scenario, final EventsManager eventsManager) {
		this.eventsManager = eventsManager;
		this.handler = new ODHandler(scenario.getNetwork().getLinks());
		this.eventsManager.addHandler(handler);
	}

	/*
	 * public EventsManager getEventsManager(){ return this.eventsManager; }
	 */

	public void writeInfo(String filePath) throws IOException {
		this.handler.writeCSV(filePath);
	}
}
