package withinDay;

import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimPassengerAgent;
import org.matsim.core.mobsim.qsim.agents.PersonDriverAgentImpl;
import org.matsim.core.mobsim.qsim.agents.TransitAgent;
import org.matsim.core.mobsim.qsim.agents.TransitAgentImpl;
import org.matsim.core.mobsim.qsim.pt.PTPassengerAgent;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.functions.OnlyTravelTimeDependentScoringFunctionFactory;
import org.matsim.withinday.controller.ExampleWithinDayController;
import org.matsim.withinday.controller.WithinDayConfigGroup;
import org.matsim.withinday.controller.WithinDayModule;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.mobsim.WithinDayEngine;
import org.matsim.withinday.replanning.identifiers.ActivityEndIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.InitialIdentifierImplFactory;
import org.matsim.withinday.replanning.identifiers.LeaveLinkIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.LegPerformingIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.filter.LinkFilterFactory;
import org.matsim.withinday.replanning.identifiers.filter.NextTransportModeFilterFactory;
import org.matsim.withinday.replanning.identifiers.filter.ProbabilityFilterFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.AgentFilterFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringActivityAgentSelector;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringActivityIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringLegAgentSelector;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringLegIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.InitialIdentifier;
import org.matsim.withinday.replanning.identifiers.interfaces.InitialIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.tools.ActivityReplanningMap;
import org.matsim.withinday.replanning.identifiers.tools.LinkReplanningMap;
import org.matsim.withinday.replanning.replanners.CurrentLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.InitialReplannerFactory;
import org.matsim.withinday.replanning.replanners.NextLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayInitialReplannerFactory;

import com.google.common.collect.Sets;

public class HKWithinDayController implements StartupListener {
	// yyyy I think that for the now existing guice approach this example has too many factories at too many levels. kai, feb'16

	/*
	 * Define the Probability that an Agent uses the
	 * Replanning Strategy. It is possible to assign
	 * multiple Strategies to the Agents.
	 */
	private double pInitialReplanning = 0.1;
	private double pDuringActivityReplanning = 1.0;
	private double pDuringLegReplanning = 0.10;
	
	private InitialIdentifierFactory initialIdentifierFactory;
	private DuringActivityIdentifierFactory duringActivityIdentifierFactory;
	private DuringLegIdentifierFactory duringLegIdentifierFactory;
	private InitialIdentifier initialIdentifier;
	private DuringActivityAgentSelector duringActivityIdentifier;
	private DuringLegAgentSelector duringLegIdentifier;
	private WithinDayInitialReplannerFactory initialReplannerFactory;
	private WithinDayDuringActivityReplannerFactory duringActivityReplannerFactory;
	private WithinDayDuringLegReplannerFactory duringLegReplannerFactory;
	private AgentFilterFactory initialProbabilityFilterFactory;
	private AgentFilterFactory duringActivityProbabilityFilterFactory;
	private AgentFilterFactory duringLegProbabilityFilterFactory;
	
	@Inject private Scenario scenario;
	@Inject private Provider<TripRouter> tripRouterProvider;
	@Inject private MobsimDataProvider mobsimDataProvider;
	@Inject private WithinDayEngine withinDayEngine;
	@Inject private ActivityReplanningMap activityReplanningMap;
	@Inject private LinkReplanningMap linkReplanningMap;
	@Inject private LeastCostPathCalculatorFactory pathCalculatorFactory;
	@Inject private Map<String,TravelDisutilityFactory> travelDisutilityFactories;
	@Inject private Map<String,TravelTime> travelTimes;


	/*
	 * ===================================================================
	 * main
	 * ===================================================================
	 */
	public static void main(final String[] args) {
		if ((args == null) || (args.length == 0)) {
			System.out.println("No argument given!");
//			System.out.println("Usage: Controler config-file [dtd-file]");
			// the [dtd-file] argument was not honoured when I found this fca451f279bd4c8e3921846597d657614d5a5832 . kai, may'17
			System.out.println("Usage: Controler config-file");
			System.out.println();
			System.exit(-1);
		} 
		
		Config config = ConfigUtils.loadConfig( args[0] , new WithinDayConfigGroup() ) ;
		config.controler().setRoutingAlgorithmType( ControlerConfigGroup.RoutingAlgorithmType.Dijkstra );

		Scenario scenario = ScenarioUtils.loadScenario( config) ;
		
		final Controler controler = new Controler(scenario);
		configure(controler);
		controler.run();
	}

	static void configure(Controler controler) {
		// factored out for testing. kai, jun'16
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new WithinDayModule());
				addControlerListenerBinding().to(ExampleWithinDayController.class);

				addTravelDisutilityFactoryBinding(TransportMode.car).toInstance(new OnlyTimeDependentTravelDisutilityFactory());

				// Use a Scoring Function that only scores the travel times:
				// (yy but why? kai,  jun'16)
				bindScoringFunctionFactory().toInstance(new OnlyTravelTimeDependentScoringFunctionFactory());
			}
		});
	}

	@Override
	public void notifyStartup(StartupEvent event) {
		this.initReplanners(  );
	}
	
	private void initReplanners( ) {
		Network network = this.scenario.getNetwork() ;
		
		TravelTime travelTime = travelTimes.get( TransportMode.car ) ;

		TravelDisutilityFactory travelDisutilityFactory = travelDisutilityFactories.get( TransportMode.car ) ;
		TravelDisutility travelDisutility = travelDisutilityFactory.createTravelDisutility(travelTime ) ;

		LeastCostPathCalculator pathCalculator = pathCalculatorFactory.createPathCalculator(network, travelDisutility, travelTime ) ;
		
		//The initial replanning is bugged, and should not be necessary.
//		this.initialIdentifierFactory = new InitialIdentifierImplFactory(this.mobsimDataProvider);
//		this.initialProbabilityFilterFactory = new HKAgentFilterFactory(Sets.newHashSet(MobsimAgent.class, TransitAgent.class), this.mobsimDataProvider, 
//				this.pInitialReplanning);
//		this.initialIdentifierFactory.addAgentFilterFactory(this.initialProbabilityFilterFactory);
//		this.initialIdentifier = initialIdentifierFactory.createIdentifier();
//		this.initialReplannerFactory = new InitialReplannerFactory(this.scenario, this.withinDayEngine, this.tripRouterProvider);
//		this.initialReplannerFactory.addIdentifier(this.initialIdentifier);
//		this.withinDayEngine.addIntialReplannerFactory(this.initialReplannerFactory); 
		this.duringActivityIdentifierFactory = new ActivityEndIdentifierFactory(this.activityReplanningMap);
		Set<Id<Link>> links = Sets.newHashSet(Id.createLinkId("101447_101663"), Id.createLinkId("101415_101416")
				, Id.createLinkId("105132_101459"), Id.createLinkId("101391_101676"), Id.createLinkId("101445_101446"),
				Id.createLinkId("105140_101565"),Id.createLinkId("101563_105128"));
		this.duringActivityProbabilityFilterFactory = new HKAgentFilterFactory(Sets.newHashSet(PersonDriverAgentImpl.class, TransitAgent.class), this.mobsimDataProvider, //or MobsimAgent.class, TransitAgent.class
				links, this.pDuringActivityReplanning, 6 * 3600, 18.5 * 3600);
		this.duringActivityIdentifierFactory.addAgentFilterFactory(this.duringActivityProbabilityFilterFactory);
		this.duringActivityIdentifier = duringActivityIdentifierFactory.createIdentifier();
		this.duringActivityReplannerFactory = new NextLegReplannerFactory(this.scenario, this.withinDayEngine, this.tripRouterProvider, null);
		this.duringActivityReplannerFactory.addIdentifier(this.duringActivityIdentifier);
		this.withinDayEngine.addDuringActivityReplannerFactory(this.duringActivityReplannerFactory);

		this.duringLegIdentifierFactory = new LegPerformingIdentifierFactory(linkReplanningMap, mobsimDataProvider);//new LeaveLinkIdentifierFactory(this.linkReplanningMap, this.mobsimDataProvider);
		this.duringLegProbabilityFilterFactory = new HKAgentFilterFactory(Sets.newHashSet(PersonDriverAgentImpl.class, 
				TransitAgent.class), this.mobsimDataProvider, //or MobsimAgent.class, TransitAgent.class
				links, this.pDuringActivityReplanning, 7 * 3600, 18 * 3600);
//				new HKAgentFilterFactory(Sets.newHashSet(TransitAgentImpl.class, PTPassengerAgent.class), this.mobsimDataProvider, //or MobsimAgent.class, TransitAgent.class
//				this.pDuringLegReplanning);
		this.duringLegIdentifierFactory.addAgentFilterFactory(this.duringLegProbabilityFilterFactory);
		this.duringLegIdentifier = this.duringLegIdentifierFactory.createIdentifier();
		this.duringLegReplannerFactory = new CurrentLegReplannerFactory(this.scenario, this.withinDayEngine, pathCalculator);
		this.duringLegReplannerFactory.addIdentifier(this.duringLegIdentifier);
		this.withinDayEngine.addDuringLegReplannerFactory(this.duringLegReplannerFactory);
	}
}
