package analysisOffline;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.pt.PtConstants;
import org.matsim.vehicles.Vehicle;

import com.google.common.collect.Lists;

public class ODHandler implements ActivityStartEventHandler, ActivityEndEventHandler, PersonDepartureEventHandler,
		LinkLeaveEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler {

	public enum Tunnel {
		CHT, WHC, EHC
	}

	private final Map<Id<Person>, List<TripInfo>> finishedTripInfos;
	private final Map<Id<Person>, TripInfo> travellingTripInfos;
	private final Map<Id<Vehicle>, List<Id<Person>>> vehicleOfPeople;
	private final Map<Id<Link>, ? extends Link> links;

	public ODHandler(Map<Id<Link>, ? extends Link> map) {
		finishedTripInfos = new HashMap<Id<Person>, List<TripInfo>>();
		travellingTripInfos = new HashMap<Id<Person>, TripInfo>();
		vehicleOfPeople = new HashMap<Id<Vehicle>, List<Id<Person>>>();
		this.links = map;
	}

	private Tunnel linkIdOfTunnel(Id<Link> linkId) {
		String linkString = linkId.toString();
		if (linkString.charAt(0) <= '3' && linkString.charAt(0) >= '1') {
			return null;
		} else {
			if (linkString.contains("CHT") || linkString.contains("TWL_TST") || linkString.contains("TST")) {
				return Tunnel.CHT;
			} else if (linkString.contains("WHC") || linkString.contains("TCL_KOW_0")) {
				return Tunnel.WHC;
			} else if (linkString.contains("EHC") || linkString.contains("TKL_QUB_1")
					|| linkString.contains("HungHom")) {
				return Tunnel.EHC;
			}
		}
		return null;
	}

	// Add the length to the trip information.
	@Override
	public void handleEvent(LinkLeaveEvent event) {
		Id<Vehicle> vehicleId = event.getVehicleId();
		if (vehicleOfPeople.containsKey(vehicleId)) {
			Tunnel tunnelTravelled = linkIdOfTunnel(event.getLinkId());

			for (Id<Person> personId : vehicleOfPeople.get(vehicleId)) {
				travellingTripInfos.get(personId).addDistance(links.get(event.getLinkId()).getLength());
				if (tunnelTravelled != null) {
					travellingTripInfos.get(personId).setCrossHarbourTunnel(tunnelTravelled);
				}
			}
		}
		// TODO Handle the trip for cross harbour
	}

	private void writingHeader(StringBuilder sb) {
		sb.append("Person id");
		sb.append(',');
		sb.append("trip number");
		sb.append(',');
		sb.append("mode");
		sb.append(',');
		sb.append("distance_m");
		sb.append(',');
		sb.append("time taken_s");
		sb.append(',');
		sb.append("tunnel");
		sb.append(',');
		sb.append("From district");
		sb.append(',');
		sb.append("To district");
		sb.append(',');
		sb.append('\n');
	}

	private void writeElement(StringBuilder sb, Id<Person> personId, int tripNum, String mode, double distance_in_m,
			double time_in_s, Tunnel tunnel, int fromDis, int toDis) {
		sb.append(personId.toString());
		sb.append(',');
		sb.append(tripNum);
		sb.append(',');
		sb.append(mode);
		sb.append(',');
		sb.append(distance_in_m);
		sb.append(',');
		sb.append(time_in_s);
		sb.append(',');
		sb.append(tunnel);
		sb.append(',');
		sb.append(fromDis);
		sb.append(',');
		sb.append(toDis);
		sb.append(',');
		sb.append('\n');
	}

	public void writeFinishedTrip(String filePath) throws IOException {
		PrintWriter pw = new PrintWriter(filePath + "finished.csv", "UTF-8");
		StringBuilder sb = new StringBuilder();
		writingHeader(sb);

		for (Id<Person> personId : finishedTripInfos.keySet()) {
			int i = 0;
			for (TripInfo tripInfo : finishedTripInfos.get(personId)) {
				writeElement(sb, personId, i, tripInfo.mode, tripInfo.getTripDistance(), tripInfo.getTripTime(),
						tripInfo.tunnelUsed, tripInfo.startDistrict, tripInfo.endDistrict);
				i++;
			}
		}
		pw.write(sb.toString());
		pw.close();
	}

	public void writeUnfinishedTrip(String filePath) throws IOException {
		PrintWriter pw = new PrintWriter(filePath + "unfinished.csv", "UTF-8");
		StringBuilder sb = new StringBuilder();
		writingHeader(sb);

		for (Id<Person> personId : travellingTripInfos.keySet()) {
			TripInfo tripInfo = travellingTripInfos.get(personId);
			writeElement(sb, personId, 0, tripInfo.mode, tripInfo.getTripDistance(), tripInfo.getTripTime(),
					tripInfo.tunnelUsed, tripInfo.startDistrict, tripInfo.endDistrict);
		}
		pw.write(sb.toString());
		pw.close();
	}

	public void writeCSV(String filePath) throws IOException {
		writeFinishedTrip(filePath);
		writeUnfinishedTrip(filePath);
	}

	private int getDistrict(Id<Link> linkId) {
		if (linkId.toString().charAt(0) == '2' && linkId.toString().charAt(7) == '2') {
			return 0;
		} else if (linkId.toString().charAt(0) == '1' && linkId.toString().charAt(7) == '1') {
			return 1;
		} else if (linkId.toString().charAt(0) == '3' && linkId.toString().charAt(7) == '3') {
			return 2;
		} else {
			return 3;
		}
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		// TODO Put the agent into the working map
		if (!event.getActType().equals(PtConstants.TRANSIT_ACTIVITY_TYPE)) {

			Id<Person> personId = event.getPersonId();
			TripInfo finished = travellingTripInfos.remove(personId);
			finished.endTrip(event.getTime(), event.getLinkId());

			List<TripInfo> tripInfoList;
			if (finishedTripInfos.containsKey(personId)) {
				tripInfoList = finishedTripInfos.get(personId);
			} else {
				tripInfoList = new LinkedList<TripInfo>();
			}
			tripInfoList.add(finished);
			finishedTripInfos.put(personId, tripInfoList);
		}

	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		// The trip (leg) starts whenever the activity end
		if (!event.getActType().equals(PtConstants.TRANSIT_ACTIVITY_TYPE)) {
			Id<Person> personId = event.getPersonId();
			travellingTripInfos.put(personId, new TripInfo(event.getTime(), event.getLinkId()));
		}
	}

	/**
	 * This handler handles the departure event
	 * 
	 * @param event
	 */
	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if (!event.getPersonId().toString().contains("pt")) {
			TripInfo personTrip = travellingTripInfos.get(event.getPersonId());
			String legMode = event.getLegMode();
			if (personTrip.isTransit()) {
				return;
			}
			if (legMode.equals(TransportMode.transit_walk) || legMode.equals(TransportMode.pt)) {
				personTrip.setMode(event.getTime(), "pt");
			} else if (legMode.equals(TransportMode.car)) {
				personTrip.setMode(event.getTime(), "car");
			} else {
				throw new RuntimeException("The mode " + legMode + " is not considered in this handler!");
			}
		}
	}

	/**
	 * A helper class to store trip info for analysis
	 * 
	 * @author eleead
	 *
	 */
	private class TripInfo {
		private Id<Link> fromLinkId;
		private double startTime;
		private Id<Link> toLinkId;
		private double endTime;
		private String mode;

		private double distanceTraveled;

		private Tunnel tunnelUsed;
		// private boolean viaCBD; //To be checked later

		private int startDistrict; // 0: Eastern, 1: Central Western and Wan Chai, 2: Southern
		private int endDistrict; // 0: Eastern, 1: Central Western and Wan Chai, 2: Southern

		// Probably add a 'last seen'?

		private TripInfo(double startTime, Id<Link> fromLinkId) {
			this.startTime = startTime;
			this.fromLinkId = fromLinkId;
			this.startDistrict = getDistrict(fromLinkId);
		}

		private void setMode(double startTime, String mode) {
			if (startTime != this.startTime) {
				throw new IllegalArgumentException("The start time is not consistent!");
			}
			this.mode = mode;
		}

		private void addDistance(double linkLength) {
			distanceTraveled += linkLength;
		}

		private void endTrip(double endTime, Id<Link> linkId) {
			this.endTime = endTime;
			this.toLinkId = linkId;
			this.endDistrict = getDistrict(linkId);
		}

		private double getTripTime() {
			return endTime - startTime;
		}

		private void setCrossHarbourTunnel(Tunnel tunnel) {
			this.tunnelUsed = tunnel;
		}

		private double getTripDistance() {
			return distanceTraveled;
		}

		private boolean isTransit() {
			if (mode != null && mode.equals("pt")) {
				return true;
			}
			return false;
		}

	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		Id<Person> personId = event.getPersonId();
		if (!personId.toString().contains("pt")) {
			Id<Vehicle> vehicleId = event.getVehicleId();
			List<Id<Person>> passengers = null;
			if (vehicleOfPeople.containsKey(vehicleId)) {
				passengers = vehicleOfPeople.get(vehicleId);
				passengers.add(personId);
			} else {
				passengers = Lists.newArrayList(personId);
			}
			vehicleOfPeople.put(vehicleId, passengers);
		}
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		Id<Person> personId = event.getPersonId();
		if (!personId.toString().contains("pt")) {
			List<Id<Person>> passengers = vehicleOfPeople.remove(event.getVehicleId());
			passengers.remove(personId);
			if (!passengers.isEmpty()) {
				vehicleOfPeople.put(event.getVehicleId(), passengers);
			}
		}
	}
}
