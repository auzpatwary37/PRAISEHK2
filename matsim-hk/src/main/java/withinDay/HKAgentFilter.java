package withinDay;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.replanning.identifiers.filter.AgentTypeFilter;
import org.matsim.withinday.replanning.identifiers.filter.AgentTypeFilterFactory;
import org.matsim.withinday.replanning.identifiers.filter.LinkFilter;
import org.matsim.withinday.replanning.identifiers.filter.LinkFilterFactory;
import org.matsim.withinday.replanning.identifiers.filter.ProbabilityFilter;
import org.matsim.withinday.replanning.identifiers.filter.ProbabilityFilterFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.AgentFilter;

public class HKAgentFilter implements AgentFilter {
	
	private Set<Class<?>> includedAgentTypes;
	private Set<Id<Link>> links;
	private MobsimDataProvider mobsimDataProvider;
	private AgentTypeFilter atf;
	private LinkFilter lf;
	private final ProbabilityFilter ptf;
	
	private final double startTime;
	private final double endTime;
	
	// use the factory
	/*package*/ HKAgentFilter(Set<Class<?>> includedAgentTypes, MobsimDataProvider mobsimDataProvider, 
			 Set<Id<Link>> links, double probability, double startTime, double endTime) {
		this.includedAgentTypes = includedAgentTypes;
		this.mobsimDataProvider = mobsimDataProvider;
		this.links = links;
		this.ptf = new ProbabilityFilterFactory(probability).createAgentFilter();
		this.atf = null;
		
		this.startTime = startTime;
		this.endTime = endTime;
	}
	
	@Override
	public void applyAgentFilter(Set<Id<Person>> set, double time) {
		Iterator<Id<Person>> iter = set.iterator();
		
		while (iter.hasNext()) {
			Id<Person> id = iter.next();
			if (!this.applyAgentFilter(id, time)) iter.remove();
		}
	}

	@Override
	/**
	 * Find out the agents needed to be withinday replanned.
	 * Remove (filtered out) if false
	 */
	public boolean applyAgentFilter(Id<Person> id, double time) {
		if(time < startTime || time > endTime) {
			return false;
		}
		
		if(this.atf == null){ //Try to initialize while it is useful. As there was a null pointer exception for the constructor.
			this.atf = new AgentTypeFilterFactory(includedAgentTypes, mobsimDataProvider).createAgentFilter();
			this.lf = new LinkFilterFactory(links, mobsimDataProvider).createAgentFilter();
		}
		
		return atf.applyAgentFilter(id, time) && ptf.applyAgentFilter(id, time) && lf.applyAgentFilter(id, time); //Need both to be true to pass through
	}

}
