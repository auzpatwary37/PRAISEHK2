package analysisOffline;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.beust.jcommander.internal.Lists;

import running.RunUtils;
import transitFareAndHandler.FirstClassCountHandler;
import transitFareAndHandler.MTRFlowEventHandler;
import transitFareAndHandler.StopCountHandler;
import transitFareAndHandler.StopWaitingTimeHandler;

public class MTRFlowAnalysis {
	private static String dir = "outputMTRAfter87NTE889Hot/";
	private static String configFile = dir + "output_config.xml";
	private static String eventsFile = dir + "0.events.xml.gz";
	
	public static void main(String[] args) throws IOException {
//		FileWriter fileWriter = new FileWriter(dir+"MTR_sensitivity.csv", true);
//		fileWriter.append("station,actual7,sim7,actual8,sim8,actual9,sim9\n");
//		fileWriter.append(evaluateCountDiff(fileWriter, "")+"\n");
//		fileWriter.flush();
//		fileWriter.close();
		
//		FileWriter fileWriter2 = new FileWriter(dir+"MTR_sensitivity.csv", true);
//		fileWriter2.append("station,sim7,sim8,sim9\n");
//		getCounts(fileWriter2);
//		fileWriter2.flush();
//		fileWriter2.close();
		getComprehensiveCounts(10.);
	}
	
//	public static List<List<Tuple<String,String>>> getTMLP2CountList() {
//		// Get the count list for TML Phase II
//		return Lists.newArrayList(
//				Lists.newArrayList(new Tuple<>("KTL-TIK_WHA","KTL_KOTDown"),new Tuple<>("KTL-TIK_HOM","KTL_KOTDown")), //KTL KOT-SKM
//				Lists.newArrayList(new Tuple<>("KTL-TIK_WHA","KTL_SKMDown"),new Tuple<>("KTL-TIK_HOM","KTL_SKMDown")), //KTP SKM-PRE
//				Lists.newArrayList(new Tuple<>("EAL-LMC_HUH","EAL_TAWDown"),new Tuple<>("EAL-LOW_HUH","EAL_TAWDown")), //EAL TAW-KOT
//				Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_CKTDown")), //TML CKT-TAW
//				Lists.newArrayList(new Tuple<>("TKL-LHP_NOP","TKL_YATDown"),new Tuple<>("TKL-POA_NOP","TKL_YATDown")), //TKL YAT-QUB
//				Lists.newArrayList(new Tuple<>("ISL-CHW_KET","ISL_NOPUp")),
//				Lists.newArrayList(new Tuple<>("ISL-CHW_KET","ISL_TIHUp")),
//				
//				Lists.newArrayList(new Tuple<>("SIL-ADM_SOH","SIL_ADMUp")),
//				Lists.newArrayList(new Tuple<>("SIL-SOH_ADM","SIL_OCPDown")),
//				Lists.newArrayList(new Tuple<>("TCL-TUC_HOK","TCL_KOWDown")),
//				Lists.newArrayList(new Tuple<>("TWL-TSW_CEN","TWL_YMTDown")),
//				Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_KSRUp"))
//				); 
//	}
//	
//	public static List<List<Tuple<String,String>>> getTMLP1CountList() {
//		// Get the count list for TML Phase I
//		return Lists.newArrayList(
//				Lists.newArrayList(new Tuple<>("KTL-TIK_WHA","KTL_KOTDown"),new Tuple<>("KTL-TIK_HOM","KTL_KOTDown")), //KTL KOT-SKM
//				Lists.newArrayList(new Tuple<>("KTL-TIK_WHA","KTL_SKMDown"),new Tuple<>("KTL-TIK_HOM","KTL_SKMDown")), //KTP SKM-PRE
//				Lists.newArrayList(new Tuple<>("EAL-LMC_HUH","EAL_TAWDown"),new Tuple<>("EAL-LOW_HUH","EAL_TAWDown")), //EAL TAW-KOT
//				Lists.newArrayList(new Tuple<>("TML-WKS_KAT","TML_CKTDown")), //TML CKT-TAW
//				Lists.newArrayList(new Tuple<>("TKL-LHP_NOP","TKL_YATDown"),new Tuple<>("TKL-POA_NOP","TKL_YATDown")), //TKL YAT-QUB
//				Lists.newArrayList(new Tuple<>("ISL-CHW_KET","ISL_NOPUp")),
//				Lists.newArrayList(new Tuple<>("ISL-CHW_KET","ISL_TIHUp")),
//				
//				Lists.newArrayList(new Tuple<>("SIL-ADM_SOH","SIL_ADMUp")),
//				Lists.newArrayList(new Tuple<>("SIL-SOH_ADM","SIL_OCPDown")),
//				Lists.newArrayList(new Tuple<>("TCL-TUC_HOK","TCL_KOWDown")),
//				Lists.newArrayList(new Tuple<>("TWL-TSW_CEN","TWL_YMTDown")),
//				Lists.newArrayList(new Tuple<>("WRL-TUM_HUH","WRL_KSRDown"))
//				); 
//	}
		
//	public static double evaluateCountDiff(FileWriter fileWriter, String prefix) throws IOException {
//		Config config = ConfigUtils.createConfig();
//		ConfigUtils.loadConfig(config, configFile);
//
//		Scenario scenario = ScenarioUtils.loadScenario(config);
//
//		EventsManager eventsManager = EventsUtils.createEventsManager();
//		MTRFlowEventHandler handler7 = new MTRFlowEventHandler(scenario.getTransitSchedule(), 
//				scenario.getTransitVehicles(), 7 * 3600, 8 * 3600);
//		MTRFlowEventHandler handler8 = new MTRFlowEventHandler(scenario.getTransitSchedule(), 
//				scenario.getTransitVehicles(), 8 * 3600, 9 * 3600);
//		MTRFlowEventHandler handler9 = new MTRFlowEventHandler(scenario.getTransitSchedule(), 
//				scenario.getTransitVehicles(), 9 * 3600, 10 * 3600);
//		eventsManager.addHandler(handler7);
//		eventsManager.addHandler(handler8);
//		eventsManager.addHandler(handler9);
//
//		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
//		matsimEventsReader.readFile(eventsFile);
//		
//		List<List<Tuple<String,String>>> countList = getTMLP2CountList();
//				
//		List<Double> counts7 = Lists.newArrayList(27354.4,27137.7,22762.1,13117.5,27981.5,26663.7,24524.5,
//				4467.1, 6277.3,10408.9, 24712.4, 22580.6);
//		List<Double> counts8 = Lists.newArrayList(43544.,44599.3,35520.8,16129.8,43108.6,45840.1,45459.2,
//				7757.4, 8871.1, 21191.8, 48003.4, 32438.1);
//		List<Double> counts9 = Lists.newArrayList(28253.1,30056.6,18909.2,7931.5,25963.4,30107.,31586.4,
//				4490.8,	5676.3, 14138.3, 36261.3, 15394.6);
//		
//		//Loop through the counts and evaluate the problem
//		double totalCountDiff = 0;
//		for(int i = 0; i< countList.size(); i++) {
//			List<Tuple<String, String>> stationCountList = countList.get(i);
//			double count7 = 0, count8 = 0, count9 = 0;
//			for(Tuple<String, String> routeAndStop: stationCountList) {
//				double routeStopCount7 = handler7.getCount(Id.create(routeAndStop.getFirst(), TransitRoute.class), Id.create(routeAndStop.getSecond(), TransitStopFacility.class));
//				count7 += routeStopCount7;
//				double routeStopCount8 = handler8.getCount(Id.create(routeAndStop.getFirst(), TransitRoute.class), Id.create(routeAndStop.getSecond(), TransitStopFacility.class));
//				count8 += routeStopCount8;
//				double routeStopCount9 = handler9.getCount(Id.create(routeAndStop.getFirst(), TransitRoute.class), Id.create(routeAndStop.getSecond(), TransitStopFacility.class));
//				count9 += routeStopCount9;
//			}
//			fileWriter.append(prefix+stationCountList.get(0).getSecond()+",");
//			fileWriter.append(counts7.get(i)+","+count7+","+counts8.get(i)+","+count8+","+counts9.get(i)+","+count9+"\n");
//			double diff = Math.abs(counts7.get(i) - count7) + Math.abs(counts8.get(i) - count8) +
//					Math.abs(counts9.get(i) - count9);
//			totalCountDiff += diff;
//		}
//		return totalCountDiff;
//	}
	
	/**
	 * This function gets the stop aboard count, passenger departure count, stop queues, first class hourly count, etc.
	 * @param scale
	 * @throws IOException
	 */
	public static void getComprehensiveCounts(double scale) throws IOException{
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, configFile);
		config.plans().setInputFile(null);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		RunUtils.scaleDownPt(scenario.getTransitVehicles(), 1./scale);

		EventsManager eventsManager = EventsUtils.createEventsManager();
		List<MTRFlowEventHandler> handlers = new ArrayList<>();
		List<FirstClassCountHandler> fistClassHandlers = new ArrayList<>();
		for(double i = 0; i < 20 * 3; i++) {
			MTRFlowEventHandler handler = new MTRFlowEventHandler(scenario.getTransitSchedule(), 
					scenario.getTransitVehicles(), (7 + i * 5/60) * 3600, (7 + (i+1) * 5/60) * 3600);
			handlers.add(handler);
			eventsManager.addHandler(handler);
		}
		
		for(int i = 6; i < 10; i++) {
			FirstClassCountHandler handler = new FirstClassCountHandler(i * 3600, (i+1) * 3600);
			fistClassHandlers.add(handler);
			eventsManager.addHandler(handler);
		}
		//Add the stop count
		StopCountHandler stopCountHandler = new StopCountHandler(Lists.newArrayList("ISL_ADMDown","ISL_ADMUp",
				"TWL_ADMUp", "SIL_ADMUp","NSL_ADMUp","NSL_KOTDown","NSL_HUHDown","NSL_TAWDown","NSL_TWODown", "NSL_FANDown",
				"NSX_KOTDown","NSX_HUHDown","NSX_TAWDown","NSX_TWODown", "NSX_FANDown", "NSX_TAPDown", "NSX_UNIDown", "NSX_FOTDown", "NSX_SHTDown",
				"TML_TAWDown", "EAL_TAWDown", "NSL_TAPDown", "NSL_UNIDown", "NSL_FOTDown", "NSL_SHTDown",
				"EAL_TWODown",  "EAL_FANDown","EAL_TAPDown", "EAL_UNIDown", "EAL_FOTDown", "EAL_SHTDown"), 10 * 3600, 10.);
		eventsManager.addHandler(stopCountHandler);
		
		StopWaitingTimeHandler stopWait = new StopWaitingTimeHandler(Lists.newArrayList(
				"NSL_KOTDown","NSL_HUHDown","NSL_TAWDown", "NSL_TAPDown", "NSL_UNIDown", "NSL_FOTDown", "NSL_SHTDown", 
				"NSX_KOTDown","NSX_HUHDown","NSX_TAWDown","NSX_TWODown", "NSX_FANDown", "NSX_TAPDown", "NSX_UNIDown", "NSX_FOTDown", "NSX_SHTDown",
				"NSL_TWODown", "NSL_FANDown", "EAL_TWODown",  "EAL_FANDown",
				"EAL_TAPDown", "EAL_UNIDown", "EAL_FOTDown", "EAL_SHTDown", "EAL_TAWDown"), 6 * 3600, 10 * 3600);
		eventsManager.addHandler(stopWait);
		
		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
		matsimEventsReader.readFile(eventsFile);
		
		FileWriter fcfileWriter = new FileWriter(dir+"first_class_count.csv", false);
		fcfileWriter.append("hour,count\n");
		for(int i = 0; i < 4; i++) {
			FirstClassCountHandler handler = fistClassHandlers.get(i);
			fcfileWriter.append((i+6)+","+handler.getCount()+"\n");
		}
		fcfileWriter.close();
		
		stopWait.printStopWaiting(dir);
		stopCountHandler.createGraphs(dir);
		
		FileWriter fileWriter = new FileWriter(dir+"MTR_sensitivityTable.csv", false);
		fileWriter.append("line,station,dir");
		
		List<Map<Id<TransitLine>, Map<Id<TransitStopFacility>, Double>>> flows = new ArrayList<>();
		List<Map<Id<TransitLine>, Map<Id<TransitStopFacility>, Double>>> caps = new ArrayList<>();
		for(int i = 0; i < handlers.size(); i++) {
			MTRFlowEventHandler handler = handlers.get(i);
			flows.add(handler.processLineFlow());
			caps.add(handler.processCap());
			fileWriter.append(",sim"+i+",cap"+i);
		}
		fileWriter.append("\n");
		
//		Map<Id<TransitLine>, Map<Id<TransitStopFacility>, Double>> flow7 = handler7.processLineFlow();
//		Map<Id<TransitLine>, Map<Id<TransitStopFacility>, Double>> flow8 = handler8.processLineFlow();
//		Map<Id<TransitLine>, Map<Id<TransitStopFacility>, Double>> flow9 = handler9.processLineFlow();
		for(var lineEntry: flows.get(0).entrySet()) {
			for(var stopEntry: lineEntry.getValue().entrySet()) {
				fileWriter.append(lineEntry.getKey().toString()+","); // Line
				fileWriter.append(scenario.getTransitSchedule().getFacilities().get(stopEntry.getKey()).getName()+","); //Name
				fileWriter.append(stopEntry.getKey().toString().substring(7)+","+ stopEntry.getValue());
				fileWriter.append(","+ caps.get(0).get(lineEntry.getKey()).get(stopEntry.getKey()));
				
				for(int i = 1; i < 20 * 3; i++) {
					fileWriter.append(","+flows.get(i).get(lineEntry.getKey()).get(stopEntry.getKey()));
					fileWriter.append(","+caps.get(i).get(lineEntry.getKey()).get(stopEntry.getKey()));
				}
				fileWriter.append("\n");
			}
		}
		fileWriter.flush();
		fileWriter.close();	
	}
	
//	public static void getCounts(FileWriter fileWriter) throws IOException {
//		List<List<Tuple<String,String>>> EAL_TMLList = Lists.newArrayList(
//		Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_SHSDown")), //EAL TAW-KOT
//		Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_FANDown")), //EAL TAW-KOT
//		Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_TWODown")), //EAL TAW-KOT
//		Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_TAPDown"),new Tuple<>("EAL-TAP_HUH","EAL_TAPDown")), //EAL TAW-KOT
//		Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_UNIDown"),new Tuple<>("EAL-TAP_HUH","EAL_UNIDown")), //EAL TAW-KOT
//		Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_FOTDown"),new Tuple<>("EAL-TAP_HUH","EAL_FOTDown"),
//				new Tuple<>("EAL-FOT_HUH","EAL_FOTDown")), //EAL TAW-KOT	
//		Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_SHTDown"),new Tuple<>("EAL-TAP_HUH","EAL_SHTDown"),
//				new Tuple<>("EAL-FOT_HUH","EAL_SHTDown"),new Tuple<>("EAL-SHT_HUH","EAL_SHTDown")), //EAL TAW-KOT
//		Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_TAWDown"),new Tuple<>("EAL-TAP_HUH","EAL_TAWDown"),
//				new Tuple<>("EAL-FOT_HUH","EAL_TAWDown"),new Tuple<>("EAL-SHT_HUH","EAL_TAWDown")),
//		Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_KOTDown"),new Tuple<>("EAL-TAP_HUH","EAL_KOTDown"),
//				new Tuple<>("EAL-FOT_HUH","EAL_KOTDown"),new Tuple<>("EAL-SHT_HUH","EAL_KOTDown")),
//		Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_MKKDown"),new Tuple<>("EAL-TAP_HUH","EAL_MKKDown"),
//				new Tuple<>("EAL-FOT_HUH","EAL_MKKDown"),new Tuple<>("EAL-SHT_HUH","EAL_MKKDown")),
//		
//		Lists.newArrayList(new Tuple<>("EAL-HUH_SHS","EAL_HUHUp")), //EAL TAW-KOT
//		Lists.newArrayList(new Tuple<>("EAL-HUH_SHS","EAL_MKKUp")), 
//		Lists.newArrayList(new Tuple<>("EAL-HUH_SHS","EAL_KOTUp")), 
//		Lists.newArrayList(new Tuple<>("EAL-HUH_SHS","EAL_TAWUp")), 
//		Lists.newArrayList(new Tuple<>("EAL-HUH_SHS","EAL_SHTUp")), 
//		Lists.newArrayList(new Tuple<>("EAL-HUH_SHS","EAL_FOTUp")), 
//		Lists.newArrayList(new Tuple<>("EAL-HUH_SHS","EAL_UNIUp")), 
//		Lists.newArrayList(new Tuple<>("EAL-HUH_SHS","EAL_TAPUp")), 
//		Lists.newArrayList(new Tuple<>("EAL-HUH_SHS","EAL_TWOUp")), 
//		Lists.newArrayList(new Tuple<>("EAL-HUH_SHS","EAL_FANUp")), 
//		
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_TUMDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_SIHDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_TISDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_LOPDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_YULDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_KSRDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_TWWDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_MEFDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_NACDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_AUSDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_ETSDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_HUHDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_HOMDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_TKWDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_SUWDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_KATDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_DIHDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_HIKDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_TAWDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_CKTDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_STWDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_CIODown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_SHMDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_TSHDown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_HEODown")),
//		Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_MOSDown")),
//		
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_WKSUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_MOSUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_HEOUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_TSHUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_SHMUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_CIOUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_STWUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_CKTUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_TAWUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_HIKUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_DIHUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_KATUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_SUWUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_TKWUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_HOMUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_HUHUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_ETSUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_AUSUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_NACUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_MEFUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_TWWUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_KSRUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_YULUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_LOPUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_TISUp")),
//		Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_SIHUp"))
//		); 
//		
//		Config config = ConfigUtils.createConfig();
//		ConfigUtils.loadConfig(config, configFile);
//
//		Scenario scenario = ScenarioUtils.loadScenario(config);
//
//		EventsManager eventsManager = EventsUtils.createEventsManager();
//		MTRFlowEventHandler handler7 = new MTRFlowEventHandler(scenario.getTransitSchedule(), 
//				scenario.getTransitVehicles(), 7 * 3600, 8 * 3600);
//		MTRFlowEventHandler handler8 = new MTRFlowEventHandler(scenario.getTransitSchedule(), 
//				scenario.getTransitVehicles(), 8 * 3600, 9 * 3600);
//		MTRFlowEventHandler handler9 = new MTRFlowEventHandler(scenario.getTransitSchedule(), 
//				scenario.getTransitVehicles(), 9 * 3600, 10 * 3600);
//		eventsManager.addHandler(handler7);
//		eventsManager.addHandler(handler8);
//		eventsManager.addHandler(handler9);
//
//		MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
//		matsimEventsReader.readFile(eventsFile);
//		
//		for(int i = 0; i< EAL_TMLList.size(); i++) {
//			List<Tuple<String, String>> stationCountList = EAL_TMLList.get(i);
//			double count7 = 0, count8 = 0, count9 = 0;
//			for(Tuple<String, String> routeAndStop: stationCountList) {
//				double routeStopCount7 = handler7.getCount(Id.create(routeAndStop.getFirst(), TransitRoute.class), Id.create(routeAndStop.getSecond(), TransitStopFacility.class));
//				count7 += routeStopCount7;
//				double routeStopCount8 = handler8.getCount(Id.create(routeAndStop.getFirst(), TransitRoute.class), Id.create(routeAndStop.getSecond(), TransitStopFacility.class));
//				count8 += routeStopCount8;
//				double routeStopCount9 = handler9.getCount(Id.create(routeAndStop.getFirst(), TransitRoute.class), Id.create(routeAndStop.getSecond(), TransitStopFacility.class));
//				count9 += routeStopCount9;
//			}
//			fileWriter.append(stationCountList.get(0).getSecond()+",");
//			fileWriter.append(count7+","+count8+","+count9+"\n");
//		}
//	}
}
