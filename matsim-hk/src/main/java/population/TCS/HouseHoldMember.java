package population.TCS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import com.google.common.collect.Lists;

import population.PopGenUtils;
import population.TPUSB;

/**
 * 
 * @author Ashraf
 *
 */
public class HouseHoldMember {
/**
 * This Class will represent the household member as provided in TCS database 2011
 */
	
	private Id<HouseHoldMember> HmId;
	private Id<HouseHold> HhId;
	private double relation;
	private double age;
	private double sex;
	private Double haveJob;
	private Double currentLevelofStudy;
	private Double haveLisence;
	private double monthlyHouseHoldIncome;
	private Double referenceTravelDay;
	private Double totalFeeofSchoolBus;
	private Double isOneWay;
	private Double enteredFromOverseas;
	private Double controlPointUsed;
	private Double destinationAfterEnteringHK;
	private TPUSB addressTPUSB;
	private double hmExpansionFactor;
	private double usualOrMobileResident;
	private double econominStatus;
	private HashMap<Double,TCSTrip> trips=new HashMap<>(); //A map of departure time and trip
	private ArrayList<Id<Person>> personsWithCar=new ArrayList<>();
	private ArrayList<Id<Person>> personsWithoutCar=new ArrayList<>();
	
	
	public HouseHoldMember(double qNo,double memberNo,double relation, double sex, double age,Double haveJob, Double levelOfStudy,
			Double haveLisence, double MonthlyIncome,Double refTravelDay,Double feeOfShuttle, Double isOneWay,Double isFromOverseas,
			Double controlPoint, Double destinationHk, TPUSB address,double hmExpansionFactor,double usualormobileResidence, double EconStatus) {
		this.HmId=Id.create(Double.toString(qNo)+"_"+Double.toString(memberNo), HouseHoldMember.class);
		this.HhId=Id.create(Double.toString(qNo), HouseHold.class);
		this.relation=relation;
		this.age=age;
		this.sex=sex;
		this.haveJob=haveJob;
		this.currentLevelofStudy=levelOfStudy;
		this.haveLisence=haveLisence;
		this.monthlyHouseHoldIncome=MonthlyIncome;
		this.referenceTravelDay=refTravelDay;
		this.totalFeeofSchoolBus=feeOfShuttle;
		this.isOneWay=isOneWay;
		this.enteredFromOverseas=isFromOverseas;
		this.controlPointUsed=controlPoint;
		this.destinationAfterEnteringHK=destinationHk;
		this.addressTPUSB=address;
		this.hmExpansionFactor=hmExpansionFactor;
		this.usualOrMobileResident=usualormobileResidence;
		this.econominStatus=EconStatus;
		
	}
	public HouseHoldMember clone() {
		return new HouseHoldMember(Double.parseDouble((this.HmId.toString().split("_")[0])),Double.parseDouble((this.HmId.toString().split("_")[1])),
				this.relation,this.sex,this.age,this.haveJob,this.currentLevelofStudy,this.haveLisence,this.monthlyHouseHoldIncome,this.referenceTravelDay,
				this.totalFeeofSchoolBus,this.isOneWay,this.enteredFromOverseas,this.controlPointUsed,this.destinationAfterEnteringHK,this.addressTPUSB,
				this.hmExpansionFactor,this.usualOrMobileResident,this.econominStatus);
	}
	
	public void addTrip(TCSTrip trip) {
		this.trips.put(trip.getDepartureTime(), trip); //Sort by departure time.
	}
	public Id<HouseHoldMember> getMemberId() {
		return HmId;
	}
	public Id<HouseHold> getHouseHoldId() {
		return HhId;
	}
	public double getRelation() {
		return relation;
	}
	public double getAge() {
		return age;
	}
	public double getSex() {
		return sex;
	}
	public double getHaveJob() {
		return haveJob;
	}
	public double getCurrentLevelofStudy() {
		return currentLevelofStudy;
	}
	public double getHaveLisence() {
		return haveLisence;
	}
	public double getMonthlyHouseHoldIncome() {
		return monthlyHouseHoldIncome;
	}
	public double getReferenceTravelDay() {
		return referenceTravelDay;
	}
	public double getTotalFeeofSchoolBus() {
		return totalFeeofSchoolBus;
	}
	public double getIsOneWay() {
		return isOneWay;
	}
	public double getEnteredFromOverseas() {
		return enteredFromOverseas;
	}
	public double getControlPointUsed() {
		return controlPointUsed;
	}
	public double getDestinationAfterEnteringHK() {
		return destinationAfterEnteringHK;
	}
	public TPUSB getAddressTPUSB() {
		return addressTPUSB;
	}
	public double getHmExpansionFactor() {
		return hmExpansionFactor;
	}
	public double getUsualOrMobileResident() {
		return usualOrMobileResident;
	}
	public double getEconominStatus() {
		return econominStatus;
	}
	public HashMap<Double, TCSTrip> getTrips() {
		return trips;
	}
	
	/**
	 * Obtain the minimum trip weight across all trips
	 * @return
	 */
	private double getMinimumTripWeight() {
		//double minWeight=this.hmExpansionFactor;
		double minWeight=Double.MAX_VALUE;
		if(this.trips!=null && this.trips.size()!=0) {
			for(TCSTrip trip:this.trips.values()) {
				if(trip.getTripExpansionFactor()<minWeight) {
					minWeight=trip.getTripExpansionFactor();
				}
			}
		}
		return minWeight;
	}
	
	/**
	 * This function create the plan for the whole person.
	 * @param modesDetails
	 * @param activityDetails
	 * @param popfac
	 * @return
	 */
	private Plan createPlanFromTrips(HashMap<Double,TCSMode> modesDetails, HashMap<Double,String> activityDetails) {
		PopulationFactory popfac = PopulationUtils.getFactory();
		HashMap<Id<TPUSB>,Tuple<Double,Double>>randXY=this.generatePersonSpecificRandomNumber();
		ArrayList<Activity> activities=new ArrayList<>();
		ArrayList<Leg> activityConnectorTripLegs=new ArrayList<>();
		ArrayList<Double> tripOrder=new ArrayList<>(this.trips.keySet());
		Collections.sort(tripOrder);
		int i=0;
		for(double tOrder : tripOrder) { //Iterate through the order of trip (by time)
			TCSTrip trip=this.trips.get(tOrder);
			if(trip.getOtpusb()!=null && trip.getDtpusb()!=null) {
				//Initialize the origin and destination activities
				Coord ocoord=new Coord(trip.getOriginX()+randXY.get(trip.getOtpusb().getTPUSBId()).getFirst(),
						trip.getOriginY()+randXY.get(trip.getOtpusb().getTPUSBId()).getSecond());
				Coord dcoord=new Coord(trip.getDestinationX()+randXY.get(trip.getDtpusb().getTPUSBId()).getFirst(),
						trip.getDestinationY()+randXY.get(trip.getDtpusb().getTPUSBId()).getSecond());
				Activity oact=popfac.createActivityFromCoord(activityDetails.get(trip.getOriginActivity()),ocoord);
				oact.setEndTime(trip.getDepartureTime());
				if(i==0) { //In the first iteration, we simply put add the activities
					activities.add(oact);
				}else {
					activities.get(i).setEndTime(trip.getDepartureTime()); //The last activity is ends at departure time here.
					//If this original activity is not the same as the destination utility before, add a dummy activity between.
					if(!oact.getCoord().equals(activities.get(i).getCoord())) { 
						oact.setStartTime(trip.getDepartureTime());
						oact.setEndTime(trip.getDepartureTime());
						activities.add(oact);
						i++; //Add one index as one more activity is added

						Leg legDummy = popfac.createLeg("car"); //Add a dummy leg for connection.
						legDummy.setDepartureTime(trip.getDepartureTime());
						legDummy.setTravelTime(0);
						activityConnectorTripLegs.add(legDummy); 

						//throw new IllegalArgumentException("Discontinuous Trip Chain!!!");
						//lets think about it later.
					}
				}
				
				//Create the destination trip
				Activity dact=popfac.createActivityFromCoord(activityDetails.get(trip.getDestinationActivity()),dcoord);
				dact.setStartTime(trip.getArrivalTime());
				activities.add(dact); //Add the activity after all the handling stuffs.
				
				Leg leg = popfac.createLeg(trip.getMainMode(modesDetails).getFlatMode());
				leg.setDepartureTime(trip.getDepartureTime());
				leg.setTravelTime(trip.getArrivalTime()-trip.getDepartureTime());
				activityConnectorTripLegs.add(leg);
				i++;
			}
			
		}
		
		//Create the actual plan.
		Plan plan = PopulationUtils.createPlan();
		if(activities.size()!=0) {
			plan.addActivity(activities.get(0));
			for(int k=0;k<activities.size()-1;k++) {
				plan.addLeg(activityConnectorTripLegs.get(k));
				plan.addActivity(activities.get(k+1));
			}
		}
		return plan;
	}
	
	/**
	 * This function creates a list of Person and Vehicle, based on the minimumVehicleWeight
	 * @param modesDetails
	 * @param activityDetails
	 * @param populationFactory
	 * @param vehiclesFactory
	 * @return
	 */
	private ArrayList<Tuple<Person,Vehicle>> getMinWeightPersonAndVehicle(HashMap<Double,TCSMode> modesDetails,
			HashMap<Double,String> activityDetails){
		ArrayList<Tuple<Person,Vehicle>> personList=new ArrayList<>();
		
		Plan plan = createPlanFromTrips(modesDetails, activityDetails);
		PopGenUtils.checkPlanValidity(plan, this.getMemberId().toString());
		
		for(int j=0;j<this.getMinimumTripWeight();j++) {
			Person person = PopulationUtils.getFactory().createPerson(Id.createPersonId(this.getMemberId().toString()+"_"+j));
			Plan thisPlan = PopulationUtils.createPlan(person);
			PopulationUtils.copyFromTo(plan, thisPlan);
			Vehicle vehicle=this.createVehicle(modesDetails, "_"+j);
			if(vehicle.getType().getDescription().equals("taxi_car")) {
				this.personsWithoutCar.add(person.getId());
			}else{
				this.personsWithCar.add(person.getId());
			}
			
			person.addPlan(plan);
			personList.add(new Tuple<Person,Vehicle>(person,vehicle));
		}
		return personList;
	}
	
	public Vehicle createVehicle(HashMap<Double,TCSMode>modesDetails,String addedtoMemberId) {
		VehiclesFactory vf = VehicleUtils.getFactory();
		if(this.trips!=null && this.trips.size()!=0) {
			for(TCSTrip trip:this.trips.values()) {
				TCSMode mode=trip.getMainMode(modesDetails);
				if(mode.getFlatMode().equals("car")) {
					
					VehicleType vt = vf.createVehicleType(Id.create(Double.toString(mode.getModeId()), VehicleType.class));
					vt.setPcuEquivalents(mode.getPcu());
					vt.setDescription(mode.getModeDetails()+"_"+mode.getFlatMode());
					return vf.createVehicle(Id.createVehicleId(this.getMemberId().toString()+addedtoMemberId),vt);
				}
			}
		}
		//Create taxi type vehicle
		VehicleType taxi_type=vf.createVehicleType(Id.create(Double.toString(100), VehicleType.class));
		taxi_type.setPcuEquivalents(1);
		taxi_type.setDescription("taxi_car");
		return vf.createVehicle(Id.createVehicleId(this.getMemberId().toString()+addedtoMemberId),taxi_type);
	}

	private ArrayList<Tuple<Person,Vehicle>> getTripPersonAndVehicle(TCSTrip trip, double weight, 
			HashMap<Double,String> activityDetails, HashMap<Double,TCSMode> modesDetails) {
		ArrayList<Tuple<Person,Vehicle>> personList=new ArrayList<>();
		PopulationFactory popfac = PopulationUtils.getFactory();
		if(trip.getOtpusb()!=null && trip.getDtpusb()!=null) {
			for(int i=0;i<weight;i++) {
				HashMap<Id<TPUSB>,Tuple<Double,Double>>randXY=this.generateTripSpecificRandomNumber(trip);
				
				Person person=popfac.createPerson(Id.createPersonId(this.getMemberId().toString()+"_"+trip.getTripNo()+"_"+i));
				Plan plan=popfac.createPlan();
				
				Activity oAct=popfac.createActivityFromCoord(activityDetails.get(trip.getOriginActivity()), 
						new Coord(trip.getOriginX() + randXY.get(trip.getOtpusb().getTPUSBId()).getFirst(),
								trip.getOriginY() + randXY.get(trip.getOtpusb().getTPUSBId()).getSecond()));
				Activity dAct=popfac.createActivityFromCoord(activityDetails.get(trip.getDestinationActivity()), 
						new Coord(trip.getDestinationX() + randXY.get(trip.getDtpusb().getTPUSBId()).getFirst(),
								trip.getDestinationY() + randXY.get(trip.getDtpusb().getTPUSBId()).getSecond()));
				oAct.setEndTime(trip.getDepartureTime());
				dAct.setStartTime(trip.getArrivalTime());
				Leg leg=popfac.createLeg(trip.getMainMode(modesDetails).getFlatMode());
				leg.setDepartureTime(trip.getDepartureTime());
				leg.setTravelTime(trip.getArrivalTime()-trip.getDepartureTime());
				plan.addActivity(oAct);
				plan.addLeg(leg);
				plan.addActivity(dAct);
				person.addPlan(plan);
				Vehicle vehicle=this.createVehicle(modesDetails,"_"+trip.getTripNo()+"_"+i);
//				if(vehicle.getType().getDescription().equals("taxi_car")) {
//					this.personsWithoutCar.add(person.getId());
//				}else if(vehicle.getType().getDescription().equals("car")) {
//					this.personsWithCar.add(person.getId());
//				}
				personList.add(new Tuple<Person,Vehicle>(person,vehicle));
				if(i==500) { //At most 500 same trips
					break;
				}
			}
		}
		return personList;
	}
	
	public ArrayList<Tuple<Person,Vehicle>> getClonedVehicleAndPersons(HashMap<Double,String> activityDetails, 
			HashMap<Double,TCSMode> modesDetails){
		ArrayList<Tuple<Person,Vehicle>> personsAndVehicles=new ArrayList<>();
		if(this.trips.size()==0) {
			return personsAndVehicles;
		}
		personsAndVehicles.addAll(this.getMinWeightPersonAndVehicle(modesDetails,activityDetails));
		for(TCSTrip trip:this.trips.values()) {
			personsAndVehicles.addAll(this.getTripPersonAndVehicle(trip, trip.getTripExpansionFactor()-this.getMinimumTripWeight(),
					activityDetails, modesDetails));
		}
		
		return personsAndVehicles;
	}
	
	public void loadClonedVehicleAndPersons(Population population, Vehicles vehicles, HashMap<Double,String> activityDetails, 
			HashMap<Double,TCSMode> modesDetails, String personGroupName, String tripGroupName, 
			Double tripPerson, Double personPerson){
		if(this.trips.size()==0) {
			return;
		}
		
		//Get minimum weight
		List<Tuple<Person,Vehicle>> personsAndVehiclessub1 = this.getMinWeightPersonAndVehicle(modesDetails, activityDetails);
		for(Tuple<Person,Vehicle> t:personsAndVehiclessub1) {
			if(t.getSecond()!=null && !vehicles.getVehicleTypes().containsKey(t.getSecond().getType().getId())) {
				vehicles.addVehicleType(t.getSecond().getType()); //Add the respective vehicle type
			}
			
			population.addPerson(t.getFirst()); //Actually add the person
			if(this.personsWithCar.contains(t.getFirst().getId())) { //Add supopulation
				t.getFirst().getAttributes().putAttribute("SUBPOP_ATTRIB_NAME", personGroupName+"_TCSwithCar");
			}else {
				t.getFirst().getAttributes().putAttribute("SUBPOP_ATTRIB_NAME", personGroupName+"_TCSwithoutCar");
			}
			if(t.getSecond()!=null) {
				vehicles.addVehicle(t.getSecond()); //Add vehicle (taxi and car)
			}
		}
		personPerson+=personsAndVehiclessub1.size();
		
		//This part is for the trips
		ArrayList<Tuple<Person,Vehicle>> personsAndVehiclessub2 = Lists.newArrayList();
		for(TCSTrip trip:this.trips.values()) {
			personsAndVehiclessub2.addAll(this.getTripPersonAndVehicle(trip, 
					trip.getTripExpansionFactor()-this.getMinimumTripWeight(), activityDetails, modesDetails));
		}
		for(Tuple<Person,Vehicle> t:personsAndVehiclessub2) {
			if(t.getSecond()!=null && !vehicles.getVehicleTypes().containsKey(t.getSecond().getType().getId())) {
				vehicles.addVehicleType(t.getSecond().getType()); //Add the respective vehicle type
			}
			
			population.addPerson(t.getFirst()); //Actually add the person and subpopulation
			t.getFirst().getAttributes().putAttribute("SUBPOP_ATTRIB_NAME", tripGroupName+"_TCS");
			if(t.getSecond()!=null) {
				vehicles.addVehicle(t.getSecond()); //Add vehicle
			}
		}
		tripPerson+=personsAndVehiclessub2.size();
		//double ratio=personsAndVehiclessub1.size()/(personsAndVehiclessub1.size()+personsAndVehiclessub2.size());
	}
	
	private HashMap<Id<TPUSB>,Tuple<Double,Double>> generatePersonSpecificRandomNumber() {
		HashMap<Id<TPUSB>,Tuple<Double,Double>> tpusbSpecificRandomXYPair=new HashMap<>();
		for(TCSTrip trip:this.trips.values()) {
			TPUSB otpusb=trip.getOtpusb();
			TPUSB dtpusb=trip.getDtpusb();
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
		
		return tpusbSpecificRandomXYPair;
	}
	private HashMap<Id<TPUSB>,Tuple<Double,Double>> generateTripSpecificRandomNumber(TCSTrip trip){
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
}


