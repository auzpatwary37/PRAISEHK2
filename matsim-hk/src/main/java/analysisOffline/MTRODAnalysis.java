package analysisOffline;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import javax.xml.parsers.ParserConfigurationException;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.xml.sax.SAXException;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitFareAndHandler.FirstClassCountHandler;
import transitFareAndHandler.TransitFareHandler;

/**
 * 
 * This class is inteneded to obtain the OD / O / D of the MTR system in the dynamic model, for more refined result analysis
 * 
 * @author Enoch Lee
 *
 */
public class MTRODAnalysis {
	private static String dir = "C:\\Users\\envf\\eclipse-workspace\\matsim-playground\\outHKTill9V2\\";
	private static String configFile = dir + "output_config.xml";
	private static String eventsFile = dir + "output_events.xml.gz";
	
	public static void main(String[] args) throws IOException {
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, configFile);
		config.plans().setInputFile(null);
		config.transit().setTransitScheduleFile(dir+"output_transitSchedule.xml.gz");
		config.transit().setVehiclesFile(dir+"output_transitVehicles.xml.gz");

		Scenario scenario = ScenarioUtils.loadScenario(config);
		Map<String, FareCalculator> fareCals = null;
		try {
			fareCals= RunTransitFareAnalysis.getFareCalculators(scenario.getTransitSchedule(), "fare/");
		} catch (SAXException|IOException|ParserConfigurationException e) {
			e.printStackTrace();
			throw new RuntimeException();
		} 
		
		EventsManager eventsManager = EventsUtils.createEventsManager();
		TransitFareHandler handler = new TransitFareHandler(eventsManager, fareCals, scenario.getTransitSchedule().getTransitLines(),
				null);
		List<Tuple<Double, Double>> mtrODTimeBins = new ArrayList<>();
		double AMStart = 8;
		mtrODTimeBins.add(new Tuple<>(AMStart*3600, AMStart*3600+15*60));
		mtrODTimeBins.add(new Tuple<>(AMStart*3600+15*60, AMStart*3600+30*60));
		mtrODTimeBins.add(new Tuple<>(AMStart*3600+30*60, AMStart*3600+45*60));
		mtrODTimeBins.add(new Tuple<>(AMStart*3600+45*60, AMStart*3600+60*60));
		mtrODTimeBins.add(new Tuple<>(18.*3600, 18.*3600+15*60));
		mtrODTimeBins.add(new Tuple<>(18.*3600+15*60, 18.*3600+30*60));
		mtrODTimeBins.add(new Tuple<>(18.*3600+30*60, 18.*3600+45*60));
		mtrODTimeBins.add(new Tuple<>(18.*3600+45*60, 18.*3600+60*60));
		handler.setMTRTimeBins(mtrODTimeBins);
		eventsManager.addHandler(handler);
		
		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
		matsimEventsReader.readFile(eventsFile);
		
		Map<String, Map<String, List<Integer>>> mtrODCount = handler.getMTRODCount();
		
		//Write the output OD file
		FileWriter odFileWriter = new FileWriter(dir+"OD_counts.csv", false);
		FileWriter oFileWriter = new FileWriter(dir+"O_counts.csv", false);
		odFileWriter.append("from_stop,to_stop,1,2,3,4\n");
		oFileWriter.append("from_stop,1,2,3,4\n");
		Map<String, List<Double>> dCount = new HashMap<>();
		for(var entry: mtrODCount.entrySet()) {
			double[] counts = new double[mtrODTimeBins.size()];
			for(var toStop: entry.getValue().entrySet()) {
				//Write the OD file
				odFileWriter.append(entry.getKey()+","+toStop.getKey());
				for(int i = 0; i < counts.length; i++) {
					odFileWriter.append(","+toStop.getValue().get(i));
					counts[i] += toStop.getValue().get(i);
				}
				odFileWriter.append("\n");
				//Setup the destination count
				List<Double> destinationCounts = new ArrayList<>();
				if(dCount.containsKey(toStop.getKey())){
					destinationCounts = dCount.get(toStop.getKey());
					for(int i = 0; i < counts.length; i++)
						destinationCounts.set(i, destinationCounts.get(i) + toStop.getValue().get(i));
				}else {
					destinationCounts = DoubleStream.of(counts).boxed().collect(Collectors.toList());
				}
				dCount.put(toStop.getKey(), destinationCounts);
			}
			
			//Write the O file
			oFileWriter.append(entry.getKey());
			for(int i = 0; i < counts.length; i++)
				oFileWriter.append(","+counts[i]);
			oFileWriter.append("\n");
		}
		odFileWriter.close();
		oFileWriter.close();

		FileWriter dFileWriter = new FileWriter(dir+"D_counts.csv", false);
		dFileWriter.append("to_stop,7-8,8-9,9-10\n");
		for(var toStopCount: dCount.entrySet()) {
			dFileWriter.append(toStopCount.getKey());
			for(int i = 0; i < mtrODTimeBins.size(); i++)
				dFileWriter.append(","+toStopCount.getValue().get(i));
			dFileWriter.append("\n");
		}
		dFileWriter.close();
	}
}
