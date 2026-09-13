package population.TCS;

/**
 * Store the mode inside TCS
 * @author eleead
 *
 */
public class TCSMode {
	private double modeId;
	private String modeDetails;
	private String flatMode;
	private Double pcu;
	
	public TCSMode(double modeId, String modeDetails, String flatMode, Double pcu) {
		this.modeId=modeId;
		this.modeDetails=modeDetails;
		this.flatMode=flatMode;
		this.pcu=pcu;
	}

	public double getModeId() {
		return modeId;
	}

	public String getModeDetails() {
		return modeDetails;
	}

	public String getFlatMode() {
		return flatMode;
	}

	public double getPcu() {
		return pcu;
	}
}
