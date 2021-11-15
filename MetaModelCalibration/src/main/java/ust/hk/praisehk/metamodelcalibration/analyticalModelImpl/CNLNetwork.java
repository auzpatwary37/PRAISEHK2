package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.VehicleType;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SignalFlowReductionGenerator;



/**
 * 
 * @author Ashraf
 *
 */
public class CNLNetwork extends AnalyticalModelNetwork{

	/**
	 * constructor same as SUE Network
	 */
	public CNLNetwork(Network network){
		
		for(Id<Node> NodeId:network.getNodes().keySet()){
			
			this.network.addNode(cloneNode(network.getNodes().get(NodeId),network.getFactory()));
		}
		for(Id<Link> linkId:network.getLinks().keySet()){
			this.network.addLink(new CNLLink(network.getLinks().get(linkId),this.network));
		}
		
	}
	
	public CNLNetwork(Network network,SignalFlowReductionGenerator sg){
		
		for(Id<Node> NodeId:network.getNodes().keySet()){
			
			this.network.addNode(cloneNode(network.getNodes().get(NodeId),network.getFactory()));
		}
		for(Id<Link> linkId:network.getLinks().keySet()){
			AnalyticalModelLink link=new CNLLink(network.getLinks().get(linkId),this.network);
			if(sg!=null) {
				double[] a = sg.getGCratio(network.getLinks().get(linkId));
				link.setGcRatio(a[0]);
			}
			this.network.addLink(link);
		}
		
	}
	
	
	public Map<Id<Link>, CNLLink> getCNLLinks(){
		return (Map<Id<Link>,CNLLink>)this.network.getLinks();
	}
	
	
	@Override
	public CNLNetwork createNetwork(Network network) {
		CNLNetwork newNetwork=new CNLNetwork(network);
		return newNetwork;
	}


	@Override
	public void clearLinkVolumesfull() {
		for(CNLLink link:this.getCNLLinks().values()) {
			link.clearLinkCarFlow();
			link.clearTransitPassangerFlow();
		}
	}

	@Override
	public void clearLinkCarVolumes() {
		for(CNLLink link:this.getCNLLinks().values()) {
			link.clearLinkCarFlow();
		}
		
	}

	@Override
	public void clearLinkTransitPassangerVolume() {
		for(CNLLink cl:this.getLinks().values()) {
			cl.clearTransitPassangerFlow();
		}
	}
	
	public void clearLinkCarVolume() {
		for(CNLLink cl:this.getLinks().values()) {
			cl.clearLinkCarFlow();
		}
	}
	@Override
	public void clearLinkNANVolumes() {
		for(CNLLink cl:this.getLinks().values()) {
			cl.clearNANFlow();
		}
	}

	@Override
	public Map<Id<Link>, CNLLink> getLinks() {		
		return (Map<Id<Link>,CNLLink>)this.network.getLinks();
	}


	@Override
	public void addLinkTransitVolume(TransitSchedule ts) {
		// TODO Auto-generated method stub
		throw new IllegalArgumentException("Not active function!!!");
	}
	
	@Override
	public void clearVehicleSpecificVolumeExceptTransit() {
	
		for(CNLLink link:this.getLinks().values()) {
			link.resetVehicleSpecificVolumeExceptTransit();
		}
		
	}

	@Override
	public Map<Id<Link>, Map<Id<VehicleType>, Double>> generateLinkVolumeProfile() {
		Map<Id<Link>,Map<Id<VehicleType>,Double>> profileMap = new HashMap<>();
		for(CNLLink link:this.getLinks().values()) {
			profileMap.put(link.getId(),link.getVehicleSpecificVolume());
		}
		return profileMap;
	}

}
