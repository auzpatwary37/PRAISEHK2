package transitFareAndHandler;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.AgentWaitingForPtEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

/**
 * This class counts the passengers at a stop facility in given time, and plot them
 * @author Enoch Lee
 *
 */
public class StopCountHandler implements AgentWaitingForPtEventHandler, PersonEntersVehicleEventHandler, 
		VehicleArrivesAtFacilityEventHandler, VehicleDepartsAtFacilityEventHandler{

	private Map<Id<TransitStopFacility>, List<TimeLoad>> stopWaitCount = new HashMap<>();
	private Map<Id<TransitStopFacility>, List<TimeLoad>> departingStopLoad = new HashMap<>();
	private Map<Id<Vehicle>, Id<TransitStopFacility>> vehicleAtStop = new HashMap<>();
	private double endTime = Double.MAX_VALUE;
	private final double scale;
	
	private Map<Id<TransitStopFacility>, List<TimeLoad>> stopEntry = new HashMap<>();
	
	public StopCountHandler(List<String> facilitiesConsidered, double endTime, double scale) {
		this.endTime = endTime;
		for(String tsfId: facilitiesConsidered) {
			stopWaitCount.put(Id.create(tsfId, TransitStopFacility.class), new ArrayList<>());
			departingStopLoad.put(Id.create(tsfId, TransitStopFacility.class), new ArrayList<>());
			stopEntry.put(Id.create(tsfId, TransitStopFacility.class), new ArrayList<>());
		}
		this.scale = scale;
	}
	
	@Override
	public void reset(int iteration) {
		//Reset the route data
		for(List<TimeLoad> timeLoadList: stopWaitCount.values()) {
			timeLoadList.clear();;
		}
	}
	
	@Override
	public void handleEvent(AgentWaitingForPtEvent event) {
		Id<TransitStopFacility> waitStopFacilityId = event.waitingAtStopId;
		if(stopWaitCount.containsKey(waitStopFacilityId)) {			
			List<TimeLoad> stopLoadTime = stopWaitCount.get(waitStopFacilityId);
			List<TimeLoad> stopEntryTime = stopEntry.get(waitStopFacilityId);
			if(stopLoadTime.size() > 0) {
				TimeLoad lastStopLoad = stopLoadTime.get(stopLoadTime.size() - 1);
				if(lastStopLoad.time == event.getTime()) {
					lastStopLoad.load++; //If the load object already there, add the load
					stopEntryTime.get(stopEntryTime.size() - 1).load++;
				}else {
					stopLoadTime.add(new TimeLoad(event.getTime(), lastStopLoad.load + 1)); //Otherwise, put a new load object
					stopEntryTime.add(new TimeLoad(event.getTime(), 1));
				}
			}else {
				stopLoadTime.add(new TimeLoad(event.getTime(), 1));
				stopEntryTime.add(new TimeLoad(event.getTime(), 1));
			}
		}
	}
	
	@Override
	public void handleEvent(VehicleArrivesAtFacilityEvent event) {
		if(stopWaitCount.containsKey(event.getFacilityId())) {
			vehicleAtStop.put(event.getVehicleId(), event.getFacilityId());
			departingStopLoad.get(event.getFacilityId()).add(new TimeLoad(event.getTime(), 0)); // Add a dummy timeload
		}
	}
	
	@Override
	public void handleEvent(VehicleDepartsAtFacilityEvent event) {
//		if(stopWaitCount.containsKey(event.getFacilityId())) {
//			List<TimeLoad> timeLoadList = stopWaitCount.get(event.getFacilityId());
//			List<TimeLoad> departureTimeLoadList = departingStopLoad.get(event.getFacilityId());
//			if(departureTimeLoadList.size() > 0 && departureTimeLoadList.get(departureTimeLoadList.size() - 1).time == event.getTime()) {
//				return;
//			}
//			
//			if(timeLoadList.size() > 1) {
//				TimeLoad lastStopLoad = timeLoadList.get(timeLoadList.size()-1);
//				if(lastStopLoad.time == event.getTime()) {
//					departureTimeLoadList.add(new TimeLoad(event.getTime(), timeLoadList.get(timeLoadList.size()-2).load));
//				}else {
//					departureTimeLoadList.add(new TimeLoad(event.getTime(), lastStopLoad.load));
//				}
//			}else {
//				departureTimeLoadList.add(new TimeLoad(event.getTime(), 0));
//			}
//		}
		vehicleAtStop.remove(event.getVehicleId());
	}
	
	@Override
	/**
	 * Reduce the platform load if a person enters vehicle
	 */
	public void handleEvent(PersonEntersVehicleEvent event) {
		Id<Vehicle> vehicleId = event.getVehicleId();
		if(vehicleAtStop.containsKey(vehicleId)) {
			Id<TransitStopFacility> tsFId = vehicleAtStop.get(vehicleId);
			List<TimeLoad> stopLoadTime = stopWaitCount.get(tsFId);
			TimeLoad lastStopLoad = stopLoadTime.get(stopLoadTime.size() - 1);
			if(lastStopLoad.time == event.getTime()) {
				lastStopLoad.load--; //If the load object already there, offload the load
			}else {
				stopLoadTime.add(new TimeLoad(event.getTime(), lastStopLoad.load - 1)); //Otherwise, put a new load object
			}
			
			//Set the depart (close door load)
			TimeLoad departLoad = departingStopLoad.get(tsFId).get(departingStopLoad.get(tsFId).size() - 1);
			departLoad.load = stopLoadTime.get(stopLoadTime.size() - 1).load;
			departLoad.time = event.getTime();
		}
	}
	
	public void createGraphs(final String directory) {
		createTimeSeries(stopWaitCount, directory, "stopLoad");
		createTimeSeries(departingStopLoad, directory, "departStopLoad");
		createTimeSeries(stopEntry, directory, "stopEntry");
	}
	
	/**
	 * This function creates the chart for stop load
	 * @param directory
	 */
	private void createTimeSeries(Map<Id<TransitStopFacility>, List<TimeLoad>> obj, String directory, 
			String suffix) {
		for(var stopWait: obj.entrySet()) {
			List<TimeLoad> loadTimeList = stopWait.getValue();
			if(loadTimeList.isEmpty()) {
				continue;
			}
			int numSeries = 0;
			double earliestTime = loadTimeList.get(0).time;
			double latestTime = endTime;
			
			FileWriter fileWriter;
			TimeSeries series = new TimeSeries("Stop Load");
			try {
				fileWriter = new FileWriter(directory+"MTR_"+stopWait.getKey().toString()+suffix+".csv", false);
				fileWriter.append("time,count\n");
	
				for (TimeLoad timeLoad : loadTimeList) {
					if(timeLoad.time > endTime) {
						break;
					}
					int hour = (int) timeLoad.time/3600;
					int minute = (int) (timeLoad.time - hour * 3600) / 60;
					int second = (int) (timeLoad.time - hour * 3600 - minute * 60);
					Second secondObj = new Second(second, minute, hour, 3, 5, 2022);
					series.add(secondObj, timeLoad.load * scale);
					fileWriter.append(hour+":"+minute+":"+second+","+timeLoad.load * scale+"\n");
				}
				fileWriter.close();
			} catch (IOException e1) {
				throw new UncheckedIOException(e1);
			}
			TimeSeriesCollection dataset = new TimeSeriesCollection(series);
	
			String baseString = "Stop Load-Time Diagram, Stop = ";
			if(suffix.equals("departStopLoad")) {
				baseString = "Stop Load-Time Diagram at Departure, Stop = ";
			}
			
			JFreeChart c = ChartFactory.createTimeSeriesChart(baseString + stopWait.getKey().toString(), 
					"Time", "Stop Load",
					dataset, 
					false, // legend?
					false, // tooltips?
					false // URLs?
					);
			c.setBackgroundPaint(new Color(1.0f, 1.0f, 1.0f, 1.0f));
	
			XYPlot p  = (XYPlot) c.getPlot();
	
//			p.getRangeAxis().setInverted(true);
//			p.getRangeAxis().setRange(earliestTime, latestTime);
			XYItemRenderer renderer = p.getRenderer();
			for (int i = 0; i < numSeries; i++) {
				renderer.setSeriesPaint(i, Color.black);
				renderer.setDefaultStroke(new BasicStroke(5.0f));
			}
	
			try {
				ChartUtils.saveChartAsPNG(new File(directory + stopWait.getKey().toString() + suffix + ".png"), c, 1024, 768, null, true, 9);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private class TimeLoad{
		private double time;
		private int load;
		
		private TimeLoad(double time, int load) {
			this.time = time;
			this.load = load;
		}
		
		@Override
		public String toString() {
			return "Time Load at "+time+" with load "+load+".";
		}
	}
}
