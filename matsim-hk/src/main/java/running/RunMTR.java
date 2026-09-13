package running;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.QSimConfigGroup.VehiclesSource;
import org.matsim.core.controler.Controler;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.population.routes.RouteFactories;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.TransitScheduleReaderV2;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.xml.sax.SAXException;

import com.beust.jcommander.internal.Lists;

import de.xypron.jcobyla.Calcfc;
import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import transitFareAndHandler.MTRFlowEventHandler;

public class RunMTR {
	/**
	 * This short class demonstrates how to run the MTR line scneario only.
	 * 
	 * @param args
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws InterruptedException
	 */
	private static final String PersonChangeWithCar_NAME = "person_TCSwithCar";
	private static final String PersonFixed_NAME = "trip_TCS";
	
    private static double rhobeg = 0.5;
    private static double rhoend = 1.0e-6;
    private static int iprint = 1;
    private static int maxfun = 3500;
	
	public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException, InterruptedException {
//		createMTRPopulation("data/OD_August_process.csv");
		FileWriter fileWriter = new FileWriter("onlyMTR/MTR_sensitivity_Aug.csv");
		fileWriter.append("pt_travel,pt_distance,walk_distance,station,actual7,sim7,actual8,sim8,actual9,sim9\n");
//		for(double waitUtil = -10; waitUtil < -2; waitUtil += 0.5) {
//			for(double j = -15; j < 0; j += 1) {
//				for(double distanceRateWalk = -0.008; distanceRateWalk < -0.002; distanceRateWalk += 0.0005) {
		Calcfc calcfc = new Calcfc() {
			@Override
			public double compute(int n, int m, double[] x, double[] con) {
				double waitUtil = x[0];
				double j = x[1];
				double distanceRateWalk = x[2];
				
				String prefix = waitUtil +"," + j + "," + distanceRateWalk + ",";

				Config config = setupConfig();
				config.controler().setOutputDirectory("outputFullHKTMLTrialAug"+waitUtil+"_"+j+"_"+distanceRateWalk+"/");
				config.planCalcScore().getScoringParameters(PersonFixed_NAME).setUtilityOfLineSwitch(j);
				config.planCalcScore().getScoringParameters(PersonFixed_NAME).getModes().get("walk")
					.setMonetaryDistanceRate(distanceRateWalk);
				config.planCalcScore().getScoringParameters(PersonFixed_NAME).setMarginalUtlOfWaitingPt_utils_hr(waitUtil);
				
				Scenario scenario = ScenarioUtils.loadScenario(config);
				//Start the run
				Controler controler = new Controler(scenario);

				ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
				SAXParser saxParser;
				try {
					saxParser = SAXParserFactory.newInstance().newSAXParser();
					saxParser.parse("data/busFare.xml", busFareGetter);
				} catch (ParserConfigurationException | SAXException | IOException e) {
					e.printStackTrace();
					throw new RuntimeException();
				}

				controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/new_mtr_fare.csv", "fare/first_class_fare.csv", 
						"fare/transitDiscount.json", "fare/light_rail_fares.csv", "fare/busFareGTFS.json", 
						"fare/ferryFareGTFS.json"));
				MTRFlowEventHandler handler7 = new MTRFlowEventHandler(scenario.getTransitSchedule(), scenario.getTransitVehicles(), 7 * 3600, 8 * 3600);
				MTRFlowEventHandler handler8 = new MTRFlowEventHandler(scenario.getTransitSchedule(), scenario.getTransitVehicles(), 8 * 3600, 9 * 3600);
				MTRFlowEventHandler handler9 = new MTRFlowEventHandler(scenario.getTransitSchedule(), scenario.getTransitVehicles(), 9 * 3600, 10 * 3600);
				controler.getEvents().addHandler(handler7);
				controler.getEvents().addHandler(handler8);
				controler.getEvents().addHandler(handler9);
				controler.run();
				
//				List<List<Tuple<String,String>>> countList = Lists.newArrayList(
//						Lists.newArrayList(new Tuple<>("KTL-TIK_WHA","KTL_KOTDown"),new Tuple<>("KTL-TIK_HOM","KTL_KOTDown")), //KTL KOT-SKM
//						Lists.newArrayList(new Tuple<>("KTL-TIK_WHA","KTL_SKMDown"),new Tuple<>("KTL-TIK_HOM","KTL_SKMDown")), //KTP SKM-PRE
//						Lists.newArrayList(new Tuple<>("EAL-LMC_HUH","EAL_TAWDown"),new Tuple<>("EAL-LOW_HUH","EAL_TAWDown")), //EAL TAW-KOT
//						Lists.newArrayList(new Tuple<>("TML-WKS_KAT","TML_CKTDown")), //TML CKT-TAW
//						Lists.newArrayList(new Tuple<>("TKL-LHP_NOP","TKL_YATDown"),new Tuple<>("TKL-POA_NOP","TKL_YATDown")), //TKL YAT-QUB
//						Lists.newArrayList(new Tuple<>("ISL-CHW_KET","ISL_NOPUp")), //ISL NOP-FOH
//						Lists.newArrayList(new Tuple<>("ISL-CHW_KET","ISL_TIHUp")), //ISL TIH-CWB
//						
//						Lists.newArrayList(new Tuple<>("SIL-ADM_SOH","SIL_ADMUp")), // SIL ADM-OCP
//						Lists.newArrayList(new Tuple<>("SIL-SOH_ADM","SIL_OCPDown")), //SIL OCP-ADM
//						Lists.newArrayList(new Tuple<>("TCL-TUC_HOK","TCL_KOWDown")), //SIL KOW-HOK
//						Lists.newArrayList(new Tuple<>("TWL-TSW_CEN","TWL_YMTDown")), //TWL YMT-JOR
//						Lists.newArrayList(new Tuple<>("WRL-TUM_HUH","WRL_KSRDown")) //WRL KSR-TWW
//						); 
				
				
				List<List<Tuple<String,String>>> countList = Lists.newArrayList(
						Lists.newArrayList(new Tuple<>("KTL-TIK_WHA","KTL_KOTDown"),new Tuple<>("KTL-TIK_HOM","KTL_KOTDown")), //KTL KOT-SKM
						Lists.newArrayList(new Tuple<>("KTL-TIK_WHA","KTL_SKMDown"),new Tuple<>("KTL-TIK_HOM","KTL_SKMDown")), //KTP SKM-PRE
						Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_TAWDown"),new Tuple<>("EAL-FOT_HUH","EAL_TAWDown"),
								new Tuple<>("EAL-TAP_HUH","EAL_TAWDown"),new Tuple<>("EAL-SHT_HUH","EAL_TAWDown")), //EAL TAW-KOT
						Lists.newArrayList(new Tuple<>("EAL-SHS_HUH","EAL_KOTDown"),new Tuple<>("EAL-FOT_HUH","EAL_KOTDown"),
								new Tuple<>("EAL-TAP_HUH","EAL_KOTDown"),new Tuple<>("EAL-SHT_HUH","EAL_KOTDown")), //EAL TAW-KOT
						Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_CKTDown")), //TML CKT-TAW
						Lists.newArrayList(new Tuple<>("TML-WKS_TUM","TML_HIKDown")), //TML CKT-TAW
						Lists.newArrayList(new Tuple<>("TKL-LHP_NOP","TKL_YATDown"),new Tuple<>("TKL-POA_NOP","TKL_YATDown")), //TKL YAT-QUB
						Lists.newArrayList(new Tuple<>("ISL-CHW_KET","ISL_NOPUp")), //ISL NOP-FOH
						Lists.newArrayList(new Tuple<>("ISL-CHW_KET","ISL_TIHUp")), //ISL TIH-CWB
						
						Lists.newArrayList(new Tuple<>("SIL-ADM_SOH","SIL_ADMUp")), // SIL ADM-OCP
						Lists.newArrayList(new Tuple<>("SIL-SOH_ADM","SIL_OCPDown")), //SIL OCP-ADM
						Lists.newArrayList(new Tuple<>("TCL-TUC_HOK","TCL_KOWDown")), //SIL KOW-HOK
						Lists.newArrayList(new Tuple<>("TWL-TSW_CEN","TWL_YMTDown")), //TWL YMT-JOR
						Lists.newArrayList(new Tuple<>("TML-TUM_WKS","TML_KSRUp")) //TML KSR-TWW
						); 								
						
				List<Double> counts7 = Lists.newArrayList(19134.,19574.,14043.,0., 9741.,0.,19926.,19988.,18809.,
						3584.,4877.,8558.,21300., 21651.);
				List<Double> counts8 = Lists.newArrayList(37193.,38530.,25774.,13280., 16647.,21743.,42664.,45856.,45526.,
						7288.,8765.,22188.,49102., 34159.);
				List<Double> counts9 = Lists.newArrayList(26112.,27984.,14476.,0., 9164.,0.,27455.,32218.,33696.,
						4705.,6536., 15116.,38840., 16906.);
				
//				List<Double> counts7 = Lists.newArrayList(27354.4,27137.7,22762.1,13117.5,27981.5,26663.7,24524.5,
//						4467.1, 6277.3,10408.9, 24712.4, 22580.6);
//				List<Double> counts8 = Lists.newArrayList(43544.,44599.3,35520.8,16129.8,43108.6,45840.1,45459.2,
//						7757.4, 8871.1, 21191.8, 48003.4, 32438.1);
//				List<Double> counts9 = Lists.newArrayList(28253.1,30056.6,18909.2,7931.5,25963.4,30107.,31586.4,
//						4490.8,	5676.3, 14138.3, 36261.3, 15394.6);
				
				//Loop through the counts and evaluate the problem
				double totalCountDiff = 0;
				for(int i = 0; i< countList.size(); i++) {
					List<Tuple<String, String>> stationCountList = countList.get(i);
					double count7 = 0, count8 = 0, count9 = 0;
					for(Tuple<String, String> routeAndStop: stationCountList) {
						double routeStopCount7 = handler7.getCount(Id.create(routeAndStop.getFirst(), TransitRoute.class), Id.create(routeAndStop.getSecond(), TransitStopFacility.class));
						count7 += routeStopCount7;
						double routeStopCount8 = handler8.getCount(Id.create(routeAndStop.getFirst(), TransitRoute.class), Id.create(routeAndStop.getSecond(), TransitStopFacility.class));
						count8 += routeStopCount8;
						double routeStopCount9 = handler9.getCount(Id.create(routeAndStop.getFirst(), TransitRoute.class), Id.create(routeAndStop.getSecond(), TransitStopFacility.class));
						count9 += routeStopCount9;
					}
					//Scale back the MATSim count
					count7 /= 10;
					count8 /= 10;
					count9 /= 10;
					try {
						fileWriter.append(prefix+stationCountList.get(0).getSecond()+",");
						fileWriter.append(counts7.get(i)+","+count7+","+counts8.get(i)+","+count8+","+counts9.get(i)+","+count9+"\n");
					} catch (IOException e) {
						e.printStackTrace();
						throw new RuntimeException();
					}
					double diff = Math.pow(Math.abs(counts7.get(i) - count7), 2) + 
							Math.pow(Math.abs(counts8.get(i) - count8), 2) +
							Math.pow(Math.abs(counts9.get(i) - count9), 2);
					totalCountDiff += diff;
				}
				try {
					fileWriter.append(totalCountDiff+"\n");
					fileWriter.flush();
				}catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException();
				}
				throw new RuntimeException(); //To terminate the program
				//return totalCountDiff;
			}
		};
		
		double[] x = {-5,-5,-0.0058};
		CobylaExitStatus result = Cobyla.findMinimum(calcfc, 3, 0, x, rhobeg, rhoend, iprint, maxfun);
		fileWriter.flush();
		fileWriter.close();
	}
	
	private static Config setupConfig() {
		Config config = ConfigUtils.loadConfig("config_clean.xml", new EmissionsConfigGroup());
		
		Config configGV = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(configGV, "data/config_Ashraf.xml");
		for (ActivityParams act: configGV.planCalcScore().getActivityParams()) {
			if(config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getActivityParams(act.getActivityType())==null) {
				act.setEarliestEndTime(0);
				act.setPriority(2);
				config.planCalcScore().getScoringParameters(PersonFixed_NAME).addActivityParams(act);
			}
		}
		RunUtils.createStrategies(config, PersonFixed_NAME, 0, 0.02, 0, 300);
		
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(10);
		config.strategy().setFractionOfIterationsToDisableInnovation(0.9);
		config.transitRouter().setSearchRadius(700);
		config.transitRouter().setMaxBeelineWalkConnectionDistance(300);

		TransitRouterFareDynamicImpl.distanceFactor = 0;
		TransitRouterFareDynamicImpl.aStarSetting = 'c';
		config.controler().setOutputDirectory("outputFullHKTMLTrialAugust/");
		//Updated: The new transit router is applied
		config.plans().setInputFile("cache/populationMTR.xml");
		config.network().setInputFile("onlyMTR/networkP2.xml");
		config.controler().setLinkToLinkRoutingEnabled(false);
		config.qsim().setUseLanes(false);
		config.qsim().setStartTime(0*3600);
		config.qsim().setEndTime(14*3600);
		config.travelTimeCalculator().setCalculateLinkToLinkTravelTimes(false);
		config.transit().setTransitScheduleFile("onlyMTR/transitScheduleP2.xml");
		config.transit().setVehiclesFile("onlyMTR/transitVehiclesP2.xml");
		config.removeModule("roadpricing");
		config.removeModule("signalsystems");
		
		config.global().setNumberOfThreads(26);
		// config.plansCalcRoute().setNetworkModes(Sets.newHashSet("car","taxi"));
		config.qsim().setVehiclesSource(VehiclesSource.defaultVehicle);
		
		config.parallelEventHandling().setNumberOfThreads(20);
		return config;
	}
	
	public static Population createMTRPopulation(String filePath) throws NumberFormatException, IOException {
		Config config = ConfigUtils.createConfig();
		Population population = PopulationUtils.createPopulation(config);
		
		TransitScheduleFactory tsF = new TransitScheduleFactoryImpl();
		TransitSchedule ts = tsF.createTransitSchedule();
		new TransitScheduleReaderV2(ts, new RouteFactories()).readFile("onlyMTR/transitScheduleP2.xml");
		
		Map<String, Coord> mtrStops = new HashMap<>();
		for(TransitStopFacility facility: ts.getFacilities().values()) {
			String idString = facility.getId().toString();
			if(idString.length() >= 9) {
				mtrStops.put(idString.substring(4, 7), facility.getCoord());
			}
		}
		
		String row = null;
		BufferedReader csvReader = new BufferedReader(new FileReader(filePath));
		boolean firstLine = true;
		while((row = csvReader.readLine()) != null){
			if(firstLine) { //We skip the first line
				firstLine = false;
				continue;
			}
			
			String[] data = row.split(",");
			if(data[1].contains("RAC") || data[2].contains("RAC")) {
				continue;
			}
			
			Coord originCoord = mtrStops.get(data[1]);
			if(originCoord == null) {
				originCoord = mtrStops.get(data[1].substring(0, 3));
			}
			Coord destCoord = mtrStops.get(data[2]);
			if(destCoord == null) {
				destCoord = mtrStops.get(data[2].substring(0, 3));
			}
			if(originCoord == null || destCoord == null) {
				throw new IllegalArgumentException("The coordinate is not found!"); 
			}
			
			List<Integer> hours = Lists.newArrayList(7,8,9);
			
			for(Integer hour: hours) {
				for(int i = 0; i < Double.parseDouble(data[hour-4]); i++) {
					double timeOffset = (Math.random() * 60 - 15) * 60; 
//					if(hour == 9) {
//						if(Math.random() < 0.5) {
//							timeOffset = (Math.random() * 40 - 20) * 60; // First 1/2: 0-20 mins
//						}else {
//							timeOffset = (Math.random() * 80 - 20 - 20) * 60; // Second 1/2: 20-60 mins
//						}
//					}
					//Actually create the person
					String personId = "p_" + data[1]+"_"+data[2]+"_"+hour + "_" + i;
					Person person = population.getFactory().createPerson(Id.createPersonId(personId));
					PopulationUtils.putSubpopulation(person, "trip_TCS");
					Plan plan = population.getFactory().createPlan();
		
					Activity home = population.getFactory().createActivityFromCoord("Home_StartOrEnd", originCoord);
					home.setEndTime(hour * 60 * 60 + timeOffset);
					plan.addActivity(home);
		
					Leg hinweg = population.getFactory().createLeg("pt");
					plan.addLeg(hinweg);
		
					Activity work = population.getFactory().createActivityFromCoord("Usual place of work_28800.0", destCoord);
					plan.addActivity(work);
		
					person.addPlan(plan);
					population.addPerson(person);
				}
			}
		}
		new PopulationWriter(population).write("cache/populationMTR.xml");
		csvReader.close();
		return population;
	}
}

