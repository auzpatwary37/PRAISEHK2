package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitTransferLink;






/**
 * 
 * @author Ashraf
 *
 */
/*
 * TODO: Fix Route Utility
 * TODO: Move to Fare Link based fare
 */
public class CNLTransitRoute implements AnalyticalModelTransitRoute{
	
	private final Logger logger=Logger.getLogger(CNLTransitRoute.class);
	
	private TransitSchedule transitSchedule;
	private Scenario scenario;
	private Id<AnalyticalModelTransitRoute> trRouteId;
	private double routeTravelTime=0;
	private double routeWalkingDistance=0;
	private double routeWaitingTime=0;
	protected double routeFare=0;
	private List<CNLTransitDirectLink> directLinks=new ArrayList<>();
	protected List<FareLink>FareEntryAndExit=new ArrayList<>(); 
	private List<CNLTransitTransferLink> transferLinks=new ArrayList<>();
	private Map<Id<TransitLink>, TransitLink> trLinks=new HashMap<>();
	private Map<String,Double> routeCapacity=new HashMap<>();
	private routeInfoOut info;
	private final Id<AnalyticalModelTransitRoute> oldTrRouteId;
	public static final String routeIdSubscript = "_tr_";
	/**
	 * Constructor
	 * these two lists holds all the pt legs and pt activity sequentially for one single transit trip
	 * @param ptlegList
	 * @param ptactivityList
	 */
	public CNLTransitRoute(ArrayList<Leg> ptlegList,ArrayList<Activity> ptactivityList, 
			TransitSchedule ts,Scenario scenario) {
		this.scenario=scenario;
		this.transitSchedule=ts;
		
		try {
		if((!(ptlegList.get(0).getMode().equals("transit_walk") && ptlegList.get(ptlegList.size()-1).getMode().equals("transit_walk")))&&(!(ptlegList.get(0).getMode().equals("walk") && ptlegList.get(ptlegList.size()-1).getMode().equals("walk")))) {
			logger.error("Invalid trip legs, The trip must have at least two walk legs at the start and end");
			throw new IllegalArgumentException("Invalid input for creating transit route");
		}else if (ptactivityList.size()!=ptlegList.size()+1) {
			logger.error("There must be exactly one more activity than no of trip legs");
			throw new IllegalArgumentException("Invalid input for creating transit route");
		}
		
		}catch(Exception e) {
			logger.error("could not create transit route, see error log.");
		}
		
		
		/*
		 * coming from backward and assuming there will always be at least one more transfer links than the direct links
		 */
		int transferLinkCount=0;
		int directLinkCount=0;
		String idstring;
		int a=(ptlegList.size()-1)/2-1;
		int b=(ptlegList.size()-1)/2;
		Map<Integer,CNLTransitDirectLink> tempDirectLinks=new HashMap<>();
		Map<Integer, CNLTransitTransferLink> tempTransferLinks=new HashMap<>();
		for(int i=ptlegList.size()-1;i>=0;i--) {
			Leg l=ptlegList.get(i);
			String startStopId=null;
			String endStopId=null;
			Id<Link> startLinkId=l.getRoute().getStartLinkId();
			Id<Link> endLinkId=l.getRoute().getEndLinkId();
			//System.out.println("testing");
			if(l.getMode().equals("transit_walk")||l.getMode().equals("walk")) {
				transferLinkCount++;
				this.routeWalkingDistance+=l.getRoute().getDistance();
				CNLTransitTransferLink t;
				if (i==ptlegList.size()-1) {
					t=new CNLTransitTransferLink(startStopId, 
							endStopId, startLinkId, endLinkId, ts,null);	
				}else {
					t=new CNLTransitTransferLink(startStopId, 
							endStopId, startLinkId, endLinkId, ts,tempDirectLinks.get(a+1));
				}
				tempTransferLinks.put(b, t);
				b--;
			}else{
				directLinkCount++;
				CNLTransitDirectLink dlink=new CNLTransitDirectLink(l.getRoute().getRouteDescription(), 
						startLinkId, endLinkId, ts, scenario);
				tempDirectLinks.put(a, dlink);
				a--;
				
			}
			
		}
		
		for(int i=0;i<=Collections.max(tempTransferLinks.keySet());i++) {
			this.transferLinks.add(tempTransferLinks.get(i));
		}
		for(int i=0;i<=Collections.max(tempDirectLinks.keySet());i++) {
			this.directLinks.add(tempDirectLinks.get(i));
		}
		
		idstring=this.transferLinks.get(0).getTrLinkId().toString();
		for(int i=0;i<this.directLinks.size();i++) {
			idstring+=this.directLinks.get(i).getTrLinkId().toString()+this.transferLinks.get(i+1).getTrLinkId().toString();
		}
		this.trRouteId=Id.create(idstring,AnalyticalModelTransitRoute.class);
		this.oldTrRouteId=Id.create(idstring,AnalyticalModelTransitRoute.class);
		
		this.trLinks.put(this.transferLinks.get(0).getTrLinkId(), this.transferLinks.get(0));
		for(int i=0;i<this.directLinks.size();i++) {
			this.trLinks.put(this.directLinks.get(i).getTrLinkId(), this.directLinks.get(i));
			this.trLinks.put(this.transferLinks.get(i+1).getTrLinkId(),this.transferLinks.get(i+1));
		}
		this.calcFareEntryAndExitLinks();
	}
	
	
	
	public CNLTransitRoute(List<CNLTransitTransferLink> transferLinks,List<CNLTransitDirectLink>dlink,Scenario scenario,TransitSchedule ts,
			double routeWalkingDistance,String routeId) {
		this.directLinks=dlink;
		this.transferLinks=transferLinks;
		this.transitSchedule=ts;
		this.routeWalkingDistance=routeWalkingDistance;
		this.trRouteId=Id.create(routeId, AnalyticalModelTransitRoute.class);
		this.oldTrRouteId=Id.create(routeId,AnalyticalModelTransitRoute.class);
		this.trLinks.put(this.transferLinks.get(0).getTrLinkId(), this.transferLinks.get(0));
		for(int i=0;i<this.directLinks.size();i++) {
			this.trLinks.put(this.directLinks.get(i).getTrLinkId(), this.directLinks.get(i));
			this.trLinks.put(this.transferLinks.get(i+1).getTrLinkId(),this.transferLinks.get(i+1));
		}
		this.calcFareEntryAndExitLinks();
	}
	
//	@Override
//	public double calcRouteUtility(LinkedHashMap<String, Double> params,LinkedHashMap<String, Double> anaParams,AnalyticalModelNetwork network,Map<String,FareCalculator>farecalc,Tuple<Double,Double>timeBean) {
//		
//		double MUTravelTime=params.get(CNLSUEModel.MarginalUtilityofTravelptName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
//		double MUDistance=params.get(CNLSUEModel.MarginalUtilityOfDistancePtName);
//		double MUWalkTime=params.get(CNLSUEModel.MarginalUtilityOfWalkingName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
//		double MUWaitingTime=params.get(CNLSUEModel.MarginalUtilityofWaitingName)/3600-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
//		double ModeConstant=params.get(CNLSUEModel.ModeConstantPtname);
//		double MUMoney=params.get(CNLSUEModel.MarginalUtilityofMoneyName);
//		double DistanceBasedMoneyCostWalk=params.get(CNLSUEModel.DistanceBasedMoneyCostWalkName);
//		double fare=this.getFare(transitSchedule, farecalc);
//		double travelTime=this.calcRouteTravelTime(network,timeBean,params,anaParams);
//		double walkTime=this.getRouteWalkingDistance()/1.4;
//		double walkDist=this.getRouteWalkingDistance();
//		double waitingTime=this.getRouteWaitingTime(anaParams,network);
//		double distance=this.getRouteDistance(network);
//		double utility=0;
//		double MUTransfer=params.get(CNLSUEModel.UtilityOfLineSwitchName);
//		
//		utility=ModeConstant+
//				travelTime*MUTravelTime+
//				MUMoney*fare+
//				MUWalkTime*walkTime+
//				MUMoney*DistanceBasedMoneyCostWalk*walkDist+
//				MUWaitingTime*waitingTime
//				+MUTransfer*(this.transferLinks.size()-1)
//				+MUDistance*distance*MUMoney;
//		if(utility==0) {
//			logger.warn("Stop!!! route utility is zero.");
//		}
//		return utility*anaParams.get(CNLSUEModel.LinkMiuName);
//	}
	
	
	public double calcRouteUtility(LinkedHashMap<String, Double> params,LinkedHashMap<String, Double> anaParams,AnalyticalModelNetwork network,Map<Id<TransitLink>,TransitLink>transitLinks,Map<String,FareCalculator>farecalc,Map<String,Object> AdditionalDataContainer,
			Tuple<Double,Double> timeBean) {
		routeInfoOut info=this.calcRouteTravelAndWaitingTime(network,transitLinks, timeBean, params, anaParams);
		this.info=info;
		double MUTravelTime=params.get(CNLSUEModel.MarginalUtilityofTravelptName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double MUDistance=params.get(CNLSUEModel.MarginalUtilityOfDistancePtName);
		double MUWalkTime=params.get(CNLSUEModel.MarginalUtilityOfWalkingName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double MUWaitingTime=params.get(CNLSUEModel.MarginalUtilityofWaitingName)/3600-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double ModeConstant=params.get(CNLSUEModel.ModeConstantPtname);
		double MUMoney=params.get(CNLSUEModel.MarginalUtilityofMoneyName);
		double DistanceBasedMoneyCostWalk=params.get(CNLSUEModel.DistanceBasedMoneyCostWalkName);
		double fare=-1*this.getFare(transitSchedule, farecalc, AdditionalDataContainer);
		double travelTime=info.getTravelTime();
		double walkTime=this.getRouteWalkingDistance()/1.4;
		double walkDist=this.getRouteWalkingDistance();
		double waitingTime=info.getWaitingTime();
		double distance=info.getRouteDistance();
		double utility=0;
		double MUTransfer=params.get(CNLSUEModel.UtilityOfLineSwitchName);
		
		utility=ModeConstant+
				travelTime*MUTravelTime+
				MUMoney*fare+
				MUWalkTime*walkTime+
				MUMoney*DistanceBasedMoneyCostWalk*walkDist+
				MUWaitingTime*waitingTime
				+MUTransfer*(this.transferLinks.size()-1)
				+MUDistance*distance*MUMoney;
		if(utility==0 ||Double.isNaN(utility)) {
			logger.warn("Stop!!! route utility is zero or infinity.");
		}
		return utility*anaParams.get(CNLSUEModel.LinkMiuName);
	}
	
	
	public double calcRouteUtility(LinkedHashMap<String, Double> params,LinkedHashMap<String, Double> anaParams,Map<String,AnalyticalModelNetwork> network,Map<Id<TransitLink>,TransitLink>transitLinks, Map<String,FareCalculator>farecalc,
			Map<String,Tuple<Double,Double>> timeBean,AnalyticalModelODpair odpair,String timeBeanId,String nextTimeBeanId) {
		routeInfoOut info=this.calcRouteTravelAndWaitingTime(network,transitLinks, timeBean, params, anaParams,odpair,timeBeanId,nextTimeBeanId);
		this.info=info;
		double MUTravelTime=params.get(CNLSUEModel.MarginalUtilityofTravelptName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double MUDistance=params.get(CNLSUEModel.MarginalUtilityOfDistancePtName);
		double MUWalkTime=params.get(CNLSUEModel.MarginalUtilityOfWalkingName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double MUWaitingTime=params.get(CNLSUEModel.MarginalUtilityofWaitingName)/3600-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double ModeConstant=params.get(CNLSUEModel.ModeConstantPtname);
		double MUMoney=params.get(CNLSUEModel.MarginalUtilityofMoneyName);
		double DistanceBasedMoneyCostWalk=params.get(CNLSUEModel.DistanceBasedMoneyCostWalkName);
		double fare=this.getFare(transitSchedule, farecalc);
		double travelTime=info.getTravelTime();
		double walkTime=this.getRouteWalkingDistance()/1.4;
		double walkDist=this.getRouteWalkingDistance();
		double waitingTime=info.getWaitingTime();
		double distance=info.getRouteDistance();
		double utility=0;
		double MUTransfer=params.get(CNLSUEModel.UtilityOfLineSwitchName);
		
		utility=ModeConstant+
				travelTime*MUTravelTime+
				MUMoney*fare+
				MUWalkTime*walkTime+
				MUMoney*DistanceBasedMoneyCostWalk*walkDist+
				MUWaitingTime*waitingTime
				+MUTransfer*(this.transferLinks.size()-1)
				+MUDistance*distance*MUMoney;
		if(utility==0 || Double.isNaN(utility)) {
			logger.warn("Stop!!! route utility is zero.");
		}
		return utility*anaParams.get(CNLSUEModel.LinkMiuName);
	}
	
	

	
	public double getFare(TransitSchedule ts, Map<String, FareCalculator> farecalc) {
//		if(ts==null) {
//			ts=this.transitSchedule;
//		}
//		if(this.routeFare!=0) {
//			return this.routeFare;
//		}
		this.routeFare=0;
//		String StartStopIdTrain=null;
//		String EndStopIdTrain=null;
//		int k=0;
//		for(CNLTransitDirectLink dlink :this.directLinks) {
//			k++;
//			Id<TransitLine> tlineId=Id.create(dlink.getLineId(), TransitLine.class);
//			Id<TransitRoute> trouteId=Id.create(dlink.getRouteId(), TransitRoute.class);
//			
//			
//			//Handling the train fare
//			if(ts.getTransitLines().get(tlineId).getRoutes().get(trouteId).getTransportMode().equals("train")) {
//				
//				if(StartStopIdTrain!=null) {//Train trip already started
//					EndStopIdTrain=dlink.getEndStopId();
//					if(k==this.directLinks.size()) {
//						MTRFareCalculator mtrFare=(MTRFareCalculator) farecalc.get("train");
//						this.routeFare=mtrFare.getMinFare(null, null, Id.create(StartStopIdTrain, TransitStopFacility.class),
//								Id.create(EndStopIdTrain, TransitStopFacility.class));
//					}
//				}else {//Train trip started in this link
//					StartStopIdTrain=dlink.getStartStopId();
//					EndStopIdTrain=dlink.getEndStopId();
//					if(k==this.directLinks.size()) {
//						MTRFareCalculator mtrFare=(MTRFareCalculator) farecalc.get("train");
//						this.routeFare=mtrFare.getMinFare(null, null, Id.create(StartStopIdTrain, TransitStopFacility.class),
//								Id.create(EndStopIdTrain, TransitStopFacility.class));
//					}
//				}
//			}else{//not a train trip leg, so two possibilities, train trip just ended in the previous trip or completely new trip
//				if(StartStopIdTrain!=null) {//train trip just ended, the fare will be added.
//					MTRFareCalculator mtrFare=(MTRFareCalculator) farecalc.get("train");
//					this.routeFare+=mtrFare.getMinFare(null, null, Id.create(StartStopIdTrain, TransitStopFacility.class),
//							Id.create(EndStopIdTrain, TransitStopFacility.class));
//					StartStopIdTrain=null;
//					EndStopIdTrain=null;
//					//now bus fare of the current trip is added	
//					TransitRoute tr=ts.getTransitLines().get(Id.create(dlink.getLineId(),TransitLine.class)).getRoutes().get(Id.create(dlink.getRouteId(),TransitRoute.class));
//					this.routeFare+=farecalc.get(tr.getTransportMode()).getFares(tr.getId(), Id.create(dlink.getLineId(),TransitLine.class), 
//							Id.create(dlink.getStartStopId(), TransitStopFacility.class), Id.create(dlink.getEndStopId(), TransitStopFacility.class)).get(0);
//				}else {//only bus fare is added
//					TransitRoute tr=ts.getTransitLines().get(Id.create(dlink.getLineId(),TransitLine.class)).getRoutes().get(Id.create(dlink.getRouteId(),TransitRoute.class));
//					this.routeFare+=farecalc.get(tr.getTransportMode()).getFares(tr.getId(), Id.create(dlink.getLineId(),TransitLine.class), 
//							Id.create(dlink.getStartStopId(), TransitStopFacility.class), Id.create(dlink.getEndStopId(), TransitStopFacility.class)).get(0);
//				}
//			}
//		}
//		
		
		for(FareLink f:this.FareEntryAndExit) {
			
			String mode=f.getMode();
			if(f.getType().equals(FareLink.NetworkWideFare)) {
				this.routeFare+=farecalc.get(mode).getFares(null, null, f.getBoardingStopFacility(), f.getAlightingStopFacility()).get(0);
			}else {
				this.routeFare+=farecalc.get(mode).getFares(f.getTransitRoute(), f.getTransitLine(), f.getBoardingStopFacility(), f.getAlightingStopFacility()).get(0);
			}
		}
		
		
		return this.routeFare;
	}

	@Override
	public double calcRouteTravelTime(AnalyticalModelNetwork network,Map<Id<TransitLink>,TransitLink>transitLinks, Tuple<Double,Double>timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams) {
		double routeTravelTime=0;
		for(CNLTransitDirectLink dlink:this.directLinks) {
			routeTravelTime+=dlink.getLinkTravelTime(network,timeBean,params,anaParams);
		}
		return routeTravelTime;
	}
	
//	public void calcLinkReachTime(Map<String,AnalyticalModelNetwork> networks,Map<String,Map<Id<TransitLink>,Double>> transitLinks,Map<String,Tuple<Double,Double>>timeBeans,double startTime,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams) {
//		
//	}
	
	public routeInfoOut calcRouteTravelAndWaitingTime(AnalyticalModelNetwork network,Map<Id<TransitLink>,TransitLink>transitLinks, Tuple<Double,Double>timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams) {
		double time=0;
		double waitingTime=0;
		double travelTime=0;
		double routeDistance=0;
		//String timeId=this.getTimeId(time, timeBean);
		Map<Id<TransitLink>,Double> linkReachTimeDL=new HashMap<>();
		Map<Id<TransitLink>,Double> linkReachTimeTL=new HashMap<>();
		linkReachTimeTL.put(this.transferLinks.get(0).getTrLinkId(),time);
		double neededwaitingTime=((CNLTransitTransferLink)transitLinks.get(this.transferLinks.get(0).getTrLinkId())).getWaitingTime(anaParams, network);
		waitingTime+=neededwaitingTime;
		time+=neededwaitingTime;
			
		for(int i=0;i<this.directLinks.size();i++) {
			linkReachTimeDL.put(this.directLinks.get(i).getTrLinkId(), time);
			double travelTimeDL=this.directLinks.get(i).getLinkTravelTime(network, timeBean, params, anaParams);
			//System.out.println();
			routeDistance+=this.getTransitDirectLinks().get(i).getPhysicalDistance(network);
			travelTime+=travelTimeDL;
			time+=travelTimeDL;
			linkReachTimeTL.put(this.transferLinks.get(i+1).getTrLinkId(), time);
			double waitingTimeTL=((CNLTransitTransferLink)transitLinks.get(this.transferLinks.get(i+1).getTrLinkId())).getWaitingTime(anaParams, network);
			if(Double.isNaN(waitingTime))
				System.out.println("a");
			waitingTime+=waitingTimeTL;
			time+=waitingTimeTL;
			
		}
		if(Double.isNaN(waitingTime))
			System.out.println();
		this.info=new routeInfoOut(travelTime,waitingTime,routeDistance,linkReachTimeDL,linkReachTimeTL);
		return info;
	}
	
	public routeInfoOut calcRouteTravelAndWaitingTime(Map<String,AnalyticalModelNetwork> network,Map<Id<TransitLink>,TransitLink>transitLinks,
			Map<String,Tuple<Double,Double>>timeBean,LinkedHashMap<String,Double>params,
			LinkedHashMap<String,Double>anaParams,AnalyticalModelODpair odpair,String timeBeanId, String nextTimeBeanId) {
		double time=0;
		double waitingTime=0;
		double travelTime=0;
		double routeDistance=0;
		//String timeId=this.getTimeId(time, timeBean);
		Map<Id<TransitLink>,Double> linkReachTimeDL=new HashMap<>();
		Map<Id<TransitLink>,Double> linkReachTimeTL=new HashMap<>();
		linkReachTimeTL.put(this.transferLinks.get(0).getTrLinkId(),time);
		double neededwaitingTime=((CNLTransitTransferLink)transitLinks.get(this.transferLinks.get(0).getTrLinkId())).getWaitingTime(anaParams, network.get(timeBeanId));
		waitingTime+=neededwaitingTime;
		time+=neededwaitingTime;
			
		for(int i=0;i<this.directLinks.size();i++) {
			linkReachTimeDL.put(this.directLinks.get(i).getTrLinkId(), time);
			double travelTimeDL1=this.directLinks.get(i).getLinkTravelTime(network.get(timeBeanId), timeBean.get(timeBeanId), params, anaParams);
			double travelTimeDL2=this.directLinks.get(i).getLinkTravelTime(network.get(nextTimeBeanId), timeBean.get(nextTimeBeanId), params, anaParams);
			routeDistance+=this.getTransitDirectLinks().get(i).getPhysicalDistance(network.get(timeBeanId));
			double p1=odpair.getDepartureTimeDistributions().get(timeBeanId).cumulativeProbability(timeBean.get(timeBeanId).getSecond()-time);
			double avgtt=travelTimeDL1*p1+travelTimeDL2*(1-p1);
			travelTime+=avgtt;
			time+=avgtt;
			linkReachTimeTL.put(this.transferLinks.get(i+1).getTrLinkId(), time);
			double waitingTimeTL1=((CNLTransitTransferLink)transitLinks.get(this.transferLinks.get(i+1).getTrLinkId())).getWaitingTime(anaParams, network.get(timeBeanId));
			double waitingTimeTL2=((CNLTransitTransferLink)transitLinks.get(this.transferLinks.get(i+1).getTrLinkId())).getWaitingTime(anaParams, network.get(nextTimeBeanId));
			double p2=odpair.getDepartureTimeDistributions().get(timeBeanId).cumulativeProbability(timeBean.get(timeBeanId).getSecond()-time);
			double avgWaitTime=waitingTimeTL1*p2+waitingTimeTL2*(1-p2);
			waitingTime+=avgWaitTime;
			time+=avgWaitTime;
		}
		this.info=new routeInfoOut(travelTime,waitingTime,routeDistance,linkReachTimeDL,linkReachTimeTL);
		return info;
	}
	
	public String getTimeId(Double time,Map<String,Tuple<Double,Double>>timeBeans) {
		if(time>24*3600) time=time-24*3600;
		if(time==0) time=1.;
		for(Entry<String, Tuple<Double, Double>> s:timeBeans.entrySet()) {
			if(time>s.getValue().getFirst() && time<=s.getValue().getSecond()) {
				return s.getKey();
			}
		}
		return null;
	}
	

	@Override
	public double getRouteWalkingDistance() {
		return this.routeWalkingDistance;
	}

	@Override
	public double getRouteWaitingTime(LinkedHashMap<String,Double> anaParams,AnalyticalModelNetwork network,Map<Id<TransitLink>,TransitLink>transitLinks) {
		double routeWaitingTime=0;
		for(CNLTransitTransferLink tlink:this.transferLinks) {
			routeWaitingTime+=((CNLTransitTransferLink)transitLinks.get(tlink.getTrLinkId())).getWaitingTime(anaParams,network);
		}
		return routeWaitingTime;
	}
	
	private void calcFareEntryAndExitLinks() {
		TransitSchedule ts=this.transitSchedule;
		String StartStopIdTrain=null;
		String EndStopIdTrain=null;
		int k=0;
		for(CNLTransitDirectLink dlink :this.directLinks) {
			k++;
			Id<TransitLine> tlineId=Id.create(dlink.getLineId(), TransitLine.class);
			Id<TransitRoute> trouteId=Id.create(dlink.getRouteId(), TransitRoute.class);
			
			
			//Handling the train fare
			if(ts.getTransitLines().get(tlineId).getRoutes().get(trouteId).getTransportMode().equals("train")) {
				
				if(StartStopIdTrain!=null) {//Train trip already started
					EndStopIdTrain=dlink.getEndStopId();
					if(k==this.directLinks.size()) {//StartStopIdTrain+"___"+EndStopIdTrain+"___"+"train"
						this.FareEntryAndExit.add(new FareLink(FareLink.NetworkWideFare+FareLink.seperator+StartStopIdTrain+FareLink.seperator+EndStopIdTrain+FareLink.seperator+"train"));
					}
				}else {//Train trip started in this link
					StartStopIdTrain=dlink.getStartStopId();
					EndStopIdTrain=dlink.getEndStopId();
					if(k==this.directLinks.size()) {//StartStopIdTrain+"___"+EndStopIdTrain+"___"+"train"
						this.FareEntryAndExit.add(new FareLink(FareLink.NetworkWideFare+FareLink.seperator+StartStopIdTrain+FareLink.seperator+EndStopIdTrain+FareLink.seperator+"train"));
					}
				}
			}else{//not a train trip leg, so two possibilities, train trip just ended in the previous trip or completely new trip
				if(StartStopIdTrain!=null) {//train trip just ended, the fare will be added.
					this.FareEntryAndExit.add(new FareLink(FareLink.NetworkWideFare+FareLink.seperator+StartStopIdTrain+FareLink.seperator+EndStopIdTrain+FareLink.seperator+"train"));
					StartStopIdTrain=null;
					EndStopIdTrain=null;
					//now bus fare of the current trip is added	
					TransitRoute tr=ts.getTransitLines().get(Id.create(dlink.getLineId(),TransitLine.class)).getRoutes().get(Id.create(dlink.getRouteId(),TransitRoute.class));
					this.FareEntryAndExit.add(new FareLink(FareLink.InVehicleFare+FareLink.seperator+dlink.getLineId()+FareLink.seperator+tr.getId()+FareLink.seperator+dlink.getStartStopId()+FareLink.seperator+dlink.getEndStopId()+FareLink.seperator+tr.getTransportMode()));
				}else {//only bus fare is added
					TransitRoute tr=ts.getTransitLines().get(Id.create(dlink.getLineId(),TransitLine.class)).getRoutes().get(Id.create(dlink.getRouteId(),TransitRoute.class));
					this.FareEntryAndExit.add(new FareLink(FareLink.InVehicleFare+FareLink.seperator+dlink.getLineId()+FareLink.seperator+tr.getId()+FareLink.seperator+dlink.getStartStopId()+FareLink.seperator+dlink.getEndStopId()+FareLink.seperator+tr.getTransportMode()));
				}
			}
		}
	}
	
	/**
	 * Convenient method to break down route description
	 * has not been used in the current setup
	 * @param s
	 * @return
	 */
	private HashMap<String,String> parsedirectTransitLink(String s){
		HashMap<String, String> parsedOutput=new HashMap<>();
		
		String[] part=s.split("===");
		parsedOutput.put("startStopId",part[1].trim());
		parsedOutput.put("endStopId",part[4].trim());
		parsedOutput.put("lineId",part[2].trim());
		parsedOutput.put("routeId",part[3].trim());
		
		return parsedOutput;
	}



	@Override
	public Id<AnalyticalModelTransitRoute> getTrRouteId() {
		return this.trRouteId;
	}


	private double getRouteDistance(AnalyticalModelNetwork network) {
		double d=0;
		for(CNLTransitDirectLink l:this.directLinks) {
			for(Id<Link> lid:l.getLinkList()) {
				d+=network.getLinks().get(lid).getLength();
			}
		}
		return d;
	}

	@Override
	public ArrayList<Id<TransitLink>> getTrLinkIds() {
		return new ArrayList<Id<TransitLink>>(this.trLinks.keySet());
	}
	
	@Override
	public Map<Id<TransitLink>,TransitLink> getTransitLinks(){
		for(CNLTransitDirectLink dl:this.directLinks) {
			this.trLinks.put(dl.getTrLinkId(), dl);
		}
		for(CNLTransitTransferLink tl:this.transferLinks) {
			this.trLinks.put(tl.getTrLinkId(), tl);
		}
		
		return this.trLinks;
	}
	@Override
	public void calcCapacityHeadway(Map<String,Tuple<Double,Double>>timeBeans,String timeBeanId) {
		double routecapacity=Double.MAX_VALUE;
		for(CNLTransitDirectLink dl:this.directLinks) {
			dl.calcCapacityAndHeadway(timeBeans, timeBeanId);
			routecapacity=Double.min(routecapacity, dl.capacity);
		}
		this.routeCapacity.put(timeBeanId, routecapacity);
	}
	
	
	public void calcCapacityHeadway(Map<String,Map<String,Double>>vehicleCount,Map<String,Map<String,Double>>capacity,Map<String,Tuple<Double,Double>>timeBeans,String timeBeanId) {
		double routecapacity=Double.MAX_VALUE;
		for(CNLTransitDirectLink dl:this.directLinks) {
			//System.out.println("test");
			dl.calcCapacityAndHeadway(timeBeans, timeBeanId);
			routecapacity=Double.min(routecapacity, dl.capacity);
		}
		this.routeCapacity.put(timeBeanId, routecapacity);
	}
	@Override
	public Map<String, Double> getRouteCapacity() {
		return routeCapacity;
	}

	@Override
	public AnalyticalModelTransitRoute cloneRoute() {
		ArrayList<CNLTransitDirectLink> dlinks=new ArrayList<>();
		ArrayList<CNLTransitTransferLink> transferLinks=new ArrayList<>();
		int i=0;
		for(CNLTransitTransferLink tl:this.transferLinks) {
			if(tl.getNextdLink()!=null) {
			dlinks.add(tl.getNextdLink().cloneLink(tl.getNextdLink()));
			transferLinks.add(tl.cloneLink(tl, dlinks.get(i)));
			}else {
				transferLinks.add(tl.cloneLink(tl, null));
			}
			i++;
		}
		CNLTransitRoute trRoute=new CNLTransitRoute(transferLinks,dlinks,this.scenario,this.transitSchedule,this.routeWalkingDistance,this.trRouteId.toString());
		
		return trRoute ; 
	}

	@Override
	public double getRouteDistance(Network network) {
		double distance=0;
		for(TransitDirectLink trl: this.directLinks) {
			distance+=trl.getPhysicalDistance(network);
		}
		return distance;
	}

	@Override
	public List<TransitDirectLink> getTransitDirectLinks() {
		List<TransitDirectLink> trdls=new ArrayList<>();
		for(TransitDirectLink trdl:this.directLinks) {
			trdls.add(trdl);
		}
		return trdls;
	}

	@Override
	public List<Id<Link>> getPhysicalLinks() {
		Set<Id<Link>> physicalLinks=new HashSet<>();
		for(TransitDirectLink tdl:this.directLinks) {
			physicalLinks.addAll(tdl.getLinkList());
		}
		return new ArrayList<Id<Link>>(physicalLinks);
	}

	@Override
	public List<FareLink> getFareLinks() {
		return FareEntryAndExit;
	}

	public routeInfoOut getInfo() {
		return info;
	}



	@Override
	public double getFare(TransitSchedule ts, Map<String, FareCalculator> farecalc,
			Map<String, Object> AdditionalDataContainer) {
		// TODO Auto-generated method stub
		return this.getFare(ts, farecalc);
	}

	@Override
	public List<TransitTransferLink> getTransitTransferLinks() {
		// TODO Auto-generated method stub
		List<TransitTransferLink> tlLinks = new ArrayList<TransitTransferLink>();
		this.transferLinks.stream().forEach((l)->{
			tlLinks.add(l);
		});
		return tlLinks;
	}



	@Override
	public void updateToOdBasedId(Id<AnalyticalModelODpair> odId, int routeNumber) {
		this.trRouteId = Id.create(odId.toString()+routeIdSubscript+routeNumber,AnalyticalModelTransitRoute.class);
	}



	@Override
	public Id<AnalyticalModelTransitRoute> getOldTrRouteId() {
		return this.oldTrRouteId;
	}
	
	
	
}	

class routeInfoOut{
	private final double travelTime;
	private final double waitingTime;
	private final Map<Id<TransitLink>,Double> directLinkReachTime;
	private final Map<Id<TransitLink>,Double> transferLinkReachTime;
	private final double routeDistance;
	public routeInfoOut(Double travelTime,Double waitingTime,double routeDistance,Map<Id<TransitLink>,Double>directLinkReachTime,Map<Id<TransitLink>,Double>transferLinkReachTime) {
		this.travelTime=travelTime;
		this.waitingTime=waitingTime;
		this.routeDistance=routeDistance;
		this.directLinkReachTime=directLinkReachTime;
		this.transferLinkReachTime=transferLinkReachTime;
	}
	public double getTravelTime() {
		return travelTime;
	}
	public double getWaitingTime() {
		return waitingTime;
	}
	public Map<Id<TransitLink>, Double> getDirectLinkReachTime() {
		return directLinkReachTime;
	}
	public Map<Id<TransitLink>, Double> getTransferLinkReachTime() {
		return transferLinkReachTime;
	}
	public double getRouteDistance() {
		return routeDistance;
	}
	public Map<Id<TransitLink>,Double> getLinkReachTime(){
		Map<Id<TransitLink>,Double> linkReachTime=new HashMap<>();
		linkReachTime.putAll(this.directLinkReachTime);
		linkReachTime.putAll(this.transferLinkReachTime);
		return linkReachTime;
	}
}
