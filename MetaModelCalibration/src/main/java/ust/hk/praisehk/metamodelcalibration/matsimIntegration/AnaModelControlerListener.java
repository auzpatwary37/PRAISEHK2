package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
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
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsWriter;






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
	private MeasurementsStorage storage;
	@Inject
	private @Named("CurrentParam") paramContainer currentParam;
	
	private int maxIter;
	private final Map<String, FareCalculator> farecalc;
	private int AverageCountOverNoOfIteration=5;
	private boolean shouldAverageOverIteration=true;
	private Map<String, Map<Id<Link>, Double>> counts=null;
	
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
		this.eventsManager.addHandler(pcuVolumeCounter);
		this.maxIter=event.getServices().getConfig().controler().getLastIteration();
		}
	
	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		this.pcuVolumeCounter.resetLinkCount();
	}
	

	public void notifyAfterMobsim(AfterMobsimEvent event) {
		if(this.shouldAverageOverIteration) {
			int counter=event.getIteration();
			if(counter>this.maxIter-5) {
				if(this.counts==null) {
					counts=new HashMap<>(this.pcuVolumeCounter.geenerateLinkCounts());
				}else {
					Map<String,Map<Id<Link>,Double>> newcounts=this.pcuVolumeCounter.geenerateLinkCounts();
					for(String s:this.counts.keySet()) {
						for(Id<Link> lId:this.counts.get(s).keySet()) {
							counts.get(s).put(lId, counts.get(s).get(lId)+newcounts.get(s).get(lId));
						}
					}
				}
			}
			if(counter==this.maxIter) {
				for(String s:this.counts.keySet()) {
					for(Id<Link> lId:this.counts.get(s).keySet()) {
						counts.get(s).put(lId, counts.get(s).get(lId)/this.AverageCountOverNoOfIteration);
					}
				}
				Measurements m=storage.getCalibrationMeasurements().clone();
				//m.updateMeasurements(counts);
				//Update the measurements here for different types of measurement
				//this one is for link volumes
				List<Measurement> linkVolumeMeasurements=m.getMeasurementsByType().get(MeasurementType.linkVolume);
				for(Measurement mm:linkVolumeMeasurements) {
					for(String timeId:mm.getVolumes().keySet()) {
						double volume=0;
						for(Id<Link>linkId:(ArrayList<Id<Link>>)mm.getAttribute(Measurement.linkListAttributeName)) {
							volume+=counts.get(timeId).get(linkId);
						}
						mm.addVolume(timeId, volume);
					}
				}
				//new MeasurementsWriter(m).write();
				this.storage.storeMeasurements(this.currentParam.getParam(), m);
			}
		}else {
		int counter=event.getIteration();
			if(counter==this.maxIter) {
				Measurements m=storage.getCalibrationMeasurements().clone();
				Map<String,Map<Id<Link>,Double>>counts= this.pcuVolumeCounter.geenerateLinkCounts();
				//m.updateMeasurements(counts);
				//Update the measurements here for different types of measurement
				//this one is for link volumes
				List<Measurement> linkVolumeMeasurements=m.getMeasurementsByType().get(MeasurementType.linkVolume);
				for(Measurement mm:linkVolumeMeasurements) {
					for(String timeId:mm.getVolumes().keySet()) {
						double volume=0;
						for(Id<Link>linkId:(ArrayList<Id<Link>>)mm.getAttribute(Measurement.linkListAttributeName)) {
							volume+=counts.get(timeId).get(linkId);
						}
						mm.addVolume(timeId, volume);
					}
				}
				//m.writeCSVMeasurements(fileLoc);
				this.storage.storeMeasurements(this.currentParam.getParam(), m);
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