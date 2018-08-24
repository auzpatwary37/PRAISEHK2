package calibrator;

import java.util.Map;

import org.matsim.api.core.v01.Id;

import matamodels.MetaModel;
import measurements.Measurement;

public interface Calibrator {

	//This will generate meta-models
	public Map<Id<Measurement>,Map<String,MetaModel>> generateMetaModel();
	
	
}
