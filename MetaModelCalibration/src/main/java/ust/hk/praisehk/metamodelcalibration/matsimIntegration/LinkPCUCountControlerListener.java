package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;

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
			this.simStorage.storeMeasurements(this.currentParam.getParam(), this.pcuVolumeCounter.geenerateLinkCounts());
		}
		
	}
	
	
}

