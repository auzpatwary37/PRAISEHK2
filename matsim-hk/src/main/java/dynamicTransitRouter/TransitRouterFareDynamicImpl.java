package dynamicTransitRouter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.PreProcessDijkstra;
import org.matsim.core.router.RoutingRequest;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;

import dynamicTransitRouter.costs.MultiNodeAStarEucliean;
import dynamicTransitRouter.costs.StopStopTime;
import dynamicTransitRouter.costs.TransferWalkingTime;
import dynamicTransitRouter.costs.VehicleOccupancy;
import dynamicTransitRouter.costs.WaitingTime;
import dynamicTransitRouter.costs.MultiNodeAStarEucliean.InitialNode;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import running.RunUtils;
import transitFareAndHandler.FareTransitRouterConfig;

/**
 * It is like the TransitRouteVariableImpl in eventsBasedPTRouter, but changed the transit network to mine one.
 * @author eleead
 *
 */
public class TransitRouterFareDynamicImpl implements TransitRouter {
	private static TransitRouterNetworkHR transitNetwork;

	private Map<String, MultiNodeAStarEucliean> dijkstraMap = new HashMap<>();
	protected static Map<String, FareTransitRouterConfig> tConfig;
	protected Map<String, TransitRouterNetworkTravelTimeAndDisutility> ttCalculatorMap = new HashMap<>();
	private static AtomicInteger directWalkCount; //A thread-safe counter.
	
	public static double distanceFactor; //The factor of distance
	public static char aStarSetting; // A star settings
	private static final Logger log = Logger.getLogger(TransitRouterFareDynamicImpl.class);
	public static Map<TransitLineRoute, Set<TransitLineRoute>> lineRouteRelationMap;
	
	private static boolean processedNetwork = false;
	private static FareTransitRouterConfig randomTConfig;

	@Inject
	public TransitRouterFareDynamicImpl(final Scenario scenario, final WaitingTime waitTime, 
			final StopStopTime stopStopTime, final VehicleOccupancy vehicleOccupancy, 
			final Map<String, FareCalculator> fareCals, TransferDiscountCalculator tdc,
			TransferWalkingTime walking){
		this(scenario);
		for(String subpop: tConfig.keySet()) {
			setFareCalculatorAndDijkstra(subpop, new FareDynamicTransitTimeAndDisutility(tConfig.get(subpop), 
					waitTime, stopStopTime, vehicleOccupancy, fareCals, tdc,
					new PreparedTransitSchedule(scenario.getTransitSchedule())));
		}
		if(!processedNetwork) {
			PreProcessDijkstra preProcessDijkstra = new PreProcessDijkstra();
			preProcessDijkstra.run(transitNetwork);
			processedNetwork = true;
		}
	}
	
	protected TransitRouterFareDynamicImpl(final Scenario scenario) {
		if(lineRouteRelationMap == null) {
			lineRouteRelationMap = RunUtils.getLineRouteRelationList(scenario.getTransitSchedule());
		}
		//TODO: Update the config so that there are multiple parameters for multiple populations
		if(tConfig==null) {
			tConfig = new HashMap<>();
			PlanCalcScoreConfigGroup pcsConfig = scenario.getConfig().planCalcScore();
			for(String subpopulation: pcsConfig.getScoringParametersPerSubpopulation().keySet()) {
				ScoringParameterSet pSet = pcsConfig.getScoringParameters(subpopulation);
				FareTransitRouterConfig tConfigsub = new FareTransitRouterConfig(scenario.getConfig().planCalcScore(),
						scenario.getConfig().plansCalcRoute(), scenario.getConfig().transitRouter(),
						scenario.getConfig().vspExperimental(), subpopulation, pSet);
				tConfig.put(subpopulation, tConfigsub);
				randomTConfig = tConfigsub;
			}
//			tConfig = new FareTransitRouterConfig(scenario.getConfig().planCalcScore(),
//				scenario.getConfig().plansCalcRoute(), scenario.getConfig().transitRouter(),
//				scenario.getConfig().vspExperimental());
		}
		if(transitNetwork==null)
			transitNetwork = TransitRouterNetworkHR.createFromSchedule(scenario.getNetwork(), 
					scenario.getTransitSchedule(), randomTConfig.getBeelineWalkConnectionDistance());
	}
	
	/**
	 * Reset the essential static variables, should be run in startup
	 */
	public static void reset() {
		tConfig = null;
		transitNetwork = null;
		lineRouteRelationMap = null;
		processedNetwork = false;
		randomTConfig = null;
	}
	
	/**
	 * It is constructed for the new calculator and dijkstra
	 * @param ttCalculator
	 * @param dijkstra
	 */
	protected void setFareCalculatorAndDijkstra(String subpopulation, TransitRouterNetworkTravelTimeAndDisutility ttCalculator) {
		this.ttCalculatorMap.put(subpopulation, ttCalculator);
		this.dijkstraMap.put(subpopulation, new MultiNodeAStarEucliean(transitNetwork, 
				ttCalculator, ttCalculator, distanceFactor));
	}
	
	@Deprecated
	/**
	 * It is the constructor using in the test case.
	 * @param config
	 * @param ttCalculator
	 * @param routerNetwork
	 */
	public TransitRouterFareDynamicImpl(final FareTransitRouterConfig config, 
			final TransitRouterNetworkTravelTimeAndDisutility ttCalculator, final TransitRouterNetworkHR routerNetwork) {
		if(lineRouteRelationMap == null) {
			lineRouteRelationMap = new HashMap<>();
		}
		if(tConfig==null) {
			tConfig = new HashMap<>();
			tConfig.put("default", config);
			randomTConfig = config;
		}
		if(transitNetwork==null)
			transitNetwork = routerNetwork;
		setFareCalculatorAndDijkstra("default", ttCalculator);
		PreProcessDijkstra preProcessDijkstra = new PreProcessDijkstra();
		preProcessDijkstra.run(routerNetwork);
	}
	
	public static void initializeDirectWalkCount() {
		directWalkCount = new AtomicInteger();
	}
	
	//The walk count is accessed in PTRecordHandler
	public static int getDirectWalkCount() {
		return directWalkCount.get();
	}
	
	public static void resetWalkCounter() {
		directWalkCount.set(0);
	}
	
	private Map<Node, InitialNode> locateWrappedNearestTransitNodes(String subPopulation, Coord coord, double departureTime){
		//(String) person.getAttributes().getAttribute("subpopulation");
		Collection<TransitRouterNetworkHR.TransitRouterNetworkNode> nearestNodes = 
				transitNetwork.getNearestTSFNodes(coord, tConfig.get(subPopulation).getSearchRadius());
		if (nearestNodes.size() < 2) {
			// also enlarge search area if only one stop found, maybe a second one is near the border of the search area
			TransitRouterNetworkHR.TransitRouterNetworkNode nearestNode = transitNetwork.getNearestNode(coord);	//nearestNodes is not guaranteed to have anything JLo
			double distance = CoordUtils.calcEuclideanDistance(coord, nearestNode.getCoord());
			nearestNodes = transitNetwork.getNearestTSFNodes(coord, distance + tConfig.get(subPopulation).getExtensionRadius());
		}
		Map<Node, InitialNode> wrappedNearestNodes = new LinkedHashMap<Node, InitialNode>();
		for (TransitRouterNetworkHR.TransitRouterNetworkNode node : nearestNodes) {
			Coord toCoord = node.getCoord();
			double initialTime = getWalkTime(subPopulation, coord, toCoord);
			double initialCost = getWalkDisutility(subPopulation, coord, toCoord);
			wrappedNearestNodes.put(node, new InitialNode(initialCost, initialTime + departureTime));
		}
		return wrappedNearestNodes;
	}
	
	private double getWalkTime(String subPopulation, Coord coord, Coord toCoord) {
		return this.ttCalculatorMap.get(subPopulation).getWalkTravelTime(null, coord, toCoord);
	}
	
	private double getWalkDisutility(String subPopulation, Coord coord, Coord toCoord) {
		return this.ttCalculatorMap.get(subPopulation).getWalkTravelDisutility(null, coord, toCoord);
	}
	
	/**
	 * The main function for transit. Calculate the route from fromFacility to toFacility.
	 */
	@Override
	public List<Leg> calcRoute(final RoutingRequest request) {
    	final Facility fromFacility = request.getFromFacility();
    	final Facility toFacility = request.getToFacility();
    	final double departureTime = request.getDepartureTime();
    	final Person person = request.getPerson();
  
    	final String subpopulation = (String) person.getAttributes().getAttribute("subpopulation");
    	
		// find possible start stops
		Map<Node, InitialNode> wrappedFromNodes = this.locateWrappedNearestTransitNodes(subpopulation, fromFacility.getCoord(), departureTime);
		// find possible end stops
		Map<Node, InitialNode> wrappedToNodes  = this.locateWrappedNearestTransitNodes(subpopulation, toFacility.getCoord(), departureTime);

		// find routes between start and end stops
		Path p = null;
		try {
			p = this.dijkstraMap.get(subpopulation).calcLeastCostPath(wrappedFromNodes, wrappedToNodes, fromFacility.getCoord(), toFacility.getCoord(), person);
		} catch (Throwable ex) { //If the path calculation somehow went wrong, use a direct walk instead.
			ex.printStackTrace();
			log.warn("The path of "+person.getId()+" at "+ departureTime +" cannot be found!");
			return createDirectWalk(fromFacility, toFacility, departureTime);
		}
		
		double directWalkDistance = CoordUtils.calcEuclideanDistance(fromFacility.getCoord(), toFacility.getCoord());
		double directWalkTime = directWalkDistance / randomTConfig.getBeelineWalkSpeed();
		double directWalkCost = directWalkTime * ( 0 - tConfig.get(subpopulation).getMarginalUtilityOfTravelTimeWalk_utl_s());
		double pathCost = (p == null) ? Double.MAX_VALUE : p.travelCost ; 
		//p.travelCost + wrappedFromNodes.get(p.nodes.get(0)).initialCost + wrappedToNodes.get(p.nodes.get(p.nodes.size() - 1)).initialCost;
				//Changed to not remove and add back in the initial walking cost -JLo
		
		if (directWalkCost * randomTConfig.getDirectWalkFactor() < pathCost) {
//			if(directWalkDistance>5000)		//temp check
//				log.warn("This guy "+person.getId()+" is walking more than 5km!!!");
			return createDirectWalk(fromFacility, toFacility, departureTime);
		}

		return convertPathToLegList( departureTime, p, fromFacility.getCoord(), toFacility.getCoord(), subpopulation ) ;
	}
	
	List<Leg> createDirectWalk(Facility fromFacility, Facility toFacility, double departureTime){
		directWalkCount.getAndIncrement();
		List<Leg> legs = new ArrayList<Leg>();
		Leg leg = PopulationUtils.createLeg(TransportMode.transit_walk);
		double walkDistance = CoordUtils.calcEuclideanDistance(fromFacility.getCoord(), toFacility.getCoord());
		Route walkRoute = RouteUtils.createGenericRouteImpl(null, null);
		walkRoute.setDistance(walkDistance);
		leg.setRoute(walkRoute);
		leg.setTravelTime(walkDistance/randomTConfig.getBeelineWalkSpeed());
		leg.setDepartureTime(departureTime);
		legs.add(leg);
		return legs;
	}
	
    /**
     * This function is solely for debug
     * @param fromCoord
     * @param toCoord
     * @param departureTime
     * @param person
     * @return
     */
	public Path calcPathRoute(final Coord fromCoord, final Coord toCoord, final double departureTime, final String subpopulation) {
		// find possible start stops
		Map<Node, InitialNode> wrappedFromNodes = this.locateWrappedNearestTransitNodes(subpopulation, fromCoord, departureTime);
		// find possible end stops
		Map<Node, InitialNode> wrappedToNodes  = this.locateWrappedNearestTransitNodes(subpopulation, toCoord, departureTime);
		// find routes between start and end stops
		Path path = this.dijkstraMap.get(subpopulation).calcLeastCostPath(wrappedFromNodes, wrappedToNodes, fromCoord, toCoord, null);
		if (path == null) {
			return null;
		}
		double directWalkDistance = CoordUtils.calcEuclideanDistance(fromCoord, toCoord);
		double directWalkTime = directWalkDistance / randomTConfig.getBeelineWalkSpeed();
		double directWalkCost = directWalkTime * ( 0 - tConfig.get(subpopulation).getMarginalUtilityOfTravelTimeWalk_utl_s());
		double pathCost = path.travelCost + wrappedFromNodes.get(path.nodes.get(0)).initialCost + wrappedToNodes.get(path.nodes.get(path.nodes.size() - 1)).initialCost;
		//Changed to not remove and add back in the initial walking cost -JLo
		if (directWalkCost * randomTConfig.getDirectWalkFactor()< pathCost) {
//			if(directWalkDistance>5000)		//temp check
//				log.warn("This guy "+person.getId()+" is walking more than 5km!!!");
			return new Path(new ArrayList<Node>(), new ArrayList<Link>(), directWalkTime, directWalkCost);
		}
		double pathTime = path.travelTime + wrappedFromNodes.get(path.nodes.get(0)).initialTime + wrappedToNodes.get(path.nodes.get(path.nodes.size() - 1)).initialTime - 2*departureTime;
		return new Path(path.nodes, path.links, pathTime, pathCost);
	}
	
	public List<Leg> convertPathToLegList( double departureTime, Path p, Coord fromCoord, Coord toCoord, String subpopulation) {
		List<Leg> legs = new ArrayList<Leg>();
		Leg leg;
		double walkDistance, walkWaitTime, travelTime = 0;
		Route walkRoute;
		Coord coord = fromCoord;
		TransitRouteStop stop = null;
		double time = departureTime;
		
		for (Link link : p.links) {
			TransitRouterNetworkHR.TransitRouterNetworkLink l = (TransitRouterNetworkHR.TransitRouterNetworkLink) link;
			if(l.route!=null) {
				//in line link
				double ttime = ttCalculatorMap.get(subpopulation).getLinkTravelTime(l, time, null, null);
				travelTime += ttime;
				//time += ttime;
			}
			else if(l.fromNode.route!=null) {
				//inside link
				leg = PopulationUtils.createLeg(TransportMode.pt);
				DefaultTransitPassengerRoute ptRoute = new DefaultTransitPassengerRoute(stop.getStopFacility(), l.fromNode.line, l.fromNode.route, l.fromNode.stop.getStopFacility());
				leg.setRoute(ptRoute);
				leg.setDepartureTime(time);
				leg.setTravelTime(travelTime);
				time += travelTime;
				legs.add(leg);
				travelTime = 0;
				stop = l.fromNode.stop;
				coord = l.fromNode.stop.getStopFacility().getCoord();
			}
			else if(l.toNode.route!=null) {
				//wait link
				leg = PopulationUtils.createLeg(TransportMode.transit_walk);
				walkWaitTime = -1;
				if(stop != null)
					walkWaitTime = TransferWalkingTime.getWalkingTime(stop.getStopFacility().getId(), l.toNode.stop.getStopFacility().getId());
				if(walkWaitTime != -1) {
					walkDistance = walkWaitTime * randomTConfig.getBeelineWalkSpeed();
				}else {
					walkDistance = CoordUtils.calcEuclideanDistance(coord, l.toNode.stop.getStopFacility().getCoord()); 
					walkWaitTime = walkDistance/randomTConfig.getBeelineWalkSpeed()/*+ttCalculator.getLinkTravelTime(l, time+walkDistance/this.config.getBeelineWalkSpeed(), person, null)*/;
				}
				walkRoute = RouteUtils.createGenericRouteImpl(stop==null?null:stop.getStopFacility().getLinkId(), l.toNode.stop.getStopFacility().getLinkId());
				walkRoute.setDistance(walkDistance);
				leg.setDepartureTime(time);
				leg.setRoute(walkRoute);
				leg.setTravelTime(walkWaitTime);
				legs.add(leg);
				stop = l.toNode.stop;
				time += walkWaitTime;
			}
			
		}
		leg = PopulationUtils.createLeg(TransportMode.transit_walk);
		walkDistance = CoordUtils.calcEuclideanDistance(coord, toCoord); 
		walkWaitTime = walkDistance/randomTConfig.getBeelineWalkSpeed();
		walkRoute = RouteUtils.createGenericRouteImpl(stop==null?null:stop.getStopFacility().getLinkId(), null);
		walkRoute.setDistance(walkDistance);
		leg.setDepartureTime(time);
		leg.setRoute(walkRoute);
		leg.setTravelTime(walkWaitTime);
		legs.add(leg);
		return legs;
	}

	public TransitRouterNetworkHR getTransitRouterNetwork() {
		return transitNetwork;
	}
}
