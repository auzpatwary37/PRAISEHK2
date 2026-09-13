package transitFareAndHandler;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.handler.AgentWaitingForPtEventHandler;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class FirstClassCountHandler implements AgentWaitingForPtEventHandler, PersonEntersVehicleEventHandler {
		
	private final double fromTime;
	private final double toTime;
	private double count = 0.;
	
	public FirstClassCountHandler(double fromTime, double toTime) {
		this.fromTime = fromTime;
		this.toTime = toTime;
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		return;
	}

	@Override
	public void handleEvent(AgentWaitingForPtEvent event) {
		if(event.getTime() >= fromTime && event.getTime() <= toTime) {
			String thisFacilityId = event.getWaitingAtStopId().toString();
			if(thisFacilityId.length() > 3 && thisFacilityId.subSequence(0, 3).equals("NSX")){
				count++;
			}
		}
	}
	
	public double getCount() {
		return count;
	}
}
