package transitFareAndHandler;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.handler.AgentWaitingForPtEventHandler;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class StopWaitingTimeHandler implements AgentWaitingForPtEventHandler, PersonEntersVehicleEventHandler{
	
	private final double fromTime;
	private final double toTime;
	private Map<Id<TransitStopFacility>, List<WaitInfo>> facilitiesConsidered = new HashMap<>();
	private Map<Id<Person>, WaitInfo> waitingPerson = new HashMap<>();
	
	public StopWaitingTimeHandler(List<String> facilitiesConsidered, double fromTime, double toTime) {
		this.fromTime = fromTime;
		this.toTime = toTime;
		for(String facility: facilitiesConsidered) {
			this.facilitiesConsidered.put(Id.create(facility, TransitStopFacility.class), new ArrayList<>());
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if(event.getTime() >= fromTime && event.getTime() <= toTime) {
			if(this.waitingPerson.containsKey(event.getPersonId())) {
				WaitInfo wi = this.waitingPerson.get(event.getPersonId());
				wi.waitingTime_s = event.getTime() - wi.arrivalTime;
				this.waitingPerson.remove(event.getPersonId());
			}
		}
	}

	@Override
	public void handleEvent(AgentWaitingForPtEvent event) {
		if(event.getTime() >= fromTime && event.getTime() <= toTime) {
			Id<TransitStopFacility> thisFacilityId = event.getWaitingAtStopId();
			if(this.facilitiesConsidered.containsKey(thisFacilityId)){
				WaitInfo wi = new WaitInfo(event.getTime());
				this.facilitiesConsidered.get(thisFacilityId).add(wi);
				this.waitingPerson.put(event.getPersonId(), wi);
			}
		}
	}
	
	public void printStopWaiting(String dir) {
		for(var facilityAndCount: facilitiesConsidered.entrySet()) {
			if(facilityAndCount.getValue().size() == 0) {
				continue;
			}
			try {
				FileWriter fileWriter = new FileWriter(dir+"MTR_"+facilityAndCount.getKey().toString()+".csv", false);
				fileWriter.append("arrival_time,waiting_time\n");
				for(WaitInfo wi: facilityAndCount.getValue()) {
					fileWriter.append(wi.arrivalTime+","+wi.waitingTime_s+"\n");
				}
				fileWriter.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private class WaitInfo{
		private final double arrivalTime;
		private double waitingTime_s;
		
		private WaitInfo(double arrivalTime) {
			this.arrivalTime = arrivalTime;
		}
	}
}
