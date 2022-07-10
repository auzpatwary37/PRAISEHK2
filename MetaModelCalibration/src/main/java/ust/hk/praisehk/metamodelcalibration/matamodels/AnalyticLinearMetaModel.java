package ust.hk.praisehk.metamodelcalibration.matamodels;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.stat.regression.GLSMultipleLinearRegression;
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
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.BaseVector;
import smile.data.vector.DoubleVector;
import smile.regression.LASSO;
import smile.regression.LinearModel;
import smile.regression.RidgeRegression;
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
	private final double ridgeCoefficient=1e-3;
	private final double positivityCoefficient =10;
	private Map<Integer,Double> analyticalData=new HashMap<>();
	private static double errorT = 0;
	public double zerothPredictoin;
	public double anaValue;
	public double simValue;
	public double[] scaleMean;
	public double[] scaleSigma;
	public double scaleMeanY = 0;
	public double scaleSigmaY =1;
	public AnalyticLinearMetaModel(Id<Measurement> measurementId,Map<Integer,Measurements> SimData, Map<Integer,Measurements> AnalyticalData,
			Map<Integer, LinkedHashMap<String, Double>> paramsToCalibrate,String timeBeanId, int currentParamNo) {
		
		super(measurementId,SimData,paramsToCalibrate,timeBeanId,currentParamNo);
		for(Entry<Integer,Measurements> e:AnalyticalData.entrySet()) {
			this.analyticalData.put(e.getKey(),e.getValue().getMeasurements().get(this.measurementId).getVolumes().get(timeBeanId));
			
		}
		this.noOfMetaModelParams=this.noOfParams+2;
		this.scaleMean = new double[this.noOfMetaModelParams];
		this.scaleSigma = new double[this.noOfMetaModelParams];
		for(int i = 0;i<this.scaleSigma.length;i++)this.scaleSigma[i]=1;
//		try {
//			this.calibrateMetaModelWithAdam(currentParamNo);
//		}catch(Exception e) {
//			
//		}
		this.calibrateMetaModel(currentParamNo);
		//this.calibrateMetaModelAnalytically(currentParamNo);
		//this.calibrateMetaModelWithAdam(currentParamNo);
		//this.calibrateMetaModelWithApache(currentParamNo);
		//this.calibrateMetaModelWithSmile(currentParamNo);
		
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
	
	public void calibrateMetaModelWithApache(int currentParamNo) {
		
		double[][] weights = new double[this.params.size()][this.params.size()];
		double[] y = new double[weights.length];
		RealMatrix x = MatrixUtils.createRealMatrix(this.params.size(), this.noOfMetaModelParams);
		
		for(int i:params.keySet()) {
			weights[i][i] = 1/calcEuclDistanceBasedWeight(params, i,currentParamNo);
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
		
		GLSMultipleLinearRegression regr = new GLSMultipleLinearRegression();
        regr.setNoIntercept(false);
        regr.newSampleData(y, x.getData(), weights);
        double[] params = regr.estimateRegressionParameters();
        this.MetaModelParams = params;
	    
	}
	
public void calibrateMetaModelWithSmile(int currentParamNo) {
		
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
		DataFrame df = DataFrame.of(x.getData()).merge(DoubleVector.of("deflator", y));
		//LinearModel model = RidgeRegression.fit(Formula.lhs("deflator"), df, weights, new double[] {this.ridgeCoefficient}, new double[]{0.0});
		LinearModel model = LASSO.fit(Formula.lhs("deflator"), df);
        this.MetaModelParams[0] = model.intercept();
        int i = 0;
        for(double d:model.coefficients()) {
        	this.MetaModelParams[i+1] = d;
        	i++;
        }
	    
	}
	public Tuple<Double,Double> calcMeanAndSD(double[] a){
		double sum = 0;
		double sumSq = 0;
		for(double d:a)sum+=d;
		
		double mean = sum/a.length;
		for(double d:a)sumSq+=(d-mean)*(d-mean);
		double sd = Math.sqrt(sumSq/a.length);
		return new Tuple<>(mean,sd);
	}
	
	public double[] applyScale(double[] a,double m,double sd) {
		for(int i=0;i<a.length;i++)a[i] = (a[i]-m)/sd;
		return a;
	}
	
	public void calibrateMetaModelWithAdam(int currentParamNo) {
		double[] weights = new double[this.params.size()];
		double[] y = new double[weights.length];
		RealMatrix x = MatrixUtils.createRealMatrix(this.params.size(), this.noOfMetaModelParams);
		
		double error = 0;
		
		
		
		for(int i:params.keySet()) {
			weights[i] = calcEuclDistanceBasedWeight(params, i,currentParamNo);
			y[i] = simData.get(i);
			double[] xrow = new double[this.noOfMetaModelParams];
			xrow[0] = 1;
			xrow[1] = analyticalData.get(i);
			
			error += Math.pow(xrow[1]-y[i],2);
			int k = 0;
			for(double d:params.get(i).values()) {
				xrow[k+2] = d;
				k++;
			}
			x.setRow(i, xrow);
		}
	
		DataFrame df = DataFrame.of(x.getData());
		double[] center = df.toMatrix().colMeans();
	    double[] scale = df.toMatrix().colSds();
	    
	    
	    
		for(int i = 0;i<scale.length;i++)if(scale[i]==0)scale[i]= 1;
		this.scaleMean = center;
		this.scaleSigma = scale;
		Tuple<Double,Double> t = this.calcMeanAndSD(y);
 		this.scaleMeanY = t.getFirst();
 		this.scaleSigmaY = t.getSecond();
 		if(this.scaleSigmaY==0)this.scaleSigmaY = 1;
		
		double[][] aa = df.toMatrix().scale(center, scale).toArray();
		x = MatrixUtils.createRealMatrix(aa);
		RealVector Y = MatrixUtils.createRealVector(this.applyScale(y, this.scaleMeanY, this.scaleSigmaY));
		RealVector b = MatrixUtils.createRealVector(new double[this.noOfMetaModelParams]);
		b.setEntry(1, 1);
		
		RealVector bIn = MatrixUtils.createRealVector(b.toArray());
		//		/
//		if(error>400) {
//			System.out.println("Debug!!!");
//		}
		double[] delta = null;
		MatrixBasedUnconstrainedAdam gd  = new MatrixBasedUnconstrainedAdam(this.noOfMetaModelParams,.02);
		gd.setLimitFor2ndElement(new Tuple<Double,Double>(0.,3.));
		double dCheck = Double.POSITIVE_INFINITY;
		double[] bbest = null;
		boolean updated = false;
		for(int i=0;i<3000;i++) {
			
			delta= x.operate(b).subtract(Y).toArray();
			double[] aug = new double[weights.length];
			for(int j = 0;j<weights.length;j++)aug[j]=weights[j]*delta[j];
			RealVector g = x.transpose().operate(MatrixUtils.createRealVector(aug)).add(b.subtract(bIn).mapMultiply(ridgeCoefficient));//Activate this for ridge
			//RealVector g = x.transpose().operate(MatrixUtils.createRealVector(aug)).add(b.subtract(bIn).map(v->Math.signum(v)).mapMultiply(ridgeCoefficient));//Activate this for lasso
			//if(b.getEntry(1)<0)g.setEntry(1, g.getEntry(1)+b.getEntry(1)*this.positivityCoefficient);
			
			double newdCheck = MatrixUtils.createRealVector(delta).getL1Norm();
			System.out.println(newdCheck);
			if(newdCheck<=dCheck ) {
				dCheck = newdCheck;
				bbest = b.toArray();
			}else {
				//System.out.println("Debug!!!SF!!!");
				dCheck = newdCheck;
			}
			if(newdCheck<0.0001)break;
			b = gd.update(b, g);
			updated = true;
		}
		this.MetaModelParams = bbest;
		if(updated) {
			System.out.println();
		}
		double e = simData.get(0)-this.calcMetaModel(analyticalData.get(0), params.get(0));
		updateErrorT(Math.pow(e, 2));
		if(Math.abs(e)>1.01) {
			System.out.println("Error!!!"+e);
		}
		this.zerothPredictoin = this.calcMetaModel(analyticalData.get(0), params.get(0));
		this.anaValue = analyticalData.get(0);
		this.simValue = simData.get(0);
	}

	@Override
	public double calcMetaModel(double analyticalModelPart, LinkedHashMap<String, Double> param) {
		double modelOutput=this.MetaModelParams[0]+MetaModelParams[1]*(analyticalModelPart-this.scaleMean[1])/this.scaleSigma[1];
		int i=1;
		for(double d:param.values()) {
			modelOutput+=this.MetaModelParams[i+1]*(d-this.scaleMean[i+1])/this.scaleSigma[i+1];
			i++;
		}
		double out =modelOutput*this.scaleSigmaY+this.scaleMeanY;
		if(out>6000) {
			System.out.println("Debug!!!");
		}
		return out;
		
	}


	@Override
	public String getMetaModelName() {
		return this.AnalyticalLinearMetaModelName;
	}


	@Override
	public double[] getGradientVector() {
		double[] grad = new double[this.MetaModelParams.length-2];
		for(int i = 2;i<this.MetaModelParams.length;i++)grad[i-2] = this.MetaModelParams[i]/this.scaleSigma[i];
		return grad;
	}


	@Override
	public Double getanaGradMultiplier() {
		return this.MetaModelParams[1]/this.scaleSigma[1];
	}
//	public static void main(String[] args) {
//		DataFrame df = DataFrame.of(new double[3][2]);
//		System.out.println("Done");
//	}
	
}
