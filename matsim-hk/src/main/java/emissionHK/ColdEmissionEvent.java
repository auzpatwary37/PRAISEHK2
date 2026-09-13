package emissionHK;

import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.vehicles.Vehicle;

import emissionHK.EmissionUtils.ColdPollutant; //This is the only line of difference.

/**
 * This class is the origin ColdEmissionEvent in emission contrib, but the ColdPollutant is the one defined for Hong Kong.
 * @author eleead
 *
 */
public class ColdEmissionEvent extends Event {
    public final static String EVENT_TYPE = "coldEmissionEvent";
    public final static String ATTRIBUTE_LINK_ID = "linkId";
    public final static String ATTRIBUTE_VEHICLE_ID = "vehicleId";
    private final Id<Link> linkId;
	private final Id<Vehicle> vehicleId;
	private final Map<ColdPollutant, Double> coldEmissions;
	
	public ColdEmissionEvent(double time, Id<Link> linkId, Id<Vehicle> vehicleId, Map<ColdPollutant, Double> coldEmissions) {
        super(time);
        this.linkId = linkId;
		this.vehicleId = vehicleId;
		this.coldEmissions = coldEmissions;
	}

	public Id<Link> getLinkId() {
		return linkId;
	}
	
	public Id<Vehicle> getVehicleId() {
		return vehicleId;
	}
	
	public Map<ColdPollutant, Double> getColdEmissions() {
		return coldEmissions;
	}

	@Override
	public Map<String, String> getAttributes(){
		Map<String, String> attributes = super.getAttributes();
		attributes.put(ATTRIBUTE_LINK_ID, this.linkId.toString());
		attributes.put(ATTRIBUTE_VEHICLE_ID, this.vehicleId.toString());
		for(Entry<ColdPollutant, Double> entry : coldEmissions.entrySet()){
			ColdPollutant pollutant = entry.getKey();
			Double value = entry.getValue();
			attributes.put(pollutant.toString(), value.toString());
		}
		return attributes;
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}


}
