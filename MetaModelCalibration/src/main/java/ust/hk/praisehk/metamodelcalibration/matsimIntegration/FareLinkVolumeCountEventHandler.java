package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.handler.PersonMoneyEventHandler;
import org.matsim.core.utils.collections.Tuple;

import com.google.inject.Inject;

import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class FareLinkVolumeCountEventHandler implements PersonMoneyEventHandler{
	
	private Measurements measurements;
	private Map<String,Set<Id<Measurement>>> fareLinkIncidence = new HashMap<>(); 
	
	
	
	public FareLinkVolumeCountEventHandler(Measurements measurements) {
		this.measurements = measurements;
		measurements.getMeasurementsByType().get(MeasurementType.fareLinkVolume).forEach(m->{
			FareLink fl = (FareLink)m.getAttribute(Measurement.FareLinkAttributeName);
			this.fareLinkIncidence.compute(fl.toString(), (k,v)->(v==null)?new HashSet<>():v);
			this.fareLinkIncidence.get(fl.toString()).add(m.getId());
		});
		
		measurements.getMeasurementsByType().get(MeasurementType.fareLinkVolumeCluster).forEach(m->{
			List<FareLink> fareLinks = (List<FareLink>) m.getAttribute(Measurement.FareLinkClusterAttributeName);
			fareLinks.forEach(fl->{
				this.fareLinkIncidence.compute(fl.toString(), (k,v)->(v==null)?new HashSet<>():v);
				this.fareLinkIncidence.get(fl.toString()).add(m.getId());
			});
		});
	}

	@Override
	public void handleEvent(PersonMoneyEvent event) {
		
		if(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_PURPOSE).equals(FareLink.FareTransactionName)) {//So, this is a fare payment event
			FareLink fl = new FareLink(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_TRANSACTION_PARTNER));
			String timeId = getTimeId(event.getTime(), this.measurements.getTimeBean());
			if(timeId!=null) {
				if(this.fareLinkIncidence == null) {
					throw new NullPointerException();
				}
				if(this.fareLinkIncidence.get(fl.toString()) != null) { //Only for valid fare links
					for(Id<Measurement> mId:this.fareLinkIncidence.get(fl.toString())) {
						Map<String,Double> vols = this.measurements.getMeasurements().get(mId).getVolumes();
						if(vols.containsKey(timeId)) {
							vols.put(timeId,vols.get(timeId)+1);
						}
					}
				}
			}
		}
	}
	
	@Override
	public void reset(int iteration) {
		measurements.resetMeasurementsByType(MeasurementType.fareLinkVolume);
		measurements.resetMeasurementsByType(MeasurementType.fareLinkVolumeCluster);
	}
	public static String getTimeId(double time, Map<String,Tuple<Double,Double>>timeBeans) {
		if(time>24*3600) {
			time=time-24*3600;
		}
		String timeId=null;
		for(Entry<String, Tuple<Double, Double>> timeBean:timeBeans.entrySet()) {
			if(time>timeBean.getValue().getFirst() && time<=timeBean.getValue().getSecond()) {
				timeId=timeBean.getKey();
			}
		}
		return timeId;
	}

	public Measurements getMeasurements() {
		return measurements;
	}
	
}
