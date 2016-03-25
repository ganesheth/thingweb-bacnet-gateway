package ethz.ganeshr.wot.test;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.thingweb.desc.DescriptionParser;
import de.thingweb.desc.pojo.InteractionDescription;
import de.thingweb.desc.pojo.Metadata;
import de.thingweb.desc.pojo.PropertyDescription;
import de.thingweb.desc.pojo.ThingDescription;
import de.thingweb.servient.ServientBuilder;
import de.thingweb.servient.ThingInterface;
import de.thingweb.servient.ThingServer;
import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Property;
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
		List<ThingDescription> things = bacnetChannel.discover(5000);
		//bacnet.close();
		
		for(ThingDescription thing : things){
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
	
	public void attachHandler(ServedThing thing){
		thing.onPropertyRead((input) -> {
			Property property = (Property)input;
			log.info("Got a read");
			String result = bacnetChannel.read(property.getDescription());
			thing.setProperty(property, result);	
			
		});
	}	

}
