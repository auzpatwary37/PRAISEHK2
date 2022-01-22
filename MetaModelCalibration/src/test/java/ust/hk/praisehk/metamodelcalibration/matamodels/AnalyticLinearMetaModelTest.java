package ust.hk.praisehk.metamodelcalibration.matamodels;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.linear.MatrixUtils;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class AnalyticLinearMetaModelTest {



	// This one tests for zero valued single point fitting
	@Test
	public void test1() {
		double error = 0;
		inputSingleData d = new inputSingleData();
		Id<Measurement> mId = d.idZeroSame;
		MetaModel m = new AnalyticLinearMetaModel(mId,d.simM,d.anaM,d.param,d.timeId,d.currentParamNo);
		double meta = m.calcMetaModel(d.anaM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId), d.param.get(0));
		double sim = d.simM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId);
		error = Math.pow((meta-sim),2);
		if(error>0.00001) {
			fail("Error too high!!!"+error);
		}
		if(m.getanaGradMultiplier()<0)fail("AnaMultiplier negative!!!");
		if(MatrixUtils.createRealVector(m.getGradientVector()).getNorm()>1)fail("Meta Gradients too high!!!");

	}

	// This one tests for zero valued single point fitting
	@Test
	public void test2() {
		double error = 0;
		inputSingleData d = new inputSingleData();
		Id<Measurement> mId = d.idNonZeroSame;
		MetaModel m = new AnalyticLinearMetaModel(mId,d.simM,d.anaM,d.param,d.timeId,d.currentParamNo);
		double meta = m.calcMetaModel(d.anaM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId), d.param.get(0));
		double sim = d.simM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId);
		error = Math.pow((meta-sim),2);
		if(error>0.00001)fail("Error too high!!!");
		if(m.getanaGradMultiplier()<0)fail("AnaMultiplier negative!!!");
		if(MatrixUtils.createRealVector(m.getGradientVector()).getNorm()>1)fail("Meta Gradients too high!!!");
		System.out.println(Arrays.toString(m.getMetaModelParams()));
	}

	// This one tests for zero valued single point fitting
	@Test
	public void test3() {
		double error = 0;
		inputSingleData d = new inputSingleData();
		Id<Measurement> mId = d.idNotSameZeroAna;
		MetaModel m = new AnalyticLinearMetaModel(mId,d.simM,d.anaM,d.param,d.timeId,d.currentParamNo);
		double meta = m.calcMetaModel(d.anaM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId), d.param.get(0));
		double sim = d.simM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId);
		error = Math.pow((meta-sim),2);
		if(error>0.001) {
			fail("Error too high!!!");
		}
		if(m.getanaGradMultiplier()<0)fail("AnaMultiplier negative!!!");
		System.out.println(Arrays.toString(m.getMetaModelParams()));
		//if(MatrixUtils.createRealVector(m.getGradientVector()).getNorm()>1)fail("Meta Gradients too high!!!");

	}

	// This one tests for zero valued single point fitting
	@Test
	public void test4() {
		double error = 0;
		inputSingleData d = new inputSingleData();
		Id<Measurement> mId = d.idNotSameZeroSim;
		MetaModel m = new AnalyticLinearMetaModel(mId,d.simM,d.anaM,d.param,d.timeId,d.currentParamNo);
		double meta = m.calcMetaModel(d.anaM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId), d.param.get(0));
		double sim = d.simM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId);
		error = Math.pow((meta-sim),2);
		if(error>.00001)fail("Error too high!!!");
		if(m.getanaGradMultiplier()<0)fail("AnaMultiplier negative!!!");
		//if(MatrixUtils.createRealVector(m.getGradientVector()).getNorm()>1)fail("Meta Gradients too high!!!");
		System.out.println(Arrays.toString(m.getMetaModelParams()));
	}
	
	// This one tests for zero valued single point fitting
		@Test
		public void test5() {
			double error = 0;
			inputSingleData d = new inputSingleData();
			Id<Measurement> mId = d.idNotSameNonZero;
			MetaModel m = new AnalyticLinearMetaModel(mId,d.simM,d.anaM,d.param,d.timeId,d.currentParamNo);
			double meta = m.calcMetaModel(d.anaM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId), d.param.get(0));
			double sim = d.simM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId);
			error = Math.pow((meta-sim),2);
			if(error>0.00001)fail("Error too high!!!");
			if(m.getanaGradMultiplier()<0)fail("AnaMultiplier negative!!!");
			//if(MatrixUtils.createRealVector(m.getGradientVector()).getNorm()>1)fail("Meta Gradients too high!!!");
			System.out.println(Arrays.toString(m.getMetaModelParams()));
		}
	// This one tests for zero valued single multi point fitting
			@Test
			public void test6() {
				double error = 0;
				inputMultiSameData d = new inputMultiSameData();
				Id<Measurement> mId = d.idNotSameNonZero;
				MetaModel m = new AnalyticLinearMetaModel(mId,d.simM,d.anaM,d.param,d.timeId,d.currentParamNo);
				double meta = m.calcMetaModel(d.anaM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId), d.param.get(0));
				double sim = d.simM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId);
				error = Math.pow((meta-sim),2);
				if(error>0.00001)fail("Error too high!!!");
				if(m.getanaGradMultiplier()<0)fail("AnaMultiplier negative!!!");
				//if(MatrixUtils.createRealVector(m.getGradientVector()).getNorm()>1)fail("Meta Gradients too high!!!");
				System.out.println(Arrays.toString(m.getMetaModelParams()));
			}
			// This one tests for zero valued multi point fitting
			@Test
			public void test7() {
				double error = 0;
				inputMultiSepData d = new inputMultiSepData();
				Id<Measurement> mId = d.idSame;
				MetaModel m = new AnalyticLinearMetaModel(mId,d.simM,d.anaM,d.param,d.timeId,d.currentParamNo);
				double meta = m.calcMetaModel(d.anaM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId), d.param.get(0));
				double sim = d.simM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId);
				error = Math.pow((meta-sim),2);
				if(error>0.00001)fail("Error too high!!!");
				if(m.getanaGradMultiplier()<0)fail("AnaMultiplier negative!!!");
				//if(MatrixUtils.createRealVector(m.getGradientVector()).getNorm()>1)fail("Meta Gradients too high!!!");
				System.out.println(Arrays.toString(m.getMetaModelParams()));
			}
			
			// This one tests for zero valued multi point fitting
			@Test
			public void test8() {
				double error = 0;
				inputMultiSepData d = new inputMultiSepData();
				Id<Measurement> mId = d.idNotSame;
				MetaModel m = new AnalyticLinearMetaModel(mId,d.simM,d.anaM,d.param,d.timeId,d.currentParamNo);
				double meta = m.calcMetaModel(d.anaM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId), d.param.get(0));
				double sim = d.simM.get(0).getMeasurements().get(mId).getVolumes().get(d.timeId);
				error = Math.pow((meta-sim),2);
				if(error>0.00001)fail("Error too high!!!");
				if(m.getanaGradMultiplier()<0)fail("AnaMultiplier negative!!!");
				//if(MatrixUtils.createRealVector(m.getGradientVector()).getNorm()>1)fail("Meta Gradients too high!!!");
				System.out.println(Arrays.toString(m.getMetaModelParams()));
			}
	class inputSingleData{
		public Id<Measurement> idZeroSame = Id.create("a", Measurement.class);
		public Id<Measurement> idNonZeroSame = Id.create("b", Measurement.class);
		public Id<Measurement> idNotSameZeroAna = Id.create("c", Measurement.class);
		public Id<Measurement> idNotSameZeroSim = Id.create("d", Measurement.class);
		public Id<Measurement> idNotSameNonZero = Id.create("e", Measurement.class);
		public String timeId = "1";
		public Measurements mm;
		public Map<Integer,Measurements> simM = new HashMap<>();
		public Map<Integer,Measurements> anaM = new HashMap<>();
		public Map<Integer,LinkedHashMap<String,Double>> param = new HashMap<>();
		public int currentParamNo = 0;

		public inputSingleData() {
			Map<String,Tuple<Double,Double>> tb = new HashMap<>();
			tb.put(timeId, new Tuple<Double,Double>(0.,3600.));
			mm = Measurements.createMeasurements(tb);//this will be sim 

			mm.createAnadAddMeasurement(idZeroSame.toString(), MeasurementType.linkVolume);
			mm.createAnadAddMeasurement(idNonZeroSame.toString(), MeasurementType.linkVolume);
			mm.createAnadAddMeasurement(idNotSameZeroAna.toString(), MeasurementType.linkVolume);
			mm.createAnadAddMeasurement(idNotSameZeroSim.toString(), MeasurementType.linkVolume);
			mm.createAnadAddMeasurement(idNotSameNonZero.toString(), MeasurementType.linkVolume);

			Measurements am = mm.clone();//this will be ana 

			mm.getMeasurements().get(idZeroSame).putVolume(timeId,0.);
			am.getMeasurements().get(idZeroSame).putVolume(timeId,0.);

			mm.getMeasurements().get(idNonZeroSame).putVolume(timeId,1000.);
			am.getMeasurements().get(idNonZeroSame).putVolume(timeId,1000.);

			mm.getMeasurements().get(idNotSameZeroAna).putVolume(timeId,1000.);
			am.getMeasurements().get(idNotSameZeroAna).putVolume(timeId,0.);


			mm.getMeasurements().get(idNotSameZeroSim).putVolume(timeId,0.);
			am.getMeasurements().get(idNotSameZeroSim).putVolume(timeId,1000.);

			mm.getMeasurements().get(idNotSameNonZero).putVolume(timeId,700.);
			am.getMeasurements().get(idNotSameNonZero).putVolume(timeId,1000.);

			this.simM.put(0, mm);
			this.anaM.put(0,am);

			LinkedHashMap<String,Double> p = new LinkedHashMap<>();
			for(int i =0;i<1000;i++) {
				p.put("od_"+i, 1.);
			}
			this.param.put(0, p);
		}
	}

	class inputMultiSameData{
		public Id<Measurement> idZeroSame = Id.create("a", Measurement.class);
		public Id<Measurement> idNonZeroSame = Id.create("b", Measurement.class);
		public Id<Measurement> idNotSameZeroAna = Id.create("c", Measurement.class);
		public Id<Measurement> idNotSameZeroSim = Id.create("d", Measurement.class);
		public Id<Measurement> idNotSameNonZero = Id.create("e", Measurement.class);
		public String timeId = "1";
		public Measurements mm;
		public Map<Integer,Measurements> simM = new HashMap<>();
		public Map<Integer,Measurements> anaM = new HashMap<>();
		public Map<Integer,LinkedHashMap<String,Double>> param = new HashMap<>();
		public int currentParamNo = 1;

		public inputMultiSameData() {
			Map<String,Tuple<Double,Double>> tb = new HashMap<>();
			tb.put(timeId, new Tuple<Double,Double>(0.,3600.));
			mm = Measurements.createMeasurements(tb);//this will be sim 

			mm.createAnadAddMeasurement(idZeroSame.toString(), MeasurementType.linkVolume);
			mm.createAnadAddMeasurement(idNonZeroSame.toString(), MeasurementType.linkVolume);
			mm.createAnadAddMeasurement(idNotSameZeroAna.toString(), MeasurementType.linkVolume);
			mm.createAnadAddMeasurement(idNotSameZeroSim.toString(), MeasurementType.linkVolume);
			mm.createAnadAddMeasurement(idNotSameNonZero.toString(), MeasurementType.linkVolume);

			Measurements am = mm.clone();//this will be ana 

			mm.getMeasurements().get(idZeroSame).putVolume(timeId,0.);
			am.getMeasurements().get(idZeroSame).putVolume(timeId,0.);

			mm.getMeasurements().get(idNonZeroSame).putVolume(timeId,1000.);
			am.getMeasurements().get(idNonZeroSame).putVolume(timeId,1000.);

			mm.getMeasurements().get(idNotSameZeroAna).putVolume(timeId,1000.);
			am.getMeasurements().get(idNotSameZeroAna).putVolume(timeId,0.);


			mm.getMeasurements().get(idNotSameZeroSim).putVolume(timeId,0.);
			am.getMeasurements().get(idNotSameZeroSim).putVolume(timeId,1000.);

			mm.getMeasurements().get(idNotSameNonZero).putVolume(timeId,700.);
			am.getMeasurements().get(idNotSameNonZero).putVolume(timeId,1000.);

			this.simM.put(0, mm);
			this.anaM.put(0,am);

			LinkedHashMap<String,Double> p = new LinkedHashMap<>();
			for(int i =0;i<1000;i++) {
				p.put("od_"+i, 1.);
			}
			LinkedHashMap<String,Double> p1 = new LinkedHashMap<>();
			for(int i =0;i<1000;i++) {
				double sign =0; 
				if(Math.random()>0.5)sign = 1;
				else sign = -1;
				p1.put("od_"+i, 1.+Math.random()*sign);
			}
			this.param.put(0, p);
			this.param.put(1, p1);
			this.simM.put(1,mm.clone());
			this.anaM.put(1, am.clone());
		}
	}

	class inputMultiSepData{
		public Id<Measurement> idSame = Id.create("a", Measurement.class);
		public Id<Measurement> idNotSame = Id.create("b", Measurement.class);

		public String timeId = "1";
		public Measurements mm;
		public Map<Integer,Measurements> simM = new HashMap<>();
		public Map<Integer,Measurements> anaM = new HashMap<>();
		public Map<Integer,LinkedHashMap<String,Double>> param = new HashMap<>();
		public int currentParamNo = 1;

		public inputMultiSepData() {
			Map<String,Tuple<Double,Double>> tb = new HashMap<>();
			tb.put(timeId, new Tuple<Double,Double>(0.,3600.));
			mm = Measurements.createMeasurements(tb);//this will be sim 

			mm.createAnadAddMeasurement(idSame.toString(), MeasurementType.linkVolume);
			mm.createAnadAddMeasurement(idNotSame.toString(), MeasurementType.linkVolume);


			Measurements am = mm.clone();//this will be ana 

			mm.getMeasurements().get(idSame).putVolume(timeId,110.);
			am.getMeasurements().get(idSame).putVolume(timeId,110.);

			mm.getMeasurements().get(idNotSame).putVolume(timeId,100.);
			am.getMeasurements().get(idNotSame).putVolume(timeId,150.);


			this.simM.put(0, mm);
			this.anaM.put(0,am);

			LinkedHashMap<String,Double> p = new LinkedHashMap<>();
			for(int i =0;i<1000;i++) {
				p.put("od_"+i, 1.);
			}
			LinkedHashMap<String,Double> p1 = new LinkedHashMap<>();
			for(int i =0;i<1000;i++) {
				double sign =0; 
				if(Math.random()>0.5)sign = 1;
				else sign = -1;
				p1.put("od_"+i, 1.+Math.random()*sign);
			}
			this.param.put(0, p);
			this.param.put(1, p1);
			this.simM.put(1,mm.clone());
			this.anaM.put(1, am.clone());
			this.simM.get(1).getMeasurements().get(idSame).putVolume(timeId, 150);
			this.anaM.get(1).getMeasurements().get(idSame).putVolume(timeId, 150);

			this.simM.get(1).getMeasurements().get(idNotSame).putVolume(timeId, 110);
			this.anaM.get(1).getMeasurements().get(idNotSame).putVolume(timeId, 200);
		}
	}
}
