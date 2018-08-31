package ust.hk.praisehk.metamodelcalibration.matsimIntegration;



import org.matsim.core.controler.AbstractModule;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;






public class RouteAndODGeneratorModule extends AbstractModule{
	private AnalyticalModel sueAssignment;
	
	public RouteAndODGeneratorModule(AnalyticalModel sueAssignment) {
		this.sueAssignment=sueAssignment;
	}
	public void install(){
		bind(AnalyticalModel.class).toInstance(this.sueAssignment);
		this.addControlerListenerBinding().to(RoutesAndODGeneratorControllerListener.class);
	}
	
	
}
