package population;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;

public class TPUSB {
	/**
	 * This class stores details of each TPUSB and its pdz zone as well as broad District zones.
	 */
	private final Coord satCoord;
	private final Id<TPUSB> TPUSBId;
	private final double TPUId;
	private final double SBVCId;
	private final double PDZ454id;
	private final double district26Id;
	private final String district26Name;
	private final double area;
	private final double halfLength;


	public TPUSB(double TPUSBId,Coord satCoord,int pdzId,int distId,String distName,double area) {
		this.satCoord=satCoord;
		this.TPUSBId=Id.create(Double.toString(TPUSBId), TPUSB.class);
		this.TPUId=TPUSBId/1000;
		this.SBVCId=TPUSBId-this.TPUId*1000;
		this.PDZ454id=pdzId;
		this.district26Id=distId;
		this.district26Name=distName;
		this.area=area;
		this.halfLength=Math.sqrt(area)/2.;
	}
	public TPUSB(String TPUSBId,Coord satCoord,int pdzId,int distId,String distName,double area) {
		this.satCoord=satCoord;
		this.TPUSBId=Id.create(TPUSBId, TPUSB.class);
		this.TPUId=0;
		this.SBVCId=0;
		this.PDZ454id=pdzId;
		this.district26Id=distId;
		this.district26Name=distName;
		this.area=area;
		this.halfLength=Math.sqrt(area)/2.;
	}
	
	public double getHalfLength() {
		return halfLength;
	}
	public Coord getSatCoord() {
		return satCoord;
	}


	public Id<TPUSB> getTPUSBId() {
		return TPUSBId;
	}


	public double getTPUId() {
		return TPUId;
	}


	public double getSBVCId() {
		return SBVCId;
	}


	public double getPDZ454id() {
		return PDZ454id;
	}


	public double getDistrict26Id() {
		return district26Id;
	}


	public String getDistrict26Name() {
		return district26Name;
	}


}
