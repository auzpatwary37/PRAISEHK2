package MeasurementsReaderWriterTest;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementType;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsReader;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementsWriter;

public class MeasurementCreator {
	
	@Test
	public void tryReadAndWrite() {
		HashMap<String,Tuple<Double,Double>>timeBeans=new HashMap<>();
		timeBeans.put("BeforeMorningPeak", new Tuple<Double,Double>(0.0,25200.));
		timeBeans.put("MorningPeak", new Tuple<Double,Double>(25200.,36000.));
		timeBeans.put("AfterMorningPeak", new Tuple<Double,Double>(36000.,57600.));
		timeBeans.put("AfternoonPeak", new Tuple<Double,Double>(57600.,72000.));
		timeBeans.put("AfterAfternoonPeak", new Tuple<Double,Double>(72000.,86400.));
		
		Measurements m=Measurements.createMeasurements(timeBeans);
		
		Id<Measurement> m1Id=Id.create("1", Measurement.class);
		m.createAnadAddMeasurement("1",MeasurementType.linkVolume);
		ArrayList<Id<Link>> linkIds=new ArrayList<>();
		linkIds.add(Id.createLinkId("1_1"));
		linkIds.add(Id.createLinkId("1_2"));
		linkIds.add(Id.createLinkId("1_3"));
		linkIds.add(Id.createLinkId("1_4"));
	
		m.getMeasurements().get(m1Id).setAttribute(Measurement.linkListAttributeName, new ArrayList<>(linkIds));
		m.getMeasurements().get(m1Id).putVolume("BeforeMorningPeak",40);
		m.getMeasurements().get(m1Id).putVolume("MorningPeak",70);
		m.getMeasurements().get(m1Id).putVolume("AfterMorningPeak",140);
		
		Id<Measurement> m2Id=Id.create("2", Measurement.class);
		m.createAnadAddMeasurement("2",MeasurementType.linkVolume);
		linkIds=new ArrayList<>();
		linkIds.add(Id.createLinkId("2_1"));
		linkIds.add(Id.createLinkId("2_2"));
		linkIds.add(Id.createLinkId("2_3"));
		linkIds.add(Id.createLinkId("2_4"));
	
		m.getMeasurements().get(m2Id).setAttribute(Measurement.linkListAttributeName, new ArrayList<>(linkIds));
		m.getMeasurements().get(m2Id).putVolume("BeforeMorningPeak",50);
		m.getMeasurements().get(m2Id).putVolume("MorningPeak",80);
		m.getMeasurements().get(m2Id).putVolume("AfterMorningPeak",120);
		m.getMeasurements().get(m2Id).putVolume("AfternoonPeak",240);
	
		new MeasurementsWriter(m).write("src/main/resources/Measurements.xml");
		new MeasurementsWriter(m).write("src/main/resources/Measurements1.xml");
		Measurements m2=new MeasurementsReader().readMeasurements("src/main/resources/Measurements.xml");
		
		assertEquals(m,m2);
	}
}
