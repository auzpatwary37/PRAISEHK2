package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.LinkedHashMap;

import org.matsim.core.controler.AbstractModule;

import com.google.inject.name.Names;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;




public class AnaModelCalibrationModule extends AbstractModule{
	private MeasurementsStorage storage;
	private AnalyticalModel sueAssignment;
	private final String fileLoc;
	private boolean generateRoutesAndOD=true;
	private paramContainer currentParam;
	private Measurements outputMeasurements;
	
	public AnaModelCalibrationModule(MeasurementsStorage countData,AnalyticalModel sueAssignment,String FileLoc) {
		this.storage=countData;
		this.sueAssignment=sueAssignment;
		this.fileLoc=FileLoc;
		}	
	public AnaModelCalibrationModule(MeasurementsStorage countData,AnalyticalModel sueAssignment,String FileLoc,LinkedHashMap<String,Double> currentParam,boolean generateRouteAndOD) {
		this.storage=countData;
		this.sueAssignment=sueAssignment;
		this.fileLoc=FileLoc;
		this.generateRoutesAndOD=generateRouteAndOD;
		this.currentParam=new paramContainer(currentParam);
		this.outputMeasurements=this.storage.getCalibrationMeasurements().clone();
		}
	
	public void install() {
		
		LinkCountEventHandler lc=new LinkCountEventHandler(this.outputMeasurements);
		AverageOccupancyEventHandler ao=new AverageOccupancyEventHandler(this.outputMeasurements);
		TravelTimeEventHandler tt=new TravelTimeEventHandler(this.outputMeasurements);
		SmartCardEntryAndExitEventHandler sc=new SmartCardEntryAndExitEventHandler(this.outputMeasurements);
		
		bind(MeasurementsStorage.class).toInstance(this.storage);
		bind(Measurements.class).annotatedWith(Names.named("Output Measurements")).toInstance(this.outputMeasurements);
		bind(AnalyticalModel.class).toInstance(this.sueAssignment);
		bind(String.class).annotatedWith(Names.named("fileLoc")).toInstance(this.fileLoc);
		this.addControlerListenerBinding().to(AnaModelControlerListener.class);
		this.addEventHandlerBinding().toInstance(lc);
		bind(LinkCountEventHandler.class).toInstance(lc);
		this.addEventHandlerBinding().toInstance(ao);
		bind(AverageOccupancyEventHandler.class).toInstance(ao);
		this.addEventHandlerBinding().toInstance(tt);
		bind(TravelTimeEventHandler.class).toInstance(tt);
		this.addEventHandlerBinding().toInstance(sc);
		bind(SmartCardEntryAndExitEventHandler.class).toInstance(sc);
		bind(boolean.class).annotatedWith(Names.named("generateRoutesAndOD")).toInstance(this.generateRoutesAndOD);
		bind (paramContainer.class).annotatedWith(Names.named("CurrentParam")).toInstance(this.currentParam);
		bind(Measurements.class).annotatedWith(Names.named("CalibrationCounts")).toInstance(this.storage.getCalibrationMeasurements());
	}
	public Measurements getOutputMeasurements() {
		return outputMeasurements;
	}
	
}