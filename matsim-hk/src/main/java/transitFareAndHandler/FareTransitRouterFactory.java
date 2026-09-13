package transitFareAndHandler;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.matsim.core.config.Config;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterImpl;
import org.matsim.pt.router.TransitRouterNetwork;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import dynamicTransitRouter.fareCalculators.FareCalculator;

/**
 * A factory unit to get the TransitRouter with fare.
 * 
 * @author eleead
 *
 */
@Deprecated
public class FareTransitRouterFactory implements Provider<TransitRouter> {

	private final FareTransitRouterConfig config;
	private final TransitRouterNetwork routerNetwork;
	private final PreparedTransitSchedule preparedTransitSchedule;
	private final TransitFareTravelDisutility disutility;

	@Inject
	FareTransitRouterFactory(final TransitSchedule schedule, final Config config,
			Map<String, FareCalculator> fareCals) {
		this(schedule, new FareTransitRouterConfig(config.planCalcScore(), config.plansCalcRoute(),
				config.transitRouter(), config.vspExperimental(), 0), fareCals);
	}

	public FareTransitRouterFactory(final TransitSchedule schedule, final FareTransitRouterConfig config,
			Map<String, FareCalculator> fareCals) {
		this.config = config;
		this.routerNetwork = TransitRouterNetwork.createFromSchedule(schedule,
				this.config.getBeelineWalkConnectionDistance());
		this.preparedTransitSchedule = new PreparedTransitSchedule(schedule);
		this.disutility = new TransitFareTravelDisutility(config, preparedTransitSchedule, schedule, fareCals);
	}

	@Override
	public TransitRouter get() {
		TransitRouterNetworkTravelTimeAndDisutility ttCalculator = new TransitRouterNetworkTravelTimeAndDisutility(
				this.config, this.preparedTransitSchedule);
		return new TransitRouterImpl(this.config, this.preparedTransitSchedule, this.routerNetwork, ttCalculator,
				disutility);
	}

}
