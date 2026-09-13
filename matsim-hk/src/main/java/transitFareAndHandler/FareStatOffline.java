package transitFareAndHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.xml.sax.SAXException;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import dynamicTransitRouter.transfer.AllPTTransferDiscount;

/**
 * A helper class to analysis the fare stat
 * TODO: Update it to the newest measure. 19 June
 * @author eleead
 *
 */
public class FareStatOffline {
	private static final String configFile = "data/config.xml";
	
	private static final String eventsFile = "outputDynamicTransitFactor0.0/120.EBPTR.events.xml";
	
	// =======================================================================================================		
	
	public static void main (String[] args) throws Exception{
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "data/config.xml");

		Scenario scenario = ScenarioUtils.loadScenario(config);

		EventsManager eventsManager = EventsUtils.createEventsManager();
		TransitFareHandler handler = new TransitFareHandler(eventsManager, getFareMap(scenario.getTransitSchedule()), 
				scenario.getTransitSchedule().getTransitLines(), new AllPTTransferDiscount("data/GMB.csv"));
		
		eventsManager.addHandler(handler);

		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
		matsimEventsReader.readFile(eventsFile);
		
		handler.reset(120);
	}
	
	private static Map<String, FareCalculator> getFareMap(TransitSchedule ts) throws SAXException, IOException, ParserConfigurationException{
		Map<String, FareCalculator> fareMap = new HashMap<>();

		fareMap.put("train", new MTRFareCalculator("input/mtr_lines_fares.csv", ts));
		
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(ts);
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

		saxParser.parse("data/busFare.xml", busFareGetter);
		fareMap.put("bus", busFareGetter.get());

		// The tram fare calculator
		fareMap.put( "tram", new UniformFareCalculator(2.3) );
		fareMap.put( "ship", new UniformFareCalculator(3) );
		return fareMap;
	}
}
