package withinDay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class EquivalentStopForFare {
	Map<Id<TransitRoute>, Map<Id<TransitStopFacility>, List<Id<TransitStopFacility>>>> stopEquivalent = new HashMap<>();
	
	public void addEquivalentStops(List<Id<TransitRoute>> routeIdLists, 
			List<String> newStops, String facilityToMap) {
		Map<Id<TransitStopFacility>, List<Id<TransitStopFacility>>> tsfMap = new HashMap<>();
		Id<TransitStopFacility> toMapStopFacilityId = Id.create(facilityToMap, TransitStopFacility.class);
		List<Id<TransitStopFacility>> addedStops = newStops.stream().map(e -> Id.create(e, TransitStopFacility.class)).collect(Collectors.toList());
		tsfMap.put(toMapStopFacilityId, addedStops);
		
		for(Id<TransitRoute> routeId: routeIdLists) {
			if(stopEquivalent.containsKey(routeId)) {
				Map<Id<TransitStopFacility>, List<Id<TransitStopFacility>>> tsfMapTemp = stopEquivalent.get(routeId);
				tsfMapTemp.put(toMapStopFacilityId, addedStops);
			}else {
				stopEquivalent.put(routeId, tsfMap);
			}
		}
	}
	
	public Map<Id<TransitStopFacility>, List<Id<TransitStopFacility>>> getFacilityMapping(Id<TransitRoute> routeId){
		return stopEquivalent.get(routeId);
	}
}
