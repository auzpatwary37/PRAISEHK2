package createMTR;

import java.util.ArrayList;

/**
 * A class to define the direction (route) of a train line, it is definied by
 * first stop and last stop
 * 
 * @author eleead
 *
 */
public class Direction {
	private String firstStop;
	private String lastStop;
	private String id;
	private ArrayList<String> joint;

	public Direction(String first, String last) {
		this.firstStop = first;
		this.lastStop = last;
	}

	public String getFirstStop() {
		return this.firstStop;
	}

	public String getLastStop() {
		return this.lastStop;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void addJoint(String id) {
		if (this.joint == null) {
			this.joint = new ArrayList<String>();
		}
		this.joint.add(id);
	}

	public String getId() {
		return this.id;
	}

	public ArrayList<String> getJoint() {
		return this.joint;
	}

	public boolean equals(Object o) {
		Direction other = (Direction) o;
		if (other.getFirstStop().equals(this.firstStop) && other.getLastStop().equals(this.lastStop)) {
			return true;
		}
		return false;
	}

	public int hashCode() {
		return firstStop.hashCode() * 923543 + lastStop.hashCode();
	}

	public String toString() {
		return "Direction from " + this.firstStop + " to " + this.lastStop;
	}
}
