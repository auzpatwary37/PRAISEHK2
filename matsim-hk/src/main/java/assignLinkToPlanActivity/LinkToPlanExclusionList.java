/**
 * 
 */
package assignLinkToPlanActivity;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

/**
 * to limit the links an agent can start/end activity on,<br>
 * based on TD network declaration of elevation of the links,<br>
 * translated using the matching tables<br>
 * TD network link elevation had been edited to remove undesired links.<p>
 * 
 * <b>DO NOT USE THIS LIST FOR BUS STOP MAPPING</b>
 * 
 * @author JLo
 *
 */
class LinkToPlanExclusionList {
	private static String KLNFilePath = "C:\\Users\\FYP\\Desktop\\All you need\\Matching Tables\\kln.csv";
	private static String HKIFilePath = "C:\\Users\\FYP\\Desktop\\All you need\\Matching Tables\\hki_new_withCWB.csv";
	private static String HKI2FilePath = "C:\\Users\\FYP\\Desktop\\All you need\\Matching Tables\\hki_new_withoutCWB.csv";
	private static String NTWFilePath = "C:\\Users\\FYP\\Desktop\\All you need\\Matching Tables\\ntw.csv";
	private static String NTEFilePath = "C:\\Users\\FYP\\Desktop\\All you need\\Matching Tables\\nte.csv";
	private static String ExcludeFilePath = "C:\\Users\\FYP\\Desktop\\All you need\\Matching Tables\\route_elevation.csv";
	
	private static final String MatchColHeader = "matching";
	private static final String MatchColHeader_TDID = "id";
	private static final String TD_ID_MatchColHeader = "ROUTE_ID";
	private static final String TD_ELE_MatchColHeader = "ELEVATION";
	private static final Pattern pattern = Pattern.compile("a?\\d+_a?\\d+");
	private static final Pattern commapattern = Pattern.compile(",");
	private static final Pattern fromtopattern = Pattern.compile("FROM.TO");
	private final static Logger log = Logger.getLogger(LinkToPlanExclusionList.class);
	
	//private static ArrayList<String> FilePaths = new ArrayList<String>(Arrays.asList(KLNFilePath, NTWFilePath, NTEFilePath));
	
	private static HashMap<Id<Link>, String> elevatedLinks;
	
	//manually add links that need to be excluded and stuff to exclude from the TD exclude list
	private static final String[] ManualExStuff = {
			"101386_101542", "105125_101368", "105123_105125", "105123_105124", "101379_101380", "101369_101370", "101480_101391", "101369_101480", "402440_402428",
			"401916_403106", "403107_401915", "403103_403107", "509118_503764", "503764_509118", "651608_651607", "651607_651609",
			"875070_140983", "875084_875088", "988197_988198"	/*FROM network change event*/};
	private static final ArrayList<String> ManualExExStuff = new ArrayList<String>(Arrays.asList(
			"104222_101538", "101538_104222", "653269_657006", "657006_653269", "657006_657005", "657005_657006", "657005_657004", "657004_657005", "657004_659002", "659002_657004", 
			"659002_659001", "659001_659002", "659001_653272", "653272_659001", "871312_871200", "310013_310019", "310016_310019"));
	
	public static void defineFilePath(String path) {
		KLNFilePath = path + "kln.csv";
		HKIFilePath = path + "hki_new_withCWB.csv";
		HKI2FilePath = path + "hki_new_withoutCWB.csv";
		NTWFilePath = path + "ntw.csv";
		NTEFilePath = path + "nte.csv";
		ExcludeFilePath = path + "route_elevation.csv";
	}
	
	public LinkToPlanExclusionList(boolean withCWB, Network net, Network TDnet, double lengthRatio) throws FileNotFoundException, IOException {
		ArrayList<String> FilePaths = new ArrayList<String>(Arrays.asList(KLNFilePath, NTWFilePath, NTEFilePath));
		//put correct HKI matching table into to check list
		if(withCWB)
			FilePaths.add(HKIFilePath);
		else
			FilePaths.add(HKI2FilePath);
		
		elevatedLinks = new HashMap<Id<Link>, String>();
		HashMap<String, String> TDLinks = new HashMap<String, String>();
		
		//read and load the TDlinks for matching later
		log.info("Reading TD elevated links ID");
		CSVParser parser = new CSVParser(new FileReader(ExcludeFilePath), CSVFormat.EXCEL.withFirstRecordAsHeader());
		List<CSVRecord> list = parser.getRecords();
		
		for(CSVRecord record : list) {
			TDLinks.put(record.get(TD_ID_MatchColHeader), record.get(TD_ELE_MatchColHeader));
			TDLinks.put(record.get(TD_ID_MatchColHeader)+"R", record.get(TD_ELE_MatchColHeader));	
			//try to put in reverse link as well, the non-existing links won't get caught in the matching table read through assuming the matching tables are correct
		}
		parser.close();
		log.info("Finished reading TD elevated links ID");
		
		//go through all of the matching tables for the IDs
		for(String thisFile: FilePaths) {
			log.info("Reading matching table "+thisFile);
			CSVParser parser2 = new CSVParser(new FileReader(thisFile), CSVFormat.EXCEL.withFirstRecordAsHeader());
			List<CSVRecord> list2 = parser2.getRecords();
			
			for(CSVRecord record : list2) {
				if(TDLinks.containsKey(record.get(MatchColHeader_TDID))) {
					String linkIDInfo = record.get(MatchColHeader).toUpperCase();
					
					int startint = 0;
					boolean found3 = false;
					
					do {
						//Check the matched link
						Matcher match1 = pattern.matcher(linkIDInfo.substring(startint));
						Matcher match2 = fromtopattern.matcher(linkIDInfo.substring(startint));
						boolean found1 = match1.find(); //True if there is only one link
						boolean found2 = match2.find(); //True if it is from and to pattern
						
						//due to match2.start will give illegal state if no found string, the following complex logic is required
						if(found1 && found2) {
							if(match1.start()<match2.start())
								putlink(net, TDnet, match1.group(), record.get(MatchColHeader_TDID), TDLinks.get(record.get(MatchColHeader_TDID)), lengthRatio);
						} else if(found1)
							putlink(net, TDnet, match1.group(), record.get(MatchColHeader_TDID), TDLinks.get(record.get(MatchColHeader_TDID)), lengthRatio);
						
						//check if there are commas and subsequent links
						Matcher match3 = commapattern.matcher(linkIDInfo.substring(startint));
						found3 = match3.find();
						if(found3)
							startint += match3.end();
						
					} while(found3);
				}
			}
			
			parser2.close();
			log.info("Finished reading matching table "+thisFile);
		}
		
		//add links that need to be manually excluded
		for(String TL:ManualExStuff)
			putlink(net, TDnet, TL, "0", "Manually Added", 0);
	}
	
	private static void putlink(Network net, Network TDnet, String linkId, String TDLinkId, String elevation, double lengthRatio) {
		if(ManualExExStuff.contains(linkId)) {	//doesn't add if on the ex-ex-list
		} else if(TDnet.getLinks().containsKey(Id.createLinkId(TDLinkId)) && net.getLinks().containsKey(Id.createLinkId(linkId))) {
			if(TDnet.getLinks().get(Id.createLinkId(TDLinkId)).getLength()/net.getLinks().get(Id.createLinkId(linkId)).getLength()>lengthRatio)
				elevatedLinks.put(Id.createLinkId(linkId), elevation);
		} else //put anyways if somehow either link is not found in both network
			elevatedLinks.put(Id.createLinkId(linkId), elevation);
	}
		
	public Set<Id<Link>> getExclusionLinks(){
		return elevatedLinks.keySet();
	}
	
	public void putAllElevationAttribute(Network net) {
		for(Id<Link> linkId: elevatedLinks.keySet())
			if(net.getLinks().containsKey(linkId)) 
				net.getLinks().get(linkId).getAttributes().putAttribute("Elevation", elevatedLinks.get(linkId));
	}
	
	
	
	
}
