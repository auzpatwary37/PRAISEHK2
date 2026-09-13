package createBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class CreateBusUtils {
	public static TransitStopFacility createAndAddOrGetTransitStopFacility(TransitSchedule ts, String stopId, 
			String stopName, boolean isTerminus, Id<Link> linkId, Coord coord, boolean blockage) {
		Id<TransitStopFacility> tsfId = Id.create(stopId, TransitStopFacility.class);
		if(!ts.getFacilities().containsKey(tsfId)) {
			TransitStopFacility tsf = ts.getFactory().createTransitStopFacility(
					tsfId, coord, blockage);
			tsf.setLinkId(linkId);
			tsf.setName(stopName);
			ts.addStopFacility(tsf);
			return tsf;
		}else {
			return ts.getFacilities().get(tsfId);
		}
	}

	/**
	 * 
	 * @param operator
	 * @param name
	 * @param dir The directory
	 * @param searchTarget Either 'headway', 'stop' or 'section'
	 * @return
	 * @throws IOException
	 */
	public static List<String> findFilePaths(String operator, String name, String dir, String searchTarget) throws IOException {
		List<String> filePaths = new ArrayList<String>();
		
		File directory = new File(dir);
		for (File file : directory.listFiles()) {
			if (file.isDirectory()) {
				throw new IllegalArgumentException("The file has a directory.");
			} else if (file.getName().matches(operator + "( )+" + name.replace("(", "\\(").replace(")", "\\)") + " .*\\.csv")) { // Testing
				filePaths.add(file.getCanonicalPath());
			} else if (file.getName().matches(operator + " " + name.replace("(", "\\(").replace(")", "\\)") + " .*\\.csv")) {
				filePaths.add(file.getCanonicalPath());
			}
		}
		if ( (searchTarget.equals("headway") || searchTarget.equals("stop") )&& filePaths.isEmpty())
			throw new IllegalArgumentException(searchTarget+ " file for " + operator + " " + name + " is not found!");
		else if(searchTarget.equals("headway") || searchTarget.equals("stop") || searchTarget.equals("section")){
			return filePaths;
		}else {
			throw new IllegalArgumentException("The search target "+ searchTarget + " is invalid!");
		}
	}
	
	
}
