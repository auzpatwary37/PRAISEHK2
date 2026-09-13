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
import org.matsim.contrib.emissions.Pollutant;

import com.google.common.collect.Lists;

import emissionHK.EmissionUtils.EmissionVehicleType;

/**
 * This class imitates a table for the enquiry of warm emission.
 * @author eleead
 *
 */
public class WarmEmissionTable {
	private final Map<EmissionVehicleType, Map<Pollutant, WarmEmissionFunction>> pollutionMap;
	private final List<Pollutant> pollutantsConcerned;
	
	public WarmEmissionTable(String filePath) throws IOException { //Initialization from the file.
		pollutionMap = new HashMap<>();
		Set<Pollutant> pollutantConcernedSet = new HashSet<>();
		
		Reader line_in = new FileReader(filePath);
		Iterable<CSVRecord> nodes = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(line_in);
		
		EmissionVehicleType lastVehicleType = null;
		Map<Pollutant, WarmEmissionFunction> vehicleEmissionMap = null;
		Pollutant lastPollutant = null;
		WarmEmissionFunction emissionFunctionCache = null;
		for(CSVRecord node : nodes) {
			EmissionVehicleType thisVehicleType = EmissionUtils.getEmissionVehicleTypes(node.get("Vehicle type"));
			Pollutant thisPollutant = EmissionUtils.getWarmPollutant(node.get("Pollutant"));
			if(!thisVehicleType.equals(lastVehicleType)) { //Create new vehicle type if possible
				vehicleEmissionMap = new HashMap<>();
				pollutionMap.put(thisVehicleType, vehicleEmissionMap);
			}
			
			//Create pollutant if possible
			if(!thisPollutant.equals(lastPollutant)) {
				emissionFunctionCache = new WarmEmissionFunction();
				vehicleEmissionMap.put(thisPollutant, emissionFunctionCache);
				pollutantConcernedSet.add(thisPollutant);
			}
			//Add the emission to the cache
			emissionFunctionCache.addEmissionValue(Double.parseDouble(node.get(2)), Double.parseDouble(node.get(3)));
			
			lastPollutant = thisPollutant;
			lastVehicleType = thisVehicleType;
		}
		
		this.pollutantsConcerned = Lists.newArrayList(pollutantConcernedSet);
	}
	
	public Map<Pollutant, Double> getWarmEmissions(EmissionVehicleType vt, double linkLength_m, double speed){
		Map<Pollutant, Double> warmEmissions = new HashMap<>();
		for(Pollutant pollutant: this.pollutantsConcerned) {
			double emissionFactor = getEmissionFactor(vt, pollutant, speed);
			warmEmissions.put(pollutant, emissionFactor * linkLength_m/1000.0);
		}
		return warmEmissions;
	}
	
	public double getEmissionFactor(EmissionVehicleType vt, Pollutant pollutant, double speed) {
		if(vt!=EmissionVehicleType.ZeroEmissionVeh) {
			return pollutionMap.get(vt).get(pollutant).getEmissionFactor(speed*3.6);
		}else {
			return 0.0;
		}
	}
	
	private class WarmEmissionFunction{
		private List<Double> speed_kph;
		private List<Double> emission_gpkm;
		/**
		 * Get the emission factor in grams/trip
		 * @return
		 */
		public WarmEmissionFunction() {
			this.speed_kph = new LinkedList<Double>();
			this.emission_gpkm = new ArrayList<Double>();
		}
		
		public void addEmissionValue(double speed_kph, double emissions) {
			this.speed_kph.add(speed_kph);
			this.emission_gpkm.add(emissions);
		}
		
		/**
		 * Get the emission factor from speed_kph
		 * @param speed_kph
		 * @return The emission factor value in gram/km
		 */
		public double getEmissionFactor(double speed_kph) { 
			int index = Collections.binarySearch(this.speed_kph, speed_kph);
			if(index>=0) {
				return this.emission_gpkm.get(index);
			}else {
				index = -(index+1);
				if(index>=this.speed_kph.size()) {
					return this.emission_gpkm.get(this.emission_gpkm.size()-1); //Give the final element if out of boundary
				}else if (index==0) {
					return this.emission_gpkm.get(0);
				}
				double alpha = (speed_kph - this.speed_kph.get(index-1)) / (this.speed_kph.get(index) - this.speed_kph.get(index-1));		
				return alpha * this.emission_gpkm.get(index) + (1-alpha) * this.emission_gpkm.get(index-1);
			}
		}
	}
}
