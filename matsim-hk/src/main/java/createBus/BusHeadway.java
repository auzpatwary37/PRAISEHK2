package createBus;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitRoute;

/**
 * A customized class that stores the bus headway.
 * 
 * @author eleead
 *
 */
public class BusHeadway {
	private final ArrayList<Integer> headWaySecond;
	private final ArrayList<Integer> fromTime;
	private final ArrayList<Integer> toTime;
	private final ArrayList<Id<TransitRoute>> headwayRouteId;

	private DepartureList departures; // The list of departures and the corresponding transitRouteId

	private final ArrayList<Integer> departureTime; // The separated departure time.
	private final ArrayList<Id<TransitRoute>> timeRouteId;

	private final String mode;

	public BusHeadway(String mode) {
		headWaySecond = new ArrayList<Integer>();
		fromTime = new ArrayList<Integer>();
		toTime = new ArrayList<Integer>();
		headwayRouteId = new ArrayList<Id<TransitRoute>>();

		departureTime = new ArrayList<Integer>();
		timeRouteId = new ArrayList<Id<TransitRoute>>();
		if (mode.equals("minibus") || mode.equals("bus")) {
			this.mode = mode;
		} else {
			throw new IllegalArgumentException(
					"The mode " + mode + " is not supported! Should be either bus or minibus.");
		}
	}

	public void addHeadway(Id<TransitRoute> transitRouteId, int from, int to, int second) {
		if (!mode.equals("minibus") && !toTime.isEmpty() && from < toTime.get(toTime.size() - 1) && 
				transitRouteId.equals(headwayRouteId.get(headwayRouteId.size()-1))) {
//			throw new IllegalArgumentException("The from time is before the last headway schedule ends.");
			//Log.warn("The from time is before the last headway schedule ends.");
		}
		fromTime.add(from);
		toTime.add(to);
		headWaySecond.add(second);
		headwayRouteId.add(transitRouteId);
	}

	public void addDeparture(Id<TransitRoute> transitRoute, int time) {
		timeRouteId.add(transitRoute);
		departureTime.add(time);
	}

	public void addDeparture(Id<TransitRoute> transitRoute, int hour, int minute) {
		if (hour < 4) { // The bus cannot be run in the beginning of the day
			hour += 24;
		}
		this.addDeparture(transitRoute, hour * 60 * 60 + minute * 60);
	}

	/**
	 * 
	 * @param fromHour
	 *            The hour of the headway effective
	 * @param fromMinute
	 *            The minute of the headway effective
	 * @param toHour
	 *            The hour of the headway effective
	 * @param toMinute
	 *            The minute of the headway ends
	 * @param headwayMinute
	 *            The headway in minute
	 */
	public void addHeadWay(Id<TransitRoute> transitRoute, int fromHour, int fromMinute, int toHour, int toMinute,
			int headwayMinute) {
		if (fromHour > 30) {
			throw new IllegalArgumentException("The from hour " + fromHour + " is wrong. Should be between 0-30");
		}
		if (toHour > 30) {
			throw new IllegalArgumentException("The to hour " + toHour + " is wrong. Should be between 0-30");
		}
		if (fromMinute >= 60) {
			throw new IllegalArgumentException("The from minute " + fromMinute + " is wrong. Should be between 0-59");
		}
		if (toMinute >= 60) {
			throw new IllegalArgumentException("The to minute " + toMinute + " is wrong. Should be between 0-59");
		}
		this.addHeadway(transitRoute, fromHour * 60 * 60 + fromMinute * 60, toHour * 60 * 60 + toMinute * 60,
				headwayMinute * 60);
		System.out.println("added headway "+fromHour+":"+fromMinute+" to "+toHour+":"+toMinute+" headway "+headwayMinute);
	}

	public int getHeadWaySecond(int index) {
		return headWaySecond.get(index);
	}

	public int getFromTime(int index) {
		return fromTime.get(index);
	}

	public int getToTime(int index) {
		return toTime.get(index);
	}

	public int getSize() {
		return toTime.size();
	}

	private DepartureList generateDeparture() {
		DepartureList departure = new DepartureList();
		for (int i = 0; i < departureTime.size(); i++) {
			departure.addDeparture(timeRouteId.get(i), departureTime.get(i));
		}

		for (int i = 0; i < fromTime.size(); i++) {
			Id<TransitRoute> routeId = headwayRouteId.get(i);
			for (int time = fromTime.get(i); time <= toTime.get(i); time += headWaySecond.get(i)) {
				if (!departure.haveDeparture(routeId, time)) {
					departure.addDeparture(routeId, time);
				}
			}
		}
		//TODO: Add a checking for replicated departure.
		// System.out.println(departure);
		return departure;
	}

	public void makeBusDepartures(String direction, String filePath) throws IOException {
		if (!this.mode.equals("bus")) {
			throw new IllegalStateException("This headway object is not for bus! For " + this.mode + " instead.");
		}
		this.new BusHeadwayReader().readFileAndConvertToSchedule(direction, filePath);
		this.departures = this.generateDeparture();
	}

	public void makeMinibusDepartures(String filePath) throws IOException {
		if (!this.mode.equals("minibus")) {
			throw new IllegalStateException("This headway object is not for minibus! For " + this.mode + " instead.");
		}
		this.new BusHeadwayReader().readFileAndConvertToSchedule(null, filePath);
		this.departures = this.generateDeparture();
	}

	public DepartureList getDepartures() {
		return departures;
	}

	/**
	 * A reader to read the headway file and add it to corresponding list
	 * 
	 * @author eleead
	 *
	 */
	private class BusHeadwayReader {
		private static final String rangeString = "[0-9][0-9]:[0-9][0-9](\\*)?\\s\\-\\s\\d\\d:\\d\\d(\\*)?"; // e.g. 01:23 - 02:34
		private static final String multiTimeString = "((\\d\\d:\\d\\d, )+(\\d\\d:\\d\\d))"; // e.g. 01:23, 02:34, 03:34
		private static final String singleTimeString = "(\\A\\d\\d:\\d\\d\\Z)"; // e.g. 05:20
		private static final String singleHeadwayString = "\\A\\d+\\Z"; // e.g. 12
		private static final String meansNothingString1 = "\\* - "; 
		private static final String meansNothingString2 = " - \\d\\d:\\d\\d"; //e.g. - 10:12
		private static final String rangeOrSlashHeadwayString = "(\\A\\d+(~|-|/)\\d+\\Z)"; // e.g. 12~15
		private static final String minibusRangeString = "\\A\\d\\d:\\d\\d (a|p)m - \\d\\d:\\d\\d (a|p)m"; // e.g. 06:45
																											// am -
																											// 12:25 am
		private static final String minibusSingleTimeString = "\\A\\d\\d:\\d\\d (a|p)m"; // e.g. 07:40 am
		private static final String minibusMultipleTimeString = "((\\d\\d:\\d\\d (a|p)m,)+(\\d\\d:\\d\\d (a|p)m))"; // e.g. 07:40 am,08:00 am
		private final Pattern rangePattern;
		private final Pattern multiTimePattern;
		private final Pattern singleTimePattern;
		private final Pattern singleHeadwayPattern;
		private final Pattern rangeHeadwayPattern;
		private Pattern meansNothingPattern1;
		private Pattern meansNothingPattern2;
		private int splitTime = 4; 			//time which the simulation splits the day or for bus headway split

		private BusHeadwayReader() {
			if (mode.equals("bus")) {
				rangePattern = Pattern.compile(rangeString);
				multiTimePattern = Pattern.compile(multiTimeString);
				singleTimePattern = Pattern.compile(singleTimeString);
				singleHeadwayPattern = Pattern.compile(singleHeadwayString);
				rangeHeadwayPattern = Pattern.compile(rangeOrSlashHeadwayString);
				meansNothingPattern1 = Pattern.compile(meansNothingString1);
				meansNothingPattern2 = Pattern.compile(meansNothingString2);
			} else if (mode.equals("minibus")) {
				rangePattern = Pattern.compile(minibusRangeString);
				multiTimePattern = Pattern.compile(minibusMultipleTimeString);
				singleTimePattern = Pattern.compile(minibusSingleTimeString);
				singleHeadwayPattern = Pattern.compile(singleHeadwayString);
				rangeHeadwayPattern = Pattern.compile(rangeOrSlashHeadwayString);
			} else {
				throw new IllegalArgumentException("The mode specified is wrong!");
			}
		}

		private void readFileAndConvertToSchedule(String direction, String filePath) throws IOException {
			if (mode.equals("bus")) {
				this.readBusFileAndConvertToSchedule(direction, filePath);
			} else if (mode.equals("minibus")) {
				this.readMinibusFileAndConvertToSchedule(filePath);
			} else {
				throw new IllegalArgumentException("Something went wrong!");
			}
		}

		private void readMinibusFileAndConvertToSchedule(String filePath) throws IOException {
			Reader line_in = new FileReader(filePath);
			Iterable<CSVRecord> nodes = CSVFormat.RFC4180.withSkipHeaderRecord().withHeader("A","Start1","Start2","Headway").parse(line_in);
			boolean weekDay = false;
			String direction1 = null;
			String direction2 = null;
			for (CSVRecord node : nodes) {
				// First iteration, obtain the directions first!
				if (direction1 == null) {
					direction1 = node.get("Start1");
					direction2 = node.get("Start2");
					continue;
				}

				// For second iteration.
				String timeString1 = node.get("Start1");
				String timeString2 = node.get("Start2");

				// To ensure the search is on the weekday. Maybe it is not needed here.
				if (!weekDay && (timeString1.contains("Monday"))) {
					weekDay = true;
					continue;
				} else if (!weekDay) {
					if (rangePattern.matcher(timeString1).find() || singleTimePattern.matcher(timeString1).find()) {
						weekDay = true;
						// It is the case when the Monday string is skipped, so we let go
					} else {
						throw new IllegalArgumentException("This file " + filePath + " seemed not legit!");
					}
				}
				Id<TransitRoute> routeId1 = null, routeId2 = null;

				if (!timeString2.isEmpty()) {
					routeId1 = Id.create(direction1 + " " + direction2.replace("From", "To"), TransitRoute.class);
					routeId2 = Id.create(direction2 + " " + direction1.replace("From", "To"), TransitRoute.class);
				} else {
					routeId1 = Id.create(direction1, TransitRoute.class);
				}
				Matcher rangeMatch1 = rangePattern.matcher(timeString1);
				Matcher rangeMatch2 = rangePattern.matcher(timeString2);
				if (rangeMatch1.find()) {
					processHeadway(routeId1, timeString1, node.get("Headway").replaceAll(" ", ""));
					if (!timeString2.isEmpty() && !timeString2.equals("- -")) {
						processHeadway(routeId2, timeString2, node.get("Headway").replaceAll(" ", ""));
					}
				}else if (multiTimePattern.matcher(timeString1).find()) {
					for(String timeString: timeString1.split(",")) {
						addDeparture(routeId1, getHour(timeString.substring(0, 2), timeString.substring(6, 8)),
								Integer.parseInt(timeString.substring(3, 5)));
					}
					//For route 2
					if(rangeMatch2.find()) {
						processHeadway(routeId2, timeString2, node.get("Headway").replaceAll(" ", ""));
					}else if(singleTimePattern.matcher(timeString2).find()) {
						addDeparture(routeId2, getHour(timeString2.substring(0, 2), timeString2.substring(6, 8)),
								Integer.parseInt(timeString2.substring(3, 5)));
					}
					break;
				}else if (singleTimePattern.matcher(timeString1).find()) {
					addDeparture(routeId1, getHour(timeString1.substring(0, 2), timeString1.substring(6, 8)),
							Integer.parseInt(timeString1.substring(3, 5)));
					
					//For route 2
					if (!timeString2.isEmpty()) {
						if(multiTimePattern.matcher(timeString2).find()) {
							for(String timeString: timeString2.split(",")) {
								addDeparture(routeId2, getHour(timeString.substring(0, 2), timeString.substring(6, 8)),
										Integer.parseInt(timeString.substring(3, 5)));
							}
						}else addDeparture(routeId2, getHour(timeString2.substring(0, 2), timeString2.substring(6, 8)),
									Integer.parseInt(timeString2.substring(3, 5)));
					}
				}else if(weekDay) {
					break;
				}
			}
		}

		private int getHour(String hourValue, String period) {
			int hour = Integer.parseInt(hourValue);
			hour = (hour == 12 ? 0 : hour); // Adjustment for 12:XX (am/pm) to 0
			if (period.equals("pm")) { // Adjustment for pm
				hour += 12;
			}
			return hour;
		}

		private void processHeadway(Id<TransitRoute> routeId, String timeString, String headway) {
			String startPeriod = timeString.substring(6, 8);
			int startHour = getHour(timeString.substring(0, 2), startPeriod);

			// Get the correct end hour
			int endHour = Integer.parseInt(timeString.substring(11, 13));
			endHour = (endHour == 12 ? 0 : endHour);
			String endPeriod = timeString.substring(17, 19);
			if (endPeriod.equals("pm")) { // Adjustment for pm
				endHour += 12;
			} else if (startPeriod.equals("am") && startHour > endHour) { // If it is over another day
				endHour += 24;
			}

			// The start routeId and end hour is set cannot be less than 4
			addHeadWay(routeId, startHour, Integer.parseInt(timeString.substring(3, 5)), endHour,
					Integer.parseInt(timeString.substring(14, 16)), getHeadway(headway));
		}

		private void readBusFileAndConvertToSchedule(String direction, String filePath) throws IOException {
			Reader line_in = new FileReader(filePath);
			Iterable<CSVRecord> nodes;
			try {
				nodes = CSVFormat.RFC4180.withSkipHeaderRecord().withHeader("A","Timetable","Headway","Route").parse(line_in); //Either four columns
			}catch(IllegalArgumentException e) {
				nodes = CSVFormat.RFC4180.withSkipHeaderRecord().withHeader("A","Timetable","Headway").parse(line_in); //or three columns
			}
			boolean weekDay = false;
			boolean keepPassing = false; // true to pass the rows.
			for (CSVRecord node : nodes) {
				String timeString = node.get("Timetable");
				boolean rangeFound = rangePattern.matcher(timeString).find();
				boolean multiTimeFound = multiTimePattern.matcher(timeString).find();
				boolean singleTimeFound = singleTimePattern.matcher(timeString).find();
				boolean meansNothingFound = meansNothingPattern1.matcher(timeString).find() || meansNothingPattern2.matcher(timeString).find();
				// To ensure the search is on the weekday.
				if(weekDay && (rangeFound || multiTimeFound || singleTimeFound)) {
					//Let the rest of the program work for weekday and time pattern
				}else if(meansNothingFound) {
					continue; //We continue the iteration if it proven to be means nothing.
				}else if ( node.get("Timetable").contains("Every Monday to Friday")
						|| node.get("Timetable").equalsIgnoreCase("Every Monday to Thursday")
						|| node.get("Timetable").equalsIgnoreCase("Every Monday to Saturday")
						|| node.get("Timetable").equalsIgnoreCase("Daily")
						|| node.get("Timetable").contains("Mon To Fri")
						|| node.get("Timetable").contains("Mon To Sat")
						|| node.get("Timetable").equalsIgnoreCase("everyday")) {
					weekDay = true; //Start parsing
					keepPassing = false;
					continue;
				} else if (weekDay || keepPassing || timeString.contains("Approx")) {
					keepPassing = true; // The headway will not be considered if it is just approximation for circular
										// route
					weekDay = false;
					continue;
				} else if (!weekDay) {
					return; // This headway would not be considered.
					// throw new IllegalArgumentException("This file "+filePath+" seemed not
					// legit!");
				}

				Id<TransitRoute> thisRouteId = null;
				if (node.size() == 3) {
					thisRouteId = Id.create(direction, TransitRoute.class);
				} else if (node.size() == 4) {
					if (node.get("Route").isEmpty()) {
						break;
					}
					thisRouteId = Id.create(direction + "_" + node.get(3).charAt(1), TransitRoute.class);
				}
				
				if (rangeFound) {
					int startHour = Integer.parseInt(timeString.substring(0, 2));
					int endHour = Integer.parseInt(timeString.charAt(5)!='*'?timeString.substring(8, 10) :
																			timeString.substring(9, 11));
					int startMin = Integer.parseInt(timeString.substring(3, 5));
					int endMin = Integer.parseInt(timeString.charAt(5)!='*'?timeString.substring(11, 13):timeString.substring(12, 14));
					int headway = getHeadway(node.get("Headway"));
					if(headway == 999)
						continue;

//					// The start hour and end hour is set cannot be less than 4
//					addHeadWay(thisRouteId, ((startHour < 4) ? startHour + 24 : startHour),
//							Integer.parseInt(timeString.substring(3, 5)), ((endHour < 4) ? endHour + 24 : endHour),
//							Integer.parseInt(timeString.charAt(5)!='*'?timeString.substring(11, 13):
//								timeString.substring(12, 14)), getHeadway(node.get("Headway")));
//					
//					//splits the departures into 2 parts at the split time
//					if((startHour<splitTime && endHour>splitTime) || (startHour>endHour && endHour>splitTime) || (startHour==endHour && startMin>=endMin)) {
//						//e.i. 0100-0500 2200-0500 if splitTime = 4
//						if(startHour<splitTime)
//							startHour+=24;
//						
//						int dummyStartHour = startHour;
//						int dummyStartMin = startMin;
//						int dummyHeadway = headway;
//						
//						addHeadWay(thisRouteId, startHour,
//								dummyStartMin, splitTime+24,
//								0, dummyHeadway);
//						
//						//to find start time after 4 am split
//						while(dummyStartHour<splitTime+24) {
//							dummyStartMin += dummyHeadway;
//							while(dummyStartMin>=60) {
//								dummyStartMin-=60;
//								dummyStartHour++;
//							}
//						}
//						
//						addHeadWay(thisRouteId, dummyStartHour-24,
//								dummyStartMin, endHour,
//								endMin, dummyHeadway);
//						
//					} else {
//						if(startHour<splitTime)
//							startHour+=24;
//						if(endHour<splitTime || (endHour==splitTime && endMin==0))
//							endHour+=24;
//						addHeadWay(thisRouteId, startHour,
//								startMin, endHour,
//								endMin, headway);
//					}
					
					
					//trial 2, duplicates the departures from 0000 to splitTime
					if((startHour<splitTime) || (startHour>endHour) /*|| (startHour==endHour && startMin>=endMin)*/) {
						//stop supporting 24hr range where it is not 0000-2400 due to RIRO ruining everything
						if(startHour<splitTime /*&& !(startHour==endHour && startMin>=endMin)*/)
							startHour+=24;
							
						int dummyStartHour = startHour;
						int dummyStartMin = startMin;
						
						while(dummyStartHour<24) {
							dummyStartMin += headway;
							while(dummyStartMin>=60) {
								dummyStartMin-=60;
								dummyStartHour++;
							}
						}
						
						addHeadWay(thisRouteId, dummyStartHour-24, dummyStartMin, endHour, endMin, headway);
						if(endHour<splitTime /*&& !(startHour==endHour && startMin>=endMin)*/)
							addHeadWay(thisRouteId, startHour, startMin, endHour+24, endMin, headway);
						else
							addHeadWay(thisRouteId, startHour, startMin, splitTime+24, 0, headway);
					} else {
						addHeadWay(thisRouteId, startHour, startMin, endHour, endMin, headway);
					}
						
					
					
					// System.out.println("Type 1 : "+timeRangeString);
				} else if (multiTimeFound) {
					for (String time : timeString.split(", ")) {
						addDeparture(thisRouteId, Integer.parseInt(time.substring(0, 2)),
								Integer.parseInt(time.substring(3, 5)));
					}
				} else if (singleTimeFound) {
					addDeparture(thisRouteId, Integer.parseInt(timeString.substring(0, 2)),
							Integer.parseInt(timeString.substring(3, 5)));
				} else {
					break;
				}
			}
		}

		private int getHeadway(String headwayString) {
			Matcher rangeHeadway = rangeHeadwayPattern.matcher(headwayString);
			Matcher singleHeadway = singleHeadwayPattern.matcher(headwayString);
			Matcher withSpaceHeadway = rangeHeadwayPattern.matcher(headwayString.replace(" ", ""));
			if (singleHeadway.find()) {
				return Integer.parseInt(headwayString);
			} else if (rangeHeadway.find()) {
				int index = Math.max(Math.max(headwayString.indexOf('~'), headwayString.indexOf('-')), headwayString.indexOf('/'));
				return (Integer.parseInt(headwayString.substring(0, index))
						+ Integer.parseInt(headwayString.substring(index + 1, headwayString.length()))) / 2;
			} else if(withSpaceHeadway.find()) {
				headwayString = headwayString.replace(" ", "");
				int index = Math.max(Math.max(headwayString.indexOf('~'), headwayString.indexOf('-')), headwayString.indexOf('/'));
				return (Integer.parseInt(headwayString.substring(0, index))
						+ Integer.parseInt(headwayString.substring(index + 1, headwayString.length()))) / 2;
			}else {
				throw new IllegalArgumentException("The string " + headwayString + " is not in a proper format!");
			}
		}
	}

	public class DepartureList {
		final private List<Integer> departureTimes;
		final private List<Id<TransitRoute>> departureRoute;

		private DepartureList() {
			departureTimes = new ArrayList<Integer>();
			departureRoute = new ArrayList<Id<TransitRoute>>();
		}

		private void addDeparture(Id<TransitRoute> routeId, int time) {
			this.departureRoute.add(routeId);
			this.departureTimes.add(time);
		}

		/**
		 * Return the unique routes that is in the departures of headway.
		 * 
		 * @return
		 */
		public List<Id<TransitRoute>> getUniqueRoutes() {
			return new ArrayList<Id<TransitRoute>>(new HashSet<Id<TransitRoute>>(departureRoute));
		}

		public int getSize() {
			return departureTimes.size();
		}

		public int getDepartureTime(int index) {
			return departureTimes.get(index);
		}

		public Id<TransitRoute> getDepartureRoute(int index) {
			return departureRoute.get(index);
		}

		public List<Integer> getDeparturesForRoute(Id<TransitRoute> routeId) {
			List<Integer> departuresForRoute = new ArrayList<Integer>();
			for (int i = 0; i < departureRoute.size(); i++) {
				if (departureRoute.get(i).equals(routeId)) {
					departuresForRoute.add(departureTimes.get(i));
				}
			}
			return departuresForRoute;
		}

		public Map<Integer, Integer> getDepartureAndOrderForRoute(Id<TransitRoute> routeId) {
			Map<Integer, Integer> departuresAndOrderForRoute = new HashMap<Integer, Integer>();
			for (int i = 0; i < departureRoute.size(); i++) {
				if (departureRoute.get(i).equals(routeId)) {
					departuresAndOrderForRoute.put(i, departureTimes.get(i));
				}
			}
			return departuresAndOrderForRoute;
		}

		/**
		 * Return true if there is departure in the specified time and the same route
		 * @param routeId The route ID
		 * @param time The time
		 * @return
		 */
		private boolean haveDeparture(Id<TransitRoute> routeId, int time) {
			for(int i = 0; i< departureTimes.size(); i++) {
				if(departureTimes.get(i)==time) {
					if(departureRoute.get(i).equals(routeId)) {
						return true;
					}
				}
			}
			return false;
		}
	}
}
