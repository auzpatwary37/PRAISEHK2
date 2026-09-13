/**
 * 
 */
package assignLinkToPlanActivity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehiclesFactory;

import com.google.common.collect.Sets;

import createBus.BusDataExtractor;
import createBus.L2lLeastCostCalculatorFactory;
import createBus.L2lNetworkLeastCostPathCalculator;

/**
 * @author JLo
 *
 */
public class AssignLinkToPlanActivity {
	/**
	 * A convenience function to define the matching table.
	 * @param path
	 */
	public static void defineMatchingTablePath(String path) {
		LinkToPlanExclusionList.defineFilePath(path);
	}
	
	/**
	 * preps the population by optimising the setLink of each activity for least cost path to next activity<br>
	 * runs parallelly, specified by numOfthread<p>
	 * 
	 * ----------------------------- !!! NOTICE !!! ----------------------------- <br>
	 * there is a timeout for parallel threads <br>
	 * curr set at <b>24HR</b> and should be fine for 4 threads full HK population<br>
	 * --------------------------------------------------------------------------
	 * 
	 * @param scenario
	 * @param numOfThread
	 * @throws InterruptedException
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	public static void run(Scenario scenario, Network TDnet, int numOfThread, boolean withCWB) throws InterruptedException, FileNotFoundException, IOException {
		Logger log = Logger.getLogger(AssignLinkToPlanActivity.class);
		
		//pre-create a vic as shared resource
		VehiclesFactory vf = scenario.getVehicles().getFactory();
		VehicleType vt = vf.createVehicleType(Id.create("car", VehicleType.class));
		vt.setPcuEquivalents(1);
		vt.setMaximumVelocity(50);
		vt.setDescription("non-descript car");
		Vehicle vic = vf.createVehicle(Id.createVehicleId("Dummy Car"), vt); //i hope the worker thread can share a car
		
		LinkToPlanExclusionList LinkToPlanExclusionList = new LinkToPlanExclusionList(withCWB, scenario.getNetwork(), TDnet, 0.5);
		Set<Id<Link>> exclusionList = LinkToPlanExclusionList.getExclusionLinks();
		
		//TODO: Optional put attribute
		LinkToPlanExclusionList.putAllElevationAttribute(scenario.getNetwork());
		
		//pre-create L2lLeastCostCalculator for num of threads as shared resource
		LockAlgo lockAlgo = new LockAlgo(scenario, numOfThread);
		
		//dump individual person as task to the executor
		ExecutorService executor = Executors.newFixedThreadPool(numOfThread);		
		ArrayList<Future<?>> listener = new ArrayList<Future<?>>();
		for(Person person:scenario.getPopulation().getPersons().values()) {
			Runnable worker = new AsignLinkToPlanActivityWorkerThread(lockAlgo, person, scenario.getNetwork(), vic, exclusionList);
			listener.add(executor.submit(worker));
		}
		
		executor.shutdown();
//		executor.awaitTermination(24, TimeUnit.HOURS);	//XXX: takes about 17 hours at 4 threads for whole HK population
		int nextMsg = 1;
		while(!executor.isTerminated()) {
			Thread.sleep(10000);	//XXX: checks every 10 second
			int count = 0;
			for(Future<?> FT: listener)
				if(FT.isDone())
					count++;
			if(count>=nextMsg) {
				log.info("Processed person # "+nextMsg);
				nextMsg *= 4;
			}
		}
		
	}
	
}

class AsignLinkToPlanActivityWorkerThread implements Runnable{
	
	private LockAlgo lockAlgo;
	private Person person;
	private Network net;
	private Vehicle vic;
	private Set<Id<Link>> exclusionList;
	
	public AsignLinkToPlanActivityWorkerThread(LockAlgo lockAlgo, Person person, Network net, Vehicle vic, 
			Set<Id<Link>> exclusionList) {
		this.lockAlgo = lockAlgo;
		this.person = person;
		this.net = net;
		this.vic = vic;
		this.exclusionList = exclusionList;
	}
	
	private Id<Link> getNextCarOnlyLink(Network net, Id<Link> firstLinkId){
		for(Link link: net.getLinks().get(firstLinkId).getToNode().getOutLinks().values()) {
			if(link.getAllowedModes().contains(TransportMode.car)) {
				return link.getId();
			}
		}
		throw new IllegalArgumentException("There is no car only link adjacent to the link "+firstLinkId.toString());
	}
	
	@Override
	public void run() {
		//get the LeastCostPathCalculator from pool
		int lockKey = lockAlgo.asign();
		L2lNetworkLeastCostPathCalculator algo = lockAlgo.get(lockKey);
		
		//extract plan activities for easier access
		ArrayList<Tuple<Activity,List<Id<Link>>>> LinkStuffList = new ArrayList<Tuple<Activity,List<Id<Link>>>>(); //It is a list for every activity
		for(PlanElement p_e:person.getPlans().get(0).getPlanElements())
			if(p_e instanceof Activity)
				LinkStuffList.add(new Tuple<Activity,List<Id<Link>>>(
						(Activity) p_e, 
						BusDataExtractor.getNearestLinksExactlyByMath(net, ((Activity)p_e).getCoord(), BusDataExtractor.SEARCH_DISTANCE, Sets.newHashSet(TransportMode.car), 70/3.6, exclusionList) ));
		if(LinkStuffList.size()<2) {
			lockAlgo.release(lockKey);
			throw new IllegalArgumentException("incomplete plan for person "+person.getId().toString());
		}
		
		//for the 1st leg, compare all combinations
		Id<Link> startLinkId = LinkStuffList.get(0).getSecond().get(0);
		Id<Link> secondLinkId = LinkStuffList.get(1).getSecond().get(0);
		boolean breakFlag = false;
		double mincost = Double.MAX_VALUE;	//turns out path cost is in magnitude of 10^4 -> in seconds so ~3hr?
		for(Id<Link> LinkId:LinkStuffList.get(0).getSecond()) {
			MinCom TMinCom = new MinCom(LinkId, algo, person, vic);
			for(Id<Link> toLinkId:LinkStuffList.get(1).getSecond())
				if(!TMinCom.compareTheMin(toLinkId) && LinkStuffList.get(0).getSecond().size()==1){
					startLinkId = LinkId; //This line is not necessary
					secondLinkId = getNextCarOnlyLink(net, LinkId);
					breakFlag = true;
					break;
				}
			if(breakFlag)
				break;
			if(TMinCom.leastCost()<=mincost) {
				startLinkId = LinkId;
				secondLinkId = TMinCom.closestLink();
				mincost = TMinCom.leastCost();
			}
		}
		LinkStuffList.get(0).getFirst().setLinkId(startLinkId);
		LinkStuffList.get(1).getFirst().setLinkId(secondLinkId);
		
		//make iterator for the rest of the legs
		Iterator<Tuple<Activity, List<Id<Link>>>> itr = LinkStuffList.iterator();
		itr.next();
		itr.next();
		startLinkId = secondLinkId;
		
		//for rest of legs, find least cost
		while(itr.hasNext()) {
			breakFlag = false;
			Tuple<Activity, List<Id<Link>>> nextAct = itr.next();
			MinCom TMinCom = new MinCom(startLinkId, algo, person, vic);
			for(Id<Link> TLinkId:nextAct.getSecond())
				if(!TMinCom.compareTheMin(TLinkId))
					if(nextAct.getSecond().size()==1) {
						Id<Link> temp = getNextCarOnlyLink(net, TLinkId);
						nextAct.getFirst().setLinkId(temp);
						startLinkId = temp;
						breakFlag = true;
						break;
					}
			if(breakFlag)
				continue;
			nextAct.getFirst().setLinkId(TMinCom.closestLink());
			startLinkId = TMinCom.closestLink();
		}
		
		//release algo back into pool !!!VERY IMPORTANT!!!
		lockAlgo.release(lockKey);
	}
	
	private class MinCom{
		private Id<Link> startLink;
		private L2lNetworkLeastCostPathCalculator algo;
		private Person person2;
		private Vehicle vic;
		
		private Id<Link> closestLink;
		private double minCost;
		
		public MinCom(Id<Link> startLink, L2lNetworkLeastCostPathCalculator algo, Person person2, Vehicle vic) {
			this.startLink = startLink;
			this.algo = algo;
			this.person2 = person2;
			this.vic = vic;
			this.minCost = Double.MAX_VALUE;
		}
		
		/**
		 * This function compares and store the min cost
		 * @param linkId
		 * @return true, if the linkId input is not the startLink
		 */
		public boolean compareTheMin(Id<Link> linkId) {
			double thisCost;
			if(linkId.equals(startLink))
				return false;
			else 
				thisCost = algo.calcLeastCostPath(startLink, linkId, 0, person2, vic).travelCost;
			
			if(thisCost<=minCost) {
				this.minCost = thisCost;
				this.closestLink = linkId;
			}
			
			return true;
		}
		
		public Id<Link> closestLink(){
			return this.closestLink;
		}
		
		public double leastCost() {
			return this.minCost;
		}
	}
}

/**
 * class to limit the use and avoid repeated creation of L2lNetworkLeastCostPathCalculator across threads and worker threads
 * by creating a fixed pool of L2lNetworkLeastCostPathCalculator according to the num of thread can be used
 * @author JLo
 *
 */
class LockAlgo{
	
	private ConcurrentHashMap<Integer, L2lNetworkLeastCostPathCalculator> algoPool;
	private ArrayDeque<Integer> lock;
	
	public LockAlgo(Scenario scenario, int numOfThread) {
		this.algoPool = new ConcurrentHashMap<Integer, L2lNetworkLeastCostPathCalculator>();
		this.lock = new ArrayDeque<Integer>();
		for(int i=0;i<numOfThread;i++) {
			this.lock.add(i);
			this.algoPool.put(i, new L2lLeastCostCalculatorFactory(scenario, Sets.newHashSet(TransportMode.car)).getRoutingAlgo());
		}
	}
	
	public synchronized int asign() {
		return lock.pop();
	}
	
	public synchronized void release(int key) {
		this.lock.add(key);
	}
	
	public L2lNetworkLeastCostPathCalculator get(int key) {
		return algoPool.get(key);
	}
	
}
