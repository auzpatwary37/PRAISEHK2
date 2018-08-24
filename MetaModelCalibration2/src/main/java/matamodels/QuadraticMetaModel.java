package matamodels;

import java.util.HashMap;
import java.util.LinkedHashMap;

import de.xypron.jcobyla.Calcfc;
import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;

/**
 * 
 * @author Ashraf
 *
 */

public class QuadraticMetaModel extends MetaModelImpl{
	/**
	 * The format is Y=Bo+BX+X'HX
	 * Bo --> Const.
	 * B --> Vector of Meta-Model Parameters
	 * X --> Vector of Parameters to be Calibrated
	 * H --> a symmetric matrix of parameters 
	 */
	
	
	public QuadraticMetaModel(HashMap<Integer,HashMap<String,Double>> SimData,
			HashMap<Integer, LinkedHashMap<String, Double>> paramsToCalibrate,String timeBeanId, int counter) {
		super(SimData, paramsToCalibrate, timeBeanId, counter);
		
		this.noOfMetaModelParams=1+this.noOfParams+this.noOfParams*(this.noOfParams+1)/2;
		this.calibrateMetaModel(counter);
		this.params.clear();
		this.simData.clear();
	}
	
	private static Double[][] matProduct(Double[][] A, Double[][] B) {
		int arow=A.length;
		int brow=B.length;
		int acol=A[0].length;
		int bcol=B[0].length;
		
		 if (acol != brow) {
	            throw new IllegalArgumentException("A:Rows: " + acol + " did not match B:Columns " + brow + ".");
	        }

		 Double[][] C = new Double[arow][bcol];
		 for (int i = 0; i < arow; i++) {
			 for (int j = 0; j < bcol; j++) {
				 C[i][j] = 0.00000;
			 }
		 }

		 for (int i = 0; i < arow; i++) { // aRow
			 for (int j = 0; j < bcol; j++) { // bColumn
				 for (int k = 0; k < acol; k++) { // aColumn
					 C[i][j] += A[i][k] * B[k][j];
				 }
			 }
		 }

		 return C;
	}

	@Override
	public double calcMetaModel(double analyticalModelPart, LinkedHashMap<String, Double> param) {
		double modelResult=this.MetaModelParams[0];
		Double[][] x=new Double[param.size()][1];
		Double[][] xprime=new Double[1][param.size()];
		int i=1;
		for(double d: param.values()) {
			modelResult+=d*this.MetaModelParams[i];
			
			xprime[0][i-1]=d;
			x[i-1][0]=d;
			i++;
		}
		
		double hessianPart=matProduct(matProduct(xprime,hessianCreator(this.MetaModelParams)),x)[0][0];
		
		modelResult+=hessianPart;
		return modelResult;
	}

	@Override
	protected void calibrateMetaModel(final int counter) {
		Calcfc optimization=new Calcfc() {

			@Override
			public double compute(int m, int n, double[] x, double[] constrains) {
				double objective=0;
				MetaModelParams=x;
				for(int i:params.keySet()) {
					objective+=Math.pow(calcMetaModel(0, params.get(i))-simData.get(i),2)*calcEuclDistanceBasedWeight(params, i,counter);
				}
				for(double d:x) {
					objective+=d*d;
				}
				return objective;
			}
			
		};
		double[] x=new double[this.noOfMetaModelParams]; 
		for(int i=0;i<this.noOfMetaModelParams;i++) {
			x[i]=0;
		}
	    CobylaExitStatus result = Cobyla.findMinimum(optimization, this.noOfMetaModelParams, 0, x,0.5,Math.pow(10, -6) ,3, 1500);
	    this.MetaModelParams=x;
		
	}
	private Double[][] hessianCreator(double[] x){
		Double[][] H=new Double[this.noOfParams][this.noOfParams];
		int k=this.noOfParams+1;
		for(int i=0;i<H.length;i++) {
			for(int j=i;j<H.length;j++){
				H[i][j]=x[k];
				H[j][i]=x[k];
				k++;
			}
		}
		
		return H;
	}

	@Override
	public String getMetaModelName() {
		return this.QudaraticMetaModelName;
	}
}
