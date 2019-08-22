package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.controler.listener.StartupListener;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitCalculatorsWithFare.TransitFareHandler;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;






public class AnaModelControlerListener implements StartupListener,BeforeMobsimListener, AfterMobsimListener,IterationEndsListener, ShutdownListener{
	private boolean generateOD=true;
	private Scenario scenario;
	@Inject
	private AnalyticalModel SueAssignment;
	@Inject
	private @Named("CalibrationCounts") Measurements calibrationMeasurements;
	private String fileLoc;
	@Inject
	private LinkCountEventHandler pcuVolumeCounter;
	@Inject
	private AverageOccupancyEventHandler occupancyCalculator;
	@Inject
	private SmartCardEntryAndExitEventHandler sc;
	@Inject
	private TravelTimeEventHandler travelTimeCalculator;
	
	private MeasurementsStorage storage;
	@Inject
	private @Named("CurrentParam") paramContainer currentParam;
	
	
	private @Named("Output Measurements") Measurements outputMeasurements;
	
	private int maxIter;
	private final Map<String, FareCalculator> farecalc;
	private int AverageCountOverNoOfIteration=5;
	private boolean shouldAverageOverIteration=true;
	private Map<Id<Measurement>, Map<String, Double>> counts=null;
	
	@Inject
	public AnaModelControlerListener(Scenario scenario,AnalyticalModel sueAssignment, 
			Map<String,FareCalculator> farecalc,@Named("fileLoc") String fileLoc,@Named("generateRoutesAndOD") boolean generateRoutesAndOD, MeasurementsStorage storage){
		this.SueAssignment=sueAssignment;
		this.farecalc=farecalc;
		this.scenario=scenario;
		this.fileLoc=fileLoc;
		this.generateOD=generateRoutesAndOD;
		this.storage=storage;
	}
	
	@Inject
	private EventsManager eventsManager;
	
	
	
	@Override
	public void notifyStartup(StartupEvent event) {
		this.maxIter=event.getServices().getConfig().controler().getLastIteration();
		}
	
	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		this.pcuVolumeCounter.resetLinkCount();
		this.occupancyCalculator.reset();
		this.travelTimeCalculator.reset();
		this.sc.reset(event.getIteration());
	}
	

	public void notifyAfterMobsim(AfterMobsimEvent event) {
		if(this.shouldAverageOverIteration) {
			int counter=event.getIteration();
			if(counter>this.maxIter-5) {
				this.pcuVolumeCounter.geenerateLinkCounts();
				this.occupancyCalculator.getOutputMeasurements();
				this.travelTimeCalculator.getUpdatedMeasurements();
				//this.fareHandler.getUpdatedMeasurements();
				if(this.counts==null) {
					counts=new HashMap<>();
					for(Measurement m:this.outputMeasurements.getMeasurements().values()) {
						counts.put(m.getId(), new HashMap<>());
						for(String timeId:m.getVolumes().keySet()) {
							counts.get(m.getId()).put(timeId, m.getVolumes().get(timeId));
						}
					}
				}else {
					//Map<String,Map<Id<Link>,Double>> newcounts=this.pcuVolumeCounter.geenerateLinkCounts();
					for(Measurement m:this.outputMeasurements.getMeasurements().values()) {
						for(String timeId:m.getVolumes().keySet()) {
							counts.get(m.getId()).put(timeId, counts.get(m.getId()).get(timeId)+m.getVolumes().get(timeId));
						}
					}
				}
			}
			if(counter==this.maxIter) {
				for(Measurement m:this.outputMeasurements.getMeasurements().values()) {
					for(String timeId:m.getVolumes().keySet()) {
						m.addVolume(timeId, counts.get(m.getId()).get(timeId)/this.AverageCountOverNoOfIteration);
					}
				}
				//new MeasurementsWriter(m).write();
				this.storage.storeMeasurements(this.currentParam.getParam(), this.outputMeasurements);
			}
		}else {
		int counter=event.getIteration();
			if(counter==this.maxIter) {
				this.pcuVolumeCounter.geenerateLinkCounts();
				this.occupancyCalculator.getOutputMeasurements();
				this.travelTimeCalculator.getUpdatedMeasurements();
				this.outputMeasurements.writeCSVMeasurements(fileLoc);
				this.storage.storeMeasurements(this.currentParam.getParam(), this.outputMeasurements);
			}
		}
		
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		
		
	}

	

	@Override
	public void notifyShutdown(ShutdownEvent event) {
		if(this.generateOD) {
		this.SueAssignment.generateRoutesAndOD(event.getServices().getScenario().getPopulation(),
				event.getServices().getScenario().getNetwork(),
				event.getServices().getScenario().getTransitSchedule(),
				event.getServices().getScenario(), this.farecalc);
		}	

		
	}

	public int getAverageCountOverNoOfIteration() {
		return AverageCountOverNoOfIteration;
	}

	public void setAverageCountOverNoOfIteration(int averageCountOverNoOfIteration) {
		AverageCountOverNoOfIteration = averageCountOverNoOfIteration;
	}

	public boolean isShouldAverageOverIteration() {
		return shouldAverageOverIteration;
	}

	public void setShouldAverageOverIteration(boolean shouldAverageOverIteration) {
		this.shouldAverageOverIteration = shouldAverageOverIteration;
	}
	

	
	
}