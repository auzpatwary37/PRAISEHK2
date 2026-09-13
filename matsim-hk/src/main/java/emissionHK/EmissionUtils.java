package emissionHK;

import org.matsim.contrib.emissions.Pollutant;
import org.matsim.vehicles.Vehicle;

public class EmissionUtils {
	public enum EmissionVehicleType { PC, MotorCycle, LGV3, LGV4, LGV6, HGV7, HGV8, Bus, SDBus, Minibus, ZeroEmissionVeh };
	public enum ColdPollutant{ CO("CO"), HC("HC"), NOX("NOx"), CO2_TOTAL("CO2(total)"), PM("PM");
		
		private final String key;

		ColdPollutant(String key) {
			this.key = key;
		}

		public String getText() {
			return key;
		}}
	
	/**
	 * Given the vehicle object, find out the vehicle type.
	 * @param vehicle Vehicle to be concerned
	 * @return The vehicleType
	 */
	public static EmissionVehicleType getEmissionVehicleTypes(Vehicle vehicle) {
		String description = vehicle.getType().getDescription();
		String vehicleId = vehicle.getType().getId().toString();
		if(vehicleId.equals("defaultVehicleType") || description.contains("PASSENGER_CAR")) {
			return EmissionVehicleType.PC;
		}else if(description.contains("Medium Goods Van")) { //Weight is not less than 9t
			return EmissionVehicleType.HGV7; //5.5-15t
		}else if(description.contains("Light Goods Van")) { //Permitted not less than 2.7t
			return EmissionVehicleType.LGV4; //2.5-3.5t
		}else if(description.contains("Heavy Goods Van") || //Permitted not less than 30t
				description.contains("Trailer Unit TU")) {
			return EmissionVehicleType.HGV8; //>15t
		}else if(vehicleId.equals("bus") || vehicleId.equals("crossHarbourbus")) {
			return EmissionVehicleType.Bus;
		}else if (vehicleId.equals("minibus")) {
			return EmissionVehicleType.Minibus;
		}else {
			return EmissionVehicleType.ZeroEmissionVeh;
		}
	}
	
	/**
	 * Given a string, convert it to the respective vehicle type.
	 * @param vehicleName
	 * @return
	 */
	public static EmissionVehicleType getEmissionVehicleTypes(String vehicleName) {
		switch(vehicleName) {
			case "PC":
				return EmissionVehicleType.PC;
			case "LGV3":
				return EmissionVehicleType.LGV3;
			case "LGV4":
				return EmissionVehicleType.LGV4;
			case "LGV6":
				return EmissionVehicleType.LGV6;
			case "HGV7":
				return EmissionVehicleType.HGV7;
			case "HGV8":
				return EmissionVehicleType.HGV8;
			case "FBDD":
				return EmissionVehicleType.Bus;
			case "FBSD":
				return EmissionVehicleType.SDBus; //Single Decker bus
			case "PLB":
				return EmissionVehicleType.Minibus;
			case "MC":
				return EmissionVehicleType.MotorCycle;
			default:
				throw new IllegalArgumentException("The vehicle name "+vehicleName+" is not found in our scheme!");
		}
	}
	
	public static Pollutant getWarmPollutant(String pollutantName) {
		if(pollutantName.equals("CO")) {
			return Pollutant.CO;
		}else if (pollutantName.equals("CO2")) {
			return Pollutant.CO2_TOTAL;
		}else if (pollutantName.equals("PM2.5")) {
			return Pollutant.PM;
		}else if (pollutantName.equals("VOC")) {
			return Pollutant.HC;
		}else if (pollutantName.equals("NOx")) {
			return Pollutant.NOx;
		}else {
			throw new IllegalArgumentException("The pollutant "+pollutantName+" is not found in our scheme!");
		}
	}
	
	public static ColdPollutant getColdPollutant(String pollutantName) {
		if(pollutantName.equals("CO")) {
			return ColdPollutant.CO;
		}else if (pollutantName.equals("CO2")) {
			return ColdPollutant.CO2_TOTAL;
		}else if (pollutantName.equals("PM2.5")) {
			return ColdPollutant.PM;
		}else if (pollutantName.equals("VOC")) {
			return ColdPollutant.HC;
		}else if (pollutantName.equals("NOx")) {
			return ColdPollutant.NOX;
		}else {
			throw new IllegalArgumentException("The pollutant "+pollutantName+" is not found in our scheme!");
		}
	}
}
