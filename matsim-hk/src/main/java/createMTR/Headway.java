package createMTR;

public class Headway {
	private double headwaySecond;
	private int portion;
	private int denominator;
	private boolean hasPortion = false;

	public Headway(double freq) {
		this.headwaySecond = freq;
	}

	public Headway(String freq) {
		this.headwaySecond = Double.parseDouble(freq);
	}

	public void setPortionAndDenominator(int portion, int denominator) {
		this.portion = portion;
		this.denominator = denominator;
		this.hasPortion = true;
	}

	public void setPortionAndDenominator(String portion, String denominator) {
		this.portion = Integer.parseInt(portion);
		this.denominator = Integer.parseInt(denominator);
		this.hasPortion = true;
	}

	public int getPortion() {
		return portion;
	}

	public int getDenominator() {
		return denominator;
	}

	public double getFrequency() {
		return headwaySecond;
	}

	public boolean hasPortion() {
		return hasPortion;
	}
}
