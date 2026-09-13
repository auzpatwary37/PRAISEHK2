package transitFareAndHandler;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.pt.config.TransitRouterConfigGroup;
import org.matsim.pt.router.TransitRouterConfig;

/**
 * This class essentially has every function of a basic TransitRouterConfig, but
 * added marginal utility of money for the fare conversion to utility.
 * 
 * @author eleead
 *
 */
public class FareTransitRouterConfig extends TransitRouterConfig {

	private double marginalUtilityOfMoney;
	private String subPopulation;
	private double standingTimeDisutility;
	
	//Standing and time disutility for MTR
	private double metroTimeDisutility;
	private double metroStandingTimeDisutility;
	
	public FareTransitRouterConfig(Config config) {
		super(config);
	}

	/**
	 * This is the fare transit router for default parameters
	 * @param pcsConfig
	 * @param pcrConfig
	 * @param trConfig
	 * @param vspConfig
	 */
	public FareTransitRouterConfig(final PlanCalcScoreConfigGroup pcsConfig, final PlansCalcRouteConfigGroup pcrConfig,
			final TransitRouterConfigGroup trConfig, final VspExperimentalConfigGroup vspConfig, double standingTimeDisutility) {
		super(pcsConfig, pcrConfig, trConfig, vspConfig);
		this.marginalUtilityOfMoney = pcsConfig.getMarginalUtilityOfMoney();
		this.standingTimeDisutility = standingTimeDisutility / 3600;
	}
	
	/**
	 * This adjusts the fare transit router config for specific subpopulation
	 * @param pcsConfig
	 * @param pcrConfig
	 * @param trConfig
	 * @param vspConfig
	 * @param scoringParameters
	 */
	public FareTransitRouterConfig(final PlanCalcScoreConfigGroup pcsConfig, final PlansCalcRouteConfigGroup pcrConfig,
			final TransitRouterConfigGroup trConfig, final VspExperimentalConfigGroup vspConfig, 
			String subPopulation, ScoringParameterSet scoringParameters) {
		super(pcsConfig, pcrConfig, trConfig, vspConfig);
		this.marginalUtilityOfMoney = pcsConfig.getMarginalUtilityOfMoney();
		this.subPopulation = subPopulation;
		
		this.setMarginalUtilityOfTravelTimeWalk_utl_s(scoringParameters.getModes().get(TransportMode.walk).
				getMarginalUtilityOfTraveling() /3600.0 - scoringParameters.getPerforming_utils_hr()/3600.) ;
		
		this.setMarginalUtilityOfTravelDistanceWalk_utl_m(scoringParameters.getMarginalUtilityOfMoney() *
				scoringParameters.getModes().get(TransportMode.walk).getMonetaryDistanceRate() +
				scoringParameters.getModes().get(TransportMode.walk).getMarginalUtilityOfDistance());
		
		// pt:
		this.setMarginalUtilityOfTravelTimePt_utl_s(scoringParameters.getModes().get(TransportMode.pt).
				getMarginalUtilityOfTraveling() /3600.0 -scoringParameters.getPerforming_utils_hr()/3600.) ;

		this.setMarginalUtilityOfTravelDistancePt_utl_m(scoringParameters.getMarginalUtilityOfMoney() *
				scoringParameters.getModes().get(TransportMode.pt).getMonetaryDistanceRate() +
				scoringParameters.getModes().get(TransportMode.pt).getMarginalUtilityOfDistance());

		this.setMarginalUtilityOfWaitingPt_utl_s(scoringParameters.getMarginalUtlOfWaitingPt_utils_hr() / 3600.0 - 
				scoringParameters.getPerforming_utils_hr()/3600.);

		this.setUtilityOfLineSwitch_utl(scoringParameters.getUtilityOfLineSwitch());
		
		this.standingTimeDisutility = scoringParameters.getModes().get("standing").getMarginalUtilityOfTraveling() / 3600;
		this.metroTimeDisutility = scoringParameters.getModes().get("metro").getMarginalUtilityOfTraveling() / 3600;
	}

	public double getMarginalUtilityOfMoney() {
		return this.marginalUtilityOfMoney;
	}
	
	public double getStandingTimeDisutility() {
		return this.standingTimeDisutility;
	}
	
	public double getMetroTimeDisutility() {
		return this.metroTimeDisutility;
	}
	
	public double getMetroStandingTimeDisutility() {
		return this.metroStandingTimeDisutility;
	}
	
	public String getSubPopulation() {
		return subPopulation;
	}

}
