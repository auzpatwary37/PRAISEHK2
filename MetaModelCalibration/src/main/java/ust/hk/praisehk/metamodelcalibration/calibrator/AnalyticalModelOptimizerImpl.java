package ust.hk.praisehk.metamodelcalibration.calibrator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;

import cz.cvut.fit.jcool.core.Hessian;
import cz.cvut.fit.jcool.core.Point;
import cz.cvut.fit.jcool.utils.CentralDifferenceHessian;
import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

/**
 * generic implementation of AnalyticalModelOptimizer
 * @author h
 *
 */
public class AnalyticalModelOptimizerImpl implements AnalyticalModelOptimizer{
	
	private int maxIter=1500;
	private final int noOfVariables;
	private double startingRadius=10;
	private double endingRadius=0.00001;
	private OptimizationFunction optimFunction;	
	private CentralDifferenceHessian HassinCalculator;
	private boolean useUnitDiag=true;
	private final LinkedHashMap<String,Double> currentParams;

	
	public AnalyticalModelOptimizerImpl(OptimizationFunction optimFunc) {
			
			this.currentParams=optimFunc.getCurrentParams();
			this.noOfVariables=currentParams.size();
			double[] hessian=new double[this.noOfVariables];
			double[] currentPoint=new double[this.noOfVariables];
			int j=0;
			for(double d:currentParams.values()) {
				currentPoint[j]=d;
				j++;
			}
			if(useUnitDiag==false) {
			File file=new File("Calibration/hessian.csv");
			if(file.exists()) {
				try {
					BufferedReader bf=new BufferedReader(new FileReader(file));
					String Line=bf.readLine();
					String[] part=Line.split(",");
					for(int i=0;i<part.length;i++) {
						hessian[i]=Double.parseDouble(part[i].trim());
					}

				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}else {
				this.HassinCalculator=new CentralDifferenceHessian();
				Point CurrentPointforHessian=Point.at(currentPoint);
				Hessian Hessian=this.HassinCalculator.hessianAt(new HessianObjective(optimFunc.getSUE(),optimFunc.getRealData(),this),CurrentPointforHessian);
				try {
					FileWriter fileWriter=new FileWriter(file);
					double[] diag=new double[this.noOfVariables];
					
					for(int i=0;i<Hessian.toArray().length;i++) {
						diag[i]=Math.sqrt(Math.abs(Hessian.toArray()[i][i]));
						
					}
					for(int i=0;i<diag.length;i++) {
						hessian[i]=diag[i];
						fileWriter.append(diag[i]+",");
					}
					fileWriter.flush();
					fileWriter.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			}
			if(this.useUnitDiag) {
				for(int i=0; i<hessian.length;i++) {
					hessian[i]=1;
				}
			}
			this.optimFunction=optimFunc;
			this.optimFunction.setHessian(hessian);
	}
	
	public AnalyticalModelOptimizerImpl(AnalyticalModel SUE,Measurements realData, Map<Id<Measurement>,Map<String,MetaModel>>metaModels,LinkedHashMap<String,Double>currentParam,double trustRegionRadius,LinkedHashMap<String,Tuple<Double,Double>>paramLimit,String objectiveType) {
		this(new SimpleOptimizationFunction(SUE, realData, metaModels, currentParam, trustRegionRadius,paramLimit,objectiveType));
	}
	
	
	
	

	@Override
	public OptimizationFunction getOptimizationFunction() {
		return optimFunction;
	}

	@Override
	public LinkedHashMap<String, Double> performOptimization() {
		//initialization
		double[] x=new double[noOfVariables];

		for (int j=0;j<x.length;j++) {
			x[j]=1;
			j++;
		}

		//Call the optimization subroutine
		CobylaExitStatus result = Cobyla.findMinimum(this.optimFunction,this.noOfVariables, this.noOfVariables*2+1,
				x,this.startingRadius,this.endingRadius ,3, this.maxIter);
		for(int i=0;i<x.length;i++)System.out.println(x[i]);
		LinkedHashMap<String,Double> resultParam=this.optimFunction.ScaleUp(x);
		System.out.println();
		return resultParam;
	}	
}
