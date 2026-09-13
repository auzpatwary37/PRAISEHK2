package dynamicTransitRouter.fareCalculators;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.healthmarketscience.jackcess.Cursor;
import com.healthmarketscience.jackcess.CursorBuilder;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

import createBus.Runbus;
import dynamicTransitRouter.fareCalculators.ZonalFareCalculator.BusRouteFare;

/**
 * This class is solely designed for extract the bus fare information from TD.
 * @author eleead
 *
 */
public class BusFareDataExtractor {

	private File fareFile;
	
	public BusFareDataExtractor() throws IOException {
		this(Runbus.PATH+"FARE_BUS.mdb"); // default values
	}
	
	public BusFareDataExtractor(String fareFileDir) throws IOException {
		fareFile = new File(fareFileDir);
	}
	
	public void loadFareToZonalFareCal(int route_id, int route_seq, int max_stop_seq, BusRouteFare busRouteFare) throws IOException {
		List<SectionFare> sectionFareList = Lists.newArrayList();
		
		Database db = DatabaseBuilder.open(fareFile);
		Table table = db.getTable("FARE");
		Cursor cursor = CursorBuilder.createCursor(table);
		//Input the route stop, and sort them
		for (Row row : cursor.newIterable().setMatchPattern("ROUTE_ID", route_id)) {
			if(row.getInt("ROUTE_SEQ")==route_seq) {
				SectionFare sectionFare = new SectionFare(row.get("ON_SEQ"), row.get("OFF_SEQ"), row.get("PRICE"));
				sectionFareList.add(sectionFare);
			}
		}
		
		Collections.sort(sectionFareList); //Sort them in the correct sequence.
		
		for(SectionFare sf: sectionFareList) {
			if(sf.getOffSequence()<=max_stop_seq)
				busRouteFare.addSectionFare(sf.getOnSequence() - 1, sf.getOffSequence() - 1, sf.getFare());
		}
	}
	
	private class SectionFare implements Comparable<SectionFare> {
		private int onSequence;
		private int offSequence;
		private BigDecimal fare;

		public SectionFare(Object onSeq, Object offSeq, Object price) {
			this.onSequence = (int) onSeq;
			this.offSequence = (int) offSeq;
			this.fare = (BigDecimal) price;
		}

		@Override
		public int compareTo(SectionFare o) {
			if(onSequence > o.onSequence) 
				return 1;
			else if(onSequence < o.getOnSequence())
				return -1;
			else {
				if(offSequence < o.offSequence) 
					return 1;
				else if(offSequence > o.getOffSequence())
					return -1;
				else
					return 0;
			}
		}

		public double getFare() {
			return this.fare.doubleValue();
		}

		public int getOffSequence() {
			return this.offSequence;
		}

		public int getOnSequence() {
			return this.onSequence;
		}

		@Override
		public String toString() {
			return "On: "+ onSequence + " Off: " + offSequence + " fare:$"+fare;
		}
	}
}
