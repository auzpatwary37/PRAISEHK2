package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.LinkedHashMap;

import org.matsim.core.controler.AbstractModule;

import com.google.inject.name.Names;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;




public class AnaModelCalibrationModule extends AbstractModule{
	private MeasurementsStorage storage;
	private AnalyticalModel sueAssignment;
	private final String fileLoc;
	private boolean generateRoutesAndOD=true;
	private paramContainer currentParam;
	
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
		}
	
	public void install() {
		bind(MeasurementsStorage.class).toInstance(this.storage);
		bind(AnalyticalModel.class).toInstance(this.sueAssignment);
		bind(String.class).annotatedWith(Names.named("fileLoc")).toInstance(this.fileLoc);
		this.addControlerListenerBinding().to(AnaModelControlerListener.class);
		this.addEventHandlerBinding().toInstance(new LinkCountEventHandler(this.storage.getCalibrationMeasurements()));
		bind(LinkCountEventHandler.class).toInstance(new LinkCountEventHandler(this.storage.getCalibrationMeasurements()));
		bind(boolean.class).annotatedWith(Names.named("generateRoutesAndOD")).toInstance(this.generateRoutesAndOD);
		bind (paramContainer.class).annotatedWith(Names.named("CurrentParam")).toInstance(this.currentParam);
	}
}