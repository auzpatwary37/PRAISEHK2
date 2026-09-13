package running;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.api.core.v01.population.Route;
import org.matsim.contrib.roadpricing.RoadPricingScheme;
import org.matsim.contrib.roadpricing.RoadPricingSchemeImpl;
import org.matsim.contrib.roadpricing.RoadPricingUtils;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.GenericRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import assignLinkToPlanActivity.AssignLinkToPlanActivity;
import createBus.BusDataExtractor;
import dynamicTransitRouter.TransitLineRoute;

/**
 * A class with essential classes for the run functions
 * @author eleead
 *
 */
public class RunUtils {
	
	private static final String PersonChangeWithCar_NAME = "person_TCSwithCar";
	private static final String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
	private static final String PersonChangeWithoutCar2_NAME = "person_TCSwithoutCarTimeInsensitive";
	private static final String PersonFixed_NAME = "trip_TCS";
	private static final String GVChange_NAME = "person_GV";
	private static final String GVFixed_NAME = "trip_GV";
	/**
	 * This function analyses the line route list to obtain all interacted route.
	 * The key is to ensure these transfers would not be after a leg.
	 * e.g. If A can change to B directly, it is not allowed to do A->C->B for any C.
	 * @param ts
	 * @return
	 */
	public static Map<TransitLineRoute, Set<TransitLineRoute>> getLineRouteRelationList(TransitSchedule ts){
		//Step 1: For each stop, we construct the transitStopFacility to set of transitLineRoute map.
		Map<Id<TransitStopFacility>, Set<TransitLineRoute>> tsfToLineRoute = new HashMap<>();
		for(TransitLine tl: ts.getTransitLines().values()) {
			for(TransitRoute tr: tl.getRoutes().values()) { //Iterate through every lineRoute
				TransitLineRoute tlr = new TransitLineRoute(tl.getId(), tr.getId());
				for(TransitRouteStop trs: tr.getStops()) {
					Id<TransitStopFacility> tsfId = trs.getStopFacility().getId();
					Set<TransitLineRoute> stopRouteSet = tsfToLineRoute.get(tsfId);
					if(stopRouteSet == null) {
						stopRouteSet = new HashSet<>();
					}
					stopRouteSet.add(tlr);
					tsfToLineRoute.put(tsfId, stopRouteSet);
				}
			}
		}
		
		//Step 2: For each route, we search the interacting routes from the map above.
		Map<TransitLineRoute, Set<TransitLineRoute>> tlrToTlr = new HashMap<>();
		for(TransitLine tl: ts.getTransitLines().values()) {
			for(TransitRoute tr: tl.getRoutes().values()) { //Iterate through every lineRoute
				TransitLineRoute tlr = new TransitLineRoute(tl.getId(), tr.getId());
				Set<TransitLineRoute> tlrTransferRouteSet = tlrToTlr.get(tlr);
				if(tlrTransferRouteSet == null) tlrTransferRouteSet = new HashSet<>();
				
				for(TransitRouteStop trs: tr.getStops()) {
					Id<TransitStopFacility> tsfId = trs.getStopFacility().getId();
					Set<TransitLineRoute> stopRouteSet = tsfToLineRoute.get(tsfId);					
					tlrTransferRouteSet.addAll(stopRouteSet);
				}
				tlrToTlr.put(tlr, tlrTransferRouteSet);
			}
		}
		
		return tlrToTlr;
	}
	
	public static int changeAllModeToPt(Scenario scenario) {
		int changeCount = 0;
		for(Iterator<?> it = scenario.getPopulation().getPersons().entrySet().iterator(); it.hasNext(); ){
			Map.Entry<?, ?> entry = (Entry<?, ?>) it.next();
			Person person = (Person) entry.getValue();
			if(person.getId().toString().matches("\\d+.0_\\d+.0_\\d+")) {
				for(PlanElement pe: person.getSelectedPlan().getPlanElements()){
					if(pe instanceof Leg){
						if( !((Leg) pe).getMode().equals("pt")){
							((Leg) pe).setMode("pt");
							changeCount++;
						}
					}
				}
			}
		}
		return changeCount;
	}
	
	public static Network createCarOnlyNet(Network net) {
		Network newNet = NetworkUtils.createNetwork();
		TransportModeNetworkFilter filter = new TransportModeNetworkFilter( net );
		HashSet<String> modes = new HashSet<String>();
		modes.add(TransportMode.car);
		filter.filter(newNet, modes); //Filter the network
		return newNet;
	}
	
	/**
	 * Find the newest car link
	 * @param net
	 * @param coord
	 * @param allowedModes
	 * @return
	 */
	public static Id<Link> getNearestCarLink(Network carOnlyNet, Coord coord){
		Id<Link> returnLinkId = NetworkUtils.getNearestLink(carOnlyNet, coord).getId();
		if(returnLinkId==null) {
			throw new IllegalArgumentException("The link is not found!");
		}
		return returnLinkId;
	}
	
	/**
	 * Create a direct walk route with distance set.
	 * @param network
	 * @param startLinkId
	 * @param endLinkId
	 * @return
	 */
	public static Route createDirectWalkRoute(Network network, Id<Link> startLinkId, Id<Link> endLinkId) {
		Route gr = new GenericRouteFactory().createRoute(startLinkId, endLinkId);
		if(network.getLinks().containsKey(startLinkId) && network.getLinks().containsKey(endLinkId))
			gr.setDistance(NetworkUtils.getEuclideanDistance(network.getLinks().get(startLinkId).getCoord(), 
					network.getLinks().get(endLinkId).getCoord()));
		return gr;
	}
	
	/**
	 * Get the link to link that was available in the previous Lanes, and not in the current Lanes.
	 * Case 1: Current have a link with l2l, previous does not have AND previous network have some downstream link that is not available.
	 * Case 2: Both have l2l, and previous has a downstream link that is still in current network, but not in current lane.
	 * @param oldLanes
	 * @param newLanes
	 * @return
	 */
	public static List<Tuple<Id<Link>, Id<Link>>> getLinkToLinkForbiddenList(Lanes oldLanes, 
			Network oldNetwork, Lanes newLanes, Network newNetwork){
		List<Tuple<Id<Link>, Id<Link>>> linkToLinkForbiddenList = new ArrayList<>();
		for(LanesToLinkAssignment l2l: newLanes.getLanesToLinkAssignments().values()) { //Those restricted.
			Id<Link> fromLinkId = l2l.getLinkId();
			
			//Obtain the current downstream link Id
			LanesToLinkAssignment currentL2l = newLanes.getLanesToLinkAssignments().get(fromLinkId);
			Set<Id<Link>> currDownStreamLinkId = new HashSet<>();
			for(Lane lane: currentL2l.getLanes().values()) {
				if(lane.getToLinkIds()!=null)
					currDownStreamLinkId.addAll(lane.getToLinkIds());
			}
			
			//Find the old downstream toLinks
			Set<Id<Link>> oldDownStreamLinkId = new HashSet<>();
			if( !oldLanes.getLanesToLinkAssignments().containsKey(fromLinkId) ) { //Case one, no l2l
				if(oldNetwork.getLinks().containsKey(fromLinkId)) {
					for(Id<Link> toLinkId: oldNetwork.getLinks().get(fromLinkId).getToNode().getOutLinks().keySet()) {
						if(newNetwork.getLinks().containsKey(toLinkId))
							oldDownStreamLinkId.add(toLinkId); //Only store the links that still here
					}
				}
			}else {
				//Obtain the old downstream link Ids.
				LanesToLinkAssignment oldL2l = oldLanes.getLanesToLinkAssignments().get(fromLinkId);
				for(Lane lane: oldL2l.getLanes().values()) {
					if(lane.getToLinkIds()!=null) {
						for(Id<Link> toLinkId: lane.getToLinkIds()) {
							if(newNetwork.getLinks().containsKey(toLinkId))
								oldDownStreamLinkId.add(toLinkId); //Only store the links that still here
						}
					}
				}
			}
			if(!currDownStreamLinkId.containsAll(oldDownStreamLinkId)) { //If there are some allowed before but not now.
				for(Id<Link> diffLinkId: Sets.difference(oldDownStreamLinkId, currDownStreamLinkId)) {
					linkToLinkForbiddenList.add(new Tuple<>(fromLinkId, diffLinkId));
				}
			}
		}
		return linkToLinkForbiddenList;
	}
	
	/**
	 * It would figure out if a route is no longer possible or not
	 * @param nr The NetworkRoute, should not be null.
	 * @param turnForbiddenList
	 * @param deletedLinks
	 * @return
	 */
	public static boolean isRouteNoLongerPossible(NetworkRoute nr, 
			List<Tuple<Id<Link>, Id<Link>>> turnForbiddenList, Set<Id<Link>> deletedLinks) {
		if(nr==null) {
			throw new IllegalArgumentException("The network route should not be null!");
		}
		
		for(Id<Link> linkId: deletedLinks) { //found if the route has a link removed
			if(nr.getLinkIds().contains(linkId) || nr.getStartLinkId().equals(linkId) ||
					nr.getEndLinkId().equals(linkId)){
				return true;
			}
		}
		
		for(Tuple<Id<Link>, Id<Link>> turnForbidden: turnForbiddenList) {
			if(nr.getStartLinkId().equals(turnForbidden.getFirst())) {
				if( (!nr.getLinkIds().isEmpty() && nr.getLinkIds().get(0).equals( turnForbidden.getSecond() )) ||
						(nr.getLinkIds().isEmpty() && nr.getEndLinkId().equals( turnForbidden.getSecond() ) )
						) {
					return true;
				}
			}else if(!nr.getLinkIds().isEmpty()) {
				int index = nr.getLinkIds().indexOf(turnForbidden.getFirst()); //Find the index of the first thing.
				if(index!=-1) {
					if(index!=nr.getLinkIds().size()-1 && nr.getLinkIds().get(index+1).equals(turnForbidden.getSecond())) {
						return true;
					}else if(index==nr.getLinkIds().size()-1 && nr.getEndLinkId().equals(turnForbidden.getSecond())){
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	public static int removeSomePlanByLinkId(Network oldNetwork, Network network, Lanes oldLanes, Lanes newLanes,
			TransitSchedule ts, Population population) {
		Network carOnlyNet = createCarOnlyNet(network);
		List<Tuple<Id<Link>, Id<Link>>> linkToLinkForbiddenList = getLinkToLinkForbiddenList( oldLanes,  oldNetwork, 
				 newLanes,  network);
		
		int populationCount = 0;
		int nextMsg = 2;
		int foundPlanCount = 0;
		Set<Id<Link>> linkIdSet = Sets.newHashSet();
		//Step 1: Identify all links that was in the old network but not the new network.
		for(Id<Link> linkId: oldNetwork.getLinks().keySet()) {
			if(!network.getLinks().keySet().contains(linkId)) {
				linkIdSet.add(linkId);
			}
		}
		
		//Step 2: Remove those links in the linkIdSet
		for(Person p: population.getPersons().values()) {			
			populationCount++;
			if(populationCount == nextMsg) {
				System.out.println("Processing population #"+populationCount);
				nextMsg *= 2;
			}
			
			if(p.getId().toString().equals("24277.0_1.0_75")) {
				System.out.print("");
			}
			int planSize = p.getPlans().size();
			int originPlanSize = p.getPlans().size();
			List<Integer> cleanWholeTransitTrip = new ArrayList<>();
			for(Iterator<?> it = p.getPlans().iterator(); it.hasNext();) {
				boolean found = false;
				int tripNum = 0;
				Plan plan = (Plan) it.next();
				Leg lastWalkLeg = null;
				Id<Link> lastStopLinkId = null;
				Activity lastPtInteraction = null;
				for(PlanElement pe: plan.getPlanElements()) {
					if(pe instanceof Leg && ((Leg) pe).getMode().equals("car")) {
						NetworkRoute nr = (NetworkRoute) ((Leg) pe).getRoute();
						for(Id<Link> linkId: linkIdSet) { //found if the route has a link removed
							if(nr!=null && isRouteNoLongerPossible(nr, linkToLinkForbiddenList, linkIdSet)){
								found = true;
								planSize--;
								foundPlanCount++;
								break;
							}
						}
					}else if(pe instanceof Leg && ((Leg) pe).getMode().equals("pt")){ //Refresh the transit route in case the access and egress stop changed
						ExperimentalTransitRoute tr = (ExperimentalTransitRoute) ((Leg) pe).getRoute();
						if(tr==null) {
							continue; //For debug purpose
						}
						TransitStopFacility accessStop = ts.getFacilities().get(tr.getAccessStopId());
						TransitStopFacility egressStop = ts.getFacilities().get(tr.getEgressStopId());
						if(accessStop == null || egressStop == null || accessStop.getId().toString().contains("GMB_M") || 
								egressStop.getId().toString().contains("GMB_M") || !ts.getTransitLines().containsKey(tr.getLineId())) {
							//If the stop or line is disappeared, just remove it.
							found = true;
							planSize--;
							foundPlanCount++;
						}else {
							//Set the new transit route.
							ExperimentalTransitRoute newRoute = new ExperimentalTransitRoute(accessStop, egressStop, tr.getLineId(), tr.getRouteId());
							newRoute.setDistance(RouteUtils.calcDistance(newRoute, ts, network));
							((Leg) pe).setRoute(newRoute);
							
							if(lastWalkLeg!=null) { //Set the direct walk leg
								lastWalkLeg.setRoute(createDirectWalkRoute(network, lastWalkLeg.getRoute().getStartLinkId(), 
										accessStop.getLinkId()));
								lastPtInteraction.setLinkId(accessStop.getLinkId());
								lastPtInteraction.setCoord(accessStop.getCoord());
							}
							lastStopLinkId = egressStop.getLinkId();
						}
					}else if(pe instanceof Leg && ((Leg) pe).getMode().equals("transit_walk")) {
						lastWalkLeg = (Leg) pe;
						if(lastStopLinkId!=null) {
							lastWalkLeg.setRoute(createDirectWalkRoute(network, lastStopLinkId, 
									lastWalkLeg.getRoute().getEndLinkId()));
							lastStopLinkId = null; //Used
						}
						
					}
					if(found && planSize==0) { //If found a plan
						planSize++; //Add back that planSize error
						if( ((Leg) pe).getMode().equals("pt")) {
							cleanWholeTransitTrip.add(tripNum);
						}
						((Leg) pe).setRoute(null);
						found = false; // to avoid it to be removed
					}
					if(pe instanceof Activity ) {
						Activity act = (Activity) pe;
						if ( ! act.getType().equals("pt interaction")) {
							tripNum++;
						}else {
							if(lastStopLinkId!=null)
								act.setLinkId(lastStopLinkId); //This is after a travel, so pt interaction is known
							else
								lastPtInteraction = act; //This is after a walk, the pt interaction link is unknown yet.
						}
						
						if(linkIdSet.contains( act.getLinkId() )) {
							Id<Link> newActivityLinkId = getNearestCarLink(carOnlyNet, act.getCoord());
							if(lastWalkLeg!=null) {
								lastWalkLeg.setRoute(createDirectWalkRoute(network, lastWalkLeg.getRoute().getStartLinkId(), 
										newActivityLinkId));
							}
							((Activity) pe).setLinkId(newActivityLinkId); //Adjust the link Id
							lastStopLinkId = newActivityLinkId;
						}
					}
					if(found) break; //Jump out if it is found
				}
				if(found) {
					it.remove();
				}
				if(!cleanWholeTransitTrip.isEmpty()) {
					Leg onlyLegLeft = null;
					tripNum = 0; //Second loop
					//This part remove the unnecessary trip elements of the route.
					for(Iterator<PlanElement> peIter = plan.getPlanElements().iterator(); peIter.hasNext(); ) {
						PlanElement pe = peIter.next();
						if(pe instanceof Leg && ((Leg) pe).getMode().equals("pt")){
							if(cleanWholeTransitTrip.contains(tripNum)) {
								if(onlyLegLeft == null) {
									onlyLegLeft = (Leg) pe;
								}else {
									peIter.remove();
								}
							}
						}else if(pe instanceof Leg && ((Leg) pe).getMode().equals("transit_walk")) {
							if(cleanWholeTransitTrip.contains(tripNum)) {
								peIter.remove();
							}
						}else if(pe instanceof Activity) {
							if ( ! ((Activity) pe).getType().equals("pt interaction")) {
								if(onlyLegLeft != null) {
									onlyLegLeft.setRoute(null);
									onlyLegLeft = null;
								}
								tripNum++;
							}else if(cleanWholeTransitTrip.contains(tripNum)){
								peIter.remove();
							}
						}
					}
				}
			}
			if(planSize < originPlanSize) {
				p.setSelectedPlan(p.getPlans().get(0)); //If some plans are deleted, select the first plan that are still there.
			}
			
		}
		return foundPlanCount;
	}
	
	public static void addStrategy(Config config, String strategy, String subpopulationName, double weight, int disableAfter) {
		if(weight <=0 || disableAfter <0) {
			throw new IllegalArgumentException("The parameters can't be less than or equal to 0!");
		}
		StrategySettings strategySettings = new StrategySettings() ;
		strategySettings.setStrategyName(strategy);
		strategySettings.setSubpopulation(subpopulationName);
		strategySettings.setWeight(weight);
		if(disableAfter>0) {
			strategySettings.setDisableAfter(disableAfter);
		}
		config.strategy().addStrategySettings(strategySettings);
	}
	
	/**
	 * 
	 * @param config The config to be processed
	 * @param subpopName The string of subpopulation name
	 * @param timeMutationWeight
	 * @param reRouteWeight The probability of reroute
	 * @param changeTripModeWeight The probability of change trip mode weight
	 * @param iterToSwitchOffInnovation 0 means never switch off.
	 */
	public static void createStrategies(Config config, String subpopName, double timeMutationWeight, double reRouteWeight, 
			double changeTripModeWeight, int iterToSwitchOffInnovation) {
		if(timeMutationWeight < 0 || reRouteWeight <0 || changeTripModeWeight <0 || iterToSwitchOffInnovation <0) {
			throw new IllegalArgumentException("The parameters can't be less than 0!");
		}
		
		if(timeMutationWeight>0) {
			addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator.toString(), subpopName, 
					timeMutationWeight, 0);
		}
		
		if(reRouteWeight > 0) {
			addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), subpopName, 
					reRouteWeight, iterToSwitchOffInnovation);
		}
		
		if(changeTripModeWeight>0) {
			addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), subpopName, 
					changeTripModeWeight, iterToSwitchOffInnovation);
		}
		
		addStrategy(config, DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta.toString(), subpopName, 
				1- changeTripModeWeight - timeMutationWeight - reRouteWeight, 0);
	}
	
	static void addLinkAndCost(RoadPricingSchemeImpl scheme, Id<Link> linkId, double cost) {
		RoadPricingUtils.addLink(scheme, linkId);
		RoadPricingUtils.addLinkSpecificCost(scheme, linkId, 0, 30 * 60 * 60, cost);
	}
	
	public static RoadPricingScheme createDefaultRoadPricingScheme(Scenario scenario, boolean tollAdjustment) {
		RoadPricingSchemeImpl scheme = RoadPricingUtils.addOrGetMutableRoadPricingScheme(scenario);
		//Tai Lam Tunnel
		addLinkAndCost(scheme, Id.createLinkId("879520_877115"), 48);
		addLinkAndCost(scheme, Id.createLinkId("877115_879520"), 48);
		//Tsing Ma Bridge
		addLinkAndCost(scheme, Id.createLinkId("982191_982156"), 15);
		addLinkAndCost(scheme, Id.createLinkId("982179_982192"), 15);
		//Tate's Cairn Tunnel
		addLinkAndCost(scheme, Id.createLinkId("27293_653210"), 20);
		addLinkAndCost(scheme, Id.createLinkId("653210_27313"), 20);
		//Lion Rock Tunnel
		addLinkAndCost(scheme, Id.createLinkId("23036_651880"), 8);
		addLinkAndCost(scheme, Id.createLinkId("21563_23109"), 8);
		//Tsing Sha Tunnel
		addLinkAndCost(scheme, Id.createLinkId("408371_656434"), 8);
		addLinkAndCost(scheme, Id.createLinkId("656434_408371"), 8);
		//Shing Mun Tunnel
		addLinkAndCost(scheme, Id.createLinkId("983228_17172"), 5);
		addLinkAndCost(scheme, Id.createLinkId("17156_983228"), 5);
		//TKO Tunnel
		addLinkAndCost(scheme, Id.createLinkId("506350_761158"), 3);
		addLinkAndCost(scheme, Id.createLinkId("761158_506351"), 3);
		//Aberdeen Tunnel
		addLinkAndCost(scheme, Id.createLinkId("101542_303010"), 5);
		addLinkAndCost(scheme, Id.createLinkId("303010_101542"), 5);
		//Full HK Cross Harbour
		if(tollAdjustment) {
			addLinkAndCost(scheme, Id.createLinkId("401851_404008"), 50);
			addLinkAndCost(scheme, Id.createLinkId("404008_401851"), 50);
			addLinkAndCost(scheme, Id.createLinkId("101375_501226"), 40);
			addLinkAndCost(scheme, Id.createLinkId("501226_101375"), 40);
			addLinkAndCost(scheme, Id.createLinkId("209709_506521"), 40);
			addLinkAndCost(scheme, Id.createLinkId("506520_209710"), 40);
		}else {
			addLinkAndCost(scheme, Id.createLinkId("401851_404008"), 70);
			addLinkAndCost(scheme, Id.createLinkId("404008_401851"), 70);
			addLinkAndCost(scheme, Id.createLinkId("101375_501226"), 20);
			addLinkAndCost(scheme, Id.createLinkId("501226_101375"), 20);
			addLinkAndCost(scheme, Id.createLinkId("209709_506521"), 25);
			addLinkAndCost(scheme, Id.createLinkId("506520_209710"), 25);
		}
		//HKI Only Cross Harbour
		addLinkAndCost(scheme, Id.createLinkId("WHCNorth"), 70);
		addLinkAndCost(scheme, Id.createLinkId("WHCSouth"), 70);
		addLinkAndCost(scheme, Id.createLinkId("CHTNorth"), 20);
		addLinkAndCost(scheme, Id.createLinkId("CHTSouth"), 20);
		addLinkAndCost(scheme, Id.createLinkId("EHCNorth"), 25);
		addLinkAndCost(scheme, Id.createLinkId("EHCSouth"), 25);
		
		RoadPricingUtils.setType(scheme, RoadPricingScheme.TOLL_TYPE_LINK);// Possibly change to cordon toll
		
		return scheme;
	}
	
	/**
	 * A function for conveniently scale down population
	 * @param population The scneario population
	 * @param portion The portion that would like to left, for example, 10% scale is 0.1, 20% scale is 0.2
	 */
	public static void scaleDownPopulation(Population population, double portion) {
		int i = 0;
		for (Iterator<?> it = population.getPersons().entrySet().iterator(); it.hasNext();) {
			it.next();
			if (Math.random() > portion) {
				it.remove();
			}
		}
	}
	
	/**
	 * A function for conveniently scale down population
	 * @param population The scneario population
	 * @param portion The portion that would like to left, for example, 10% scale is 0.1, 20% scale is 0.2
	 */
	public static void scaleDownAreaPopulation(Population population, double portion, 
			double minX, double maxX, double minY, double maxY) {
		int i = 0;
		for (Iterator<?> it = population.getPersons().entrySet().iterator(); it.hasNext(); ) {
			Entry<Id<Person>, Person> p = (Entry<Id<Person>, Person>) it.next();
			Activity firstAct = (Activity) p.getValue().getSelectedPlan().getPlanElements().get(0);
			if(minX <= firstAct.getCoord().getX() && firstAct.getCoord().getX() <= maxX &&
					minY <= firstAct.getCoord().getY() && firstAct.getCoord().getY() <= maxY)
				if (Math.random() > portion) {
					it.remove();
				}
		}
	}
	
	/**
	 * Scale up the population given the integer factor
	 * @param population
	 * @param factor
	 */
	public static void scaleUpPopulation(Population population, int factor) {
		List<Person> peopleToAdd = new ArrayList<>();
		for(Person person: population.getPersons().values()) {
			for(int i = 0; i < factor - 1; i++) { //It is factor - 1, because there is originally one copy
				Person newPerson = PopulationUtils.getFactory().createPerson(Id.createPersonId(person.getId().toString()+"_"+i));
				newPerson.getAttributes().putAttribute("subpopulation", person.getAttributes().getAttribute("subpopulation"));
				Plan thisPlan = PopulationUtils.createPlan(newPerson);
				PopulationUtils.copyFromTo(person.getSelectedPlan(), thisPlan);
				newPerson.addPlan(thisPlan);
				peopleToAdd.add(newPerson);
			}
		}
		for(Person p: peopleToAdd) {
			population.addPerson(p);
		}
	}
	
	/**
	 * A function for conveniently scale down the size of pt vehicles
	 * @param transitVehicles The transit vehicles container
	 * @param portion The portion that would like to left, for example, 10% scale is 0.1, 20% scale is 0.2
	 */
	public static void scaleDownPt(Vehicles transitVehicles, double portion) {
		for(VehicleType vt: transitVehicles.getVehicleTypes().values()) {
			VehicleCapacity vc = vt.getCapacity();
			vc.setSeats((int) Math.ceil(vc.getSeats().intValue() * portion));
			vc.setStandingRoom((int) Math.ceil(vc.getStandingRoom().intValue() * portion));
			vt.setPcuEquivalents(vt.getPcuEquivalents() * portion);
		}
	}
	
	/**
	 * Modify the scenario so that every person would be assigned a link that generates a multi-destination shortest path.
	 * 
	 * @param scenario Input Scenario
	 * @param inputDirectory This directory should contain network_TD.xml, and matching tables
	 * @param outputDirectory The directory to output the Population
	 * @throws FileNotFoundException
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public static void ignoreBridgeAndTakeShortestPath(Scenario scenario, String inputDirectory, String outputDirectory) throws FileNotFoundException, InterruptedException, IOException {
		Network ctsNet = NetworkUtils.createNetwork();
		new MatsimNetworkReader(ctsNet).readFile(inputDirectory+"network_TD.xml");
		AssignLinkToPlanActivity.defineMatchingTablePath(inputDirectory);
		AssignLinkToPlanActivity.run(scenario, ctsNet, 40, true);
		PopulationWriter popWriter=new PopulationWriter(scenario.getPopulation());
		popWriter.write(outputDirectory+"populationHKI.xml");
	}
	
	/**
	 * This function adjust the capacity of those links and lanes with signal to the designated capacity
	 * @param net
	 * @param lanes
	 * @param sd
	 * @param capacity
	 */
	public static void capacityAdjustmentsWithSignals(Network net, Lanes lanes, SignalsData sd, double capacity) {
		List<Id<Node>> nodesWithSignals = Lists.newArrayList();
		for(Id<SignalSystem> signalSystems: sd.getSignalSystemsData().getSignalSystemData().keySet()) {
			nodesWithSignals.add( Id.createNodeId( signalSystems.toString() ) );
		}
		List<Id<Link>> linksWithSignals = Lists.newArrayList();
		for(Link link: net.getLinks().values()) {
			if(nodesWithSignals.contains(link.getToNode().getId())){
				link.setCapacity(link.getNumberOfLanes() * capacity);
				linksWithSignals.add(link.getId());
			}
		}
		
		for(LanesToLinkAssignment l2l: lanes.getLanesToLinkAssignments().values()) {
			if(linksWithSignals.contains(l2l.getLinkId())){
				for(Lane lane: l2l.getLanes().values()) {
					lane.setCapacityVehiclesPerHour(lane.getNumberOfRepresentedLanes() * capacity);
				}
			}
		}
	}
	
	/**
	 * Reduce the PCU of vehicles
	 * @param vehicles
	 */
	public static VehicleType reduceVehiclePCU(Vehicles vehicles) {
		VehicleType defaultVt = null;
		for(VehicleType vt: vehicles.getVehicleTypes().values()) {
			if(vt.getPcuEquivalents()==1) {
				vt.setPcuEquivalents(0.7);
				defaultVt = vt;
			}
		}
		return defaultVt;
	}
	
	/**
	 * Add the vehicles to the respective agents
	 * @param scenario
	 * @param defaultVt
	 */
	public static void addVehicleToAgents(Scenario scenario, VehicleType defaultVt) {
		for(Person p: scenario.getPopulation().getPersons().values()) {
			Map<String, Id<Vehicle>> vehicleMap = new HashMap<>();
			Id<Vehicle> vId = Id.createVehicleId(p.getId());
			vehicleMap.put("car", vId);
			vehicleMap.put("taxi", vId);
			if(!scenario.getVehicles().getVehicles().containsKey(vId)) { //Add back the vehicle Id if it does not.
				scenario.getVehicles().addVehicle(VehicleUtils.createVehicle(vId, defaultVt));
			}
			VehicleUtils.insertVehicleIdsIntoAttributes(p, vehicleMap);
		}
	}
	
	public static void filterPopulationByTime(Population pop, double earliestStartTime, double latestEndTime) {
		//Clean the person
//		double earliestStartTime = 5 * 3600;
//		double latestEndTime = 12 * 3600;
		boolean adjusted = false;
		for (Iterator<?> person_it = pop.getPersons().entrySet().iterator(); person_it.hasNext();) {
			Map.Entry<Id<Person>, Person> entry = (Entry<Id<Person>, Person>) person_it.next();
			Person p = entry.getValue();
			if(p.getId().toString().equals("12386.0_1.0_6")) {
				System.out.print("Catched!");
			}
			
			boolean withInTime = false;
			adjusted = false;
			for (Iterator<PlanElement> it = p.getSelectedPlan().getPlanElements().iterator(); it.hasNext();) {
				PlanElement pe = it.next();
				if(pe instanceof Activity) {
					Activity act = (Activity) pe;
					double startTime = act.getStartTime().isDefined()?act.getStartTime().seconds():0;
					double endTime = act.getEndTime().isDefined()?act.getEndTime().seconds():50*3600;
					if( withInTime && act.getEndTime().isUndefined()) {
						endTime = latestEndTime - 1; //If it is undefined, we assume it is innocent.
					}
					
					if( endTime < earliestStartTime || startTime > latestEndTime || endTime > latestEndTime) { //Either too early or too late
						if(withInTime) {
							if(act.getType().equals("pt interaction")) {
								continue; //We do nothing for pt interaction
							}
							act.setEndTimeUndefined(); //Allow a free end time
							withInTime = false; //We keep the first in-time activity
							adjusted = true;
						}else {
							it.remove();
						}
					}else {
						if(adjusted) {
							it.remove(); //Still remove it once started
							continue;
							//throw new RuntimeException("The activity chain is not linear!");
						}
						withInTime = true;
					}
				}else if(!withInTime) {
					it.remove();
				}
			}
			if(p.getSelectedPlan().getPlanElements().size() == 0) {
				person_it.remove(); //Remove the person if there is no plan.
			}
		}
		
	}
	
	public void addPopulationTo(Population population, String subpopName, Coord coord, double portion) {
		List<Person> peopleToAdd = new ArrayList<>();
		int kaiTakCount = 0;
		for(Person person: population.getPersons().values()) {
			if(Math.random() < portion) {
				Person newPerson = PopulationUtils.getFactory().createPerson(Id.createPersonId("KaiTak_"+kaiTakCount));
				newPerson.getAttributes().putAttribute("subpopulation", subpopName);
				Plan thisPlan = PopulationUtils.createPlan(newPerson);
				newPerson.addPlan(thisPlan);
				
				List<PlanElement> pes = person.getSelectedPlan().getPlanElements();
				Activity firstAct = (Activity) pes.get(0);
				Activity firstActCopied = PopulationUtils.createActivity(firstAct);
				Activity secondAct = null;
				for(PlanElement pe: pes.subList(1, pes.size())) {
					if(pe instanceof Activity) {
						if(((Activity) pe).getType()!="pt interaction") {
							secondAct = (Activity) pe;
							break;
						}
					}
				}
				Activity secondActCopied = PopulationUtils.createActivity(secondAct);
				secondActCopied.setEndTimeUndefined();

				if(Math.random() < 0.5) { //To Kai Tak
					firstActCopied.setCoord(coord);
				}else { //From Kai Tak
					secondActCopied.setCoord(coord);
				}
				thisPlan.addActivity(firstActCopied);
				Leg leg = PopulationUtils.createLeg("pt");
				leg.setDepartureTime(firstActCopied.getEndTime().seconds());
				thisPlan.addLeg(leg);
				thisPlan.addActivity(secondActCopied);
				kaiTakCount++;
				peopleToAdd.add(newPerson);
			}
		}
	}
	
	/**
	 * Add standing and metro time parameters
	 * @param config
	 * @param utility
	 */
	public static void addStandingAndMTRTimeParams(Config config, double standingDisutility, double MTRTimeDisutility) {
		PlanCalcScoreConfigGroup.ModeParams standing = new PlanCalcScoreConfigGroup.ModeParams("standing");
		standing.setMarginalUtilityOfTraveling(standingDisutility);
		config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).addModeParams(standing);
		config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).addModeParams(standing);
		config.planCalcScore().getScoringParameters(PersonFixed_NAME).addModeParams(standing);
		config.planCalcScore().getScoringParameters(GVChange_NAME).addModeParams(standing);
		config.planCalcScore().getScoringParameters(GVFixed_NAME).addModeParams(standing);
		config.planCalcScore().getScoringParameters(PersonChangeWithoutCar2_NAME).addModeParams(standing);
		config.planCalcScore().getScoringParameters("default").addModeParams(standing);
		
		PlanCalcScoreConfigGroup.ModeParams metro = new PlanCalcScoreConfigGroup.ModeParams("metro");
		metro.setMarginalUtilityOfTraveling(MTRTimeDisutility);
		config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).addModeParams(metro);
		config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).addModeParams(metro);
		config.planCalcScore().getScoringParameters(PersonFixed_NAME).addModeParams(metro);
		config.planCalcScore().getScoringParameters(GVChange_NAME).addModeParams(metro);
		config.planCalcScore().getScoringParameters(GVFixed_NAME).addModeParams(metro);
		config.planCalcScore().getScoringParameters(PersonChangeWithoutCar2_NAME).addModeParams(metro);
		config.planCalcScore().getScoringParameters("default").addModeParams(metro);
	}
	
	/**
	 * Convert a rate of personChangeWithoutCar to time insensitive version
	 * @param scenario
	 * @param rate
	 */
	public static void convertSubpop(Scenario scenario, double rate) {
		for(Person p: scenario.getPopulation().getPersons().values()) {
			if(p.getAttributes().getAttribute("subpopulation").equals(PersonChangeWithoutCar_NAME)) {
				if(Math.random() > rate) {
					p.getAttributes().putAttribute("subpopulation", PersonChangeWithoutCar2_NAME);
				}
			}
		}
	}
	
	public static void removeBoundaryTrips(Scenario scenario) {
		for (Iterator<?> person_it = scenario.getPopulation().getPersons().entrySet().iterator(); person_it.hasNext();) {
			Map.Entry<Id<Person>, Person> entry = (Entry<Id<Person>, Person>) person_it.next();
			Person p = entry.getValue();
			for(PlanElement pe: p.getSelectedPlan().getPlanElements()) {
				if(pe instanceof Activity) {
					if(((Activity) pe).getType().equals("Boundary Control Point")) {
						person_it.remove();
						break;
					}
				}
			}
		}
	}
	
	/**
	 * This function clears the pt interaction in plans
	 * @param scenario
	 */
	public static void cleanPlans(Scenario scenario) {
		for (Iterator<?> person_it = scenario.getPopulation().getPersons().entrySet().iterator(); person_it.hasNext();) {
			Map.Entry<Id<Person>, Person> entry = (Entry<Id<Person>, Person>) person_it.next();
			Person p = entry.getValue();
			for(Plan plan: p.getPlans()) {
				List<Integer> ptInteractionIndex = new ArrayList<>();
				for(int i = 0; i < plan.getPlanElements().size(); i++){
					PlanElement pe = plan.getPlanElements().get(i);
					if(pe instanceof Activity) {
						if(((Activity) pe).getType().equals("pt interaction"))
							ptInteractionIndex.add(0, i);
					}else if(pe instanceof Leg) {
						((Leg) pe).setRoute(null); //Remove the route
					}
				}
				for(int index: ptInteractionIndex) {
					PopulationUtils.removeActivity(plan, index); //It removes the activity, and the following leg
				}
			}
		}
	}
	
//	public static void addEBPTR(Control controler) {
//		final WaitTimeStuckCalculator waitTimeCalculator = new WaitTimeStuckCalculator(controler.getScenario().getPopulation(), controler.getScenario().getTransitSchedule(), controler.getConfig().travelTimeCalculator().getTraveltimeBinSize(), (int) (controler.getConfig().qsim().getEndTime()-controler.getConfig().qsim().getStartTime()));
//		controler.getEvents().addHandler(waitTimeCalculator);
//		final StopStopTimeCalculatorImpl stopStopTimeCalculator = new StopStopTimeCalculatorImpl(controler.getScenario().getTransitSchedule(), controler.getConfig().travelTimeCalculator().getTraveltimeBinSize(), (int) (controler.getConfig().qsim().getEndTime()-controler.getConfig().qsim().getStartTime()));
//		controler.getEvents().addHandler(stopStopTimeCalculator);
//		final VehicleOccupancyCalculator vehicleOccupancyCalculator = new VehicleOccupancyCalculator(controler.getScenario().getTransitSchedule(), ((MutableScenario)controler.getScenario()).getTransitVehicles(), controler.getConfig().travelTimeCalculator().getTraveltimeBinSize(), (int) (controler.getConfig().qsim().getEndTime()-controler.getConfig().qsim().getStartTime()));
//		controler.getEvents().addHandler(vehicleOccupancyCalculator);
//		controler.addOverridingModule(new AbstractModule() {
//			@Override
//			public void install() {
//				bind(WaitTime.class).toInstance(waitTimeCalculator.get());
//				bind(StopStopTime.class).toInstance(stopStopTimeCalculator.get());
//				bind(VehicleOccupancy.class).toInstance(vehicleOccupancyCalculator.getVehicleOccupancy());
//				bind(TransitRouter.class).toProvider(TransitRouterEventsWSVFactory.class);
//			}
//		});
//	}

}
