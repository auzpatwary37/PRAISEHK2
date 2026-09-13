package networkFromSaturn;

/**
 * This class is normally a container of LinkDefinitions11 and SignalsData20, but with some function added to it for a good reason.
 */
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.signals.SignalSystemsConfigGroup;
import org.matsim.contrib.signals.controller.fixedTime.DefaultPlanbasedSignalSystemController;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalControlData;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalControlDataFactory;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalGroupSettingsData;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalPlanData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupsData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemControllerData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemsDataFactory;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.contrib.signals.utils.SignalUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesFactory;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.lanes.LanesUtils;

public class LaneAndSignals {
	private Lanes lanes;
	private SignalsData signalsdata;

	public LaneAndSignals(Lanes lanes, SignalsData signalsdata) {
		this.lanes = lanes;
		this.signalsdata = signalsdata;
	}

	// Access the lanes data
	public Lanes getLanes() {
		return this.lanes;
	}

	// Access the SignalsData
	public SignalsData getSignalsData() {
		return this.signalsdata;
	}

	// Access the SignalSystemsData
	public SignalSystemsData getSignalSystemsData() {
		return signalsdata.getSignalSystemsData();
	}

	// Access the SignalControlData
	public SignalControlData getSignalControlData() {
		return signalsdata.getSignalControlData();
	}

	// Access the SignalGroupsData
	public SignalGroupsData getSignalGroupsData() {
		return signalsdata.getSignalGroupsData();
	}

	public LaneAndSignals cleanLanesAndSignals(Config config, Network net) {
		Set<Id<Link>> LinkIdset = net.getLinks().keySet();

		SignalsData newSignalsData = SignalUtils.createSignalsData(
				ConfigUtils.addOrGetModule(config, SignalSystemsConfigGroup.GROUP_NAME, SignalSystemsConfigGroup.class));
		SignalSystemsData newSystems = newSignalsData.getSignalSystemsData();
		SignalGroupsData newGroups = newSignalsData.getSignalGroupsData();
		SignalControlData newControl = newSignalsData.getSignalControlData();
		SignalControlDataFactory SCDfactory = newControl.getFactory();
		SignalSystemsDataFactory SSDfactory = newSystems.getFactory();
		HashMap<Id<SignalSystem>, SignalSystemData> newSsdSet = new HashMap<Id<SignalSystem>, SignalSystemData>();

		Lanes new_lanes = LanesUtils.createLanesContainer();
		LanesFactory factory = new_lanes.getFactory();

		for (LanesToLinkAssignment l2l : lanes.getLanesToLinkAssignments().values()) {
			// Obtain the ID of the current link
			Id<Link> linkId = l2l.getLinkId();
			// The new LanesToLinkAssignment to replace the old one
			LanesToLinkAssignment new_l2l = null;

			boolean toProcessSignal = false; // A boolean to determine signal to be processed or not

			// The signal system, if it is to be modified, has an ID of toNode of that link
			SignalSystemData oldSystem = null;
			Id<SignalSystem> signalSystemId = null;
			SignalSystemData newSystem = null;

			// 1. Check if the lane to link is defined on a valid link, if not, replace it.
			if (LinkIdset.contains(linkId)) {
				new_l2l = factory.createLanesToLinkAssignment(linkId); // Create new link to link container
				signalSystemId = Id.create(net.getLinks().get(linkId).getToNode().getId().toString(),
						SignalSystem.class);
				// Obtain the old system and enable signal processing.
				if (getSignalSystemsData().getSignalSystemData().containsKey(signalSystemId)) {
					toProcessSignal = true;
					oldSystem = getSignalSystemsData().getSignalSystemData().get(signalSystemId);
				}

				// Obtain the new system if it is to be processed, elso create a new one
				if (newSsdSet.containsKey(signalSystemId) && toProcessSignal) {
					newSystem = newSsdSet.get(signalSystemId);
				} else if (toProcessSignal) {
					newSystem = SSDfactory.createSignalSystemData(signalSystemId);
				}
			} else {
				continue;
			}

			// 2. Check if a lane is going to a null link, if there is, cancel the link
			List<Id<Lane>> validLaneIds = new ArrayList<Id<Lane>>();
			Lane firstLane = null; // The lane just after entering the road.

			for (Lane lane : l2l.getLanes().values()) {
				List<Id<Link>> toLinkIds = lane.getToLinkIds();

				// Save the first lane for later recreation.
				if (toLinkIds == null || l2l.getLanes().size() == 1) {
					firstLane = lane;
					continue;
				}

				List<Id<Link>> new_toLinkIds = new ArrayList<Id<Link>>();

				// For the first link: replace the occurance of this link in other lanes
				// information
				for (Id<Link> toLinkId : toLinkIds) {
					if (LinkIdset.contains(toLinkId)) {
						new_toLinkIds.add(toLinkId);
					}
				}

				// Only add the lane if there are still valid turns
				if (new_toLinkIds.size() > 0) {
					// Create the lane with valid turn
					Id<Lane> laneId = lane.getId(); // Obtain THIS lane Id
					validLaneIds.add(laneId);
					LanesUtils.createAndAddLane(new_l2l, factory, laneId, lane.getCapacityVehiclesPerHour(),
							lane.getStartsAtMeterFromLinkEnd(), lane.getAlignment(),
							(int) lane.getNumberOfRepresentedLanes(), new_toLinkIds, null);
					assert (lane.getToLaneIds() == null);

					// Create and add signal for the SignalSystem here, once the lane is confirmed
					// there
					if (toProcessSignal) {
						// Iterate the old signal system to find the suitable signal, with same link and
						// lane
						for (Id<Signal> signalId : oldSystem.getSignalData().keySet()) {
							SignalData signalData = oldSystem.getSignalData().get(signalId);
							// TODO: Add the case where a signal is needed, but it is not found in any
							// signal
							if (signalData.getLaneIds().contains(laneId) && signalData.getLinkId().equals(linkId)) {
								SignalUtils.createAndAddSignal(newSystem, SSDfactory, signalId, linkId,
										Arrays.asList(laneId));
								newSsdSet.put(signalSystemId, newSystem); // Store the signal system
							}
						}
					}
				}
			}

			if(firstLane == null)
				throw new IllegalArgumentException(linkId+" does not have .ol lane!");
			// If after cancellation, the lane have nowhere to turn, simply remove the lane,
			// if there are no lane afterwards, remove the l2l
			if (new_l2l.getLanes().size() > 0)
				LanesUtils.createAndAddLane(new_l2l, factory, firstLane.getId(), firstLane.getCapacityVehiclesPerHour(),
						net.getLinks().get(linkId).getLength(), firstLane.getAlignment(),
						(int) firstLane.getNumberOfRepresentedLanes(), null, validLaneIds);
			else if (firstLane!=null) {
				LanesUtils.createAndAddLane(new_l2l, factory, firstLane.getId(), firstLane.getCapacityVehiclesPerHour(),
						net.getLinks().get(linkId).getLength(), firstLane.getAlignment(),
						(int) firstLane.getNumberOfRepresentedLanes(), firstLane.getToLinkIds(), null);
			}
			new_lanes.addLanesToLinkAssignment(new_l2l);
		}

		// After confirmed the existence of signals, we create the new signal groups and
		// signal control according to it.

		// It would be done by iterate through every signal system that are still valid
		for (Id<SignalSystem> signalSystemId : newSsdSet.keySet()) {
			SignalSystemData signalSystem = newSsdSet.get(signalSystemId); // Obtain the signal system
			newSystems.addSignalSystemData(signalSystem); // Add it to SignalSystems
			// =====SignalSystems done=======

			// Create system controller
			SignalSystemControllerData controller = SCDfactory.createSignalSystemControllerData(signalSystemId);
			newControl.addSignalSystemControllerData(controller);
			controller.setControllerIdentifier(DefaultPlanbasedSignalSystemController.IDENTIFIER);

			SignalSystemControllerData oldController = signalsdata.getSignalControlData()
					.getSignalSystemControllerDataBySystemId().get(signalSystemId);
			SignalPlanData oldPlan = oldController.getSignalPlanData().get(Id.create("1", SignalPlanData.class));
			SignalPlanData plan = SignalUtils.createSignalPlan(SCDfactory, oldPlan.getCycleTime(), oldPlan.getOffset());
			controller.addSignalPlanData(plan);

			// Create signal group
			// Iterate by signal groups for this signal system
			for (SignalGroupData oldSignalGroupData : signalsdata.getSignalGroupsData()
					.getSignalGroupDataBySystemId(signalSystemId).values()) {
				SignalGroupData signalgroup = null;
				Id<SignalGroup> oldSignalGroupDataId = oldSignalGroupData.getId();

				// If the signal group has at least one signal found, create a new signal group
				// and add it to signalgroups
				for (Id<Signal> signalId : oldSignalGroupData.getSignalIds()) {
					if (signalSystem.getSignalData().keySet().contains(signalId)) {

						// Create a new group if it is first found, and add the signal
						if (signalgroup == null) {
							signalgroup = newGroups.getFactory().createSignalGroupData(signalSystemId,
									oldSignalGroupDataId);
							signalgroup.addSignalId(signalId);
						}
						// Otherwise, just add the signal into it is fine
						else {
							signalgroup.addSignalId(signalId);
						}
					} else {
						System.out.println("Signal " + signalSystemId + " " + signalId + " has been filtered");
					}
				}

				// Add it to groups, and add the settings to the controller
				if (signalgroup != null) {
					newGroups.addSignalGroupData(signalgroup);
					SignalGroupSettingsData oldPlanSetting = oldPlan.getSignalGroupSettingsDataByGroupId()
							.get(oldSignalGroupDataId);
					plan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(SCDfactory,
							oldSignalGroupData.getId(), oldPlanSetting.getOnset(), oldPlanSetting.getDropping()));
				} else {
					System.out.println(
							"Signalgroup " + signalSystemId + " " + oldSignalGroupDataId + " has been filtered");
				}

			}
		}
		return new LaneAndSignals(new_lanes, newSignalsData);
	}
}
