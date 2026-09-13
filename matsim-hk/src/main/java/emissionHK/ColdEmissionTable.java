package emissionHK;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.vehicles.Vehicle;

import com.google.common.collect.Lists;

import emissionHK.EmissionUtils.ColdPollutant;
import emissionHK.EmissionUtils.EmissionVehicleType;

public class ColdEmissionTable {
	private final Map<EmissionVehicleType, Map<ColdPollutant, ColdEmissionFunction>> pollutionMap;
	private final List<ColdPollutant> pollutantConsidered;
	
	public ColdEmissionTable(String filePath) throws IOException { //Initialization from the file.
		pollutionMap = new HashMap<>();
		pollutantConsidered = new ArrayList<ColdPollutant>();
		pollutantConsidered.add(ColdPollutant.HC); //As VOC in the table.
		pollutantConsidered.add(ColdPollutant.CO);
		pollutantConsidered.add(ColdPollutant.NOX);
		pollutantConsidered.add(ColdPollutant.PM); //Equivalent to PM2.5
		pollutantConsidered.add(ColdPollutant.CO2_TOTAL);
		
		Reader line_in = new FileReader(filePath);
		Iterable<CSVRecord> nodes = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(line_in);
		
		EmissionVehicleType lastVehicleType = null;
		Map<ColdPollutant, ColdEmissionFunction> vehicleEmissionMap = null;
		ColdPollutant lastPollutant = null;
		ColdEmissionFunction emissionFunctionCache = null;
		for(CSVRecord node : nodes) {
			EmissionVehicleType thisVehicleType = EmissionUtils.getEmissionVehicleTypes(node.get("Vehicle type"));
			ColdPollutant thisPollutant = EmissionUtils.getColdPollutant(node.get("Pollutant"));
			if(!thisVehicleType.equals(lastVehicleType)) { //Create new vehicle type if possible
				vehicleEmissionMap = new HashMap<>();
				pollutionMap.put(thisVehicleType, vehicleEmissionMap);
			}
			
			//Create pollutant if possible
			if(!thisPollutant.equals(lastPollutant)) {
				emissionFunctionCache = new ColdEmissionFunction();
				vehicleEmissionMap.put(thisPollutant, emissionFunctionCache);
			}
			//Add the emission to the cache
			emissionFunctionCache.addEmissionValue(Integer.parseInt(node.get(2)), Double.parseDouble(node.get(3)));
			
			lastPollutant = thisPollutant;
			lastVehicleType = thisVehicleType;
		}
	}
	
	public Map<ColdPollutant, Double> processAndThrowColdEmissions(EventsManager em, double eventTime, Id<Link> linkId, Id<Vehicle> vehicleId, 
			EmissionVehicleType vt, double stopTime_in_s) {
		Map<ColdPollutant, Double> pollutants = getColdEmissions(vt, stopTime_in_s);
		em.processEvent(new ColdEmissionEvent(eventTime, linkId, vehicleId, pollutants)); //Throw the cold emission event at once
		return pollutants;
	}
	
	public Map<ColdPollutant, Double> getColdEmissions(EmissionVehicleType vt, double stopTime_s){
		Map<ColdPollutant, Double> pollutants = new HashMap<ColdPollutant, Double>();
		for(ColdPollutant pollutant: pollutantConsidered) {
			double factor = getEmissionFactor(vt, pollutant, stopTime_s);
			pollutants.put(pollutant, factor);
		}
		return pollutants;
	}
	
	public double getEmissionFactor(EmissionVehicleType vt, ColdPollutant pollutant, double stopTime_s) {
		return pollutionMap.get(vt).get(pollutant).getEmissionFactor(stopTime_s / 60 );
	}
	
	private class ColdEmissionFunction{
		private List<Integer> waitingTime_min;
		private List<Double> emission;
		/**
		 * Get the emission factor in grams/trip
		 * @return
		 */
		public ColdEmissionFunction() {
			this.waitingTime_min = new LinkedList<Integer>();
			this.emission = new ArrayList<Double>();
		}
		
		public void addEmissionValue(int waitingTime_min, double emissions) {
			this.waitingTime_min.add(waitingTime_min);
			this.emission.add(emissions);
		}
		
		public double getEmissionFactor(double stopTime_min) { 
			int index = Collections.binarySearch(waitingTime_min, (int) stopTime_min);
			if(index>=0) {
				return this.emission.get(index);
			}else {
				index = -(index+1);
				if(index>=this.waitingTime_min.size()) {
					return this.emission.get(this.emission.size()-1); //Give the final element if out of boundary
				}else if (index==0) {
					return this.emission.get(0);
				}
				double alpha = (stopTime_min - this.waitingTime_min.get(index-1)) / (this.waitingTime_min.get(index) - this.waitingTime_min.get(index-1));		
				return alpha * this.emission.get(index) + (1-alpha) * this.emission.get(index-1);
			}
		}
	}
}
