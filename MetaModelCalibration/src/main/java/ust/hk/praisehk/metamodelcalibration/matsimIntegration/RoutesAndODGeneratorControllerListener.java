package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.LinkedHashMap;
import java.util.Map;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.ShutdownListener;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;


public class RoutesAndODGeneratorControllerListener implements ShutdownListener{
	
	@Inject
	private @Named("fileLoc") String fileLoc;
	@Inject
	private @Named("currentParam") paramContainer currentParam;
	@Inject
	private AnalyticalModel sueAssignment;
	@Inject
	private final Map<String, FareCalculator> farecalc;
	
	@Inject
	public RoutesAndODGeneratorControllerListener(Map<String, FareCalculator> farecalc) {
		this.farecalc=farecalc;
	}
	
	@Override
	public void notifyShutdown(ShutdownEvent event) {
		new PopulationWriter(event.getServices().getScenario().getPopulation()).write(fileLoc+"population_"+MeasurementsStorage.genearteParamId(this.currentParam.getParam())+".xml");
		this.sueAssignment.generateRoutesAndOD(event.getServices().getScenario().getPopulation(),
				event.getServices().getScenario().getNetwork(),
				event.getServices().getScenario().getTransitSchedule(),
				event.getServices().getScenario(), this.farecalc);
	}
}
