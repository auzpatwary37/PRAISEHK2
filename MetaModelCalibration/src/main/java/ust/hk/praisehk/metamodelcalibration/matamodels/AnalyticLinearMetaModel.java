package ust.hk.praisehk.metamodelcalibration.matamodels;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.checkutil.CheckUtil;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.inverse.InvertMatrix;

import de.xypron.jcobyla.Calcfc;
import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

/**
 * 
 * @author Ashraf
 *
 */
public class AnalyticLinearMetaModel extends MetaModelImpl {
	/**
	 * a ridge penalty is added
	 * assuming constant of 1
	 * 
	 * The format is Y=Bo+B1*A+B(2-N+1)X
	 * Bo --> Const.
	 * B --> Vector of Meta-Model Parameters
	 * X --> Vector of Parameters to be Calibrated
	 * A --> Analytical Model Output 
	 */
	/**
	 * constructor
	 * 
	 */
	private final boolean addRidgePenalty=true;
	private final double ridgeCoefficient=10;
	private final double positivityCoefficient =10;
	private Map<Integer,Double> analyticalData=new HashMap<>();
	private double[] xL;
	private double[] scale;
	private static double errorT = 0;
	public double zerothPredictoin;
	public double anaValue;
	public double simValue;
	
	public AnalyticLinearMetaModel(Id<Measurement> measurementId,Map<Integer,Measurements> SimData, Map<Integer,Measurements> AnalyticalData,
			Map<Integer, LinkedHashMap<String, Double>> paramsToCalibrate,String timeBeanId, int currentParamNo) {
		
		super(measurementId,SimData,paramsToCalibrate,timeBeanId,currentParamNo);
		for(Entry<Integer,Measurements> e:AnalyticalData.entrySet()) {
			this.analyticalData.put(e.getKey(),e.getValue().getMeasurements().get(this.measurementId).getVolumes().get(timeBeanId));
			
		}
		this.noOfMetaModelParams=this.noOfParams+2;
		//this.calibrateMetaModel(currentParamNo);
		this.xL = new double[this.noOfMetaModelParams];
		this.scale = new double[this.noOfMetaModelParams];
		for(int i =0; i<this.scale.length;i++)this.scale[i] = 1;
		this.calibrateMetaModelWithAdam(currentParamNo);
		
		
		
		
		this.params.clear();
		this.simData.clear();
		this.analyticalData.clear();
		
	}
	public static synchronized void updateErrorT(double e) {
		errorT+=e;
	}
	
	public void calibrateMetaModel(final int currentParamNo) {
		Calcfc optimization=new Calcfc() {

			@Override
			public double compute(int m, int n, double[] x, double[] constrains) {
				double objective=0;
				MetaModelParams=x;
				for(int i:params.keySet()) {
					objective+=Math.pow(calcMetaModel(analyticalData.get(i), params.get(i))-simData.get(i),2)*calcEuclDistanceBasedWeight(params, i,currentParamNo);
				}
				if(addRidgePenalty==true) {
					for(double d:x) {
						objective+=d*d*ridgeCoefficient;
					}
				}
				return objective;
			}
			
		};
		double[] x=new double[this.noOfMetaModelParams]; 
		for(int i=0;i<this.noOfMetaModelParams;i++) {
			x[i]=1;
		}

	    CobylaExitStatus result = Cobyla.findMinimum(optimization, this.noOfMetaModelParams, 0, x,0.5,Math.pow(10, -6) ,0, 1500);

	    this.MetaModelParams=x;
		
	}
	
	/**
	 * This function aims to analytically derive the solution of the weighted least square problem defined as the metamodel fitting in Patwary et al.(2021)
	 * The solution to the least square problem (y-xb)'w(w-xb)+l*b'b
	 * y is column matrix of outputs, here should be the simulation measurements
	 * x is the augmented matrix of the following form
	 * 
	 * x = [1 \lambda x] 
	 * 
	 * 1 is the column matrix of 1, \lambda is the analytical model output column matrix and x is the matrix of input decision variables for all measurements
	 * 
	 * w is the diagonal matrix where the diagonals contain the Eucleaddian weights. 
	 * 
	 * b is the column matrix of the parameters to be calibrated for this metamodel. 
	 * 
	 * The analytical solution is: b = (x'wx+l*I)^(-1)x'wy
	 * 
	 * @param currentParamNo
	 */
	public void calibrateMetaModelAnalytically(int currentParamNo) {
		double[] weights = new double[this.params.size()];
		double[] y = new double[weights.length];
		RealMatrix x = MatrixUtils.createRealMatrix(this.params.size(), this.noOfMetaModelParams);
		
		
		for(int i:params.keySet()) {
			weights[i] = calcEuclDistanceBasedWeight(params, i,currentParamNo);
			y[i] = simData.get(i);
			double[] xrow = new double[this.noOfMetaModelParams];
			xrow[0] = 1;
			xrow[1] = analyticalData.get(i);
			int k = 0;
			for(double d:params.get(i).values()) {
				xrow[k+2] = d;
				k++;
			}
			x.setRow(i, xrow);
		}
		
		//RealVector Y = MatrixUtils.createRealVector(y);
		//RealMatrix W = MatrixUtils.createRealDiagonalMatrix(weights);
		INDArray X = CheckUtil.convertFromApacheMatrix(x, DataType.DOUBLE);
		INDArray yy = Nd4j.create(y);
		INDArray w = Nd4j.diag(Nd4j.create(weights));
		INDArray b1 =X.transpose().mmul(w).mmul(X).add(Nd4j.eye(this.noOfMetaModelParams).mul(this.ridgeCoefficient));
		
		
		//INDArray b1 = x.transpose().multiply(W).multiply(x).add(MatrixUtils.createRealIdentityMatrix(this.noOfMetaModelParams).scalarMultiply(this.ridgeCoefficient));
		INDArray b1Inv = InvertMatrix.pinvert(b1,true);
		//RealMatrix b1Inv = MatrixUtils.inverse(b1);
		//RealVector b2 = x.transpose().multiply(W).operate(Y); 
		INDArray b2 = X.transpose().mmul(w).mmul(yy);
		INDArray b = b1Inv.mmul(b2);
		
		//RealVector b = b1Inv.operate(b2);
		this.MetaModelParams = b.toDoubleVector();
	}
	
	public void calibrateMetaModelWithAdam(int currentParamNo) {
		double[] weights = new double[this.params.size()];
		double[] y = new double[weights.length];
		RealMatrix x = MatrixUtils.createRealMatrix(this.params.size(), this.noOfMetaModelParams);
		
		double error = 0;
		double[] xMax = new double[this.noOfMetaModelParams];
		double [] xMin = new double[this.noOfMetaModelParams];
		xMax[0] = 1;
		xMin[0] = 0;
		for(int i=1;i<xMax.length;i++) {
			xMax[i] = Double.NEGATIVE_INFINITY;
			xMin[i] = Double.POSITIVE_INFINITY;
		}
		for(int i:params.keySet()) {
			weights[i] = calcEuclDistanceBasedWeight(params, i,currentParamNo);
			y[i] = simData.get(i);
			double[] xrow = new double[this.noOfMetaModelParams];
			xrow[0] = 1;
			xrow[1] = analyticalData.get(i);
			if(xMax[1]<xrow[1]) {
				xMax[1] = xrow[1];
			}
			else if(xMin[1]>xrow[1])xMin[1] = xrow[1];
			
			error += Math.pow(xrow[1]-y[i],2);
			int k = 0;
			for(double d:params.get(i).values()) {
				xrow[k+2] = d;
				if(xMax[k+2]<xrow[k+2])xMax[k+2] = xrow[k+2];
				else if(xMin[k+2]>xrow[k+2])xMin[k+2] = xrow[k+2];
				k++;
			}
			x.setRow(i, xrow);
		}
	
		RealVector xL = MatrixUtils.createRealVector(xMin);
		if(xL.isInfinite()) {
			for(int i = 0;i<xMin.length;i++)if(Double.isInfinite(xMin[i]))xMin[i]=0;
			xL = MatrixUtils.createRealVector(xMin);
		}
		RealVector scale = MatrixUtils.createRealVector(xMax).subtract(xL);
		for(int i = 0;i<scale.getDimension();i++)if(scale.getEntry(i)==0)scale.setEntry(i, 1);
		this.scale = scale.toArray();
		this.xL =xMin;
		
		for(int i = 0; i<x.getRowDimension();i++) {
			x.setRow(i, MatrixUtils.createRealVector(x.getRow(i)).subtract(xL).ebeDivide(scale).toArray());
		}
		
		RealVector Y = MatrixUtils.createRealVector(y);
		//RealVector b = MatrixUtils.createRealVector(new double[this.noOfMetaModelParams]).mapAdd(1.0);
		RealVector b = MatrixUtils.createRealVector(new double[this.noOfMetaModelParams]);
		b.setEntry(1, this.scale[1]);
		RealVector bIn = MatrixUtils.createRealVector(b.toArray());
		//		/
//		if(error>400) {
//			System.out.println("Debug!!!");
//		}
		double[] delta = null;
		MatrixBasedUnconstrainedGD gd  = new MatrixBasedUnconstrainedGD(this.noOfMetaModelParams);
		gd.setLimitFor2ndElement(new Tuple<Double,Double>(0.,this.scale[1]*2));
		double dCheck = Double.POSITIVE_INFINITY;
		for(int i=0;i<300;i++) {
			
			delta= x.operate(b).subtract(Y).toArray();
			double[] aug = new double[weights.length];
			for(int j = 0;j<weights.length;j++)aug[j]=weights[j]*delta[j];
			RealVector g = x.transpose().operate(MatrixUtils.createRealVector(aug)).add(b.subtract(bIn).mapMultiply(ridgeCoefficient));//Activate this for ridge
			//RealVector g = x.transpose().operate(MatrixUtils.createRealVector(aug)).add(b.subtract(bIn).map(v->Math.signum(v)).mapMultiply(ridgeCoefficient));//Activate this for lasso
			//if(b.getEntry(1)<0)g.setEntry(1, g.getEntry(1)+b.getEntry(1)*this.positivityCoefficient);
			b = gd.update(b, g);
			double newdCheck = MatrixUtils.createRealVector(delta).getL1Norm();
			if(newdCheck<=dCheck ) {
				dCheck = newdCheck;
			}else {
				//System.out.println("Debug!!!SF!!!");
				dCheck = newdCheck;
			}
			
		}
		this.MetaModelParams = b.toArray();
		double e = simData.get(0)-this.calcMetaModel(analyticalData.get(0), params.get(0));
		updateErrorT(Math.pow(e, 2));
		if(Math.abs(Math.abs(e)-Math.abs(delta[0]))>1||Math.abs(e)>1.01) {
			System.out.print("Error!!!!");
		}
		this.zerothPredictoin = this.calcMetaModel(analyticalData.get(0), params.get(0));
		this.anaValue = analyticalData.get(0);
		this.simValue = simData.get(0);
	}

	@Override
	public double calcMetaModel(double analyticalModelPart, LinkedHashMap<String, Double> param) {
		double modelOutput=this.MetaModelParams[0]+MetaModelParams[1]*(analyticalModelPart-this.xL[1])/this.scale[1];
		int i=1;
		for(double d:param.values()) {
			modelOutput+=this.MetaModelParams[i+1]*(d-this.xL[i+1])/this.scale[i+1];
			i++;
		}
		
		return modelOutput;
		
	}


	@Override
	public String getMetaModelName() {
		return this.AnalyticalLinearMetaModelName;
	}


	@Override
	public double[] getGradientVector() {
		double[] grad = new double[this.MetaModelParams.length-2];
		for(int i = 2;i<this.MetaModelParams.length;i++)grad[i-2] = this.MetaModelParams[i]/this.scale[i];
		return grad;
	}


	@Override
	public Double getanaGradMultiplier() {
		return this.MetaModelParams[1]/this.scale[1];
	}
	
	
}
