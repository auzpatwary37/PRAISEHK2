package optimizer;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealVector;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.utils.collections.Tuple;

import core.MapToArray;




public class Adam implements Optimizer{
	private double alphaMultiplier = 0.5;
	private double alpha = .005; //Note: These values are default one, and are changed by using another constructor
	private double beta1 = .9;
	private double beta2 = 0.999;
	private double eta = 10e-4;
	private double c = 1000;
	private Map<String,VariableDetails> variables = new HashMap<>();
	//private Map<String,Double> m = new HashMap<>();
	private RealVector m;
	//private Map<String,Double> v = new HashMap<>(); 
	private RealVector v;
	private int counter;
	private final String id;
	private static final Logger logger = Logger.getLogger(Adam.class);
	private double dropout = 0;
	
	public Adam(Plan maasAgentPlan) {
		this.id = maasAgentPlan.getPerson().getId().toString();
		for(Entry<String, Object> a:maasAgentPlan.getAttributes().getAsMap().entrySet()){
			if(a.getValue() instanceof VariableDetails) this.variables.put(a.getKey(), (VariableDetails)a.getValue());
		}
		this.initialize();
	}

	public Adam(String id, Map<String,VariableDetails> variables) {
		this.variables = variables;
		this.id = id;
		this.initialize();
	}
	
	public Adam(String id, Map<String,VariableDetails> variables,double alpha,double beta1,double beta2,double eta) {
		this.variables = variables;
		this.alpha = alpha;
		this.beta1 = beta1;
		this.beta2 = beta2;
		this.eta = eta;
		this.id = id;
		this.initialize();
	}
	
	private void initialize() {
//		this.variables.keySet().forEach(var->{
//			m.put(var, 0.);
//			v.put(var,0.);
//			counter = 0;
//		});
		m = MatrixUtils.createRealVector(new double[this.variables.size()]);
		v = MatrixUtils.createRealVector(new double[this.variables.size()]);
		counter = 0;
	}

	/**
	 * This will minimize
	 */
	public Map<String,VariableDetails> takeStep(Map<String,Double> gradient){
		
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
		m = m.mapMultiply(this.beta1).add(g.mapMultiply(1-beta1));
		v = v.mapMultiply(this.beta2).add(g.ebeMultiply(g).mapMultiply(1-this.beta2));
		RealVector m_h = m.mapDivide(1-this.beta1);
		RealVector v_h = v.mapDivide(1-this.beta2);
		//RealVector p_new = p.subtract(m_h.mapMultiply(this.alpha/(1+(counter-1)*0.4)).ebeDivide(v_h.map(k->Math.sqrt(k)).mapAdd(this.eta)));
		RealVector p_new = p.subtract(m_h.mapMultiply(this.alpha).ebeDivide(v_h.map(k->Math.sqrt(k)).mapAdd(this.eta)));
		for(Entry<String,Double> var:m2a.getMap(p_new.toArray()).entrySet()) {
			Tuple<Double,Double> limit = this.variables.get(var.getKey()).getLimit();
			if(var.getValue()>limit.getSecond())var.setValue(limit.getSecond());
			else if(var.getValue()<limit.getFirst())var.setValue(limit.getFirst());
			if(Math.random()>this.dropout) {
				this.variables.get(var.getKey()).setCurrentValue(var.getValue());
				if(Double.isNaN(var.getValue())) {
					System.out.println("Debug!!!!");
				}
			}
		}
		
		//for(Entry<String, Double> g:gradient.entrySet()) {
//		gradient.entrySet().parallelStream().forEach(g->{
//			m.compute(g.getKey(), (k,v)->this.beta1*v+(1-beta1)*g.getValue());
//			v.compute(g.getKey(), (k,v)->this.beta2*v+(1-this.beta2)*g.getValue()*g.getValue());
//			double m_h = m.get(g.getKey())/(1-this.beta1);
//			double v_h = v.get(g.getKey())/(1-this.beta2);
//			double var = this.variables.get(g.getKey()).getCurrentValue() + this.alpha*m_h/((Math.sqrt(v_h))+this.eta);
//			Tuple<Double,Double> limit = this.variables.get(g.getKey()).getLimit();
//			if(var<limit.getFirst()) var = limit.getFirst();
//			else if (var>limit.getSecond()) var = limit.getSecond();
//			this.variables.get(g.getKey()).setCurrentValue(var);
//		});
		//}
		
		return this.variables;
	}
	
	public Map<String,VariableDetails> takeRepeatedStep(Map<String,Double> gradient, Map<String,Double>variables){
		this.alpha = this.alpha*this.alphaMultiplier;
		if(gradient==null) {
			logger.debug("Gradient is null");
		}
		MapToArray<String> m2a = new MapToArray<String>("",this.variables.keySet());
		RealVector p = m2a.getRealVector(variables.keySet().stream().collect(Collectors.toMap(k->k, k->variables.get(k))));
		RealVector g = m2a.getRealVector(gradient);
		if(g.getNorm()>c*g.getDimension()) {//Clipping
			if(!Double.isInfinite(g.getNorm())) {
				g = g.mapDivide(g.getNorm()).mapMultiply(c*g.getDimension());
			}else {
				g = g.mapDivide(g.getL1Norm()).mapMultiply(c*g.getDimension());
			}
		}
		RealVector m_h = m.mapDivide(1-this.beta1);
		RealVector v_h = v.mapDivide(1-this.beta2);
		RealVector p_new = p.subtract(m_h.mapMultiply(this.alpha).ebeDivide(v_h.map(k->Math.sqrt(k)).mapAdd(this.eta)));
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
	
	public static void main(String[] args) {
		Map<String,VariableDetails> vars = new HashMap<>();
		vars.put("1",new VariableDetails("1",new Tuple<Double,Double>(1.0,10.0),5.0));
		Adam adam = new Adam("test",vars);
		Map<String,Double> grad = new HashMap<>();
		grad.put("1", 0.);
		adam.takeStep(grad);
	}

	public double getAlpha() {
		return alpha;
	}
	
	
}
