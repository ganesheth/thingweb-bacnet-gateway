package ethz.ganeshr.wot.test.KNX;

import java.util.ArrayList;
import java.util.List;


import de.thingweb.thing.Action;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;
import de.thingweb.thing.ThingMetadata;

public class KNXDiscoveryHandler {

	public void startDiscovery(KNXChannel channel){
		new Thread(new Runnable() {
			@Override
			public void run() {
				Thing t = createThing();
				channel.reportDiscovery(t);
			}
		}).start();
	}
	
public static Thing theThing;

private static Thing createThing(){		
	
		Thing thing = new Thing("test_knx_thing");
		String uri = "knxnet/test";
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_URIS, uri, uri);
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_CONTEXT, "BACnet:http://n.ethz.ch/student/ganeshr/bacnet/knxtypes.json");
		thing.getMetadata().add("@id", "1.1.5");
		thing.getMetadata().add("@type", "KNX:GeneralActuator");
		//thing.getMetadata().add("associations", "{\"href\":\"device\",\"rt\":\"parent\"}", "{\"href\":\"device\",\"rt\":\"parent\"}");
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_ENCODINGS, "JSON");
		ArrayList<String> hrefs = new ArrayList<>();
		Property pd = new Property("Output", "xsd:boolean", true, true, "KNX:DPT_RELAY_OUTP", hrefs);
		pd.getMetadata().add("@id", "6/1/1");			
		thing.addProperty(pd);
		theThing = thing;
		return thing;
	}		
}
