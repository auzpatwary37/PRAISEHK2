package createMTR;

import java.io.FileInputStream;
import java.io.InputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

@Deprecated
public class RunMTRConfig {
	public static void main(String argv[]) {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			InputStream xmlInput = new FileInputStream("data/lineSettings.xml");
			SAXParser saxParser = factory.newSAXParser();
			MTRconfigReader handler = new MTRconfigReader();
			saxParser.parse(xmlInput, handler);
			System.out.println(MTRconfigReader.getFirstTrain(new Direction("ADM", "SOH")));
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
