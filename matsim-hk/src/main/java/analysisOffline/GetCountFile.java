package analysisOffline;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.counts.Count;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsReaderMatsimV1;
import org.matsim.counts.CountsWriter;
import org.matsim.vehicles.MatsimVehicleReader;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import analysisOffline.PCUVolumeAnalyzer;


public class GetCountFile {
	public static void main(String[] args) throws IOException {
		String fileDir = "C:\\Users\\eleead\\Desktop\\Simulation results\\new withoutTollAdjustment\\";
		String eventFilesLoc = fileDir + "output_events.xml.gz";
		
		EventsManager em=EventsUtils.createEventsManager();
		MatsimEventsReader eventsReader=new MatsimEventsReader(em);
		
		Network network = NetworkUtils.createNetwork();//Put in the network
		new MatsimNetworkReader(network).readFile(fileDir+"output_network.xml.gz");
		Vehicles vehicles = VehicleUtils.createVehiclesContainer();
		new MatsimVehicleReader.VehicleReader(vehicles).readFile(fileDir+"output_vehicles.xml.gz");
		Vehicles transitVehicles = VehicleUtils.createVehiclesContainer();
		new MatsimVehicleReader.VehicleReader(transitVehicles).readFile(fileDir+"output_transitVehicles.xml.gz");
		PCUVolumeAnalyzer pcuanalyzer=new PCUVolumeAnalyzer(network, vehicles, transitVehicles, em, 15 * 60);
		eventsReader.readFile(eventFilesLoc); //Read the file
		
		Counts<Link> counts = new Counts<Link>(); //Get the count from the old count file
		new CountsReaderMatsimV1(counts).readFile("output/count_higherDemand3.txt");
		
		Counts<Link> newCounts=new Counts<Link>(); //Get the new count file
		for(Count<Link> count: counts.getCounts().values()) { //Iterate through each count
			Id<Link> linkId = count.getId();
			double[] pcuVolumes = pcuanalyzer.getPCUVolumesForLink(linkId);
			if(pcuVolumes != null) {
				newCounts.createAndAddCount(linkId, count.getCsLabel());
				for(int hour : count.getVolumes().keySet()) {
					newCounts.getCount(linkId).createVolume(hour, pcuVolumes[hour==97?0:hour]);
				}
			}
		}
		//new CountsWriter(counts).write("output/count_orig.txt");
		new CountsWriter(newCounts).write("output/count_new3.txt");
		
		//Write the CSV to compare the count.
		BufferedWriter writer = new BufferedWriter(new FileWriter("output/count_compare3.csv"));
		for(Id<Link> countLinkId: counts.getCounts().keySet()) {
			Count<Link> count = counts.getCounts().get(countLinkId);
			Count<Link> runCount = newCounts.getCounts().get(countLinkId);
			if(runCount!=null) {
				for(int hour : count.getVolumes().keySet()) {
					if(!runCount.getVolumes().isEmpty()) {
						writer.write(countLinkId.toString()+","+count.getCsLabel()+","+hour+","+count.getVolume(hour).getValue()+","+
									runCount.getVolume(hour).getValue()+"\n");
					}
				}
			}
		}
		writer.close();
	}
}