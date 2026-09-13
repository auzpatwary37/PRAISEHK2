package dynamicTransitRouter.transfer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dynamicTransitRouter.DynamicRoutingModule;
import dynamicTransitRouter.RouteHelper;

/**
 * It is a comprehensive transfer discount calculator for all modes
 * @author envf
 *
 */
public class AllPTTransferDiscount implements TransferDiscountCalculator {
	//Data
	HashSet<Id<TransitLine>> KMB_MiniBus_Discount;
	HashMap<Id<TransitLine>, HashSet<Id<TransitLine>>> LRTFeeder_Free;
	HashMap<Id<TransitLine>, Double> MTR_BusMiniBus_Discounts;
	HashMap<Id<TransitRoute>, HashMap<Id<TransitRoute>, BusDiscountData>> BBI_Discount;
	
	List<ArrayList<Id<TransitRoute>>> listOfrouteList;
	
	//XXX Discount Constants
	private static final double MTR_LRT_InterchangeDiscount = 5.2;
	private static final double Kbus_Fare = 4.0;
	private static final double LRTFeeder_Fare = 4.9;
	private static final double KMB_GMBHKI_discount = 1.0;
	private static final double MTR_Minibus_minDiscount = 0.3;
	private static final double tram_Fare = 2.6;
		/* Update this discount value periodically JLo
		 */
	
	//FIXME options
	public static final boolean allowRebate = false;
	@Inject @Named(DynamicRoutingModule.fixedDiscountName) Boolean fixedDiscountEnabled;
	@Inject @Named(DynamicRoutingModule.busBusDiscountName) Double busBusInterchangeDiscount;
	@Inject @Named(DynamicRoutingModule.busTrainDiscountName) Double trainBusInterchangeDiscount;
	
	//@Inject @Named("FixedRateEnabled") Boolean fixedRateDiscountEnabled;
	@Inject @Named(DynamicRoutingModule.fareRateName) Double fixedRateDiscount;
	
		/* for performance please rewrite the code JLo
		 * Best to have the value being pass into this class be BigDecimal already 
		 */
	
	//Useful Strings
	protected static final String LRTFeeder = "LRTFeeder";
	protected static final String KMB_ = "KMB_";
	protected static final String GMB_HKI = "GMB_HKI";
	protected static final String KBus = "KMB_K";
	private static final Logger log = Logger.getLogger(AllPTTransferDiscount.class);
	
	@Inject
	public AllPTTransferDiscount(@Named(DynamicRoutingModule.transferDiscountJsonName) String discountJSONFilePath, 
			TransitSchedule ts, @Named(DynamicRoutingModule.selectedStopsName) List<Id<TransitStopFacility>> selectedStops,
			@Named(DynamicRoutingModule.selectedFareRateName) Double rate, 
			@Named(DynamicRoutingModule.selectedStopsName2) List<Id<TransitStopFacility>> selectedStops2,
			@Named(DynamicRoutingModule.selectedFareRateName2) Double rate2) 
			throws IOException {
		if(discountJSONFilePath != null && discountJSONFilePath != "") {
			loadJSON(discountJSONFilePath);
			if(rate != -1) {
				setFixedStopDiscount(ts, selectedStops);
				if(rate2 != -1) {
					setFixedStopDiscount(ts, selectedStops2);
					updateFixedStopDiscount(Lists.newArrayList(rate, rate2));
				}else {
					updateFixedStopDiscount(Lists.newArrayList(rate));
				}
			}
		}
	}
	
	/**
	 * Real constructor, loads JSON file into memory
	 */
	public AllPTTransferDiscount(@Named(DynamicRoutingModule.transferDiscountJsonName) String discountJSONFilePath) 
			throws IOException {
		loadJSON(discountJSONFilePath);
	}
	
	/**
	 * Empty Constructor, used in DataReaderAndConverter to make one before storing into JSON
	 */
	public AllPTTransferDiscount() {}
	
	public void setTransferDiscountAmount(double busbusInterchange, double trainBusInterchange) {
		this.busBusInterchangeDiscount = busbusInterchange;
		this.trainBusInterchangeDiscount = trainBusInterchange;
		this.fixedDiscountEnabled = true;
	}
	
//	public void setFixedRateDiscount(double fixedRateDiscount) {
//		this.fixedRateDiscount = fixedRateDiscount;
//		this.fixedRateDiscountEnabled = true;
//	}
	
	public void updateFixedStopDiscount(List<Double> rates) {
		if(rates.size()!=listOfrouteList.size()) {
			throw new IllegalArgumentException("The length of rate is different from the route lists!");
		}
		for(int i = 0; i<rates.size(); i++) { //Iterate for every set of stops
			double rate = rates.get(i);
			ArrayList<Id<TransitRoute>> routeList = listOfrouteList.get(i);
			
			ArrayList<Id<TransitRoute>> secondRouteList = (ArrayList<Id<TransitRoute>>) routeList.clone();
			//Step 2: Insert it to the existing bbi.
			for(Id<TransitRoute> fromRouteId: routeList) {
				HashMap<Id<TransitRoute>, BusDiscountData> fromRouteEntry = null;
				if(BBI_Discount.containsKey(fromRouteId))
					fromRouteEntry = BBI_Discount.get(fromRouteId);
				else {
					fromRouteEntry = new HashMap<>();
					BBI_Discount.put(fromRouteId, fromRouteEntry);
				}
				for(Id<TransitRoute> toRouteId: secondRouteList) {
					BusDiscountData thisData;
					if(fromRouteEntry.containsKey(toRouteId))
						thisData = fromRouteEntry.get(toRouteId);
					else {
						thisData = new BusDiscountData();
						fromRouteEntry.put(toRouteId, thisData);
					}
					thisData.addString("Discount $"+rate);
				}
			}
		}
	}
	
	/**
	 * Setup the fixed stop discounts
	 * @param ts
	 * @param stops 
	 */
	public void setFixedStopDiscount(TransitSchedule ts, List<Id<TransitStopFacility>> stops) {
		//Step 1: Create a route list for the stop.
		ArrayList<Id<TransitRoute>> routeList = new ArrayList<>();
		for(TransitLine tl : ts.getTransitLines().values()) {
			for(TransitRoute tr : tl.getRoutes().values()) {
				if(!tr.getTransportMode().equals(RouteHelper.BusMode)) {
					continue;
				}
				for(TransitRouteStop trs: tr.getStops()) {
					if(stops.contains(trs.getStopFacility().getId())){
						routeList.add(tr.getId());
						break;
					}
				}
			}
		}
		if(this.listOfrouteList == null) {
			this.listOfrouteList = new ArrayList<>();
		}
		this.listOfrouteList.add(routeList);
	}

	@Override
	public double getInterchangeDiscount(Id<TransitLine> fromTransitLineId, Id<TransitLine> toTransitLineId, 
			Id<TransitRoute> fromTransitRouteId, Id<TransitRoute> toTransitRouteId, 
			String fromMode, String toMode, double lastStartTime, double lastEndTime, double thisAboardTime,
			double lastFare, double thisFare) {
		
		if(fixedDiscountEnabled==null) {
			fixedDiscountEnabled = false;
		}
		if(fixedRateDiscount==null) {
			fixedRateDiscount = 1.;
		}
		
		//First trip would not have any interchange discount.
		if(fromTransitLineId==null || toTransitLineId==null) {
			return 0; 
		}
		
		//BBI
		if(eitherDirectionTransferByMode(fromMode, toMode, RouteHelper.BusMode, RouteHelper.BusMode) && withinTime(lastStartTime, thisAboardTime, 120)) {
			if(fixedDiscountEnabled!=null && fixedDiscountEnabled) {
				return busBusInterchangeDiscount * fixedRateDiscount; //The fixed interchange discount for paper
			}
			//Any bus route to another bus route
			if(this.BBI_Discount.containsKey(fromTransitRouteId)) {
				BusDiscountData out = this.BBI_Discount.get(fromTransitRouteId).get(toTransitRouteId);
				if(out!=null)
					return out.genDiscount(lastFare, thisFare) * fixedRateDiscount;
			}
			//LRTFeeder to LRTFeeder
			if(this.LRTFeeder_Free.containsKey(fromTransitLineId)) {
				if(this.LRTFeeder_Free.get(fromTransitLineId).contains(toTransitLineId))
					return LRTFeeder_Fare * fixedRateDiscount;
			}
		}
		//HKI KMB to GMB
		else if(eitherDirectionTransferByOperator(fromTransitLineId, toTransitLineId, KMB_, GMB_HKI) && withinTime(lastStartTime, thisAboardTime, 120)) {
			if(this.KMB_MiniBus_Discount.contains(toTransitLineId) || this.KMB_MiniBus_Discount.contains(fromTransitLineId))
					return KMB_GMBHKI_discount * fixedRateDiscount;
		}
		//From KMB to tram, or vise versa
		else if(eitherDirectionTransferByMode(fromMode, toMode, RouteHelper.BusMode, RouteHelper.TramMode) && withinTime(lastStartTime, thisAboardTime, 120)) {
			if(fromTransitLineId.toString().contains(KMB_) || toTransitLineId.toString().contains(KMB_))
				return DynamicRoutingModule.tramFare * fixedRateDiscount;
		}
		//MTR to MTR BUS
		else if(eitherDirectionTransferByOperator(fromTransitLineId, toTransitLineId, LRTFeeder, "WRL") && withinTime(lastStartTime, thisAboardTime, 120)) {
			return LRTFeeder_Fare * fixedRateDiscount;
		} else if(eitherDirectionTransferByOperator(fromTransitLineId, toTransitLineId, KBus, "ERL") && withinTime(lastStartTime, thisAboardTime, 120)) {
			return Kbus_Fare * fixedRateDiscount;
		}
		//MTR to minibus / bus
		else if(fromMode.equals(RouteHelper.MTRMode) && (toMode.equals(RouteHelper.BusMode) || toMode.equals(RouteHelper.MinibusMode)) 
				&& withinTime(lastEndTime, thisAboardTime, 90) ) {
			if(fixedDiscountEnabled) return trainBusInterchangeDiscount * fixedRateDiscount; //The fixed interchange discount for paper
			
			if(this.MTR_BusMiniBus_Discounts.containsKey(toTransitLineId))
				return this.MTR_BusMiniBus_Discounts.get(toTransitLineId) * fixedRateDiscount;
			else if(toMode.equals(RouteHelper.MinibusMode))
				return MTR_Minibus_minDiscount * fixedRateDiscount;
		} else if(toMode.equals(RouteHelper.MTRMode) && (fromMode.equals(RouteHelper.BusMode) || fromMode.equals(RouteHelper.MinibusMode)) 
				&& withinTime(lastStartTime, thisAboardTime, 90) ) {
			if(fixedDiscountEnabled) return trainBusInterchangeDiscount * fixedRateDiscount; //The fixed interchange discount for paper
			
			if(this.MTR_BusMiniBus_Discounts.containsKey(fromTransitLineId))
				return this.MTR_BusMiniBus_Discounts.get(fromTransitLineId) * fixedRateDiscount;
			else if(fromMode.equals(RouteHelper.MinibusMode))
				return MTR_Minibus_minDiscount * fixedRateDiscount;
		}
		//MTR to LR
		else if(eitherDirectionTransferByMode(fromMode, toMode, RouteHelper.MTRMode, RouteHelper.LRMode) && withinTime(lastEndTime, thisAboardTime, 30)) {
			if(fromMode.equals(RouteHelper.LRMode) && lastFare>MTR_LRT_InterchangeDiscount)
				return 0.0;
			else
				return MTR_LRT_InterchangeDiscount * fixedRateDiscount;	
		}
		//MTR bus to LR
		else if(eitherDirectionTransferByMode(fromMode, toMode, RouteHelper.BusMode, RouteHelper.LRMode) && withinTime(lastStartTime, thisAboardTime, 120)) {
			if(fromTransitLineId.toString().contains(LRTFeeder) || toTransitLineId.toString().contains(LRTFeeder)) {
				return LRTFeeder_Fare * fixedRateDiscount;
			}
		}
		
		return 0.0;
	}

	@Override
	public BigDecimal getExactInterchangeDiscount(Id<TransitLine> fromTransitLineId, Id<TransitLine> toTransitLineId, 
			Id<TransitRoute> fromTransitRouteId, Id<TransitRoute> toTransitRouteId, 
			String fromMode, String toMode, double lastStartTime, double lastEndTime, double thisAboardTime,
			double lastFare, double thisFare) {
		double discount = getInterchangeDiscount(fromTransitLineId, toTransitLineId, fromTransitRouteId,  toTransitRouteId, 
				fromMode, toMode, lastStartTime, lastEndTime, thisAboardTime, lastFare, thisFare);
		return new BigDecimal(discount).setScale(2, RoundingMode.HALF_UP);
	}
	
	/**
	 * Convenience method to check if transfer is between the 2 specified operators/lines
	 */
	private boolean eitherDirectionTransferByOperator(Id<TransitLine> fromTransitLineId, Id<TransitLine> toTransitLineId, 
			String operator1, String operator2) {
		return (fromTransitLineId.toString().contains(operator1) && toTransitLineId.toString().contains(operator2)) || 
				(fromTransitLineId.toString().contains(operator2) && toTransitLineId.toString().contains(operator1));
	}
	
	/**
	 * Convenience method to check if transfer is between the 2 specified modes
	 */
	private boolean eitherDirectionTransferByMode(String fromMode, String toMode, String mode1, String mode2) {
		return (fromMode.equals(mode1) && toMode.equals(mode2)) || (toMode.equals(mode1) && fromMode.equals(mode2));
	}
	
	/**
	 * Convenience method to check if the time delta is within the limit
	 * @param limit : time in minutes
	 */
	private boolean withinTime(double time1, double time2, double limit) {
		return (time2 - time1)<=limit*60;
	}
	
	/**
	 * Called automatically from constructor to load stored discounts on JSON
	 */
	private void loadJSON(String jsonFile) throws IOException {
		log.info("Loading Transfer Discount Calaulator from JSON file");
		//reader bit
		BufferedReader readerFareCal = new BufferedReader(new FileReader(jsonFile));
		Gson gson = new Gson();
		dummyDiscountContainer out = gson.fromJson(readerFareCal, dummyDiscountContainer.class);
		readerFareCal.close();
		//init the variables
		this.KMB_MiniBus_Discount = new HashSet<Id<TransitLine>>();
		this.LRTFeeder_Free = new HashMap<Id<TransitLine>, HashSet<Id<TransitLine>>>();
		this.MTR_BusMiniBus_Discounts = new HashMap<Id<TransitLine>, Double>();
		this.BBI_Discount = new HashMap<Id<TransitRoute>, HashMap<Id<TransitRoute>, BusDiscountData>>();
		//convert the data
		for(String TS: out.dummy_KMB_MiniBus_Discount)
			this.KMB_MiniBus_Discount.add(Id.create(TS, TransitLine.class));
		for(Entry<String, HashSet<String>> Tentry: out.dummy_LRTFeeder_Free.entrySet()) {
			HashSet<Id<TransitLine>> thisEntry = new HashSet<Id<TransitLine>>();
			for(String TS: Tentry.getValue())
				thisEntry.add(Id.create(TS, TransitLine.class));
			this.LRTFeeder_Free.put(Id.create(Tentry.getKey(), TransitLine.class), thisEntry);
		}
		for(Entry<String, Double> Tentry: out.dummy_MTR_BusMiniBus_Discounts.entrySet())
			this.MTR_BusMiniBus_Discounts.put(Id.create(Tentry.getKey(), TransitLine.class), Tentry.getValue());
		for(Entry<String, HashMap<String, BusDiscountData>> Tentry: out.dummy_BBI_Discount.entrySet()) {
			HashMap<Id<TransitRoute>, BusDiscountData> thisEntry = new HashMap<Id<TransitRoute>, BusDiscountData>();
			for(Entry<String, BusDiscountData> T2Entry: Tentry.getValue().entrySet())
				thisEntry.put(Id.create(T2Entry.getKey(), TransitRoute.class), T2Entry.getValue());
			this.BBI_Discount.put(Id.create(Tentry.getKey(), TransitRoute.class), thisEntry);
		}
	}
	
	/**
	 * Prints all the discount data to specified JSON file<br>
	 * through a dummy container, as GSON can't handle Id<?> 
	 */
	public void printJSON(String jsonFile) throws IOException {
		log.info("Printing Transit Discount Calculator to JSON");
		BufferedWriter writerFareCal = new BufferedWriter(new FileWriter(jsonFile));
		Gson gsonDisCal = new GsonBuilder().setPrettyPrinting().create();
		gsonDisCal.toJson(new dummyDiscountContainer(this), dummyDiscountContainer.class, writerFareCal);
		writerFareCal.flush();
		writerFareCal.close();
	}
	
	/**
	 * GSON can't parse Id<?> so a dummy container is required
	 */
	private class dummyDiscountContainer{
		//Data
		HashSet<String> dummy_KMB_MiniBus_Discount;
		HashMap<String, HashSet<String>> dummy_LRTFeeder_Free;
		HashMap<String, Double> dummy_MTR_BusMiniBus_Discounts;
		HashMap<String, HashMap<String, BusDiscountData>> dummy_BBI_Discount;
		
		dummyDiscountContainer(AllPTTransferDiscount AllPTTransferDiscount) {
			this.dummy_KMB_MiniBus_Discount = new HashSet<String>();
			this.dummy_LRTFeeder_Free = new HashMap<String, HashSet<String>>();
			this.dummy_MTR_BusMiniBus_Discounts = new HashMap<String, Double>();
			this.dummy_BBI_Discount = new HashMap<String, HashMap<String, BusDiscountData>>();
			
			for(Id<TransitLine> TTL: AllPTTransferDiscount.KMB_MiniBus_Discount)
				this.dummy_KMB_MiniBus_Discount.add(TTL.toString());
			for(Entry<Id<TransitLine>, HashSet<Id<TransitLine>>> Tentry: AllPTTransferDiscount.LRTFeeder_Free.entrySet()) {
				HashSet<String> thisEntry = new HashSet<String>();
				for(Id<TransitLine> TTL: Tentry.getValue())
					thisEntry.add(TTL.toString());
				this.dummy_LRTFeeder_Free.put(Tentry.getKey().toString(), thisEntry);
			}
			for(Entry<Id<TransitLine>, Double> Tentry: AllPTTransferDiscount.MTR_BusMiniBus_Discounts.entrySet())
				this.dummy_MTR_BusMiniBus_Discounts.put(Tentry.getKey().toString(), Tentry.getValue());
			for(Entry<Id<TransitRoute>, HashMap<Id<TransitRoute>, BusDiscountData>> Tentry: AllPTTransferDiscount.BBI_Discount.entrySet()) {
				HashMap<String, BusDiscountData> thisEntry = new HashMap<String, BusDiscountData>();
				for(Entry<Id<TransitRoute>, BusDiscountData> T2Entry: Tentry.getValue().entrySet())
					thisEntry.put(T2Entry.getKey().toString(), T2Entry.getValue());
				this.dummy_BBI_Discount.put(Tentry.getKey().toString(), thisEntry);
			}
		}
	}

}

/**
 * Stores the different discount info
 * 
 * @author JLo
 *
 */
class BusDiscountData {
	private boolean isDiscount;
	private boolean isPay;
	private boolean isFullFare;
	private boolean isRebate;
	private BigDecimal amount;
	
	public BusDiscountData() {
		this.reset();
	}
	
	/**
	 * Reads string of the format "[discount type] $[value]"<br>
	 * and stores the higher discount value if existed already<br>
	 * or overwrite if the discount types are different
	 */
	public void addString(String fareString) {
		if(fareString.contains("Discount")) {
			this.addDiscount(new BigDecimal(fareString.substring(fareString.indexOf('$')+1)));
		} else if(fareString.contains("Full Trip")) {
			this.addFullFare(new BigDecimal(fareString.substring(fareString.indexOf('$')+1)));
		} else if(fareString.contains("Pay")) {
			this.addPay(new BigDecimal(fareString.substring(fareString.indexOf('$')+1)));
		} else if(fareString.contains("Free")) {
			this.addPay(BigDecimal.ZERO);
		} else if(fareString.contains("Rebate")) {
			this.addRebate(new BigDecimal(fareString.substring(fareString.indexOf('$')+1)));
		} else
			throw new IllegalArgumentException("An unexpected value read from discount file: "+fareString);
	}
	
	private void addDiscount(BigDecimal discount) {
		if(discount.compareTo(new BigDecimal(0.0))>=0) {
			if(this.isFullFare || this.isPay || this.isRebate)
				this.reset();
			else if(this.isDiscount)
				if(this.amount.compareTo(discount)>=0)
					return;
			
			this.isDiscount = true;
			this.amount = discount;
		}
	}
	
	private void addPay(BigDecimal pay) {
		if(pay.compareTo(new BigDecimal(0.0))>=0) {
			if(this.isFullFare || this.isDiscount || this.isRebate)
				this.reset();
			else if(this.isPay)
				if(this.amount.compareTo(pay)<=0)
					return;
			
			this.isPay = true;
			this.amount = pay;
		}
	}
	
	private void addFullFare(BigDecimal fullFare) {
		if(fullFare.compareTo(new BigDecimal(0.0))>=0) {
			if(this.isPay || this.isDiscount || this.isRebate)
				this.reset();
			else if(this.isFullFare)
				if(this.amount.compareTo(fullFare)<=0)
					return;
			
			this.isFullFare = true;
			this.amount = fullFare;
		}
	}	
	
	private void addRebate(BigDecimal rebate) {
		if(rebate.compareTo(new BigDecimal(0.0))>=0) {
			if(this.isDiscount || this.isFullFare || this.isPay)
				this.reset();
			else if(this.isRebate)
				if(this.amount.compareTo(rebate)>=0)
					return;
			this.isRebate = true;
			this.amount = rebate;
		}
	}
	
	private void reset() {
		this.isDiscount = false;
		this.isFullFare = false;
		this.isPay = false;
		this.isRebate = false;
	}
	
	/**
	 * Gens the discount amount based on discount mode stored
	 */
	public double genDiscount(double lastFare, double thisFare) {
		if(this.isDiscount)
			return this.amount.doubleValue();
		else if(this.isPay)
			return thisFare - this.amount.doubleValue();
		else if(this.isRebate)	//FIXME for performance if settled on one or the other
			return AllPTTransferDiscount.allowRebate ? thisFare*-1 : thisFare;
		else if(this.isFullFare) {
			double out = (thisFare + lastFare) - this.amount.doubleValue();
			return (out>=0.0 ? out : 0.0);
		} else
			return 0.0;
	}
	
	/**
	 * Just in case method that fully utilise the BigDecimal from the ground up
	 * Gens the discount amount based on discount mode stored
	 */
	public BigDecimal genDiscount(BigDecimal lastFare, BigDecimal thisFare) {
		if(this.isDiscount)
			return this.amount;
		else if(this.isPay)
			return thisFare.subtract(this.amount);
		else if(this.isRebate)	//FIXME for performance if settled on one or the other
			return AllPTTransferDiscount.allowRebate ? thisFare.negate() : thisFare;
		else if(this.isFullFare) {
			BigDecimal out = thisFare.add(lastFare).subtract(this.amount);
			BigDecimal.valueOf(0);
			return (out.signum()>=0 ? out : BigDecimal.ZERO);
		} else
			return BigDecimal.ZERO;
	}
	
}
