package ust.hk.praisehk.metamodelcalibration.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;


public class LinkVehicleCompReader extends DefaultHandler {
	
	
	private Map<String,Tuple<Double,Double>> timeBeans=new HashMap<>();
	private Map<String,Map<Id<Link>,Map<String,Double>>> vehicleSpecificCount = new HashMap<>();
	private Map<Id<Link>,Map<String,Double>> currentTimeBeanVolumeMap = null;
	private Map<String,Double> currentTimeBeanLinkVolumeMap = null;
	private Map<String,Map<Id<Link>,Double>> averageLinkSpeed = new HashMap<>();
	private Map<Id<Link>,Double>currentTimeBeanSpeedMap = null;
	
	
	@Override 
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		
		if(qName.equalsIgnoreCase("timeBean")) {
			this.timeBeans.put(attributes.getValue("timeBeanId"),new Tuple<Double,Double>(Double.parseDouble(attributes.getValue("StartingTime")),Double.parseDouble(attributes.getValue("EndingTime"))));
		}
		
		if(qName.equalsIgnoreCase("LinkCompositionsPerTimSlot")) {
			String timeBean = attributes.getValue("timeId");
			this.vehicleSpecificCount.put(timeBean, new HashMap<>());
			this.currentTimeBeanVolumeMap = this.vehicleSpecificCount.get(timeBean);
			this.averageLinkSpeed.put(timeBean, new HashMap<>());
			this.currentTimeBeanSpeedMap = this.averageLinkSpeed.get(timeBean);
		}
		
		if(qName.equalsIgnoreCase("LinkDetails")) {
			Id<Link> linkId = Id.createLinkId(attributes.getValue("LinkId"));
			this.currentTimeBeanVolumeMap.put(linkId, new HashMap<>());
			this.currentTimeBeanLinkVolumeMap = this.currentTimeBeanVolumeMap.get(linkId);
			this.currentTimeBeanSpeedMap.put(linkId, Double.parseDouble(attributes.getValue("AverageSpeed")));
		}
		
		if(qName.equalsIgnoreCase("VehicleComposition")) {
			String vehicleType = attributes.getValue("VehicleType");
			Double volume = Double.parseDouble(attributes.getValue("Volume"));
			this.currentTimeBeanLinkVolumeMap.put(vehicleType, volume);
		}
	}
	
	@Override 
	public void endElement(String uri, String localName, String qName) {
	}
	
	/**
	 * Should be able to read multiple files
	 * @param fileLoc
	 */
	public void readAndStoreLinkVehicleComp(String fileLoc) {
		try {
			SAXParserFactory.newInstance().newSAXParser().parse(fileLoc,this);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
	}

	public Map<String, Tuple<Double, Double>> getTimeBeans() {
		if(this.timeBeans.size()==0) {
			throw new IllegalArgumentException("No file have been read yet");
		}
		return timeBeans;
	}

	public Map<String, Map<Id<Link>, Map<String, Double>>> getVehicleSpecificCount() {
		if(this.vehicleSpecificCount == null) {
			throw new IllegalArgumentException("No file have been read yet");
		}
		return vehicleSpecificCount;
	}

	public Map<String, Map<Id<Link>, Double>> getAverageLinkSpeed() {
		if(this.averageLinkSpeed == null) {
			throw new IllegalArgumentException("No file have been read yet");
		}
		return averageLinkSpeed;
	}

	public static void main(String[] args) throws IOException {
		LinkVehicleCompReader reader = new LinkVehicleCompReader();
		reader.readAndStoreLinkVehicleComp("temp/LinkComposition_final0.xml");
		reader.readAndStoreLinkVehicleComp("temp/LinkComposition_final1.xml");
		reader.readAndStoreLinkVehicleComp("temp/LinkComposition_final2.xml");
		reader.readAndStoreLinkVehicleComp("temp/LinkComposition_final3.xml");
		Map<String,Map<Id<Link>,Map<String,Double>>> linkComp = reader.getVehicleSpecificCount();
		
		//From here we will create the weights
		
		//trying all time steps and all vehicles first
		Map<Id<Link>,Double> linkVolume = new HashMap<>();
		for(Entry<String, Map<Id<Link>, Map<String, Double>>> timeBeanMap:linkComp.entrySet()) {
			for(Entry<Id<Link>, Map<String, Double>> linkCompMap:timeBeanMap.getValue().entrySet()) {
				for(Entry<String, Double> vehicleComp:linkCompMap.getValue().entrySet()) {
					linkVolume.compute(linkCompMap.getKey(), (k,v)->(v==null)?vehicleComp.getValue():v+vehicleComp.getValue());
				}
			}
		}
		//------------------------------------------------------------------------
		//System.out.println(reader.getTimeBeans());
		Network network = NetworkUtils.readNetwork("temp/network.xml");
		FileWriter nodeWriter = new FileWriter(new File("temp/network_nodes.csv"));
		nodeWriter.append("NodeId,x,y\n");
		network.getNodes().entrySet().forEach(n->{
			try {
				nodeWriter.append(n.getKey().toString()+","+n.getValue().getCoord().getX()+","+n.getValue().getCoord().getY()+"\n");
				nodeWriter.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		FileWriter linkWriter = new FileWriter(new File("temp/network_links.csv"));
		linkWriter.append("LinkId,FromNode,ToNode,weight\n");
		network.getLinks().entrySet().forEach(l->{
			try {
				double volume = 0;
				if(linkVolume.get(l.getKey())!=null)volume = linkVolume.get(l.getKey());
				//double cap = l.getValue().getCapacity()*linkComp.size();
				linkWriter.append(l.getKey().toString()+","+l.getValue().getFromNode().getId().toString()+","+l.getValue().getToNode().getId().toString()+","+volume+"\n");
				linkWriter.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		
		
		
		//Plotting subroutine
		String command = "temp/plotting.py";
		
		ProcessBuilder process = new ProcessBuilder(Arrays.asList("/home/ashraf/anaconda3/bin/python", command));
		
		Process p = process.start();
		
		BufferedReader treader = new BufferedReader(new InputStreamReader(p.getInputStream()));
		StringBuilder builder = new StringBuilder();
		String line = null;
		while ( (line = treader.readLine()) != null) {
			builder.append(line);
			builder.append(System.getProperty("line.separator"));
		}
		String result = builder.toString();
		System.out.println(result);
		System.out.println("done");
	}
	
}
