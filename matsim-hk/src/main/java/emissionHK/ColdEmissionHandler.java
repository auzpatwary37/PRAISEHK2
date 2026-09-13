package emissionHK;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.gbl.Gbl;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;

import emissionHK.EmissionUtils.ColdPollutant;
import emissionHK.EmissionUtils.EmissionVehicleType;

public class ColdEmissionHandler implements LinkLeaveEventHandler, VehicleLeavesTrafficEventHandler, VehicleEntersTrafficEventHandler {

    private final Logger logger = Logger.getLogger(ColdEmissionHandler.class);

    private final Vehicles vehicles;
    private final Vehicles transitVehicles;
    private final ColdEmissionTable coldEmissionAnalysisTable;
    private final EventsManager eventsManager;

    private int nonCarWarn = 0;
    private final Map<ColdPollutant, Double> pollutantCount;

    private final Map<Id<Vehicle>, Double> vehicleId2stopEngineTime = new HashMap<>();
    private final Map<Id<Vehicle>, Double> vehicleId2parkingDuration = new HashMap<>();
    private final Map<Id<Vehicle>, Id<Link>> vehicleId2coldEmissionEventLinkId = new HashMap<>();
    
    public ColdEmissionHandler(Vehicles vehicles, Vehicles transitVehicles,
            EventsManager emissionEventsManager, String emissionTablePath) throws IOException {
        this.vehicles = vehicles;
        this.transitVehicles = transitVehicles;
        this.coldEmissionAnalysisTable = new ColdEmissionTable(emissionTablePath);
        this.eventsManager = emissionEventsManager;
        this.pollutantCount = new HashMap<>();
    }

    @Override
    public void reset(int iteration) {
        vehicleId2stopEngineTime.clear();
        vehicleId2parkingDuration.clear();
        vehicleId2coldEmissionEventLinkId.clear();
        pollutantCount.clear();
    }
    
    public Map<ColdPollutant, Double> getColdpollutantCount(){
    	return pollutantCount;
    }

    @Override
    public void handleEvent(LinkLeaveEvent event) {
    	//Comment: I can't see the reason why the cold emission event is handled here, the engine is not switched off. Enoch, Jan 2018.
    	
//		Id<Vehicle> vehicleId = event.getVehicleId();
//        Id<Link> linkId = event.getLinkId();
//        Link link = this.network.getLinks().get(linkId);
//        double linkLength = link.getLength();
//
//        if (linkLength == 0.) {
//            if (zeroLinkLengthWarnCnt == 0 ){
//                logger.warn("Length of the link "+ linkId + " is zero. No emissions will be estimated for this link. Make sure, this is intentional.");
//                logger.warn(Gbl.ONLYONCE);
//                zeroLinkLengthWarnCnt++;
//            }
//            return;
//        }
//
//        Double previousDistance = this.vehicleId2accumulatedDistance.get(vehicleId);
//        if (previousDistance != null) {
//            double distance = previousDistance + linkLength;
//            double parkingDuration = this.vehicleId2parkingDuration.get(vehicleId);
//            Id<Link> coldEmissionEventLinkId = this.vehicleId2coldEmissionEventLinkId.get(vehicleId);
//
//            Vehicle vehicle = vehicles.getVehicles().get(vehicleId);
//
//            if ((distance / 1000) > 1.0) {
//                this.coldEmissionAnalysisModule.calculateColdEmissionsAndThrowEvent(
//                        coldEmissionEventLinkId,
//                        vehicle,
//                        event.getTime(),
//                        parkingDuration,
//                        2
//                );
//                this.vehicleId2accumulatedDistance.remove(vehicleId);
//            } else {
//                this.vehicleId2accumulatedDistance.put(vehicleId, distance);
//            }
//        }
    }

    @Override
    public void handleEvent(VehicleLeavesTrafficEvent event) {
        if (!event.getNetworkMode().equals("car")) {
            if( nonCarWarn <=1) {
                logger.warn("non-car modes are supported, however, not properly tested yet.");
                logger.warn(Gbl.ONLYONCE);
                nonCarWarn++;
            }
        }
        Id<Vehicle> vehicleId = event.getVehicleId();
        Double stopEngineTime = event.getTime();
        this.vehicleId2stopEngineTime.put(vehicleId, stopEngineTime); //Record the stop engine time.
    }

    // TODO actually, the engine starts before with the PersonEntersVehicleEvent
    // In Hong Kong scenario, we assume it starts only when it enters traffic, as congestion is serious. Enoch, Jan 2018.
    @Override
    public void handleEvent(VehicleEntersTrafficEvent event) {
        if (!event.getNetworkMode().equals("car")) {
            if( nonCarWarn <=1) {
                logger.warn("non-car modes are supported, however, not properly tested yet.");
                logger.warn(Gbl.ONLYONCE);
                nonCarWarn++;
            }
        }
        Id<Link> linkId = event.getLinkId();
        Id<Vehicle> vehicleId = event.getVehicleId();
        double startEngineTime = event.getTime();
        this.vehicleId2coldEmissionEventLinkId.put(vehicleId, linkId);

        double parkingDuration;
        if (this.vehicleId2stopEngineTime.containsKey(vehicleId)) {
            double stopEngineTime = this.vehicleId2stopEngineTime.get(vehicleId);
            parkingDuration = startEngineTime - stopEngineTime;

        } else { //parking duration is assumed to be at least 12 hours when parking overnight
            parkingDuration = 43200.0; //TODO: A problem aroused is that, for the public transit vehicle, we don't know what exactly the parking duration is.
        }
        this.vehicleId2parkingDuration.put(vehicleId, parkingDuration);

        Vehicle vehicle = vehicles.getVehicles().get(vehicleId);
        if(vehicle==null) { //Look for the transit vehicle table if not in ordinary vehicle map.
        	vehicle = transitVehicles.getVehicles().get(vehicleId);
        }
        EmissionVehicleType vt = EmissionUtils.getEmissionVehicleTypes(vehicle);
        if(vt.equals(EmissionVehicleType.ZeroEmissionVeh)) {
        	return; //No emission for zero emission vehicle.
        }
        Map<ColdPollutant, Double> coldEmissions = this.coldEmissionAnalysisTable.processAndThrowColdEmissions(eventsManager, 
        		event.getTime(), linkId, vehicleId, vt, parkingDuration);
		for(ColdPollutant pollutant: coldEmissions.keySet()) {
			Double count = pollutantCount.get(pollutant); //Update the pollutant count
			if(count==null) {
				count = (double) 0;
			}
			count+=coldEmissions.get(pollutant);
			pollutantCount.put(pollutant, count);
		}
        
    }
}