package emissionHK;

import java.util.Map;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.ShutdownListener;

import emissionHK.EmissionUtils.ColdPollutant;

public class PRAISEEmissionControlerListener implements IterationStartsListener, IterationEndsListener, ShutdownListener {

	final static private Logger logger = Logger.getLogger(PRAISEEmissionControlerListener.class);
	private WarmEmissionHandler warmEmissionHandler;
	private ColdEmissionHandler coldEmissionHandler;
	private int lastIteration;
	
	@Inject
	PRAISEEmissionControlerListener(WarmEmissionHandler warmEmissionHandler, 
			ColdEmissionHandler coldEmissionHandler, int lastIteration) {
		this.warmEmissionHandler = warmEmissionHandler;
		this.coldEmissionHandler = coldEmissionHandler;
		this.lastIteration = lastIteration;
	}
	
	@Override
	public void notifyShutdown(ShutdownEvent event) {
		// TODO Auto-generated method stub

	}
	
	public void notifyIterationStarts(IterationStartsEvent event) {
		if(event.getIteration()==lastIteration) {
			event.getServices().getEvents().addHandler(this.warmEmissionHandler);
			event.getServices().getEvents().addHandler(this.coldEmissionHandler);
		}
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		//Print the warm emissions.
		Map<Pollutant, Double> warmPollutantCounts = warmEmissionHandler.getPollutantCount();
		for(Pollutant pollutant: warmPollutantCounts.keySet()) {
			logger.info("Warm pollutant "+pollutant+" count: "+warmPollutantCounts.get(pollutant)+"g.");
		}
		
		//Print the cold emissions.
		Map<ColdPollutant, Double> coldpollutantCount = coldEmissionHandler.getColdpollutantCount();
		for(ColdPollutant pollutant: coldpollutantCount.keySet()) {
			logger.info("Cold pollutant "+pollutant+" count: "+coldpollutantCount.get(pollutant)+"g.");
		}
	}

}
