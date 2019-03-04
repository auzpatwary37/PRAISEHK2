package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalControlData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupSettingsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupsData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalPlanData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsData;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalPlan;
import org.matsim.contrib.signals.model.SignalSystem;

public class SignalFlowReductionGenerator {
	private final Scenario scenario;
	
	public SignalFlowReductionGenerator(Scenario scenario){
		this.scenario = scenario;
	}
	
	public double getGCratio(Link link) {
		SignalsData sd = (SignalsData) scenario.getScenarioElement("signalsData");
		SignalControlData signalControlData = sd.getSignalControlData();
		SignalGroupsData signalsGroupsData = sd.getSignalGroupsData();
		SignalSystemsData signalsSystemsData = sd.getSignalSystemsData();
		
		SignalSystemData ssd = signalsSystemsData.getSignalSystemData().get(Id.create(link.getToNode().toString(), SignalSystem.class)); //We spot for that signal system
		double number_of_lane = 0;
		double seconds_green = 0;
		if(ssd == null) {
			return 1.0; //No signal, so it is 1.0
		}else {
			for(SignalData signalData: ssd.getSignalData().values()) { //Work for every signal
				if(signalData.getLinkId().equals(link.getId())) {
					number_of_lane++;
					
					//Step 1: Find the signal group Id
					Id<SignalGroup> signalGroupDataId = null;
					for(SignalGroupData sg: signalsGroupsData.getSignalGroupDataBySystemId( ssd.getId() ).values() ) {
						if(sg.getSignalIds().contains(signalData.getId())) {
							signalGroupDataId = sg.getId();
						}
					}
					
					//Step 2: Find the green time of this signal group.
					for(SignalPlanData sp: signalControlData.getSignalSystemControllerDataBySystemId().get( ssd.getId() ).getSignalPlanData().values()) {
						SignalGroupSettingsData signalGroupSetting = sp.getSignalGroupSettingsDataByGroupId().get(signalGroupDataId);
						double time = signalGroupSetting.getDropping() - signalGroupSetting.getOnset();
						seconds_green += time;
						break;
					}
				}
			}
			return seconds_green / number_of_lane;
		}
	}
}
