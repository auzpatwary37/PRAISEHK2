/**
 * 
 */
package createPTGTFS;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import networkFromSaturn.WGS84toSaturn;

/**
 * A generic stop for PT data extracted from GTFS
 * @author JLo
 *
 */
public class DataStopPT {
	protected String stopId;	//only TD number in string form, for actual created stop_id look inside TSF
	protected String stopName;
	protected Coord coord;
	protected Id<Link> linkId;
	protected TransitStopFacility tsf;
	protected String type;
	public enum Types {bus, minibus, ferry};
	
	public DataStopPT(String stop_id, String stop_name, double stop_lat, double stop_lon) {
		this.stopId = stop_id;
		this.stopName = stop_name;
		
		CoordinateTransformation ct = new WGS84toSaturn();
		this.coord = ct.transform(new Coord(stop_lon,stop_lat));
		this.type = "";
	}
	
	public void setType(Types t) {
		this.type = t.toString();
	}
	
	public String getStopId() {
		return this.stopId;
	}
	
	public String getStopName() {
		return this.stopName;
	}
	
	public Coord getCoord() {
		return this.coord;
	}
	
	public Id<Link> getLinkId(){
		return this.linkId;
	}
	
	public TransitStopFacility getTransitStopFacility() {
		return this.tsf;
	}
	
	/**
	 * generic implementation
	 * @param ts
	 * @param linkId
	 * @param blocksLane
	 */
	public void setTransitStopFacility(TransitSchedule ts, Id<Link> linkId, boolean blocksLane) {
		this.linkId = linkId;
		this.tsf = ts.getFactory().createTransitStopFacility(Id.create(type+"_"+stopId, TransitStopFacility.class), this.coord, blocksLane);
		this.tsf.setName(this.stopName);
		this.tsf.setLinkId(linkId);
		ts.addStopFacility(this.tsf);
	}
}
