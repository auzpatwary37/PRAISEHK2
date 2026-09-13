package networkFromTDData;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class RunInOutProcessor extends DefaultHandler {
	List<Id<Link>> linkToIgnore = new ArrayList<>();
	
	private boolean rd_id;
	
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if(qName.equals("fme:RD_ID_1")) {
			rd_id = true;
		}
	}
	
	public void endElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if(qName.equals("fme:RD_ID_1")) {
			rd_id = false;
		}
	}
	
	public void characters(char ch[], int start, int length) throws SAXException{
		if(rd_id) {
			linkToIgnore.add(Id.createLinkId(new String(ch, start, length)));
		}
	}
}
