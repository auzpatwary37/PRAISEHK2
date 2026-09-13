package networkFromSaturn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.signals.controller.fixedTime.DefaultPlanbasedSignalSystemController;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalControlData;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalControlDataFactory;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalGroupSettingsData;
import org.matsim.contrib.signals.data.signalcontrol.v20.SignalPlanData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupData;
import org.matsim.contrib.signals.data.signalgroups.v20.SignalGroupsData;
import org.matsim.contrib.signals.data.signalsystems.v20.SignalSystemControllerData;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.contrib.signals.utils.SignalUtils;

public class SignalGenerator {
	@Deprecated
	private List<String> signals;
	@Deprecated
	private int[] onset;
	@Deprecated
	private int[] dropping;
	@Deprecated
	private int[] group;
	@Deprecated
	private int group_count = 0;
	@Deprecated
	private int gap_tolerance = 10;

	@Deprecated
	private List<String> buffer_signals;
	@Deprecated
	private List<Integer> buffer_onset;
	@Deprecated
	private List<Integer> buffer_dropping;
	
	//new stuff JLo
	private int cycleTime;
	private int inputedCT;	//to measure if declared stages has same cycle time as declared
	private int offSet;
	private HashMap<Id<Signal>, LinkedList<Integer>> sigDStageMap;
	private HashMap<Integer, DStage> dStageMap;
	private final static Logger log = Logger.getLogger(SignalGenerator.class);
	
	@Deprecated
	public SignalGenerator(SignalsData sig, String sigSysIdString, List<Id<Signal>> allSigIds, int cycleTime, int offSet) {
		this.signals = new ArrayList<String>();
		for (Id<Signal> id : allSigIds) {
			if (!this.signals.contains(id.toString()))
				this.signals.add(id.toString());
		}
		
		int signal_counts = allSigIds.size();
		this.onset = new int[signal_counts];
		this.dropping = new int[signal_counts];
		this.group = new int[signal_counts];
		for (int i = 0; i < signal_counts; i++) {
			this.onset[i] = -1;
			this.dropping[i] = -1;
			this.group[i] = -1;
		}
		
		// For buffers
		this.buffer_signals = new ArrayList<String>();
		this.buffer_onset = new ArrayList<Integer>();
		this.buffer_dropping = new ArrayList<Integer>();
	}
		
	/**
	 * FIXME new constructor
	 */
	public SignalGenerator(List<Id<Signal>> allSigIds, int cycleTime, int offSet) {
		if(offSet<0)
			offSet+=cycleTime;
		this.cycleTime = cycleTime;
		this.offSet = offSet;
		this.inputedCT = 0;
		this.sigDStageMap = new HashMap<Id<Signal>, LinkedList<Integer>>();
		for(Id<Signal> TSig: allSigIds)
			this.sigDStageMap.put(TSig, new LinkedList<Integer>());
		this.dStageMap = new HashMap<Integer, DStage>();
		
	}
	
	/**
	 * Stores a SATURN stage
	 * Data same as SATURN, such that minimal handling of data needed outside of here
	 * @param duration		- same as saturn
	 * @param interGreen	- same as saturn
	 * @param thisStageIds
	 */
	public void addStage(int duration, int interGreen, List<Id<Signal>> thisStageIds) {
		int dStageId = this.dStageMap.size();
		for(Id<Signal> TS: thisStageIds)
			this.sigDStageMap.get(TS).add(dStageId);
		this.dStageMap.put(dStageId, new DStage(this.inputedCT, duration, interGreen));
		this.inputedCT += (duration + interGreen);
	}
	
	public void createSignals(Id<SignalSystem> SSID, SignalGroupsData groups, SignalControlData control) {
		//DEBUG use
//		if(SSID.toString().equals("101330"))
//			System.out.println("");
		
		//#1 	Checking stages
		//consistent cycle time
		//if not, scales the cycle time according to the declared CT
		//mirroring the behaviour of SATURN
		if(this.cycleTime != this.inputedCT)
			for(DStage TDS: this.dStageMap.values())
				TDS.scale(this.cycleTime, this.inputedCT);
		//consistent staging
		int numOfDStage = this.dStageMap.size();
		LinkedHashMap<LinkedList<Integer>, HashSet<Id<Signal>>> invDStageMap = new LinkedHashMap<LinkedList<Integer>, HashSet<Id<Signal>>>();
		for(Entry<Id<Signal>, LinkedList<Integer>> sigDStageEntry: this.sigDStageMap.entrySet()) {
			LinkedList<Integer> dStageList = sigDStageEntry.getValue();
			//only needed check if crosses more than 1 stage
			if(dStageList.size()>1)
				if(!checkDummyStaging(numOfDStage, dStageList)) {
					//split the stages if more than 1 green (real) stage in a cycle
					HashSet<LinkedList<Integer>> splitStageList = splitStaging(numOfDStage, dStageList);
					for(LinkedList<Integer> TDSList: splitStageList) {
						//invert the list to sort by using the same stages
						if(!invDStageMap.containsKey(TDSList))
							invDStageMap.put(TDSList, new HashSet<Id<Signal>>());
						invDStageMap.get(TDSList).add(sigDStageEntry.getKey());
					}
					continue;
				}
			//invert the list to sort by using the same stages
			if(!invDStageMap.containsKey(dStageList))
				invDStageMap.put(dStageList, new HashSet<Id<Signal>>());
			invDStageMap.get(dStageList).add(sigDStageEntry.getKey());
		}
		
		//#2	Actual Building
		SignalControlDataFactory fac = control.getFactory();
		SignalPlanData plan = SignalUtils.createSignalPlan(fac, this.cycleTime, this.offSet);
		
		int buildingGroupNum = 0;
		for(Entry<LinkedList<Integer>, HashSet<Id<Signal>>> invSigDStageMapEntry: invDStageMap.entrySet()) {
			//skip if always green, 	always green can also be key.size == numOfDStage, but will break other sig that has only 1 stage with long intergreen for ped crossing
			if(invSigDStageMapEntry.getKey().isEmpty())
				continue;
			//figure out the onset offset
			int onSet = this.dStageMap.get(invSigDStageMapEntry.getKey().peekFirst()).startTime;
			int dropp = onSet;
			Iterator<Integer> sigDStageItr = invSigDStageMapEntry.getKey().iterator();
			while(sigDStageItr.hasNext()) {
				Integer thisStageKey = sigDStageItr.next();
				dropp += this.dStageMap.get(thisStageKey).duration;
				//don't count the last inter-green time
				if(sigDStageItr.hasNext())
					dropp += this.dStageMap.get(thisStageKey).intergreen;
			}
			//make sig-gp for it
			groups.addSignalGroupData(groups.getFactory().createSignalGroupData(SSID, Id.create(buildingGroupNum, SignalGroup.class)));
			SignalGroupSettingsData sigGpSet = fac.createSignalGroupSettingsData(Id.create(buildingGroupNum, SignalGroup.class));
			sigGpSet.setOnset(onSet);
			sigGpSet.setDropping(dropp % this.cycleTime);
			plan.addSignalGroupSettings(sigGpSet);
			//put the signals into the group
			for(Id<Signal> TSigId: invSigDStageMapEntry.getValue())
				groups.getSignalGroupDataBySystemId(SSID).get(Id.create(buildingGroupNum, SignalGroup.class)).addSignalId(TSigId);
			//house cleaning
			buildingGroupNum++;
		}
		//only put it in iff there are signals built
		if(buildingGroupNum > 0) {
			//dumped the whole thing in to avoid empty controllers
			SignalSystemControllerData controller = fac.createSignalSystemControllerData(SSID);
			controller.setControllerIdentifier(DefaultPlanbasedSignalSystemController.IDENTIFIER);
			controller.addSignalPlanData(plan);
			control.addSignalSystemControllerData(controller);
		}
	}
	
	/**
	 * Checks if the inputed staging are sequential<br>
	 * if the sequence crosses 0, it will be sorted such that it is in sequential order (ie: {4,5,0,1})
	 */
	private static boolean checkDummyStaging(int numOfDummyStages, LinkedList<Integer> stageList) {
		int hasStages = stageList.size();
		//case: always green
		if(hasStages == numOfDummyStages)
			return true;
		//try start point
		for(int i=0; i<hasStages; i++) {
			int pointer = stageList.get(i);
			int counter = 1;
			//check seq from that starting point
			//+  numOfDummyStages) % numOfDummyStages because -counter may go to negative num
			while((stageList.get((i+counter)%hasStages) - counter +  numOfDummyStages) % numOfDummyStages == pointer) {	
				counter++;
				if(counter == hasStages) {
					//sort the list according to the seq order
					for(int j=0; j<i; j++) {
						Integer Temp = stageList.pop();
						stageList.add(Temp);
					}
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Assuming there are multiple real green stages<br>
	 * splits them into individual lists and in correct order for build
	 */
	private static HashSet<LinkedList<Integer>> splitStaging(int numOfDummyStages, LinkedList<Integer> orgStageList) {
		HashSet<LinkedList<Integer>> output = new HashSet<LinkedList<Integer>>();
		
		HashSet<Integer> startpoint = new HashSet<Integer>();
		boolean lastHas = false;
		//find the start points, assume 0 is one iff it is in orgStageList
		for(int i=0; i<numOfDummyStages; i++) {
			if(orgStageList.contains(i) && !lastHas) {
				startpoint.add(i);
				lastHas = true;
			} else
				lastHas = false;
			
			//if the last is in orgStageList then 0 is not a start point
			if(i+1 == numOfDummyStages && orgStageList.contains(i))
				startpoint.remove(0);
		}
		
		//make output acc to startpoint list
		for(Integer start: startpoint) {
			LinkedList<Integer> newList = new LinkedList<Integer>();
			int thisStage = start;
			newList.add(thisStage);
			thisStage++;
			while(orgStageList.contains(thisStage%numOfDummyStages)) {
				newList.add(thisStage%numOfDummyStages);
				thisStage++;
			}
			output.add(newList);
		}
		
		return output;
	}
	
	/**
	 * tuple dummy stage
	 * @author JLo
	 *
	 */
	private class DStage {
		public int startTime;
		public int duration;
		public int intergreen;
		
		public DStage(int startTime, int duration, int intergreen) {
			this.startTime = startTime;
			this.duration = duration;
			this.intergreen = intergreen;
		}
		
		public void scale(int CycleTime, int totalReced) {
			this.startTime = this.startTime * CycleTime / totalReced;
			this.duration = this.duration * CycleTime / totalReced;
			this.intergreen = this.intergreen * CycleTime / totalReced;
		}
	}
	
	@Deprecated
	public SignalGenerator(int signal_counts, List<Id<Signal>> ids) {
		this.signals = new ArrayList<String>();

		for (Id<Signal> id : ids) {
			if (!this.signals.contains(id.toString()))
				this.signals.add(id.toString());
		}

		this.onset = new int[signal_counts];
		this.dropping = new int[signal_counts];
		this.group = new int[signal_counts];

		for (int i = 0; i < signal_counts; i++) {
			this.onset[i] = -1;
			this.dropping[i] = -1;
			this.group[i] = -1;
		}

		// For buffers
		this.buffer_signals = new ArrayList<String>();
		this.buffer_onset = new ArrayList<Integer>();
		this.buffer_dropping = new ArrayList<Integer>();
	}

	// Return changed offset
	@Deprecated
	public int add_green(int onset, int dropping, int cycletime, int Offset, List<Id<Signal>> ids) {
		int prev_onset = -1;
		int prev_dropping = -1;

		for (Id<Signal> id : ids) {
			int a = this.signals.indexOf(id.toString());

			// Pass the duplicate calls
			if (onset == this.onset[a] && dropping == this.dropping[a])
				continue;

			// If the group is occurred in the second time, but in different line
			if (prev_onset != this.onset[a] || prev_dropping != this.dropping[a]) {
				this.group_count++;
				prev_onset = this.onset[a];
				prev_dropping = this.dropping[a];
			}

			if (this.onset[a] == -1) {
				this.onset[a] = onset;
				this.dropping[a] = dropping;
				this.group[a] = this.group_count;
			}
			// If it is the second time to call this signal
			// It would throw error if the first dropping and onset is too long from each
			// other
			// Otherwise, two phases would be combined
			else {
				if (onset - this.dropping[a] < this.gap_tolerance) {
					this.dropping[a] = dropping;
					this.group[a] = this.group_count;
				}
				// This case is when the last collapses with the first
				else if (cycletime - dropping < this.gap_tolerance && this.onset[a] == 0) {
					Offset += onset;
					if (Offset >= cycletime) {
						Offset -= cycletime;
					}
					Offset -= this.swift_phase(Offset, cycletime, a, onset);
					dropping = dropping + cycletime - Offset;
					if (dropping >= cycletime) {
						dropping -= cycletime;
					}
					this.group[a] = this.group_count;
				}
				// The other signal that just shifted would fall into this case
				else if (this.onset[a] + onset == cycletime) {
					this.onset[a] = 0;
					this.group[a] = this.group_count;
				} else {
					this.buffer_signals.add(id.toString());
					this.buffer_onset.add(onset);
					this.buffer_dropping.add(dropping);
					// throw new IllegalArgumentException("The second onset time looks strange");
				}
			}
		}
		this.group_count++;
		return Offset;
	}

	// Return: the additional offset shifted.
	@Deprecated
	private int swift_phase(int offset, int cycletime, int signal_index, int new_onset) {
		this.onset[signal_index] = 0;
		this.dropping[signal_index] = this.dropping[signal_index] + cycletime - new_onset;

		// Shift other signals
		for (int i = 0; i < this.onset.length; i++) {
			if (i != signal_index) {
				this.onset[i] = this.onset[i] + cycletime - new_onset;
				this.dropping[i] = this.dropping[i] + cycletime - new_onset;
				if (this.onset[i] >= cycletime) {
					this.onset[i] -= cycletime;
					this.dropping[i] -= cycletime;
				}
			}
		}
		// Try to fix the signal if the dropping is too long.
		boolean needToFix = false;
		for (int i = 0; i < this.dropping.length; i++) { // Find out if there is dropping that is too long.
			if (this.dropping[i] > cycletime) {
				needToFix = true;
				break;
			}
		}
		if (needToFix) { // Find if there is time of all red
			// Fill in the green time
			boolean[] timeArray = new boolean[cycletime]; // true: There is green
			for (int i = 0; i < this.dropping.length; i++) {
				for (int j = this.onset[i]; j < Math.min(this.dropping[i], cycletime); j++) {
					timeArray[j] = true;// True indicates there is green.
				}
			}

			int offset2 = -1; // New shift.
			boolean isThereAllRed = false;
			for (int i = 0; i < cycletime; i++) { // Find the time to start split.
				if (timeArray[i] == false) {
					isThereAllRed = true;
				} else if (isThereAllRed) { // The first green after all red.
					offset2 = i;
					break;
				}
			}
			if (offset2 == -1) { // If there is no all red time, then just add the green time by make the green
									// starts earlier, to conserve the flow.
				for (int i = 0; i < this.onset.length; i++) {
					int diff = this.dropping[i] - cycletime;
					if (diff > 0) {
						this.onset[i] -= diff;
						this.dropping[i] -= diff;
					}
				}
			} else {
				for (int i = 0; i < this.onset.length; i++) {
					this.onset[i] -= offset2;
					this.dropping[i] -= offset2;
					if (this.onset[i] < 0) {
						this.onset[i] += cycletime;
						this.dropping[i] += cycletime;
					}
				}
			}
			return offset2;
		}

		// For the case where the buffer is needed.
		for (int i = 0; i < buffer_onset.size(); i++) {
			buffer_onset.set(i, buffer_onset.get(i) + cycletime - new_onset);
			buffer_dropping.set(i, buffer_dropping.get(i) + cycletime - new_onset);
		}
		return 0;
	}

	@Deprecated
	public int getGroup_count() {
		return this.group_count;
	}

	@Deprecated
	public void create_signals(Id<SignalSystem> SSID, SignalGroupsData groups, SignalControlData control, int cycletime,
			int offset) {
		this.group_count++;
		if (this.buffer_onset.size() != 0) {
			for (int i = 0; i < this.buffer_onset.size(); i++) {
				int signal_index = this.signals.indexOf(this.buffer_signals.get(i));
				if ((cycletime - this.buffer_dropping.get(i)) < this.gap_tolerance && this.onset[signal_index] == 0) {
					offset += this.buffer_onset.get(i);
					if (offset >= cycletime) {
						offset -= cycletime;
					}
					this.swift_phase(offset, cycletime, signal_index, this.buffer_onset.get(i));
					this.group[signal_index] = this.group_count;
				} else {
					System.out.println(
							"Signal " + this.signals.get(signal_index) + " is moved, and the signals are shifted");
				}
			}
		}
		// Initialize the control of this signal
		SignalControlDataFactory fac = control.getFactory();

		SignalSystemControllerData controller = fac.createSignalSystemControllerData(SSID);
		control.addSignalSystemControllerData(controller);
		controller.setControllerIdentifier(DefaultPlanbasedSignalSystemController.IDENTIFIER);
		SignalPlanData plan = SignalUtils.createSignalPlan(fac, cycletime, offset);
		controller.addSignalPlanData(plan);

		for (int i = 0; i <= this.group_count; i++) {
			// Create signal groups
			Id<SignalGroup> signalGroupId = Id.create(i, SignalGroup.class);
			SignalGroupData group4signal = groups.getFactory().createSignalGroupData(SSID, signalGroupId);
			int onset = -1;
			int dropping = -1;
			boolean founded = false;

			for (int j = 0; j < this.group.length; j++) {
				if (this.group[j] == i) {
					group4signal.addSignalId(Id.create(this.signals.get(j), Signal.class));
					onset = this.onset[j];
					dropping = this.dropping[j];
					if (dropping < 0) {
						dropping += cycletime;
					}
					founded = true;
					if (dropping > cycletime) {
						throw new IllegalArgumentException("The dropping time is longer than the cycle time");
					}

				}
			}

			if (founded) {
				groups.addSignalGroupData(group4signal);
				plan.addSignalGroupSettings(SignalUtils.createSetting4SignalGroup(fac, signalGroupId, onset, dropping));
			} else
				continue;

			if (onset < 0 || dropping < 0) {
				throw new IllegalArgumentException("The onset and dropping time have error");
			}
		}
	}
}
