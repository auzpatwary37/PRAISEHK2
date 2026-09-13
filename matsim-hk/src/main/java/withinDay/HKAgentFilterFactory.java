package withinDay;

import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.replanning.identifiers.interfaces.AgentFilter;
import org.matsim.withinday.replanning.identifiers.interfaces.AgentFilterFactory;

public class HKAgentFilterFactory implements AgentFilterFactory {
	
	private final MobsimDataProvider mobsimDataProvider;
	private final Set<Class<?>> includedAgentTypes;
	private final Set<Id<Link>> links;
	private final double replanningProbability;
	
	private final double startTime;
	private final double endTime;

	public HKAgentFilterFactory(Set<Class<?>> includedAgentTypes, MobsimDataProvider mobsimDataProvider, 
			 Set<Id<Link>> links, double replanningProbability, double startTime, double endTime) {
		this.replanningProbability = replanningProbability;
		this.mobsimDataProvider = mobsimDataProvider;
		this.includedAgentTypes = includedAgentTypes;
		this.links = links;
		
		this.startTime = startTime;
		this.endTime = endTime;
	}
	
	@Override
	public AgentFilter createAgentFilter() {
		return new HKAgentFilter(includedAgentTypes, mobsimDataProvider, links, replanningProbability, startTime, endTime);
	}

}
