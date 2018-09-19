package ust.hk.praisehk.metamodelcalibration.measurements;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;

public class MeasurementsWriter extends DefaultHandler{

	private final Measurements m;
	public MeasurementsWriter(Measurements m) {
		this.m=m;
	}

	public void write(String fileLoc) {
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

			Document document = documentBuilder.newDocument();

			Element rootEle = document.createElement("Measurements");

			Element TimeBeans=document.createElement("timeBeans");
			for(Entry<String, Tuple<Double, Double>>timeBean:m.getTimeBean().entrySet()) {
				Element TimeBean=document.createElement("timeBean");
				TimeBean.setAttribute("timeBeanId", timeBean.getKey());
				TimeBean.setAttribute("StartingTime", Double.toString(timeBean.getValue().getFirst()));
				TimeBean.setAttribute("EndingTime", Double.toString(timeBean.getValue().getSecond()));
				TimeBeans.appendChild(TimeBean);
			}
			rootEle.appendChild(TimeBeans);
			
			for(Measurement mm:m.getMeasurements().values()) {
				Element measurement=document.createElement("Measurement");
				measurement.setAttribute("MeasurementId", mm.getId().toString());
				if(mm.getCoord()!=null) {
					Element Coord=document.createElement("Coord");
					Coord.setAttribute("X", Double.toString(mm.getCoord().getX()));
					Coord.setAttribute("Y", Double.toString(mm.getCoord().getY()));
					measurement.appendChild(Coord);
				}
				Element Volumes=document.createElement("Volumes");
				for(Entry<String,Double>e:mm.getVolumes().entrySet()) {
					Element volume=document.createElement("Volume");
					volume.setAttribute("TimeBeanId", e.getKey());
					volume.setAttribute("PCUVolume", Double.toString(e.getValue()));
					Volumes.appendChild(volume);
				}
				measurement.appendChild(Volumes);

				Element linkIds=document.createElement("LinkIds");

				if(mm.getAttribute(mm.linkListAttributeName)==null) {
					Element lId=document.createElement("LinkId");
					lId.setAttribute("Id", mm.getId().toString());
					linkIds.appendChild(lId);
				}else {
					for(Id<Link>linkId:((ArrayList<Id<Link>>)mm.getAttribute(mm.linkListAttributeName))) {
						Element lId=document.createElement("LinkId");
						lId.setAttribute("Id", linkId.toString());
						linkIds.appendChild(lId);
					}
				}
				measurement.appendChild(linkIds);
				
				for(String s:mm.getAttributes().keySet()) {
					measurement.setAttribute(s, mm.getAttribute(s).toString());
				}
				
				rootEle.appendChild(measurement);
			}
			document.appendChild(rootEle);
			

			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			//tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "measurements.dtd");
			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			tr.transform(new DOMSource(document), new StreamResult(new FileOutputStream(fileLoc)));


		}catch(Exception e) {
			
		}

	}
}
