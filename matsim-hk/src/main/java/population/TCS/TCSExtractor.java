package population.TCS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.Vehicles;

import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

import population.PopGenUtils;
import population.TPUSB;

public class TCSExtractor {
	
	private static final double weightFactor=0.5;
	private static final TPUSB CHT = new TPUSB("CHT",new Coord(36662.2,18205.8),0,0,null,100.);
	private static final TPUSB EHC = new TPUSB("EHC",new Coord(42051.36,17985.4),0,0,null,100.);
	private static final TPUSB WHC = new TPUSB("WHC",new Coord(34436.52,18383.08),0,0,null,100.);
	
	private static final TPUSB YauTong = new TPUSB("YauTong",new Coord(42512,17637),0,0,null,100.);
	private static final TPUSB Kowloon = new TPUSB("Kowloon",new Coord(34704,18472),0,0,null,100.);
	private static final TPUSB TST = new TPUSB("TST",new Coord(35763,17644),0,0,null,100.);
	private static final TPUSB KwunTongFerry = new TPUSB("KT_Ferry",new Coord(34436.52,18383.08),0,0,null,100.);
	private static final TPUSB StarFerry = new TPUSB("Star_Ferry",new Coord(34436.52,18383.08),0,0,null,100.);
	
	/**
	 * 
	 * @param population
	 * @param vehicles
	 * @param tripPerson
	 * @param personPerson
	 * @param weight
	 * @return The activity detail of the TCS
	 * @throws IOException
	 */
	public static HashMap<Double,String> fillPopulationAndVehicleByTCS(Population population, 
			Vehicles vehicles, Double tripPerson, Double personPerson, double weight, boolean hkiOnly) throws IOException {
		HashMap<Id<TPUSB>,TPUSB> tpusbs = PopGenUtils.getTPUSB();
		
		HashMap<Double,String> activityDetails=new HashMap<>();
		HashMap<Double,TCSMode> modesDetails=new HashMap<>();
		String activityFileLoc = PopGenUtils.manuelPath + "ActivityManual.csv";
		String modeFileLoc = PopGenUtils.manuelPath + "ModeManual.csv";
		
		readModeAndActivityTypeManual(activityFileLoc, modeFileLoc, activityDetails, modesDetails);
		
		Database tcsDatabase=DatabaseBuilder.open(new File(PopGenUtils.TCSFilePath));
		Table HH=tcsDatabase.getTable("HH");
		Table HM=tcsDatabase.getTable("HM");
		Table TP=tcsDatabase.getTable("TP");
		
		HashMap<Id<HouseHold>,HouseHold> houseHolds = createHouseHolds(HH,tpusbs, weight);
		HashMap<Id<HouseHoldMember>,HouseHoldMember> members = createMember(HM,TP,tpusbs,houseHolds, weight);
		
		if(hkiOnly) extractHKITrip(members);
		
		for(HouseHoldMember hm : members.values()) {
			hm.loadClonedVehicleAndPersons(population, vehicles, activityDetails, modesDetails, 
					"person", "trip", tripPerson, personPerson);
		}
		return activityDetails;

	}
	
	public static void main(String[] args) throws IOException {
		Double tripPerson=0.;
		Double personPerson=0.;

		//HKITripExtractor(members);
		
		Config config=ConfigUtils.createConfig();
		Scenario scenario=ScenarioUtils.createScenario(config);
		
		Population population=scenario.getPopulation();
		Vehicles vehicles=scenario.getVehicles();
		HashMap<Double,String> activityDetails = fillPopulationAndVehicleByTCS(population, vehicles, 
				tripPerson, personPerson, weightFactor, false);
		for(String s:activityDetails.values()) {
			PopGenUtils.addActivityPlanParameter(config.planCalcScore(),s,80*60);
		}
		
		new ConfigWriter(config).write("TCS/TCSConfig.xml");
		new PopulationWriter(population).write("TCS/TCSpopulation.xml");
		new VehicleWriterV1(vehicles).writeFile("TCS/TCSvehicles.xml");
		//new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes()).writeFile("TCS/TCSPersonAttributes.xml");
		
		System.out.println("TestLine");
	}
	
	public static HashMap<Id<HouseHold>,HouseHold> createHouseHolds(Table HH,HashMap<Id<TPUSB>,TPUSB> tpusbs,double weightFactor){
		HashMap<Id<HouseHold>,HouseHold> houseHolds=new HashMap<>();
		for(Row row:HH) {
			HouseHold houseHold=new HouseHold((double)row.get("Q_NO"),(double)row.get("A1"),
					(double)row.get("A3"),(double)row.get("A7"),(double)row.get("DC"),(double)row.get("TYPE"),
					(double)row.get("AS"),(double)row.get("B15"),tpusbs.get(Id.create(Double.toString((double)row.get("HH_TPUSB")),TPUSB.class)),
					(double)row.get("WT_HH")*weightFactor);
			houseHolds.put(houseHold.getHouseHoldId(), houseHold);
		}
		return houseHolds;
	}

	public static HashMap<Id<HouseHoldMember>,HouseHoldMember> createMember(Table HM, Table TP, HashMap<Id<TPUSB>, TPUSB> tpusbs, 
																			HashMap<Id<HouseHold>, HouseHold> houseHolds, double weightFactor){
		HashMap<Id<HouseHoldMember>,HouseHoldMember> members=new HashMap<>();
		
		for(Row row:HM) {
			HouseHoldMember member=new HouseHoldMember((double)row.get("Q_NO"),(double)row.get("MEM"),(double)row.get("A4"),
					(double)row.get("B1"),(double)row.get("B2"),(Double)row.get("B3"),(Double)row.get("B7"),(Double)row.get("B8"),
					(double)row.get("B15"),(Double)row.get("D1"),(Double)row.get("D10"),(Double)row.get("D10A"),(Double)row.get("G1"),
					(Double)row.get("G2"),(Double)row.get("G3"),tpusbs.get(Id.create(Double.toString((double)row.get("HH_TPUSB")),TPUSB.class)),
					(double)row.get("WT_MEM")*weightFactor,(double)row.get("UR_MR"),(double)row.get("E_Status"));
			members.put(member.getMemberId(), member);
			houseHolds.get(member.getHouseHoldId()).addMember(member);
		}
		
		for(Row row:TP) {
			TCSTrip trip=new TCSTrip((double)row.get("Q_NO"),(double)row.get("MEM"),(double)row.get("Trip_no"),(double)row.get("D5_code"),
					(double)row.get("D6_code"),tpusbs.get(Id.create(Double.toString((double)row.get("D5")),TPUSB.class)),
					tpusbs.get(Id.create(Double.toString((double)row.get("D6")),TPUSB.class)),(double)row.get("D7"),(double)row.get("D8"),
					(double)row.get("Pur"),(double)row.get("Mode_Hier"),(double)row.get("TiPer"),(double)row.get("WT_TRIP")*weightFactor);
			for(int i=1; i<=9; i=i+2) { //Process the course of a mechanized trip.
				boolean shouldBreak=false;
				TPUSB otpusb;
				TPUSB dtpusb;
				Double boardingMtr;
				Double boardingAirMtr;
				Double allightingMtr;
				Double allightingAirportMtr;
				
				if(i==1) {
					otpusb=tpusbs.get(Id.create(Double.toString((double)row.get("D5")),TPUSB.class));
					dtpusb=tpusbs.get(Id.create(Double.toString((double)row.get("D9T"+(i+1)+"A")),TPUSB.class));
					boardingMtr=(Double)row.get("D9T"+(i+1)+"B");
					boardingAirMtr=(Double)row.get("D9T"+(i+1)+"C");
					allightingMtr=null;
					allightingAirportMtr =null;
				}else if(i==9) {
					otpusb=tpusbs.get(Id.create(Double.toString((double)row.get("D9T"+(i)+"A")),TPUSB.class));
					dtpusb=tpusbs.get(Id.create(Double.toString((double)row.get("D6")),TPUSB.class));
					boardingMtr=null;
					boardingAirMtr=null;
					allightingMtr=(Double)row.get("D9T"+i+"B");
					allightingAirportMtr =(Double)row.get("D9T"+i+"C");
				}else if((Double)row.get("D9T"+(i+1))==null){
					otpusb=tpusbs.get(Id.create(Double.toString((double)row.get("D9T"+(i)+"A")),TPUSB.class));
					dtpusb=tpusbs.get(Id.create(Double.toString((double)row.get("D6")),TPUSB.class));
					boardingMtr=null;
					boardingAirMtr=null;
					allightingMtr=(Double)row.get("D9T"+i+"B");
					allightingAirportMtr =(Double)row.get("D9T"+i+"C");
					shouldBreak=true;
				}else {
					otpusb=tpusbs.get(Id.create(Double.toString((double)row.get("D9T"+(i)+"A")),TPUSB.class));
					dtpusb=tpusbs.get(Id.create(Double.toString((double)row.get("D9T"+(i+1)+"A")),TPUSB.class));
					boardingMtr=(Double)row.get("D9T"+(i+1)+"B");
					boardingAirMtr=(Double)row.get("D9T"+(i+1)+"C");
					allightingMtr=(Double)row.get("D9T"+i+"B");
					allightingAirportMtr =(Double)row.get("D9T"+i+"C");
				}
				WalkTripLeg walkTrip=new WalkTripLeg(i,(double)row.get("D9T"+i),otpusb,dtpusb,boardingMtr,boardingAirMtr,allightingMtr,allightingAirportMtr);
				for(int j=1;j<=5;j++) {
					
					if(i<=2) {
						if((Double)row.get("D9T"+i+"_"+3+"P"+j)==null) {break;}
						walkTrip.addMTRInterchange((Double)row.get("D9T"+i+"_"+3+"P"+j));
					}else {
						if((Double)row.get("D9T"+i+"_"+i+"P"+j)==null) {break;}
						walkTrip.addMTRInterchange((Double)row.get("D9T"+i+"_"+i+"P"+j));
					}
				}
				trip.addWalkTripLeg(walkTrip);
				if(shouldBreak) {break;}
				
				
			}
			
			
			for(int i=2;i<9;i=i+2) {
				TPUSB otpusb;
				TPUSB dtpusb;
				Double boardingMtr;
				Double boardingAirMtr;
				Double allightingMtr;
				Double allightingAirportMtr;
				Double mode=(Double)row.get("D9T"+i);
				if(mode!=null) {
					otpusb=tpusbs.get(Id.create(Double.toString((double)row.get("D9T"+(i)+"A")),TPUSB.class));
					dtpusb=tpusbs.get(Id.create(Double.toString((double)row.get("D9T"+(i+1)+"A")),TPUSB.class));
					boardingMtr=(Double)row.get("D9T"+(i)+"B");
					boardingAirMtr=(Double)row.get("D9T"+(i)+"C");
					allightingMtr=(Double)row.get("D9T"+(i+1)+"B");
					allightingAirportMtr =(Double)row.get("D9T"+(i+1)+"C");
					MechanaisedTripLeg tripLeg=new MechanaisedTripLeg(i,(double)row.get("D9T"+i),otpusb,dtpusb,boardingMtr,boardingAirMtr,allightingMtr,allightingAirportMtr);
				
					for(int j=1;j<=5;j++) {
						
						if(i<=2) {
							if((Double)row.get("D9T"+i+"_"+3+"P"+j)==null) {break;}
							tripLeg.addMTRInterchange((Double)row.get("D9T"+i+"_"+3+"P"+j));
						}else {
							if((Double)row.get("D9T"+i+"_"+i+"P"+j)==null) {break;}
							tripLeg.addMTRInterchange((Double)row.get("D9T"+i+"_"+i+"P"+j));
						}
					}
					
					trip.addTripLeg(tripLeg);
					
					if((Double)row.get("E1T"+i)!=null) {
						TaxiTripLeg taxiLeg=new TaxiTripLeg(i,(double)row.get("D9T"+i),otpusb,dtpusb,boardingMtr,boardingAirMtr,allightingMtr,allightingAirportMtr,
								(Double)row.get("E6T"+i),(Double)row.get("E3T"+i),(Double)row.get("E1T"+i),(Double)row.get("E2T"+i));
						for(int j=1;j<=3;j++) {
							if((Double)row.get("E5T"+i+"P"+j)!=null) {
								taxiLeg.addTollRoadUsed((Double)row.get("E5T"+i+"P"+j));
							}
							
						}
						trip.addTaxiTripleg(taxiLeg);
					}
					
					if((Double)row.get("F1T"+i)!=null) {
						CarTripLeg carLeg=new CarTripLeg(i,(double)row.get("D9T"+i),otpusb,dtpusb,boardingMtr,boardingAirMtr,allightingMtr,allightingAirportMtr,
								(Double)row.get("F5AT"+i),(Double)row.get("F4T"+i),(double)row.get("F1T"+i));
						for(int j=1;j<=3;j++) {
							if((Double)row.get("F3T"+i+"P"+j)!=null) {
								carLeg.addTollRoadUsed((Double)row.get("F3T"+i+"P"+j));
							}
							
						}
						trip.addCarTripLeg(carLeg);
					}
					
				}else {
					break;
				}
			}
			members.get(trip.getMemberId()).addTrip(trip);
		}
		return members;
	}
	
	private static void readModeAndActivityTypeManual(String activityfileLoc,String modeFileLoc,HashMap<Double, String> activityDetails,HashMap<Double,TCSMode> modesDetails) {
		try {
			BufferedReader activitybuff=new BufferedReader(new FileReader(new File(activityfileLoc)));
			BufferedReader modebuff=new BufferedReader(new FileReader(new File(modeFileLoc)));
			modebuff.readLine();
			activitybuff.readLine();
			String activityLine;
			String modeLine;
			while((activityLine=activitybuff.readLine())!=null) {
				String[] part=activityLine.split(",");
				activityDetails.put(Double.parseDouble(part[0].trim()),part[1].trim());
			}
			activitybuff.close();
			while((modeLine=modebuff.readLine())!=null) {
				String[] par=modeLine.split(",");
				String[] part=new String[4];
				for(int i=0;i<4;i++) {
					if(i>=par.length) {
						part[i]=null;
					}else {
						part[i]=par[i].trim();
					}
				}
				TCSMode modeDetails;
				if(part[3]!=null) {
					modeDetails=new TCSMode(Double.parseDouble(part[0]),part[1],part[2],Double.parseDouble(part[3]));
				}else {
					modeDetails=new TCSMode(Double.parseDouble(part[0]),part[1],part[2],null);
				}
				modesDetails.put(modeDetails.getModeId(), modeDetails);
			}
			modebuff.close();
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Find the nearest tunnel to that point
	 * @param tl
	 * @param origin
	 * @return 1: CHT, 2: EHC, 3: WHC, 4: Yau Tong Station 5:Kowloon Station 6: Tsim Sha Tsui Station 0: Not found
	 */
	private static int findNearestTunnel(TripLeg tl, boolean origin) {
		if((tl.getMode()<7 || tl.getMode()>9)) {
			if( tl.getTunnelUsed()!=null && tl.getTunnelUsed()==1) {
				return 1;
			}else if(tl.getTunnelUsed()!=null && tl.getTunnelUsed()==2) {
				return 2;
			}else if(tl.getTunnelUsed()!=null && tl.getTunnelUsed()==3) {
				return 3;
			}else {
				ArrayList<Double>distance=new ArrayList<>();
				if(origin) {
					distance.add(NetworkUtils.getEuclideanDistance(tl.getOriginTPUSB().getSatCoord(),CHT.getSatCoord()));
					distance.add(NetworkUtils.getEuclideanDistance(tl.getOriginTPUSB().getSatCoord(),EHC.getSatCoord()));
					distance.add(NetworkUtils.getEuclideanDistance(tl.getOriginTPUSB().getSatCoord(),WHC.getSatCoord()));
				}else {
					distance.add(NetworkUtils.getEuclideanDistance(tl.getDestinationTPUSB().getSatCoord(),CHT.getSatCoord()));
					distance.add(NetworkUtils.getEuclideanDistance(tl.getDestinationTPUSB().getSatCoord(),EHC.getSatCoord()));
					distance.add(NetworkUtils.getEuclideanDistance(tl.getDestinationTPUSB().getSatCoord(),WHC.getSatCoord()));
				}
				
				if(distance.get(0)<distance.get(1) &&distance.get(0)<distance.get(2)) {
					return 1;
				}else if(distance.get(1)<distance.get(2) &&distance.get(1)<distance.get(0)) {
					return 2;
				}else {
					return 3;
				}
			}
		}else if(tl.getMode()==7 || tl.getMode() == 8){ //MTR heavy rail and AEL
			ArrayList<Double>distance=new ArrayList<>();
			if(origin) {
				distance.add(NetworkUtils.getEuclideanDistance(tl.getOriginTPUSB().getSatCoord(),YauTong.getSatCoord()));
				distance.add(NetworkUtils.getEuclideanDistance(tl.getOriginTPUSB().getSatCoord(),Kowloon.getSatCoord()));
				distance.add(NetworkUtils.getEuclideanDistance(tl.getOriginTPUSB().getSatCoord(),TST.getSatCoord()));
			}else {
				distance.add(NetworkUtils.getEuclideanDistance(tl.getDestinationTPUSB().getSatCoord(),YauTong.getSatCoord()));
				distance.add(NetworkUtils.getEuclideanDistance(tl.getDestinationTPUSB().getSatCoord(),Kowloon.getSatCoord()));
				distance.add(NetworkUtils.getEuclideanDistance(tl.getDestinationTPUSB().getSatCoord(),TST.getSatCoord()));
			}
			if(distance.get(0)<distance.get(1) &&distance.get(0)<distance.get(2)) {
				return 4;
			}else if(distance.get(1)<distance.get(2) &&distance.get(1)<distance.get(0)) {
				return 5;
			}else {
				return 6;
			}
		}
		throw new IllegalArgumentException("They should not take LRT!");
	}
	
	/**
	 * Read the HouseHoldMember map, and remove all trips that is not HKI.
	 * Also, put the cross harbour trip to the tunnel.
	 * @param members
	 */
	private static void extractHKITrip(HashMap<Id<HouseHoldMember>, HouseHoldMember> members) {
		for(HouseHoldMember hm:members.values()) {			
			Iterator<Entry<Double,TCSTrip>> iterTrip = hm.getTrips().entrySet().iterator();
			while(iterTrip.hasNext()) {
				Entry<Double,TCSTrip> et = iterTrip.next(); //Look for another entry
				TCSTrip trip = et.getValue(); //Get the trip data
				trip.calcTunnelUsed();
				Iterator<Entry<Integer,TripLeg>> it = trip.getTripLegs().entrySet().iterator();
				while(it.hasNext()) {
					Entry<Integer,TripLeg> e=it.next();
					TripLeg tl=e.getValue();
					//From outside HKI to HKI
					if(tl.getOriginTPUSB().getDistrict26Id()>4 && tl.getDestinationTPUSB().getDistrict26Id()<=4) {
						switch( findNearestTunnel(tl, true) ) {
							case 1: tl.setOriginTPUSB(CHT); break;
							case 2: tl.setOriginTPUSB(EHC); break;
							case 3: tl.setOriginTPUSB(WHC); break;
							case 4: tl.setOriginTPUSB(YauTong); break;
							case 5: tl.setOriginTPUSB(Kowloon); break;
							case 6: tl.setOriginTPUSB(TST); break;
						}
					//From HKI to outside HKI
					}else if(tl.getDestinationTPUSB().getDistrict26Id()>4 && tl.getOriginTPUSB().getDistrict26Id()<=4){
						switch( findNearestTunnel(tl, false) ) {
							case 1: tl.setDestinationTPUSB(CHT); break;
							case 2: tl.setDestinationTPUSB(EHC); break;
							case 3: tl.setDestinationTPUSB(WHC); break;
							case 4: tl.setDestinationTPUSB(YauTong); break;
							case 5: tl.setDestinationTPUSB(Kowloon); break;
							case 6: tl.setDestinationTPUSB(TST); break;
						}
					//Whole outside HKI
					}else if(tl.getOriginTPUSB().getDistrict26Id()>4 && tl.getDestinationTPUSB().getDistrict26Id()>4) {
						it.remove();
					}
				}
				if(!trip.getTripLegs().isEmpty()) {
					ArrayList<Integer> tripOrders=new ArrayList<>(trip.getTripLegs().keySet());
					Collections.sort(tripOrders);
					trip.setOtpusb(trip.getTripLegs().get(tripOrders.get(0)).getOriginTPUSB());
					trip.setDtpusb(trip.getTripLegs().get(tripOrders.get(tripOrders.size()-1)).getDestinationTPUSB());
					boolean onlyWalk=true;
					for(TripLeg tl:trip.getTripLegs().values()) {
						if(!(tl instanceof WalkTripLeg)) {
							onlyWalk=false;
						}
					}
					if(onlyWalk) {
						iterTrip.remove();
					}
				}else {
					iterTrip.remove();
				}
			}
		}
	}
}
