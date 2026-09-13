/**
 * 
 */
package dynamicTransitRouter.transfer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import com.google.common.collect.Sets;

/**
 * Reads raw collected data and convert to JSON structure for easy store and reread
 * Contains a debug tool for KMB BBI dir
 * 
 * mtr source: http://www.mtr.com.hk/en/customer/tickets/octopus_fares.html
 * @author JLo
 *
 */
public class DataReaderAndConverter {
	
	public static void main(String[] args) throws IOException {
		// TODO All the file paths
		final String MTR_BusMinibus_file = "C:\\Users\\FYP\\Desktop\\All you need\\Interchange Discounts\\MTR.csv";
		final String LRTFeeder_file = "C:\\Users\\FYP\\Desktop\\All you need\\Interchange Discounts\\LRTFeederBus.csv";
		final String KMB_Minibus_file = "C:\\Users\\FYP\\Desktop\\All you need\\Interchange Discounts\\GMB.csv";
		final String KMBBBI_folder = "C:\\Users\\FYP\\Desktop\\All you need\\Interchange Discounts\\leeen";
		final String KMBEqMap_File = "C:\\Users\\FYP\\Desktop\\All you need\\Interchange Discounts\\KMB Matching List.csv";
		
		final String transitScheduleFile = "D:\\eclipse-workspace\\intern-project\\outputFullHK\\transitSchedule.xml";
		final String jsonFile = "D:\\eclipse-workspace\\intern-project\\outputFullHK\\transitDiscount.json";
		
		//pre-load the transit schedule for later use
		Config config = ConfigUtils.createConfig();
		config.transit().setTransitScheduleFile(transitScheduleFile);
		TransitSchedule ts = ScenarioUtils.loadScenario(config).getTransitSchedule();
		
		//DEBUG
//		debugBusDir(KMBBBI_folder, KMBEqMap_File, ts);
		
		//dumpin the calculators
		AllPTTransferDiscount discountCalculator = new AllPTTransferDiscount();
		discountCalculator.MTR_BusMiniBus_Discounts = readAndConvertMTRBusMinibus(MTR_BusMinibus_file);
		discountCalculator.LRTFeeder_Free = readAndConvertLRTFeeder(LRTFeeder_file);
		discountCalculator.KMB_MiniBus_Discount = readMinibusDiscountFile(KMB_Minibus_file);
		discountCalculator.BBI_Discount = readAndConvertKMB(KMBBBI_folder, KMBEqMap_File, ts);
		
		//print AllPTTransferDiscount
		discountCalculator.printJSON(jsonFile);

	}
	
	/** TODO
	 * Run debugBusDir before running this, this does no checks whatsoever
	 */
	public static HashMap<Id<TransitRoute>, HashMap<Id<TransitRoute>, BusDiscountData>> readAndConvertKMB(String folder, String KMBEqMap_File, TransitSchedule ts) throws IOException {
		
		HashMap<Id<TransitRoute>, HashMap<Id<TransitRoute>, BusDiscountData>> output = new HashMap<Id<TransitRoute>, HashMap<Id<TransitRoute>, BusDiscountData>>();
			
		HashMap<Id<TransitLine>, TLTRIder> helper = new HashMap<Id<TransitLine>, TLTRIder>();	
			//could have used plain String without operator up front, but this is better prepared
		
		//#1	Figure out which file applies to which routes
		for(File TF: FileUtils.listFiles(new File(folder), new String[]{"csv"}, false)) {
			String fileName = TF.getName();
			
			if(fileName.contains("Second leg"))
				continue;
			fileName = fileName.replace(".csv", "");
			String[] splitName = fileName.split(" First leg To ");
			
			Map<Id<TransitLine>, TransitLine> transitLines = ts.getTransitLines();
			Id<TransitLine> TLId = genKMBTransitLineId(splitName[0], ts);
			if(TLId == null)
				continue;
			
			TLTRIder TLTRIder;
			if(!helper.containsKey(TLId))
				TLTRIder = new TLTRIder(transitLines.get(TLId));
			else
				TLTRIder = helper.get(TLId);
			
			TLTRIder.findMatching(TF, splitName[1]);
			helper.put(TLId, TLTRIder);
		}
		//deal with the circulars
		for(TLTRIder TT: helper.values())
			if(!TT.allAssigned() || !TT.allFound())
				TT.findCir();
		//additional fixing
		HashMap<String, HashSet<String>> fileNameAssignmentMap = initKMBEqMap(KMBEqMap_File);
		for(TLTRIder TT: helper.values())
			for(Entry<Integer, File> ent: TT.fileMap.entrySet()) {
				String fileNameKey = ent.getValue().getName();
				if(fileNameAssignmentMap.containsKey(fileNameKey)) {
					HashSet<String> TRIdShort = fileNameAssignmentMap.get(fileNameKey);
					for(Entry<Id<TransitRoute>, HashSet<Integer>> ent2: TT.TRMap.entrySet())
						if(TRIdShort.contains(ent2.getKey().toString().subSequence(0, 6))) {
							ent2.getValue().add(ent.getKey());
							TT.unAssigned.remove(ent.getKey());
						}
					break;
				}
			}
		
		//#2	parse files and build output
		//start looping through the lines
		for(TLTRIder TT: helper.values()) {
			//make a temp file content holder
			TT.loadFiles(helper, ts);
			//Building the output
			for(Entry<Id<TransitRoute>, HashSet<Integer>> ent: TT.TRMap.entrySet()) 
				if(!ent.getValue().isEmpty()){
					HashMap<Id<TransitRoute>, BusDiscountData> thisEntry = new HashMap<Id<TransitRoute>, BusDiscountData>();
					for(Integer intKey: ent.getValue())
						for(Entry<HashSet<Id<TransitRoute>>, String> subent: TT.disInfoHolder.get(intKey).entrySet())
							for(Id<TransitRoute> toRoute: subent.getKey()) {
								//update existing or put in the new one
								BusDiscountData thisData;
								if(thisEntry.containsKey(toRoute))
									thisData = thisEntry.get(toRoute);
								else {
									thisData = new BusDiscountData();
									thisEntry.put(toRoute, thisData);
								}
								thisData.addString(subent.getValue());
							}
					output.put(ent.getKey(), thisEntry);
				}
			//clean mem
			TT.disInfoHolder = null;
		}
		
		return output;
	}
	
	public static HashSet<Id<TransitLine>> readMinibusDiscountFile(String minibusFilePath) throws IOException {
		HashSet<Id<TransitLine>> output = new HashSet<Id<TransitLine>>();
		
		CSVParser parser = new CSVParser(new FileReader(minibusFilePath), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		for(CSVRecord record: parser.getRecords())
			output.add(Id.create("GMB_HKI_"+record.get("GMB"), TransitLine.class));
			
		parser.close();
		return output;
	}
	
	private static HashMap<Id<TransitLine>, Double> readAndConvertMTRBusMinibus(String file) throws IOException {
		HashMap<Id<TransitLine>, Double> output = new HashMap<Id<TransitLine>, Double>();
		
		CSVParser parser = new CSVParser(new FileReader(file), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		String prefix = "";
		for(CSVRecord record: parser.getRecords()) {
			//figure out which operator
			if(record.get(0).equals("Green Minibus-Hong Kong Island")) {
				prefix = "GMB_HKI_";
				continue;
			} else if(record.get(0).equals("Green Minibus ?? Kowloon")) {
				prefix = "GMB_KLN_";
				continue;
			} else if(record.get(0).equals("Green Minibus ?? New Territories")) {
				prefix = "GMB_NT_";
				continue;
			} else if(record.get(0).equals("Citybus")) {
				prefix = "CTB_";
				continue;
			} else if(record.get(0).equals("New Lantao Bus")) {
				prefix = "NLB_";
				continue;
			}
			
			assert !prefix.isEmpty();
			
			//scrub the records
			String inputLines = record.get(1);
			inputLines = inputLines.replace("/", ", ").replaceAll("[^\\w ,]", "");
			String inputDiscount = record.get(2).replaceAll("[^\\.0-9 /]", "");
			
			//get the discount amount
			double TDiscount = 0.0;
			for(String TDis: inputDiscount.split("/"))
				if(Double.parseDouble(TDis)>TDiscount)
					TDiscount = Double.parseDouble(TDis);
			
			//get line ids and put it in output
			for(String TLine: inputLines.split(","))
				output.put(Id.create(prefix+TLine.trim(), TransitLine.class), TDiscount);
		}
		
		parser.close();
		return output;
	}
	
	private static HashMap<Id<TransitLine>, HashSet<Id<TransitLine>>> readAndConvertLRTFeeder(String file) throws IOException {
		HashMap<Id<TransitLine>, HashSet<Id<TransitLine>>> output = new HashMap<Id<TransitLine>, HashSet<Id<TransitLine>>>();
		
		CSVParser parser = new CSVParser(new FileReader(file), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		for(CSVRecord record: parser.getRecords()) {
			Id<TransitLine> fromLine = Id.create(AllPTTransferDiscount.LRTFeeder+"_"+record.get(0), TransitLine.class);
			HashSet<Id<TransitLine>> toLineSet = new HashSet<Id<TransitLine>>();
			
			String rec = record.get(1);
			
			for(String toLineS: rec.split(", "))
				toLineSet.add(Id.create(AllPTTransferDiscount.LRTFeeder+"_"+toLineS, TransitLine.class));
			
			output.put(fromLine, toLineSet);
		}
		
		parser.close();
		return output;
	}
	
	/**
	 * DEBUG use
	 * Unmatchable file names are listed in ADEBUG.csv
	 * @throws IOException 
	 */
	public static void debugBusDir(String folder, String KMBEqMap_File, TransitSchedule ts) throws IOException {
		
		ArrayList<String> errors = new ArrayList<String>();
		
		HashMap<Id<TransitLine>, TLTRIder> helper = new HashMap<Id<TransitLine>, TLTRIder>();	
			//could have used plain String without operator up front, but this is better prepared
		
		for(File TF: FileUtils.listFiles(new File(folder), new String[]{"csv"}, false)) {
			String fileName = TF.getName();
			
			//DEBUG
//			if(fileName.contains("968A"))
//				System.out.println("");
			
			if(fileName.contains("Second leg"))
				continue;
			fileName = fileName.replace(".csv", "");
			String[] splitName = fileName.split(" First leg To ");
			
			Map<Id<TransitLine>, TransitLine> transitLines = ts.getTransitLines();
			Id<TransitLine> TLId = genKMBTransitLineId(splitName[0], ts);
			if(TLId == null)
				continue;
			
			TLTRIder TLTRIder;
			if(!helper.containsKey(TLId))
				TLTRIder = new TLTRIder(transitLines.get(TLId));
			else
				TLTRIder = helper.get(TLId);
			
			TLTRIder.findMatching(TF, splitName[1]);
			helper.put(TLId, TLTRIder);
		}
		
		//deal with the circulars
		for(TLTRIder TT: helper.values())
			if(!TT.allAssigned() || !TT.allFound())
				TT.findCir();
		
		//additional fixing
		HashMap<String, HashSet<String>> fileNameAssignmentMap = initKMBEqMap(KMBEqMap_File);
		for(TLTRIder TT: helper.values())
			for(Entry<Integer, File> ent: TT.fileMap.entrySet()) {
				String fileNameKey = ent.getValue().getName();
				if(fileNameAssignmentMap.containsKey(fileNameKey)) {
					HashSet<String> TRIdShort = fileNameAssignmentMap.get(fileNameKey);
					for(Entry<Id<TransitRoute>, HashSet<Integer>> ent2: TT.TRMap.entrySet())
						if(TRIdShort.contains(ent2.getKey().toString().subSequence(0, 6))) {
							ent2.getValue().add(ent.getKey());
							TT.unAssigned.remove(ent.getKey());
							System.out.println("Additional fix "+ent2.getKey().toString()+" | "+fileNameKey);
						}
					break;
				}
			}
		
		//checking
		for(Id<TransitLine> tlID: ts.getTransitLines().keySet())
			if(tlID.toString().contains("KMB")) {
				TLTRIder TT = helper.get(tlID);
				
				if(TT == null)
					errors.add(tlID.toString());
				else
					if(!TT.allAssigned() || !TT.allFound()) {
						//there are still files unassigned
						for(Integer key: TT.unAssigned)
							errors.add(TT.fileMap.get(key).getName());
						//TR has no discount assigned, which is guaranteed as KMB-LWB_A transfer
						if(!TT.allFound())
							for(Entry<Id<TransitRoute>, HashSet<Integer>> stuff: TT.TRMap.entrySet())
								if(stuff.getValue().isEmpty())
									errors.add(stuff.getKey().toString());
					}
			}
		//print stuff
		CSVPrinter printer = new CSVPrinter(new FileWriter(folder+"ADEBUG.csv"), CSVFormat.DEFAULT);	//XXX file location
		for(String out: errors)
			printer.printRecord(out);
		printer.close();
		
	}
	
	/**
	 * Initiates a map containing transfer discount file name as key, and HashSet of (partial) transit route Id (XXXX-X) as value
	 */
	private static HashMap<String, HashSet<String>> initKMBEqMap(String file) throws IOException {
		HashMap<String, HashSet<String>> output = new HashMap<String, HashSet<String>>();
		CSVParser parser = new CSVParser(new FileReader(file), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		for(CSVRecord record: parser.getRecords())
			output.put(record.get(0), Sets.newHashSet(record.get(1).split("/")));
		parser.close();
		return output;
	}
	
	/**
	 * Brute force the Transit Line Id, with preference to KMB routes first
	 * includes other operators because operator to operator BBI is a thing
	 */
	static Id<TransitLine> genKMBTransitLineId(String lineName, TransitSchedule ts) {
		Map<Id<TransitLine>, TransitLine> transitLines = ts.getTransitLines();
		Id<TransitLine> TLId = null;
		
		//the theres a clue in it somewhere solution
		if(!lineName.matches("\\w{1,5}")) {
			if(lineName.contains("KMB")) {
				TLId = Id.create("KMB_"+lineName.replaceAll("\\W*KMB\\W*", "").trim(),  TransitLine.class);
				if(transitLines.containsKey(TLId))
					return TLId;
			} else if (lineName.contains("CTB")) {
				TLId = Id.create("CTB_"+lineName.replaceAll("\\W*CTB\\W*", "").trim(),  TransitLine.class);
				if(transitLines.containsKey(TLId))
					return TLId;
			} else if (lineName.contains("NWFB")) {
				TLId = Id.create("NWFB_"+lineName.replaceAll("\\W*NWFB\\W*", "").trim(),  TransitLine.class);
				if(transitLines.containsKey(TLId))
					return TLId;
			}
		}
		
		//brute force
		for(String prefix: new String[] {"KMB_", "LWB_", "KMB+CTB_", "KMB+NWFB_", "CTB_", "NWFB_", "NLB_"}) {	//this is stupid
			TLId = Id.create(prefix+lineName, TransitLine.class);
			if(transitLines.containsKey(TLId))
				return TLId;
		}
		return null;
	}

}

class TLTRIder {
	HashMap<Id<TransitRoute>, HashSet<Integer>> TRMap;
	HashMap<Integer, File> fileMap;	
	HashMap<Integer, String> toStopNameMap;			//invert this to use as string key search
	HashSet<Integer> unAssigned;
	boolean hasCir;			
//	Integer cirKey;		//not actually really used, eq to unassigned, but ran quick enough that I don't want to touch again
	
	HashMap<Integer, HashMap<HashSet<Id<TransitRoute>>, String>> disInfoHolder;
	
	public TLTRIder(TransitLine transitLine) {
		this.TRMap = new HashMap<Id<TransitRoute>, HashSet<Integer>>();
		for(Id<TransitRoute> TRId: transitLine.getRoutes().keySet())
			TRMap.put(TRId, new HashSet<Integer>());
		this.fileMap = new HashMap<Integer, File>();
		this.toStopNameMap = new HashMap<Integer, String>();
		this.unAssigned = new HashSet<Integer>();
	}
	
	public void loadFiles(HashMap<Id<TransitLine>, TLTRIder> helper, TransitSchedule ts) throws IOException {
		this.disInfoHolder = new HashMap<Integer, HashMap<HashSet<Id<TransitRoute>>, String>>();
		
		for(Entry<Integer, File> ent: this.fileMap.entrySet()) {
			CSVParser parser = new CSVParser(new FileReader(ent.getValue()), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
			HashMap<HashSet<Id<TransitRoute>>, String> fromFileData = new HashMap<HashSet<Id<TransitRoute>>, String>();
			
			for(CSVRecord record: parser.getRecords()) {
				HashSet<Id<TransitRoute>> toRoutes = new HashSet<Id<TransitRoute>>();
				//figure out the transit line
				Id<TransitLine> toLine = DataReaderAndConverter.genKMBTransitLineId(record.get("route"), ts);
				if(toLine == null) {
					System.out.println(record.get("route")+" is not built currently!");
					continue;
				}
				String toStop = record.get("To");
				TLTRIder toLineData = helper.get(toLine);
				if(toLineData != null) {
					//figure out the key to file
					Integer toFileKey = null;
					for(Entry<Integer, File> entToLine: toLineData.fileMap.entrySet())
						if(entToLine.getValue().getName().contains(toStop)) {
							toFileKey = entToLine.getKey();
							break;
						}
					
					if(toFileKey != null) {
						//get the route Ids
						for(Entry<Id<TransitRoute>, HashSet<Integer>> entToRoute: toLineData.TRMap.entrySet())
							if(entToRoute.getValue().contains(toFileKey))
								toRoutes.add(entToRoute.getKey());
					} else {
						System.out.println("Can't find to dest for this line "+toLine.toString()+" assuming all without assignment is it");
						//as there are only "2 directions" then if one has file the others must be the other direction
						for(Entry<Id<TransitRoute>, HashSet<Integer>> entToRoute: toLineData.TRMap.entrySet())
							if(entToRoute.getValue().isEmpty()) {
								toRoutes.add(entToRoute.getKey());
								System.out.println("	Matched "+toStop+" to "+entToRoute.getKey().toString());
							}
					}
				} else {
					if(!toLine.toString().contains("LWB"))
						System.out.println("Theres no helper for this toLine: "+toLine.toString());
					//plain contains all the routes to loosely find something la
					for(Id<TransitRoute> tocheck: ts.getTransitLines().get(toLine).getRoutes().keySet())
						if(tocheck.toString().contains(toStop)) {
							toRoutes.add(tocheck);
							System.out.println("	Matched "+toStop+" to "+tocheck.toString());
						}
				}
				//save stuff
				fromFileData.put(toRoutes, record.get("info"));
			}
			parser.close();
			this.disInfoHolder.put(ent.getKey(), fromFileData);
		}
	}

	public boolean findMatching(File file, String toStopName) {
		Integer toPutKey = this.fileMap.size();
		toStopName = toStopName.trim().replace(" ", "");
		
		this.fileMap.put(toPutKey, file);
		this.toStopNameMap.put(toPutKey, toStopName);
		
		boolean found = false;
		//Id circular
		if(toStopName.contains("CIR") || toStopName.contains("Circular")) {
			if(this.hasCir)
				throw new RuntimeException("There are more than 1 file with cir for this line");
			this.hasCir = true;
			this.unAssigned.add(toPutKey);
			return true;
		}
		
		//#strat 1, plain contains
		for(Entry<Id<TransitRoute>, HashSet<Integer>> ent: TRMap.entrySet())
			if(ent.getKey().toString().replace(" ", "").contains(toStopName)) {
				ent.getValue().add(toPutKey);
				found = true;
			}
			//no break, have to loop all to find other sub-routes
		
		//#strat 2, match brackets substring inside/outside (ie: outside(inside) )
		if(!found)
			if(toStopName.contains("(")) {
				int openIndex = toStopName.indexOf('(');
				int closIndex = toStopName.indexOf(')');
				
				String first = toStopName.substring(0, openIndex).replace("ESTATE", "").replace("GARDENS", "GARDEN").trim();
				String second = toStopName.substring(openIndex+1, closIndex).replace("ESTATE", "").replace("GARDENS", "GARDEN").trim();
				
				assert (!first.isEmpty() || !second.isEmpty());
				
				//outside
				for(Entry<Id<TransitRoute>, HashSet<Integer>> ent: TRMap.entrySet())
					if(ent.getKey().toString().replace(" ", "").contains(first)) {
						ent.getValue().add(toPutKey);
						found = true;
						//DEBUG
//						System.out.println("strat 2 found "+ent.getKey().toString()+" | "+toStopName);
					}
				//inside
				for(Entry<Id<TransitRoute>, HashSet<Integer>> ent: TRMap.entrySet())
					if(ent.getKey().toString().replace(" ", "").contains(second)) {
						ent.getValue().add(toPutKey);
						found = true;
						//DEBUG
//						System.out.println("strat 2 found "+ent.getKey().toString()+" | "+toStopName);
					}
			}
		
		if(!found)
			this.unAssigned.add(toPutKey);
		return false;
	}
	
	/**
	 * 
	 */
	public boolean findCir() {
		//#strat 3, TL only has 1 TR, then jam everything into that
		if(this.TRMap.size() == 1) {
			this.TRMap.values().iterator().next().addAll(this.unAssigned);
			//DEBUG
//			System.out.println("strat 3 found "+this.TRMap.keySet().iterator().next().toString()+" | "+
//					this.fileMap.get(this.unAssigned.iterator().next()).getName() );
			this.unAssigned.clear();
			return true;
		//#strat 4: all TR in TL has same destination, assume circular, jam everything into everything
		} else {
			//check if the dest are all the same name
			Iterator<Id<TransitRoute>> itr = this.TRMap.keySet().iterator();
			String toCheck = itr.next().toString();
			String nameCheck = toCheck.substring(toCheck.indexOf(':')+2);
			while(itr.hasNext()) {
				toCheck = itr.next().toString();
				if(!nameCheck.equals(toCheck.substring(toCheck.indexOf(':')+2)))
					return false;
			}
			//if they are then dump it all in
			Iterator<HashSet<Integer>> itr2 = this.TRMap.values().iterator();
			while(itr2.hasNext())
				itr2.next().addAll(this.unAssigned);
			
			//DEBUG
//			System.out.println("strat 4 found "+this.TRMap.keySet().iterator().next().toString()+" | "+
//					this.fileMap.get(this.unAssigned.iterator().next()).getName() );
			this.unAssigned.clear();
			return true;
		}
	}
	
	/**
	 * all TR has mapped with toStopName
	 */
	public boolean allFound() {
		for(HashSet<Integer> i: this.TRMap.values())
			if(i.isEmpty())
				return false;
		return true;
	}
	
	public boolean allAssigned() {
		if(this.unAssigned.size()>0)
			return false;
		else
			return true;
	}
	
	
	
}
















