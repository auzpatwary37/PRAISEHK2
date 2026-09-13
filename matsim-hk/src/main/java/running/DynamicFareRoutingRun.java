package running;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationWriter;
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
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.counts.PtCountsModule;
import org.matsim.pt.router.TransitRouter;
import org.xml.sax.SAXException;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;

public class DynamicFareRoutingRun {
	private static void addPlanParameter(PlanCalcScoreConfigGroup config, String name, int typicalDuration,
			boolean scoreOrNot) {
		ActivityParams act = new ActivityParams(name);
		act.setTypicalDuration(typicalDuration);
		act.setScoringThisActivityAtAll(scoreOrNot);
		config.addActivityParams(act);
	}

	public static void main(String[] args)
			throws IOException, SAXException, ParserConfigurationException, InterruptedException {

		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "data/config.xml");
		config.controler().setLastIteration(75);
		config.strategy().setFractionOfIterationsToDisableInnovation(0.95);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setWriteEventsInterval(25);
		config.controler().setWritePlansInterval(25);
		config.removeModule("roadpricing");
		// config.ptCounts().setAlightCountsFileName("data/alightCount.xml");
		// config.ptCounts().setBoardCountsFileName("data/boarding.xml");
		// config.ptCounts().setOccupancyCountsFileName("data/occupancy.xml");

		TransitRouterFareDynamicImpl.distanceFactor = 0.3;
		config.controler().setOutputDirectory(
				"outputDynamicTransitFactor" + TransitRouterFareDynamicImpl.distanceFactor + "/");

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

		int i = 0;
		for (Iterator<?> it = scenario.getPopulation().getPersons().entrySet().iterator(); it.hasNext();) {
			Map.Entry<?, ?> entry = (Entry<?, ?>) it.next();
			if (i != 0) {
				it.remove();
			}
			i++;
			if (i > 10000) {
				i = 0;
			}
		}

		Controler controler = new Controler(scenario);

		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

		saxParser.parse("data/busFare.xml", busFareGetter);

		// Add the signal module to the controller
		Signals.configure(controler);
		controler.addOverridingModule(new DynamicRoutingModule(busFareGetter.get(), "fare/mtr_lines_fares.csv", "fare/first_class_fare.csv", 
				"fare/GMB.csv", "fare/light_rail_fares.csv", "fare/busFareGTFS.json", "fare/ferryFareGTFS.json"));

		controler.getConfig().controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		controler.run();
	}
}
