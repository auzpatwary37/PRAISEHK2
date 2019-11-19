package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

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
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalPlan;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.lanes.Lane;

import com.google.common.collect.Sets;

public class SignalFlowReductionGenerator {
	private final Scenario scenario;
	public int activeGc=0;
	public SignalFlowReductionGenerator(Scenario scenario){
		this.scenario = scenario;
	}
	
	public double[] getGCratio(Link link) {
//		if(link!=null) {
//			return new double[] {1,60};
//		}
		if((SignalsData) scenario.getScenarioElement("signalsData")==null) {
			return new double[] {1,60};
		}
		SignalsData sd = (SignalsData) scenario.getScenarioElement("signalsData");
		SignalControlData signalControlData = sd.getSignalControlData();
		SignalGroupsData signalsGroupsData = sd.getSignalGroupsData();
		SignalSystemsData signalsSystemsData = sd.getSignalSystemsData();
		
		Id<SignalSystem> sigSysId = Id.create(link.getToNode().getId().toString(), SignalSystem.class);
		SignalSystemData ssd = signalsSystemsData.getSignalSystemData().get(sigSysId); 
		if(ssd==null) {
			return new double[] {1,60};
		}
		//take all the sig of the same link
		HashMap<Id<Signal>, Id<SignalGroup>> sigIds = new HashMap<Id<Signal>, Id<SignalGroup>>();
		for(Entry<Id<Signal>, SignalData> entry: ssd.getSignalData().entrySet()) {
			if(entry.getValue().getLinkId().equals(link.getId())) {
				sigIds.put(entry.getKey(), null);
			}
		}
		
		//If there is no lane with a signal, return a 1.0 means no signal.
		if(sigIds.size() == 0) {
			return new double[] {1,60}; 
		}
		
		//take the sig group id for each sig
		for(Entry<Id<SignalGroup>, SignalGroupData> entry: signalsGroupsData.getSignalGroupDataBySystemId(sigSysId).entrySet()) {
			for(Entry<Id<Signal>, Id<SignalGroup>> entry2 : sigIds.entrySet())
				if(entry.getValue().getSignalIds().contains(entry2.getKey()))
					entry2.setValue(entry.getKey());
		}
		
		//aggregate the timings
		SignalPlanData planData = signalControlData.getSignalSystemControllerDataBySystemId().get(sigSysId).getSignalPlanData().get(Id.create("1", SignalPlan.class));
		double cycleTime = planData.getCycleTime();
		double out = 0;
		for(Id<SignalGroup> entry : sigIds.values()) {
			if(entry == null)
				continue;
			SignalGroupSettingsData thing = planData.getSignalGroupSettingsDataByGroupId().get(entry);
			int onSet = thing.getOnset();
			int dropping = thing.getDropping();
			//add this gc ratio into out
			out += ((onSet>=dropping ? dropping+cycleTime : dropping) - onSet) / cycleTime;
		}
		if(out/sigIds.size()!=1) {
			this.activeGc++;
		}
		double gc=out/sigIds.size();
		if(gc==0) {
			gc=1;
		}
		return new double[] {gc, cycleTime};
	}
	
	public double[] getGCratio(Link link, Id<Link> toLinkId) {
		Set<Id<Lane>> laneIds = Sets.newHashSet();
		if(toLinkId != null && scenario.getLanes().getLanesToLinkAssignments().get(link.getId())!=null) {
			for(Lane lane: scenario.getLanes().getLanesToLinkAssignments().get(link.getId()).getLanes().values()) {
				if(lane.getToLinkIds()!=null && lane.getToLinkIds().contains(toLinkId)) {
					laneIds.add(lane.getId());
				}
			}
		}
		
		SignalsData sd = (SignalsData) scenario.getScenarioElement("signalsData");
		SignalControlData signalControlData = sd.getSignalControlData();
		SignalGroupsData signalsGroupsData = sd.getSignalGroupsData();
		SignalSystemsData signalsSystemsData = sd.getSignalSystemsData();
		
		SignalSystemData ssd = signalsSystemsData.getSignalSystemData().get(Id.create(link.getToNode().getId().toString(), SignalSystem.class)); //We spot for that signal system
		double number_of_lane = 0;
		double seconds_green = 0;
		if(ssd == null) {
			return new double[] {1.0,60}; //No signal, so it is 1.0
		}else {
			double cycleTime = 0.0;
			for(SignalData signalData: ssd.getSignalData().values()) { //Work for every signal
				if(signalData.getLinkId().equals(link.getId()) && (toLinkId==null || laneIds.isEmpty() || //Must be the same link
						!Sets.intersection(laneIds, signalData.getLaneIds()).isEmpty())) { //If we check for lane, then it must contain the lane also, if there are.
					//Step 1: Find the signal group Id
					Id<SignalGroup> signalGroupDataId = null;
					for(SignalGroupData sg: signalsGroupsData.getSignalGroupDataBySystemId( ssd.getId() ).values() ) {
						if(sg.getSignalIds().contains(signalData.getId())) {
							signalGroupDataId = sg.getId();
						}
					}
					if(signalGroupDataId == null) {
						continue; //We ignore this signal if it is not found in any group
					}
					
					number_of_lane++;
					//Step 2: Find the green time of this signal group.
					for(SignalPlanData sp: signalControlData.getSignalSystemControllerDataBySystemId().get( ssd.getId() ).getSignalPlanData().values()) {
						cycleTime = sp.getCycleTime();
						SignalGroupSettingsData signalGroupSetting = sp.getSignalGroupSettingsDataByGroupId().get(signalGroupDataId);
						double time = signalGroupSetting.getDropping() - signalGroupSetting.getOnset();
						seconds_green += time;
						break;
					}
				}
			}
			if(number_of_lane > 0) {
				return new double[] {seconds_green / (cycleTime * number_of_lane),cycleTime};
			}else {
				return new double[] {1,60}; //If there is no lane with a signal, return a 1.0 means no signal.
			}
		}
	}
}
