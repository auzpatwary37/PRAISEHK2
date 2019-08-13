package ust.hk.praisehk.metamodelcalibration.calibrator;

import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

import cz.cvut.fit.jcool.core.Gradient;
import cz.cvut.fit.jcool.core.Hessian;
import cz.cvut.fit.jcool.core.ObjectiveFunction;
import cz.cvut.fit.jcool.core.Point;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.SUEModelOutput;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;


public class HessianObjective implements ObjectiveFunction {
		private Measurements realData;
		private AnalyticalModel sueAssignment;
		private AnalyticalModelOptimizer optimizer;
		private int dimension=0;
		
		
		public HessianObjective(AnalyticalModel sueAssignment, Measurements realdata,AnalyticalModelOptimizer anaModelOptimizer) {
			this.sueAssignment=sueAssignment;
			this.realData=realdata;
			this.optimizer=anaModelOptimizer;
			dimension=this.optimizer.getOptimizationFunction().getCurrentParams().size();
		}
		@Override
		public double valueAt(Point point) {
			double[] x= point.toArray();
			LinkedHashMap<String,Double> params=optimizer.getOptimizationFunction().ScaleUp(x);
			Measurements anaData=this.realData.clone();
			SUEModelOutput linkVolume=sueAssignment.perFormSUE(params);
			anaData.updateMeasurements(linkVolume, this.sueAssignment,null);
			double value=this.optimizer.getOptimizationFunction().calcMetaModelObjective(anaData, params);
			return value;
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


