package ethz.ganeshr.wot.test.KNX;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;

import javax.naming.directory.InvalidAttributesException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.sun.org.apache.xerces.internal.util.URI;

import de.thingweb.desc.ThingDescriptionParser;
import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Action;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;
import ethz.ganeshr.wot.test.ChannelBase;
import ethz.ganeshr.wot.test.ServerMain;
import ethz.ganeshr.wot.test.BACnet.BACnetDiscoveryHandler;
import ethz.ganeshr.wot.test.BACnet.BACnetThingRelationship;
import tuwien.auto.calimero.DetachEvent;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.Priority;
import tuwien.auto.calimero.exception.KNXException;
import tuwien.auto.calimero.knxnetip.Discoverer;
import tuwien.auto.calimero.knxnetip.servicetype.SearchResponse;
import tuwien.auto.calimero.link.KNXNetworkLink;
import tuwien.auto.calimero.link.KNXNetworkLinkIP;
import tuwien.auto.calimero.link.medium.TPSettings;
import tuwien.auto.calimero.process.ProcessCommunicator;
import tuwien.auto.calimero.process.ProcessCommunicatorImpl;
import tuwien.auto.calimero.process.ProcessEvent;
import tuwien.auto.calimero.process.ProcessListener;
import tuwien.auto.calimero.Util;

public class KNXChannel extends ChannelBase {
	
	KNXDiscoveryHandler discoveryHandler = new KNXDiscoveryHandler();
	
	private ProcessCommunicator pc;
	private KNXNetworkLink link;
	private boolean doingRead = false;
	
	@Override
	public void open() throws Exception
	{
		link = new KNXNetworkLinkIP(KNXNetworkLinkIP.TUNNELING, new InetSocketAddress(InetAddress.getByName("192.168.0.102"), 0),
			getServer(), false, TPSettings.TP1);
		pc = new ProcessCommunicatorImpl(link);
		pc.setResponseTimeout(5);
		pc.setPriority(Priority.LOW);
		pc.addProcessListener(new ProcessListener(){

			@Override
			public void groupWrite(ProcessEvent e) {
				// TODO Auto-generated method stub
				System.out.println("A group write was detected " );
				try{
					if(e.getServiceCode() == 0x80 && e.getDestination().equals(new GroupAddress("6/1/1"))){
						System.out.println("Eis ächti notification?" );
						ServedThing st = (ServedThing)KNXDiscoveryHandler.theThing.servedThing;
						Property property = (Property) st.getThingModel().getProperty("Output");
						new Thread(new Runnable() {
							@Override
							public void run() {
								Object o = read(property);
								property.isUnderAsyncUpdate = true;
								st.setProperty(property, o);
								property.isUnderAsyncUpdate = false;
							}
						}).start();	
					}
				}catch (Exception ex){} 
				
			}

			@Override
			public void detached(DetachEvent e) {
				// TODO Auto-generated method stub
				
			}});
	}
	
	@Override
	public void close(){
		pc.detach();
		link.close();
	}
	
	@Override
	public void discoverFromFile(String filename) {
		try {
			ClassLoader classLoader = KNXChannel.class.getClassLoader();
			URL fileURL = classLoader.getResource(filename);	
			final Thing knxThing = ThingDescriptionParser.fromFile(fileURL.getPath().substring(1));
			List<String> uris = knxThing.getMetadata().getAll("uris");
			String uriKnxBase = null;
			int uriIndex = 0;
			for(String u : uris){
				if(u.startsWith("knxip:")){
					uriKnxBase = u;
					break;
				}
				uriIndex++;
			}
			if(uriKnxBase == null)
				return;
			URI uri = new URI(uriKnxBase);
			for(Property prop : knxThing.getProperties()){
				List<String> hrefs = prop.getHrefs();
				String knxHref = hrefs.get(uriIndex);
				
				URI href = new URI(knxHref, true);
				if(!href.isAbsoluteURI())
					href.absolutize(uri);

			}
			super.reportDiscovery(knxThing);
			
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	};
	
	private static InetSocketAddress server;
	private static IndividualAddress device;
	
	public static InetSocketAddress getServer() throws KNXException
	{
		if (server == null) {
			final Discoverer d = new Discoverer(Util.getLocalHost().getAddress(), Util.getLocalHost().getPort(), false, false);
			try {
				d.startSearch(2, true);
			}
			catch (final InterruptedException e) {
				e.printStackTrace();
			}
			final SearchResponse[] searchResponses = d.getSearchResponses();
			for (int i = 0; i < searchResponses.length; i++) {
				final SearchResponse res = searchResponses[i];
				final InetAddress addr = res.getControlEndpoint().getAddress();
				server = new InetSocketAddress(addr, res.getControlEndpoint().getPort());
				device = res.getDevice().getAddress();
				return server;				
			}
			System.err.println("\n\tA unit test case requests the KNX test server, but no running instance was found!\n"
					+ "\t\t--> Many tests requiring KNXnet/IP will fail.\n");
		}
		return server;
	}

	@Override
	public void discoverAsync(boolean activeMode)  throws Exception {
		// TODO Auto-generated method stub
		//discoveryHandler.startDiscovery(this);
	}

	@Override
	public Object read(Property property) {
		doingRead = true;
		String groupId = property.getHrefs().get(2);
		try{
			System.out.println("group read" );
			GroupAddress grpAddr = new GroupAddress(groupId);
			boolean result = pc.readBool(grpAddr);
			doingRead = false;
			return result ? "true" : "false";
		}catch (Exception e){
			return "Error";
		}
	}

	@Override
	public Object update(Property property, String value) {
		String groupId = property.getHrefs().get(2);
		try{
			GroupAddress grpAddr = new GroupAddress(groupId);
			ObjectMapper mapper = new ObjectMapper();
			JsonNode node = mapper.readTree(value);
			if(!node.isBoolean())
				return new RuntimeException("Payload is not a boolean value. Expected {\"value\":<boolean>}");
			boolean val = node.asBoolean();
			pc.write(grpAddr, val);
			return "ok";
		}catch (Exception e){
			return e;
		}
	}

	@Override
	public void handleAction(ServedThing thing, Action action, Object inputData) throws Exception {
		// TODO Auto-generated method stub
		
	}	
	

}
