package ethz.ganeshr.wot.test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.obj.ObjectProperties;
import com.serotonin.bacnet4j.obj.PropertyTypeDefinition;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

import de.thingweb.servient.ServientBuilder;
import de.thingweb.servient.ThingInterface;
import de.thingweb.servient.ThingServer;
import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Action;
import de.thingweb.thing.Content;
import de.thingweb.thing.HyperMediaLink;
import de.thingweb.thing.MediaType;
import de.thingweb.thing.Metadata;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;
import de.thingweb.thing.ThingMetadata;
import de.thingweb.util.encoding.ContentHelper;

public class ServerMain {
	
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		ServerMain serverMain = new ServerMain();
		serverMain.start();
		
	}
	
	private final ThingServer server;
	private static final Logger log = LoggerFactory.getLogger(ServerMain.class);
	BACnetChannel bacnetChannel = null;
	
	public ServerMain() throws Exception{
		System.out.println("Hello");
		ServientBuilder.getHttpBinding().setPort(80);
		ServientBuilder.initialize();
		server = ServientBuilder.newThingServer();
		//final ThingDescription basicLedDesc = DescriptionParser.fromFile("e:/data/temp/basic_led.jsonld");
		//ThingInterface basicLed = server.addThing(basicLedDesc);
		//attachBasicHandlers(basicLed);

		bacnetChannel = new BACnetChannel();
		bacnetChannel.open();
		List<Thing> things = bacnetChannel.discover(5000);
		//bacnet.close();
		
		for(Thing thing : things){
			if(thing == null)
				log.error("null thing!");
			else{
			ThingInterface thingIfc = server.addThing(thing);
			attachHandler((ServedThing)thingIfc);
			}
		}
	}
	
	public void start() throws Exception{
		ServientBuilder.start();		
	}
	Date dummy;
	public void attachHandler(ServedThing thing){
		thing.onPropertyRead((input) -> {
			Property property = (Property)input;
			log.info("Got a read");
			Object result = "";
			String propertyName = property.getName();
			if(propertyName.equalsIgnoreCase("_actionSubscribeCOV/status")){
				Date d = new Date();
				long i = d.getTime() - dummy.getTime();
				if(i > 10000)
					result = "{\"state\":\"done\"}";
				else
					result = String.format("{\"state\":\"%d\"}", i);;
			}
			else{
				result = bacnetChannel.read(property);
			}
			thing.setProperty(property, result);	
			
		});
		
		thing.onPropertyUpdate((p,v)->{
			Property property = (Property)p;
			bacnetChannel.update(property, (String)v);
		});
		
		thing.onActionInvoke((a,p)->{
			Action action = (Action)a;
			String actionName = action.getName();
			Thing subThing = new Thing("_monitor_" + actionName + "_" + UUID.randomUUID().toString());
			
			String uri = "SubResources/" + thing.getName()  + "/" + actionName + "/" + subThing.getName();
			
			subThing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_URIS, uri, uri);
			subThing.getMetadata().addContext("BACnet", "http://n.ethz.ch/student/ganeshr/bacnet/bacnettypes.json");
			subThing.getMetadata().getAssociations().add(new HyperMediaLink("parent", thing.getURIs().get(0)));
			subThing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_ENCODINGS, "JSON", "JSON");
			//(String name, String xsdType, boolean isReadable, boolean isWritable, String propertyType, List<String> hrefs)
			ArrayList<String> hrefs = new ArrayList<>();
			Property statusProperty = new Property("status", "BACnet:Value", true, false, "BACnet:Monitor", hrefs);
			subThing.addProperty(statusProperty);			
			ThingInterface subThingServed = server.addThing(subThing);
			attachHandler((ServedThing)subThingServed);
			action.getMetadata().getAssociations().add(new HyperMediaLink("child", uri));
			//server.rebindSec(thing.getName(), false);
			bacnetChannel.subscribe((String)p, thing, (ServedThing)subThingServed, statusProperty);
			return new Content("{\"state\":\"subscribed\"}".getBytes(), MediaType.APPLICATION_JSON);
		});
	}	

}
