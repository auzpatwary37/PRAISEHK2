package dynamicTransitRouter.fareCalculators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Deprecated
public class ZonalFareXMLParser extends DefaultHandler {
	private ZonalFareCalculator zonalFare;
	private List<Id<TransitStopFacility>> stopFacilityList;
	private HashMap<Integer, Integer> stopSequence2Zone; // Store the zone number based on the position of
															// TransitRouteStop
	private HashMap<Id<TransitStopFacility>, List<Integer>> stopSequenceFromFacility;
	private List<Integer> tempSequences;
	private List<List<Double>> zonalFareFromTo;
	private List<Double> fareTo;

	private Id<TransitLine> currTransitLineId;
	private Id<TransitRoute> currTransitRouteId;

	public ZonalFareXMLParser(TransitSchedule ts) {
		zonalFare = new ZonalFareCalculator(ts);
	}

	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if (qName.equalsIgnoreCase("Line")) {
			currTransitLineId = Id.create(attributes.getValue("id"), TransitLine.class);
			zonalFare.addLine(currTransitLineId, Boolean.parseBoolean(attributes.getValue("Circular")),
					Double.parseDouble(attributes.getValue("FullFare")));
		}
		if (qName.equalsIgnoreCase("Route")) {
			currTransitRouteId = Id.create(attributes.getValue("id"), TransitRoute.class);
		}

		if (qName.equalsIgnoreCase("StopList")) {
			stopFacilityList = new ArrayList<Id<TransitStopFacility>>();
		}
		if (qName.equalsIgnoreCase("Stop")) {
			stopFacilityList.add(Id.create(attributes.getValue("id"), TransitStopFacility.class));
		}

		if (qName.equalsIgnoreCase("stopsequencetoZone")) {
			stopSequence2Zone = new HashMap<Integer, Integer>();
		}

		if (qName.equalsIgnoreCase("StopSequenceAndZone")) {
			stopSequence2Zone.put(Integer.parseInt(attributes.getValue("seq")),
					Integer.parseInt(attributes.getValue("zone")));
		}

		if (qName.equalsIgnoreCase("FacilityToStopSequence")) {
			stopSequenceFromFacility = new HashMap<Id<TransitStopFacility>, List<Integer>>();
		}

		if (qName.equalsIgnoreCase("StopFacility")) {
			tempSequences = new ArrayList<Integer>();
			stopSequenceFromFacility.put(Id.create(attributes.getValue("id"), TransitStopFacility.class),
					tempSequences);
		}

		if (qName.equalsIgnoreCase("StopSequence")) {
			tempSequences.add(Integer.parseInt(attributes.getValue("seq")));
		}

		if (qName.equalsIgnoreCase("ZoneFareFromTo")) {
			zonalFareFromTo = new ArrayList<List<Double>>();
		}

		if (qName.equalsIgnoreCase("From")) {
			fareTo = new ArrayList<Double>();
		}

		if (qName.equalsIgnoreCase("To")) {
			fareTo.add(Double.parseDouble(attributes.getValue("fare")));
		}

	}

	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (qName.equalsIgnoreCase("Route")) {
			zonalFare.addRoute(currTransitLineId, currTransitRouteId, stopFacilityList, stopSequence2Zone,
					stopSequenceFromFacility, zonalFareFromTo);
		}

		if (qName.equalsIgnoreCase("From")) {
			zonalFareFromTo.add(fareTo);
		}

	}

	public ZonalFareCalculator get() {
		return zonalFare;
	}
}
