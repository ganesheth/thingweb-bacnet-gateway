package ethz.ganeshr.wot.test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.thingweb.desc.ThingDescriptionParser;
import de.thingweb.discovery.TDRepository;
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
import ethz.ganeshr.wot.test.BACnet.BACnetDiscoveryHandler;
import ethz.ganeshr.wot.test.KNX.KNXChannel;

public class ServerMain {

	private static String tdfile = "room_h110_compliant_with_comments.jsonld";

	public static final TDRepository repo = new TDRepository("http://localhost:8088");
	public static long lifetime;

	public static void main(String[] args) throws Exception {
		String bacip = null;
		int bacport = 47808, httpport = 80, coapport = 5683;
		long lifetime = 600;

		if (args.length > 0) {
			int index = 0;
			while (index < args.length) {
				String arg = args[index];
				if ("-usage".equals(arg) || "-help".equals(arg) || "-h".equals(arg) || "-?".equals(arg)) {
					printUsage();
				} else if ("-adapter".equals(arg)) {
					bacip = args[index + 1];
				} else if ("-bacport".equals(arg)) {
					bacport = Integer.parseInt(args[index + 1]);
				} else if ("-httpport".equals(arg)) {
					httpport = Integer.parseInt(args[index + 1]);
				} else if ("-coapport".equals(arg)) {
					coapport = Integer.parseInt(args[index + 1]);
				} else if ("-tdfile".equals(arg)) {
					tdfile = args[index + 1];
				} else if ("-lifetime".equals(arg)) {
					lifetime = Integer.parseInt(args[index + 1]);
				} else {
					System.err.println("Unknwon arg " + arg);
					printUsage();
				}
				index += 2;
			}
		}
		ServerMain serverMain = new ServerMain(bacip, bacport, httpport, coapport, lifetime);

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
		System.out.println("	-adapter ADDRESS");
		System.out.println("		Bind the client to a specific host IP address given by ADDRESS .");
		System.out.println("	-bacport PORT");
		System.out.println("		Listen on UDP port PORT (default is 47808).");
		System.out.println("	-httpport PORT");
		System.out.println("		HTTP Server to listen on this port.");
		System.out.println("	-tdfile filename");
		System.out.println("		Filename of the ThingDescription to use.");
		System.exit(0);
	}

	private final ThingServer server;
	private static final Logger log = LoggerFactory.getLogger(ServerMain.class);
	ChannelBase bacnetChannel = null;
	ChannelBase knxChannel = null;
	List<ChannelBase> channels = new ArrayList<>();

	public ServerMain(String bacnetAdapterIpAddr, int bacnetPort, int httpPort, int coapPort, long lifeTime)
			throws Exception {

		ServientBuilder.getHttpBinding().setPort(httpPort);
		// ServientBuilder.getCoapBinding().setPort(coapPort);
		ServientBuilder.initialize();
		server = ServientBuilder.newThingServer();

		lifetime = lifeTime;

		if (bacnetAdapterIpAddr == null) {
			bacnetChannel = new BACnetChannel();
		} else {
			bacnetChannel = new BACnetChannel(new BACnetChannelParam(bacnetAdapterIpAddr, bacnetPort));
			knxChannel = new KNXChannel(bacnetAdapterIpAddr);
		}	
		
		channels.add(bacnetChannel);
		if(knxChannel != null)
			channels.add(knxChannel);
	}
	
	private void registerTestTD(){
	    try {
			ClassLoader classLoader = ServerMain.class.getClassLoader();
			URL fileURL = classLoader.getResource("test.jsonld");	
		    Path path = Paths.get(fileURL.getPath().substring(1));
			byte[] data = Files.readAllBytes(path);
			repo.addTD("https://example.com/things", 600, data);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void start() throws Exception {
		ServientBuilder.start();
		for (ChannelBase channel : channels) {
			channel.addThingFoundCallback((l) -> {
				List<Thing> things = (List<Thing>) l;
				System.out.println(String.format("Discovery report %d new Things", things.size()));

				for (Thing thing : things) {
					if (thing == null)
						log.error("null thing!");
					else {
						log.info("Serving " + thing.getName());
						ThingInterface thingIfc = server.addThing(thing);
						thing.servedThing = thingIfc;
						attachHandler(channel, (ServedThing) thingIfc);

						try {
							//String handle = repo.addTD(thing.getMetadata().get("@id"), lifetime, ThingDescriptionParser.toBytes(thing));
							//log.info("Registered under " + handle + " for " + lifetime + " s");
						} catch (Exception e) {
							log.error("Error during TD Repo registration: " + e.getMessage());
						}
					}
				}
			});

			channel.addThingDeletedCallback((t) -> {
				server.removeThing((Thing) t);
			});

			channel.open();
			channel.discoverAsync(true);
		}
		//bacnetChannel.discoverFromFile(tdfile);
		// bacnetChannel.discoverFromFile("room_h110_compliant_with_comments.jsonld");
		knxChannel.discoverFromFile("knx_1.jsonld");
	}

	public void stop() {
		try {
			for (ChannelBase channel : channels)
				channel.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	Date dummy;

	public void attachHandler(ChannelBase channel, ServedThing thing) {
		thing.onPropertyRead((input) -> {
			Property property = (Property) input;
			log.info("Got a read");
			Object result = "";
			result = channel.read(property);
			thing.setProperty(property, result);
		});

		thing.onPropertyUpdate((p, v) -> {
			Property property = (Property) p;
			Object result = channel.update(property, (String) v);
			return result;
		});

		thing.onActionInvoke((a, p) -> {
			Action action = (Action) a;
			Content content = new Content("{\"result\":\"sucess\"}".getBytes(), MediaType.APPLICATION_JSON);
			try {
				content = channel.handleAction(thing, action, p);
			} catch (Exception e) {
				e.printStackTrace();
				content = new Content(("{\"error\":\"" + e.getMessage() + "\"}").getBytes(), MediaType.APPLICATION_JSON);
				content.setResponseType(Content.ResponseType.SERVER_ERROR);
			}

			return content;
		});
	}
}
