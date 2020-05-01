package ust.hk.praisehk.metamodelcalibration.Utils;

//import com.mathworks.toolbox.javabuilder.MWArray;
//import com.mathworks.toolbox.javabuilder.MWClassID;
//import com.mathworks.toolbox.javabuilder.MWJavaObjectRef;
//import com.mathworks.toolbox.javabuilder.MWNumericArray;
//
//import OptimDemo2.Optimizer;

public class MatlabOptimizer {
	
	private MatlabObj objective;
	private double[] initialValue;
	private double[] lowerBound;
	private double[] upperBound;
	
	public MatlabOptimizer(MatlabObj objective, double[] initialValue, double[] lowerBound, double[] upperBound) {
		this.objective=objective;
		this.initialValue=initialValue;
		this.lowerBound=lowerBound;
		this.upperBound=upperBound;
	}
	
	
	
	public MatlabObj getObjective() {
		return objective;
	}



	public MatlabResult performOptimization() {
//		Optimizer theOptimizer = null;		/* Stores component 
//        instance */
//		MWJavaObjectRef origRef = null;	/* Java object reference to 
//      be passed to component */
//		MWJavaObjectRef outputRef = null;	/* Output data extracted 
//         from result */
//		MWNumericArray x0 = null;	/* Initial point for optimization */
//		MWNumericArray xl = null;	/* Initial point for optimization */
//		MWNumericArray xu = null;	/* Initial point for optimization */
//		MWNumericArray x = null;	/* Location of minimal value */
//		MWNumericArray fval = null;	/* Minimal function value */
//		Object[] result = null;	/* Stores the result */
//
//		try
//		{
//
//			/* Instantiate a new Java object */
//			/* This should only be done once per application instance */
//			theOptimizer = new Optimizer();
//
//			try {
//				x0 = new MWNumericArray(this.initialValue, MWClassID.DOUBLE);
//				xl=	new MWNumericArray(this.lowerBound, MWClassID.DOUBLE);
//				xu=	new MWNumericArray(this.upperBound, MWClassID.DOUBLE);
//				
//				
//				System.out.println("Using x0 =");
//				System.out.println(x0);
//
//				/* Create object reference to objective function object */
//				origRef = new MWJavaObjectRef(this.objective);
//
//				/* Pass Java object to a MATLAB function that lists its 
//				methods, etc */            
//				System.out.println("*********************************");
//				System.out.println("** Properties of Java object   **");
//				System.out.println("*********************************");
//				result = theOptimizer.displayObj(1, origRef);     
//				MWArray.disposeArray(result);
//				System.out.println("** Finished DISPLAYOBJ **********");
//
//				/* Call the Java component to optimize the function */
//				/* using the MATLAB function FMINCon */
//				System.out.println("**********************************");
//				System.out.println("** constrained nonlinear optim**");
//				System.out.println("**********************************");
//				result = theOptimizer.doOptimFminCon(2, origRef, x0, xl, xu);
//				try {
//					System.out.println("** Finished DOOPTIM ****** *********");
//					x = (MWNumericArray)result[0];
//					fval = (MWNumericArray)result[1];
//
//					/* Display the results of the optimization */
//					System.out.println("Location of minimum: ");
//					System.out.println(x);
//					System.out.println("Function value at minimum: ");
//					System.out.println(fval.toString());
//				}
//				finally
//				{
//					MWArray.disposeArray(result);
//				}
//			}
//			finally
//			{
//				/* Free native resources */
//				MWArray.disposeArray(origRef);
//				MWArray.disposeArray(outputRef);
//				MWArray.disposeArray(x0);
//			}
//		}
//		catch (Exception e)
//		{
//			System.out.println("Exception: " + e.toString());
//		}
//
//		finally
//		{
//			/* Free native resources */
//			if (theOptimizer != null)
//				theOptimizer.dispose();
//		}
//		
//		return new MatlabResult(x.getDoubleData(),fval.getDoubleData()[0]);
//
		return null;
	}

}


