package createBus;

import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class BusStop {
	private final Coord coordinate;
	private List<Id<Link>> possibleLinks;
	private Id<Link> linkId;
	private final String stopId;
	private final String name;
	private final String type; // Either bus or minibus

	private boolean isTerminus = false;

	/**
	 * The constructor of BusStop
	 * 
	 * @param type
	 *            Type of stop
	 * @param stopID
	 *            stopID proposed
	 * @param name
	 *            stop name proposed
	 * @param coord
	 *            stop coordinate, in MATSim standard
	 * @param links
	 *            Possible links
	 */
	public BusStop(String type, String stopID, String name, Coord coord, List<Id<Link>> links) {
		this.stopId = stopID;
		this.name = name;
		this.type = type;
		coordinate = coord;
		possibleLinks = links;
	}

	public void setLinkId(Id<Link> linkId) {
		if (possibleLinks.contains(linkId) || isTerminus)
			this.linkId = linkId;
	}

	public Coord getCoord() {
		return coordinate;
	}

	public Id<Link> getLinkId() {
		return linkId;
	}

	public List<Id<Link>> getPossibleLinks() {
		return possibleLinks;
	}

	public String getStopId() {
		return stopId;
	}

	public String getName() {
		return this.name;
	}

	public TransitStopFacility createOrGetTransitStopFacility(Network n, TransitSchedule ts) {
		TransitStopFacility toReturn;
		if (type.equals("bus")) {
			toReturn = getStopFacility(ts, isTerminus);
		} else if (type.equals("minibus")) {
			toReturn = createOrGetMiniBusTransitStopFacility(n, ts);
		} else {
			throw new RuntimeException("The type of this stop is not supported!");
		}
		if (toReturn.getLinkId() == null) {
			throw new IllegalArgumentException("The link Id is not exist! For stop " + stopId);
		}
		return toReturn;
	}

	/**
	 * It try to get a stop facility from the link, but the
	 * 
	 * @param n
	 * @param ts
	 * @return
	 */
	private TransitStopFacility createOrGetMiniBusTransitStopFacility(Network n, TransitSchedule ts) {
		TransitStopFacility candidateTSF = null;
		for (TransitStopFacility tsf : ts.getFacilities().values()) {
			if (tsf.getLinkId().equals(this.getLinkId())) {
				// Change if the distance is the shortest
				if (candidateTSF == null
						|| NetworkUtils.getEuclideanDistance(candidateTSF.getCoord(), this.getCoord()) > NetworkUtils
								.getEuclideanDistance(tsf.getCoord(), this.getCoord())
						|| NetworkUtils.getEuclideanDistance(tsf.getCoord(),
								this.getCoord()) < MiniBusRoute.distancePerStop) {
					candidateTSF = tsf;
					if (candidateTSF.getName() == null || candidateTSF.getName().equals("")) {
						candidateTSF.setName(name);
					}
				}
			}
		}

		// If stop is not found.
		if (candidateTSF == null) {
			Id<TransitStopFacility> tsfId = Id.create(stopId, TransitStopFacility.class);
			TransitStopFacility tsf = ts.getFactory().createTransitStopFacility(tsfId, coordinate, false);
			tsf.setLinkId(linkId);
			tsf.setName(name);
			ts.addStopFacility(tsf);
			return tsf;
		}
		return candidateTSF;
	}

	private TransitStopFacility getStopFacility(TransitSchedule ts, boolean isTerminus) {
		TransitStopFacility obtained = null;
		if(isTerminus) {
			obtained = ts.getFacilities().get(Id.create("BT_" + stopId, TransitStopFacility.class));
		}else {
			obtained = ts.getFacilities().get(Id.create("bus_" + stopId, TransitStopFacility.class));
		}
		this.linkId = obtained.getLinkId();
		return obtained;
	}
	
//	private TransitStopFacility createOrGetBusTransitStopFacility(Network n, TransitSchedule ts) {
//		Id<TransitStopFacility> tsfId = Id.create("bus_" + stopId, TransitStopFacility.class);
//		TransitStopFacility getTSF = ts.getFacilities().get(tsfId);
//		if (getTSF == null) {
//			TransitStopFacility tsf = ts.getFactory().createTransitStopFacility(tsfId, coordinate, false);
//			tsf.setLinkId(linkId);
//			tsf.setName(name);
//			ts.addStopFacility(tsf);
//			stopFacilityLinkCount.put(tsfId, Lists.newArrayList(new Tuple<Id<Link>, Integer>(linkId, 1)));
//			return tsf;
//		} else {
//			List<Tuple<Id<Link>, Integer>> linkCounts = stopFacilityLinkCount.get(tsfId);
//			Tuple<Id<Link>, Integer> tupleFound = null;
//			for(Tuple<Id<Link>, Integer> linkCount: linkCounts) { //Iterate to find the tuple with same linkId
//				if(this.linkId.equals(linkCount.getFirst())) {
//					tupleFound = linkCount;
//					break;
//				}
//			}
//			if(tupleFound!=null) { //Replace the tuple with one with higher count.
//				linkCounts.add(new Tuple<Id<Link>, Integer>(this.linkId, tupleFound.getSecond()+1));
//				linkCounts.remove(tupleFound);
//			}else {
//				linkCounts.add(new Tuple<Id<Link>, Integer>(this.linkId, 1));
//			}
//			
//			// TODO: To fix this issue.
//			if (getTSF.getLinkId() != this.linkId) {
//				log.warn("The link ID of TSF obtained " + getTSF.getLinkId() + " is not equal to this " + "link ID "
//						+ this.getLinkId() + ". BusStop " + getName());
//			}
//			this.setLinkId(getTSF.getLinkId());
//			return getTSF;
//		}
//	}

	@Override
	public boolean equals(Object o) {
		if (((BusStop) o).getStopId().equals(stopId)) {
			return true;
		}
		return false;
	}

	public String toString() {
		return "Id:" + this.stopId + " " + this.getName();
	}

	public boolean isTerminus() {
		return isTerminus;
	}

	public void setTerminus(boolean isTerminus) {
		this.isTerminus = isTerminus;
	}
}
