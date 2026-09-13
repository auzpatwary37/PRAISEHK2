package population.GVTCS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

import population.PopGenUtils;
import population.TPUSB;

public class GVTCSConverter {
/**
 * [The purpose for this class is to convert the trip information from GVTCS to Matsim Trip information 
 * The GVTCS has three trip databases
 * 
 * The weight based trip still is another problem
 * Connecting the database spatially is also another issue as the origin and destination code provided is most probably not TPUSB codes as the 454 PDZ zones do not match.
 * 
 * Have to confirm
 * Currently mapping to TPUSB] JAN 18
 * @throws IOException 
 * 
 * 
 * 
 */
	private static final double weightFactor=1.0;
	
	public static HashMap<Double, String> fillPopulationAndVehicleByGVTCS(Population population, 
			Vehicles vehicles, Double tripPerson, Double personPerson, double weight, boolean hkiOnly) throws IOException {
		HashMap<Id<TPUSB>,TPUSB> tpusbs = PopGenUtils.getTPUSB();
		Database gvtcsDatabase = DatabaseBuilder.open(new File(PopGenUtils.GVTCSFilePath));
		//Table govOwner=gvtcsDatabase.getTable("GOV-OWNER");
		Table govTrip=gvtcsDatabase.getTable("GOV-TRIP");
		Table govVehicle=gvtcsDatabase.getTable("GOV-VEH");
		//Table ngovOwner=gvtcsDatabase.getTable("NGOV-OWNER");
		Table ngovTrip=gvtcsDatabase.getTable("NGOV-TRIP");
		Table ngovVehicle=gvtcsDatabase.getTable("NGOV-VEH");
		//Table sectors=gvtcsDatabase.getTable("Sectors");
		//Table sgisTrip=gvtcsDatabase.getTable("SGIS-TRIP");
		
		BufferedReader bf=new BufferedReader(new FileReader(new File("data/GVTCS DATABASE/LandUseCode.csv")));
		bf.readLine();
		String line;
		HashMap<Double,String> activityDetailsgvtcs = new HashMap<>();
		while((line=bf.readLine())!=null) {
			String[] part=line.split(",");
			activityDetailsgvtcs.put(Double.parseDouble(part[0].trim()), part[1].trim());
			//gvtcsConverter.addActivityPlanParameter(config.planCalcScore(),part[1].trim(),30*60);
 		}
		bf.close();
		
		//Create the goods vehicles
		HashMap<Id<Vehicle>,GoodsVehicle> goodsVehicles = createGovVehicles(vehicles.getFactory(), 
				govTrip, govVehicle, tpusbs, weight, hkiOnly);
		goodsVehicles.putAll(createNonGovVehicles(vehicles.getFactory(), ngovTrip, ngovVehicle,
				tpusbs, weight, hkiOnly));
		gvtcsDatabase.close(); //Close here to prevent memory leak.
		
		for(GoodsVehicle gv:goodsVehicles.values()) {
			gv.loadClonedVehicleAndPersons(population, vehicles, activityDetailsgvtcs, "person", "trip", tripPerson, personPerson);
		}
		return activityDetailsgvtcs;
	}
	
	public static void main(String[] args) throws IOException {
		Double personPerson=0.;
		Double tripPerson=0.;
		
		Config config=ConfigUtils.createConfig();
		ConfigWriter configWriter=new ConfigWriter(config);
		configWriter.write("data/GVTCS DATABASE/gvtcsConfig.xml");
		Population population = PopulationUtils.createPopulation(config);
		Vehicles vehicles = VehicleUtils.createVehiclesContainer();
		
		HashMap<Double,String> activityDetails = fillPopulationAndVehicleByGVTCS(population, vehicles, tripPerson, personPerson, 
				weightFactor, false);
		
		for(String s : activityDetails.values()) {
			PopGenUtils.addActivityPlanParameter(config.planCalcScore(), s, 30*60);
		}
		PopGenUtils.writeFiles("output/FinalHKITCSandGVTCS/", config, population, vehicles);
	}
	
	private static void createFakeTPUSBCrossHarbour(FreightTrip trip) {
		if(trip.getOtpusb()!=null && trip.getDtpusb()!=null) {
			TPUSB tpusbCHT=new TPUSB("CHT",new Coord(36662.2,18205.8),0,0,null,100.);
			TPUSB tpusbEHC=new TPUSB("EHC",new Coord(42051.36,17985.4),0,0,null,100.);
			TPUSB tpusbWHC=new TPUSB("WHC",new Coord(34436.52,18383.08),0,0,null,100.);
			if(trip.getOtpusb().getDistrict26Id()>4 && trip.getDtpusb().getDistrict26Id()<=4) {
				if(trip.getTunnelOrTollUsed().contains(1.)) {
					trip.setOtpusb(tpusbCHT);
				}else if(trip.getTunnelOrTollUsed().contains(2.)) {
					trip.setOtpusb(tpusbEHC);
				}else if(trip.getTunnelOrTollUsed().contains(3.)) {
					trip.setOtpusb(tpusbWHC);
				}else {
					ArrayList<Double>distance=new ArrayList<>();
					
					distance.add(NetworkUtils.getEuclideanDistance(trip.getOtpusb().getSatCoord(), tpusbCHT.getSatCoord()));
					distance.add(NetworkUtils.getEuclideanDistance(trip.getOtpusb().getSatCoord(), tpusbEHC.getSatCoord()));
					distance.add(NetworkUtils.getEuclideanDistance(trip.getOtpusb().getSatCoord(), tpusbWHC.getSatCoord()));
					
					if(distance.get(0)<distance.get(1) &&distance.get(0)<distance.get(2)) {
						trip.setOtpusb(tpusbCHT);
					}else if(distance.get(1)<distance.get(2) &&distance.get(1)<distance.get(0)) {
						trip.setOtpusb(tpusbEHC);
					}else {
						trip.setOtpusb(tpusbWHC);
					}
				}
			}else if(trip.getDtpusb().getDistrict26Id()>4 && trip.getOtpusb().getDistrict26Id()<=4) {
				if(trip.getTunnelOrTollUsed().contains(1.)) {
					trip.setDtpusb(tpusbCHT);
				}else if(trip.getTunnelOrTollUsed().contains(2.)) {
					trip.setDtpusb(tpusbEHC);
				}else if(trip.getTunnelOrTollUsed().contains(3.)) {
					trip.setDtpusb(tpusbWHC);
				}else {
					ArrayList<Double>distance=new ArrayList<>();
					distance.add(NetworkUtils.getEuclideanDistance(trip.getDtpusb().getSatCoord(), tpusbCHT.getSatCoord()));
					distance.add(NetworkUtils.getEuclideanDistance(trip.getDtpusb().getSatCoord(), tpusbEHC.getSatCoord()));
					distance.add(NetworkUtils.getEuclideanDistance(trip.getDtpusb().getSatCoord(), tpusbWHC.getSatCoord()));
					
					if(distance.get(0)<distance.get(1) &&distance.get(0)<distance.get(2)) {
						trip.setDtpusb(tpusbCHT);
					}else if(distance.get(1)<distance.get(2) &&distance.get(1)<distance.get(0)) {
						trip.setDtpusb(tpusbEHC);
					}else {
						trip.setDtpusb(tpusbWHC);
					}
				}
			}else if (trip.getDtpusb().getDistrict26Id()>4 && trip.getOtpusb().getDistrict26Id()>4) {
				trip.setOtpusb(null);
				trip.setDtpusb(null);
			}
		}
	}
	
	private static HashMap<Id<Vehicle>,GoodsVehicle> createGovVehicles(VehiclesFactory vf, Table govTrip, 
			Table govVehicle, HashMap<Id<TPUSB>,TPUSB> tpusbs, double weightFactor, boolean onlyHkiTrip){
		HashMap<Id<Vehicle>,GoodsVehicle> vehicles=new HashMap<>();
		
		for(Row row:govVehicle) {
			GoodsVehicle gv;
			if((Double)row.get("Q5")!=null && (double)row.get("Q5")==1) {
				gv=new GoodsVehicle(GoodsVehicleType.getVehicleType(vf, (double)row.get("Veh_Type") ), 
					(String)row.get("GV_id"), (double)row.get("WT_VEH")*weightFactor, (double)row.get("Q7a"),
					(double)row.get("Q7b"), (double)row.get("Q7c"), (double)row.get("Q9c_Amount"), 
					(double)row.get("Q4_Driver"), (double)row.get("Q9c_Unit"),
					(double)row.get("Q9d"), (Double)row.get("Q5"),
					Double.parseDouble((String)row.get("Q6_Cross_Harbour_Used")),
					Double.parseDouble((String)row.get("Q6_Cross_Harbour_Time")),
					Double.parseDouble((String)row.get("Q6_Trip_Purpose")),
					(String)row.get("Q6_Control_Point"));
			}else {
				gv=new GoodsVehicle(GoodsVehicleType.getVehicleType(vf, (double)row.get("Veh_Type") ),
						(String)row.get("GV_id"),(double)row.get("WT_VEH")*weightFactor,(double)row.get("Q7a"),
						(double)row.get("Q7b"),(double)row.get("Q7c"),(double)row.get("Q9c_Amount"),
						(double)row.get("Q4_Driver"),(double)row.get("Q9c_Unit"),
						(double)row.get("Q9d"),(Double)row.get("Q5"),null,null,null,null);
			}
			vehicles.put(gv.getId(), gv);
		}
		
		for(Row row:govTrip) {
			FreightTrip trip;
			String a=(String)row.get("Q2_Control_Point");
			if(row.get("Q2_Control_Point")!=null && !"".equals((String)(row.get("Q2_Control_Point")))) {
				TPUSB otpusb=tpusbs.get(Id.create(""+(double)row.get("Q2_Origin_Code"),TPUSB.class));
				TPUSB dtpusb=tpusbs.get(Id.create(""+(double)row.get("Q2_Origin_Code"),TPUSB.class));
				trip=new FreightTrip(Id.create((String)row.get("GV_id"), Vehicle.class),(double)row.get("Trips_id"),
					tpusbs.get(Id.create(""+(double)row.get("Q2_Origin_Code"),TPUSB.class)), 
					tpusbs.get(Id.create(""+(double)row.get("Q2_Destination_Code"),TPUSB.class)),(String)row.get("Q2_Street_Origin"),
					(String)row.get("Q2_Building_Origin"),(String)row.get("Q2_Street_Destination"),(String)row.get("Q2_Building_Destination"),
					(double)row.get("Q2_Land_Use_Origin"),(double)row.get("Q2_Land_Use_Destination"),(double)row.get("Q2_Departure_Time"),
					(double)row.get("Q2_Arrival_Time"),(double)row.get("Q2_Trip_Purpose"),
					(double)row.get("WT_TRIP")*weightFactor,
					(double)row.get("O454"),
					(double)row.get("D454"),Double.parseDouble((String)row.get("Q2_Control_Point")),
					Double.parseDouble((String)row.get("Q2_Control_Point_Time")),(double)row.get("Ti_Pe"));
			}else {
				TPUSB otpusb=tpusbs.get(Id.create(Double.toString((double)row.get("Q2_Origin_Code")),TPUSB.class));
				TPUSB dtpusb=tpusbs.get(Id.create(Double.toString((double)row.get("Q2_Origin_Code")),TPUSB.class));
				trip=new FreightTrip(Id.create((String)row.get("GV_id"), Vehicle.class),(double)row.get("Trips_id"),
						tpusbs.get(Id.create(""+(double)row.get("Q2_Origin_Code"),TPUSB.class)), 
						tpusbs.get(Id.create(""+(double)row.get("Q2_Destination_Code"),TPUSB.class)),(String)row.get("Q2_Street_Origin"),
						(String)row.get("Q2_Building_Origin"),(String)row.get("Q2_Street_Destination"),(String)row.get("Q2_Building_Destination"),
						(double)row.get("Q2_Land_Use_Origin"),(double)row.get("Q2_Land_Use_Destination"),(double)row.get("Q2_Departure_Time"),
						(double)row.get("Q2_Arrival_Time"),(double)row.get("Q2_Trip_Purpose"),
						(double)row.get("WT_TRIP")*weightFactor,(double)row.get("O454"),(double)row.get("D454"),null,null,(double)row.get("TiPer"));
			}
			
			for(int i=1;i<=12;i++) {
				if(row.get("Q2_Toll_facility_"+i)!=null && !"".equals((String)row.get("Q2_Toll_facility_"+i)) ) {
					trip.addTollFacilityUsed(Double.parseDouble((String)row.get("Q2_Toll_facility_"+i)));
				}
			}
			if(onlyHkiTrip) {
				GVTCSConverter.createFakeTPUSBCrossHarbour(trip);
			}
			vehicles.get(trip.getVehicleId()).addTrip(trip);
		}

		return vehicles;
		
		
	}

	private static HashMap<Id<Vehicle>,GoodsVehicle> createNonGovVehicles(VehiclesFactory vf, Table nonGovTrip,
			Table nonGovVehicle,HashMap<Id<TPUSB>,TPUSB> tpusbs,double weightFactor,boolean onlyHkiTrip){
		HashMap<Id<Vehicle>,GoodsVehicle> vehicles=new HashMap<>();
		
		for(Row row:nonGovVehicle) {
			GoodsVehicle gv;
			if((Double)row.get("Q6")!=null && (double)row.get("Q6")==1) {
				gv=new GoodsVehicle(GoodsVehicleType.getVehicleType(vf, (double)row.get("Veh_Type") ),
					(String)row.get("GV_id"),(double)row.get("WT_VEH")*weightFactor,(double)row.get("Q8a"),
					(double)row.get("Q8b"),(double)row.get("Q8c"),(double)row.get("Q10c_Amount"),
					(double)row.get("Q5_Driver"),(double)row.get("Q10c_Unit"),
					(double)row.get("Q10e"),(Double)row.get("Q6"),
					Double.parseDouble((String)""+row.get("Q7_Cross_Harbour_Used")),
					Double.parseDouble((String)""+row.get("Q7_Cross_Harbour_Time")),
					Double.parseDouble((String)""+row.get("Q7_Trip_Purpose")),
					(String)row.get("Q7_Control_Point"));
			}else {
				gv=new GoodsVehicle(GoodsVehicleType.getVehicleType(vf, (double)row.get("Veh_Type") ),
						(String)row.get("GV_id"),(double)row.get("WT_VEH")*weightFactor,(double)row.get("Q8a"),
						(double)row.get("Q8b"),(double)row.get("Q8c"),(double)row.get("Q10c_Amount"),
						(double)row.get("Q5_Driver"),(double)row.get("Q10c_Unit"),
						(double)row.get("Q10e"),
						(Double)row.get("Q6"),null,null,null,null);
			}
			vehicles.put(gv.getId(), gv);
		}
		
		for(Row row:nonGovTrip) {
			FreightTrip trip;
			String a=(String)row.get("Q2_Control_Point");
			if(row.get("Q3_Control_Point")!=null && !"".equals((String)(row.get("Q3_Control_Point")))) {
				trip=new FreightTrip(Id.create((String)row.get("GV_id"), Vehicle.class),(double)row.get("Trips_id"),
					tpusbs.get(Id.create(""+(double)row.get("Q3_Origin_Code"),TPUSB.class)), 
					tpusbs.get(Id.create(""+(double)row.get("Q3_Destination_Code"),TPUSB.class)),(String)row.get("Q3_Origin_Street"),
					(String)row.get("Q3_Origin_Building"),(String)row.get("Q3_Destination_Street"),(String)row.get("Q2_Destination_Building"),
					(double)row.get("Q3_Land_Use_Origin"),(double)row.get("Q3_Land_Use_Destination"),(double)row.get("Q3_Departure_Time"),
					(double)row.get("Q3_Arrival_Time"),(double)row.get("Q3_Trip_Purpose"),
					(double)row.get("WT_TRIP")*weightFactor,
					(double)row.get("O454"),
					(double)row.get("D454"),Double.parseDouble((String)row.get("Q3_Control_Point")),
					Double.parseDouble((String)row.get("Q3_Control_Point_Time")),(double)row.get("Ti_Pe"));
			}else {
				trip=new FreightTrip(Id.create((String)row.get("GV_id"), Vehicle.class),(double)row.get("Trips_id"),
						tpusbs.get(Id.create(""+(double)row.get("Q3_Origin_Code"),TPUSB.class)), 
						tpusbs.get(Id.create(""+(double)row.get("Q3_Destination_Code"),TPUSB.class)),(String)row.get("Q3_Origin_Street"),
						(String)row.get("Q3_Origin_Building"),(String)row.get("Q3_Destination_Street"),(String)row.get("Q2_Destination_Building"),
						(double)row.get("Q3_Land_Use_Origin"),(double)row.get("Q3_Land_Use_Destination"),(double)row.get("Q3_Departure_Time"),
						(double)row.get("Q3_Arrival_Time"),(double)row.get("Q3_Trip_Purpose"),
						(double)row.get("WT_TRIP")*weightFactor,
						(double)row.get("O454"),
						(double)row.get("D454"),null,null,(double)row.get("TiPer"));
			}
			
			for(int i=1;i<=12;i++) {
				if(row.get("Q2_Toll_facility_"+i)!=null && !"".equals((String)row.get("Q2_Toll_facility_"+i)) ) {
					trip.addTollFacilityUsed(Double.parseDouble((String)row.get("Q2_Toll_facility_"+i)));
				}
			}
			if(onlyHkiTrip) {
				GVTCSConverter.createFakeTPUSBCrossHarbour(trip);
			}
			vehicles.get(trip.getVehicleId()).addTrip(trip);
		}

		return vehicles;
	}
}

