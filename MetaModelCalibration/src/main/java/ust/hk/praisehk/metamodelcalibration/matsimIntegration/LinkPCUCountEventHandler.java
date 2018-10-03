package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import org.matsim.vehicles.Vehicle;

import com.google.inject.Inject;



public class LinkPCUCountEventHandler implements LinkEnterEventHandler, TransitDriverStartsEventHandler, VehicleEntersTrafficEventHandler{
	private Map<String,Map<Id<Link>,Double>> linkCounts=new HashMap<>();
	private Map<String,Map<Id<Link>,List<Id<Vehicle>>>> Vehicles=new ConcurrentHashMap<>();
	private Map<Id<Vehicle>,Double> transitVehicles=new ConcurrentHashMap<>();
	private final Map<String, Tuple<Double,Double>> timeBean;
	//private CountData countdata;
	
	private MeasurementsStorage simStorage; 
	
	@Inject
	private Scenario scenario;
	
	
	public LinkPCUCountEventHandler(MeasurementsStorage simStorage) {
		this.timeBean=simStorage.getTimeBean();
		for(String timeBeanId:this.timeBean.keySet()) {
			linkCounts.put(timeBeanId,new ConcurrentHashMap<Id<Link>, Double>());
			Vehicles.put(timeBeanId, new ConcurrentHashMap<Id<Link>, List<Id<Vehicle>>>());
			for(Id<Link> linkId:simStorage.getLinksToCount()) {
				linkCounts.get(timeBeanId).put(linkId, 0.0);
				Vehicles.get(timeBeanId).put(linkId, Collections.synchronizedList(new ArrayList<Id<Vehicle>>()));
			}
		}
		this.simStorage=simStorage;
		
	}
	
	public Map<String,Map<Id<Link>,Double>> geenerateLinkCounts(){
		for(String timeBeanId:this.timeBean.keySet()) {
			for(Id<Link> LinkId:linkCounts.get(timeBeanId).keySet()) {
				double totalVehicle=0;
				for(Id<Vehicle> vId:new ArrayList<>(Vehicles.get(timeBeanId).get(LinkId))) {
					if(this.transitVehicles.containsKey(vId)) {
						totalVehicle+=this.transitVehicles.get(vId);
					}else {
						totalVehicle+=this.scenario.getVehicles().getVehicles().get(vId).getType().getPcuEquivalents();
					}
				}
				linkCounts.get(timeBeanId).put(LinkId,totalVehicle);
			}
		}
		return linkCounts;
	}
	
	
	@Override
	public void handleEvent(LinkEnterEvent event) {
	
		int time=(int) event.getTime();
		if(time>=86400) {time=86400;}
		String timeId=null;
		for(String s:this.timeBean.keySet()) {
			if(time>this.timeBean.get(s).getFirst() && time<=timeBean.get(s).getSecond()) {
				timeId=s;
			}
		}
		if(this.linkCounts.get(timeId).containsKey(event.getLinkId())){
				this.Vehicles.get(timeId).get(event.getLinkId()).add(event.getVehicleId());			
		}
	}


	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		

		org.matsim.vehicles.Vehicles vehicles=this.scenario.getTransitVehicles();
		this.transitVehicles.put(event.getVehicleId(),vehicles.getVehicles().
				get(event.getVehicleId()).getType().getPcuEquivalents());
		
	}
	
	public void resetLinkCount() {
		this.linkCounts.clear();
		this.Vehicles.clear();
		this.transitVehicles.clear();
		for(String timeBeanId:this.timeBean.keySet()) {
			linkCounts.put(timeBeanId,new ConcurrentHashMap<Id<Link>, Double>());
			Vehicles.put(timeBeanId, new ConcurrentHashMap<Id<Link>, List<Id<Vehicle>>>());
			for(Id<Link> linkId:simStorage.getLinksToCount()) {
				linkCounts.get(timeBeanId).put(linkId, 0.0);
				Vehicles.get(timeBeanId).put(linkId, Collections.synchronizedList(new ArrayList<Id<Vehicle>>()));
			}
		}
	}

	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		int time=(int) event.getTime();
		if(time>=86400) {time=86400;}
		String timeId=null;
		for(String s:this.timeBean.keySet()) {
			if(time>this.timeBean.get(s).getFirst() && time<=timeBean.get(s).getSecond()) {
				timeId=s;
			}
		}
		if(this.linkCounts.get(timeId).containsKey(event.getLinkId())){
				this.Vehicles.get(timeId).get(event.getLinkId()).add(event.getVehicleId());			
		}
	}
	
}
