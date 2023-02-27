package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

public class SUEModelOutput {
	
	private Map<String,Map<Id<Link>,Double>> linkVolume;
	private Map<String,Map<Id<TransitLink>,Double>> linkTransitVolume;
	private Map<String,Map<Id<Link>,Map<String,Double>>> trainCount;
	private Map<String,Map<String,Double>> trainTransfers;
	
	
	private Map<String,Map<Id<Link>,Double>> linkTravelTime;
	private Map<String,Map<Id<TransitLink>,Double>>trLinkTravelTime;
	
	private Map<String,Map<Id<Link>,Double>> averagePtOccupancyOnLink;
	
	private Map<String,Double> MaaSPackageUsage;
	
	private List<String> variables;
	
	private Map<String,Map<Id<Link>,Double>> linkFlowWithoutPT = null;
	
	private Map<String,Map<Id<TransitLink>,Double>> transitDirectLinkTT = null;
	private Map<String,Map<Id<TransitLink>,Double>> transitTransferLinkTT = null;
	
	private Map<String,Map<Id<Link>,double[]>> linkVolumeGrad;
	private Map<String,Map<Id<TransitLink>,double[]>> linkTransitVolumeGrad;
	private Map<String,Map<Id<Link>,Map<String,double[]>>> trainCountGrad;
	private Map<String,Map<String,double[]>> trainTransfersGrad;
	
	
	private Map<String,Map<Id<Link>,double[]>> linkTravelTimeGrad;
	private Map<String,Map<Id<TransitLink>,double[]>>trLinkTravelTimeGrad;
	
	private Map<String,Map<Id<Link>,double[]>> averagePtOccupancyOnLinkGrad;
	
	private Map<String,double[]> MaaSPackageUsageGrad;
	
	private Map<String,Map<Id<Link>,double[]>> linkFlowWithoutPTGrad = null;
	
	private Map<String,Map<Id<TransitLink>,double[]>> transitDirectLinkTTGrad = null;
	private Map<String,Map<Id<TransitLink>,double[]>> transitTransferLinkTTGrad = null;
	
	
	private double autoFlow = 0;
	private double transitFlow = 0;
	private double activities = 0;
	
	
	private Map<String,Map<String,Map<String,Double>>> MaaSSpecificFareLinkFlow;
	private Map<String,Map<String,Map<String,double[]>>> MaaSSpecificFareLinkFlowGrad;
	
	//private Map<String,Map<Id<TransitStopFacility>,Double>> smartCardEntry;
	
	private Map<String,Map<String,Double>>FareLinkVolume;
	
	private Map<String,Map<String,double[]>>FareLinkVolumeGrad;
	
	public SUEModelOutput(Map<String,Map<Id<Link>,Double>> linkVolume,Map<String,Map<Id<TransitLink>,Double>> linkTransitVolume,Map<String,Map<Id<Link>,Double>> linkTravelTime,Map<String,Map<Id<TransitLink>,Double>>trLinkTravelTime, Map<String,Map<String,Double>> fareLinkVolume) {
		this.linkVolume=linkVolume;
		this.linkTransitVolume=linkTransitVolume;
		this.linkTravelTime=linkTravelTime;
		this.trLinkTravelTime=trLinkTravelTime;
		this.FareLinkVolume = fareLinkVolume;
	}

	public Map<String, Map<Id<Link>, Double>> getLinkVolume() {
		return linkVolume;
	}

	public Map<String, Map<Id<TransitLink>, Double>> getLinkTransitVolume() {
		return linkTransitVolume;
	}

	public Map<String, Map<Id<Link>, Double>> getLinkTravelTime() {
		return linkTravelTime;
	}
	
	public Map<String, Map<Id<Link>, Map<String, Double>>> getTrainCount() {
		return trainCount;
	}

	public void setTrainCount(Map<String, Map<Id<Link>, Map<String, Double>>> trainCount) {
		this.trainCount = trainCount;
	}

	public Map<String, Map<Id<TransitLink>, Double>> getTrLinkTravelTime() {
		return trLinkTravelTime;
	}

	public Map<String, Map<String, Double>> getTrainTransfers() {
		return trainTransfers;
	}

	public void setTrainTransfers(Map<String, Map<String, Double>> trainTransfers) {
		this.trainTransfers = trainTransfers;
	}

	public Map<String, Map<Id<Link>, Double>> getAveragePtOccupancyOnLink() {
		return averagePtOccupancyOnLink;
	}

	public void setAveragePtOccupancyOnLink(Map<String, Map<Id<Link>, Double>> averagePtOccupancyOnLink) {
		this.averagePtOccupancyOnLink = averagePtOccupancyOnLink;
	}

	public Map<String, Map<String, Double>> getFareLinkVolume() {
		return FareLinkVolume;
	}

	public Map<String, Map<String, double[]>> getFareLinkVolumeGradient() {
		return FareLinkVolumeGrad;
	}
	public Map<String, Double> getMaaSPackageUsage() {
		return MaaSPackageUsage;
	}

	public void setMaaSPackageUsage(Map<String, Double> maaSPackageUsage) {
		MaaSPackageUsage = maaSPackageUsage;
	}

	/**
	 * Follow timeId-MaaSPacakgeId-FareLink-volume Convention
	 * @return
	 */
	public Map<String, Map<String, Map<String, Double>>> getMaaSSpecificFareLinkFlow() {
		return MaaSSpecificFareLinkFlow;
	}

	/**
	 * Follow timeId-MaaSPacakgeId-FareLink-volume Convention
	 * @param maaSSpecificFareLinkFlow
	 */
	public void setMaaSSpecificFareLinkFlow(Map<String, Map<String, Map<String, Double>>> maaSSpecificFareLinkFlow) {
		MaaSSpecificFareLinkFlow = maaSSpecificFareLinkFlow;
	}

	public void setLinkTravelTime(Map<String, Map<Id<Link>, Double>> linkTravelTime) {
		this.linkTravelTime = linkTravelTime;
	}

	public void setTrLinkTravelTime(Map<String, Map<Id<TransitLink>, Double>> trLinkTravelTime) {
		this.trLinkTravelTime = trLinkTravelTime;
	}

	public Map<String, Map<Id<Link>, Double>> getLinkFlowWithoutPT() {
		return linkFlowWithoutPT;
	}

	public void setLinkFlowWithoutPT(Map<String, Map<Id<Link>, Double>> linkFlowWithoutPT) {
		this.linkFlowWithoutPT = linkFlowWithoutPT;
	}

	public Map<String, Map<Id<TransitLink>, Double>> getTransitDirectLinkTT() {
		return transitDirectLinkTT;
	}

	public void setTransitDirectLinkTT(Map<String, Map<Id<TransitLink>, Double>> transitDirectLinkTT) {
		this.transitDirectLinkTT = transitDirectLinkTT;
	}

	public Map<String, Map<Id<TransitLink>, Double>> getTransitTransferLinkTT() {
		return transitTransferLinkTT;
	}

	public void setTransitTransferLinkTT(Map<String, Map<Id<TransitLink>, Double>> transitTransferLinkTT) {
		this.transitTransferLinkTT = transitTransferLinkTT;
	}

	public double getAutoFlow() {
		return autoFlow;
	}

	public void setAutoFlow(double autoFlow) {
		this.autoFlow = autoFlow;
	}


	public double getTransitFlow() {
		return transitFlow;
	}

	public void setTransitFlow(double transitFlow) {
		this.transitFlow = transitFlow;
	}

	public double getActivities() {
		return activities;
	}

	public void setActivities(double activities) {
		this.activities = activities;
	}
	public void setFareLinkVolume(Map<String, Map<String, Double>> fareLinkVolume) {
		FareLinkVolume = fareLinkVolume;

	}
	public void setFareLinkVolumeGrad(Map<String, Map<String, double[]>> fareLinkVolumeGrad) {
		FareLinkVolumeGrad = fareLinkVolumeGrad;

	}
	
	public List<String> getVariables() {
		return variables;
	}

	public void setVariables(List<String> variables) {
		this.variables = variables;
	}

	public Map<String, Map<Id<Link>, double[]>> getLinkVolumeGrad() {
		return linkVolumeGrad;
	}

	public Map<String, Map<Id<TransitLink>, double[]>> getLinkTransitVolumeGrad() {
		return linkTransitVolumeGrad;
	}

	public Map<String, Map<Id<Link>, Map<String, double[]>>> getTrainCountGrad() {
		return trainCountGrad;
	}

	public Map<String, Map<String, double[]>> getTrainTransfersGrad() {
		return trainTransfersGrad;
	}

	public Map<String, Map<Id<Link>, double[]>> getLinkTravelTimeGrad() {
		return linkTravelTimeGrad;
	}

	public Map<String, Map<Id<TransitLink>, double[]>> getTrLinkTravelTimeGrad() {
		return trLinkTravelTimeGrad;
	}

	public Map<String, Map<Id<Link>, double[]>> getAveragePtOccupancyOnLinkGrad() {
		return averagePtOccupancyOnLinkGrad;
	}

	public Map<String, double[]> getMaaSPackageUsageGrad() {
		return MaaSPackageUsageGrad;
	}

	public Map<String, Map<Id<Link>, double[]>> getLinkFlowWithoutPTGrad() {
		return linkFlowWithoutPTGrad;
	}

	public Map<String, Map<Id<TransitLink>, double[]>> getTransitDirectLinkTTGrad() {
		return transitDirectLinkTTGrad;
	}

	public Map<String, Map<Id<TransitLink>, double[]>> getTransitTransferLinkTTGrad() {
		return transitTransferLinkTTGrad;
	}

	public Map<String, Map<String, Map<String, double[]>>> getMaaSSpecificFareLinkFlowGrad() {
		return MaaSSpecificFareLinkFlowGrad;
	}

	public void setLinkVolume(Map<String, Map<Id<Link>, Double>> linkVolume) {
		this.linkVolume = linkVolume;
	}

	public void setLinkTransitVolume(Map<String, Map<Id<TransitLink>, Double>> linkTransitVolume) {
		this.linkTransitVolume = linkTransitVolume;
	}

	public void setLinkVolumeGrad(Map<String, Map<Id<Link>, double[]>> linkVolumeGrad) {
		this.linkVolumeGrad = linkVolumeGrad;
	}

	public void setLinkTransitVolumeGrad(Map<String, Map<Id<TransitLink>, double[]>> linkTransitVolumeGrad) {
		this.linkTransitVolumeGrad = linkTransitVolumeGrad;
	}

	public void setTrainCountGrad(Map<String, Map<Id<Link>, Map<String, double[]>>> trainCountGrad) {
		this.trainCountGrad = trainCountGrad;
	}

	public void setTrainTransfersGrad(Map<String, Map<String, double[]>> trainTransfersGrad) {
		this.trainTransfersGrad = trainTransfersGrad;
	}

	public void setLinkTravelTimeGrad(Map<String, Map<Id<Link>, double[]>> linkTravelTimeGrad) {
		this.linkTravelTimeGrad = linkTravelTimeGrad;
	}

	public void setTrLinkTravelTimeGrad(Map<String, Map<Id<TransitLink>, double[]>> trLinkTravelTimeGrad) {
		this.trLinkTravelTimeGrad = trLinkTravelTimeGrad;
	}

	public void setAveragePtOccupancyOnLinkGrad(Map<String, Map<Id<Link>, double[]>> averagePtOccupancyOnLinkGrad) {
		this.averagePtOccupancyOnLinkGrad = averagePtOccupancyOnLinkGrad;
	}

	public void setMaaSPackageUsageGrad(Map<String, double[]> maaSPackageUsageGrad) {
		MaaSPackageUsageGrad = maaSPackageUsageGrad;
	}

	public void setLinkFlowWithoutPTGrad(Map<String, Map<Id<Link>, double[]>> linkFlowWithoutPTGrad) {
		this.linkFlowWithoutPTGrad = linkFlowWithoutPTGrad;
	}

	public void setTransitDirectLinkTTGrad(Map<String, Map<Id<TransitLink>, double[]>> transitDirectLinkTTGrad) {
		this.transitDirectLinkTTGrad = transitDirectLinkTTGrad;
	}

	public void setTransitTransferLinkTTGrad(Map<String, Map<Id<TransitLink>, double[]>> transitTransferLinkTTGrad) {
		this.transitTransferLinkTTGrad = transitTransferLinkTTGrad;
	}

	public void setMaaSSpecificFareLinkFlowGrad(
			Map<String, Map<String, Map<String, double[]>>> maaSSpecificFareLinkFlowGrad) {
		MaaSSpecificFareLinkFlowGrad = maaSSpecificFareLinkFlowGrad;
	}

	public void writeTrainCountsAndTransfers(String folderLoc, String optionalTimeBean) {
		File file = new File(folderLoc);
		if(!file.isDirectory())file.mkdir();
		if(optionalTimeBean==null)optionalTimeBean = "";
		String linkCountFileName = folderLoc+"/trainLinkCounts_"+optionalTimeBean+".csv";
		String transferFileName = folderLoc+"/trainTransfers_"+optionalTimeBean+".csv";
		if(this.trainCount!=null) {
			try {
				FileWriter fw = new FileWriter(new File(linkCountFileName));
				fw.append("timeId,LinkId,LineAndRoute,Count\n");
				
				this.trainCount.entrySet().forEach(e->{
					e.getValue().entrySet().forEach(linkC->{
						linkC.getValue().entrySet().forEach(lrv->{
							try {
								fw.append(e.getKey()+","+linkC.getKey().toString()+","+lrv.getKey()+","+lrv.getValue()+"\n");
								fw.flush();
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						});
					});
				});
				
				fw.close();
				
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if(this.trainTransfers!=null) {
			try {
				FileWriter fw = new FileWriter(new File(transferFileName));
				fw.append("timeId,FromStop,toStop,Volume\n");
				for(Entry<String, Map<String, Double>> d:this.trainTransfers.entrySet()) {
					for(Entry<String, Double> e:d.getValue().entrySet()) {
						fw.append(d.getKey()+","+e.getKey().split("___")[0]+","+e.getKey().split("___")[1]+","+e.getValue()+"\n");
						fw.flush();
					}
				}
				fw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static <T> Tuple<Map<String,Map<Id<T>,Double>>,Map<String,Map<Id<T>,double[]>>> splitMap(Map<String,Map<Id<T>,Tuple<Double,double[]>>> input){
		Map<String,Map<Id<T>,Double>> first = new HashMap<>();
		Map<String,Map<Id<T>,double[]>> second = new HashMap<>();
		
		input.entrySet().forEach(e->{
			first.put(e.getKey(), new HashMap<>());
			second.put(e.getKey(), new HashMap<>());
			e.getValue().entrySet().forEach(ee->{
				first.get(e.getKey()).put(ee.getKey(), ee.getValue().getFirst());
				second.get(e.getKey()).put(ee.getKey(), ee.getValue().getSecond());
			});
		});
		
		return new Tuple<>(first,second);
		
	}
	
	public void setLinkVolumeAndGradient(Map<String, Map<Id<Link>, Tuple<Double,double[]>>> linkVolumeAndGradient) {
		Tuple<Map<String, Map<Id<Link>, Double>>, Map<String, Map<Id<Link>, double[]>>> aux = splitMap(linkVolumeAndGradient);
		this.setLinkVolume(aux.getFirst());
		this.setLinkVolumeGrad(aux.getSecond());
	}
	
}
