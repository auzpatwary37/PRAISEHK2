package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.Map;

import org.matsim.core.utils.collections.Tuple;

public class TimeUtils {

	public static String getTimeId(Map<String,Tuple<Double,Double>> timeBean, double time) {
		if(time>24*3600) {
			time = time-24*3600;
		}else if(time==0) {
			time=1;
		}
		String timeId=null;
		for(String t:timeBean.keySet()) {
			if(time>timeBean.get(t).getFirst() && time<=timeBean.get(t).getSecond()) {
				timeId=t;
			}
			
		}
		return timeId;
	}
}
