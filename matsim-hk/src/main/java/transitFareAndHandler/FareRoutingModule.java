package transitFareAndHandler;

import org.matsim.pt.router.TransitRouter;

import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;

import java.util.HashMap;
import java.util.Map;

import org.matsim.core.controler.AbstractModule;

@Deprecated
public final class FareRoutingModule extends AbstractModule {

	private FareCalculator busCalculator;

	/**
	 * 
	 * @param busFareCalculator
	 *            The fare calculator for calculating the bus fare.
	 */
	public FareRoutingModule(FareCalculator busFareCalculator) {
		this.busCalculator = busFareCalculator;
	}

	@Override
	public void install() {
		if (getConfig().transit().isUseTransit()) {
			// Bind the fare to fare calculator
			bind(TransitRouter.class).toProvider(FareTransitRouterFactory.class);

			MapBinder<String, FareCalculator> mapbinder = MapBinder.newMapBinder(binder(), String.class,
					FareCalculator.class);

			// Bind the MTR fare calculator
			mapbinder.addBinding("train").to(MTRFareCalculator.class).in(Scopes.SINGLETON);
			bind(String.class).annotatedWith(Names.named("trainFareInput")).toInstance("input/mtr_lines_fares.csv"); // File
																														// path

			// The bus fare calculator
			if (busCalculator == null)
				mapbinder.addBinding("bus").toInstance(new UniformFareCalculator(3.0));
			else {
				mapbinder.addBinding("bus").toInstance(this.busCalculator);
			}

			// The tram fare calculator
			mapbinder.addBinding("tram").toInstance(new UniformFareCalculator(2.3));
			mapbinder.addBinding("ship").toInstance(new UniformFareCalculator(3));

			addEventHandlerBinding().to(TransitFareHandler.class); // Add the transit fare handler
		}
	}
}
