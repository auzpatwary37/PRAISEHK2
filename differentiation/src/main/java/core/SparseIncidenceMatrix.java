package core;
import java.util.*;
import org.apache.commons.math3.linear.*;

public class SparseIncidenceMatrix <S,T>{
	private final List<S> xKeys;
	private final List<T> yKeys;
	private final String id;
	private final RealMatrix matrix;
	
	public SparseIncidenceMatrix(String id, Map<S,Map<T,Double>> incidenceMap) {
		this.id = id;
		xKeys = new ArrayList<>();
		yKeys = new ArrayList<>();
		for(S s:incidenceMap.keySet()) {
			xKeys.add(s);
			for(T t:incidenceMap.get(s).keySet()) {
				if(!yKeys.contains(t))yKeys.add(t);
			}
		}
		
		matrix = new OpenMapRealMatrix(xKeys.size(),yKeys.size());
		for(S s:incidenceMap.keySet()) {
			for(T t:incidenceMap.get(s).keySet()) {
				matrix.setEntry(xKeys.indexOf(s), yKeys.indexOf(t), incidenceMap.get(s).get(t));
			}
		}
		
	}

	public RealMatrix getMatrix() {
		return matrix;
	}

	public List<S> getxKeys() {
		return xKeys;
	}

	public List<T> getyKeys() {
		return yKeys;
	}

	public String getId() {
		return id;
	}
	
}
