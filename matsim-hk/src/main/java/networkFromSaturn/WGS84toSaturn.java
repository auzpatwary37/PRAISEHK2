package networkFromSaturn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import com.google.gson.Gson;

public class WGS84toSaturn implements CoordinateTransformation {

	@Override
	public Coord transform(Coord coord) {
		//FIXME so so wrong
		// HKG1980 grid (EPSG:2326)
//		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84,
//				"EPSG:2326");
//		Coord temp = ct.transform(coord);
//
//		// Then minus x and y by 800000 and return
//		return new Coord(temp.getX() - 800000, temp.getY() - 800000);
		
		ApiReturn returnCoord = null;
		try {
			URL requestURL = new URL("http://www.geodetic.gov.hk/transform/v2/?inSys=wgsgeog&outSys=hkgrid&lat="+coord.getY()+"&long="+coord.getX());
			BufferedReader request = new BufferedReader(new InputStreamReader(requestURL.openStream()));
			returnCoord = (new Gson()).fromJson(request, ApiReturn.class);
			request.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return returnCoord.getCoord();
	}
	
	private class ApiReturn {
		private double hkN;
		private double hkE;
		
		public Coord getCoord() {
			return new Coord(hkE-800000, hkN-800000);
		}
	}

}
