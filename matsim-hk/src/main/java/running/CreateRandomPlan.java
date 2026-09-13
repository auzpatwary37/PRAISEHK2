package running;

import java.io.IOException;

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
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.signals.builder.Signals;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.contrib.roadpricing.RoadPricingModule;
import org.matsim.contrib.roadpricing.RoadPricingScheme;
import org.matsim.contrib.roadpricing.RoadPricingSchemeImpl;
import org.matsim.contrib.roadpricing.RoadPricingUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;
import org.xml.sax.SAXException;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
/**
 * This class creates a random plan, with the vehicles
 * @author eleead 
 */
public class CreateRandomPlan {
	public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "data/config.xml");
		config.controler().setLastIteration(10);

		ActivityParams home = new ActivityParams("home");
		home.setTypicalDuration(16 * 60 * 60);
		config.planCalcScore().addActivityParams(home);
		ActivityParams work = new ActivityParams("work");
		work.setTypicalDuration(8 * 60 * 60);
		config.planCalcScore().addActivityParams(work);

		// config.removeModule("roadpricing");
		config.removeModule("emissions");

		config.planCalcScore().setWriteExperiencedPlans(true);
		config.planCalcScore().setPerforming_utils_hr(800);

		config.global().setNumberOfThreads(1);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());

		// Create vehicles
		Vehicles vehicles = scenario.getVehicles();
		VehiclesFactory vf = vehicles.getFactory();
		VehicleType vt = vf.createVehicleType(Id.create("car", VehicleType.class));
		vt.setPcuEquivalents(1);
		vt.setMaximumVelocity(50);
		vt.setDescription("BEGIN_EMISSIONSPASSENGER_CAR;average;average;averageEND_EMISSIONS");
		vehicles.addVehicleType(vt);

		// new MatsimNetworkReader(scenario.getNetwork()).readFile("data/network.xml");
		int segments = 1;
		int populations = 100;
		for (int i = 0; i < segments; i++) {
			fillScenario(scenario, vt, 30000, 43000, 12000, 17000, populations / segments,
					(i - segments / 2) * 10 * 60);
		}

		new PopulationWriter(scenario.getPopulation(), scenario.getNetwork()).write("output/plan.xml");
		new VehicleWriterV1(scenario.getVehicles()).writeFile("output/vehicles.xml");

		Controler controler = new Controler(scenario);
		RoadPricingScheme scheme = createRoadPricingScheme(scenario);

		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

		saxParser.parse("data/busFare.xml", busFareGetter);

		// Add the signal module to the controller
		Signals.configure(controler);
		controler.addOverridingModule(new RoadPricingModule(scheme));
		controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", "fare/first_class_fare.csv", 
				"fare/GMB.csv", "fare/light_rail_fares.csv", "fare/busFareGTFS.json", "fare/ferryFareGTFS.json"));
		
		controler.getConfig().controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		controler.run();

		/*
		 * config.controler().setLastIteration(0);
		 * 
		 * //Base case Controler controler1 = new Controler(scenario);
		 * controler1.addOverridingModule(new SignalsModule());
		 * controler1.addOverridingModule(new RoadPricingModule());
		 * controler1.getConfig().controler().setOverwriteFileSetting(
		 * OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		 * controler1.run(); double scoreBasecase =
		 * controler1.getScenario().getPopulation().getPersons().get(Id.create("p_1_0",
		 * Person.class)).getPlans().get(0).getScore();
		 * 
		 * //Toll case Controler controler2 = new Controler(scenario);
		 * controler2.addOverridingModule(new SignalsModule());
		 * controler2.addOverridingModule(new RoadPricingModule());
		 * controler2.addOverridingModule(new FareRoutingModule());
		 * controler2.getConfig().controler().setOverwriteFileSetting(
		 * OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		 * controler2.run(); double scoreFarecase =
		 * controler1.getScenario().getPopulation().getPersons().get(Id.create("p_1_0",
		 * Person.class)).getPlans().get(0).getScore();
		 * 
		 * System.out.println(scoreBasecase+" "+scoreFarecase);
		 */
	}

	private static Population fillScenario(Scenario scenario, VehicleType vt, double x_min, double x_max, double y_min,
			double y_max, int NoAgents, int offset) {
		Population population = scenario.getPopulation();
		double x_mid = (x_min + x_max) / 2;
		double y_mid = (y_min + y_max) / 2;

		int grid_size = (int) Math.sqrt(NoAgents);

		for (int i = 0; i < NoAgents; i++) {
			Coord coord = new Coord((double) (x_min + (i % grid_size - grid_size / 2) * (x_max - x_min) / grid_size),
					(double) (y_min + (i % grid_size - grid_size / 2) * (y_max - y_min) / grid_size));
			Coord coordWork = new Coord(
					(double) (x_max - (i % grid_size - grid_size / 2) * (x_max - x_min) / grid_size),
					(double) (y_max - (i % grid_size - grid_size / 2) * (y_max - y_min) / grid_size));
			createOnePerson(scenario, population, vt, i, coord, coordWork, offset);
		}
		return population;
	}

	private static void createOnePerson(Scenario scenario, Population population, VehicleType vt, int i, Coord coord,
			Coord coordWork, int time_offset) {
		String personId = "p_" + i + "_" + time_offset / 60;
		Person person = population.getFactory().createPerson(Id.createPersonId(personId)); // Create person

		// Create and add vehicle for this person
		Vehicles vehicles = scenario.getVehicles();
		VehiclesFactory vf = vehicles.getFactory();
		Vehicle v = vf.createVehicle(Id.createVehicleId(personId), vt);
		vehicles.addVehicle(v);

		Plan plan = population.getFactory().createPlan();

		Activity home = population.getFactory().createActivityFromCoord("home", coord);
		home.setEndTime(9 * 60 * 60 + time_offset);
		plan.addActivity(home);

		Leg hinweg;
		if (i % 2 == 0) {
			hinweg = population.getFactory().createLeg("car");
		} else {
			hinweg = population.getFactory().createLeg("pt");
		}
		plan.addLeg(hinweg);

		Activity work = population.getFactory().createActivityFromCoord("work", coordWork);
		// work.setStartTime(9*60*60+time_offset);
		work.setEndTime(17 * 60 * 60 + time_offset);
		plan.addActivity(work);

		Leg rueckweg;
		if (i % 2 == 0) {
			rueckweg = population.getFactory().createLeg("car");
		} else {
			rueckweg = population.getFactory().createLeg("pt");
		}
		plan.addLeg(rueckweg);

		Activity home2 = population.getFactory().createActivityFromCoord("home", coord);
		plan.addActivity(home2);

		person.addPlan(plan);
		population.addPerson(person);
	}

	/**
	 * This is a helper function to create the road pricing scheme.
	 * 
	 * @return
	 */
	private static RoadPricingScheme createRoadPricingScheme(Scenario scenario) {
		RoadPricingSchemeImpl scheme = RoadPricingUtils.createAndRegisterMutableScheme(scenario);
		RoadPricingUtils.addLink(scheme, Id.createLinkId("101542_303010"));
		RoadPricingUtils.addLinkSpecificCost(scheme, Id.createLinkId("101542_303010"), 0, 30 * 60 * 60, 8);
		RoadPricingUtils.addLink(scheme, Id.createLinkId("301010_101542"));
		RoadPricingUtils.addLinkSpecificCost(scheme, Id.createLinkId("301010_101542"), 0, 30 * 60 * 60, 8);
		RoadPricingUtils.setType(scheme, RoadPricingScheme.TOLL_TYPE_LINK);// Possibly change to cordon toll

		// For cordon, every link have to be inserted into the scheme
		return scheme;
	}

}
