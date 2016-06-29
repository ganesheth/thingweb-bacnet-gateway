package ethz.ganeshr.wot.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.thingweb.servient.ServientBuilder;
import de.thingweb.servient.ThingInterface;
import de.thingweb.servient.ThingServer;
import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Action;
import de.thingweb.thing.Content;
import de.thingweb.thing.MediaType;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;
import ethz.ganeshr.wot.test.BACnet.BACnetChannel;
import ethz.ganeshr.wot.test.BACnet.BACnetChannelParam;
import ethz.ganeshr.wot.test.KNX.KNXChannel;

public class ServerMain {
	
	public static void main(String[] args) throws Exception {
		String bacip = null;
		int bacport = 47808, httpport = 80;
		if (args.length > 0) {
			int index = 0;
			while (index < args.length) {
				String arg = args[index];
				if ("-usage".equals(arg) || "-help".equals(arg) || "-h".equals(arg) || "-?".equals(arg)) {
					printUsage();
				} else if ("-bacip".equals(arg)) {
					bacip = args[index+1];
				} else if ("-bacport".equals(arg)) {
					bacport = Integer.parseInt(args[index+1]);
				} else if ("-httpport".equals(arg)) {
					httpport = Integer.parseInt(args[index+1]);
				} else {
					System.err.println("Unknwon arg "+arg);
					printUsage();
				}
				index += 2;
			}
		}	
		ServerMain serverMain = new ServerMain(bacip, bacport, 80);
		
		serverMain.start();
		
		 try {
             System.in.read();
             serverMain.stop();
		 } catch (IOException e) {
             e.printStackTrace();
		 }
		 System.exit(0);
		
	}
	
	private static void printUsage() {
		System.out.println();
		System.out.println("SYNOPSIS");
		System.out.println("	" + ServerMain.class.getSimpleName() + " [-bacip ADDRESS] [-bacport PORT] [-httpport PORT]");
		System.out.println("OPTIONS");
		System.out.println("	-bacip ADDRESS");
		System.out.println("		Bind the BACnet client to a specific host IP address given by ADDRESS .");
		System.out.println("	-bacportp PORT");
		System.out.println("		Listen on UDP port PORT (default is 47808).");
		System.out.println("	-httpport PORT");
		System.out.println("		HTTP Server to listen on this port.");
		System.exit(0);
	}	
	
	private final ThingServer server;
	private static final Logger log = LoggerFactory.getLogger(ServerMain.class);
	ChannelBase bacnetChannel = null;
	ChannelBase knxChannel = null;
	List<ChannelBase> channels = new ArrayList<>();
	
	public ServerMain(String bacnetAdapterIpAddr, int bacnetPort, int httpPort) throws Exception{
		ServientBuilder.getHttpBinding().setPort(httpPort);
		ServientBuilder.initialize();
		server = ServientBuilder.newThingServer();

		if(bacnetAdapterIpAddr == null)
			bacnetChannel = new BACnetChannel();
		else
			bacnetChannel = new BACnetChannel(new BACnetChannelParam(bacnetAdapterIpAddr, bacnetPort));		

		knxChannel = new KNXChannel();	
		
		channels.add(bacnetChannel);
		//channels.add(knxChannel);		
	}
	
	public void start() throws Exception{
		ServientBuilder.start();	
		for(ChannelBase channel : channels){
			channel.addThingFoundCallback((l)->{
				List<Thing> things = (List<Thing>)l;
				System.out.println(String.format("Discovery report %d new Things", things.size()));
				
				for(Thing thing : things){
					if(thing == null)
						log.error("null thing!");
					else{
						ThingInterface thingIfc = server.addThing(thing);
						thing.servedThing = thingIfc;
						attachHandler(channel, (ServedThing)thingIfc);
					}
				}			
			});
			
			channel.addThingDeletedCallback((t)->{
				server.removeThing((Thing)t);
			});
			
			channel.open();
			channel.discoverAsync(false);
		}
		
		bacnetChannel.discoverFromFile("room_h110_compliant_with_comments.jsonld");
		knxChannel.discoverFromFile("knx_1.jsonld");
	}
	
	public void stop(){
		try {
			for(ChannelBase channel : channels)
				channel.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	Date dummy;
	public void attachHandler(ChannelBase channel, ServedThing thing){
		thing.onPropertyRead((input) -> {
			Property property = (Property)input;
			log.info("Got a read");
			Object result = "";
			result = channel.read(property);
			thing.setProperty(property, result);			
		});
		
		thing.onPropertyUpdate((p,v)->{
			Property property = (Property)p;
			Object result = channel.update(property, (String)v);
			return result;
		});
		
		thing.onActionInvoke((a,p)->{
			Action action = (Action)a;
			try {
				channel.handleAction(thing, action, p);
			} catch (Exception e) {
				e.printStackTrace();
			}

			return new Content("{\"state\":\"subscribed\"}".getBytes(), MediaType.APPLICATION_JSON);
		});
	}	

}
