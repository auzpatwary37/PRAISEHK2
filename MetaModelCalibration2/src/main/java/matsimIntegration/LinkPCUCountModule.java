package matsimIntegration;

import java.util.LinkedHashMap;

import org.matsim.core.controler.AbstractModule;

import com.google.inject.Singleton;
import com.google.inject.name.Names;

import measurements.Measurements;



public class LinkPCUCountModule extends AbstractModule{
		
		private final String fileLoc;
		private MeasurementsStorage Storage;
		private LinkedHashMap<String,Double> currentParam;
		
		public LinkPCUCountModule(MeasurementsStorage storage,String FileLoc,LinkedHashMap<String,Double>currentParam) {
			this.Storage=storage;
			this.fileLoc=FileLoc;
			this.currentParam=currentParam;
			}	
		
		public void install() {
			
			bind(String.class).annotatedWith(Names.named("fileLoc")).toInstance(this.fileLoc);
			bind(paramContainer.class).annotatedWith(Names.named("currentParam")).toInstance(new paramContainer(this.currentParam));
			bind(LinkPCUCountEventHandler.class).toInstance(new LinkPCUCountEventHandler(this.Storage));
			this.addControlerListenerBinding().to(LinkPCUCountControlerListener.class);
			bind(MeasurementsStorage.class).toInstance(this.Storage);
			
		}
		
		
}
class paramContainer{
	private LinkedHashMap<String,Double> param;
	public paramContainer(LinkedHashMap<String,Double> param){
		this.param=param;
	}
	public LinkedHashMap<String,Double>getParam(){
		return this.param;
	}
}