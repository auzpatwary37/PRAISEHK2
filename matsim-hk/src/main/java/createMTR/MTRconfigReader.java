package createMTR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.matsim.core.utils.collections.Tuple;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public final class MTRconfigReader extends DefaultHandler {
	private static HashMap<String, ArrayList<Direction>> lineToDirection = new HashMap<String, ArrayList<Direction>>();
	private static HashMap<String, Integer> lineToVehicleCount = new HashMap<String, Integer>();
	private static HashMap<String, String> lineToVehicleIdFront = new HashMap<String, String>();
	private static HashMap<Direction, Integer> firstTrain = new HashMap<Direction, Integer>();
	private static HashMap<Direction, Integer> lastTrain = new HashMap<Direction, Integer>();
	private static HashMap<Direction, LinkedHashMap<Tuple<Integer, Integer>, Headway>> headwayFromTimeMap = new HashMap<Direction, LinkedHashMap<Tuple<Integer, Integer>, Headway>>();

	private static LinkedHashMap<Tuple<Integer, Integer>, Headway> headwayFromTime; // A map to store the headway, with
																					// time as key.
	private String currLine;
	private Direction currDirection;
	private ArrayList<Direction> currLineDirections;
	private int lastTime = 0;

	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if ("transitLine".equals(qName)) {
			currLine = attributes.getValue("name");
			currLineDirections = new ArrayList<Direction>();
		} else if ("direction".equals(qName)) { // Store the from to, and direction, if it is starting of direction.
			String from = attributes.getValue("from");
			String to = attributes.getValue("to");
			currDirection = new Direction(from, to);
			headwayFromTime = new LinkedHashMap<Tuple<Integer, Integer>, Headway>();
			currLineDirections.add(currDirection);
		} else if ("firstTrain".equals(qName)) { // Store the time of first train of that direction
			firstTrain.put(currDirection, convertToTime(attributes.getValue("time")));
			lastTime = convertToTime(attributes.getValue("time"));
		} else if ("lastTrain".equals(qName)) { // Store the time of last train of that direction
			int lastTrainTime = convertToTime(attributes.getValue("time"));
			if (lastTrainTime != lastTime) {
				throw new RuntimeException("The last train time is not align with frequency end time");
			}
			lastTrain.put(currDirection, lastTrainTime);
		} else if ("headway".equals(qName)) { // Store the headway from time to time
			int fromTime = convertToTime(attributes.getValue("from"));
			int toTime = convertToTime(attributes.getValue("to"));

			if (fromTime != lastTime)
				throw new RuntimeException("The headwaies of " + currLine + " " + currDirection + " starts in "
						+ fromTime + " are not one by one");

			Headway headway = new Headway(attributes.getValue("headway"));
			if (attributes.getLength() > 3) {
				headway.setPortionAndDenominator(attributes.getValue("portion"), attributes.getValue("denominator"));
			}
			headwayFromTime.put(new Tuple<Integer, Integer>(fromTime, toTime), headway);
			lastTime = toTime;
		} else if ("vehicle".equals(qName)) {
			lineToVehicleCount.put(currLine, Integer.parseInt(attributes.getValue("count")));
			lineToVehicleIdFront.put(currLine, attributes.getValue("IdFront"));
		}
	}

	public void endElement(String uri, String localName, String qName) {
		if ("transitLine".equals(qName))
			lineToDirection.put(currLine, currLineDirections);
		else if ("direction".equals(qName)) {
			headwayFromTimeMap.put(currDirection, headwayFromTime);
		}
	}

	/**
	 * 
	 * @param time
	 *            String time in format "xx:xx", 24h format
	 * @return the minutes from 00:00
	 */
	private int convertToTime(String time) {
		int hour = Integer.parseInt(time.substring(0, 2));
		int min = Integer.parseInt(time.substring(3, 5));

		if (hour > 4) {
			return hour * 60 + min;
		} else {
			return (hour + 24) * 60 + min;
		}
	}

	public static int getFirstTrain(Direction d) {
		return firstTrain.get(d);
	}

	public static int getLastTrain(Direction d) {
		return lastTrain.get(d);
	}

	public static LinkedHashMap<Tuple<Integer, Integer>, Headway> getFrequencyFrom(Direction d) {
		return headwayFromTimeMap.get(d);
	}

	public static int getTrainCount(String line) {
		return lineToVehicleCount.get(line);
	}

	public static String getTrainIdFront(String line) {
		return lineToVehicleIdFront.get(line);
	}

	public static ArrayList<Direction> getDirections(String line) {
		return lineToDirection.get(line);
	}
}
