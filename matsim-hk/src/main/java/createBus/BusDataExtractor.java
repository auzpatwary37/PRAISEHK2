package createBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.Dijkstra;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.utils.collections.Tuple;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.healthmarketscience.jackcess.Cursor;
import com.healthmarketscience.jackcess.CursorBuilder;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

import networkFromSaturn.CreateNetworkUtils;

/**
 * A class to create bus route from reading Hong Kong government data. There
 * should be 3 files,
 * 
 * @author eleead
 *
 */
public class BusDataExtractor {
	private File stopFile;
	private File routeStopFile;
	private File routeFile;
	private static HashMap<Integer, List<String>> stopIdToNames;
	private static HashMap<String, List<Integer>> nameToStopIds;
	private static HashMap<String, List<String>> stopNameEquivalence;
	public static final List<String> tunnelsId = new CopyOnWriteArrayList<String>(
			Arrays.asList("101542_101385", "101386_101542", "101660_101386", "101385_101659", "303010_101542", "101542_303010"));

	public static final double SEARCH_DISTANCE = 50;
	
	private final static Logger log = Logger.getLogger(BusDataExtractor.class);

	public BusDataExtractor() throws IOException {
		this(Runbus.PATH+"STOP_BUS.mdb", Runbus.PATH+"RSTOP_BUS.mdb", Runbus.PATH+"ROUTE_BUS.mdb"); // default values
	}

	/**
	 * A constructor to take the files.
	 * 
	 * @param stopFileDir:
	 *            The file that map stop ID with the stop coordinate
	 * @param routeStopFileDir:
	 *            The file that takes route ID, and give the stop ID, stop sequence
	 *            and route sequence of it's stops
	 * @param routeFileDir:
	 *            The file that match route ID with it's operater and route name.
	 * @throws IOException
	 */
	public BusDataExtractor(String stopFileDir, String routeStopFileDir, String routeFileDir) throws IOException {
		stopFile = new File(stopFileDir);
		routeStopFile = new File(routeStopFileDir);
		routeFile = new File(routeFileDir);
		initializeStopEquivalentMap();
	}

	public static void addRouteToRouteStop(String operator, String route, int routeSeq, int stopSeq,
			String busStopNameEnglish) {

	}

	private void boundStopIdToStopName(int id, String name) {
		String nameUpper = name.toUpperCase();
		stopIdToNames.get(id).add(nameUpper);
		if (nameToStopIds.containsKey(nameUpper)) {
			nameToStopIds.get(nameUpper).add(id);
		} else {
			List<Integer> ids = new LinkedList<Integer>();
			ids.add(id);
			nameToStopIds.put(nameUpper, ids);
		}

	}

	private void initializeStopEquivalentMap() throws IOException {
		stopIdToNames = new HashMap<>();
		nameToStopIds = new HashMap<>();
		stopNameEquivalence = new HashMap<>();
		Database db = DatabaseBuilder.open(routeStopFile);
		Table table = db.getTable("RSTOP");
		Cursor cursor = CursorBuilder.createCursor(table);

		cursor.beforeFirst();
		while (cursor.moveToNextRow()) {
			Row currRow = cursor.getCurrentRow();
			int stopId = currRow.getInt("STOP_ID");
			String stopName = currRow.getString("STOP_NAMEE").toUpperCase();

			/*
			 * Step 1: Search through all stop names(Not case sensitive), and put the
			 * possible stop IDs for this name as a list into a map
			 */
			List<String> nameListforId;
			if (stopIdToNames.containsKey(stopId)) {
				nameListforId = stopIdToNames.get(stopId);
			} else {
				nameListforId = new ArrayList<String>();
				stopIdToNames.put(stopId, nameListforId);
			}
			if (!nameListforId.contains(stopName)) {
				nameListforId.add(stopName);
				if (stopName.contains(" BUS TERMINUS")) {
					nameListforId.add(stopName.replace(" BUS TERMINUS", ""));
				}
			}

			/*
			 * Step 2: Search through every stop IDs, and put the possible stop names as
			 * list for the stop ID into a map
			 */
			List<Integer> idListforStopName;
			if (nameToStopIds.containsKey(stopName)) {
				idListforStopName = nameToStopIds.get(stopName);
			} else {
				idListforStopName = new ArrayList<Integer>();
				nameToStopIds.put(stopName, idListforStopName);
			}
			if (!idListforStopName.contains(stopId)) {
				idListforStopName.add(stopId);
			}
		}

		/*
		 * Step 3: Put some manual equivalence into another table
		 */
		boundStopIdToStopName(418, "Queen Elizabeth Stadium, Queen's Road East");
		boundStopIdToStopName(429, "St. Joseph's Primary School, Queen's Road East");
		boundStopIdToStopName(8333, "KITEC, Kai Fuk Road");
		boundStopIdToStopName(6272, "Kwong Fuk Football Ground, Nam Wan Road");
		boundStopIdToStopName(3273, "CENTRAL GOVERNMENT PIER");
		boundStopIdToStopName(10241, "CENTRAL FERRY PIER NO.5");
		boundStopIdToStopName(12584, "ST. PAUL'S HOSPITAL"); // Old name updated for 108
		boundStopIdToStopName(289, "BRAEMAR HILL MANSIONS (HKSYU)"); // Old name updated for 108
		addStopNameEquivalent("Tempo Court, Tin Hau Temple Road", "Pak Fuk Road, Tin Hau Temple Road"); // Changed name
		addStopNameEquivalent("Pok Fu Lam Playground, Pok Fu Lam Road", "Pok Fu Lam Road Playground, Pok Fu Lam Road"); // Name
																														// difference
		addStopNameEquivalent("Lotus Mansion, Taikoo Wan Road", "Lotus Mansion Taikoo Shing, Taikoo Wan Road");
		addStopNameEquivalent("King Fai House, Yue Kwong Road", "King Fai House Yue Fai Court, Yue Kwong Road");
		addStopNameEquivalent("Kwun Tong District Branch Offices, Hip Wo Street", "Tung Yan Street, Hip Wo Street"); // For
																														// 601
		addStopNameEquivalent("Sheung Tak Shopping Centre, Tong Ming Street", "Sheung Tak Plaza, Tong Ming Street"); // For
																														// 692P
		addStopNameEquivalent("Hong Kong Convention & Exhibition Centre, Harbour Road",
				"HK Convention & Exhibition Centre, Harbour Road"); // For 915
		addStopNameEquivalent("HK Auxiliary Police Force Headquarters, Kai Cheung Road",
				"Hong Kong Auxiliary Police Force Headquarters, Kai Cheung Road"); // For 107
		addStopNameEquivalent("Wong Tai Sin Police Station, Choi Hung Road", "Yin Hing Street, Choi Hung Road"); // For
																													// 111
		addStopNameEquivalent("Kim Pik House, Choi Hung access road",
				"Kam Pik House Choi Hung Estate, Choi Hung access road"); // For 111
		addStopNameEquivalent("Hau Tak Shopping Centre, Chung Wa Road", "Chung Wah Road"); // For 694
		addStopNameEquivalent("Kwai Shun House, Hing Fong Road", "Kwai Shun House Kwai Fong Estate, Hing Fong Road"); // For
																														// 930
		addStopNameEquivalent("Wu Pik House, Wu Chui Road", "Wu Pik House Wu King Estate, Wu Chui Road"); // For 962
		addStopNameEquivalent("Wu Poon House, Wu King Road", "Wu Poon House Wu King Estate, Wu King Road"); // For 962
		addStopNameEquivalent("Wu Tsui House, Wu King Road", "Wu Tsui House Wu King Estate, Wu King Road"); // For 962
		addStopNameEquivalent("Nang Fu House, Tin Sau Road", "Nang Fu House Tin Fu Court, Tin Sau Road"); // For 967
		addStopNameEquivalent("Yat Yeung House, Tin Sau Road", "Yat Yeung House Tin Yat Estate, Tin Sau Road"); // For
																												// 967
		addStopNameEquivalent("Chestwood Court, Tin Wing Road", "Chestwood Court Kingswood Villas, Tin Wing Road"); // For
																													// 969P
		addStopNameEquivalent("TSIM SHA TSUI EAST (MODY ROAD)", "Tsim Sha Tsui (Mody Road)"); // For 973
		addStopNameEquivalent("Tai Wo Plaza, Po Nga Road", "Tai Wo Station, Po Nga Road"); // For 307P
		addStopNameEquivalent("Tsing Yi Garden, Fung Shue Wo Road", "Greenview Villa, Fung Shue Wo Road"); // For 948
		addStopNameEquivalent("WANG TUNG STREET, KOWLOON BAY", "Wang Tung Street, Kai Fuk Road"); // For 606X
		addStopNameEquivalent("Yiu Tung Shopping Centre, Yiu Hing Road", "Yiu Tung Estate"); // For 606A
		addStopNameEquivalent("Shui Chuen O Public Transport Interchange", "Shui Chuen O Estate"); // For 682B
		addStopNameEquivalent("Pok Hong Estate Pok Tai House, Shui Chuen Au Street",
				"Pok Tai House Pok Hong Estate, Shui Chuen Au Street"); // For 682B
		addStopNameEquivalent("City One Plaza, Ngan Shing Street", "Fortune City One, Ngan Shing Street"); // For 682C
		addStopNameEquivalent("CHI KIANG ST. PLAYGROUND", "CHI KIANG STREET PLAYGROUND"); // For 108

		// Added for a bypass.
		boundStopIdToStopName(32, "HONG KONG CONVENTION & EXHIBITION CENTRE");
		addStopNameEquivalent("Marsh Road, Hennessy Road", "Wan Chai Fire Station, Hennessy Road"); // 592 changed stop
																									// location on April
		addStopNameEquivalent("Pottinger Street, Connaught Road Central",
				"Queen Victoria Street, Connaught Road Central"); // 780P stop changed on Nov 2015
		addStopNameEquivalent("Ma Tau Kok Road, Ma Tau Chung Road",
				"ARGYLE STREET PLAYGROUND/<br/>Argyle Street Playground, Ma Tau Chung Road"); // 116 changed stop on Jan
																								// 2017
		addStopNameEquivalent("Smithfield, Catchick Street", "Davis Street, Catchick Street"); // 914 changed stop
																								// location
		addStopNameEquivalent("Geranium House, Ma Tau Chung Road", "Ma Tau Kok Road, Ma Tau Chung Road"); // 111 changed
																											// stop
																											// location
		addStopNameEquivalent("Wan Chai Ferry Pier, Convention Avenue",
				"HK Convention & Exhibition Centre, Fleming Road"); // Moved location fo 930
		addStopNameEquivalent("HONG KONG CONVENTION & EXHIBITON CENTRE",
				"HK Convention & Exhibition Centre, Harbour Road"); // 914P moved location
		
		addStopNameEquivalent("Old Pok Fu Lam Kennels, Victoria Road","Victoria Road Zone Sub-station, Victoria Road"); //CTB 47P
		addStopNameEquivalent("Hacienda, Repulse Bay Road", "90 Repulse Bay Road, Repulse Bay Road"); //NWFB 63
		addStopNameEquivalent("Cyberport Road, Victoria Road","Victoria Road Sitting-out Area, Victoria Road"); //CTB 43M
		addStopNameEquivalent("THE HK ACADEMY FOR PERFORMING ARTS","H.K. ACADEMY FOR PERFORMING ARTS");//KMB 936
		addStopNameEquivalent("JU CHING CHU SEC. SCH.","JU CHING CHU SECONDARY SCHOOL"); //KMB 936.
		addStopNameEquivalent("TAK TIN PLAZA","LAM TIN COMPLEX"); //KMB 603
		addStopNameEquivalent("WING KUT STREET","SHEUNG WAN STATION") ;//KMB 934
		addStopNameEquivalent("Rhythm Garden, Prince Edward Road East","King Tai Street, Prince Edward Road East"); //KMB 107
		addStopNameEquivalent("RHYTHM GARDEN", "KING TAI STREET"); //KMB 11B
		addStopNameEquivalent("KWAI SHING EAST BUS TERMINUS", "Kwai Shing (East)"); //CTB 930
		addStopNameEquivalent("KWAI YIP STREET KWAI SHING","Kwai Yip Street, Kwai Shing Circuit"); //CTB 930
		addStopNameEquivalent("NORTH POINT GOVERNMENT PRIMARY SCHOOL","NORTH POINT GOVT PRIMARY SCHOOL"); //KMB 960B
		boundStopIdToStopName(8323, "NGAU CHI WAN MARKET"); //KMB 10
		addStopNameEquivalent("PIK HOI HOUSE", "KAM PIK HOUSE"); //KMB 11C
		addStopNameEquivalent("HOI BUN ROAD PARK", "KWUN TONG PROMENADE"); //KMB 14X
		addStopNameEquivalent("SZE MEI STREET, SAN PO KONG", "TAI YAU STREET, SAN PO KONG"); //KMB 15A
		addStopNameEquivalent("COSMOPOLITAN ESTATE", "TAI TUNG SUN CHUEN"); //KMB 18
		addStopNameEquivalent("HAU WONG ROAD", "LION ROCK ROAD"); //KMB 1A
		addStopNameEquivalent("BUDDHIST TAI HUNG COLLEGE", "BEGONIA HOUSE, SO UK ESTATE"); //KMB 2
		addStopNameEquivalent("MONG KOK RAILWAY STATION", "NELSON STREET MONG KOK");
		addStopNameEquivalent("SAIGON STREET YAU MA TEI", "CHEONG LOK STREET YAU MA TEI");
		addStopNameEquivalent("AUSTIN ROAD WEST", "NGA CHEUNG ROAD(WESTERN HARBOUR CROSSING)");//KMB 203E
		addStopNameEquivalent("TSING YI RAILWAY STATION BUS TERMINUS", "TSING YI STATION");//KMB 248M
		addStopNameEquivalent("CHE KUNG MIU", "CHE KUNG TEMPLE");//KMB 249X
		addStopNameEquivalent("SHA TIN RAILWAY STATION BUS TERMINUS", "SHA TIN STATION");//KMB 263
		addStopNameEquivalent("WONG SHIU CHI MIDDLE SCHOOL", "WONG SHIU CHI SECONDARY SCHOOL");//KMB 274P
		addStopNameEquivalent("WINDFIELD GARDEN", "WING FAI GARDEN");
		addStopNameEquivalent("CITY ONE RAILWAY STATION", "CITYONE STATION"); //KMB 281A
		addStopNameEquivalent("HILTON CENTRE", "HILTON PLAZA"); //KMB 282
		addStopNameEquivalent("KWEILIN STREET SHAM SHUI PO", "PEI HO STREET SHAM SHUI PO"); //KMB 296C
		addStopNameEquivalent("TSEUNG KWAN O INDUSTRIAL ESTATE BUS TERMINUS", "T.K.O. INDUSTRIAL EST. B/T"); //KMB 298E
		addStopNameEquivalent("CHEUNG FAT STREET", "AZALEA HOUSE SO UK ESTATE"); //KMB 2A
		addStopNameEquivalent("PAK TIN ESTATE ON TIN HOUSE", "ON TIN HOUSE PAK TIN ESTATE"); //KMB 2D
		addStopNameEquivalent("PAK TIN ESTATE FU TIN HOUSE", "FU TIN HOUSE PAK TIN ESTATE"); //KMB 2D
		addStopNameEquivalent("SHEK KIP MEI ESTATE BLOCK 34", "MEI YICK HOUSE SHEK KIP MEI ESTATE"); //KMB 2D
		//addStopNameEquivalent("PAK TIN ESTATE SHUI TIN HOUSE", "SHUI TIN HOUSE PAK TIN ESTATE"); //KMB 2F
		//addStopNameEquivalent("KAM FUNG ST. SITTING-OUT", "KAM FUNG STREET SITTING-OUT AREA"); //KMB 2F
		addStopNameEquivalent("KWAI SHING CENTRAL BUS TERMINUS", "KWAI SHING WEST EST.");//KMB 43D
		addStopNameEquivalent("TAI NAN WEST STREET", "KWONG CHEUNG STREET CHEUNG SHA WAN");//KMB 69X
		addStopNameEquivalent("YUEN LONG(FUNG CHEUNG ROAD)", "YUEN LONG (FUNG CHEUNG ROAD) BUS TERMINUS");//KMB 68F
		addStopNameEquivalent("TUNG LOK STREET YUEN LONG", "KUK TING STREET YUEN LONG");//KMB 76K
		addStopNameEquivalent("SAN TIN PUBLIC TRANSPORT INTERCHANGE", "LOK MA CHAU BUS TERMINUS");
		addStopNameEquivalent("MA SIK ROAD FANLING", "GREEN CODE"); // KMB 78K
		addStopNameEquivalent("GALLIPOLI LINE SAN WAI", "SAN WAI BARRACKS"); //KMB 78K
		addStopNameEquivalent("PO LAM BUS TERMINUS", "METRO CITY PHASE II"); //KMB 93M
		addStopNameEquivalent("SAU MAU PING (CENTRAL)", "SAU MAU PING SHOPPING CENTRE"); //KMB 95
		addStopNameEquivalent("NGAN SHING COMMERCIAL CENTRE", "FORTUNE CITY ONE"); //KMB 89X
		addStopNameEquivalent("OI TAI (SOUTH)", "ON TAI (SOUTH) (HANG TAI HOUSE)"); //KMB 88
		addStopNameEquivalent("LO WU ROAD", "LO WU STATION ROAD"); //KMB 73K
		addStopNameEquivalent("Yen Chow Street, Cheung Sha Wan Road", "Pei Ho Street, Cheung Sha Wan Road"); //NWFB 796E
		addStopNameEquivalent("Wan Chai (Convention Centre)", "Golden Bauhinia Square, Expo Drive East"); //NWFB H1
		
		//Jacob additional
		addStopNameEquivalent("FUNG YING SIN KOON", "FUNG YING SEEN KOON");
		addStopNameEquivalent("SIU LEK YUEN BOWLING GREENS", "S.L.Y. BOWLING GREENS");
		addStopNameEquivalent("ASIAWORLD EXPO", "AsiaWorld-Expo, Airport Expo Boulevard");
		addStopNameEquivalent("Sham Shui Po Police Station, Yen Chow Street", "Sham Shui Po (Yen Chow Street)");
		addStopNameEquivalent("Sky City Road, Near Lamp Post TE679", "Sky City Interchange, Sky City Road East");
		addStopNameEquivalent("Tuen Mun Telephone Exchange, Shek Pai Tau Road", "Tai Hing Substation, Shek Pai Tau Road");
		addStopNameEquivalent("PEI HO STREET", "SHEK KIP MEI STREET");
		boundStopIdToStopName(10240, "Lantau Link Toll Plaza (EASTWARD)");
		boundStopIdToStopName(10239, "Lantau Link Toll Plaza (WESTWARD)");
		addStopNameEquivalent("MARY ROSE SCHOOL","LEE WAI LEE TECH.INST.");
		addStopNameEquivalent("BLK.5,TSUI CHUK GARDEN", "BLOCK5,TSUI CHUK GARDEN");
		addStopNameEquivalent("GOOD VIEW NEW VILLAGE", "NOBLE HILL");
		addStopNameEquivalent("RUMSEY STREET", "CENTRAL (RUMSEY ST) B/T");
		addStopNameEquivalent("Sky City Interchange", "Sky City Road, Near Lamp Post TE679");
		addStopNameEquivalent("Heung Yee Kuk New Territories Building", "RAVANA GARDEN");
		addStopNameEquivalent("FIREMAN TRAINING SCHOOL", "Pat Heung JPC Centre");
		
		//MTR bus
		//Tai Po Routes
		addStopNameEquivalent("TAI PO MARKET RAILWAY STATION", "UPTOWN PLAZA");
		addStopNameEquivalent("TAI PO TAI WO ROAD", "TAI PO MEGA MALL");
		//Tun Mun Routes
		addStopNameEquivalent("Tuen Mun Town Centre (LR Town Centre Stop)", "Tuen Mun Town Centre");
		addStopNameEquivalent("SKH St. Peter's Church", "SKH St. Peter's Church (Castle Peak)");
		addStopNameEquivalent("MTR Siu Hong Station (S)", "MTR Siu Hong Station (South)");
		addStopNameEquivalent("Tuen Mun Govt Secondary School", "Tuen Mun Government Secondary School");
		addStopNameEquivalent("Sam Shing Estate (near LR Sam Shing Stop)", "Sam Shing Estate");
		addStopNameEquivalent("MTR Tuen Mun Station", "Tuen Mun Station");
		addStopNameEquivalent("Tuen Mun Ferry Pier (near LR Ferry Pier Terminus)", "Tuen Mun Ferry Pier");
		addStopNameEquivalent("Wu Poon House Wu King Estate", "Wu Boon Hse, Wu King Estate");
		addStopNameEquivalent("Wu Tsui House, Wu King Estate", "Wu Tsui Hse, Wu King Estate");
		addStopNameEquivalent("Tuen Mun Public Riding School", "TM Public Riding School");
		addStopNameEquivalent("EcoPark", "Ginger Bessar");
		addStopNameEquivalent("No.312 Sha Po Kong", "312 Sha Po Kong");
		addStopNameEquivalent("No.177 Sha Po Kong", "177 Sha Po Kong");
		addStopNameEquivalent("No.127 Sha Po Kong", "127 Sha Po Kong");
		addStopNameEquivalent("No.288 Sha Po Kong", "288 Sha Po Kong");
		addStopNameEquivalent("No.351 Sha Po Kong", "351 Sha Po Kong");
		addStopNameEquivalent("So Kwun Wat (Mrs. Cheng Yam On Millennium School)", "Mrs. Cheng Yam On Millennium School");
		addStopNameEquivalent("Hanford Garden (near LR Sam Shing Stop)", "Hanford Garden");
		addStopNameEquivalent("MTR Siu Hong Station (N)", "MTR Siu Hong Station (North)");
		addStopNameEquivalent("Tsing Chung Koon", "Ching Chung Koon");
		addStopNameEquivalent("LR Tuen Mun Stop (MTR Tuen Mun Station)", "LR Tuen Mun Stop");
		addStopNameEquivalent("Lok Sang House, Kin Sang Estate", "Lok Sang House Kin Sang Estate");
		//Yuen Long Routes
		addStopNameEquivalent("YUEN LONG RAILWAY STATION", "MTR Yuen Long Station");
		addStopNameEquivalent("Tai Tong Road (near LR Tai Tong Road Stop)", "Tai Tong Road");
		addStopNameEquivalent("Yuen Long Police Station (near LR Fung Nin Road Stop)", "Yuen Long Police Station");
		addStopNameEquivalent("Ping Hing Lane, Ping Shan (near LR Ping Shan Stop)", "Ping Hing Lane, Ping Shan");
		addStopNameEquivalent("Tin Shing Court (near MTR Tin Shui Wai Station)", "Tin Shing Court");
		addStopNameEquivalent("Tin Shing Court (opposite to MTR Tin Shui Wai Station)", "Tin Shing Court");
		addStopNameEquivalent("Yuen Long Plaza (near LR Fung Nin Road Stop)", "Yuen Long Plaza");
		addStopNameEquivalent("Kuk Ting Street (near LR Tai Tong Road Stop)", "Kuk Ting Street");
		addStopNameEquivalent("Ping Cheong Path (near MTR Long Ping Station)", "Ping Cheong Path");
		addStopNameEquivalent("YL Home for the Aged Blind", "Yuen Long Home for the Aged Blind");
		addStopNameEquivalent("Yuen Long Town Hall (near LR Fung Nin Road Stop)", "Yuen Long Town Hall");
		addStopNameEquivalent("Yuet Ping Hse, Long Ping Estate (near MTR Long Ping Station)", "Yuet Ping House Long Ping Estate");
		addStopNameEquivalent("Chu Ping Hse, Long Ping Estate", "Chu Ping House, Long Ping Estate");
		addStopNameEquivalent("YL Sewage Treatment Works", "Yuen Long Sewage Treatment Works");
		addStopNameEquivalent("Wai Yuen Tong Medicine Building", "Eu Yan Sang Centre");
		addStopNameEquivalent("Telford International Industries.Ltd", "Telford International Industries Ltd");
		addStopNameEquivalent("Lut Sau Hall (near LR Fung Nin Road Stop)", "Lut Sau Hall");
		addStopNameEquivalent("South Yuen Long Gov¡¯t Primary School", "South Yuen Long Government Primary School");
		addStopNameEquivalent("Tai Kiu Tsuen (near MTR Long Ping Station)", "Tai Kiu Tsuen");
		addStopNameEquivalent("YAT TAM HOUSE TIN YAT ESTATE", "Yat Tam House, Tin Yat Estate");
		addStopNameEquivalent("Ching Pik House, Tin Ching Estate", "Ching Pik House Tin Ching Estate");
		addStopNameEquivalent("Tin Kwai Rd Maywood Court", "Maywood Court");
		addStopNameEquivalent("Tin Shing Rd Kenswood Court", "Kenswood Court, Tin Shing Road");
		addStopNameEquivalent("On Ning Road", "Yuen Long On Ning Road");
		addStopNameEquivalent("Tin Lung Rd Kenswood Court", "Kenswood Court");
		addStopNameEquivalent("Tin Kwai Rd Lynwood Court", "Lynwood Court");
		addStopNameEquivalent("Ching Wan House, Tin Ching Estate", "Ching Wan House Tin Ching Estate");
		addStopNameEquivalent("Nang Fu Hse, Tin Fu Court", "Nang Fu House Tin Fu Court");
		addStopNameEquivalent("Ching Pik House, Tin Ching Estate", "Ching Pik House Tin Ching Estate");
		addStopNameEquivalent("TWGHs C.Y.Ma Memorial College (near Long Shin Estate)", "TWGHs C.Y.Ma Memorial College");
		addStopNameEquivalent("Tin Shui Wai Station", "MTR Tin Shui Wai Station");
		
		//Park Island
		addStopNameEquivalent("Ma Wan (Tung Wan Bus Terminus)", "Park Island");
		addStopNameEquivalent("Pak Lam Road, Layby from about 70m north of the southern junction with Pak Lai Road", "Kei Wai Primary School");
		addStopNameEquivalent("Pak Lam Road, Layby near the roundabout", "Beach Commercial Complex");
		addStopNameEquivalent("Tsing Yi Station (General Loading/Unloading Bay)", "MTR Tsing Yi Station");
		addStopNameEquivalent("Pak Lam Road, Layby from about 170m north of the southern junction with Pak Lai Road", "Kei Wai Primary School");
		addStopNameEquivalent("Tin Liu New Village", "Ma Wan Rural Committee Road (North)");
		addStopNameEquivalent("Kwai Fong (Metroplaza)", "MTR Kwai Fong Station");
		addStopNameEquivalent("Pak Lam Road outside Kei Wai Primary School", "Kei Wai Primary School");
		addStopNameEquivalent("Loading and Unloading area within Cathay City", "Cathay City");
		addStopNameEquivalent("Layby at Zone 3, Cheong Hong Road, Terminal 1", "Airport - Terminal 1");
		addStopNameEquivalent("Coach Station, Terminal 2 (Pick-up bays range nos. 19 to 24)", "Airport - Terminal 2");
		addStopNameEquivalent("Pak Lam Road opposite Kei Wai Primary School", "Kei Wai Primary School");
		addStopNameEquivalent("Pak Lam Road opposite Park Island Block 21", "Tin Liu New Village");
		addStopNameEquivalent("Ma Wan (Pak Yan Road)", "Ma Wan");
		addStopNameEquivalent("MTR Tsuen Wan Station", "MTR Tsuen Wan Station (Tai Ho Road Flyover)");
		//Discovery Bay
		addStopNameEquivalent("", "");
		addStopNameEquivalent("", "");
		addStopNameEquivalent("", "");
		addStopNameEquivalent("", "");
		addStopNameEquivalent("", "");
		addStopNameEquivalent("", "");
		addStopNameEquivalent("", "");
		
		
		
		
		
		
		
		
		
		
		
		
		
		
		
	}

	private static void addStopNameEquivalent(String firstStopName, String secondStopName) {
		String firstStopNameUpper = firstStopName.toUpperCase();
		String secondStopNameUpper = secondStopName.toUpperCase();

		if (stopNameEquivalence.containsKey(firstStopNameUpper)) {
			stopNameEquivalence.get(firstStopNameUpper).add(secondStopNameUpper);
		} else {
			List<String> relevantList = new ArrayList<String>();
			relevantList.add(secondStopNameUpper);
			stopNameEquivalence.put(firstStopNameUpper, relevantList);
		}

		if (stopNameEquivalence.containsKey(secondStopNameUpper)) {
			stopNameEquivalence.get(secondStopNameUpper).add(firstStopNameUpper);
		} else {
			List<String> relevantList = new ArrayList<String>();
			relevantList.add(firstStopNameUpper);
			stopNameEquivalence.put(secondStopNameUpper, relevantList);
		}
	}

	/**
	 * Processing the stop names.
	 * 
	 * @param firstStopName
	 * @param secondStopName
	 * @return
	 */
	public static boolean stopNamesEquivalent(String firstStopName, String secondStopName) {
		List<String> firstStopNameCandidates = Lists.newArrayList(firstStopName);
		List<String> secondStopNameCandidates = Lists.newArrayList(secondStopName);
		if (firstStopName.contains("/<br/>")) {
			firstStopNameCandidates.addAll(Arrays.asList(firstStopName.split("/<br/>")));
		}
		if (secondStopName.contains("/<br/>")) {
			secondStopNameCandidates.addAll(Arrays.asList(secondStopName.split("/<br/>")));
		}
		for (String firstName : firstStopNameCandidates) {
			for (String secondName : secondStopNameCandidates) {
				if (stopNameEquivalent(firstName, secondName, false)) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Convert all short form for KMB to long form.
	 * @param stopName
	 * @return
	 */
	private static String KMBConversion(String stopName) {
		return stopName.toUpperCase().replace("EST.", "ESTATE").replace("B/T", "BUS TERMINUS").replace("RAILWAY ", "").replaceAll("STATION", "RAILWAY STATION")
				.replace("SOC.", "SOCIETY").replace("ST.","STREET").replace("COMM.", "COMMERCIAL").replace("GOV’T", "GOVERNMENT");
	}
	
	/**
	 * Return true if every element in first string, splitted by space is in second string
	 * @param firstString
	 * @param secondString
	 * @return
	 */
	private static boolean stringContained(String firstString, String secondString) {
		List<String> secondStringArr = Lists.newArrayList(secondString.split(" "));
		return secondStringArr.containsAll(Lists.newArrayList(firstString.split(" ")));
	}

	/**
	 * A helper function to check if the stop name is equivalent true if they are
	 * considered equivalent, false otherwise
	 * 
	 * @param firstStopName
	 * @param secondStopName
	 * @return
	 */
	public static boolean stopNameEquivalent(String firstStopName, String secondStopName, boolean isKMBComparison) {
		String firstStopNameUpper = firstStopName.toUpperCase();
		String secondStopNameUpper = secondStopName.toUpperCase();

		// Either one contain another one, or substring is equals, or edit distance is
		// less
		if (secondStopNameUpper.contains(firstStopNameUpper) || firstStopNameUpper.contains(secondStopNameUpper)
				|| firstStopNameUpper.substring(0, Math.min(20, firstStopName.length()))
						.equals(secondStopNameUpper.substring(0, Math.min(20, secondStopName.length())))
				|| new LevenshteinDistance(2).apply(firstStopNameUpper, secondStopNameUpper) != -1 // The edit distance
																									// is less than 3
				|| stringContained(firstStopNameUpper, secondStopNameUpper)
		) {
			return true;
		}

		// Check if the stop Name is in other names or not.
		if (lookupFromstopNameIdTables(firstStopNameUpper, secondStopNameUpper)
				|| lookupFromstopNameIdTables(secondStopNameUpper, firstStopNameUpper)) {
			return true;
		}

		// Check the direct stop name equivalence table.
		if (stopNameEquivalence.containsKey(firstStopNameUpper)) {
			if (stopNameEquivalence.get(firstStopNameUpper).contains(secondStopNameUpper)) {
				return true;
			}
		}
		
		if(!isKMBComparison && stopNameEquivalent(KMBConversion(firstStopNameUpper),KMBConversion(secondStopNameUpper), true)) {
			return true;
		}else if(firstStopNameUpper.replace("STATION", "RAILWAY STATION BUS TERMINUS").equals(secondStopNameUpper)) {
			return true;
		}

		return false;
	}

	public static boolean lookupFromstopNameIdTables(String stopNameForLookUp, String stopNameToLookUp) {
		List<Integer> stopIds = nameToStopIds.get(stopNameForLookUp);
		if (stopIds != null) {
			for (int stopId : stopIds) {
				for (String stopName : stopIdToNames.get(stopId)) {
					if (stopName.contains(stopNameToLookUp) || stopNameToLookUp.contains(stopName)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * This helper function converts a list of link from Link obejct to Id<Link>
	 * 
	 * @param linkList
	 *            List to be modified
	 * @return
	 */
	public static List<Id<Link>> convertListFromObjectToId(List<Link> linkList) {
		List<Id<Link>> closeLinksIdList = new LinkedList<Id<Link>>();
		for (Link l : linkList) {
			closeLinksIdList.add(l.getId());
		}
		return closeLinksIdList;
	}
	
	/**
	 * 
	 * @param routeSeq
	 * @param routeName
	 * @return True if it is entering HKI
	 */
	private static Tuple<String, Boolean> findTunnelAndDirection(RouteSeqContainer routeSeq, String routeName) {
		boolean crossHarbourFromHKI = false;
		boolean crossHarbourToHKI = false;
		// First run for one time to figure out which tunnel it is to be pass through
		for (Tuple<String, String> stop : routeSeq.getStopIDandNameList()) {
			int stopID = Integer.parseInt(stop.getFirst());
			// An exception for 621 and 619X, which does not stop at the EHC station.
			if ((routeName.equals("621") && stopID == 1361) || (routeName.equals("619X") && stopID == 1124)) {
				return new Tuple<String, Boolean>("EHC", true);
			} else if ((routeName.equals("962C") && stopID == 9773)
					|| (routeName.equals("969C") && stopID == 3274)) {
				return new Tuple<String, Boolean>("WHC", true);
			} else if (routeName.equals("101X") && stopID == 7136) {
				return new Tuple<String, Boolean>("CHT", true);
			} else if (routeName.equals("182X") && stopID == 845) {
				return new Tuple<String, Boolean>("CHT", false);
			}

			if (stopID == 1063 || stopID == 9015 || stopID == 921) {
				crossHarbourFromHKI = true;
			}else if (stopID == 885 || stopID == 9014 || stopID == 219 || stopID == 911) {
				crossHarbourToHKI = true;
			}
			
			if (stopID == 1063 || stopID == 885) {
				return new Tuple<String, Boolean>("EHC", crossHarbourToHKI);
			} else if (stopID == 9015 || stopID == 9014 || stopID == 219) {
				return new Tuple<String, Boolean>("CHT", crossHarbourToHKI);
			} else if (stopID == 921 || stopID == 911) {
				return new Tuple<String, Boolean>("WHC", crossHarbourToHKI);
			}
		}
		throw new RuntimeException("A cross harbour route does not stops at tunnel station!"); //If it is not found

	}
	
	public static boolean ignoreTheStop(BusStop prev, int stopID) {
		if (prev != null && ((prev.getStopId().contains("10208") && stopID == 10208)
				|| (prev.getStopId().contains("10209") && stopID == 10209) || (prev.getStopId().contains("13081") && stopID==13081) ||
				(prev.getStopId().contains("7026") && stopID == 7026) ||(prev.getStopId().contains("8207") && stopID == 8207) ||
				(prev.getStopId().contains("1326") && stopID == 1326) ||(prev.getStopId().contains("13184") && stopID == 13184)||
				(prev.getStopId().contains("13185") && stopID == 13185) || (prev.getStopId().contains("9599") && stopID == 9599) ||
				(prev.getStopId().contains("9598") && stopID == 9598) || (prev.getStopId().contains("12400") && stopID == 12400) ||
				(prev.getStopId().contains("6261") && stopID == 6261) || (prev.getStopId().contains("6635") && stopID == 6635) ||
				(prev.getStopId().contains("6011") && stopID == 6011) || (prev.getStopId().contains("13102") && stopID == 13102) ||
				(prev.getStopId().contains("9982") && stopID == 9982) || (prev.getStopId().contains("1098") && stopID == 1098) ||
				(prev.getStopId().contains("6079") && stopID == 6079) || (prev.getStopId().contains("853") && stopID == 853)   ||
				(prev.getStopId().contains("6154") && stopID == 6154) || (prev.getStopId().contains("6236") && stopID == 6236) ||
				(prev.getStopId().contains("6082") && stopID == 6082) || (prev.getStopId().contains("931") && stopID == 931) ||
				(prev.getStopId().contains("6564") && stopID == 6564))) {
			return true; // Exception for Tai Lam Tunnel and Tuen Mun Highway Interchange and Wong Tai Sin B/T, Shing Mum Tunnel, 
			//Tsing Sha Interchange, Hin King, ST Station and Hang On
		}
		return false;
	}

	/**
	 * Create bus routes and map it into the route
	 * 
	 * @param scenario
	 * @param operator
	 *            The operator, e.g. CTB
	 * @param routeName
	 *            Route Name, e.g. 1
	 * @param routeList
	 *            The list of bus stop in route,
	 * @param HKIOnly true, if the route is created on HKI only.
	 * @return BusRouteB, created by LinktoLink
	 * @throws IOException
	 */
	public BusRouteB createBusRouteB(Scenario scenario, String operator, String routeName,
			RouteSeqContainer routeSeq, boolean HKIOnly, HashMap<Integer,List<Id<Link>>> stopBindings) throws IOException {
		BusStop prev = null;
		// The least cost path is calculated by the link to link distance
		BusPathCalculator routingAlgo = new L2lLeastCostCalculatorFactory(scenario,
				Sets.newHashSet(TransportMode.car, "bus")).getRoutingAlgo(); 

		BusRouteB route = new BusRouteB(scenario, operator, routeName, routingAlgo, 
				routeSeq.getRouteId(), routeSeq.getRouteSequence()); 
		
		boolean crossHarbourFromHKI = false;
		boolean crossHarbourToHKI = false;
		String currTunnel = null;
		
		if(HKIOnly) {
			// For the route named 1XX, 3XX, 6XX or 9XX, see it's direction.
			if (isRouteCrossHarbour(routeName)) {
				Tuple<String, Boolean> tunnelAndDirection = findTunnelAndDirection(routeSeq, routeName);
				currTunnel = tunnelAndDirection.getFirst();
				crossHarbourToHKI = tunnelAndDirection.getSecond();
				crossHarbourFromHKI = !tunnelAndDirection.getSecond();
			}
		}
		
		boolean passedCrossHarbourTunnel = false;
		for (Tuple<String, String> stop : routeSeq.getStopIDandNameList()) {
			int stopID = Integer.parseInt(stop.getFirst());
			Coord coord = this.getCoordinateByStopId(stopID);

			List<Id<Link>> closeLinksList;

			if(!HKIOnly) {
				closeLinksList = getNearestLinksExactlyByMath(scenario.getNetwork(), coord, SEARCH_DISTANCE, 
						Sets.newHashSet(TransportMode.car, "bus"));
			}else {
				if ((passedCrossHarbourTunnel && crossHarbourFromHKI) || (!passedCrossHarbourTunnel && crossHarbourToHKI)) {
					closeLinksList = Lists.newArrayList(Id.createLinkId(currTunnel));
				} else {
					closeLinksList = getNearestLinksExactlyByMath(scenario.getNetwork(), coord, SEARCH_DISTANCE, 
							Sets.newHashSet(TransportMode.car, "bus"));
				}
			}
			
			//migrated to the new bus stop binding list
//			if(stopID == 371){ // A fix for the wrongly assigned stop place
//				closeLinksList = Lists
//						.newArrayList(scenario.getNetwork().getLinks().get(Id.createLinkId("303060_303061")));
//			} else if (stopID == 378) {
//				closeLinksList = Lists
//						.newArrayList(scenario.getNetwork().getLinks().get(Id.createLinkId("101129_101130")));
//			} else if (stopID == 3226 || stopID == 1538){
//				closeLinksList = Lists
//						.newArrayList(scenario.getNetwork().getLinks().get(Id.createLinkId("303140_303141")));
//			} else if (stopID == 650 || stopID == 1537) {
//				closeLinksList = Lists
//						.newArrayList(scenario.getNetwork().getLinks().get(Id.createLinkId("303141_303140")));
//			}
//			
//			if(!HKIOnly) {
//				if(stopID == 13184) {
//					closeLinksList = Lists.newArrayList(scenario.getNetwork().
//							getLinks().get(Id.createLinkId("656434_408371")));
//				}else if (stopID == 13185) {
//					closeLinksList = Lists.newArrayList(scenario.getNetwork().
//							getLinks().get(Id.createLinkId("408371_656434")));
//				}else if (stopID == 921) {
//					closeLinksList = Lists.newArrayList(scenario.getNetwork().
//							getLinks().get(Id.createLinkId("401851_404008")));
//				}else if (stopID == 911) {
//					closeLinksList = Lists.newArrayList(scenario.getNetwork().
//							getLinks().get(Id.createLinkId("404008_401851")));
//				}
//			}
			
			closeLinksList.removeIf(x -> tunnelsId.contains(x.toString()));

			String stopIDString = stop.getFirst();
			if(HKIOnly) {
				//Modify stop ID string
				if (stopID == 885 || stopID == 9014 || stopID == 219 || stopID == 911) {
					// Do nothing for the tunnel changing station.
				} else if ((passedCrossHarbourTunnel && crossHarbourFromHKI)
						|| (!passedCrossHarbourTunnel && crossHarbourToHKI)) {
					stopIDString = stop.getFirst() + "_" + routeName;
				}
				// Determine if it is crossed harbour or not.
				if (stopID == 1063 || stopID == 9015 || stopID == 921 || stopID == 885 || 
						stopID == 9014 || stopID == 911	|| stopID == 219) {
					passedCrossHarbourTunnel = !passedCrossHarbourTunnel;
				} else if ((routeName.equals("621") && stopID == 1361) || (routeName.equals("619X") && stopID == 1124)
						|| (routeName.equals("182X") && stopID == 845)) {
					passedCrossHarbourTunnel = true;
				} else if ((routeName.equals("962C") && stopID == 9773) || (routeName.equals("101X") && stopID == 7136) ||
						(routeName.equals("969C") && stopID == 3274) ) {
					passedCrossHarbourTunnel = true;
				}
			}
			
			if(stopBindings.containsKey(stopID)) {
				List<Id<Link>> tempLinkLists = stopBindings.get(stopID);
				if(tempLinkLists.get(0)!=null) {
					closeLinksList = tempLinkLists;
				}
			}
			
			if (ignoreTheStop(prev, stopID)) {
				continue;
			}

			BusStop curr = new BusStop("bus", stopIDString, stop.getSecond(), coord, closeLinksList);

			if (prev != null && prev.getStopId().equals(curr.getStopId())) {
				throw new IllegalArgumentException("Two consecutive stops in the same station!");
			}
			route.addBusStop(prev, curr);
			prev = curr;
		}
		if(!HKIOnly && (passedCrossHarbourTunnel || crossHarbourFromHKI || crossHarbourToHKI)) {
			throw new RuntimeException("Some error in your code!");
		}
		route.mapBusStop();
		return route;
	}

	/**
	 * Get the inside HKI bus routes.
	 */
	public List<Route> getHKIOperatorsAndRoute() throws IOException {
		List<Route> routes = new LinkedList<Route>();

		Database db = DatabaseBuilder.open(routeFile);
		Table table = db.getTable("ROUTE");
		Cursor cursor = CursorBuilder.createCursor(table);
		cursor.beforeFirst();
		while (cursor.moveToNextRow()) {
			Row currRow = cursor.getCurrentRow();

			String operator = currRow.getString("COMPANY_CODE");

			// The bus route should be operated by either CTB or NWFB.
			if (!operator.equals("CTB") && !operator.equals("NWFB")) {
				continue;
			}

			// The bus route name should be less than 2 digits, or 5XX, or 7XX but not 79X
			String routeName = currRow.getString("ROUTE_NAMEE");
			if(routeName.matches("2[0|2]")) {
				continue; //Not creating 20 and 22.
			}
			
			if (routeName.matches("\\d\\d[A-Z]?") || routeName.matches("\\d[A-Z]?")
					|| routeName.matches("7[1-8]\\d[A-Z]?") || routeName.matches("5\\d\\d[A-Z]?")) {

				Route currRoute = new Route(operator, routeName, currRow.getInt("ROUTE_ID"), currRow.getBigDecimal("FULL_FARE").doubleValue());
				if (!routes.contains(currRoute)) {
					routes.add(currRoute);
				} else {
					Route prevRoute = routes.get(routes.indexOf(currRoute));
					double fullFare = currRow.getBigDecimal("FULL_FARE").doubleValue();
					if (fullFare > prevRoute.getFullFare()) {
						prevRoute.setFullFare(fullFare);
					}
					prevRoute.addRouteId(currRow.getInt("ROUTE_ID"));
				}
			}
		}

		return routes;
	}

	/**
	 * A helper function to determine if a route is cross harbour or not by its
	 * name.
	 * 
	 * @param routeName
	 * @return
	 */
	private boolean isRouteCrossHarbour(String routeName) {
		if (routeName.matches("6\\d\\d([a-zA-Z]?)") || routeName.matches("3(0|7)\\d([a-zA-Z]?)")
				|| routeName.matches("1\\d\\d([a-zA-Z]?)") || routeName.matches("9\\d\\d([a-zA-Z]?)")) {
			if (!routeName.equals("629"))
				return true;
		}
		return false;
	}
	
	/**
	 * Get the bus routes
	 * 
	 * @crossHarbour true, if getting bus routes only.
	 * @return
	 * @throws IOException
	 */
	public List<Route> getOperatorsAndRoute(boolean crossHarbour) throws IOException {
		List<Route> routes = new LinkedList<Route>();

		Database db = DatabaseBuilder.open(routeFile);
		Table table = db.getTable("ROUTE");
		Cursor cursor = CursorBuilder.createCursor(table);
		cursor.beforeFirst();
		while (cursor.moveToNextRow()) {
			Row currRow = cursor.getCurrentRow();

			String operator = currRow.getString("COMPANY_CODE");

			// The bus route name should be 1XX, 30X, 6XX or 9XX, probably with a alphabet
			// afterward.	LRTFeeder
			String routeName = currRow.getString("ROUTE_NAMEE");
			
			if (!crossHarbour || isRouteCrossHarbour(routeName)) {

				if (operator.contains("CTB")) {
					operator = "CTB";
				} else if (operator.contains("NWFB")) {
					operator = "NWFB";
				} else if (operator.contains("KMB")) {
					operator = "KMB";
				} else if(crossHarbour){
					throw new RuntimeException("The route is neither KMB, NWFB or CTB!");
				} else if(operator.contains("LWB")){
					operator = "LWB";
				} else if(operator.contains("LRTFeeder")) {
					operator = "LRTFeeder";
				} else if(operator.contains("PI")) {
					operator = "PI";
				}else {
					continue;
				}

				Route currRoute = new Route(operator, routeName, currRow.getInt("ROUTE_ID"), currRow.getBigDecimal("FULL_FARE").doubleValue());
				if (!routes.contains(currRoute)) {
					routes.add(currRoute);
				} else {
					Route prevRoute = routes.get(routes.indexOf(currRoute));
					double fullFare = currRow.getBigDecimal("FULL_FARE").doubleValue();
					if (fullFare > prevRoute.getFullFare()) {
						prevRoute.setFullFare(fullFare);
					}
				}
			}
		}

		return routes;
	}

	public Coord getCoordinateByStopId(int stopID) throws IOException {
		Database db = DatabaseBuilder.open(stopFile);
		Table table = db.getTable("STOP");
		Cursor cursor = CursorBuilder.createCursor(table);
		boolean found = cursor.findFirstRow(Collections.singletonMap("STOP_ID", stopID));
		if (found) {
			return new Coord((Integer) cursor.getCurrentRowValue(table.getColumn("X")) - 800000,
					(Integer) cursor.getCurrentRowValue(table.getColumn("Y")) - 800000);
		}
		db.close();
		throw new IllegalArgumentException("The stopID " + stopID + " does not exist in the table.");
	}

	/**
	 * It returns a list of route sequence object.
	 * 
	 * @param routeID
	 * @return A list (usually two directions) of list of tuple, with first as
	 *         stopID and and second as stop name in sequence
	 * @throws IOException
	 */
	public List<RouteSeqContainer> getRouteStopIDandName(String routeID) throws IOException {
		ArrayList<RouteStop> tempRouteStop = new ArrayList<RouteStop>();
		Database db = DatabaseBuilder.open(routeStopFile);
		Table table = db.getTable("RSTOP");
		Cursor cursor = CursorBuilder.createCursor(table);
		//Input the route stop, and sort them
		for (Row row : cursor.newIterable().setMatchPattern("ROUTE_ID", routeID)) {
			RouteStop routeStop = new RouteStop(row.get("STOP_ID"), row.get("STOP_NAMEE"), row.get("STOP_SEQ"),
					row.get("ROUTE_SEQ"));
			tempRouteStop.add(routeStop);
		}
		Collections.sort(tempRouteStop); //The sorting
		List<RouteSeqContainer> routeSequenceList = new ArrayList<RouteSeqContainer>();

		// Map<String, String> stopIDandName = new LinkedHashMap<String, String>();
		int prevRouteSEQ = 1;
		RouteSeqContainer routeSequence = new RouteSeqContainer(Integer.parseInt(routeID), 1);
		for (RouteStop routeStop : tempRouteStop) {
			// If the route sequence changed, create a new map to store another line.
			if (prevRouteSEQ != routeStop.getRouteSequence()) {
				routeSequenceList.add(routeSequence);
				routeSequence = new RouteSeqContainer(Integer.parseInt(routeID), 2);
			}
			routeSequence.addStop(routeStop.getStopID(), routeStop.getStopName());
			prevRouteSEQ = routeStop.getRouteSequence(); // Store the sequence number
		}
		routeSequenceList.add(routeSequence);
		db.close();
		return routeSequenceList;
	}

	/**
	 * Find the route ID, given the company and the name of the route
	 * 
	 * @param company
	 * @param name
	 * @return
	 * @throws IOException
	 */
	@Deprecated
	public String findRouteID(String company, String name) throws IOException {
		return this.findRouteIDs(company, name).get(0);
	}

	public List<String> findRouteIDs(String operator, String name) throws IOException {
		List<String> routeIDs = new ArrayList<String>();

		Database db = DatabaseBuilder.open(routeFile);
		Table table = db.getTable("ROUTE");
		Cursor cursor = CursorBuilder.createCursor(table);
		cursor.beforeFirst();
		while (cursor.moveToNextRow()) {
			Row row = cursor.getCurrentRow();
			if (row.getString("COMPANY_CODE").contains(operator) && row.getString("ROUTE_NAMEE").equals(name)) {
				System.out.println(String.format("The route ID is %s, and fare is $%s.",
						cursor.getCurrentRowValue(table.getColumn("ROUTE_ID")),
						cursor.getCurrentRowValue(table.getColumn("FULL_FARE"))));
				routeIDs.add("" + cursor.getCurrentRowValue(table.getColumn("ROUTE_ID")));
			}
		}
		db.close();
		if (routeIDs.size() > 0) {
			return routeIDs;
		} else {
			throw new IllegalArgumentException("The route " + operator + " " + name + " does not exist in the table.");
		}
	}

	/**
	 * Test: If the network link is the same as before.
	 * 
	 * @param n
	 * @param coord
	 * @param maxDistance
	 * @return
	 */
	public static List<Link> getNearestLinksExactly(Network n, Coord coord, double maxDistance, Set<String> allowedModes) {
		if (maxDistance > 100) {
			log.warn("The distance searching is too large: " + maxDistance);
		}
		List<Link> linkList = new ArrayList<Link>();
		List<Link> removedLinkList = new ArrayList<Link>();
		
		//Remove the tunnel to get rid of being searched.
		for(String tunnelIdString: tunnelsId) {
			Link link = n.removeLink(Id.createLinkId(tunnelIdString));
			if(link!=null) {
				removedLinkList.add(link);
			}
		}
		
		//Get the first link
		Link linkObtained = NetworkUtils.getNearestLinkExactly(n, coord);
		linkList.add(linkObtained);

		// If the distance is too large, we enlarge the search distance.
		if (CreateNetworkUtils.getDistance(linkObtained, coord) > maxDistance) {
			//It is for getting the reverse links
			for (Link link : removedLinkList) {
				n.addLink(link);
			}
			return getNearestLinksExactly(n, coord, CreateNetworkUtils.getDistance(linkObtained, coord) + 1, allowedModes);
		}

		while (CreateNetworkUtils.getDistance(linkObtained, coord) <= maxDistance) {
			Link link = n.removeLink(linkObtained.getId());
			removedLinkList.add(link); // Store the link for future replace

			if (link == null) {
				throw new RuntimeException("Link " + linkObtained.getId() + " is not in the network.");
			}
			linkObtained = NetworkUtils.getNearestLinkExactly(n, coord);
			linkList.add(linkObtained);
		}

		// Add the link back
		for (Link link : removedLinkList) {
			n.addLink(link);
		}

		// Ensure the link obtained is of suitable mode
		for (Iterator<Link> itlink = linkList.iterator(); itlink.hasNext();) {
			Link tempLink = itlink.next();
			if (Sets.intersection(tempLink.getAllowedModes(), allowedModes).size()==0) {
				itlink.remove();
			}
		}

		if (linkList.isEmpty()) {
			return getNearestLinksExactly(n, coord, maxDistance + 10, allowedModes);
		} else {
			return linkList;
		}
	}
	
	public static List<Id<Link>> getNearestLinksExactlyByMath(Network net, Coord cord, double maxDistance, Set<String> AllowedModes){
		return getNearestLinksExactlyByMath(net, cord, maxDistance, AllowedModes, 300, Collections.emptySet());
	}
	
	/**
	 * Gets the links closest to a Coord<br>
	 * Does not modify net to find links, and is safe for use in threads<br>
	 * Finds the distance from Coord to Link, and then check if once coord shifted by d lies on the link<br>
	 * Gets Links to/from nearest node regardless of search distance after found links within search distance<br>
	 * Max Distance increments currently set at 20<p>
	 * Math: Rotates link by 90deg, denote as vector v<br>
	 * vector r, from cord to link from node<br>
	 * vector d, projection r onto v<br>
	 * cord moves by vector d, check if result within bounding box of link
	 * @param net
	 * @param cord
	 * @param maxDistance
	 * @param AllowedModes 
	 * @param maxSpeedAllowed - freespeed of links must be lower than
	 * @return List of Links
	 * @author JLo
	 */
	public static List<Id<Link>> getNearestLinksExactlyByMath(Network net, Coord cord, double maxDistance, Set<String> AllowedModes, double maxSpeedAllowed, Set<Id<Link>> exclusionLinks){
		if (maxDistance > 100) {
			log.warn("The distance searching is too large: " + maxDistance);
		}
		List<Id<Link>> linkIdList = new ArrayList<Id<Link>>();
		
		while(linkIdList.isEmpty()) {			//to ensure getting links
			for(Node TNode:NetworkUtils.getNearestNodes(net, cord, maxDistance))	{
				for(Link TTLink:TNode.getInLinks().values())
					if(!linkIdList.contains(TTLink.getId())
							&& TTLink.getFreespeed()<=maxSpeedAllowed
							&& !tunnelsId.contains(TTLink.getId().toString())
							&& !exclusionLinks.contains(TTLink.getId())
							&& Sets.intersection(TTLink.getAllowedModes(), AllowedModes).size()>0) {
						linkIdList.add(TTLink.getId());
					}
				for(Link TTLink:TNode.getOutLinks().values())
					if(!linkIdList.contains(TTLink.getId())
							&& TTLink.getFreespeed()<=maxSpeedAllowed
							&& !tunnelsId.contains(TTLink.getId().toString())
							&& !exclusionLinks.contains(TTLink.getId())
							&& Sets.intersection(TTLink.getAllowedModes(), AllowedModes).size()>0 ) {
						linkIdList.add(TTLink.getId());
					}
			}
			for(Link TLink:net.getLinks().values()) {
				if(!linkIdList.contains(TLink.getId())
						&& TLink.getFreespeed()<=maxSpeedAllowed
						&&!tunnelsId.contains(TLink.getId().toString())															//check tunnel links
						&& !exclusionLinks.contains(TLink.getId())
						&& Sets.intersection(TLink.getAllowedModes(), AllowedModes).size()>0) {	//check allowed modes	
//					if(TLink==Id.createLinkId("401420_401419"))		//for debug certain links
//						maxDistance=maxDistance;
					//math stuff
					double x1 = TLink.getFromNode().getCoord().getX();
					double y1 = TLink.getFromNode().getCoord().getY();
					double x2 = TLink.getToNode().getCoord().getX();
					double y2 = TLink.getToNode().getCoord().getY();
					double absv = Math.sqrt(Math.pow(x2-x1, 2)+Math.pow(y2-y1, 2));		//the magnitude of vector v
					double unitvdotr = (x2-x1)*(y1-cord.getY())-(x1-cord.getX())*(y2-y1);	//unit vector v dot r
					double d = Math.abs(unitvdotr)/absv;
					double ddir = unitvdotr/Math.abs(unitvdotr)*-1;		//*unitvdotr/Math.abs(unitvdotr) is a terrible solution to +-vector d, but it works
					
					if(d<maxDistance) {
						double x3 = cord.getX()+d*(y2-y1)/absv*ddir;	//shifts cord by vector d
						double y3 = cord.getY()-d*(x2-x1)/absv*ddir;
						if((Math.max(x1,x2)>=x3 && Math.min(x1,x2)<=x3) && (Math.max(y1,y2)>=y3 && Math.min(y1,y2)<=y3)) 	
							//check if new point lies in-between the link, assumes new point is correctly calculated on the link
							linkIdList.add(TLink.getId());
					}
//					if(TLink.getId()==Id.createLinkId("401420_401419"))		//for debug certain links
//						continue;
				}
			}
			maxDistance = maxDistance+50;		//not sure the proper distance
		} 			
		
		//get nearest node, as a safety mechanism		!!!BAD IDEA!!!
//		Node TNode0 = NetworkUtils.getNearestNode(net, cord);
//		for(Id<Link> TTLink:TNode0.getInLinks().keySet())
//			if(!linkIdList.contains(TTLink)
//					&& Sets.intersection(net.getLinks().get(TTLink).getAllowedModes(), AllowedModes).size()>0 && !tunnelsId.contains(TTLink.toString()) )
//				linkIdList.add(TTLink);
//		for(Id<Link> TTLink:TNode0.getOutLinks().keySet())
//			if(!linkIdList.contains(TTLink)
//					&& Sets.intersection(net.getLinks().get(TTLink).getAllowedModes(), AllowedModes).size()>0 && !tunnelsId.contains(TTLink.toString()) )
//				linkIdList.add(TTLink);
		return linkIdList;
	}
	
	/**
	 * Implements the Math formula for getting the distance from a point to a line
	 * @param link
	 * @param Cord
	 * @return Distance
	 * @author JLo
	 */
	@Deprecated
	public static double getDistanceFromCoordToLink(Link link, Coord cord) {
		double A = Math.abs(
				(link.getFromNode().getCoord().getX()-link.getToNode().getCoord().getX())*(link.getToNode().getCoord().getY()-cord.getY())-
				(link.getToNode().getCoord().getX()-cord.getX())*(link.getFromNode().getCoord().getY()-link.getToNode().getCoord().getY()));
		double B = Math.sqrt(
				Math.pow((link.getFromNode().getCoord().getX()-link.getToNode().getCoord().getX()),2)+
				Math.pow((link.getFromNode().getCoord().getY()-link.getToNode().getCoord().getY()), 2));
		return A/B;
	}
	
	
	/**
	 * A container for the route with different route sequence
	 * Includes route ID, route sequence and a list of stops.
	 * @author eleead
	 *
	 */
	public class RouteSeqContainer{
		private int routeId;
		private int routeSeq;
		
		List<Tuple<String, String>> stopIDandNameList;
		
		/**
		 * Constructor for the Route Sequence object
		 * @param routeId
		 * @param routeSeq
		 */
		RouteSeqContainer(int routeId, int routeSeq){
			this.routeId = routeId;
			this.routeSeq = routeSeq;
			this.stopIDandNameList = Collections.synchronizedList(new ArrayList<>());
		}
		
		public void addStop(String stopID, String stopName) {
			stopIDandNameList.add(new Tuple<String, String>(stopID, stopName));
		}
		
		public int getRouteId() {
			return this.routeId;
		}
		
		public int getRouteSequence() {
			return this.routeSeq;
		}
		
		public List<Tuple<String, String>> getStopIDandNameList(){
			return this.stopIDandNameList;
		}
		
	}

	/**
	 * Just a container include the operator(e.g. CTB), routeName(e.g. 8X) and full fare.
	 * @author eleead
	 *
	 */
	public class Route {
		private String operator;
		private String routeName;
		private List<Integer> routeIdList;
		private double fullFare;

		public Route(String operator, String routeName, int routeId, double fullFare) {
			this.operator = operator;
			this.routeName = routeName;
			this.setRouteIdList(Lists.newArrayList(routeId));//new ArrayList<Integer>();
			this.setFullFare(fullFare);
		}

		public String getOperator() {
			return operator;
		}

		public String getRouteName() {
			return routeName;
		}

		public double getFullFare() {
			return fullFare;
		}
		
		public void addRouteId(int routeId) {
			this.routeIdList.add(routeId);
		}

		public void setRouteIdList(List<Integer> routeIdList) {
			this.routeIdList = routeIdList;
		}

		public boolean equals(Object o) {
			Route other = (Route) o;
			if (other.getOperator().equals(this.operator) && this.routeName.equals(other.getRouteName())) {
				return true;
			} else {
				return false;
			}
		}

		public String toString() {
			return this.operator + " " + this.routeName;
		}

		public void setFullFare(double fullFare) {
			this.fullFare = fullFare;
		}
	}

	/**
	 * A class to store the stop information, includes stop ID, stop name and its
	 * sequence.
	 * 
	 * @author eleead
	 *
	 */
	private class RouteStop implements Comparable<RouteStop> {
		private String stopID;
		private String stopName;
		private Integer stopSequence;
		private Integer routeSequence;

		public RouteStop(Object stopID, Object stopName, Object stopSequence, Object routeSequence) {
			this.stopID = (Integer) stopID + "";
			this.stopName = (String) stopName;
			this.stopSequence = (Integer) stopSequence;
			this.routeSequence = (Integer) routeSequence;
		}

		@Override
		public int compareTo(RouteStop o) {
			if (routeSequence == o.getRouteSequence()) {
				return stopSequence.compareTo(o.getStopSequence());
			} else {
				return routeSequence.compareTo(o.getRouteSequence());
			}
		}

		public String getStopID() {
			return stopID;
		}

		public String getStopName() {
			return stopName;
		}

		public int getStopSequence() {
			return stopSequence;
		}

		public int getRouteSequence() {
			return routeSequence;
		}

		@Override
		public String toString() {
			return stopName + " " + stopID;
		}

	}
}
