package population;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.Vehicles;

import com.healthmarketscience.jackcess.Cursor;
import com.healthmarketscience.jackcess.CursorBuilder;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

import population.GVTCS.GVTCSConverter;

public class PopGenUtils {
	public static String TCSFilePath = "data/TCSDatabase/TCS2011 database.accdb";
	public static String GVTCSFilePath = "data/GVTCS DATABASE/GVTCS.accdb";
	public static String manuelPath = "TCS/";
	//public static String outputDir = "output/revisedHKITCSandGVTCS/";
	
	public static HashMap<Id<TPUSB>,TPUSB> getTPUSB() throws IOException{
		Database tcsDatabase=DatabaseBuilder.open(new File(TCSFilePath));
		Table tpusbCoord=tcsDatabase.getTable("TPUSB_coordinate_Mod");
		Table tpusb11=tcsDatabase.getTable("11TPUSB");
		HashMap<Id<TPUSB>,TPUSB> tpusbs = tpusbCreator(tpusbCoord,tpusb11);
		tcsDatabase.close();
		return tpusbs;
	}
	
	private static HashMap<Id<TPUSB>,TPUSB> tpusbCreator(Table tpusbCoord,Table tpusb11) {
		HashMap<Id<TPUSB>,TPUSB> tpusbs=new HashMap<>();
		
		for(Row row:tpusbCoord) {
			try {
				double tpusbId=(double) row.get("FUL");
				Double tpuId=(Double)row.get("TPU");
				Double sbvcId=(Double)row.get("SB_VC");
				Coord satCoord=new Coord((double)row.get("XcalSat"),(double)row.get("YcalSat"));
				Cursor cursor=CursorBuilder.createCursor(tpusb11);
				cursor.findFirstRow(Collections.singletonMap("11TPUSB", tpusbId));
				Row rowTpusb11=cursor.getCurrentRow();
				Double pdz454=(Double)rowTpusb11.get("454PDZ");
				Double db26Id=(Double)rowTpusb11.get("DB26");
				String db26Name=(String)rowTpusb11.get("DB26_name");
				Double area=(Double)row.get("SumOfShape_Area");
				TPUSB tpusb=new TPUSB(tpusbId,satCoord,pdz454.intValue(),db26Id.intValue(),db26Name,area);
				tpusbs.put(tpusb.getTPUSBId(), tpusb);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
		return tpusbs;
	}
	
	public static boolean checkPlanValidity(Plan plan, String memberId) {
		double lastTime = 0.0;
		for(PlanElement pe: plan.getPlanElements()) {
			if(pe instanceof Activity) {
				if(lastTime < ((Activity) pe).getStartTime().seconds() - 0.1) {
					throw new IllegalArgumentException("The time for plan of "+memberId+" is not correct!");
				}
				lastTime = ((Activity) pe).getEndTime().seconds();
			}
			if(pe instanceof Leg) {
				if(lastTime < ((Leg) pe).getDepartureTime().seconds() - 0.1) {
					throw new IllegalArgumentException("The time for plan of "+memberId+" is not correct!");
				}
				lastTime = ((Leg) pe).getDepartureTime().seconds() + ((Leg) pe).getTravelTime().seconds();
			}
		}
		return true;
	}
	
	public static void addActivityPlanParameter(PlanCalcScoreConfigGroup config,String name,int typicalDuration){
		ActivityParams act = new ActivityParams(name);
		act.setTypicalDuration(typicalDuration);
		config.addActivityParams(act);
	}
	
	/**
	 * Uniformly random offset the population time by number of seconds
	 * If offset if 100, then in would be uniformly distributed to [-100,100]
	 * @param population
	 * @param offSet_s Offset in the unit of second
	 */
	public static void offsetPopulationTime(Population population, double offSet_s) {
		for(Person person: population.getPersons().values()) {
			Plan plan = person.getSelectedPlan();
			double offset = offSet_s * (Math.random() -0.5)* 2;
			for(PlanElement pe: plan.getPlanElements()) {
				if(pe instanceof Activity) {
					if(((Activity) pe).getStartTime().seconds() <= offset) {
						continue; //Don't do the offset for it may become negative time.
					}
					((Activity) pe).setStartTime(((Activity) pe).getStartTime().seconds() + offset);
					((Activity) pe).setEndTime(((Activity) pe).getEndTime().seconds() + offset);
				}
				if(pe instanceof Leg) {
					((Leg) pe).setDepartureTime(((Leg) pe).getDepartureTime().seconds()+offset);
				}
			}
		}
	}
	
	/**
	 * Write the files to descinated directory.
	 * @param config
	 * @param population
	 * @param vehicles
	 */
	public static void writeFiles(String outputDir, Config config, Population population, Vehicles vehicles) {
		ConfigWriter configWriter=new ConfigWriter(config);
		PopulationWriter popWriter=new PopulationWriter(population);
		VehicleWriterV1 vehWriter=new VehicleWriterV1(vehicles);
		
		popWriter.write(outputDir+"populationHKI.xml");
		vehWriter.writeFile(outputDir+"VehiclesHKI.xml");
		configWriter.write(outputDir+"config_pop.xml");
//		new ObjectAttributesXmlWriter(population.getPersonAttributes())
//			.writeFile(outputDir+"personAttributesHKI.xml");
	}
}
