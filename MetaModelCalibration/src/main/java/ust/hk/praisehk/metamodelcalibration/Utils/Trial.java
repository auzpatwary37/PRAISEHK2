package ust.hk.praisehk.metamodelcalibration.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class Trial {
	private static int p=0;
public static void main(String[] args) {

	

}

public static wrappedMap<String,Double> generateMap(int n) {
	Map<String,Double> outerMap= new HashMap<>();
	
	for(int i=1;i<10;i++) {
		outerMap.put("t"+i, i*20.+p*i);
		p++;
	}
	return new wrappedMap<String,Double> (outerMap);
}

}

class wrappedMap<T,Y>{
	private Map<T,Y> map;
	public wrappedMap(Map<T,Y> map) {
		this.map=map;
	}
	public Map<T,Y> getMap() {
		return map;
	}
}
