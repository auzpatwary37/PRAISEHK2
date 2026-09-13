package transitFareAndHandler;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.ReplanningEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.ReplanningListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.controler.listener.StartupListener;

import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.TransitStatHandler;
import dynamicTransitRouter.costs.PTRecordHandler;
import dynamicTransitRouter.costs.StopStopTime;

public class TransitFareControlerListener implements IterationStartsListener, StartupListener, IterationEndsListener, 
	ShutdownListener {
	
	final static private Logger log = Logger.getLogger(TransitFareControlerListener.class);
	TransitFareHandler transitFareHandler;
	PTRecordHandler ptRecordHandler;
	TransitStatHandler tsh;
	
	@Inject
	TransitFareControlerListener(TransitFareHandler fareHandler, StopStopTime ptRecordHandler) {
		this.transitFareHandler = fareHandler;
		this.ptRecordHandler = (PTRecordHandler) ptRecordHandler;
		//Gbl.printBuildInfo("RoadPricing", "/org.matsim.contrib/roadpricing/revision.txt");
	}
	
	
	
	@Override
	public void notifyShutdown(ShutdownEvent event) {
		// TODO Auto-generated method stub
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		//This function is simply return the fare collected after an iteration.
		log.info("Fare collected by MTR is " + transitFareHandler.getMtrFareCollected() +
				" in "+transitFareHandler.getMtrTripCount()+" trips.");
		log.info("Fare collected by tram  is " + transitFareHandler.getTramFareCollected()+
				" in "+transitFareHandler.getTramTripCount()+" trips.");
		log.info("Fare collected by bus is " + transitFareHandler.getBusFareCollected()+
				" in "+transitFareHandler.getBusTripCount()+" trips.");
		log.info("Fare collected by ferry is " + transitFareHandler.getFerryFareCollected()+
				" in "+transitFareHandler.getFerryTripCount()+" trips.");
		log.info("Fare collected by LR is " + transitFareHandler.getLRFareCollected() +
				" in "+transitFareHandler.getLRTripCount()+" trips.");
		log.info("Total Discount given is " + transitFareHandler.getDiscountGiven() +
				" in "+transitFareHandler.getInterchangeDiscountCount()+" interchanges.");
		
		//Process the direct walk count.
		log.info("The number of direct walk pt leg is "+ TransitRouterFareDynamicImpl.getDirectWalkCount() );
		TransitRouterFareDynamicImpl.resetWalkCounter();
		
		log.info("The number of people still waiting is " + tsh.getPeopleWaiting());
		
	}

	@Override
	public void notifyStartup(StartupEvent event) {
		tsh = new TransitStatHandler(); //Initialize the transit stats
		event.getServices().getEvents().addHandler(tsh);
		
		//Reset the router
		TransitRouterFareDynamicImpl.reset();
		
	}

	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		ptRecordHandler.processResidualQueue();
	}

}
