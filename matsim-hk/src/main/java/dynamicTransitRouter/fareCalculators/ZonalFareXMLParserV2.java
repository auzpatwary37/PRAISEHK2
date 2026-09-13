package dynamicTransitRouter.fareCalculators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

public class ZonalFareXMLParserV2 extends DefaultHandler {
	private ZonalFareCalculator zonalFare;
	private List<Id<TransitStopFacility>> stopFacilityList;
	private HashMap<Integer, Integer> stopSequence2Zone; // Store the zone number based on the position of
															// TransitRouteStop
	private HashMap<Id<TransitStopFacility>, List<Integer>> stopSequenceFromFacility;
	private Map<String, Integer> occurenceOfBusStopId; //Store the actual occurence of bus stop Id
	private List<List<Double>> zonalFareFromTo;
	private List<Double> fareTo;

	private Id<TransitLine> currTransitLineId;
	private Id<TransitRoute> currTransitRouteId;

	public ZonalFareXMLParserV2(TransitSchedule ts) {
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
			stopSequence2Zone = new HashMap<Integer, Integer>();
			stopSequenceFromFacility = new HashMap<Id<TransitStopFacility>, List<Integer>>();
			occurenceOfBusStopId = new HashMap<>();
		}
		
		if (qName.equalsIgnoreCase("Stop")) {
			Id<TransitStopFacility> tsfID = Id.create(attributes.getValue("id"), TransitStopFacility.class);
			stopFacilityList.add(tsfID);
			stopSequence2Zone.put(Integer.parseInt(attributes.getValue("seq")),
					Integer.parseInt(attributes.getValue("zone")));
			
			String busStopId = tsfID.toString().replace("BT_", "").replace("bus_", "");
			//Add the facility to stop sequence.
			if(occurenceOfBusStopId.containsKey(busStopId)) {
				occurenceOfBusStopId.put(busStopId, occurenceOfBusStopId.get(busStopId)+1);
			}else {
				occurenceOfBusStopId.put(busStopId, 0);
			}
			
			List<Integer> stopSequences = stopSequenceFromFacility.containsKey(tsfID)? 
					stopSequenceFromFacility.get(tsfID) : new ArrayList<Integer>();
			while(stopSequences.size() < occurenceOfBusStopId.get(busStopId)) {
				stopSequences.add(-1); //Adding the missing stop sequences
			}
			stopSequenceFromFacility.put(Id.create(attributes.getValue("id"), TransitStopFacility.class),
					stopSequences);
			stopSequences.add(stopFacilityList.size()-1); //Add the current sequence.
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
