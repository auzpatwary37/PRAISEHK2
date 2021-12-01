package ust.hk.praisehk.metamodelcalibration.measurements;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class MTRLinkVolumeInfo {

	final Id<TransitLine> lineId;
	final Id<TransitRoute> routeId;
	final Id<TransitStopFacility> stopId;
	final Id<Link> linkId;
	
	public MTRLinkVolumeInfo(Id<TransitStopFacility> stopId, Id<TransitRoute> routeId, Id<Link> linkId, Id<TransitLine> lineId) {
		this.lineId = lineId;
		this.routeId = routeId;
		this.stopId = stopId;
		this.linkId = linkId;
		
	}
	
	public MTRLinkVolumeInfo(String s) {
		String part[] = s.split("___");
		lineId = Id.create(part[0],TransitLine.class);
		routeId = Id.create(part[1],TransitRoute.class);
		stopId = Id.create(part[2], TransitStopFacility.class);
		linkId = Id.createLinkId(part[3]);
	}
	
	@Override
	public String toString() {
		return lineId.toString()+"___"+routeId.toString()+"___"+stopId.toString()+"___"+linkId.toString();
	}
}
