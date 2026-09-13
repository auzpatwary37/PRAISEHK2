/**
 * 
 */
package createPTGTFS;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * @author JLo
 *
 */
public class ReaderFerry extends ReaderPT {
	private boolean HKIOnly;
	//TODO
	//Option A: Within HKI routes, only one from Aberdeen to Ap Lei Chau
	private final List<String> HKIOnlyRouteList = Arrays.asList("7051", "7052");
	//Option B: All the ferry routes that originates from HKI piers
	private final Pattern Pattern_Central = Pattern.compile("CENTRAL");
	private final Pattern Pattern_Aberdeen = Pattern.compile("ABERDEEN");
	private final Pattern Pattern_NorthPoint = Pattern.compile("NORTH POINT");
	private final Pattern Pattern_WanChai = Pattern.compile("WAN CHAI");
	private final Pattern Pattern_SaiWanHo = Pattern.compile("SAI WAN HO");
	
	public ReaderFerry(String FileDir, boolean HKIOnly) {
		super(FileDir, Arrays.asList("FERRY"));
		this.HKIOnly = HKIOnly;
	}
	
	/**
	 * reads routes file to extract ferry routes
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	@Override
	protected void routesReader() throws FileNotFoundException, IOException {
		log.info("Starting to read routes");
		CSVParser parser = new CSVParser(new FileReader(FileDir+"routes.txt"), CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreSurroundingSpaces().withNullString("null"));
		List<CSVRecord> list = parser.getRecords();
		for(CSVRecord record:list) {
			if(this.agencyWhitelist.contains(record.get("agency_id"))) {
				if(HKIOnly) {	//Sacrificing rows for less code invoked
					if(isRouteHKI(record.get("route_long_name")) || HKIOnlyRouteList.contains(record.get("route_long_name"))) {		//FIXME: pick one later for HKIOnly
						DataRoutePT TempPTDataRoute = new DataRoutePT(record.get("route_long_name"), record.get("agency_id"), record.get("route_id"));
						if(!this.ptDataContainer.addPTDataRoute(TempPTDataRoute))
							this.ptDataContainer.getPTDataRouteMap().get(TempPTDataRoute.getId()).getRouteIds().add(record.get("route_id"));
					}
				} else {
					DataRoutePT TempPTDataRoute = new DataRoutePT(record.get("route_long_name"), record.get("agency_id"), record.get("route_id"));
					if(!this.ptDataContainer.addPTDataRoute(TempPTDataRoute))
						this.ptDataContainer.getPTDataRouteMap().get(TempPTDataRoute.getId()).getRouteIds().add(record.get("route_id"));
				}
			}
		}
		parser.close();
		log.info("Finished reading routes");
	}
	
	private boolean isRouteHKI(String routeName) {
		//could have done with contains
		if(this.Pattern_Central.matcher(routeName).find() ||
				this.Pattern_Aberdeen.matcher(routeName).find() ||
				this.Pattern_NorthPoint.matcher(routeName).find() ||
				this.Pattern_WanChai.matcher(routeName).find() ||
				this.Pattern_SaiWanHo.matcher(routeName).find())
			return true;
		else
			return false;
	}

}
