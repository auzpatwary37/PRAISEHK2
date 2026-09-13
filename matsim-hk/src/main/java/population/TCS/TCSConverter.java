package population.TCS;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.vehicles.Vehicle;

import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Table;

import population.PopGenUtils;
import population.TPUSB;

@Deprecated
public class TCSConverter {
	public static void main(String[] args) throws IOException {
		
		Database tcsDatabase=DatabaseBuilder.open(new File("data/TCSDatabase/TCS2011 database.accdb"));
		
		Table tpusbCoord=tcsDatabase.getTable("TPUSB_coordinate_Mod");
		Table tpusb11=tcsDatabase.getTable("11TPUSB");
		Table houseHoldDatabase=tcsDatabase.getTable("HH");
		Table houseHoldMemberDatabase=tcsDatabase.getTable("HM");
		Table tripDatabase=tcsDatabase.getTable("TP");
		
		HashMap<Id<TPUSB>,TPUSB> tpusbs = PopGenUtils.getTPUSB();
		
		System.out.println("TestLine");
	}
}

class TCSHousehold{
	
}

class TCSPerson{
	private double questionaryNo;
	private double memberNo;
	private double age;
	private double sex;
	private double isEmployed;
	private double isDriver;
	private double haveLisence;
	private double levelOfStudy;
	private double monthlyIncome;
	private double refTravelDay;
	private LinkedHashMap<Integer,Double> activities;
	private LinkedHashMap<Integer,Double> legModes;
	
	
}