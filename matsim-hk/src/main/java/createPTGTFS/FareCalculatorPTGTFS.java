/**
 * 
 */
package createPTGTFS;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import withinDay.EquivalentStopForFare;

/**
 * @author JLo
 *
 */
public class FareCalculatorPTGTFS implements FareCalculator {
	private final HashMap<Id<TransitRoute>, FareCalGTFSDataRoute> compressedMap;
	private static final Logger log = Logger.getLogger(FareCalculatorPTGTFS.class);
	private double fareFactor = 1.0;
	
	public FareCalculatorPTGTFS(DataContainerPT PTDataContainer) {
		this.compressedMap = new HashMap<Id<TransitRoute>, FareCalGTFSDataRoute>();
		this.initCompressedMap(PTDataContainer);
	}
	
	public FareCalculatorPTGTFS(FareCalJSONStore fromJSON, EquivalentStopForFare esff) {
		this.compressedMap = new HashMap<Id<TransitRoute>, FareCalGTFSDataRoute>();
		for(Entry<String, FareCalJSONStoreRoute> entry: fromJSON.dummyCompressedMap.entrySet()) {
			Id<TransitRoute> routeId = Id.create(entry.getKey(), TransitRoute.class);
			this.compressedMap.put(routeId, new FareCalGTFSDataRoute(entry.getValue(), 
					esff == null? null: esff.getFacilityMapping(routeId)));
		}
	}
	
	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId, Id<TransitStopFacility> toStopId) {
		return this.compressedMap.get(routeId).getMinFare(fromStopId, toStopId) * this.fareFactor;
	}

	@Override
	public double getMinFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId) {
		return this.compressedMap.get(routeId).getMinFare(fromStopId) * this.fareFactor;
	}

	@Override
	public List<Double> getFares(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId, Id<TransitStopFacility> toStopId) {
		return this.compressedMap.get(routeId).getFares(fromStopId, toStopId);
	}

	@Override
	public double getFare(Id<TransitRoute> routeId, Id<TransitLine> lineId, Id<TransitStopFacility> fromStopId, int fromOccurence, Id<TransitStopFacility> toStopId, int toOccurence) {
		return this.compressedMap.get(routeId).getFare(fromStopId, toStopId, fromOccurence, toOccurence) * this.fareFactor;
	}
	
	private void initCompressedMap(DataContainerPT PTDataContainer) {
		//Iterates DataRoutePT / TransitLine
		for(DataRoutePT tempRoute: PTDataContainer.getPTDataRouteMap().values()) {
			//makes the stop occ map first
			tempRoute.optimiseStopsMapForFareCal();
			
			//Iterates smallRoute / TransitRoute
			for(Entry<String, DataFarePT> fareMapEntry: tempRoute.getFareMap().entrySet()) {
				//checks if this is actually built
				if(!tempRoute.getStopsMap().containsKey(fareMapEntry.getKey()))
					continue;
				
				//checks if route has only 1 fare
				Tuple<Boolean,Double> routeHasSingleFare = fareMapEntry.getValue().hasSingleFare();
				if(routeHasSingleFare.getFirst()) {
					//dump it in
					this.compressedMap.put(tempRoute.getTRId(fareMapEntry.getKey()), new FareCalGTFSDataRoute(routeHasSingleFare.getSecond()));
					continue;
				}
				
				//construct the fareSeqMap
				HashMap<Integer, HashMap<Integer, Double>> fareSeqMap = new HashMap<Integer, HashMap<Integer, Double>>();
				for(FareDataGTFS TdataFare: fareMapEntry.getValue().getFareMap().values()) {
					int fromSeq = TdataFare.getFromStopSeq();
					int toSeq = TdataFare.getToStopSeq();
					if(!fareSeqMap.containsKey(fromSeq))
						fareSeqMap.put(fromSeq, new HashMap<Integer, Double>());
					fareSeqMap.get(fromSeq).put(toSeq, TdataFare.getFare());
				}
				//construct the stopsMap
				HashMap<Id<TransitStopFacility>,FareCalGTFSDataStop> stopsMap = new HashMap<Id<TransitStopFacility>,FareCalGTFSDataStop>();
				ConcurrentHashMap<String, Tuple<Double, ArrayList<Integer>>> stopsOccMap = tempRoute.getStopsOccMap(fareMapEntry.getKey());
				for(Entry<String, Tuple<Double, ArrayList<Integer>>> stopsOccMapEntry: stopsOccMap.entrySet()) {
					FareCalGTFSDataStop tempStop = new FareCalGTFSDataStop(stopsOccMapEntry.getValue().getFirst(), stopsOccMapEntry.getValue().getSecond());
					Tuple<Boolean,Double> stopHasSingleFare = fareMapEntry.getValue().stopHasSingleFare(stopsOccMapEntry.getKey());
					if(stopHasSingleFare.getFirst())		//FIXME bug: checks non-created stops too
						tempStop.setSingleFare(stopHasSingleFare.getSecond());
					Id<TransitStopFacility> tsfId = PTDataContainer.getPTDataStopMap().get(stopsOccMapEntry.getKey()).getTransitStopFacility().getId();
					stopsMap.put(tsfId, tempStop);
					//XXX Lazy implementation -> don't want to make new class for bus only
					if(tsfId.toString().contains("bus")) {
						DataStopBus TbusStop = ((DataStopBus) PTDataContainer.getPTDataStopMap().get(stopsOccMapEntry.getKey()));
						if(TbusStop.isTerminus())
							stopsMap.put(TbusStop.getTerminusTransitStopFacility().getId(), tempStop);
					}
				}
				//discard fareSeqMap if all the stops have only 1 fare, to save on memory loading
				boolean discardFareSeqMap = true;
				for(FareCalGTFSDataStop TStop: stopsMap.values())
					if(!TStop.isSingleFareS)	//if any stop is not single fare then have to keep the map
						discardFareSeqMap = false;
				
				//dump it in
				this.compressedMap.put(tempRoute.getTRId(fareMapEntry.getKey()), new FareCalGTFSDataRoute(stopsMap, (discardFareSeqMap ? null : fareSeqMap) ));
			}
		}
	}
	
	private class FareCalGTFSDataRoute {
		public boolean isSingleFareR;
		private double singleFareR;
		private HashMap<Id<TransitStopFacility>,FareCalGTFSDataStop> stopsMap;
		private HashMap<Integer,HashMap<Integer,Double>> fareSeqMap;
		
		public FareCalGTFSDataRoute (HashMap<Id<TransitStopFacility>,FareCalGTFSDataStop> stopsMap, HashMap<Integer,HashMap<Integer,Double>> fareSeqMap) {
			this.stopsMap = stopsMap;
			this.fareSeqMap = fareSeqMap;
			this.isSingleFareR = false;
		}
		
		public FareCalGTFSDataRoute (double singleFare) {
			this.isSingleFareR = true;
			this.singleFareR = singleFare;
		}
		
		public FareCalGTFSDataRoute (FareCalJSONStoreRoute dummy, 
				Map<Id<TransitStopFacility>, List<Id<TransitStopFacility>>> facilityMapping) {
			this.isSingleFareR = dummy.DummyIsSingleFareR;
			this.singleFareR = dummy.DummySingleFareR;
			this.fareSeqMap = dummy.DummyFareSeqMap;
			if(dummy.DummyStopsMap != null) {
				this.stopsMap = new HashMap<Id<TransitStopFacility>,FareCalGTFSDataStop>();
				for(Entry<String,FareCalGTFSDataStop> entry: dummy.DummyStopsMap.entrySet()) {
					Id<TransitStopFacility> tsfId = Id.create(entry.getKey(), TransitStopFacility.class);
					this.stopsMap.put(tsfId, entry.getValue());
					if(facilityMapping != null && facilityMapping.containsKey(tsfId)) {
						for(Id<TransitStopFacility> replacementTsfId: facilityMapping.get(tsfId)) {
							this.stopsMap.put(replacementTsfId, entry.getValue());
						}
					}
				}
			}
		}
		
		public double getMinFare(Id<TransitStopFacility> fromStop) {
			if(this.isSingleFareR)
				return this.singleFareR;
			else {
				if (!this.stopsMap.containsKey(fromStop)){
					System.out.print("");
				}
				return this.stopsMap.get(fromStop).getMinFare();
			}
		}
		
		public double getMinFare(Id<TransitStopFacility> fromStop, Id<TransitStopFacility> toStop) {
			if(this.isSingleFareR)
				return this.singleFareR;
			else {
				FareCalGTFSDataStop fromStopData = this.stopsMap.get(fromStop);
				if(fromStopData.isSingleFareS)
					return fromStopData.getSingleFare();
				else if(fromStop.equals(toStop))
					return fromStopData.getMinFare();
				else {
					double min = Double.MAX_VALUE;
					for(Integer fromSeq: fromStopData.getSeqList())
						for(Integer toSeq: this.stopsMap.get(toStop).getSeqList()) 
							if(fromSeq < toSeq) {
								double thisFare = this.fareSeqMap.get(fromSeq).get(toSeq);
								if(thisFare < min)
									min = thisFare;
							}
					return (min == Double.MAX_VALUE) ?
							fromStopData.getMinFare() :
							min;
				}
			}
		}
		
		public List<Double> getFares(Id<TransitStopFacility> fromStop, Id<TransitStopFacility> toStop) {
			if(this.isSingleFareR)
				return Arrays.asList(this.singleFareR);
			else {
				FareCalGTFSDataStop fromStopData = this.stopsMap.get(fromStop);
				if(fromStopData.isSingleFareS)
					return Arrays.asList(fromStopData.getSingleFare());
				else if(fromStop.equals(toStop))
					return Arrays.asList(fromStopData.getMinFare());
				else {
					ArrayList<Double> output = new ArrayList<Double>();
					for(Integer fromSeq: fromStopData.getSeqList())
						for(Integer toSeq: this.stopsMap.get(toStop).getSeqList())
							if(fromSeq < toSeq)
								output.add(this.fareSeqMap.get(fromSeq).get(toSeq));
					if(output.isEmpty())
						output.add(fromStopData.getMinFare());
					return output;
				}
			}
		}
		
		public double getFare(Id<TransitStopFacility> fromStop, Id<TransitStopFacility> toStop, int fromOcc, int toOcc) {
			if(this.isSingleFareR)
				return this.singleFareR;
			else {
				FareCalGTFSDataStop fromStopData = this.stopsMap.get(fromStop);
				if(fromStopData.isSingleFareS)
					return fromStopData.getSingleFare();
				else if(fromStop.equals(toStop) && fromOcc == toOcc)
					return fromStopData.getMinFare();
				else
					return this.fareSeqMap.get(fromStopData.getSeq(fromOcc)).get(this.stopsMap.get(toStop).getSeq(toOcc));
			}
		}
	}
	
	private class FareCalGTFSDataStop {
		public boolean isSingleFareS;	//disregard variable safety to speed up access time
		private double singleFareS;
		private double minFare;
		private ArrayList<Integer> seqList;
		
		public FareCalGTFSDataStop(double minFare, ArrayList<Integer> sequenceList) {
			this.minFare = minFare;
			this.seqList = sequenceList;
			this.isSingleFareS = false;
		}
		
		public void setSingleFare(double fare) {
			this.isSingleFareS = true;
			this.singleFareS = fare;
		}
		
		public double getMinFare() {
			return this.minFare;
		}
		
		public double getSingleFare() {
			return this.singleFareS;
		}
		
		public Integer getSeq(int i) {
			return this.seqList.get(i);
		}
		
		public ArrayList<Integer> getSeqList() {
			return this.seqList;
		}
		
	}
	
	private class FareCalJSONStore {
		public HashMap<String, FareCalJSONStoreRoute> dummyCompressedMap;
		
		public FareCalJSONStore(HashMap<Id<TransitRoute>, FareCalGTFSDataRoute> CompressedMap) {
			dummyCompressedMap = new HashMap<String, FareCalJSONStoreRoute>();
			for(Entry<Id<TransitRoute>, FareCalGTFSDataRoute> CompressedMapEntry: CompressedMap.entrySet())
				dummyCompressedMap.put(CompressedMapEntry.getKey().toString(), new FareCalJSONStoreRoute(CompressedMapEntry.getValue()));
		}
	}
	
	private class FareCalJSONStoreRoute {
		public boolean DummyIsSingleFareR;
		public double DummySingleFareR;
		public HashMap<String,FareCalGTFSDataStop> DummyStopsMap;
		public HashMap<Integer,HashMap<Integer,Double>> DummyFareSeqMap;
		
		public FareCalJSONStoreRoute(FareCalGTFSDataRoute routething) {
			this.DummyIsSingleFareR = routething.isSingleFareR;
			this.DummySingleFareR = routething.singleFareR;
			this.DummyFareSeqMap = routething.fareSeqMap;
			
			if(routething.stopsMap != null) {
				this.DummyStopsMap = new HashMap<String,FareCalGTFSDataStop>();
				for(Entry<Id<TransitStopFacility>, FareCalGTFSDataStop> stopsMapEntry: routething.stopsMap.entrySet())
					this.DummyStopsMap.put(stopsMapEntry.getKey().toString(), stopsMapEntry.getValue());
			}
		}
	}
	
	
	/**
	 * Loads a Fare Calculator JSON file<br>
	 * @param JSONFilePath
	 * @return FareCalculatorPTGTFS
	 * @throws IOException
	 */
	public static FareCalculatorPTGTFS loadFareCalculatorPTGTFS(String JSONFilePath) throws IOException {
		log.info("Loading FareCal from JSON file");
		BufferedReader readerFareCal = new BufferedReader(new FileReader(JSONFilePath));
		Gson gsonFareCalGTFS = new Gson();
		FareCalculatorPTGTFS out = new FareCalculatorPTGTFS(gsonFareCalGTFS.fromJson(readerFareCal, FareCalJSONStore.class), null);
		readerFareCal.close();
		return out;
	}
	
	/**
	 * Loads a Fare Calculator JSON file<br>
	 * @param JSONFilePath
	 * @return FareCalculatorPTGTFS
	 * @throws IOException
	 */
	public static FareCalculatorPTGTFS loadFareCalculatorPTGTFS(String JSONFilePath, 
			EquivalentStopForFare esff) throws IOException {
		log.info("Loading FareCal from JSON file");
		BufferedReader readerFareCal = new BufferedReader(new FileReader(JSONFilePath));
		Gson gsonFareCalGTFS = new Gson();
		FareCalculatorPTGTFS out = new FareCalculatorPTGTFS(gsonFareCalGTFS.fromJson(readerFareCal, FareCalJSONStore.class), esff);
		readerFareCal.close();
		return out;
	}
	
	/**
	 * Prints a FareCalculator JSON file<br>
	 * @param filePath
	 * @throws IOException
	 */
	public void printJSON(String filePath) throws IOException {
		log.info("Printing FareCal to JSON");
		BufferedWriter writerFareCal = new BufferedWriter(new FileWriter(filePath));
		Gson gsonFareCalGTFS = new GsonBuilder().setPrettyPrinting().create();
		gsonFareCalGTFS.toJson(new FareCalJSONStore(this.compressedMap), FareCalJSONStore.class, writerFareCal);
		writerFareCal.flush();
		writerFareCal.close();
	}
	
	/**
	 * Output Format <i>"route_operator"</i>_<i>"route_shortname"</i>
	 * @param TLId
	 * @return
	 */
	@SuppressWarnings("unused")
	@Deprecated
	private static String extractTransitLineId(Id<TransitLine> TLId) {
		return TLId.toString();
	}
	
	/**
	 * Output Format XXXX_X (routeId;direction)
	 * @param TRId
	 * @return
	 */
	@SuppressWarnings("unused")
	@Deprecated
	private static String extractTransitRouteId(Id<TransitRoute> TRId) {
		return TRId.toString().substring(0,6);								//XXX 	alternative to improve performance albeit risky to updates
//		return TRId.toString().subString(0, TRId.toString().indexOf(" "));	//XXX	more resilient but more cpu
	}
	
	/**
	 * Output Format stop_id XXXXXX (not sure how many digits)
	 * @param TSFId
	 * @return
	 */
	@SuppressWarnings("unused")
	@Deprecated
	private static String extractTransitStopFacilityId(Id<TransitStopFacility> TSFId) {
		return TSFId.toString().substring(TSFId.toString().indexOf('_')+1); 
	}
	
	/**
	 * Using created TransitSchedule to check each from-to fare inthe route
	 * @param TS
	 * @return
	 */
	public boolean checkValidity(TransitSchedule TS) {
		boolean flag = true;
		log.info("Checking Validity");
		
		for(TransitLine TL: TS.getTransitLines().values()) {
			if(!this.compressedMap.containsKey(TL.getId()))
				continue;
			
			Id<TransitLine> lineId = TL.getId();
			for(Entry<Id<TransitRoute>, TransitRoute> TREntry: TL.getRoutes().entrySet()) {
				Id<TransitRoute> routeId = TREntry.getKey();
				
				//loop from stop
				HashMap<Id<TransitStopFacility>, Integer> fromStopOccMap = new HashMap<Id<TransitStopFacility>, Integer>();
				int seq = -1;
				for(TransitRouteStop TRS1: TREntry.getValue().getStops()) {
					TransitStopFacility TSF1 = TRS1.getStopFacility();
					Id<TransitStopFacility> TSF1Id = TSF1.getId();
					seq++;
					//occ count
					int count1 = 0;
					if(fromStopOccMap.containsKey(TSF1Id)) {
						count1 = fromStopOccMap.get(TSF1Id).intValue()+1;
						fromStopOccMap.replace(TSF1Id, count1);
					}
					fromStopOccMap.put(TSF1Id, count1);
					
					//loop to stop
					HashMap<Id<TransitStopFacility>, Integer> toStopOccMap = new HashMap<Id<TransitStopFacility>, Integer>();
					int seq2 = -1;
					double last = Double.MAX_VALUE;
					for(TransitRouteStop TRS2: TREntry.getValue().getStops()) {
						TransitStopFacility TSF2 = TRS2.getStopFacility();
						Id<TransitStopFacility> TSF2Id = TSF2.getId();
						seq2++;
						//occ count
						int count2 = 0;
						if(toStopOccMap.containsKey(TSF2Id)) {
							count2 = toStopOccMap.get(TSF2Id).intValue()+1;
							toStopOccMap.replace(TSF2Id, count2);
						}
						toStopOccMap.put(TSF2Id, count2);
						//check if reached first stop yet
						if(seq2>seq) {
							this.getFares(routeId, lineId, TSF1.getId(), TSF2.getId());
							this.getMinFare(routeId, lineId, TSF1.getId());
							this.getMinFare(routeId, lineId, TSF1.getId(), TSF2.getId());
							double thisfare = this.getFare(TREntry.getKey(), lineId, TSF1.getId(), count1, TSF2.getId(), count2);
							if(thisfare<last && last!=Double.MAX_VALUE) {
								System.out.println("Invalid fare; TransitLine:"+TL.getId().toString()+" TransitRoute:"+TREntry.getKey().toString()+" from stop:"+TSF1.getId().toString()+" to stop:"+TSF2.getId().toString());
								flag = false;
							}
							last = thisfare;
						}
					}
				}
			}
		}
		log.info("Checking Completed");
		return flag;
	}

	@Override
	public void setFareFactor(double fareFactor) {
		this.fareFactor = fareFactor;
	}

}
