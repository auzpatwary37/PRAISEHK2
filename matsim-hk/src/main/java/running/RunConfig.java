package running;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.mobsim.jdeqsim.Vehicle;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.contrib.roadpricing.RoadPricingScheme;
import org.matsim.contrib.roadpricing.RoadPricingSchemeImpl;
import org.matsim.contrib.roadpricing.RoadPricingUtils;
import org.xml.sax.SAXException;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import emissionHK.PRAISEEmissionModule;

public class RunConfig {
	public static int changeCount = 0;
	
	private static void addPlanParameter(PlanCalcScoreConfigGroup config, String name, int typicalDuration,
			boolean scoreOrNot) {
		ActivityParams act = new ActivityParams(name);
		act.setTypicalDuration(typicalDuration);
		act.setScoringThisActivityAtAll(scoreOrNot);
		config.addActivityParams(act);
	}
	
	/**
	 * This function aims to change all person with Id "\\d+.0_\\d+.0_\\d+" (person_TCS) to pt.
	 * @param scenario
	 */
	public static void changeAllModeToPt(Scenario scenario) {
		changeCount = 0;
		 for(Iterator<?> it = scenario.getPopulation().getPersons().entrySet().iterator(); it.hasNext(); ){
			 Map.Entry<?, ?> entry = (Entry<?, ?>) it.next();
			 Person person = (Person) entry.getValue();
			 //if(person.getId().toString().matches("\\d+.0_\\d+.0_\\d+")) {
				 for(PlanElement pe: person.getSelectedPlan().getPlanElements()){
					 if(pe instanceof Leg){
						 if( !((Leg) pe).getMode().equals("pt")){
							 ((Leg) pe).setMode("pt");
							 changeCount++;
						 }
					 }
				 }
			 //}
		}
	}

	public static void main(String[] args)
			throws IOException, SAXException, ParserConfigurationException, InterruptedException {
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "data/config.xml");
		config.controler().setLastIteration(100);
		config.strategy().setFractionOfIterationsToDisableInnovation(0.85);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		config.removeModule("roadpricing");
        EmissionsConfigGroup ecg = new EmissionsConfigGroup() ;
        config.addModule(ecg);
        
        TransitRouterFareDynamicImpl.distanceFactor = 0.034;

		config.plans().setInputFile("data/population.xml");
		config.vehicles().setVehiclesFile("data/vehicles.xml");
		config.qsim().setUsePersonIdForMissingVehicleId(true);
		config.qsim().setNumberOfThreads(4);
		config.global().setNumberOfThreads(7);

		addPlanParameter(config.planCalcScore(), "home", 16 * 60 * 60, true);
		addPlanParameter(config.planCalcScore(), "work", 8 * 60 * 60, true);
		addPlanParameter(config.planCalcScore(), "leisure", 1 * 60 * 60, true);
		addPlanParameter(config.planCalcScore(), "education", 4 * 60 * 60, true);
		addPlanParameter(config.planCalcScore(), "nonwork", 4 * 60 * 60, true);
		addPlanParameter(config.planCalcScore(), "shopping", 4 * 60 * 60, true);
		addPlanParameter(config.planCalcScore(), "financial", 6 * 60 * 60, true);
		addPlanParameter(config.planCalcScore(), "crossborder", 18 * 60 * 60, true);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
		changeAllModeToPt(scenario);
		int legCount = 0;
		int populationCount = 0;
		int i = 0;
		for (Iterator<?> it = scenario.getPopulation().getPersons().entrySet().iterator(); it.hasNext();) {
			populationCount++;
			Map.Entry<?, ?> entry = (Entry<?, ?>) it.next();
			Person person = (Person) entry.getValue();
			for(PlanElement pe: person.getSelectedPlan().getPlanElements()){
				if(pe instanceof Leg){
					legCount++;
				}
			}
			if (i != 0) {
				it.remove();
			}
			i++;
			if (i > 400) {
				i = 0;
			}
		}

		Controler controler = new Controler(scenario);
		// RoadPricingScheme scheme = createRoadPricingScheme(50.0);

		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

		saxParser.parse("data/busFare.xml", busFareGetter);

		// Add the signal module to the controller
		Signals.configure(controler);
		// controler.addOverridingModule(new RoadPricingModule(scheme));
		controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", "fare/first_class_fare.csv", 
				"fare/GMB.csv", "fare/light_rail_fares.csv", "fare/busFareGTFS.json", "fare/ferryFareGTFS.json"));
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bind(PRAISEEmissionModule.class).asEagerSingleton();
			}
		});
		controler.getConfig().controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		//controler.run();
		System.out.println(changeCount);
		System.out.println("Leg count is :"+legCount);
		System.out.println("Population count is :"+populationCount);
	}

	private static void addLinkAndCost(RoadPricingSchemeImpl scheme, Id<Link> linkId, double cost) {
		RoadPricingUtils.addLink(scheme, linkId);
		RoadPricingUtils.addLinkSpecificCost(scheme, linkId, 0, 30 * 60 * 60, cost);
	}

	private static void addLinkAndCostWithTime(RoadPricingSchemeImpl scheme, Id<Link> linkId, double cost,
			double am_start_hour, double am_end_hour, double pm_start_hour, double pm_end_hour) {
		if (!scheme.getTolledLinkIds().contains(linkId)) {
			RoadPricingUtils.addLink(scheme, linkId);
		}
		RoadPricingUtils.addLinkSpecificCost(scheme, linkId, am_start_hour * 3600, am_end_hour * 3600, cost);
		RoadPricingUtils.addLinkSpecificCost(scheme, linkId, (pm_start_hour + 12) * 3600, (pm_end_hour + 12) * 3600, cost);
	}

	/**
	 * This is a helper function to create the road pricing scheme.
	 * 
	 * @return
	 */
	private static RoadPricingScheme createRoadPricingScheme(Scenario scenario, double price) {
		RoadPricingSchemeImpl scheme = RoadPricingUtils.createAndRegisterMutableScheme(scenario);
		RoadPricingUtils.setType(scheme, RoadPricingScheme.TOLL_TYPE_LINK);// Possibly change to cordon toll
		addLinkAndCost(scheme, Id.createLinkId("101542_303010"), 8);
		addLinkAndCost(scheme, Id.createLinkId("303010_101542"), 8);
		addLinkAndCost(scheme, Id.createLinkId("WHCNorth"), 65);
		addLinkAndCost(scheme, Id.createLinkId("WHCSouth"), 65);
		addLinkAndCost(scheme, Id.createLinkId("CHTNorth"), 20);
		addLinkAndCost(scheme, Id.createLinkId("CHTSouth"), 20);
		addLinkAndCost(scheme, Id.createLinkId("EHCNorth"), 25);
		addLinkAndCost(scheme, Id.createLinkId("EHCSouth"), 25);

		// Road pricing for crossing Canal Road Flyover
		addLinkAndCostWithTime(scheme, Id.createLinkId("105128_105129"), price, 7, 9, 4, 7); // Glocester
		addLinkAndCostWithTime(scheme, Id.createLinkId("101399_101406"), price, 7, 9, 4, 7); // Inner Glocester
		addLinkAndCostWithTime(scheme, Id.createLinkId("101402_101403"), price, 7, 9, 4, 7); // Locke
		addLinkAndCostWithTime(scheme, Id.createLinkId("101322_101321"), price, 7, 9, 4, 7); // Hennessy
		addLinkAndCostWithTime(scheme, Id.createLinkId("101681_101390"), price, 7, 9, 4, 7); // Canal Road U-turn
		addLinkAndCostWithTime(scheme, Id.createLinkId("101329_101389"), price, 7, 9, 4, 7); // Leighton road
		addLinkAndCostWithTime(scheme, Id.createLinkId("101329_101330"), price, 7, 9, 4, 7); // Leighton road
		addLinkAndCostWithTime(scheme, Id.createLinkId("101384_101332"), price, 7, 9, 4, 7); // Queen's Road East

		// Road pricing for crossing the line from HSBC
		addLinkAndCostWithTime(scheme, Id.createLinkId("109990_109992"), price, 7, 9, 4, 7); // Lung Wo Road
		addLinkAndCostWithTime(scheme, Id.createLinkId("101194_101195"), price, 7, 9, 4, 7); // Connaught Road
		addLinkAndCostWithTime(scheme, Id.createLinkId("101221_101222"), price, 7, 9, 4, 7); // Connaught Road
		addLinkAndCostWithTime(scheme, Id.createLinkId("101298_101614"), price, 7, 9, 4, 7); // Chater Road
		addLinkAndCostWithTime(scheme, Id.createLinkId("101303_101744"), price, 7, 9, 4, 7); // Des Voeux Road Central

		return scheme;
	}
}
