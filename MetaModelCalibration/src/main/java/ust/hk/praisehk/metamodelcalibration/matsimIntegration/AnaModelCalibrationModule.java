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

	
	public AnaModelCalibrationModule(MeasurementsStorage countData,AnalyticalModel sueAssignment,String FileLoc) {
		this.storage=countData;
		this.sueAssignment=sueAssignment;
		this.fileLoc=FileLoc;
		}	
	public AnaModelCalibrationModule(MeasurementsStorage countData,AnalyticalModel sueAssignment,String FileLoc,boolean generateRouteAndOD) {
		this.storage=countData;
		this.sueAssignment=sueAssignment;
		this.fileLoc=FileLoc;
		this.generateRoutesAndOD=generateRouteAndOD;

		}
	
	public void install() {
		bind(MeasurementsStorage.class).toInstance(this.storage);
		bind(AnalyticalModel.class).toInstance(this.sueAssignment);
		bind(String.class).annotatedWith(Names.named("fileLoc")).toInstance(this.fileLoc);
		this.addControlerListenerBinding().to(AnaModelControlerListener.class);
		this.addEventHandlerBinding().toInstance(new LinkCountEventHandler(this.storage.getCalibrationMeasurements()));
		bind(LinkCountEventHandler.class).toInstance(new LinkCountEventHandler(this.storage.getCalibrationMeasurements()));
		bind(boolean.class).annotatedWith(Names.named("generateRoutesAndOD")).toInstance(this.generateRoutesAndOD);
	}
}