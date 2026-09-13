package population.TCS;

import java.util.ArrayList;

import org.matsim.api.core.v01.Id;

import population.TPUSB;
/**
 * 
 * @author Ashraf
 *
 */

public class HouseHold {
	/**
	 * This class will represent the household of TCS database
	 * This class will use the TPUSB class already defined in the GVTCS package
	 * 
	 */
	private Id<HouseHold> houseHoldId;
	private double typeOfHousing;
	private double noOfHouseHoldMember;
	private double vehicleAvailable;
	private double samplingDistrict;
	private double typeOfHousingTYPE;
	private double attachmentSurveyNo;
	private double monthlyHouseholdIncome;
	private TPUSB tpusbAddress;
	private double householdExpansionFactor;
	private ArrayList<HouseHoldMember> members=new ArrayList<>();
	
	public HouseHold(double qNo,double typeofhousingA1,double noOfMember,double vehicleAvailable,double samplingDistrict, 
			double typeOfHousingTYPE,double attachmentSurveyNo, double householdIncome,TPUSB addressTPUSB,double expansionFactor) {
		this.houseHoldId=Id.create(Double.toString(qNo),HouseHold.class);
		this.typeOfHousing=typeofhousingA1;
		this.noOfHouseHoldMember=noOfMember;
		this.vehicleAvailable=vehicleAvailable;
		this.samplingDistrict=samplingDistrict;
		this.typeOfHousingTYPE=typeOfHousingTYPE;
		this.attachmentSurveyNo=attachmentSurveyNo;
		this.monthlyHouseholdIncome=householdIncome;
		this.tpusbAddress=addressTPUSB;
		this.householdExpansionFactor=expansionFactor;
		
	}
	
	public HouseHold clone() {
		return new HouseHold(Double.parseDouble(this.houseHoldId.toString()),
				this.typeOfHousing,this.noOfHouseHoldMember,this.vehicleAvailable,this.samplingDistrict,this.typeOfHousingTYPE,
				this.attachmentSurveyNo,this.monthlyHouseholdIncome,this.tpusbAddress,this.householdExpansionFactor);
	}

	public Id<HouseHold> getHouseHoldId() {
		return houseHoldId;
	}

	public double getTypeOfHousing() {
		return typeOfHousing;
	}

	public double getNoOfHouseHoldMember() {
		return noOfHouseHoldMember;
	}

	public double getVehicleAvailable() {
		return vehicleAvailable;
	}

	public double getSamplingDistrict() {
		return samplingDistrict;
	}

	public double getTypeOfHousingTYPE() {
		return typeOfHousingTYPE;
	}

	public double getAttachmentSurveyNo() {
		return attachmentSurveyNo;
	}

	public double getMonthlyHouseholdIncome() {
		return monthlyHouseholdIncome;
	}

	public TPUSB getTpusbAddress() {
		return tpusbAddress;
	}

	public double getHouseholdExpansionFactor() {
		return householdExpansionFactor;
	}
	
	public void addMember(HouseHoldMember member) {
		this.members.add(member);
	}
	public ArrayList<HouseHoldMember> getMembers(){
		return this.members;
	}
}
