package running;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.contrib.roadpricing.RoadPricingModule;
import org.matsim.contrib.roadpricing.RoadPricingScheme;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.QSimConfigGroup.VehiclesSource;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.config.TransitConfigGroup.TransitRoutingAlgorithmType;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.VehicleType;
import org.xml.sax.SAXException;

import com.google.common.collect.Sets;

import createPTGTFS.FareCalculatorPTGTFS;
import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.LRFareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import networkFromSaturn.CreateNetworkUtils;

public class RunFullHK {

	private static final String PersonChangeWithCar_NAME = "person_TCSwithCar";
	private static final String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
	private static final String PersonFixed_NAME = "trip_TCS";
	private static final String GVChange_NAME = "person_GV";
	private static final String GVFixed_NAME = "trip_GV";
	
	private static void addStratgies(Config config) {
		RunUtils.createStrategies(config, PersonChangeWithCar_NAME, 0, 0.005, 0.005, 0);
		RunUtils.createStrategies(config, PersonChangeWithoutCar_NAME, 0, 0.005, 0.01, 0);
		
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta.toString(), 
				PersonChangeWithCar_NAME, 9700, 50);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), 
				PersonChangeWithCar_NAME, 100, 30);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), 
				PersonChangeWithCar_NAME, 150, 50);

		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta.toString(), 
				PersonChangeWithoutCar_NAME, 9700, 50);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), 
				PersonChangeWithoutCar_NAME, 100, 30);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), 
				PersonChangeWithoutCar_NAME, 150, 50);
	
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), PersonFixed_NAME, 
				0.025, 100);
		RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), GVFixed_NAME, 
				0.025, 100);
		
		RunUtils.createStrategies(config, PersonFixed_NAME, 0, 0.01, 0, 300);
		RunUtils.createStrategies(config, GVChange_NAME, 0, 0.01, 0, 0);
		RunUtils.createStrategies(config, GVFixed_NAME, 0, 0.01, 0, 300);
	}
	
	private static Scenario createScenario() {
		Config config = ConfigUtils.loadConfig("config_clean.xml", new EmissionsConfigGroup());
		
		Config configGV = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(configGV, "data/config_Ashraf.xml");
		for (ActivityParams act: configGV.planCalcScore().getActivityParams()) {
			if(act.getActivityType().contains("Usual place of work")) {
				act.setMinimalDuration(3600);
			}else {
				act.setEarliestEndTime(0);
				act.setLatestStartTime(100000);
				act.setOpeningTime(0);
				act.setClosingTime(100000);
			}
			if(config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getActivityParams(act.getActivityType())==null) {
				act.setEarliestEndTime(0);
				act.setPriority(2);
				config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(PersonFixed_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(GVChange_NAME).addActivityParams(act);
				config.planCalcScore().getScoringParameters(GVFixed_NAME).addActivityParams(act);
			}
		}
		
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(100);
		config.strategy().setFractionOfIterationsToDisableInnovation(0.9);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.overwriteExistingFiles);
		config.controler().setWriteEventsInterval(25);
		config.controler().setWritePlansInterval(25);
		config.planCalcScore().setWriteExperiencedPlans(false);
		config.transitRouter().setSearchRadius(700);
		config.transitRouter().setMaxBeelineWalkConnectionDistance(300);
		//config.removeModule("roadpricing");
		//config.removeModule("emissions");

		TransitRouterFareDynamicImpl.distanceFactor = 0.013;
		TransitRouterFareDynamicImpl.aStarSetting = 'c';
		config.controler().setOutputDirectory(
				"outputFullHKTrimed10pct7/");
		//Updated: The new transit router is applied
		//config.plans().setInputFile("outputFullHKTollAdjustmentOrigin15/ITERS/it.300/300.plans.xml.gz");
		config.plans().setInputFile("data/populationHK.xml");
		//config.plans().setInputPersonAttributeFile("data/personAttributesHKI.xml");
		//config.plans().setSubpopulationAttributeName("SUBPOP_ATTRIB_NAME"); /* This is the default anyway. */
		//config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);
		//config.vehicles().setVehiclesFile("data/output_vehicles.xml.gz");
		config.qsim().setNumberOfThreads(8);
		config.qsim().setStartTime(0*3600);
		config.qsim().setEndTime(14*3600);
		config.qsim().setStorageCapFactor(0.16);
		config.qsim().setFlowCapFactor(0.12);
		config.transit().setRoutingAlgorithmType(TransitRoutingAlgorithmType.DijkstraBased);
//		config.transitRouter().setMaxBeelineWalkConnectionDistance(150);
		
		config.global().setNumberOfThreads(26);
		config.parallelEventHandling().setNumberOfThreads(15);
		config.parallelEventHandling().setEstimatedNumberOfEvents((long) 1000000000);
		config.plansCalcRoute().setNetworkModes(Sets.newHashSet("car","taxi"));
		config.qsim().setVehiclesSource(VehiclesSource.defaultVehicle);
		
		config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getModes().get("car")
				.setMarginalUtilityOfTraveling(-175);
		config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getModes().get("pt")
		.setMarginalUtilityOfTraveling(-175);
		config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getModes().get("walk")
		.setMarginalUtilityOfTraveling(-175);
		config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).getModes().get("car")
		.setMarginalUtilityOfTraveling(-175);
		config.planCalcScore().getScoringParameters(PersonFixed_NAME).getModes().get("car")
		.setMarginalUtilityOfTraveling(-175);
		config.planCalcScore().getScoringParameters(GVChange_NAME).getModes().get("car")
		.setMarginalUtilityOfTraveling(-100);
		config.planCalcScore().getScoringParameters(GVFixed_NAME).getModes().get("car")
		.setMarginalUtilityOfTraveling(-100);
		//general Run Configuration
		//config.counts().setInputFile("data/ATCCountsPeakHourLink.xml");
		addStratgies(config);
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
		for(VehicleType vt: scenario.getVehicles().getVehicleTypes().values()) {
			if(vt.getPcuEquivalents()==1 && !vt.getId().toString().contains("GV")) {
				vt.setPcuEquivalents(0.75);
			}
		}
		
		scenario.getPopulation().getPersons().entrySet().removeIf(e -> 
		(e.getKey().toString().contains("23044.0_1.0_2.") && Math.random() < 0.5)||
		(e.getKey().toString().contains("27058.0_1.0_1.") && Math.random() < 0.5) );
		
		CreateNetworkUtils.setLinkCapacityWithLane(scenario.getNetwork(), scenario.getLanes(), "501226_101375", 2000.);
		CreateNetworkUtils.setLinkCapacityWithLane(scenario.getNetwork(), scenario.getLanes(), "101375_501226", 2000.);
		CreateNetworkUtils.setLinkCapacityWithLane(scenario.getNetwork(), scenario.getLanes(), "506320_506520", 2000.);
		CreateNetworkUtils.setLinkCapacityWithLane(scenario.getNetwork(), scenario.getLanes(), "201155_201158", 2000.);
		CreateNetworkUtils.setLinkCapacityWithLane(scenario.getNetwork(), scenario.getLanes(), "404006_404008", 3000.);
		CreateNetworkUtils.setLinkCapacityWithLane(scenario.getNetwork(), scenario.getLanes(), "101640_101642", 3000.);
		
		RunUtils.scaleDownPopulation(scenario.getPopulation(), 0.1);
		RunUtils.scaleDownPt(scenario.getTransitVehicles(), 0.1);
		
		RunUtils.filterPopulationByTime(scenario.getPopulation(), 6*3600, 11.5*3600);
		
		return scenario;
	}
	
	private static Map<String,FareCalculator> createFareCalculatorMap(TransitSchedule ts) throws IOException, 
		SAXException, ParserConfigurationException{
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(ts);
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		saxParser.parse("data/busFare.xml", busFareGetter);
		
		Map<String,FareCalculator>fareCalculator= new HashMap<>();
		fareCalculator.put("bus", FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/busFareGTFS.json"));
		fareCalculator.put("minibus",busFareGetter.get());
		fareCalculator.put("tram", new UniformFareCalculator(2.6));
		fareCalculator.put("ferry", FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/ferryFareGTFS.json"));
		fareCalculator.put("train",new MTRFareCalculator("fare/mtr_lines_fares.csv", null, ts));
		fareCalculator.put("LR",new LRFareCalculator("fare/light_rail_fares.csv"));
		return fareCalculator;
	}
	
	public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException, InterruptedException {
		ArrayList<String>subPopNames=new ArrayList<>();
		subPopNames.add(PersonChangeWithCar_NAME);
		subPopNames.add(PersonChangeWithoutCar_NAME);
		subPopNames.add(PersonFixed_NAME);
		subPopNames.add(GVChange_NAME);
		subPopNames.add(GVFixed_NAME);
		
		Scenario scenario = createScenario();
		//Map<String, FareCalculator> fareCalculator = createFareCalculatorMap(scenario.getTransitSchedule());
		
//		Map<String,Tuple<Double,Double>> timeBean=new HashMap<>();
//		for(int i=3;i<=29;i++) {
//			timeBean.put(Integer.toString(i), new Tuple<Double,Double>((i-1)*3600.,i*3600.));
//		}
		
//		TransferDiscountCalculator tdc = new BusMinibusTransferDiscount("fare/GMB.csv");
		//RunUtils.scaleDownPopulation(scenario.getPopulation(), 0.75);
		
//		Network ctsNet = NetworkUtils.createNetwork();
//		new MatsimNetworkReader(ctsNet).readFile("input/network_TD.xml");
//		AssignLinkToPlanActivity.defineMatchingTablePath("matching/");
//		AssignLinkToPlanActivity.run(scenario, ctsNet, 24, true);
//		PopulationWriter popWriter=new PopulationWriter(scenario.getPopulation());
//		popWriter.write("output/populationHKI.xml");
		
//		CNLSUEModelSubPop anaModel = new CNLSUEModelSubPop(scenario.getConfig(), ParamReader.getMoreCoarseTimeBean(), subPopNames);
//		//new ModalToMATSim(anaModel, scenario);
//		anaModel.getAnalyticalModelInternalParams().put(CNLSUEModel.BPRalphaName, 1.);
//		anaModel.getAnalyticalModelInternalParams().put(CNLSUEModel.LinkMiuName, 0.001);
//		double transitRatio = 0.65;
//		anaModel.generateRoutesAndODWithoutRoute(scenario.getPopulation(), scenario.getNetwork(), scenario.getLanes(), scenario.getTransitSchedule(), 
//				scenario, fareCalculator, tdc, transitRatio, false);
//		anaModel.generateMATSimRoutes(transitRatio, 60, 10);
//		new ModalToMATSim(anaModel, scenario).assignRoutesToMATSimPopulation(scenario.getPopulation(), transitRatio, true); //No change on the population time for now.
////		System.out.println("wait!!!!");
//		//RunUtils.scaleDownPopulation(scenario.getPopulation(), 01236987.01);
//		anaModel = null;
		
		//Start the run
		Controler controler = new Controler(scenario);
		Signals.configure(controler);

		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

		saxParser.parse("data/busFare.xml", busFareGetter);
		controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", "fare/first_class_fare.csv", 
				"fare/transitDiscount.json", "fare/light_rail_fares.csv", "fare/busFareGTFS.json", "fare/ferryFareGTFS.json"));
		//controler.addOverridingModule(new PRAISEEmissionModule(scenario, controler.getEvents()));
		RoadPricingScheme scheme = RunUtils.createDefaultRoadPricingScheme(scenario, false);
		controler.addOverridingModule(new RoadPricingModule(scheme));
		controler.run();
	}
}
