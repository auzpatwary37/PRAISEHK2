package ust.hk.praisehk.metamodelcalibration.measurements;

import java.util.ArrayList;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;

import dynamicTransitRouter.TransitStop;
import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;

public enum MeasurementType {
	
	
	/**
	 * this take care of the case if the measurement type is link volume
	 */
	linkVolume{
		@Override
		public void updateMeasurement(SUEModelOutput modelOut,AnalyticalModel sue,Object otherDataContainer,Measurement m) {
			Map<String,Map<Id<Link>,Double>>linkVolumes=modelOut.getLinkVolume();
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

		@Override
		public void writeAttribute(Element melelement,Measurement m) {
			//no attributes to write as link id list is written by default
		}

		@Override
		public void parseAttribute(Attributes atr,Measurement m) {
			// Nothing to parse as nothing to write
		}
		

	}
	,
	/**
	 * This takes care of the case if the measurement is link travel time 
	 */
	linkTravelTime
	{
		
		@Override
		public void updateMeasurement(SUEModelOutput modelOut,AnalyticalModel sue,Object otherDataContainer,Measurement m) {
			Map<String,Map<Id<Link>,Double>>linkTravelTime=modelOut.getLinkTravelTime();
			@SuppressWarnings("unchecked")
			ArrayList<Id<Link>> linkList=(ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName);
			if(linkList.isEmpty()) {
				System.out.println("MeasurementId: "+m.getId().toString()+" LinkList is empty!!! creating linkId from measurement ID");
				linkList.add(Id.createLinkId(m.getId().toString()));
			}
			if(m.getVolumes().size()==0) {
				System.out.println("MeasurementId: "+m.getId().toString()+" Volume is empty!!! Updating volume for all time beans");
				for(String s: m.getTimeBean().keySet()) {
					if(linkTravelTime.containsKey(s)) {
						m.getVolumes().put(s, 0.);
					}
				}
			}
			for(String s:m.getVolumes().keySet()) {
				double volume=0;
				Id<Link>linkId=linkList.get(0);
				try {
					if(linkTravelTime.get(s)==null) {
						throw new IllegalArgumentException("linkVolumes does not contain volume information");
					}
					if(linkTravelTime.get(s).get(linkId)==null) {
						throw new IllegalArgumentException("linkVolumes does not contain volume information");
					}
					volume=linkTravelTime.get(s).get(linkId);
				}catch(Exception e) {
					System.out.println("Illegal Argument Excepton. Could not update measurements. Volumes are missing for measurement Id: "+m.getId()+" timeBeanId: "
							+s+" linkId: "+linkId);
				}
				m.getVolumes().put(s, volume);
			}

		}

		@Override
		public void writeAttribute(Element melelement,Measurement m) {
			//no attributes to write as link id list is written by default
			
		}

		@Override
		public void parseAttribute(Attributes atr,Measurement m) {
			// Nothing to parse
			
		}
		
	}
	,
	
	smartCardEntry
	{
		
		@Override
		public void updateMeasurement(SUEModelOutput modelOut,AnalyticalModel sue,Object otherDataContainer,Measurement m) {
			
		}

		@Override
		public void writeAttribute(Element melement,Measurement m) {
			// Transit line id and route id will be written 
			melement.setAttribute("LineId", m.getAttribute(Measurement.transitLineAttributeName).toString());
			melement.setAttribute("RouteId", m.getAttribute(Measurement.transitRouteAttributeName).toString());
			melement.setAttribute("BoardingStop", m.getAttribute(Measurement.transitBoardingStopAtrributeName).toString());
		}

		@Override
		public void parseAttribute(Attributes atr,Measurement m) {
			// Transit line and route id will be read
			m.setAttribute(Measurement.transitLineAttributeName, Id.create(atr.getValue("LineId"),TransitLine.class));
			m.setAttribute(Measurement.transitRouteAttributeName, Id.create(atr.getValue("RouteId"),TransitRoute.class));
			m.setAttribute(Measurement.transitBoardingStopAtrributeName, atr.getValue("BoardingStop"));
		}
	},
	
	//sum of volume of all direct links with same transit boarding and alighting stops 
	
	smartCardEntryAndExit
	{
		@Override
		public void updateMeasurement(SUEModelOutput modelOut,AnalyticalModel sue,Object otherDataContainer,Measurement m) {
		
		}

		@Override
		public void writeAttribute(Element melement,Measurement m) {
			// TransitBoarding and alighting stop will be written
			melement.setAttribute("BoardingStop", m.getAttribute(Measurement.transitBoardingStopAtrributeName).toString());
			melement.setAttribute("AlightingStop", m.getAttribute(Measurement.transitAlightingStopAttributeName).toString());
			melement.setAttribute("Mode", m.getAttribute(Measurement.transitModeAttributeName).toString());
			
			if(!m.getAttribute(Measurement.transitModeAttributeName).toString().equals("train")) {
				melement.setAttribute("LineId", m.getAttribute(Measurement.transitLineAttributeName).toString());
				melement.setAttribute("RouteId", m.getAttribute(Measurement.transitRouteAttributeName).toString());
			}
		}

		@Override
		public void parseAttribute(Attributes atr,Measurement m) {
			// TransitBoarding and alighting stop will be written
			m.setAttribute(Measurement.transitBoardingStopAtrributeName, atr.getValue("BoardingStop"));
			m.setAttribute(Measurement.transitAlightingStopAttributeName, atr.getValue("AlightingStop"));
			m.setAttribute(Measurement.transitModeAttributeName, atr.getValue("Mode"));
			if(!atr.getValue("Mode").equals("train")) {
				m.setAttribute(Measurement.transitLineAttributeName, atr.getValue("LineId"));
				m.setAttribute(Measurement.transitRouteAttributeName, atr.getValue("RouteId"));
			}
		}
	},
	
	//This is the ratio of vehicle capacity to passenger on average for all transit line and route in a certain physical link 
	//This should be extracted from the total number of passengersin transit vehicle in the physical link devided by the total capacity of the vehicle??
	
	averagePTOccumpancy


	{
		@Override
		public void updateMeasurement(SUEModelOutput modelOut,AnalyticalModel sue,Object otherDataContainer,Measurement m) {
			Id<Link>linkId=((ArrayList<Id<Link>>)m.getAttribute(Measurement.linkListAttributeName)).get(0);
			for(String s:m.getVolumes().keySet()) {
				m.putVolume(s, modelOut.getAveragePtOccupancyOnLink().get(s).get(linkId));
			}

		}

		@Override
		public void writeAttribute(Element melelement,Measurement m) {
			//no attributes to write as link id list is written by default
			
		}

		@Override
		public void parseAttribute(Attributes atr,Measurement m) {
			// Nothing to parse
			
		}
	},
	
	
	fareLinkVolume
	{

		@Override
		public void updateMeasurement(SUEModelOutput modelOut, AnalyticalModel sue, Object otherDataContainer,
				Measurement m) {
			FareLink fl;
			if((fl = (FareLink)m.getAttribute(Measurement.FareLinkAttributeName))==null) {
				System.out.println("No Fare Link present in the attributes. Creating from measurement Id");
				m.setAttribute(Measurement.FareLinkAttributeName, new FareLink(m.getId().toString()));
			}
			if(m.getVolumes().size()==0) {
				System.out.println("MeasurementId: "+m.getId().toString()+" Volume is empty!!! Updating volume for all time beans");
				for(String s: m.getTimeBean().keySet()) {
					if(modelOut.getFareLinkVolume().containsKey(s)) {
						m.getVolumes().put(s, 0.);
					}
				}
			}
			String key =m.getAttribute(Measurement.FareLinkAttributeName).toString();
			Map<String,Map<String,Double>>fareLinkVolume = modelOut.getFareLinkVolume();
			
			for(String s:m.getVolumes().keySet()) {
				double volume=0;
				try {
					if(fareLinkVolume.get(s)==null) {
						throw new IllegalArgumentException("linkVolumes does not contain volume information");
					}
					if(fareLinkVolume.get(s).get(key)==null) {
						throw new IllegalArgumentException("linkVolumes does not contain volume information");
					}
					volume+=modelOut.getFareLinkVolume().get(s).get(key);
				}catch(Exception e) {
					System.out.println("Illegal Argument Excepton. Could not update measurements. Volumes are missing for measurement Id: "+m.getId()+" timeBeanId: "
							+s+" fareLinkId: "+key);
				}
				m.getVolumes().put(s, volume);
				}
				
			}
		

		@Override
		public void writeAttribute(Element melelement, Measurement m) {
			m.setAttribute(Measurement.FareLinkAttributeName, m.getAttribute(Measurement.FareLinkAttributeName).toString());
			
		}

		@Override
		public void parseAttribute(Attributes atr, Measurement m) {
			m.setAttribute(Measurement.FareLinkAttributeName, new FareLink(atr.getValue(Measurement.FareLinkAttributeName)));
		}
		
	};	
	/**
	 * !!!!USE WITH CAUTION!!!
	 * model out only includes the link and transit link volumes and travel times. So, only link Volume and Travel time is currently auto updatable. Does not work for the rest 
	 * @param modelOut
	 * @param sue
	 * @param otherDataContainer
	 * @param m
	 */
	public abstract void updateMeasurement(SUEModelOutput modelOut,AnalyticalModel sue,Object otherDataContainer,Measurement m);
	public abstract void writeAttribute(Element melelement,Measurement m);
	public abstract void parseAttribute(Attributes atr,Measurement m);
}
