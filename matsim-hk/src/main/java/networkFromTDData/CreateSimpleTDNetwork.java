package networkFromTDData;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.xml.sax.SAXException;

import com.google.common.collect.Lists;

public class CreateSimpleTDNetwork {
	
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		Network network = NetworkUtils.createNetwork();
		InputStream intersectionXML = new FileInputStream("TD_map_Nov2018/INTERSECTION.gml");
		SAXParser saxParser = factory.newSAXParser();
		IntersectionProcessor intersectionHandler = new IntersectionProcessor(network);
		saxParser.parse(intersectionXML, intersectionHandler);
		intersectionXML.close();
		
//		InputStream runInOutXML = new FileInputStream("TD_map/RUNINOUT.gml");
//		RunInOutProcessor runInOutHandler = new RunInOutProcessor();
//		saxParser.parse(runInOutXML, runInOutHandler);
		
		InputStream centreLineXML = new FileInputStream("TD_map_Nov2018/CENTRELINE.gml");
		LinkProcessor linkHandler = new LinkProcessor(network, intersectionHandler.getLinkToNode(), 
														Lists.newArrayList(Id.createLinkId("164873"), Id.createLinkId("164871")));
		saxParser.parse(centreLineXML, linkHandler);
		
		new NetworkWriter(network).writeFileV2("TD_map_Nov2018/network.xml");
	}
}
