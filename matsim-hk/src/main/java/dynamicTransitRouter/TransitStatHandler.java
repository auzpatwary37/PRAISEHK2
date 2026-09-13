package dynamicTransitRouter;

import java.util.HashSet;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.handler.AgentWaitingForPtEventHandler;
import org.matsim.vehicles.Vehicle;

/**
 * This class aims to do some transit stats, but currently is just for people wait count analysis
 * @author eleead
 *
 */
public class TransitStatHandler implements TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler, 
		AgentWaitingForPtEventHandler {

	private Set<Id<Vehicle>> transitVehicles;
	private int peopleWaiting = 0; //A counter for people waiting for pt.
	
	public TransitStatHandler() {
		peopleWaiting = 0;
		transitVehicles = new HashSet<Id<Vehicle>>();
	}
	
	public void reset(int iteration) {
		peopleWaiting = 0;
		transitVehicles.clear();
	}
	
	public int getPeopleWaiting() {
		return peopleWaiting;
	}
	
	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		transitVehicles.add(event.getVehicleId());

	}
	
	@Override
	public void handleEvent(AgentWaitingForPtEvent event) {
		this.peopleWaiting++;
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (!event.getPersonId().toString().contains("pt_")) { // Ignore driver events
			if(transitVehicles.contains(event.getVehicleId())) {
				this.peopleWaiting--;
			}
		}
	}

}
