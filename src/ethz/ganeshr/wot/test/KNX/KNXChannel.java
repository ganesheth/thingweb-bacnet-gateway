package ethz.ganeshr.wot.test.KNX;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.naming.directory.InvalidAttributesException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.sun.org.apache.xerces.internal.util.URI;

import de.thingweb.desc.ThingDescriptionParser;
import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Action;
import de.thingweb.thing.Content;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;
import ethz.ganeshr.wot.test.ChannelBase;
import ethz.ganeshr.wot.test.ServerMain;
import ethz.ganeshr.wot.test.BACnet.BACnetDiscoveryHandler;
import ethz.ganeshr.wot.test.BACnet.BACnetThingRelationship;
import javafx.util.Pair;
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
import tuwien.auto.calimero.process.ProcessCommunicationBase;
import tuwien.auto.calimero.process.ProcessCommunicator;
import tuwien.auto.calimero.process.ProcessCommunicatorImpl;
import tuwien.auto.calimero.process.ProcessEvent;
import tuwien.auto.calimero.process.ProcessListener;
import tuwien.auto.calimero.Util;
import tuwien.auto.calimero.datapoint.Datapoint;
import tuwien.auto.calimero.datapoint.StateDP;
import tuwien.auto.calimero.dptxlator.DPTXlator8BitUnsigned;

public class KNXChannel extends ChannelBase {
	
	private KNXDiscoveryHandler discoveryHandler = new KNXDiscoveryHandler();	
	private ProcessCommunicator pc;
	private KNXNetworkLink link;	
	private List<Thing> mDiscoveredThings = new ArrayList<Thing>();
	
	@Override
	public void open() throws Exception
	{
		link = new KNXNetworkLinkIP(KNXNetworkLinkIP.TUNNELING, new InetSocketAddress(InetAddress.getByName("192.168.0.102"), 0),
			getServer(), false, TPSettings.TP1);
		pc = new ProcessCommunicatorImpl(link);
		pc.setResponseTimeout(5);
		pc.setPriority(Priority.LOW);
		subscribeToConnectionEvents();
	}
	
	@Override
	public void close(){
		pc.detach();
		link.close();
	}
	
	private void subscribeToConnectionEvents(){
		pc.addProcessListener(new ProcessListener(){
			@Override
			public void groupWrite(ProcessEvent e) {
				System.out.print("A group write was detected " );
				try{
					if(e.getServiceCode() == 0x80 ){
						String address = e.getDestination().toString();
						System.out.print("Address:" + address);
						List<Pair<ServedThing, Property>> properties = findPropertiesWithAddress(address);
						for(Pair<ServedThing, Property> servedProperty : properties){
							ServedThing st = servedProperty.getKey();
							Property property = servedProperty.getValue();
							System.out.print(" Property:" + property.getName());
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
					}
					System.out.println(".");
				}catch (Exception ex){} 
				
			}

			@Override
			public void detached(DetachEvent e) {
				// TODO Auto-generated method stub
				
			}});
	}
	

	private List<Pair<ServedThing, Property>> findPropertiesWithAddress(String address){
		List<Pair<ServedThing, Property>> properties = new ArrayList<>();
		for(Thing thing : mDiscoveredThings){
			for(Property property : thing.getProperties()){
				String knxHref = (String)property.getTag();				
				if(knxHref.compareTo(address)== 0)
					properties.add(new Pair<ServedThing, Property>((ServedThing) thing.servedThing, property));
			}
		}
		return properties;
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
				prop.setTag(knxHref); //We store the KNX address as tag for later lookup in findPropertiesWithAddress;
				URI href = new URI(knxHref, true);
				if(!href.isAbsoluteURI())
					href.absolutize(uri);

			}
			super.reportDiscovery(knxThing);
			
			mDiscoveredThings.add(knxThing);
			
			
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
		String groupId = property.getHrefs().get(2);
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.createObjectNode();
		try{
			System.out.println("group read" );
			GroupAddress grpAddr = new GroupAddress(groupId);
			if(property.getValueType().contains("DPT_BOOL")){
				boolean result = pc.readBool(grpAddr);		
				node.put("value", result);
				return node.toString();
			}
			if(property.getValueType().contains("DPT_FLOAT")){
				float result = pc.readFloat(grpAddr);				
				node.put("value", result);
				return node.toString();
			}
			if(property.getValueType().contains("DPT_UINT")){
				Datapoint dp = new StateDP(grpAddr, "test");
				String str = pc.read(dp);
				int result = Integer.parseUnsignedInt(str, 16);
				node.put("value", result);
				return node.toString();
			}			
			return new RuntimeException("Cannot find KNX datatype to write");
		}catch (Exception e){			
			return new RuntimeException(e.getMessage());
		}
	}

	@Override
	public Object update(Property property, String value) {
		String groupId = property.getHrefs().get(2);
		try{
			GroupAddress grpAddr = new GroupAddress(groupId);
			ObjectMapper mapper = new ObjectMapper();
			JsonNode node = mapper.readTree(value);
			ObjectNode responseNode = mapper.createObjectNode();
			responseNode.put("result", "changed");
			if(property.getValueType().contains("DPT_BOOL")){
				if(!node.get("value").isBoolean())
					return new RuntimeException("Payload is not a boolean value. Expected {\"value\":<boolean>}");
				boolean val = node.get("value").asBoolean();
				pc.write(grpAddr, val);
				return responseNode.toString();
			}
			if(property.getValueType().contains("DPT_FLOAT")){
				float val = (float)node.get("value").asDouble();
				pc.write(grpAddr, val);
				return responseNode.toString();
			}
			if(property.getValueType().contains("DPT_UINT")){
				int val = node.get("value").asInt();
				pc.write(grpAddr, val, ProcessCommunicationBase.SCALING);
				return responseNode.toString();
			}			
			return new RuntimeException("Cannot find KNX datatype to write");
		}catch (Exception e){
			return new RuntimeException(e.getMessage());
		}
	}

	@Override
	public Content handleAction(ServedThing thing, Action action, Object inputData) throws Exception {
		return null;
	}	
	

}
