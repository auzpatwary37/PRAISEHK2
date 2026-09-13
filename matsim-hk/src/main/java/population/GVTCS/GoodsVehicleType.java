package population.GVTCS;

import org.matsim.api.core.v01.Id;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehiclesFactory;

public class GoodsVehicleType {
	
	
	/**
	 * will create vehicleType
	 * @param type
	 * @return
	 */
	public static VehicleType getVehicleType(VehiclesFactory vf, double type) {
		VehicleType vt=vf.createVehicleType(Id.create("GV"+type, VehicleType.class));
		String desc;
		if(type==1) {
			desc="Goods Vehicle";
		}else if(type==2) {
			desc="Light Goods Vehicle";
		}else if(type==3) {
			desc="Medium Goods Vehicle";
		}else if(type==4) {
			desc="Heavy Goods Vehicle";
		}else if(type==5) {
			desc="Trailer Unit TU";
		}else {
			desc="tructor??";
		}
		vt.setDescription(desc);

		return vt;
	}
}
