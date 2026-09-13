package dynamicTransitRouter.fareCalculators;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.xml.sax.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.w3c.dom.*;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class ZonalFareCalculator implements FareCalculator {

	final private HashMap<Id<TransitLine>, LineFare> lineFareMap;
	final private Map<Id<TransitLine>, TransitLine> transitLines;
	
	private double fareFactor;

	public ZonalFareCalculator(TransitSchedule ts) {
		this.transitLines = ts.getTransitLines();
		this.lineFareMap = new HashMap<>();
	}

	/**
	 * A helper function to determine whether the zonal fare is circular or not. It
	 * would return true only if 1) The line has only one route 2) The route has the
	 * same start and end terminus.
	 * 
	 * @param transitLineId
	 *            The transit line ID to be queried.
	 * @return true indicates it is a circular line
	 */
	private boolean isCircular(Id<TransitLine> transitLineId) {
		Map<Id<TransitRoute>, TransitRoute> routeMap = transitLines.get(transitLineId).getRoutes();
		if (routeMap.size() > 1) {
			return false;
		} else {
			for (TransitRoute route : routeMap.values()) {
				List<TransitRouteStop> trsList = route.getStops();
				if (trsList.get(0).getStopFacility().getId()
						.equals(trsList.get(trsList.size() - 1).getStopFacility().getId())) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * It is an initialization for the transit line indeed.
	 * 
	 * @param transitLineId
	 * @param fare
	 */
	public void setFullFare(Id<TransitLine> transitLineId, double fare) {
		if (lineFareMap.containsKey(transitLineId)) {
			throw new IllegalArgumentException("The transit line Id already input the calculator.");
		}
		LineFare busFare = new LineFare(transitLineId, fare, isCircular(transitLineId));
		lineFareMap.put(transitLineId, busFare);
	}

	public void addSectionFare(Id<TransitLine> lineId, Id<TransitRoute> routeId, Id<TransitStopFacility> fromStopId,
			int occurence, double fare) {
		LineFare busFare = lineFareMap.get(lineId);
		busFare.addSectionFare(routeId, fromStopId, occurence, fare);
	}

	public void addSectionFare(Id<TransitLine> lineId, Id<TransitRoute> routeId, int stopSequence, double fare) {
		LineFare busFare = lineFareMap.get(lineId);
		busFare.busRouteFare.get(routeId).addSectionFare(stopSequence, fare);
	}

	public void addSectionFare(Id<TransitLine> lineId, Id<TransitRoute> routeId, int fromStopSequence,
			int toStopSequence, double fare) {
		LineFare busFare = lineFareMap.get(lineId);
		busFare.busRouteFare.get(routeId).addSectionFare(fromStopSequence, toStopSequence, fare);
	}

	public void addSectionFare(Id<TransitLine> lineId, Id<TransitRoute> routeId, Id<TransitStopFacility> fromStopId,
			int fromOccurence, Id<TransitStopFacility> toStopId, int toOccurence, double fare) {
		LineFare busFare = lineFareMap.get(lineId);
		busFare.busRouteFare.get(routeId).addSectionFare(fromStopId, fromOccurence, toStopId, toOccurence, fare);
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		return lineFareMap.get(lineId).getMinFare(routeId, fromStopId, toStopId) * this.fareFactor;
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId) {
		return lineFareMap.get(lineId).getMinFare(routeId, fromStopId) * this.fareFactor;
	}

	@Override
	public List<Double> getFares(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			Id<TransitStopFacility> toStopId) {
		return lineFareMap.get(lineId).getFares(routeId, fromStopId, toStopId);
	}
	
	public void writeXMLV2(String filePath)
			throws ParserConfigurationException, FileNotFoundException, TransformerException {
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

		Document document = documentBuilder.newDocument();
		Element rootEle = document.createElement("Lines");

		for (Id<TransitLine> transitLineId : lineFareMap.keySet()) {
			Element line = document.createElement("Line");
			line.setAttribute("id", transitLineId.toString());
			LineFare lineFare = lineFareMap.get(transitLineId);
			line.setAttribute("FullFare", lineFare.fullFare + "");
			line.setAttribute("Circular", Boolean.toString(lineFare.isCircular));
			rootEle.appendChild(line);

			if (lineFare.busRouteFare == null) {
				continue;
			}

			for (Id<TransitRoute> transitRouteId : lineFare.busRouteFare.keySet()) {
				Element route = document.createElement("Route");
				route.setAttribute("id", transitRouteId.toString());

				BusRouteFare routeFare = lineFare.busRouteFare.get(transitRouteId);
				// The stop list
				Element e = document.createElement("StopList");
				
				//Stop and zone, with sequence
				for(int i = 0; i<routeFare.stopFacilityList.size(); i++) {
					Element stop = document.createElement("Stop");
					stop.setAttribute("seq", i+"");
					stop.setAttribute("id", routeFare.stopFacilityList.get(i).toString());
					stop.setAttribute("zone", routeFare.stopSequence2Zone.get(i) + "");
					e.appendChild(stop);
				}
				route.appendChild(e);

				// Zonal fare
				e = document.createElement("ZoneFareFromTo");
				for (int i = 0; i < routeFare.zonalFareFromTo.size(); i++) {
					Element from = document.createElement("From");
					from.setAttribute("val", i + "");
					for (int j = 0; j < routeFare.zonalFareFromTo.size(); j++) {
						Element to = document.createElement("To");
						to.setAttribute("val", j + "");
						to.setAttribute("fare", routeFare.zonalFareFromTo.get(i).get(j) + "");
						from.appendChild(to);
					}

					e.appendChild(from);
				}
				route.appendChild(e);
				line.appendChild(route);
			}
		}
		document.appendChild(rootEle);

		Transformer tr = TransformerFactory.newInstance().newTransformer();
		tr.setOutputProperty(OutputKeys.INDENT, "yes");
		tr.setOutputProperty(OutputKeys.METHOD, "xml");
		tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		tr.transform(new DOMSource(document), new StreamResult(new FileOutputStream(filePath)));
	}

	public void writeXML(String filePath)
			throws ParserConfigurationException, FileNotFoundException, TransformerException {
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

		Document document = documentBuilder.newDocument();

		Element rootEle = document.createElement("Lines");

		for (Id<TransitLine> transitLineId : lineFareMap.keySet()) {
			Element line = document.createElement("Line");
			line.setAttribute("id", transitLineId.toString());
			LineFare lineFare = lineFareMap.get(transitLineId);
			line.setAttribute("FullFare", lineFare.fullFare + "");
			line.setAttribute("Circular", Boolean.toString(lineFare.isCircular));
			rootEle.appendChild(line);

			if (lineFare.busRouteFare == null) {
				continue;
			}

			for (Id<TransitRoute> transitRouteId : lineFare.busRouteFare.keySet()) {
				Element route = document.createElement("Route");
				route.setAttribute("id", transitRouteId.toString());

				BusRouteFare routeFare = lineFare.busRouteFare.get(transitRouteId);
				// The stop list
				Element e = document.createElement("StopList");
				for (Id<TransitStopFacility> stops : routeFare.stopFacilityList) {
					Element stop = document.createElement("Stop");
					stop.setAttribute("id", stops.toString());
					e.appendChild(stop);
				}
				route.appendChild(e);

				// Stop sequence to zone
				e = document.createElement("StopSequenceToZone");
				for (Integer stopSequence : routeFare.stopSequence2Zone.keySet()) {
					Element stopSeq = document.createElement("StopSequenceAndZone");
					stopSeq.setAttribute("seq", stopSequence + "");
					stopSeq.setAttribute("zone", routeFare.stopSequence2Zone.get(stopSequence) + "");
					e.appendChild(stopSeq);
				}
				route.appendChild(e);

				// Facility to stop sequence
				e = document.createElement("FacilityToStopSequence");
				for (Id<TransitStopFacility> stopFacility : routeFare.stopSequenceFromFacility.keySet()) {
					Element stopFac = document.createElement("StopFacility");
					stopFac.setAttribute("id", stopFacility.toString());
					for (int stopSequence : routeFare.stopSequenceFromFacility.get(stopFacility)) {
						Element stopSeq = document.createElement("StopSequence");
						stopSeq.setAttribute("seq", stopSequence + "");
						stopFac.appendChild(stopSeq);
					}
					e.appendChild(stopFac);
				}
				route.appendChild(e);

				// Zonal fare
				e = document.createElement("ZoneFareFromTo");
				for (int i = 0; i < routeFare.zonalFareFromTo.size(); i++) {
					Element from = document.createElement("From");
					from.setAttribute("val", i + "");
					for (int j = 0; j < routeFare.zonalFareFromTo.size(); j++) {
						Element to = document.createElement("To");
						to.setAttribute("val", j + "");
						to.setAttribute("fare", routeFare.zonalFareFromTo.get(i).get(j) + "");
						from.appendChild(to);
					}

					e.appendChild(from);
				}
				route.appendChild(e);
				line.appendChild(route);
			}
		}
		document.appendChild(rootEle);

		Transformer tr = TransformerFactory.newInstance().newTransformer();
		tr.setOutputProperty(OutputKeys.INDENT, "yes");
		tr.setOutputProperty(OutputKeys.METHOD, "xml");
		tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		tr.transform(new DOMSource(document), new StreamResult(new FileOutputStream(filePath)));
	}

	/**
	 * Add a line that
	 * 
	 * @param lineId
	 * @param isCircular
	 *            true if the line is circular
	 * @param fullFare
	 *            The 'full fare' of Transport department
	 */
	public void addLine(Id<TransitLine> lineId, boolean isCircular, double fullFare) {
		if (this.lineFareMap.containsKey(lineId)) {
			throw new IllegalArgumentException("The line id specified is already there!");
		}

		LineFare lineFare = new LineFare(lineId, fullFare, isCircular);
		this.lineFareMap.put(lineId, lineFare);
	}

	/**
	 * A convenient function to add a route into the fare calculator
	 * 
	 * @param lineId
	 * @param routeId
	 * @param routeFullFare
	 *            The fare to be paid from first stop to last stop.
	 */
	public void addRoute(Id<TransitLine> lineId, Id<TransitRoute> routeId, double routeFullFare) {
		LineFare lineFare = this.lineFareMap.get(lineId);
		if (lineFare.busRouteFare == null) { // Create a table if not have one.
			lineFare.busRouteFare = new HashMap<>();
		}
		if (lineFare.busRouteFare.containsKey(routeId)) {
			throw new IllegalArgumentException("This route is already there!");
		} else {
			lineFare.busRouteFare.put(routeId, new BusRouteFare(transitLines.get(lineId).getRoutes().get(routeId).getStops(),
					routeFullFare, lineFare.isCircular));
		}
	}
	
	public void addRoute(Id<TransitLine> lineId, Id<TransitRoute> routeId, double routeFullFare, 
			int route_ID, int route_SEQ) throws IOException {
		LineFare lineFare = this.lineFareMap.get(lineId);
		List<TransitRouteStop> lineRouteStops = transitLines.get(lineId).getRoutes().get(routeId).getStops();
		BusRouteFare brf = new BusRouteFare(lineRouteStops,
				routeFullFare, lineFare.isCircular);
		
		if (lineFare.busRouteFare!=null && lineFare.busRouteFare.containsKey(routeId)) {
			throw new IllegalArgumentException("This route is already there!");
		} else {
			if(lineFare.busRouteFare==null) {
				lineFare.busRouteFare = Maps.newHashMap();
			}
			BusFareDataExtractor extractor = new BusFareDataExtractor();
			extractor.loadFareToZonalFareCal(route_ID, route_SEQ, lineRouteStops.size(), brf);
			lineFare.busRouteFare.put(routeId, brf);
		}
	}

	public void addRoute(Id<TransitLine> lineId, Id<TransitRoute> routeId,
			List<Id<TransitStopFacility>> stopFacilityList, HashMap<Integer, Integer> stopSequence2Zone,
			HashMap<Id<TransitStopFacility>, List<Integer>> stopSequenceFromFacility,
			List<List<Double>> zonalFareFromTo) {

		LineFare lineFare = this.lineFareMap.get(lineId);
		// Validate the stops
		List<TransitRouteStop> trsList = transitLines.get(lineId).getRoutes().get(routeId).getStops();
		if (trsList.size() != stopFacilityList.size()) {
			throw new IllegalArgumentException("The stop facility list length does not match the one in scenario!");
		}
		for (int i = 0; i < trsList.size(); i++) {
			if (!stopFacilityList.get(i).equals(trsList.get(i).getStopFacility().getId())) {
				throw new IllegalArgumentException("The stop facility does not match the scenario!");
			}
		}

		// Create and put back the routes
		if (lineFare.busRouteFare == null) {
			lineFare.busRouteFare = new HashMap<Id<TransitRoute>, BusRouteFare>();
		}
		lineFare.busRouteFare.put(routeId, new BusRouteFare(lineFare.isCircular, stopFacilityList, stopSequence2Zone,
				stopSequenceFromFacility, zonalFareFromTo));
	}

	private class LineFare {
		private Id<TransitLine> lineId;
		private double fullFare;
		private HashMap<Id<TransitRoute>, BusRouteFare> busRouteFare;
		private final boolean isCircular;

		private LineFare(Id<TransitLine> lineId, double fullFare, boolean isCircular) {
			this.lineId = lineId;
			this.fullFare = fullFare;
			this.busRouteFare = null;
			this.isCircular = isCircular;
		}
		
		/**
		 * 
		 * @param routeId
		 *            The route ID
		 * @param fromStopId
		 *            Start from stop Id
		 * @param occurence
		 *            The number of times of the route visits the stop to put the
		 *            section fare. 1 indicate the first time.
		 * @param fare
		 */
		private void addSectionFare(Id<TransitRoute> routeId, Id<TransitStopFacility> fromStopId, int occurence,
				double fare) {
			BusRouteFare toModify = busRouteFare.get(routeId);
			toModify.addSectionFare(fromStopId, occurence, fare);
			busRouteFare.put(routeId, toModify);
		}

		private double getMinFare(Id<TransitRoute> routeId, Id<TransitStopFacility> fromStopId,
				Id<TransitStopFacility> toStopId) {
			if (busRouteFare == null) {
				return fullFare;
			} else {
				return busRouteFare.get(routeId).getMinFare(fromStopId, toStopId);
			}
		}

		private double getMinFare(Id<TransitRoute> routeId, Id<TransitStopFacility> stopId) {
			if (busRouteFare == null) {
				return fullFare;
			} else {
				return busRouteFare.get(routeId).getMinFare(stopId);
			}
		}

		private List<Double> getFares(Id<TransitRoute> routeId, Id<TransitStopFacility> fromStopId,
				Id<TransitStopFacility> toStopId) {
			if (busRouteFare == null) {
				return Lists.newArrayList(fullFare);
			} else {
				return busRouteFare.get(routeId).getFares(fromStopId, toStopId);
			}
		}

		private double getFare(Id<TransitRoute> routeId, Id<TransitStopFacility> fromStopId, int fromOccurence,
				Id<TransitStopFacility> toStopId, int toOccurence) {
			if (busRouteFare == null) {
				return fullFare;
			} else {
				return busRouteFare.get(routeId).getFare(fromStopId, fromOccurence, toStopId, toOccurence);
			}
		}
	}
	
	public class BusRouteFare {
		private final boolean isCircular;
		private final List<Id<TransitStopFacility>> stopFacilityList;
		private HashMap<Integer, Integer> stopSequence2Zone; // Store the zone number based on the position of
																// TransitRouteStop
		private HashMap<Id<TransitStopFacility>, List<Integer>> stopSequenceFromFacility;
		private List<List<Double>> zonalFareFromTo;

		private BusRouteFare(boolean isCircular, List<Id<TransitStopFacility>> stopFacilityList,
				HashMap<Integer, Integer> stopSequence2Zone,
				HashMap<Id<TransitStopFacility>, List<Integer>> stopSequenceFromFacility,
				List<List<Double>> zonalFareFromTo) {
			this.isCircular = isCircular;
			this.stopFacilityList = stopFacilityList;
			this.stopSequence2Zone = stopSequence2Zone;
			this.stopSequenceFromFacility = stopSequenceFromFacility;
			this.zonalFareFromTo = zonalFareFromTo;
		}

		public BusRouteFare(final List<TransitRouteStop> stopList, double fullFare, boolean isCircular) {
			this.stopFacilityList = new ArrayList<Id<TransitStopFacility>>();
			for (TransitRouteStop trs : stopList) {
				this.stopFacilityList.add(trs.getStopFacility().getId());
			}
			this.stopSequenceFromFacility = new HashMap<>();
			this.stopSequence2Zone = new HashMap<>();
			this.zonalFareFromTo = new ArrayList<List<Double>>();
			this.isCircular = isCircular;
			this.initialize(fullFare);

		}

		private void initialize(double fullFare) {
			// Put the stops into the table
			for (int i = 0; i < stopFacilityList.size(); i++) { //i is the sequence
				Id<TransitStopFacility> tsFId = stopFacilityList.get(i);
				stopSequence2Zone.put(i, 0); // Start with zone 0.

				//Put the stop in the stop sequence from stop facility map.
				if (!stopSequenceFromFacility.containsKey(tsFId)) {
					stopSequenceFromFacility.put(tsFId, Lists.newArrayList(i));
				} else {
					List<Integer> stopSequenceList = stopSequenceFromFacility.get(tsFId);
					stopSequenceList.add(i);
					stopSequenceFromFacility.put(tsFId, stopSequenceList);
				}
			}
			zonalFareFromTo.add(Lists.newArrayList(fullFare)); // Index [0][0] is the full fare;
		}

		private void addSectionFare(Id<TransitStopFacility> fromStopId, int occurence, double fare) {
			if (occurence < 1) {
				throw new IllegalArgumentException("The number of occurence cannot be less than 1!");
			}
			int occurenceLeft = occurence - 1;
			int onZone = -1;
			boolean found = false;
			// Increase the number of zone.
			for (int i = 0; i < stopFacilityList.size(); i++) {
				if (!found && stopFacilityList.get(i).equals(fromStopId)) {
					if (occurenceLeft > 0) {
						occurenceLeft--;
					} else if (occurenceLeft == 0) {
						found = true;
						onZone = stopSequence2Zone.get(i); // Get the zone that is now in.
						occurenceLeft--;
					}
				}
				// For the zone number
				if (found) {
					int theZone = stopSequence2Zone.get(i);
					if (stopSequence2Zone.get(i) == onZone)
						stopSequence2Zone.put(i, onZone + 1);
					else { // If the zone is after the thing, it should be increased by 1.
						stopSequence2Zone.put(i, theZone + 1);
					}
				}
			}

			// Modify the zonalFareFromTo table
			this.modifyZonalFareFromTo(fare, onZone);
			if (!isZoneFareValid()) {
				throw new IllegalArgumentException("The zone inputed is not right!");
			}
		}

		private void addSectionFare(int stopSequence, double fare) {
			if (stopSequence < 0) {
				throw new IllegalArgumentException("The stop sequence cannot be less than 0!");
			} else if (stopSequence == 0) {
				return;
			}
			int onZone = stopSequence2Zone.get(stopSequence - 1);
			// Increase the number of zone.
			for (int i = stopSequence; i < stopFacilityList.size(); i++) {
				stopSequence2Zone.put(i, stopSequence2Zone.get(i) + 1);
			}

			// Modify the zonalFareFromTo table
			this.modifyZonalFareFromTo(fare, onZone);
			if (!isZoneFareValid()) {
				throw new IllegalArgumentException("The zone inputed is not right!");
			}
		}

		/**
		 * Add a section fare from fromStopSeq to toStopSeq, inclusive.
		 * @param fromStopSeq First stop to aboard to have that section fare.
		 * @param toStopSeq Last stop to alight to have that section fare.
		 * @param fare
		 */
		public void addSectionFare(int fromStopSeq, int toStopSeq, double fare) {
			if (fromStopSeq < 0 || toStopSeq < 0) {
				throw new IllegalArgumentException("The stop sequence cannot be less than 0!");
			}
//			if(fromStopSeq==0) {
//				int fromZone = stopSequence2Zone.get(fromStopSeq);
//				int toZone = stopSequence2Zone.get(toStopSeq);
//				//Adding a zone.
//				for(int i = toStopSeq + 1; i < stopSequence2Zone.size(); i++) {
//					stopSequence2Zone.replace(i, stopSequence2Zone.get(i) + 1);
//				}
//				
//				//Update the zonal fare table.
//				List<Double> oldToZoneFare = zonalFareFromTo.get(toZone);
//				List<Double> newToZoneFare = new ArrayList<Double>();
//				
//				newToZoneFare.addAll(oldToZoneFare);
//				newToZoneFare.add(toZone, fare);
//				
//				for(int zone = 0; zone < zonalFareFromTo.size(); zone++) {
//					if(zone < toZone) {
//						List<Double> zonalFareTo = zonalFareFromTo.get(zone);
//						zonalFareTo.add(toZone, fare);
//						for(int i = 0; i < toZone; i++) { //Replace the before one.
//							if(zonalFareTo.get(i) > fare && zonalFareTo.get(i)!=Double.POSITIVE_INFINITY) {
//								zonalFareTo.remove(i); zonalFareTo.add(i, fare);
//							}
//						}
//					}else {
//						zonalFareFromTo.get(zone).add(toZone, Double.POSITIVE_INFINITY);
//					}
//				}
//				zonalFareFromTo.add(toZone, newToZoneFare);
//				cleanTables();
//				return;
//			}else {
				//For backup purpose
			HashMap<Integer, Integer> tempStopSequence2Zone = Maps.newHashMap(stopSequence2Zone);
			List<List<Double>> tempZonalFareFromTo = new ArrayList<List<Double>>();
			for(List<Double> zonalFareFrom: zonalFareFromTo) {
				tempZonalFareFromTo.add(Lists.newArrayList(zonalFareFrom));
			}
			
			int fromZone = stopSequence2Zone.get(fromStopSeq); //Get the original from zone
			int toZone = stopSequence2Zone.get(toStopSeq); //Get the original to zone
			
			if(zonalFareFromTo.get(fromZone).get(toZone)==fare) {
				return; //No need to change if the fare is already valid.
			}
			
			if(toZone==30) {
				System.out.println("");
			}
			//Adding two zones.
			//First we increase the zone from fromZone
			for(int i = fromStopSeq; i < stopSequence2Zone.size(); i++) {
				stopSequence2Zone.replace(i, stopSequence2Zone.get(i) + 1); 
			}
			//Then, we increase the zone from the stop after toZone
			for(int i = toStopSeq + 1; i < stopSequence2Zone.size(); i++) {
				stopSequence2Zone.replace(i, stopSequence2Zone.get(i) + 1);
			}
			
			//Augment the original table
			for(int zone = 0 ; zone < zonalFareFromTo.size(); zone++) {
				zonalFareFromTo.get(zone).add(toZone, zonalFareFromTo.get(zone).get(toZone));
				zonalFareFromTo.get(zone).add(fromZone, zonalFareFromTo.get(zone).get(fromZone));
			}
			List<Double> fromZoneFare = Lists.newArrayList(zonalFareFromTo.get(fromZone));
			fromZoneFare.set(fromZone, Double.POSITIVE_INFINITY);
			zonalFareFromTo.add(fromZone+1, fromZoneFare);
			
			List<Double> toZoneFare = Lists.newArrayList(zonalFareFromTo.get(toZone+1));
			toZoneFare.set(toZone+1, Double.POSITIVE_INFINITY);
			zonalFareFromTo.add(toZone+2, toZoneFare);
			
			//Check the fare needed to be change or not.
			int changeCount = 0;
			
			for(int from = fromZone+1; from<toZone+2; from++) {
				for(int to = from; to < toZone+2; to++) {
					double originalFare = zonalFareFromTo.get(from).get(to);
					if(originalFare - fare > 0.001 || fare-originalFare > 0.001) {
						zonalFareFromTo.get(from).set(to, fare);
						changeCount++;
					}
				}
			}
			if(changeCount==0) {
				stopSequence2Zone = tempStopSequence2Zone;
				zonalFareFromTo = tempZonalFareFromTo;
			}
			cleanTables();
//			}
		}

		private void addSectionFare(Id<TransitStopFacility> fromStopId, int fromOccurence,
				Id<TransitStopFacility> toStopId, int toOccurence, double fare) {
			if (fromOccurence < 1 || toOccurence < 1) {
				throw new IllegalArgumentException("The number of occurence cannot be less than 1!");
			}
			int fromOccurenceLeft = fromOccurence - 1;
			int fromOnZone = -1;
			boolean fromFound = false;
			int toOccurenceLeft = toOccurence - 1;
			int toOnZone = -1;
			boolean toFound = false;
			// Increase the number of zone.
			for (int i = 0; i < stopFacilityList.size(); i++) {
				if (!fromFound && stopFacilityList.get(i).equals(fromStopId)) {
					if (fromOccurenceLeft > 0) {
						fromOccurenceLeft--;
					} else if (fromOccurenceLeft == 0) {
						fromFound = true;
						fromOnZone = stopSequence2Zone.get(i); // Get the zone that is now in.
						fromOccurenceLeft--;
					}
				}
				if (!toFound && stopFacilityList.get(i).equals(toStopId)) {
					if (toOccurenceLeft > 0) {
						toOccurenceLeft--;
					} else if (toOccurenceLeft == 0) {
						toFound = true;
						toOnZone = stopSequence2Zone.get(i); // Get the zone that is now in.
						toOccurenceLeft--;
					}
				}
				// Modify the zone number
				if (fromFound) {
					// First we process the fromFound
					int currZone = stopSequence2Zone.get(i);
					if (currZone == fromOnZone)
						if (currZone != toOnZone) {
							stopSequence2Zone.put(i, currZone + 1);
						} else {
							stopSequence2Zone.put(i, currZone + 2); // If to zone is found and is in the same zone from
																	// fromZone
						}
					else { // If the zone is after the on zone, it should be increased by 1.
						if (currZone == toOnZone || toFound) {
							stopSequence2Zone.put(i, currZone + 2); // The onZone would become 2 unit more
						} else if (!toFound) {
							stopSequence2Zone.put(i, currZone + 1);
						}
					}

				}
			}

			if (!fromFound) {
				throw new IllegalArgumentException(
						"The stop ID " + fromStopId.toString() + " is not found in the route!");
			} else if (!toFound) {
				throw new IllegalArgumentException(
						"The stop ID " + toStopId.toString() + " is not found in the route!");
			}

			// Modify the zone prices
			List<Double> fromOnZoneFare = zonalFareFromTo.get(fromOnZone);
			List<Double> fromZoneFare = new ArrayList<Double>();
			fromZoneFare.add(Double.POSITIVE_INFINITY);
			// Make the array for the zone created for fromStop.
			// TODO: Something may be wrong when calculating the zonal fare.
			for (int i = 0; i < zonalFareFromTo.size(); i++) {
				if (i < fromOnZone) {
					fromZoneFare.add(fromOnZoneFare.get(i));
				} else if (i < toOnZone) {
					fromZoneFare.add(fare);
				} else if (i <= toOnZone && fromOnZone == 0) { // Becuase if the from zone is 0, one more have to be
																// replace the first
					fromZoneFare.add(fare);
				} else {
					fromZoneFare.add(fromOnZoneFare.get(i));
				}
			}
			fromZoneFare.add(toOnZone + 2, fromOnZoneFare.get(toOnZone));

			// Make the array for the zone created for toStop
			List<Double> toOnZoneFare = zonalFareFromTo.get(toOnZone);
			List<Double> toZoneFare = new ArrayList<Double>();
			toZoneFare.add(Double.POSITIVE_INFINITY);
			toZoneFare.add(Double.POSITIVE_INFINITY);
			for (int i = 0; i < zonalFareFromTo.size(); i++) {
				if (i < toOnZone) {
					toZoneFare.add(Double.POSITIVE_INFINITY);
				} else {
					toZoneFare.add(toOnZoneFare.get(i));
				}
			}

			for (int i = 0; i < zonalFareFromTo.size(); i++) {
				List<Double> toModify = zonalFareFromTo.get(i);
				if (i <= fromOnZone) {
					toModify.add(fromOnZone, toModify.get(fromOnZone));
					toModify.add(toOnZone + 1, toModify.get(toOnZone + 1));
				} else if (i <= toOnZone) {
					toModify.add(toOnZone, toModify.get(toOnZone));
					toModify.add(0, Double.POSITIVE_INFINITY);
				} else if (i > toOnZone) {
					toModify.add(0, Double.POSITIVE_INFINITY);
					toModify.add(0, Double.POSITIVE_INFINITY);
				}
			}

			zonalFareFromTo.add(toOnZone + 1, toZoneFare);
			zonalFareFromTo.add(fromOnZone + 1, fromZoneFare);

			cleanTables();

			if (!isZoneFareValid()) {
				// throw new IllegalArgumentException("The zone inputed is not right!");
			}
		}

		/**
		 * If the zone does not have any stop, remove the zone and the change the
		 * corresponding tables.
		 */
		private void cleanTables() {
			int zoneSize = zonalFareFromTo.size(); // It would change from time to time.
			for (int i = 0; i < zoneSize; i++) {
				boolean found = false;
				for (int zone : stopSequence2Zone.values()) { // See if there is zone
					if (i == zone) {
						found = true;
						break;
					}
				}
				if (found) {
					continue;
				} else {
					zonalFareFromTo.remove(i);
					for (List<Double> zoneFareFrom : zonalFareFromTo) {
						zoneFareFrom.remove(i);
					}
					zoneSize--; // The zone size is now reduced by one
					for (int stopSequence : stopSequence2Zone.keySet()) {
						int zone = stopSequence2Zone.get(stopSequence);
						if (zone == i) {
							throw new RuntimeException("Something went wrong!");
						}
						if (zone > i) {
							stopSequence2Zone.put(stopSequence, zone - 1);
						}
					}
					i--;
				}
			}
		}

		private void modifyZonalFareFromTo(double fare, int onZone) {
			// Create the fare array for the zone
			ArrayList<Double> zonalFareTo = new ArrayList<Double>();
			for (int i = zonalFareFromTo.size(); i > 0; i--) {
				if (i > onZone)
					zonalFareTo.add(fare);
				else {
					zonalFareTo.add(0, Double.POSITIVE_INFINITY);
				}
			}
			zonalFareFromTo.add(onZone + 1, zonalFareTo);

			for (int i = 0; i < zonalFareFromTo.size(); i++) {
				if (i <= onZone) {
					zonalFareFromTo.get(i).add(onZone, zonalFareFromTo.get(i).get(onZone)); // Add the zone data on the
																							// list
				} else {
					zonalFareFromTo.get(i).add(0, Double.POSITIVE_INFINITY);
					for (int j = i; j < zonalFareFromTo.size(); j++) {
						if (zonalFareFromTo.get(i).get(j) < fare) {
							continue;
							// throw new IllegalArgumentException("The fare is more expensive!");
						}
						zonalFareFromTo.get(i).remove(j);
						zonalFareFromTo.get(i).add(j, fare);
					}
				}
			}
		}

		/**
		 * A private helper function to check if the fare valid.
		 * 
		 * @return
		 */
		private boolean isZoneFareValid() {
			// It would be valid anyway if it is circular.
			if (isCircular) {
				return true;
			}

			// Check it horizontally
			for (int i = 0; i < zonalFareFromTo.size(); i++) {
				for (int j = 0; j < zonalFareFromTo.size() - 1; j++) {
					if (zonalFareFromTo.get(i).get(j) > zonalFareFromTo.get(i).get(j + 1)
							&& zonalFareFromTo.get(i).get(j) != Double.POSITIVE_INFINITY) {
						// throw new IllegalArgumentException("The zone inputed is not right!");
						return false;
					}
				}
			}

			// Check it vertically
			for (int i = 0; i < zonalFareFromTo.size() - 1; i++) {
				for (int j = 0; j < zonalFareFromTo.size(); j++) {
					if (zonalFareFromTo.get(i).get(j) < zonalFareFromTo.get(i + 1).get(j)
							&& zonalFareFromTo.get(i + 1).get(j) != Double.POSITIVE_INFINITY) {
						// throw new IllegalArgumentException("The zone inputed is not right!");
						return false;
					}
				}
			}
			return true;
		}

		/**
		 * Obtain the smallest possible fare from a stop to a stop
		 * 
		 * @param fromStopId
		 *            The TransitStopFacility Id of starting point
		 * @param toStopId
		 *            The TransitStopFacility Id of the end point
		 * @return The fare
		 */
		private List<Double> getFares(Id<TransitStopFacility> fromStopId, Id<TransitStopFacility> toStopId) {
			List<Integer> fromStopSeqs = stopSequenceFromFacility.get(fromStopId); // Obtain the possible stop sequences
			List<Integer> toStopSeqs = stopSequenceFromFacility.get(toStopId);

			Set<Double> posFares = new HashSet<Double>();

			// We return the smallest possible zonal fare.
			for (int fromStopSeq : fromStopSeqs) {
				int fromZone = stopSequence2Zone.get(fromStopSeq);
				for (int toStopSeq : toStopSeqs) {
					if (fromStopSeq < toStopSeq) {
						int toZone = stopSequence2Zone.get(toStopSeq);
						posFares.add(zonalFareFromTo.get(fromZone).get(toZone));
					}
				}
			}
			return new ArrayList<Double>(posFares);
		}

		private double getMinFare(Id<TransitStopFacility> fromStopId, Id<TransitStopFacility> toStopId) {
			List<Double> posFares = getFares(fromStopId, toStopId);
			if (posFares.isEmpty()) {
				if (fromStopId.equals(toStopId)) {
					return getMinFare(fromStopId); // For one stop only.
				} else {
					throw new IllegalArgumentException("The from stop is not before the to stop!");
				}
			} else {
				return Collections.min(posFares);
			}
		}

		private double getMinFare(Id<TransitStopFacility> stop) {
			List<Integer> stopSequences = stopSequenceFromFacility.get(stop);
			double minFare = Double.MAX_VALUE;
			for (Integer stopSequence : stopSequences) {
				int zone = stopSequence2Zone.get(stopSequence);
				double currMin = Collections.min(zonalFareFromTo.get(zone));

				if (currMin < minFare) {
					minFare = currMin;
				}
			}
			return minFare;
		}

		private double getFare(Id<TransitStopFacility> fromStopId, int fromOccurence, Id<TransitStopFacility> toStopId,
				int toOccurence) {
			List<Integer> fromStopSeqs = stopSequenceFromFacility.get(fromStopId); // Obtain the possible stop sequences
			List<Integer> toStopSeqs = stopSequenceFromFacility.get(toStopId);

			// We return the smallest possible zonal fare.
			int fromZone = stopSequence2Zone.get(fromStopSeqs.get(fromOccurence));
			int toZone = stopSequence2Zone.get(toStopSeqs.get(toOccurence));
			return zonalFareFromTo.get(fromZone).get(toZone);
		}

		/**
		 * A helper function to print the stop list and zonal fare
		 */
		private void printZones() {
			for (int i = 0; i < stopFacilityList.size(); i++) {
				System.out.println(stopFacilityList.get(i) + "\t" + stopSequence2Zone.get(i));
			}

			for (int i = 0; i < zonalFareFromTo.size(); i++) {
				System.out.print("Zone " + i);
				for (int j = 0; j < zonalFareFromTo.size(); j++) {
					System.out.print("\t" + zonalFareFromTo.get(i).get(j));
				}
				System.out.println("");
			}
		}

	}

	/**
	 * A test: 43M
	 * 
	 * @param args
	 * @throws TransformerException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	public static void main(String[] args)
			throws ParserConfigurationException, TransformerException, SAXException, IOException {
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "data/config.xml");
		Scenario scenario = ScenarioUtils.loadScenario(config);
		ZonalFareCalculator test = new ZonalFareCalculator(scenario.getTransitSchedule());
		Id<TransitLine> lineToTestId = Id.create("CTB_43M", TransitLine.class);
		Id<TransitRoute> routeToTestId = Id.create("To Shek Tong Tsui", TransitRoute.class);
		
		test.setFullFare(lineToTestId, 10.6);
		test.addRoute(lineToTestId, routeToTestId, 5.3 + 5.3);
		test.addSectionFare(lineToTestId, routeToTestId, 0, 30, 5.3);
		test.addSectionFare(lineToTestId, routeToTestId, 26, 50, 5.3);
		test.addSectionFare(lineToTestId, routeToTestId, 27, 49, 5.3);
//
//		test.setFullFare(lineToTestId, 10.6);
//
//		test.addRoute(lineToTestId, routeToTestId, 5.3 + 5.3);
//
//		test.addSectionFare(lineToTestId, routeToTestId, Id.create("bus_559", TransitStopFacility.class), 1,
//				Id.create("bus_384", TransitStopFacility.class), 1, 5.3);
//
//		test.addSectionFare(lineToTestId, routeToTestId, Id.create("bus_12946", TransitStopFacility.class), 1,
//				4.8 + 5.3);
//		test.addSectionFare(lineToTestId, routeToTestId, Id.create("bus_12946", TransitStopFacility.class), 1,
//				Id.create("bus_384", TransitStopFacility.class), 1, 4.8);
//		test.addSectionFare(lineToTestId, routeToTestId, Id.create("bus_639", TransitStopFacility.class), 1, 3.6 + 5.3);
//		test.addSectionFare(lineToTestId, routeToTestId, Id.create("bus_639", TransitStopFacility.class), 1,
//				Id.create("bus_384", TransitStopFacility.class), 1, 3.6);
//
//		test.addSectionFare(lineToTestId, routeToTestId, Id.create("bus_396", TransitStopFacility.class), 1, 5.3);
//
//		test.addSectionFare(lineToTestId, routeToTestId, Id.create("bus_684", TransitStopFacility.class), 1, 3.4); // Bus
//																													// stop
//																													// 50
//
//		test.addSectionFare(lineToTestId, routeToTestId, Id.create("bus_12578", TransitStopFacility.class), 1, 3.0); // Bus
//																														// stop
//	
	}
	
	public boolean checkValidity() {
		for(LineFare lineFare:lineFareMap.values()) {
			if(lineFare.busRouteFare!=null) {
				for(BusRouteFare brf: lineFare.busRouteFare.values()) {
					for(List<Double> zonalFareTo : brf.zonalFareFromTo) {
						double lastFare = Double.POSITIVE_INFINITY;
						for(double fare: zonalFareTo) {
							if((lastFare!=Double.POSITIVE_INFINITY && fare < lastFare) || fare == Double.MAX_VALUE) {
								continue; //For debug purpose
								//throw new IllegalArgumentException("The fare is cheaper when travel more!");
							}
							lastFare = fare;
						}
					}
				}
			}
		}
		return true;
	}

	@Override
	public double getFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId,
			int fromOccurence, Id<TransitStopFacility> toStopId, int toOccurence) {
		return lineFareMap.get(lineId).getFare(routeId, fromStopId, fromOccurence, toStopId, toOccurence) * this.fareFactor;
	}

	@Override
	public void setFareFactor(double fareFactor) {
		this.fareFactor = fareFactor;
	}

}
