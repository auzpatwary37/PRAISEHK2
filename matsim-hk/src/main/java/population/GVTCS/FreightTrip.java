package population.GVTCS;

import java.util.ArrayList;

import org.matsim.api.core.v01.Id;
import org.matsim.vehicles.Vehicle;

import population.TPUSB;

public class FreightTrip {
	/**
	 * a convenient class to store important freight trip information
	 */

	private final Id<Vehicle> vehicleId;
	private TPUSB otpusb;
	private TPUSB dtpusb;
	private double tripId;
	private Id<FreightTrip> combinedVehicleandTripId;
	private String streetOrigin;
	private String buildingOrigin;
	private double landUseOrigin;
	private double departureTime;
	private double arrivalTime;
	private String streetDestination;
	private String buildingDestination;
	private double landUseDestination;
	private double tripPurpose;
	private double tripWeight;
	private double opdz;
	private double dpdz;
	private int noOfPerson;
	private Double crossBoundaryControlUsed;
	private Double timeEnteringHk;
	private ArrayList<Double> tunnelOrTollUsed=new ArrayList<>();
	private Double timePeriod;


	public FreightTrip(Id<Vehicle> vehicleId,Double tripId, TPUSB otpusb,TPUSB dtpusb,String streetOrigin,
			String buildingOrigin, String streetDestination, String buildingDestination, double landUseOrigin,double landUseDestination,
			double departureTime, double arrivalTime,double tripPurpose,double tripWeight,double opdz, double dpdz, Double crossBoundaryControlused, 
			Double timeOfEnteringHk, double timePeriod) {
		this.vehicleId=vehicleId;
		this.tripId=tripId;
		this.combinedVehicleandTripId=Id.create(this.vehicleId.toString()+this.tripId, FreightTrip.class);
		this.otpusb=otpusb;
		this.dtpusb=dtpusb;
		this.streetOrigin=streetOrigin;
		this.streetDestination=streetDestination;
		this.buildingOrigin=buildingOrigin;
		this.buildingDestination=buildingDestination;
		this.landUseOrigin=landUseOrigin;
		this.landUseDestination=landUseDestination;
		this.departureTime=departureTime;
		this.arrivalTime=arrivalTime;
		this.opdz=opdz;
		this.dpdz=dpdz;
		this.tripPurpose=tripPurpose;
		this.tripWeight=tripWeight;
		this.crossBoundaryControlUsed=crossBoundaryControlused;
		this.timeEnteringHk=timeOfEnteringHk;
		this.timePeriod=timePeriod;



	}

	public void addTollFacilityUsed(Double tollFacility) {
		this.tunnelOrTollUsed.add(tollFacility);
	}
	public Id<Vehicle> getVehicleId() {
		return vehicleId;
	}

	public TPUSB getOtpusb() {
		return otpusb;
	}

	public TPUSB getDtpusb() {
		return dtpusb;
	}

	public double getTripId() {
		return tripId;
	}

	public Id<FreightTrip> getCombinedVehicleandTripId() {
		return combinedVehicleandTripId;
	}

	public String getStreetOrigin() {
		return streetOrigin;
	}

	public String getBuildingOrigin() {
		return buildingOrigin;
	}

	public double getLandUseOrigin() {
		return landUseOrigin;
	}

	public double getDepartureTime() {
		return departureTime;
	}

	public double getArrivalTime() {
		return arrivalTime;
	}

	public String getStreetDestination() {
		return streetDestination;
	}

	public String getBuildingDestination() {
		return buildingDestination;
	}

	public double getLandUseDestination() {
		return landUseDestination;
	}

	public double getTripPurpose() {
		return tripPurpose;
	}

	public double getTripWeight() {
		return tripWeight;
	}

	public double getOpdz() {
		return opdz;
	}

	public double getDpdz() {
		return dpdz;
	}

	public int getNoOfPerson() {
		return noOfPerson;
	}
	/**
	 * this method will clone existing trip;
	 * @return
	 */
	public FreightTrip cloneTrip() {
		FreightTrip trip;
		if(this.crossBoundaryControlUsed!=null) {
		trip=new FreightTrip(Id.createVehicleId(this.vehicleId.toString()),new Double(this.tripId),this.otpusb,this.dtpusb,new String(this.streetOrigin),
				new String(this.buildingOrigin),new String(this.streetDestination),new String(this.buildingDestination),this.landUseOrigin,this.landUseDestination,
				this.departureTime,this.arrivalTime,this.tripPurpose,this.tripWeight,this.opdz,this.dpdz,new Double(this.crossBoundaryControlUsed),
				new Double(this.timeEnteringHk),this.timePeriod);
		}else {
			trip=new FreightTrip(Id.createVehicleId(this.vehicleId.toString()),new Double(this.tripId),this.otpusb,this.dtpusb,new String(this.streetOrigin),
					new String(this.buildingOrigin),new String(this.streetDestination),new String(this.buildingDestination),this.landUseOrigin,this.landUseDestination,
					this.departureTime,this.arrivalTime,this.tripPurpose,this.tripWeight,this.opdz,this.dpdz,null,null,this.timePeriod);
		}
		return trip;
	}

	public Double getCrossBoundaryControlUsed() {
		return crossBoundaryControlUsed;
	}

	public Double getTimeEnteringHk() {
		return timeEnteringHk;
	}

	public ArrayList<Double> getTunnelOrTollUsed() {
		return tunnelOrTollUsed;
	}

	public Double getTimePeriod() {
		return timePeriod;
	}

	public void setOtpusb(TPUSB otpusb) {
		this.otpusb = otpusb;
	}

	public void setDtpusb(TPUSB dtpusb) {
		this.dtpusb = dtpusb;
	}

}
