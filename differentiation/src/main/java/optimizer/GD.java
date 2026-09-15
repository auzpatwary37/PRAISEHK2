package optimizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.utils.collections.Tuple;

import core.MapToArray;

public class GD implements Optimizer{
	private double alphaMultipler = 0.5;
	private double alpha = .001;
	private int counter;
	private final String id;
	private Map<String,VariableDetails> variables;
	private static final Logger logger = Logger.getLogger(GD.class);
	private double c = 10;
	
	public GD(String id, Map<String,VariableDetails> variables) {
		this.variables = variables;
		this.id = id;
	}
	
	public GD(String id, Map<String,VariableDetails> variables,double alpha,double c) {
		this.variables = variables;
		this.alpha = alpha;
		this.id = id;
		this.c = c;
	}
	
	public GD(Plan maasAgentPlan) {
		this.id = maasAgentPlan.getPerson().getId().toString();
		maasAgentPlan.getAttributes().getAsMap().entrySet().forEach(a->{
			if(a.getValue() instanceof VariableDetails) this.variables.put(a.getKey(), (VariableDetails)a.getValue());
		});
	}
	
	@Override
	public Map<String, VariableDetails> takeStep(Map<String, Double> gradient) {
		counter = counter+1;
		if(gradient==null) {
			logger.debug("Gradient is null");
		}
		MapToArray<String> m2a = new MapToArray<String>("",this.variables.keySet());
		RealVector p = m2a.getRealVector(this.variables.keySet().stream().collect(Collectors.toMap(k->k, k->this.variables.get(k).getCurrentValue())));
		RealVector g = m2a.getRealVector(gradient);
		if(g.getNorm()>c*g.getDimension()) {//Clipping
			if(!Double.isInfinite(g.getNorm())) {
				g = g.mapDivide(g.getNorm()).mapMultiply(c*g.getDimension());
			}else {
				g = g.mapDivide(g.getL1Norm()).mapMultiply(c*g.getDimension());
			}
		}
		
		RealVector p_new = p.subtract(g.mapMultiply(this.alpha));
		for(Entry<String,Double> var:m2a.getMap(p_new.toArray()).entrySet()) {
			Tuple<Double,Double> limit = this.variables.get(var.getKey()).getLimit();
			if(var.getValue()>limit.getSecond())var.setValue(limit.getSecond());
			else if(var.getValue()<limit.getFirst())var.setValue(limit.getFirst());
			this.variables.get(var.getKey()).setCurrentValue(var.getValue());
		}
		
		return this.variables;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public Map<String, VariableDetails> getVarables() {
		return this.variables;
	}

	public Map<String, VariableDetails> takeRepeatedStep(Map<String, Double> gradient,
			LinkedHashMap<String, Double> variables) {
		this.alpha*= this.alphaMultipler;
		MapToArray<String> m2a = new MapToArray<String>("",this.variables.keySet());
		RealVector p = m2a.getRealVector(this.variables.keySet().stream().collect(Collectors.toMap(k->k, k->variables.get(k))));
		RealVector g = m2a.getRealVector(gradient);
		if(g.getNorm()>c*g.getDimension()) {//Clipping
			if(!Double.isInfinite(g.getNorm())) {
				g = g.mapDivide(g.getNorm()).mapMultiply(c*g.getDimension());
			}else {
				g = g.mapDivide(g.getL1Norm()).mapMultiply(c*g.getDimension());
			}
		}
		
		RealVector p_new = p.subtract(g.mapMultiply(this.alpha));
		for(Entry<String,Double> var:m2a.getMap(p_new.toArray()).entrySet()) {
			Tuple<Double,Double> limit = this.variables.get(var.getKey()).getLimit();
			if(var.getValue()>limit.getSecond())var.setValue(limit.getSecond());
			else if(var.getValue()<limit.getFirst())var.setValue(limit.getFirst());
			this.variables.get(var.getKey()).setCurrentValue(var.getValue());
		}
		
		return this.variables;
	}

	public double getAlpha() {
		// TODO Auto-generated method stub
		return this.alpha;
	}
	


}
