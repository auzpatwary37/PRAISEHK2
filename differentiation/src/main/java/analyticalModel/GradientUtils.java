package analyticalModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitTransferLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitTransferLink;


public class GradientUtils {
	private static final Logger logger = Logger.getLogger(GradientUtils.class);
	private static final Clip timeClip = new Clip(-3600, 3600);
	private static final Clip flowClip = new Clip(-9999, 9999);
	/**
	 * Obtain the gradient for the auto link travel time (Equation 64)
	 * @param link Link concerned
	 * @param anaParam The analytical parameters
	 * @param linkGradient
	 * @param timeBinTime
	 * @return
	 */
	public static double[] getLinkTravelTimeGrad(CNLLink link, LinkedHashMap<String,Double> anaParam, 
			Map<Id<Link>,double[]> linkGradient, double capacityMultiplier, Tuple<Double,Double> timeBinTime) {
		//for(Entry<String, Double> var:linkGradientMap.getValue().entrySet()) {
		double flow = link.getLinkCarVolume()+link.getLinkTransitVolume();
		double t_0 = link.getLength()/link.getFreespeed();//should be in sec
		double cap = link.getCapacity()*link.getGcRatio()*capacityMultiplier*(timeBinTime.getSecond()-timeBinTime.getFirst())/3600;
		double beta = anaParam.get(ODDifferentiableSUEModel.BPRbetaName);
		double alpha = anaParam.get(ODDifferentiableSUEModel.BPRalphaName);
		double cons = alpha*beta*t_0/Math.pow(cap, beta)*Math.pow(flow,beta-1)/3600;
		//double grad = anaParam.get(ODDifferentiableSUEModel.BPRalphaName)*beta*t_0/Math.pow(cap, beta)*Math.pow(flow,beta-1)*this.linkGradient.get(timeId).get(link.getId()).get(var.getKey());
		if(cons>3600) {
			logger.debug("timeGradient is too high");
		}
		return MatrixUtils.createRealVector(linkGradient.get(link.getId())).mapMultiply(cons).map(timeClip).toArray();
		//this.linkTTGradient.get(timeId).get(link.getId()).put(var.getKey(),grad);

	}
	
	/**
	 * Obtain the transit link travel time gradient (Equation 66)
	 * @param link
	 * @param linkTTGradient
	 * @param anaParam
	 * @param trLinkGradient
	 * @param gradientSize
	 * @param network
	 * @return
	 */
	public static Tuple<Id<TransitLink>, double[]> getTransitLinkTravelTimeGrad(TransitLink link, 
			Map<Id<Link>,double[]> linkTTGradient, LinkedHashMap<String,Double> anaParam, 
			Map<Id<TransitLink>,double[]> trLinkGradient, int gradientSize, AnalyticalModelNetwork network) {
		
		//In case it is TransitDirectLink
		//Equation (64) and (65)
		if(link instanceof TransitDirectLink) {
			CNLTransitDirectLink dlink = (CNLTransitDirectLink)link;
			double grad = 0;
			RealVector g = MatrixUtils.createRealVector(new double[gradientSize]);
			for(Id<Link> linkId:dlink.getLinkList()) {
				if(linkTTGradient.get(linkId)==null) {//As we have used the link plan incidence to loop, there might be some link not used by any od pairs.
					//For these links, no matter what the decision variables are, the flow will not change (flows are from transit vehicle flow only). So, for these links, we can assume the gradient
					//to be zero.// Not sure if needed here
					logger.debug("Dead link here. Putting gradient = 0.");
					
				}else {
					//grad+=this.linkTTGradient.get(timeId).get(linkId).get(var.getKey());
					g = g.add(MatrixUtils.createRealVector(linkTTGradient.get(linkId)));
				}	
			}
			
			if(g.isNaN())
				logger.debug("Debug point. Gradient is NAN");
			//this.trLinkTTGradient.get(timeId).get(dlink.getTrLinkId()).put(var.getKey(), grad);
			return new Tuple<>(dlink.getTrLinkId(),g.toArray());
		
		//If it is a transfer link (equation (66))
		}else if(link instanceof TransitTransferLink){ 
			CNLTransitTransferLink transferLink = (CNLTransitTransferLink)link;
			CNLTransitDirectLink dlink = transferLink.getNextdLink();
			double grad = 0;
			double[] g = new double[gradientSize];
			if(dlink != null) {//For an alighting link only (the last transfer leg) the next dlink is null. 
				//The gradient for this link's travel time is zero as the waiting time for a alighting only link is always zero.
				CNLLink plink = (CNLLink) network.getLinks().get(transferLink.getStartingLinkId());
				double headway = dlink.getHeadway();
				double cap = dlink.getCapacity();
				double freq = dlink.getFrequency();
				double beta = anaParam.get(ODDifferentiableSUEModel.TransferbetaName);
				//double passengerTobeBorded = transferLink.getPassangerCount();
				double passengerOnBord = plink.getTransitPassengerVolume(dlink.getLineId()+"_"+dlink.getRouteId());
				double volume = passengerOnBord;
				double grad1 = beta*headway/Math.pow(cap*freq, beta)*Math.pow(volume, beta-1)/3600;//if both the second and first term is 
				if(grad1>3600) {
					logger.debug("waiting time gradient is too high!");
				}
				if(Double.isInfinite(grad1)||Double.isNaN(grad1))grad1 = 0;
				//double grad2 = this.trLinkGradient.get(timeId).get(transferLink.getTrLinkId()).get(var.getKey());
				//RealVector g2 = MatrixUtils.createRealVector(this.trLinkGradient.get(timeId).get(transferLink.getTrLinkId()));//Why this?
				RealVector g2 = MatrixUtils.createRealVector(new double[gradientSize]);
				for(Id<TransitLink> l:transferLink.getIncidentLinkIds(network)){
					//grad2+=this.trLinkGradient.get(timeId).get(l).get(var.getKey());
					g2 = g2.add(MatrixUtils.createRealVector(trLinkGradient.get(l)));
				}
				//grad = grad1*grad2;
				g = g2.mapMultiplyToSelf(grad1).map(timeClip).toArray();
//				g = g2.toArray();
				if(MatrixUtils.createRealVector(g).isNaN())
					logger.debug("Gradients nan here!!!");
			}else {
				grad = 0;
			}
			if(Double.isNaN(grad))
				logger.debug("Debug point. Gradient is NAN");
			//this.trLinkTTGradient.get(timeId).get(transferLink.getTrLinkId()).put(var.getKey(),grad);
			return new Tuple<>(transferLink.getTrLinkId(),g);
		}
		throw new IllegalArgumentException("The link is not considered!");
	}
	
	/**
	 * Update the auto link volume gradient, consider the MSA (Equation 52)
	 * @param linkId
	 * @param counter
	 * @return
	 */
	public static double[] getLinkFlowGrad(Entry<Id<Link>, double[]> linkId, double beta, int gradientSize, 
			List<Id<AnalyticalModelRoute>> routeIncidentLink, Map<Id<AnalyticalModelRoute>,double[]> routeFlowGrad, 
			boolean useUnitUpdateWeight) {
		RealVector old = MatrixUtils.createRealVector(linkId.getValue());
//		double grad = 0;
		RealVector g = MatrixUtils.createRealVector(new double[gradientSize]);
		for(Id<AnalyticalModelRoute>r: routeIncidentLink) {
//			grad+=this.routeFlowGradient.get(timeId).get(r).get(var);
			g = g.add(MatrixUtils.createRealVector(routeFlowGrad.get(r)));
		}
		
		if(g.getMaxValue() > 50) {
			System.nanoTime(); // Check if there is a link flow vector greater than 10
		}
		//double norm = 0;
		//if((norm = g.getL1Norm())>this.linkGradL1NormThreshold)g = g.mapDivide(norm).mapMultiply(this.linkGradL1NormThreshold);
//		double gradUpdate = oldGrad + (grad-oldGrad)*(1/this.beta.get(timeId).get(counter-1));
		double updateWeight = 1/beta;
		if(useUnitUpdateWeight) updateWeight = 1;
		RealVector gUpdate = old.add(g.subtract(old).mapMultiplyToSelf(updateWeight));
//		this.linkGradient.get(timeId).get(linkId.getKey()).put(var, gradUpdate);
		return gUpdate.toArray();
	}
	
	/**
	 * Obtain the gradient for the TR link volume (Equation 53, 54)
	 * @param linkId
	 * @param gradientSize
	 * @param routeIncidentLink
	 * @param trRouteFlowGrad
	 * @param beta
	 * @param useUnitUpdateWeight
	 * @return
	 */
	public static RealVector getTrLinkVolumeGrad(Entry<Id<TransitLink>, double[]> linkId, int gradientSize, 
			List<Id<AnalyticalModelTransitRoute>> routeIncidentLink,  
			Map<Id<AnalyticalModelTransitRoute>, double[]> trRouteFlowGrad, double beta, boolean useUnitUpdateWeight) {
//		double oldGrad = this.trLinkGradient.get(timeId).get(linkId.getKey()).get(var);
		RealVector old = MatrixUtils.createRealVector(linkId.getValue());
//		double grad = 0;
		RealVector g = MatrixUtils.createRealVector(new double[gradientSize]);
		for(Id<AnalyticalModelTransitRoute>r: routeIncidentLink) {
//			grad+=this.trRouteFlowGradient.get(timeId).get(r).get(var);
			if(trRouteFlowGrad.get(r)!=null) {
				g = g.add(MatrixUtils.createRealVector(trRouteFlowGrad.get(r)));
			}
		}
		g = g.map(flowClip);
		
		//double norm = 0;
		//if((norm = g.getL1Norm())>this.linkGradL1NormThreshold)g = g.mapDivide(norm).mapMultiply(this.linkGradL1NormThreshold);
//		double gradUpdate = oldGrad + (grad-oldGrad)*(1/this.beta.get(timeId).get(counter-1));
		double updateWeight = 1/beta;
		if(useUnitUpdateWeight) updateWeight = 1;
		return old.add(g.subtract(old).mapMultiplyToSelf(updateWeight));
	}
	
	
}
