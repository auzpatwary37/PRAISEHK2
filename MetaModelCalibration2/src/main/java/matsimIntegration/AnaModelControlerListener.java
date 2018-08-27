package matsimIntegration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;



import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.controler.listener.StartupListener;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import analyticalModel.AnalyticalModel;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import measurements.Measurements;






public class AnaModelControlerListener implements StartupListener,BeforeMobsimListener, AfterMobsimListener,IterationEndsListener, ShutdownListener{
	private boolean generateOD=true;
	private Scenario scenario;
	@Inject
	private AnalyticalModel SueAssignment;
	@Inject
	private @Named("CalibrationCounts") Measurements calibrationMeasurements;
	private String fileLoc;
	@Inject
	private LinkCountEventHandler pcuVolumeCounter;
	
	private int maxIter;
	private final Map<String, FareCalculator> farecalc;
	
	@Inject
	public AnaModelControlerListener(Scenario scenario,AnalyticalModel sueAssignment, 
			Map<String,FareCalculator> farecalc,@Named("fileLoc") String fileLoc,@Named("generateRoutesAndOD") boolean generateRoutesAndOD){
		this.SueAssignment=sueAssignment;
		this.farecalc=farecalc;
		this.scenario=scenario;
		this.fileLoc=fileLoc;
		this.generateOD=generateRoutesAndOD;
	}
	
	@Inject
	private EventsManager eventsManager;
	
	
	
	@Override
	public void notifyStartup(StartupEvent event) {
		this.eventsManager.addHandler(pcuVolumeCounter);
		this.maxIter=event.getServices().getConfig().controler().getLastIteration();
		}
	
	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		this.pcuVolumeCounter.resetLinkCount();
	}
	

	public void notifyAfterMobsim(AfterMobsimEvent event) {
		
		int counter=event.getIteration();
		if(counter==this.maxIter) {
			this.countData.updateSimulationModelData(this.pcuVolumeCounter.geenerateLinkCounts());
			this.writeCSVCountData(fileLoc+"CountData_"+this.countData.getCurrentSimIteration()+".csv");
		}
		
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		
		
	}

	

	@Override
	public void notifyShutdown(ShutdownEvent event) {
		new PopulationWriter(event.getServices().getScenario().getPopulation()).write(fileLoc+"population_"+countData.getCurrentSimIteration()+".xml");
		
		if(this.generateOD) {
			//System.out.println();
		this.SueAssignment.generateRoutesAndOD(event.getServices().getScenario().getPopulation(),
				event.getServices().getScenario().getNetwork(),
				event.getServices().getScenario().getTransitSchedule(),
				event.getServices().getScenario(), this.farecalc);
		}	

		
	}
	
	private void writeCSVCountData(String fileLoc) {
		Double simObjective=0.;
		String d=",";
		String n="\n";
		String header="StationId"+d+"Time"+d+"LinkId"+d+"From Node Id"+d+"From Node X"+
				d+"From Node Y"+d+"To Node Id"+d+"To Node X"+d+"To Node Y"+d+"PCU Car Volume"+d+"RealCount"+"\n";
		try {
			FileWriter fileWriter=new FileWriter(fileLoc);
			fileWriter.append(header);
			for(CountStation cs:this.countData.getCountstationsByLink().values()) {
				for(String timeId:this.countData.getTimeBean().keySet()) {
					Node FromNode=this.scenario.getNetwork().getLinks().get(cs.getLinkId()).getFromNode();
					Node ToNode=this.scenario.getNetwork().getLinks().get(cs.getLinkId()).getToNode();
					fileWriter.append(cs.getCountStationId()+d+timeId+d+cs.getLinkId()+d+FromNode.getId()+d+FromNode.getCoord().getX()+d+FromNode.getCoord().getY()
							+d+ToNode.getId()+d+ToNode.getCoord().getX()+d+ToNode.getCoord().getY()+d+
							cs.getSimCount(this.countData.getCurrentSimIteration(), timeId)+d+cs.getRealCount(timeId));
					fileWriter.append(n);
					simObjective+=Math.pow(cs.getSimCount(this.countData.getCurrentSimIteration(), timeId)-cs.getRealCount(timeId),2);
				}
			}
			fileWriter.append("simObjective,"+simObjective);
			fileWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
		
}