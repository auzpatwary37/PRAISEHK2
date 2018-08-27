package calibrator;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

import cz.cvut.fit.jcool.core.Gradient;
import cz.cvut.fit.jcool.core.Hessian;
import cz.cvut.fit.jcool.core.ObjectiveFunction;
import cz.cvut.fit.jcool.core.Point;
import ust.hk.praisehk.AnalyticalModels.AnalyticalModel;
import ust.hk.praisehk.Counts.CountData;
import ust.hk.praisehk.MetaModels.MetaModel;

public class HessianObjective implements ObjectiveFunction {
		private CountData countData;
		private AnalyticalModel sueAssignment;
		private AnalyticalModelOptimizer optimizer;
		private int dimension=0;
		
		public HessianObjective(AnalyticalModel sueAssignment, CountData countdata,AnalyticalModelOptimizer anaModelOptimizer) {
			this.sueAssignment=sueAssignment;
			this.countData=countdata;
			this.optimizer=anaModelOptimizer;
			dimension=this.optimizer.getOptimizationFunction().getCurrentParams().size();
		}
		@Override
		public double valueAt(Point point) {
			double[] x= point.toArray();
			LinkedHashMap<String,Double> params=optimizer.getOptimizationFunction().ScaleUp(x);
			HashMap<String,HashMap<Id<Link>,Double>>linkVolume=sueAssignment.perFormSUE(params);
			double value=this.optimizer.getOptimizationFunction().calcMetaModelObjective(linkVolume, params);
			return value;
		}

		public double calcMetaModelObjective(Map<String, HashMap<Id<Link>, Double>> linkVolume,
				LinkedHashMap<String, Double> params) {
			double objective=0;
			for(String timeBeanId:this.sueAssignment.getTimeBeans().keySet()) {
				for(Id<Link> linkId:this.countData.GetRealCountData().get(timeBeanId).keySet()) {
					double RealLinkCount=this.countData.GetRealCountData().get(timeBeanId).get(linkId);
					double AnaLyticalModelLinkCount=linkVolume.get(timeBeanId).get(linkId);
					MetaModel metaModel=this.countData.getMetaModels().get(timeBeanId).get(linkId);
					objective+=Math.pow(RealLinkCount-metaModel.calcMetaModel(AnaLyticalModelLinkCount, params),2);
				}
			}
			return objective;
		}
		@Override
		public int getDimension() {
			return dimension;
		}

		@Override
		public Gradient gradientAt(Point point) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Hessian hessianAt(Point point) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public double[] getMinimum() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public double[] getMaximum() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void resetGenerationCount() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void nextGeneration() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setGeneration(int currentGeneration) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public boolean hasAnalyticalGradient() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean hasAnalyticalHessian() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isDynamic() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean inBounds(Point position) {
			// TODO Auto-generated method stub
			return false;
		}

	}


