package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.StartupListener;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;





public class LinkPCUCountControlerListener implements StartupListener,BeforeMobsimListener, AfterMobsimListener{
	@Inject
	@Named("currentParam")paramContainer currentParam;
	
	@Inject
	private LinkPCUCountEventHandler pcuVolumeCounter;
	
	@Inject
	private @Named("fileLoc") String fileLoc;
	
	@Inject
	private MeasurementsStorage simStorage;
	
	@Inject
	private EventsManager eventsManager;
	
	private int maxIter=0;
	

	
	@Override
	public void notifyStartup(StartupEvent event) {
		this.eventsManager.addHandler(pcuVolumeCounter);
		this.maxIter=event.getServices().getConfig().controler().getLastIteration();
		}
	
	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		this.pcuVolumeCounter.resetLinkCount();
	}
	
	@Override
	public void notifyAfterMobsim(AfterMobsimEvent event) {
		
		int counter=event.getIteration();
		
		
		
		if(counter==this.maxIter) {
			Measurements m=this.simStorage.getCalibrationMeasurements().clone();
			Map<String,Map<Id<Link>,Double>>counts=this.pcuVolumeCounter.geenerateLinkCounts();
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
					mm.putVolume(timeId, volume);
				}
			}
			this.simStorage.storeMeasurements(this.currentParam.getParam(),m);
		}
		
	}
	
	
}

