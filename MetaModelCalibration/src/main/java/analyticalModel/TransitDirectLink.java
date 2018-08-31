package analyticalModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

/**
 * 
 * @author Ashraf
 *
 * @param <anaNet>Type of network used
 */
public abstract class TransitDirectLink extends TransitLink{
	/**
	 * Constructor
	 * 
	 * @param startStopId
	 * @param endStopId
	 * @param startLinkId
	 * @param endLinkId
	 */
	
	public TransitDirectLink(String startStopId, String endStopId, Id<Link> startLinkId, 
			Id<Link> endLinkId, TransitSchedule ts, String lineId, String routeId) {
			
		super(startStopId, endStopId, startLinkId, endLinkId);
		this.ts=ts;
		this.lineId=lineId;
		this.routeId=routeId;
		Id<Link> strtLink=ts.getTransitLines().get(Id.create(this.lineId,TransitLine.class)).getRoutes()
				.get(Id.create(this.routeId,TransitRoute.class)).getRoute().getStartLinkId();
		Id<Link> endLink=ts.getTransitLines().get(Id.create(this.lineId,TransitLine.class)).getRoutes()
				.get(Id.create(this.routeId,TransitRoute.class)).getRoute().getEndLinkId();
		ArrayList<Id<Link>> routeLinks=new ArrayList<>();
		routeLinks.add(strtLink);
		routeLinks.addAll(ts.getTransitLines().get(Id.create(this.lineId,TransitLine.class)).getRoutes()
				.get(Id.create(this.routeId,TransitRoute.class)).getRoute().getLinkIds());
		routeLinks.add(endLink);
		int identifier=0;
		for(Id<Link> linkId:routeLinks) {
			if(linkId.toString().equals(this.getStartingLinkId().toString())){
				identifier++;
			}else if(linkId.toString().equals(this.getEndingLinkId().toString())) {
				if(identifier==1) {
					identifier--;
				}
			}
			if(identifier==1) {
				this.linkList.add(linkId);
			}
		}
		
	}
	
	
	
	
	//direct link parameters
	
	protected ArrayList<Id<Link>> linkList=new ArrayList<>();
	protected String lineId;
	protected String routeId;
	protected TransitSchedule ts;
	protected double distance=0;
	
	public ArrayList<Id<Link>> getLinkList() {
		return linkList;
	}
	public String getLineId() {
		return lineId;
	}
	public String getRouteId() {
		return routeId;
	}
	public abstract double getLinkTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams);
	public TransitSchedule getTs() {
		return ts;
	}
	

}
