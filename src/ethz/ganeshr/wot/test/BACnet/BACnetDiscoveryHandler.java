package ethz.ganeshr.wot.test.BACnet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.obj.ObjectProperties;
import com.serotonin.bacnet4j.obj.PropertyTypeDefinition;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.enumerated.Segmentation;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.util.RequestUtils;
import com.sun.jndi.toolkit.url.Uri;
import com.sun.org.apache.xerces.internal.util.URI;

import de.thingweb.desc.ThingDescriptionParser;
import de.thingweb.thing.Action;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;
import de.thingweb.thing.ThingMetadata;
import ethz.ganeshr.wot.test.ServerMain;

public class BACnetDiscoveryHandler {
	public static Map<String, Thing> discoveredThings = new HashMap<>();
	public static ArrayList<ObjectIdentifier> notificationClasses = new ArrayList<>();
	public static Map<Integer, RemoteDevice> discoveredDevices = new HashMap<>();
	private static BACnetChannel channel;
	public static boolean activeMode = false;
	public static void setChannel(BACnetChannel chnl){
		channel = chnl;
	}

	public static void handleDeviceFound(final RemoteDevice d, LocalDevice localDevice) {
		System.out.println("IAm received from " + d);
		System.out.println("Segmentation: " + d.getSegmentationSupported());
		d.setSegmentationSupported(Segmentation.noSegmentation);
		
		if(!discoveredDevices.containsKey(d.getInstanceNumber())){
			System.out.println(String.format("Adding device %d to list", d.getInstanceNumber()));
			discoveredDevices.put(d.getInstanceNumber(), d);
		}
			

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					channel.getExtendedDeviceInformation(d);
					System.out.println("Done getting extended information");

					@SuppressWarnings("unchecked")
					List<ObjectIdentifier> oids = ((SequenceOf<ObjectIdentifier>) RequestUtils
							.sendReadPropertyAllowNull(localDevice, d, d.getObjectIdentifier(),
									PropertyIdentifier.propertyList.objectList)).getValues();
					//System.out.println(oids);
					List<Thing> thingsDiscovered = new ArrayList<Thing>();
					
					String deviceName = d.getName();						
					Thing devicething = createThing(d, deviceName, d.getObjectIdentifier());
					thingsDiscovered.add(devicething);
					discoveredThings.put(d.getObjectIdentifier().toString(), devicething);

					for (ObjectIdentifier oid : oids) {
						//Register this client as recipient for notifications (with all NCs).
						if(oid.getObjectType().intValue() == ObjectType.notificationClass.intValue()){
							notificationClasses.add(oid);
							int index = channel.getRegisteredIndexInRecipientList(d, oid);
							if(index > 0)
								BACnetEventHandler.createEventSubscriptionMonitorThing(d, oid);
						}
						
						if(		oid.getObjectType().intValue() != ObjectType.binaryInput.intValue() &&
								oid.getObjectType().intValue() != ObjectType.binaryOutput.intValue() &&
								oid.getObjectType().intValue() != ObjectType.analogInput.intValue() &&
								oid.getObjectType().intValue() != ObjectType.analogOutput.intValue() &&
								oid.getObjectType().intValue() != ObjectType.eventEnrollment.intValue()){
							continue;
						}
						CharacterString objectName = (CharacterString)RequestUtils.readProperty(localDevice, d, oid, PropertyIdentifier.objectName, null);
						Thing objectThing = createThing(d, objectName.toString(), oid);
						thingsDiscovered.add(objectThing);
						discoveredThings.put(oid.toString(), objectThing);

					}
					if(activeMode)
						channel.reportDiscovery(thingsDiscovered);
					

				} catch (BACnetException e) {
					e.printStackTrace();
				}
			}


		}).start();
	}
	
	private static String processName(String name)
	{
		return name.replace('\'', '/').replace(" ", "_");
	}
	
	public static Thing handleCreateFromTDFile(String filename, boolean registerProperties){
		ClassLoader classLoader = BACnetDiscoveryHandler.class.getClassLoader();
		URL fileURL = classLoader.getResource(filename);		
		
		try {
			final Thing bacnetThing = ThingDescriptionParser.fromFile(fileURL.getPath().substring(1));
			if(registerProperties){
				List<String> uris = bacnetThing.getMetadata().getAll("uris");
				String uriBacnetBase = null;
				int uriIndex = 0;
				for(String u : uris){
					if(u.startsWith("bacnet:")){
						uriBacnetBase = u;
						break;
					}
					uriIndex++;
				}
				if(uriBacnetBase == null)
					return null;
				URI uri = new URI(uriBacnetBase);
				for(Property prop : bacnetThing.getProperties()){
					List<String> hrefs = prop.getHrefs();
					String bacnetHref = hrefs.get(uriIndex);
					
					URI href = new URI(bacnetHref, true);
					if(!href.isAbsoluteURI())
						href.absolutize(uri);
					
					String host = href.getAuthority().replace("//", "");
					
					String path = href.getPath();
					String[] parts = path.substring(1).split("/");
					Integer deviceInstance = Integer.parseInt(parts[0]);
					Integer objectType = Integer.parseInt(parts[1]);
					Integer objectInstance = Integer.parseInt(parts[2]);
					Integer propertyId = 85;
					if(parts.length > 3)
						propertyId = Integer.parseInt(parts[3]);
					Integer propertyIndex = null;
					if(parts.length > 4)
						propertyId = Integer.parseInt(parts[4]);
					
					RemoteDevice device = discoveredDevices.get(deviceInstance);
					ObjectIdentifier oid = new ObjectIdentifier(new ObjectType(objectType), objectInstance);
					PropertyIdentifier pid = new PropertyIdentifier(propertyId);
					
					BACnetThingRelationship dopid = new BACnetThingRelationship(device, oid, pid, bacnetThing, prop);
					channel.relationshipMap.put(prop.getMetadata().get("@id"), dopid);
					prop.setTag(bacnetThing);
				}
			}
			channel.reportDiscovery(bacnetThing);
			return bacnetThing;
			
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}		
	}
	
	public static Thing handleCreateFromTDFile(String filename){
		return handleCreateFromTDFile(filename, true);
	}
	
	private static Thing createThing(RemoteDevice device, String objectName, ObjectIdentifier oid){

		String deviceName = processName(device.getName());

		String uri = deviceName + "/" + processName(objectName);
		
		objectName = processName(objectName);
		
		List<PropertyTypeDefinition> properties =  ObjectProperties.getPropertyTypeDefinitions(oid.getObjectType());
		
		Thing thing = new Thing(objectName);
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_URIS, uri, uri);
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_CONTEXT, "BACnet:http://n.ethz.ch/student/ganeshr/bacnet/bacnettypes.json");
		int deviceInstance = device.getInstanceNumber();
		int typeId = oid.getObjectType().intValue();
		int instanceNumber = oid.getInstanceNumber();
		
		String id = String.format("%d_%s", deviceInstance, oid.getIDSTring());		
		thing.getMetadata().add("@id", id);
		String objectType = "BACnet:" + oid.getObjectType().toString().replace(" ", "");
		thing.getMetadata().add("@type", oid.getObjectType().toString());
		//thing.getMetadata().add("associations", "{\"href\":\"device\",\"rt\":\"parent\"}", "{\"href\":\"device\",\"rt\":\"parent\"}");
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_ENCODINGS, "JSON");

		for(PropertyTypeDefinition prop : properties){
			int propId = prop.getPropertyIdentifier().intValue();
			if(		propId != PropertyIdentifier.presentValue.intValue() &&
					propId != PropertyIdentifier.statusFlags.intValue() &&
					propId != PropertyIdentifier.outOfService.intValue()){
				continue;
			}
			PropertyIdentifier pid = prop.getPropertyIdentifier();
			String propertyName = pid.toString();
			boolean isWriteable = true;// ObjectProperties.isCommandable(oid.getObjectType(), pid);
			String typeName = prop.getClazz().getTypeName();
			int lastDot = typeName.lastIndexOf(".");
			typeName =" BACnet:" + typeName.substring(lastDot + 1);
			ArrayList<String> hrefs = new ArrayList<>();
			//hrefs.add(propertyName);
			//hrefs.add(propertyName);
			Property pd = new Property(propertyName, typeName, true, isWriteable, "BACnet:ObjectProperty", hrefs);
			BACnetThingRelationship dopid = new BACnetThingRelationship(device, oid, pid, thing, pd);
			//pd.setTag(dopid);
			String p_id =  String.format("%d_%s_%d", deviceInstance, oid.getIDSTring(), prop.getPropertyIdentifier().intValue());
			pd.getMetadata().add("@id", p_id);			
			thing.addProperty(pd);
			
			channel.relationshipMap.put(p_id, dopid);
		}		
		if(oid.getObjectType().intValue() == ObjectType.device.intValue()){
			Action action1 = Action.getBuilder("_actionSubscribeCOV").setInputType("BACnet:COVSubscriptionParameters").setOutputType("BACnet:COVMonitor").build();
			thing.addAction(action1);

			//Action action2 = Action.getBuilder("_actionSubscribeEvents").setInputType("BACnet:EventSubscriptionParameters").setOutputType("BACnet:EventMonitor").build();
			//thing.addAction(action2);
		}

		//thing.getMetadata().setAdditionalContexts(bacnetContexts);
		thing.setTag(new BACnetThingRelationship(device, oid, null, null, null));
		return thing;
	}	
}
