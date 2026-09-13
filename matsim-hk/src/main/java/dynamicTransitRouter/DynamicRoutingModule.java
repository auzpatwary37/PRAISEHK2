package dynamicTransitRouter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Id;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.mobsim.qsim.pt.TransitVehicle;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.xml.sax.SAXException;

import com.google.common.collect.Lists;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;

import createPTGTFS.FareCalculatorPTGTFS;
import dynamicTransitRouter.costs.PTRecordHandler;
import dynamicTransitRouter.costs.StopStopTime;
import dynamicTransitRouter.costs.TransferWalkingTime;
import dynamicTransitRouter.costs.VehicleOccupancy;
import dynamicTransitRouter.costs.WaitingTime;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.LRFareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import dynamicTransitRouter.transfer.AllPTTransferDiscount;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import transitFareAndHandler.ComfortHandler;
import transitFareAndHandler.TransitFareControlerListener;
import transitFareAndHandler.TransitFareHandler;
import withinDay.EquivalentStopForFare;

public class DynamicRoutingModule extends AbstractModule {

	private FareCalculator busCalculator;
	private final String MTRFareFilePath;
	private final String firstClassFarePath;
	private final String LRFareFilePath;
	private final String transferDiscountJson;
	private final String busFareJsonPath;
	private final String ferryFareJsonPath;
	private final String walkingTimePath;
	public static final double tramFare = 2.6;
	
	private boolean fixedValueTransferEnabled = false;
	private double busBusDiscount = 0.0;
	private double busTrainDiscount = 0.0;
	
	private boolean fareRateEnabled = false;
	private double fareRate = 1.;
	
	private List<Id<TransitStopFacility>> selectedStops = null;
	private List<Id<TransitStopFacility>> selectedStops2 = null;
	private Double selectedFareRate = null;
	private Double selectedFareRate2 = null;
	private EquivalentStopForFare equivalenceStopMap = null;
	
	private Map<String, FareCalculator> preloadedFareCalculatorMap;
	
	public static final String transferDiscountJsonName = "TransferDiscountJson";
	public static final String fixedDiscountName = "FixedDiscount";
	public static final String busBusDiscountName = "BBI";
	public static final String busTrainDiscountName = "TBI";
	
	public static final String fareRateEnabledName = "FareRateEnabled";
	public static final String fareRateName = "FareRate";
	
	public static final String selectedFareRateName = "SelectedFareRate";
	public static final String selectedStopsName = "SelectedStops";
	public static final String selectedFareRateName2 = "SelectedFareRate2";
	public static final String selectedStopsName2 = "SelectedStops2";
	
	/**
	 * An alternate constructor that do the bus fare loading itself.
	 * 
	 * @param busFareCalculator
	 * @param MTRFareFilePath The MTR fare, downloaded from data.gov.hk
	 * @param GMBDiscountFilePath The list of GMB route that have interchange discount with KMB
	 * @param LRFareFilePath The light rail fare, downloaded from data.gov.hk
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 */
	public DynamicRoutingModule(TransitSchedule ts, String minibusFareFilePath, String MTRFareFilePath, String firstClassFarePath,
			String transferDiscountJson, String LRFareFilePath, String busFareJsonPath, String ferryFareJsonPath) 
					throws SAXException, IOException, ParserConfigurationException {
		
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(ts);
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		saxParser.parse(minibusFareFilePath, busFareGetter);
		
		this.busCalculator = busFareGetter.get();
		this.MTRFareFilePath = MTRFareFilePath;
		this.firstClassFarePath = firstClassFarePath;
		this.transferDiscountJson = transferDiscountJson;
		this.LRFareFilePath = LRFareFilePath;
		this.busFareJsonPath = busFareJsonPath;
		this.ferryFareJsonPath = ferryFareJsonPath;
		this.walkingTimePath = "input/transfer_time.csv";
		this.preloadedFareCalculatorMap = null;
		initialize();
	}
	
	public Map<String, FareCalculator> getPreloadedFareCalculatorMap() {
		return preloadedFareCalculatorMap;
	}

	/**
	 * It is a simplier loader given a fare calculator already.
	 * Assume there is no walking distance and also transfer discounts
	 * @param preloadedFareCalculatorMap
	 */
	public DynamicRoutingModule(Map<String, FareCalculator> preloadedFareCalculatorMap) {
		this.preloadedFareCalculatorMap = preloadedFareCalculatorMap;
		this.MTRFareFilePath = "";
		this.firstClassFarePath = "";
		this.transferDiscountJson = "";
		this.LRFareFilePath = "";
		this.busFareJsonPath = "";
		this.ferryFareJsonPath = "";
		this.walkingTimePath = "";
		initialize();
		skipTransferDiscount(); 
	}
	
	/**
	 * 
	 * @param minibusFareCalculator
	 * @param MTRFareFilePath The MTR fare, downloaded from data.gov.hk
	 * @param GMBDiscountFilePath The list of GMB route that have interchange discount with KMB
	 * @param LRFareFilePath The light rail fare, downloaded from data.gov.hk
	 */
	public DynamicRoutingModule(FareCalculator minibusFareCalculator, String MTRFareFilePath, String firstClassFarePath, 
			String transferDiscountJson, String LRFareFilePath, String busFareJsonPath, String ferryFareJsonPath) {
		this.busCalculator = minibusFareCalculator;
		this.MTRFareFilePath = MTRFareFilePath;
		this.firstClassFarePath = firstClassFarePath;
		this.transferDiscountJson = transferDiscountJson;
		this.LRFareFilePath = LRFareFilePath;
		this.busFareJsonPath = busFareJsonPath;
		this.ferryFareJsonPath = ferryFareJsonPath;
		this.walkingTimePath = "input/transfer_time.csv";
		this.preloadedFareCalculatorMap = null;
		initialize();
	}
	
	protected void initialize() {
		this.selectedStops = Lists.newArrayList();
		this.selectedStops2 = Lists.newArrayList();
		this.selectedFareRate = 0.;
		this.selectedFareRate2 = 0.;
	}
	
	/**
	 * Call this function to enable the fix valued transfer discount.
	 * @param busBusInterchangeDiscount
	 * @param trainBusInterchangeDiscount
	 */
	public void enableFixValuedTransfer(double busBusInterchangeDiscount, double trainBusInterchangeDiscount) {
		if(fareRateEnabled) {
			throw new IllegalArgumentException("The fixed discount rate is already enabled!");
		}
		this.busBusDiscount = busBusInterchangeDiscount;
		this.busTrainDiscount = trainBusInterchangeDiscount;
		this.fixedValueTransferEnabled = true;
	}
	
	public void skipTransferDiscount() {
		this.enableFixValuedTransfer(0, 0);
	}
	
	public void enableFareRate(double fareRate) {
		if(fixedValueTransferEnabled) {
			throw new IllegalArgumentException("The fixed value transfer discount is already enabled!");
		}
		this.fareRateEnabled = true;
		this.fareRate = fareRate;
	}
	
	public void enableSpecialDiscount(List<Id<TransitStopFacility>> selectedStops, double selectedFareRate) {
		this.selectedStops = selectedStops;
		this.selectedFareRate = selectedFareRate;
	}
	
	public void enableTwoSpecialDiscount(List<Id<TransitStopFacility>> selectedStops1, 
			List<Id<TransitStopFacility>> selectedStops2, double selectedFareRate1, double selectedFareRate2) {
		this.selectedStops = selectedStops1;
		this.selectedStops2 = selectedStops2;
		this.selectedFareRate = selectedFareRate1;
		this.selectedFareRate2 = selectedFareRate2;
	}
	
	public void setBusequivalenceStopMap(EquivalentStopForFare equivalenceStopMap2) {
		this.equivalenceStopMap = equivalenceStopMap2;
	}
	
	/**
	 * It is a function to setup the fare calculators as well as the transit fare listeners and recorders
	 */
	public void setupFareCalculator() {
		MapBinder<String, FareCalculator> mapbinder = MapBinder.newMapBinder(binder(), String.class,
				FareCalculator.class);
		
		if(preloadedFareCalculatorMap == null){
			// Bind the MTR fare calculator
			mapbinder.addBinding("train").to(MTRFareCalculator.class).in(Scopes.SINGLETON);
			bind(String.class).annotatedWith(Names.named("trainFareInput")).toInstance(MTRFareFilePath); // File path
			
			try {
				mapbinder.addBinding("trainfirstClass").toConstructor(MTRFareCalculator.class.getConstructor(String.class, TransitSchedule.class, TransitSchedule.class));
			} catch (NoSuchMethodException | SecurityException e1) {
				throw new RuntimeException("");
			}
			bind(String.class).annotatedWith(Names.named("firstClassInput")).toInstance(firstClassFarePath);
	
			try {
				//Bus fare
				FareCalculator busFareCal = FareCalculatorPTGTFS.loadFareCalculatorPTGTFS(busFareJsonPath);
				busFareCal.setFareFactor(this.fareRate);
				mapbinder.addBinding("bus").toInstance(busFareCal);
				
				//Ferry fare
				FareCalculator ferryFareCal = FareCalculatorPTGTFS.loadFareCalculatorPTGTFS(ferryFareJsonPath);
				ferryFareCal.setFareFactor(this.fareRate);
				mapbinder.addBinding("ferry").toInstance(ferryFareCal);
			} catch (IOException e) {
				throw new RuntimeException("");
			}
			
			this.busCalculator.setFareFactor(this.fareRate);
			mapbinder.addBinding("minibus").toInstance(this.busCalculator);
	
			// The tram and ship fare calculator
			FareCalculator tramFareCal = new UniformFareCalculator(tramFare);
			tramFareCal.setFareFactor(this.fareRate);
			mapbinder.addBinding("tram").toInstance(tramFareCal);
			
			//The LR fare calculator
			mapbinder.addBinding("LR").to(LRFareCalculator.class).in(Scopes.SINGLETON);
			bind(String.class).annotatedWith(Names.named("LRFareInput")).toInstance(LRFareFilePath);
		}else {
			preloadedFareCalculatorMap.forEach((name, fareCal)->{
				mapbinder.addBinding(name).toInstance(fareCal);
				fareCal.setFareFactor(this.fareRate);
			});
		}
		bind(TransferWalkingTime.class).in(Singleton.class);;
		bind(String.class).annotatedWith(Names.named("WalkTimeInput")).toInstance(walkingTimePath); //The input of the walking time
		addEventHandlerBinding().to(TransitFareHandler.class).in(Singleton.class); // Add the transit fare handler
		addEventHandlerBinding().to(ComfortHandler.class).in(Singleton.class);
		addEventHandlerBinding().to(PTRecordHandler.class).in(Singleton.class); // Only one PTRecordHandler exists

		addControlerListenerBinding().to(TransitFareControlerListener.class);
		bind(TransitFareHandler.class).in(Singleton.class);
		
		// This one class is useful for all three calculations
		bind(StopStopTime.class).to(PTRecordHandler.class).in(Singleton.class);
		bind(VehicleOccupancy.class).to(PTRecordHandler.class).in(Singleton.class);
		bind(WaitingTime.class).to(PTRecordHandler.class).in(Singleton.class);
		
		bind(String.class).annotatedWith(Names.named(transferDiscountJsonName)).toInstance(transferDiscountJson);
		bind(Boolean.class).annotatedWith(Names.named(fixedDiscountName)).toInstance(fixedValueTransferEnabled);
		bind(Double.class).annotatedWith(Names.named(busBusDiscountName)).toInstance(busBusDiscount);
		bind(Double.class).annotatedWith(Names.named(busTrainDiscountName)).toInstance(busTrainDiscount);
		
		bind(Boolean.class).annotatedWith(Names.named(fareRateEnabledName)).toInstance(fareRateEnabled);
		bind(Double.class).annotatedWith(Names.named(fareRateName)).toInstance(fareRate);
		
		if(selectedFareRate==null) {
			selectedFareRate = -1.;
			selectedStops = Lists.newArrayList();
		}
		if(selectedFareRate2==null) {
			selectedFareRate2 = -1.;
			selectedStops2 = Lists.newArrayList();
		}
		
		bind(new TypeLiteral<List<Id<TransitStopFacility>>>(){}).annotatedWith(Names.named(selectedStopsName)).toInstance(selectedStops);
		bind(Double.class).annotatedWith(Names.named(selectedFareRateName)).toInstance(selectedFareRate);
		
		bind(new TypeLiteral<List<Id<TransitStopFacility>>>(){}).annotatedWith(Names.named(selectedStopsName2)).toInstance(selectedStops2);
		bind(Double.class).annotatedWith(Names.named(selectedFareRateName2)).toInstance(selectedFareRate2);
		
	}
	
	@Override
	public void install() {
		if (getConfig().transit().isUseTransit()) {
			setupFareCalculator();
			// Bind the fare to new dynamic and fare calculator
			bind(TransitRouter.class).to(TransitRouterFareDynamicImpl.class);
			bind(TransferDiscountCalculator.class).to(AllPTTransferDiscount.class).in(Singleton.class);
		}
	}

}
