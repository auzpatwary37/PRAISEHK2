package matsimIntegration;

import java.util.LinkedHashMap;

import org.matsim.core.controler.AbstractModule;

import com.google.inject.name.Names;


import ust.hk.praisehk.AnalyticalModels.AnalyticalModel;
import ust.hk.praisehk.Counts.CountData;

public class AnaModelCalibrationModule extends AbstractModule{
	private CountData countData;
	private AnalyticalModel sueAssignment;
	private final String fileLoc;
	private boolean generateRoutesAndOD=true;

	
	public AnaModelCalibrationModule(CountData countData,AnalyticalModel sueAssignment,String FileLoc) {
		this.countData=countData;
		this.sueAssignment=sueAssignment;
		this.fileLoc=FileLoc;
		}	
	public AnaModelCalibrationModule(CountData countData,AnalyticalModel sueAssignment,String FileLoc,boolean generateRouteAndOD) {
		this.countData=countData;
		this.sueAssignment=sueAssignment;
		this.fileLoc=FileLoc;
		this.generateRoutesAndOD=generateRouteAndOD;

		}
	
	public void install() {
		bind(CountData.class).toInstance(this.countData);
		bind(AnalyticalModel.class).toInstance(this.sueAssignment);
		bind(String.class).annotatedWith(Names.named("fileLoc")).toInstance(this.fileLoc);
		this.addControlerListenerBinding().to(AnaModelControlerListener.class);
		this.addEventHandlerBinding().toInstance(new LinkCountEventHandler(this.countData));
		bind(LinkCountEventHandler.class).toInstance(new LinkCountEventHandler(this.countData));
		bind(boolean.class).annotatedWith(Names.named("generateRoutesAndOD")).toInstance(this.generateRoutesAndOD);
	}
}