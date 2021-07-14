package ust.hk.praisehk.metamodelcalibration.measurements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;


public class MeasurementsReader extends DefaultHandler {
	
	private Measurements m=null;
	private Map<String,Tuple<Double,Double>> timeBeans=null;
	private Id<Measurement>mId;
	private ArrayList<Id<Link>>linkIds;
	
	@Override 
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		
		if(qName.equalsIgnoreCase("timeBeans")) {
			timeBeans=new HashMap<>();
		}
		
		if(qName.equalsIgnoreCase("timeBean")) {
			this.timeBeans.put(attributes.getValue("timeBeanId"),new Tuple<Double,Double>(Double.parseDouble(attributes.getValue("StartingTime")),Double.parseDouble(attributes.getValue("EndingTime"))));
		}
		
		if(qName.equalsIgnoreCase("Measurement")) {
			if(this.m==null) {
				this.m=Measurements.createMeasurements(this.timeBeans);
			}
			this.mId=Id.create(attributes.getValue("MeasurementId"), Measurement.class);
			MeasurementType mType;
			if(attributes.getValue("MeasurementType")!=null) {
				mType=MeasurementType.valueOf(attributes.getValue("MeasurementType"));
			}else {
				mType=MeasurementType.linkVolume;
			}
			this.m.createAnadAddMeasurement(mId.toString(),mType);
			mType.parseAttribute(attributes, this.m.getMeasurements().get(this.mId));
			////This piece of code is useless 
//			for(int i=0;i<attributes.getLength();i++) {
//				String attrName = attributes.getQName(i);
//                String attrVal = attributes.getValue(i);
//                if(!attrName.equals(Measurement.linkListAttributeName)&& !attrName.equals(Measurement.FareLinkAttributeName) && !attrName.equals("MeasurementId") && !attrName.equals("MeasurementType")) {
//                	this.m.getMeasurements().get(this.mId).setAttribute(attrName, attrVal);
//                }
//			}
			///// Have to see if this is even needed
		}
		
		if(qName.equalsIgnoreCase("Coord")) {
			Coord coord=new Coord(Double.parseDouble(attributes.getValue("X")),Double.parseDouble(attributes.getValue("Y")));
			this.m.getMeasurements().get(this.mId).setCoord(coord);
		}
		
		if(qName.equalsIgnoreCase("linkIds")) {
			linkIds=new ArrayList<>();
		}
		
		if(qName.equalsIgnoreCase("linkId")) {
			String id = attributes.getValue("Id");
			if(id.contains("\"")) {
				id = id.replace("\"", "");
			}
			linkIds.add(Id.createLinkId(id));
		}
		
		if(qName.equalsIgnoreCase("Volume")) {
			this.m.getMeasurements().get(this.mId).putVolume(attributes.getValue("TimeBeanId"), Double.parseDouble(attributes.getValue("PCUVolume")));
		}
		
	}
	
	@Override 
	public void endElement(String uri, String localName, String qName) {
		
		if(qName.equalsIgnoreCase("linkIds")) {
			this.m.getMeasurements().get(this.mId).setAttribute(Measurement.linkListAttributeName, linkIds);
		}
	}
	
	public Measurements readMeasurements(String fileLoc) {
		
		try {
			SAXParserFactory.newInstance().newSAXParser().parse(fileLoc,this);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
		return this.m;
	}

	
}
