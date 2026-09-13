package emissionHK;

import java.io.IOException;
import java.net.URL;

import javax.inject.Inject;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.ControlerListenerManager;

public class PRAISEEmissionModule extends AbstractModule{
	
	private Scenario scenario;
	private final EmissionsConfigGroup ecg;
//	private final EventsManager em;
	
	private static String feetColdEmissionFactorsFile;
	private static String feetWarmEmissionFactorsFile;
	
	private ColdEmissionHandler coldEmissionHandler;
	private WarmEmissionHandler warmEmissionHandler;
	
	private int lastIteration;
	
	@Inject
	public PRAISEEmissionModule(Scenario scenario, EventsManager em) throws IOException {
		this.scenario = scenario;
		this.ecg = (EmissionsConfigGroup) scenario.getConfig().getModules().get(EmissionsConfigGroup.GROUP_NAME);
//		this.em = em;
		getInputFiles();
		
		coldEmissionHandler = new ColdEmissionHandler(scenario.getVehicles(), scenario.getTransitVehicles(), 
				em, feetColdEmissionFactorsFile);
		warmEmissionHandler = new WarmEmissionHandler(scenario.getVehicles(), scenario.getTransitVehicles(), scenario.getNetwork(),
				em, feetWarmEmissionFactorsFile);
		this.lastIteration = scenario.getConfig().controler().getLastIteration();
		//em.addHandler(coldEmissionHandler);
		//em.addHandler(warmEmissionHandler);
	}
	
	//Get the input file path from the config.
	private void getInputFiles() {
		URL context = scenario.getConfig().getContext();

		feetWarmEmissionFactorsFile = ecg.getAverageWarmEmissionFactorsFileURL(context).getFile();
		feetColdEmissionFactorsFile = ecg.getAverageColdEmissionFactorsFileURL(context).getFile();
	}

	@Override
	public void install(){
		//addEventHandlerBinding().toInstance(coldEmissionHandler); // Add the cold emission handler.
		//addEventHandlerBinding().toInstance(warmEmissionHandler); //Add the hot emission handler.
		addControlerListenerBinding().toInstance(new PRAISEEmissionControlerListener(warmEmissionHandler, 
				coldEmissionHandler, lastIteration));
		//throw new RuntimeException();
	}

}
