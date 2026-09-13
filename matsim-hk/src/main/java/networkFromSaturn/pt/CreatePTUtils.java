package networkFromSaturn.pt;

import org.matsim.api.core.v01.Id;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import networkFromSaturn.CreateNetworkUtils;

public class CreatePTUtils {
	/**
	 * 
	 * @param vehicles:
	 *            vehicles container
	 * @param id:
	 *            ID of this kind of vehicleType
	 * @param length:
	 *            Length in metre
	 * @param width:
	 *            Width in metre
	 * @param maximum_velocity:
	 *            maximum velocity in m/s
	 * @param seats
	 * @param standing
	 * @param access_time:
	 *            Time required for one person to aboard
	 * @param egress_time:
	 *            Time required for one person to delight
	 * @param description:
	 *            Description
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public static VehicleType createVehicleType(Vehicles vehicles, String id, double length, double width,
			double maximum_velocity, int seats, int standing, double access_time, double egress_time, double pcu, 
			String description) {
		VehiclesFactory vehiclesFactory = vehicles.getFactory();
		VehicleType vehicletype = vehiclesFactory.createVehicleType(Id.create(id, VehicleType.class));
		vehicletype.setLength(length * CreateNetworkUtils.scaleDownFactor_pt);
		vehicletype.setWidth(width);
		vehicletype.setMaximumVelocity(maximum_velocity);
		vehicletype.setPcuEquivalents(pcu * CreateNetworkUtils.scaleDownFactor_pt);
		VehicleCapacity vehicletypeCap = vehicletype.getCapacity();
			vehicletypeCap.setSeats((int) Math.round(seats * CreateNetworkUtils.scaleDownFactor_pt));
			vehicletypeCap.setStandingRoom((int) Math.round(standing * CreateNetworkUtils.scaleDownFactor_pt));
		vehicletype.setDescription(description);
		// Engine information is left blank for simplicity
		vehicles.addVehicleType(vehicletype);

		vehicletype.setAccessTime(access_time);
		vehicletype.setEgressTime(egress_time);

		return vehicletype;
	}
}
