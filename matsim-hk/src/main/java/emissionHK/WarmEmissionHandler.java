package emissionHK;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;

import emissionHK.EmissionUtils.EmissionVehicleType;

public class WarmEmissionHandler implements LinkEnterEventHandler, LinkLeaveEventHandler, 
											VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {
	private static final Logger logger = Logger.getLogger(WarmEmissionHandler.class);

	private final Network network;
	private final Vehicles emissionVehicles;
	private final Vehicles transitVehicles;
	private final WarmEmissionTable warmEmissionTable;
	private final EventsManager eventsManager;

	private int linkLeaveCnt = 0;
	private int linkLeaveFirstActWarnCnt = 0;
	private int linkLeaveSomeActWarnCnt = 0;
	private final Map<Pollutant, Double> pollutantCount;

	private int nonCarWarn = 0;

	private final Map<Id<Vehicle>, Tuple<Id<Link>, Double>> linkenter = new HashMap<>();
	private final Map<Id<Vehicle>, Tuple<Id<Link>, Double>> vehicleEntersTraffic = new HashMap<>();
	
	public WarmEmissionHandler(Vehicles emissionVehicles, Vehicles transitVehicles, 
			final Network network, EventsManager emissionEventsManager, String emissionTablePath) throws IOException {

		this.emissionVehicles = emissionVehicles;
		this.transitVehicles = transitVehicles;
		this.network = network;
		this.warmEmissionTable = new WarmEmissionTable(emissionTablePath);
		this.eventsManager = emissionEventsManager;
		this.pollutantCount = new HashMap<>();
	}
	
	@Override
	public void reset(int iteration) {
		linkLeaveCnt = 0;
		linkLeaveFirstActWarnCnt = 0;

		linkenter.clear();
		this.pollutantCount.clear();
		vehicleEntersTraffic.clear();
	}
	
	public Map<Pollutant, Double> getPollutantCount(){
		return this.pollutantCount;
	}

	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		this.vehicleEntersTraffic.remove(event.getVehicleId()); //Remove it from the enter traffic time.
		
		if(!event.getNetworkMode().equals("car")){
			if( nonCarWarn <=1) {
				logger.warn("non-car modes are supported, however, not properly tested yet.");
				logger.warn(Gbl.ONLYONCE);
				nonCarWarn++;
			}
		}
		//Tuple<Id<Link>, Double> linkId2Time = new Tuple<Id<Link>, Double>(event.getLinkId(), event.getTime());
		//this.vehicleLeavesTraffic.put(event.getVehicleId(), linkId2Time);

		// yyyyyy This event should also trigger an emissions calculation, from link entry up to here.  Probably not done since this particular
		// event did not exist when the emissions contrib was programmed.  Would be easy to do: calculate the emission and remove
		// the vehicle from the linkenter data structure so that no second emission event is computed for travel from parking to
		// link leave.  (This could also be done, but the excellent should not be in the way of the good.)  kai, may'16
		
		//Account for the emission of this final link.
		Id<Vehicle> vehicleId = event.getVehicleId();
		if(!this.linkenter.containsKey(vehicleId)) { //It is the case when the vehicle is in the same coordinate.
			return; //Do nothing.
		}
		double enterTime = this.linkenter.get(vehicleId).getSecond();
		double leaveTime = event.getTime();
		
		Link link = this.network.getLinks().get(event.getLinkId());
		double speed = link.getLength()/(leaveTime-enterTime); //Get the link speed there.
		
		EmissionVehicleType vt = getEmissionVehicleTypeFromId(vehicleId);//Handle the vehicle
		
		getAndProcessWarmEmission(vt, link, speed, leaveTime, vehicleId);
		this.linkenter.remove(event.getVehicleId());
	}

	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		if(!event.getNetworkMode().equals("car")){
			if( nonCarWarn <=1) {
				logger.warn("non-car modes are supported, however, not properly tested yet.");
				logger.warn(Gbl.ONLYONCE);
				nonCarWarn++;
			}
		}
		Tuple<Id<Link>, Double> linkId2Time = new Tuple<Id<Link>, Double>(event.getLinkId(), event.getTime());
		this.vehicleEntersTraffic.put(event.getVehicleId(), linkId2Time);

	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		Tuple<Id<Link>, Double> linkId2Time = new Tuple<Id<Link>, Double>(event.getLinkId(), event.getTime());
		this.linkenter.put(event.getVehicleId(), linkId2Time);
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		Id<Vehicle> vehicleId = event.getVehicleId();
		Id<Link> linkId = event.getLinkId();
		double leaveTime = event.getTime();
		Link link = (Link) this.network.getLinks().get(linkId);
		double linkLength = link.getLength();

		if (linkLength == 0.) { //For Hong Kong scenario, this should not be happened.
			throw new IllegalStateException("The link length of link "+linkId+" is 0, which is illegal.");
		}

		// excluding links with zero lengths from leaveCnt. Amit July'17
		linkLeaveCnt++;

		if(!this.linkenter.containsKey(vehicleId)){
			int maxLinkLeaveFirstActWarnCnt = 3;
			if(linkLeaveFirstActWarnCnt < maxLinkLeaveFirstActWarnCnt){
				logger.info("Vehicle " + vehicleId + " is ending its first activity of the day and leaving link " + linkId + " without having entered.");
				logger.info("This is because of the MATSim logic that there is no link enter event for the link of the first activity");
				logger.info("Thus, no emissions are calculated for this link leave event.");
				if (linkLeaveFirstActWarnCnt == maxLinkLeaveFirstActWarnCnt) logger.warn(Gbl.FUTURE_SUPPRESSED);
			}
			linkLeaveFirstActWarnCnt++;
		} else if (!this.linkenter.get(vehicleId).getFirst().equals(linkId)){
			int maxLinkLeaveSomeActWarnCnt = 3;
			if(linkLeaveSomeActWarnCnt < maxLinkLeaveSomeActWarnCnt){
				logger.warn("Vehicle " + vehicleId + " is ending an activity other than the first and leaving link " + linkId + " without having entered.");
				logger.warn("This indicates that there is some inconsistency in vehicle use; please check your inital plans file for consistency.");
				logger.warn("Thus, no emissions are calculated neither for this link leave event nor for the last link that was entered.");
				if (linkLeaveSomeActWarnCnt == maxLinkLeaveSomeActWarnCnt) logger.warn(Gbl.FUTURE_SUPPRESSED);
			}
			linkLeaveSomeActWarnCnt++;
		} else { //This is the case when the link enter event exist, and matches the link enter event.
			double enterTime = this.linkenter.get(vehicleId).getSecond();
			double travelTime;
			if(!this.vehicleEntersTraffic.containsKey(vehicleId)){
				throw new IllegalStateException("A vehicle cannot leave a link if not in traffic.");
				//travelTime = leaveTime - enterTime; //If the vehicle does not even exist in the enter map. (How would it happen?)
			}else if(!this.vehicleEntersTraffic.get(vehicleId).getFirst().equals(event.getLinkId())){ //Normal case.
				//throw new IllegalStateException("Any link leave should have either vehicle enters or link enter.");
				travelTime = leaveTime - enterTime; //If it is not the link of enter traffic.
			}else { //The link is the same as the vehicle enter traffic link, in this case, no emission should be calculated.
				return; //No emission should be calculated there.
				//travelTime = leaveTime - this.vehicleEntersTraffic.get(vehicleId).getSecond();
				//throw new IllegalStateException("Any link leave should have either vehicle enters or link enter.");
			}

			if(!this.emissionVehicles.getVehicles().containsKey(vehicleId) && !this.transitVehicles.getVehicles().containsKey(vehicleId)){
				throw new RuntimeException("No vehicle defined for id " + vehicleId + ". " +
						"Please make sure that requirements for emission vehicles in " + 
						EmissionsConfigGroup.GROUP_NAME + " config group are met. Aborting...");
			}
			EmissionVehicleType vt = getEmissionVehicleTypeFromId(vehicleId);
			getAndProcessWarmEmission(vt, link, linkLength/travelTime, leaveTime, vehicleId);
		}
	}
	
	/**
	 * A convenient function to obtain the vehicle type from vehicle Id.
	 * @param vehicleId
	 * @return
	 */
	private EmissionVehicleType getEmissionVehicleTypeFromId(Id<Vehicle> vehicleId) {
		Vehicle vehicle = this.emissionVehicles.getVehicles().get(vehicleId);
		if(vehicle==null) {
			vehicle = this.transitVehicles.getVehicles().get(vehicleId);
		}
		EmissionVehicleType ev =  EmissionUtils.getEmissionVehicleTypes(vehicle);
		if(ev==null) {
			throw new RuntimeException("The vehicle "+vehicleId+" is not found or cannot be mapped! Please check.");
		}
		return ev;
	}
	
	/**
	 * A custom function to obtain and process the warm emission.
	 * @param vt The emission vehicle type
	 * @param link Link object
	 * @param travelSpeed The travel speed through the link
	 * @param leaveTime The time of reference to throw the event
	 * @param vehicleId The vehicle Id (Required for warm emission event)
	 */
	private void getAndProcessWarmEmission(EmissionVehicleType vt, Link link, double travelSpeed, double leaveTime, 
			Id<Vehicle> vehicleId) {
		if(vt==EmissionVehicleType.ZeroEmissionVeh) { //No emission for zero emission vehicle.
			return;
		}
		
		Map<Pollutant, Double> warmEmissions = warmEmissionTable.getWarmEmissions(vt, link.getLength(), travelSpeed);
		
		for(Pollutant pollutant: warmEmissions.keySet()) {
			Double count = pollutantCount.get(pollutant); //Update the pollutant count
			if(count==null) {
				count = (double) 0;
			}
			count+=warmEmissions.get(pollutant);
			pollutantCount.put(pollutant, count);
		}
		Event warmEmissionEvent = new WarmEmissionEvent(leaveTime, link.getId(), vehicleId, warmEmissions);
		this.eventsManager.processEvent(warmEmissionEvent);
	}
	
	public int getLinkLeaveCnt() {
		return linkLeaveCnt;
	}
	public int getLinkLeaveWarnCnt() {
		return linkLeaveFirstActWarnCnt;
	}
}
