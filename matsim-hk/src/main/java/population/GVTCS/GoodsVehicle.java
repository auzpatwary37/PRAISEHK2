package population.GVTCS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import population.PopGenUtils;
import population.TPUSB;
import population.TCS.HouseHoldMember;
/**
 * 
 * @author Ashraf
 *
 */
public class GoodsVehicle implements Vehicle{
	/**
	 * a convenient class to store all the goods vehicle information
	 */

	private VehicleType vehicleType;
	private Id<Vehicle> vehicleId;
	private double vehicleWeight;
	private HashMap<Double,FreightTrip> trips=new HashMap<>();
	private double ageOfgoodsVehicle;
	private double yearOfPurchase;
	private double purchasingPrice;
	private double averageFuelCost;
	private double routeCoiceCriteria;
	private List<Person> Persons;
	private List<Vehicle> vehicles;
	private double unitofAverageFuelCost;
	private double yearlyMaintainenceCost;
	private boolean crossHerbourTrip;
	private Double crossHerbourTunnelUsed;
	private Double timeAcrossCrossHerbourTunnel;
	private Double tripPurposeCrossHerbourTunnel;
	private String crossBoundaryControlPoint;


	public GoodsVehicle(VehicleType vehicleType,String gvId, double vehicleWeight,double age,
			double purchasingYear, double purchasingPrice, double fuelCost,double routeChoiceCriteria, double unitOfFuelCost,
			double yearlyMaintainanceCost, Double crossHerbourTrip,Double crossHerbourTunnelUsed, Double timeAcrossHerbour, 
			Double tripPurposeHerbour,String crossBoundaryControlPoint ) {
		this.vehicleType=vehicleType;
		this.vehicleWeight=vehicleWeight;
		this.vehicleId=Id.create(gvId,Vehicle.class);
		this.ageOfgoodsVehicle=age;
		this.yearOfPurchase=purchasingYear;
		this.averageFuelCost=fuelCost;
		this.routeCoiceCriteria=routeChoiceCriteria;
		this.purchasingPrice=purchasingPrice;
		this.unitofAverageFuelCost=unitOfFuelCost;
		this.yearlyMaintainenceCost=yearlyMaintainanceCost;
		if(crossHerbourTrip!=null && crossHerbourTrip==1) {this.crossHerbourTrip=true;}else {this.crossHerbourTrip=false;}
		this.crossHerbourTunnelUsed=crossHerbourTunnelUsed;
		this.timeAcrossCrossHerbourTunnel=timeAcrossHerbour;
		this.tripPurposeCrossHerbourTunnel=tripPurposeHerbour;
		this.crossBoundaryControlPoint=crossBoundaryControlPoint;
	}

	public void addTrip(FreightTrip trip) {
		
		if(trip.getVehicleId().equals(this.vehicleId)) {
			this.trips.put(trip.getTripId(),trip);
		}else {
			throw new IllegalArgumentException("Trip does not belong to this vehicle, Please Check.");
		}
		
	}
	
	/**
	 * This method creates person with only one trip. 
	 * i.e. this method is for creating fantom persons.
	 * @param trip
	 * @param vehiclesFactory 
	 * @param populationFactory 
	 * @return
	 */
	private ArrayList<Tuple<Person,Vehicle>> getTripPersonAndVehicle(FreightTrip trip, double weight,  
			HashMap<Double,String> activityDetails) {
		ArrayList<Tuple<Person,Vehicle>> personList=new ArrayList<>();
		PopulationFactory popfac = PopulationUtils.getFactory();
		if(trip.getOtpusb()!=null && trip.getDtpusb()!=null) {
			for(int i=0;i<weight;i++) {
				HashMap<Id<TPUSB>,Tuple<Double,Double>> randXY=this.generateTripSpecificRandomNumber(trip);
				Person person=popfac.createPerson(Id.createPersonId(this.getId().toString()+"_"+trip.getTripId()+"_"+i));
				Plan plan=popfac.createPlan();
				Activity oAct=popfac.createActivityFromCoord(activityDetails.get((Double)trip.getLandUseOrigin()), 
						new Coord(trip.getOtpusb().getSatCoord().getX()+randXY.get(trip.getOtpusb().getTPUSBId()).getFirst(),
								trip.getOtpusb().getSatCoord().getY()+randXY.get(trip.getOtpusb().getTPUSBId()).getSecond()));
				Activity dAct=popfac.createActivityFromCoord(activityDetails.get((Double)trip.getLandUseDestination()), 
						new Coord(trip.getDtpusb().getSatCoord().getX()+randXY.get(trip.getDtpusb().getTPUSBId()).getFirst(),
								trip.getDtpusb().getSatCoord().getY()+randXY.get(trip.getDtpusb().getTPUSBId()).getSecond()));
				oAct.setEndTime(trip.getDepartureTime()*24*3600);
				dAct.setStartTime(trip.getArrivalTime()*24*3600);
				Leg leg=popfac.createLeg("car");
				leg.setDepartureTime(trip.getDepartureTime()*24*3600);
				leg.setTravelTime(trip.getArrivalTime()*24*3600-trip.getDepartureTime()*24*3600);
				plan.addActivity(oAct);
				plan.addLeg(leg);
				plan.addActivity(dAct);
				person.addPlan(plan);
				Vehicle vehicle=VehicleUtils.getFactory().createVehicle(Id.createVehicleId(person.getId().toString()), 
						this.vehicleType);
				personList.add(new Tuple<Person,Vehicle>(person,vehicle));
				if(i==500) {
					break;
				}
			}
		}
		return personList;
	}
	
	/**
	 * This will create plans directly from vehicle 
	 * @param vehiclesFactory 
	 * @param populationFactory 
	 * @return
	 */
	private ArrayList<Tuple<Person,Vehicle>> getMinWeightPersonAndVehicle(HashMap<Double,String> activityDetails){
		ArrayList<Tuple<Person,Vehicle>> personList=new ArrayList<>();
		for(int j=0;j<this.getMinimumVehicleWeight();j++) {
			HashMap<Id<TPUSB>,Tuple<Double,Double>> randXY=this.generatePersonSpecificRandomNumber();
			PopulationFactory popFac = PopulationUtils.getFactory();
			Person person=popFac.createPerson(Id.createPersonId(this.getId().toString()+"_"+j));
			Vehicle vehicle= VehicleUtils.getFactory().createVehicle(Id.createVehicleId(person.getId().toString()), this.vehicleType);
			Plan plan = PopulationUtils.createPlan();
			ArrayList<Activity> activities=new ArrayList<>();
			ArrayList<Leg> tripLegs=new ArrayList<>();
			ArrayList<Double> tripOrder=new ArrayList<>(this.trips.keySet());
			Collections.sort(tripOrder);
			int i = 0;
			for(double tOrder:tripOrder) {
				FreightTrip trip=this.trips.get(tOrder);
				if(trip.getOtpusb()!=null && trip.getDtpusb()!=null) {
					Coord ocoord=new Coord(trip.getOtpusb().getSatCoord().getX()+randXY.get(trip.getOtpusb().getTPUSBId()).getFirst(),
							trip.getOtpusb().getSatCoord().getY()+randXY.get(trip.getOtpusb().getTPUSBId()).getSecond());
					Coord dcoord=new Coord(trip.getDtpusb().getSatCoord().getX()+randXY.get(trip.getDtpusb().getTPUSBId()).getFirst(),
							trip.getDtpusb().getSatCoord().getY()+randXY.get(trip.getDtpusb().getTPUSBId()).getSecond());
					Activity oact=popFac.createActivityFromCoord(activityDetails.get((Double)trip.getLandUseOrigin()),ocoord);
					oact.setEndTime(trip.getDepartureTime()*24*3600);
					if(i==0) {
						activities.add(oact);
					}else {
						activities.get(i).setEndTime(trip.getDepartureTime()*24*3600);
						if(!oact.getCoord().equals(activities.get(i).getCoord())) {
							oact.setStartTime(trip.getDepartureTime()*24*3600);
							activities.add(oact);
							i++;

							Leg legDummy=popFac.createLeg("car");
							legDummy.setDepartureTime(trip.getDepartureTime()*24*3600);
							legDummy.setTravelTime(0);
							tripLegs.add(legDummy);

							//throw new IllegalArgumentException("Discontinuous Trip Chain!!!");
							//lets think about it later.
						}
					}
					//Create the destination trip
					Activity dact=popFac.createActivityFromCoord(activityDetails.get((Double)trip.getLandUseDestination()),dcoord);
					dact.setStartTime(trip.getArrivalTime()*24*3600);
					activities.add(dact);
					
					Leg leg=popFac.createLeg("car");
					leg.setDepartureTime(trip.getDepartureTime()*24*3600);
					leg.setTravelTime(trip.getArrivalTime()*24*3600-trip.getDepartureTime()*24*3600);
					tripLegs.add(leg);
					i++;
				}
				
			}
			if(activities.size()!=0) {
			plan.addActivity(activities.get(0));
			for(int k=0;k<activities.size()-1;k++) {
				plan.addLeg(tripLegs.get(k));
				plan.addActivity(activities.get(k+1));
				
			}
			person.addPlan(plan);
			PopGenUtils.checkPlanValidity(plan, person.getId().toString());
			personList.add(new Tuple<Person,Vehicle>(person,vehicle));
			}
		}
		return personList;
	}
	private double getMinimumVehicleWeight() {
		double minWeight=this.vehicleWeight;
		for(FreightTrip trip:this.trips.values()) {
			if(trip.getTripWeight()<minWeight) {
				minWeight=trip.getTripWeight();
			}
		}
		return minWeight;
	}

	public ArrayList<Tuple<Person,Vehicle>> getClonedVehicleAndPersons(HashMap<Double,String>activityDetails){
		ArrayList<Tuple<Person,Vehicle>> personsAndVehicles=new ArrayList<>();
		if(this.trips.size()==0) {
			return personsAndVehicles;
		}
		personsAndVehicles.addAll(this.getMinWeightPersonAndVehicle(activityDetails));
		for(FreightTrip trip:this.trips.values()) {
			personsAndVehicles.addAll(this.getTripPersonAndVehicle(trip, trip.getTripWeight()-this.getMinimumVehicleWeight(),activityDetails));
		}
		
		return personsAndVehicles;
	}
	
	public void loadClonedVehicleAndPersons(Population population, Vehicles vehicles, 
			HashMap<Double,String>activityDetails, String personGroupName, String tripGroupName, 
			Double tripPerson, Double personPerson){
		//For the whole GV
		ArrayList<Tuple<Person,Vehicle>> personsAndVehiclessub1 = this.getMinWeightPersonAndVehicle(activityDetails);
		for(Tuple<Person,Vehicle> t:personsAndVehiclessub1) {
			if(!vehicles.getVehicleTypes().containsKey(t.getSecond().getType().getId())) {
				vehicles.addVehicleType(t.getSecond().getType());
			}
			population.addPerson(t.getFirst());
			t.getFirst().getAttributes().putAttribute("SUBPOP_ATTRIB_NAME", personGroupName+"_GV");
			vehicles.addVehicle(t.getSecond());
		}
		personPerson+=personsAndVehiclessub1.size();
		
		//For the trips GV
		ArrayList<Tuple<Person,Vehicle>> personsAndVehiclessub2=new ArrayList<>();
		for(FreightTrip trip:this.trips.values()) {
			personsAndVehiclessub2.addAll(this.getTripPersonAndVehicle(trip, trip.getTripWeight()-this.getMinimumVehicleWeight(),activityDetails));
		}
		for(Tuple<Person,Vehicle> t:personsAndVehiclessub2) {
			if(!vehicles.getVehicleTypes().containsKey(t.getSecond().getType().getId())) {
				vehicles.addVehicleType(t.getSecond().getType());
			}
			
			population.addPerson(t.getFirst());
			t.getFirst().getAttributes().putAttribute("SUBPOP_ATTRIB_NAME", tripGroupName+"_GV");
			vehicles.addVehicle(t.getSecond());
		}
		tripPerson+=personsAndVehiclessub2.size();
	}
	
	/**
	 * This will create vehicle
	 * @return
	 */
	public VehicleType getType() {
		return vehicleType;
	}


	public double getVehicleWeight() {
		return vehicleWeight;
	}

	public HashMap<Double, FreightTrip> getTrips() {
		return trips;
	}

	public double getAgeOfgoodsVehicle() {
		return ageOfgoodsVehicle;
	}

	public double getYearOfPurchase() {
		return yearOfPurchase;
	}

	public double getPurchasingPrice() {
		return purchasingPrice;
	}

	public double getAverageFuelCost() {
		return averageFuelCost;
	}

	public double getRouteCoiceCriteria() {
		return routeCoiceCriteria;
	}

	public List<Person> getPersons() {
		return Persons;
	}
	@Override
	public Id<Vehicle> getId() {
		return this.vehicleId;
	}

	/**
	 * This will clone vehicle 
	 * @return
	 */
	public GoodsVehicle cloneVehicle(String addedId) {
		
		GoodsVehicle newVehicle=null;
		if(this.crossHerbourTrip) {
			newVehicle=new GoodsVehicle(this.vehicleType,this.vehicleId.toString()+addedId,this.vehicleWeight,this.ageOfgoodsVehicle,this.yearOfPurchase,this.purchasingPrice,
					this.averageFuelCost,this.routeCoiceCriteria,this.unitofAverageFuelCost,this.yearlyMaintainenceCost,1.,new Double(this.crossHerbourTunnelUsed),
					new Double(this.timeAcrossCrossHerbourTunnel),new Double(this.tripPurposeCrossHerbourTunnel),new String(this.crossBoundaryControlPoint));
		}else {
			newVehicle=new GoodsVehicle(this.vehicleType,this.vehicleId.toString()+addedId,this.vehicleWeight,this.ageOfgoodsVehicle,this.yearOfPurchase,this.purchasingPrice,
					this.averageFuelCost,this.routeCoiceCriteria,this.unitofAverageFuelCost,this.yearlyMaintainenceCost,2.,null,null,null,null);
		}
		
		for(FreightTrip trip:this.trips.values()) {
			newVehicle.addTrip(trip.cloneTrip());
			
		}
		return newVehicle;
	}

	public static Plan clonePlan(Plan plan,PopulationFactory popfac) {
		Plan clonedPlan=popfac.createPlan();
		for(PlanElement pe:plan.getPlanElements()) {
			if(pe instanceof Activity) {
				Activity act=popfac.createActivityFromCoord(new String(((Activity)pe).getType()), new Coord(((Activity)pe).getCoord().getX(),((Activity)pe).getCoord().getY()));
				act.setStartTime(((Activity)pe).getStartTime().seconds());
				act.setEndTime(((Activity)pe).getEndTime().seconds());
				clonedPlan.addActivity(act);
			}else {
				Leg leg=popfac.createLeg(new String(((Leg)pe).getMode()));
				leg.setDepartureTime(((Leg)pe).getDepartureTime().seconds());
				leg.setTravelTime(((Leg)pe).getTravelTime().seconds());
				clonedPlan.addLeg(leg);
			}
		}
		return clonedPlan;
	}
	private HashMap<Id<TPUSB>,Tuple<Double,Double>> generatePersonSpecificRandomNumber() {
		HashMap<Id<TPUSB>,Tuple<Double,Double>> tpusbSpecificRandomXYPair=new HashMap<>();
		for(FreightTrip trip:this.trips.values()) {
			TPUSB otpusb=trip.getOtpusb();
			TPUSB dtpusb=trip.getDtpusb();
			if(otpusb!=null && dtpusb!=null) {
				if(!tpusbSpecificRandomXYPair.containsKey(otpusb.getTPUSBId())) {
					double randx=(Math.random()*otpusb.getHalfLength()-Math.random()*otpusb.getHalfLength())*3.14/4;
					//3.14/4 is taken to convert the square assumption to circle assumption while calculating the half length
					double randy=(Math.random()*otpusb.getHalfLength()-Math.random()*otpusb.getHalfLength())*3.14/4;
					tpusbSpecificRandomXYPair.put(otpusb.getTPUSBId(), new Tuple<>(randx,randy));
				}
				if(!tpusbSpecificRandomXYPair.containsKey(dtpusb.getTPUSBId())) {
					double randx=(Math.random()*dtpusb.getHalfLength()-Math.random()*dtpusb.getHalfLength())*3.14/4;
					//3.14/4 is taken to convert the square assumption to circle assumption while calculating the half length
					double randy=(Math.random()*dtpusb.getHalfLength()-Math.random()*dtpusb.getHalfLength())*3.14/4;
					tpusbSpecificRandomXYPair.put(dtpusb.getTPUSBId(), new Tuple<>(randx,randy));
				}
			}
		}
		
		return tpusbSpecificRandomXYPair;
	}
	private HashMap<Id<TPUSB>,Tuple<Double,Double>> generateTripSpecificRandomNumber(FreightTrip trip){
		HashMap<Id<TPUSB>,Tuple<Double,Double>> tpusbSpecificRandomXYPair=new HashMap<>();
		TPUSB otpusb=trip.getOtpusb();
		TPUSB dtpusb=trip.getDtpusb();
		
		double randx=(Math.random()*otpusb.getHalfLength()-Math.random()*otpusb.getHalfLength())*3.14/4;
		//3.14/4 is taken to convert the square assumption to circle assumption while calculating the half length
		double randy=(Math.random()*otpusb.getHalfLength()-Math.random()*otpusb.getHalfLength())*3.14/4;
		tpusbSpecificRandomXYPair.put(otpusb.getTPUSBId(), new Tuple<>(randx,randy));


		double drandx=(Math.random()*dtpusb.getHalfLength()-Math.random()*dtpusb.getHalfLength())*3.14/4;
		//3.14/4 is taken to convert the square assumption to circle assumption while calculating the half length
		double drandy=(Math.random()*dtpusb.getHalfLength()-Math.random()*dtpusb.getHalfLength())*3.14/4;
		tpusbSpecificRandomXYPair.put(dtpusb.getTPUSBId(), new Tuple<>(drandx,drandy));

		
		return tpusbSpecificRandomXYPair;
	}

	@Override
	public Attributes getAttributes() {
		throw new NotImplementedException("The attributes function is not implemented!");
	}
}
