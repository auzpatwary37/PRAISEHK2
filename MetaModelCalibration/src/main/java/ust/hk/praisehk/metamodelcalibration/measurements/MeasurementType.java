package ust.hk.praisehk.metamodelcalibration.measurements;

import java.util.ArrayList;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;

public enum MeasurementType {
	
	/**
	 * this take care of the case if the measurement type is link volume
	 */
	linkVolume{
		@Override
		public void updateMeasurement(Map<String,Map<Id<Link>,Double>> linkVolumes,Map<String,Map<Id<TransitLink>,Double>>linkTransitVolume,AnalyticalModel sue,Object otherDataContainer,Measurement m) {
			@SuppressWarnings("unchecked")
			ArrayList<Id<Link>> linkList=(ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName);
			if(linkList.isEmpty()) {
				System.out.println("MeasurementId: "+m.getId().toString()+" LinkList is empty!!! creating linkId from measurement ID");
				linkList.add(Id.createLinkId(m.getId().toString()));
			}
			if(m.getVolumes().size()==0) {
				System.out.println("MeasurementId: "+m.getId().toString()+" Volume is empty!!! Updating volume for all time beans");
				for(String s: m.getTimeBean().keySet()) {
					if(linkVolumes.containsKey(s)) {
						m.getVolumes().put(s, 0.);
					}
				}
			}
			for(String s:m.getVolumes().keySet()) {
				double volume=0;
				for(Id<Link>linkId:linkList) {
					try {
						if(linkVolumes.get(s)==null) {
							throw new IllegalArgumentException("linkVolumes does not contain volume information");
						}
						if(linkVolumes.get(s).get(linkId)==null) {
							throw new IllegalArgumentException("linkVolumes does not contain volume information");
						}
						volume+=linkVolumes.get(s).get(linkId);
					}catch(Exception e) {
						System.out.println("Illegal Argument Excepton. Could not update measurements. Volumes are missing for measurement Id: "+m.getId()+" timeBeanId: "
								+s+" linkId: "+linkId);
					}
					
				}
				m.getVolumes().put(s, volume);
			}
		}

	}
	,
	/**
	 * This takes care of the case if the measurement is link travel time 
	 */
	linkTravelTime
	{
		@Override
		public void updateMeasurement(Map<String,Map<Id<Link>,Double>> linkVolumes,Map<String,Map<Id<TransitLink>,Double>>linkTransitVolume,AnalyticalModel sue,Object otherDataContainer,Measurement m) {
			@SuppressWarnings("unchecked")
			ArrayList<Id<Link>> linkList=(ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName);
			if(linkList.isEmpty()) {
				System.out.println("MeasurementId: "+m.getId().toString()+" LinkList is empty!!! creating linkId from measurement ID");
				linkList.add(Id.createLinkId(m.getId().toString()));
			}
			if(m.getVolumes().size()==0) {
				System.out.println("MeasurementId: "+m.getId().toString()+" Volume is empty!!! Updating volume for all time beans");
				for(String s: m.getTimeBean().keySet()) {
					if(linkVolumes.containsKey(s)) {
						m.getVolumes().put(s, 0.);
					}
				}
			}
			Map<String,AnalyticalModelNetwork> networks=sue.getNetworks();
			for(String s:m.getVolumes().keySet()) {
				double volume=0;
				Id<Link>linkId=linkList.get(0);
				
				try {
					volume=((CNLLink)networks.get(s).getLinks().get(linkId)).getLinkTravelTime(sue.getTimeBeans(), sue.get, anaParams);
				}catch(Exception e) {
					System.out.println("Illegal Argument Excepton. Could not update measurements. Volumes are missing for measurement Id: "+m.getId()+" timeBeanId: "
							+s+" linkId: "+linkId);
				}
					
				
				m.getVolumes().put(s, volume);
			}
		}
	}
	,
	
	smartCardEntry
	{
		@Override
		public void updateMeasurement(Map<String,Map<Id<Link>,Double>> linkVolumes,Map<String,Map<Id<TransitLink>,Double>>linkTransitVolume,AnalyticalModel sue,Object otherDataContainer,Measurement m) {
			
		}
	},
	
	//sum of volume of all direct links with same transit boarding and alighting stops 
	
	smartCardEntryAndExit
	{
		@Override
		public void updateMeasurement(Map<String,Map<Id<Link>,Double>> linkVolumes,Map<String,Map<Id<TransitLink>,Double>>linkTransitVolume,AnalyticalModel sue,Object otherDataContainer,Measurement m) {
			// TODO Auto-generated method stub

		}
	},
	
	//This is the ratio of vehicle capacity to passenger on average for all transit line and route in a certain physical link 
	//This should be extracted from the total number of passengersin transit vehicle in the physical link devided by the total capacity of the vehicle??
	
	averagePTOccumpancy


	{
		@Override
		public void updateMeasurement(Map<String,Map<Id<Link>,Double>> linkVolumes,Map<String,Map<Id<TransitLink>,Double>>linkTransitVolume,AnalyticalModel sue,Object otherDataContainer,Measurement m) {
			// TODO Auto-generated method stub

		}
	};	
	
	public abstract void updateMeasurement(Map<String,Map<Id<Link>,Double>> linkVolumes,Map<String,Map<Id<TransitLink>,Double>>linkTransitVolume,AnalyticalModel sue,Object otherDataContainer,Measurement m);
}
