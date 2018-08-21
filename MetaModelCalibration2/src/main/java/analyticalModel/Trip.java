package analyticalModel;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.NetworkUtils;






public class Trip {
	public void setPersonId(Id<Person> personId) {
		this.PersonId = personId.toString();
	}
	private String[] part;
	private String Oact;
	private String Dact;
	private String mode;
	private String PersonId;
	private double Originx;
	private double Originy;
	private double Destinationx;
	private double Destinationy;
	private double startTime;
	private double endTime;
	protected Coord activity1coord;
	protected Coord activity2coord;
	protected Id<AnalyticalModelODpair> ODpairId;
	protected Node originNode;
	
	protected Node destinationNode;
	private double expansionFactor=1;
	private double tripWalkingTime=0;
	private AnalyticalModelRoute route;
	private AnalyticalModelTransitRoute trRoute;
	private String subPopulationName=null;
	private static final Logger logger=Logger.getLogger(Trip.class);
	/**
	 * 
	 * @param line - containing all the data probably from a file (.csv)
	 * @param ind_O index of origin Id
	 * @param ind_D index of destination Id
	 * @param ind_Ox index of Origin X
	 * @param ind_Oy
	 * @param ind_Dx
	 * @param ind_Dy
	 * @param ind_mode
	 * @param ind_strt index of trip start time 
	 * @param ind_end index of trip end time
	 */
	
	
	/**
	 * Convert the TCSTime to MATSim time
	 * @param TCSTime
	 * @return
	 */
	private double fixtime(int TCSTime) {
		int hour=TCSTime/100;
		int min=TCSTime%100;
		
		return hour*3600+min*60;
	}
	
	/**
	 * Generate a Origin Destination Pair ID
	 * @param network
	 * @return
	 */
	public Id<AnalyticalModelODpair> generateODpairId(Network network) {
		
		if(this.getSubPopulationName()!=null) {
			this.originNode=NetworkUtils.getNearestNode(network, activity1coord);
			this.destinationNode=NetworkUtils.getNearestNode(network, activity2coord);
			ODpairId=Id.create(this.getOriginNode().getId().toString()+"_"+this.getDestinationNode().getId().toString()+"_"+this.getSubPopulationName(),AnalyticalModelODpair.class);
			return ODpairId;
		}
		this.originNode=NetworkUtils.getNearestNode(network, activity1coord);
		this.destinationNode=NetworkUtils.getNearestNode(network, activity2coord);
		ODpairId=Id.create(this.getOriginNode().getId().toString()+"_"+this.getDestinationNode().getId().toString(),AnalyticalModelODpair.class);
		return ODpairId;

	}
	
	
	/**
	 * ----------------------------------Getter and Setter---------------------------------------
	 * 
	 * 
	 */
	/**
	 * For TCS expansion Factor
	 * or for further population generation
	 * @return
	 */
	public double getExpansionFactor() {
		return expansionFactor;
	}
	
	public void setExpansionFactor(double expansionFactor) {
		this.expansionFactor = expansionFactor;
	}
	
	
	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public Id<Person> getPersonId() {
		return Id.create(PersonId,Person.class);
	}

	public void setPersonId(String personId) {
		PersonId = personId;
	}

	public double getStartTime() {
		return startTime;
	}
	public void setStartTime(double strt) {
		this.startTime = strt;
	}
	public double getEndTime() {
		return endTime;
	}
	public void setEndTime(double endt) {
		this.endTime = endt;
	}
	public String getOriginActivity() {
		return Oact;
	}
	public void setOriginActivity(String oact) {
		Oact = oact;
	}
	public String getDestinationActivity() {
		return Dact;
	}
	public void setDestinationActivity(String dact) {
		Dact = dact;
	}
	
	public Coord getAct1coord() {
		return activity1coord;
	}
	public void setAct1coord(Coord act1coord) {
		this.activity1coord = act1coord;
	}
	public Coord getAct2coord() {
		return activity2coord;
	}
	public void setAct2coord(Coord act2coord) {
		this.activity2coord = act2coord;
	}
	public void setOriginNode(Node node){
		this.originNode=node;
	}
	public void setDestinationNode(Node node){
		this.destinationNode=node;
	}
	public Node getOriginNode(){
		return originNode;
	}
	public Node getDestinationNode(){
		return destinationNode;
	}

	public double getTripWalkingTime() {
		return tripWalkingTime;
	}

	public void setTripWalkingTime(double tripWalkingTime) {
		this.tripWalkingTime = tripWalkingTime;
	}

	public void setRoute(AnalyticalModelRoute route){		
		this.route=route;
	}
	
	public AnalyticalModelRoute getRoute() {
		return route;
	}
	public void setTrRoute(AnalyticalModelTransitRoute trRoute) {
		this.trRoute=trRoute;
	}
	public AnalyticalModelTransitRoute getTrRoute() {
		return this.trRoute;
	}
	
	public String getSubPopulationName() {
		return subPopulationName;
	}

	public void setSubPopulationName(String subPopulationName) {
		this.subPopulationName = subPopulationName;
	}
}
