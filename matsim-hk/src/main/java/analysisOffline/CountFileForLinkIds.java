package analysisOffline;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsReaderMatsimV1;
import org.matsim.counts.CountsWriter;
import org.matsim.vehicles.MatsimVehicleReader;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

public class CountFileForLinkIds {
	public static void main(String[] args) throws IOException {
		String fileDir = "C:\\Users\\eleead\\Desktop\\Simulation results\\new withTollAdjustment\\";
		String eventFilesLoc = fileDir + "output_events.xml.gz";
		
		EventsManager em=EventsUtils.createEventsManager();
		MatsimEventsReader eventsReader=new MatsimEventsReader(em);
		
		Network network = NetworkUtils.createNetwork();//Put in the network
		new MatsimNetworkReader(network).readFile(fileDir+"output_network.xml.gz");
		Vehicles vehicles = VehicleUtils.createVehiclesContainer();
		new MatsimVehicleReader(vehicles).readFile(fileDir+"output_vehicles.xml.gz");
		Vehicles transitVehicles = VehicleUtils.createVehiclesContainer();
		new MatsimVehicleReader(transitVehicles).readFile(fileDir+"output_transitVehicles.xml.gz");
		PCUVolumeAnalyzer pcuanalyzer=new PCUVolumeAnalyzer(network, vehicles, transitVehicles, em, 15 * 60);
		eventsReader.readFile(eventFilesLoc); //Read the file
		
//		Counts<Link> counts = new Counts<Link>(); //Get the count from the old count file
//		new CountsReaderMatsimV1(counts).readFile("output/count.txt");
		
		List<Tuple<Id<Link>, String>> IdList = new ArrayList<>();
		IdList.add(new Tuple(Id.createLinkId("506520_209710"), "EHC_S"));
		IdList.add(new Tuple(Id.createLinkId("209709_506521"), "EHC_N"));
		IdList.add(new Tuple(Id.createLinkId("101375_501226"), "CHT_N"));
		IdList.add(new Tuple(Id.createLinkId("501226_101375"), "CHT_S"));
		IdList.add(new Tuple(Id.createLinkId("101642_401851"), "WHC_N"));
		IdList.add(new Tuple(Id.createLinkId("401851_101642"), "WHC_S"));
		
		Counts<Link> newCounts=new Counts<Link>(); //Get the new count file
		for(Tuple<Id<Link>, String> linkIdAndName: IdList) { //Iterate through each count
			Id<Link> linkId =linkIdAndName.getFirst();
			double[] pcuVolumes = pcuanalyzer.getPCUVolumesForLink(linkId);
			if(pcuVolumes != null) {
				newCounts.createAndAddCount(linkId, linkIdAndName.getSecond());
				for(int timeBin = 0 ; timeBin < pcuanalyzer.maxSlotIndex+1; timeBin++) {
					newCounts.getCount(linkId).createVolume(timeBin==0?pcuanalyzer.maxSlotIndex+1:timeBin, pcuVolumes[timeBin]);
				}
			}
		}
		//new CountsWriter(counts).write("output/count_orig.txt");
		new CountsWriter(newCounts).write("output/count_higherDemand3.txt");
		
		//Write the CSV to compare the count.
//		BufferedWriter writer = new BufferedWriter(new FileWriter("output/count_compare.csv"));
//		for(Id<Link> countLinkId: counts.getCounts().keySet()) {
//			Count<Link> count = counts.getCounts().get(countLinkId);
//			Count<Link> runCount = newCounts.getCounts().get(countLinkId);
//			if(runCount!=null) {
//				for(int hour : count.getVolumes().keySet()) {
//					if(!runCount.getVolumes().isEmpty()) {
//						writer.write(countLinkId.toString()+","+count.getCsLabel()+","+hour+","+count.getVolume(hour).getValue()+","+
//									runCount.getVolume(hour).getValue()+"\n");
//					}
//				}
//			}
//		}
//		writer.close();
	}
}
