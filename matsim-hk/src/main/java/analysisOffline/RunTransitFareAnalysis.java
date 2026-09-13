package analysisOffline;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.xml.sax.SAXException;

import com.google.inject.Scopes;
import com.google.inject.name.Names;

import analysisOffline.ODAnalysisModule;
import createPTGTFS.FareCalculatorPTGTFS;
import dynamicTransitRouter.RouteHelper;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.LRFareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import dynamicTransitRouter.transfer.AllPTTransferDiscount;
import transitFareAndHandler.TransitFareHandler;

public class RunTransitFareAnalysis {
	private final static String dir = "C:\\Users\\envf\\eclipse-workspace\\local_project\\outputHKIWithoutCWBHot3\\";
	
	private final static String fareDir = "data/";
	private final static String eventsFile = dir + "output_events.xml.gz";
	private final static String transitScheduleFile = dir + "output_transitSchedule.xml.gz";
	private final static String transitVehicleFile = dir + "output_transitVehicles.xml.gz";
	//XXX discount calculator
	private final static String discountJsonFile = fareDir+"transitDiscount.json";
	
	public static Map<String, FareCalculator> getFareCalculators(TransitSchedule ts, String fareDir) throws SAXException, IOException, ParserConfigurationException{
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(ts);
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		saxParser.parse(fareDir+"minibusFare.xml", busFareGetter);
		
		busFareGetter.get().checkValidity();
		
		Map<String, FareCalculator> fareCalculator = new HashMap<>();
		fareCalculator.put(RouteHelper.BusMode, FareCalculatorPTGTFS.loadFareCalculatorPTGTFS(fareDir+"busFareGTFS.json"));
		fareCalculator.put(RouteHelper.MinibusMode, busFareGetter.get()); 
		fareCalculator.put(RouteHelper.MTRMode, new MTRFareCalculator(fareDir+"mtr_lines_fares.csv", ts));
		fareCalculator.put(RouteHelper.LRMode, new LRFareCalculator(fareDir+"light_rail_fares.csv"));
		fareCalculator.put(RouteHelper.TramMode, new UniformFareCalculator(2.6));
		fareCalculator.put(RouteHelper.FerryMode, FareCalculatorPTGTFS.loadFareCalculatorPTGTFS(fareDir+"ferryFareGTFS.json"));
		fareCalculator.put(RouteHelper.firstClassMode, new MTRFareCalculator(fareDir+"first_class_fare.csv", ts));
		
		return fareCalculator;
	}
	
	public static void main(String[] args) throws IOException, Exception {
		Config config = ConfigUtils.createConfig();
		config.transit().setTransitScheduleFile(transitScheduleFile);
		config.transit().setVehiclesFile(transitVehicleFile);
		TransitSchedule ts = ScenarioUtils.loadScenario(config).getTransitSchedule();
		
		Map<String, FareCalculator> fareCalculator = getFareCalculators(ts, fareDir);
		
		AllPTTransferDiscount discount = new AllPTTransferDiscount(discountJsonFile);
		EventsManager eventsManager = new EventsManagerImpl();
		TransitFareHandler tfh = new TransitFareHandler(eventsManager, fareCalculator, ts.getTransitLines(), discount);
		eventsManager.addHandler(tfh);

		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
		matsimEventsReader.readFile(eventsFile);
		
		tfh.getTransitRouteTaken();
		try (Writer writer = new FileWriter(dir+"ridership.csv")) {
			  for (Map.Entry<String, Integer> entry : tfh.getTransitRouteTaken().entrySet()) {
			    writer.append("\""+entry.getKey()+"\"")
			          .append(',')
			          .append(entry.getValue().toString())
			          .append("\n");
			  }
			} catch (IOException ex) {
			  ex.printStackTrace(System.err);
		}
		
		System.out.println("Fare Collected Bus		:"+tfh.getBusFareCollected());
		System.out.println("Fare Collected Ferry	:"+tfh.getFerryFareCollected());
		System.out.println("Fare Collected LR		:"+tfh.getLRFareCollected());
		System.out.println("Fare Collected MTR		:"+tfh.getMtrFareCollected());
		System.out.println("Fare Collected Tram		:"+tfh.getTramFareCollected());
		System.out.println("Total Discount Given	:"+tfh.getDiscountGiven());
		
	}
}

