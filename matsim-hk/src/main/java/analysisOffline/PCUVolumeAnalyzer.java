package analysisOffline;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;

import com.google.inject.Inject;

/**
 * Wrapper class around VolumeAnalyzer for PCU measurement
 */

public class PCUVolumeAnalyzer extends VolumesAnalyzer implements VehicleEntersTrafficEventHandler, LinkEnterEventHandler{
	public final int timeBinSize;
	private final int maxTime = 24 * 3600 - 1;
	public final int maxSlotIndex;
	private Map<Id<Link>, double[]> PCUlinks;
	//private HashMap<Id<Vehicle>,Double> enRoutePcu=new HashMap<>(); //Stores the PCU of vehicle
	private Vehicles vehicles;
	private Vehicles transitVehicles;
	
	@Inject
	PCUVolumeAnalyzer(Scenario scenario, EventsManager eventsManager) {
		this(scenario.getNetwork(), scenario.getVehicles(), scenario.getTransitVehicles(), eventsManager,
				3600);
	}
	
	public PCUVolumeAnalyzer(Network network, Vehicles vehicles, Vehicles transitVehicles, 
			EventsManager eventsManager, int timeBinSize) {
		super(timeBinSize, 24 * 3600 - 1, network);
		this.timeBinSize = timeBinSize;
		this.maxSlotIndex = (this.maxTime/this.timeBinSize) + 1;
		this.PCUlinks = new HashMap<>();
		eventsManager.addHandler(this);
		this.vehicles = vehicles;
		this.transitVehicles = transitVehicles;
	}
	
	private double getVehiclePCU (Id<Vehicle> vehicleId) {
		if(vehicles.getVehicles().containsKey(vehicleId)){
			return vehicles.getVehicles().get(vehicleId).getType().getPcuEquivalents();
		}else {
			return transitVehicles.getVehicles().get(vehicleId).getType().getPcuEquivalents();
		}
	}
	
	/**
	 * It is a function to get the PCU and store the PCU volume in the PCUlinks container
	 * @param linkId
	 * @param vehicleId
	 * @param time
	 */
	private void handleVehicleForPCU(Id<Link> linkId, Id<Vehicle> vehicleId, double time) {
		double[] volumes = this.PCUlinks.get(linkId);
		if (volumes == null) {
			volumes = new double[this.maxSlotIndex + 1]; // initialized to 0 by default, according to JVM specs
			this.PCUlinks.put(linkId, volumes);
		}
		int timeslot = this.getTimeSlotIndex(time);
		volumes[timeslot] += getVehiclePCU(vehicleId);
	}
	
	@Override	
	public void handleEvent(final VehicleEntersTrafficEvent event) {
		handleVehicleForPCU(event.getLinkId(), event.getVehicleId(), event.getTime());
	}
	
	
	//vehicles are counted if they just enter the link at that specific time step
	@Override
	public void handleEvent(final LinkEnterEvent event) {
		handleVehicleForPCU(event.getLinkId(), event.getVehicleId(), event.getTime());
	}
	
	private int getTimeSlotIndex(final double time) {
		if (time > this.maxTime) {
			return this.maxSlotIndex;
		}
		return ((int)time / this.timeBinSize);
	}
	
	public double[] getPCUVolumesPerHourForLink(final Id<Link> linkId) {
		
		double[] volumes = new double[24];
		
		double[] pcuvolumesForLink = this.getPCUVolumesForLink(linkId);
		if (pcuvolumesForLink == null) return volumes; //So basically there is 

		int slotsPerHour = (int)(3600.0 / this.timeBinSize);
		for (int hour = 0; hour < 24; hour++) {
			double time = hour * 3600.0;
			for (int i = 0; i < slotsPerHour; i++) {
				volumes[hour] += pcuvolumesForLink[this.getTimeSlotIndex(time)];
				time += this.timeBinSize;
			}
		}
		return volumes;
	}
	
	
	public double[] getPCUVolumesForLink(final Id<Link> linkId) {	
		return this.PCUlinks.get(linkId);
	}
	
	@Override
	public int[] getVolumesForLink(final Id<Link> linkId) {	
		int[] outArray=new int[this.PCUlinks.get(linkId).length];
		for(int i=0;i<this.PCUlinks.get(linkId).length;i++) {
			outArray[i]=(int)this.PCUlinks.get(linkId)[i];
		}
		
		return outArray; 
	}
	
	@Override
	public void reset(final int iteration) {
		this.PCUlinks.clear();
		//this.enRoutePcu.clear();
		super.reset(iteration);
	}

	
}

	