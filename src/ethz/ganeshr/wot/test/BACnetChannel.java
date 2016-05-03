package ethz.ganeshr.wot.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONObject;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.ServiceFuture;
import com.serotonin.bacnet4j.event.DeviceEventAdapter;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.obj.ObjectProperties;
import com.serotonin.bacnet4j.obj.PropertyTypeDefinition;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyAck;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVRequest;
import com.serotonin.bacnet4j.service.confirmed.WritePropertyRequest;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.ObjectPropertyReference;
import com.serotonin.bacnet4j.type.constructed.PropertyReference;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.ServicesSupported;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.enumerated.Segmentation;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.PropertyReferences;
import com.serotonin.bacnet4j.util.PropertyValues;
import com.serotonin.bacnet4j.util.RequestUtils;

import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Action;
import de.thingweb.thing.Event;
import de.thingweb.thing.Metadata;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;
import de.thingweb.thing.ThingMetadata;
import javafx.util.Pair;

public class BACnetChannel {
	private LocalDevice localDevice;
	List<Thing> discoveredThings = new ArrayList<>();
	private List<Pair<String, String>> bacnetContexts = new ArrayList<>();
	
	public void open() throws Exception {
		IpNetwork network = new IpNetworkBuilder().localBindAddress("192.168.0.102").build();
		Transport transport = new DefaultTransport(network);
		// transport.setTimeout(15000);
		// transport.setSegTimeout(15000);
		localDevice = new LocalDevice(1234, transport);
		localDevice.initialize();
		localDevice.getEventHandler().addListener(new Listener());
		Pair<String,String> context= new Pair<>("BACnet", "http://bacowl.sourceforge.net/2012/bacnet");
		bacnetContexts.add(context);
	}

	public void close() {

	}

	public List<Thing> discover(long timeout) throws Exception {
		discoveredThings.clear();
		localDevice.sendGlobalBroadcast(new WhoIsRequest());
		Thread.sleep(timeout);
		return discoveredThings;
	}

	public Object read(String propertyUrl){
		DeviceObjectPropertyIdentifier dopid = bacnetReferenceMap.get(propertyUrl);
		return readPropertyValueAsString(dopid.dev, dopid.oid, dopid.pid);
	}

	public Object update(String propertyUrl, String jsonString){
		DeviceObjectPropertyIdentifier dopid = bacnetReferenceMap.get(propertyUrl);
		return writePropertyValue(dopid.dev, dopid.oid, dopid.pid, jsonString);
	}
	
	public void subscribe(String subscriptionParameter, ServedThing parent, ServedThing sub, Property subProperty){
		JSONObject jsonObj = new JSONObject(subscriptionParameter);
		String propertyUrl = jsonObj.getString("href");
		int lifetime = jsonObj.getInt("lifetime");
		DeviceObjectPropertyIdentifier dopid = bacnetReferenceMap.get(propertyUrl);
		dopid.parentThing = parent;
		dopid.subThing = sub;
		dopid.propertyOnSubThing = subProperty;
		subscribeCOV(dopid.dev, dopid.oid, dopid.pid, lifetime);
	}
	

	class Listener extends DeviceEventAdapter {
		@Override
		public void iAmReceived(final RemoteDevice d) {
			System.out.println("IAm received from " + d);
			System.out.println("Segmentation: " + d.getSegmentationSupported());
			d.setSegmentationSupported(Segmentation.noSegmentation);

			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						getExtendedDeviceInformation(d);
						System.out.println("Done getting extended information");

						@SuppressWarnings("unchecked")
						List<ObjectIdentifier> oids = ((SequenceOf<ObjectIdentifier>) RequestUtils
								.sendReadPropertyAllowNull(localDevice, d, d.getObjectIdentifier(),
										PropertyIdentifier.propertyList.objectList)).getValues();
						System.out.println(oids);
						String deviceName = d.getName();
						
						addThing(d, deviceName, d.getObjectIdentifier());

						for (ObjectIdentifier oid : oids) {
							UnsignedInteger propertyArrayIndex = new UnsignedInteger(0);
							CharacterString objectName = (CharacterString)RequestUtils.readProperty(localDevice, d, oid, PropertyIdentifier.objectName, null);
							addThing(d, objectName.toString(), oid);
						}

					} catch (BACnetException e) {
						e.printStackTrace();
					}
				}
			}).start();
		}
		
        @Override
        public void covNotificationReceived(UnsignedInteger subscriberProcessIdentifier, RemoteDevice initiatingDevice,
                ObjectIdentifier monitoredObjectIdentifier, UnsignedInteger timeRemaining,
                SequenceOf<PropertyValue> listOfValues) {
            System.out.println("Received COV notification: " + listOfValues);
            for(DeviceObjectPropertyIdentifier dopid : bacnetReferenceMap.values()){
            	if(dopid.subThing != null && monitoredObjectIdentifier.equals(dopid.oid)){
            		for(PropertyValue pv : listOfValues){
            			
            			new Thread(new Runnable() {
            				@Override
            				public void run() {
            					try {
                        			if(pv.getPropertyIdentifier().equals(dopid.pid)){
                        				if(dopid.propertyOnSubThing != null){
                        					dopid.propertyOnSubThing.isUnderAsyncUpdate = true;
                        					dopid.subThing.setProperty(dopid.propertyOnSubThing, pv.getValue().toJsonString());
                        				}
                        				if(dopid.parentThing != null){
                        					dopid.propertyOnParentThing.isUnderAsyncUpdate = true;
                        					dopid.parentThing.setProperty(dopid.propertyOnParentThing, pv.getValue().toJsonString());
                        				}
                        			}       						
            					} catch (Exception e) {
            						e.printStackTrace();
            					}
            				}
            			}).start();            			

            		}
            	}
            }
        }		
	}


	private class DeviceObjectPropertyIdentifier{
		public DeviceObjectPropertyIdentifier(RemoteDevice dev, ObjectIdentifier oid, PropertyIdentifier pid, Property thingProperty){
			this.oid = oid;
			this.pid =pid;
			this.dev = dev;
			this.propertyOnParentThing = thingProperty;
		}
		public ObjectIdentifier oid;
		public PropertyIdentifier pid;
		public RemoteDevice dev;
		public ServedThing parentThing;
		public Property propertyOnParentThing;
		public ServedThing subThing;
		public Property propertyOnSubThing;
	}
	
	private Map<String, DeviceObjectPropertyIdentifier> bacnetReferenceMap = new HashMap<>();
	
	private void addThing(RemoteDevice device, String objectName, ObjectIdentifier oid){
		String deviceName = device.getName();

		String uri = device.getName() + "/" + objectName.replace('\'', '/');
		
		List<PropertyTypeDefinition> properties =  ObjectProperties.getPropertyTypeDefinitions(oid.getObjectType());
		
		Thing thing = new Thing(objectName);
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_URIS, uri, uri);
		thing.getMetadata().addContext("BACnet", "http://n.ethz.ch/student/ganeshr/bacnet/bacnettypes.json");
		//thing.getMetadata().add("associations", "{\"href\":\"device\",\"rt\":\"parent\"}", "{\"href\":\"device\",\"rt\":\"parent\"}");
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_ENCODINGS, "JSON", "JSON");

		for(PropertyTypeDefinition prop : properties){			
			PropertyIdentifier pid = prop.getPropertyIdentifier();
			String propertyName = pid.toString();
			boolean isWriteable = true;// ObjectProperties.isCommandable(oid.getObjectType(), pid);
			String typeName = prop.getClazz().getTypeName();
			int lastDot = typeName.lastIndexOf(".");
			typeName =" BACnet:" + typeName.substring(lastDot + 1);
			ArrayList<String> hrefs = new ArrayList<>();
			//hrefs.add(propertyName);
			//hrefs.add(propertyName);
			CustomPropertyDescription pd = new CustomPropertyDescription(propertyName, propertyName, isWriteable, typeName, hrefs, "BACnet:ObjectProperty");
			thing.addProperty(pd);
			String keyName = objectName + "/" + propertyName;
			bacnetReferenceMap.put(keyName, new DeviceObjectPropertyIdentifier(device, oid, pid, pd));
		}		
		//if(oid.getObjectType() == ObjectType.command){
			//Test code for events and actions
			//TODO Remove and replace with automatic generation of events and actions.
			Action action1 = Action.getBuilder("_actionSubscribeCOV").setInputType("BACnet:COVSubscriptionParameters").setOutputType("BACnet:COVMonitor").build();
			thing.addAction(action1);

			Action action2 = Action.getBuilder("_actionExecuteCommand").setInputType("BACnet:CommandParameters").setOutputType("BACnet:CommandMonitor").build();
			thing.addAction(action2);
		//}

		//thing.getMetadata().setAdditionalContexts(bacnetContexts);
		
		discoveredThings.add(thing);
	}
	
    private static void addPropertyReferences(PropertyReferences refs, ObjectIdentifier oid) {
        refs.add(oid, PropertyIdentifier.objectName);

        ObjectType type = oid.getObjectType();
        if (ObjectType.accumulator.equals(type)) {
            refs.add(oid, PropertyIdentifier.units);
        }
        else if (ObjectType.analogInput.equals(type) || ObjectType.analogOutput.equals(type)
                || ObjectType.analogValue.equals(type) || ObjectType.pulseConverter.equals(type)) {
            refs.add(oid, PropertyIdentifier.units);
        }
        else if (ObjectType.binaryInput.equals(type) || ObjectType.binaryOutput.equals(type)
                || ObjectType.binaryValue.equals(type)) {
            refs.add(oid, PropertyIdentifier.inactiveText);
            refs.add(oid, PropertyIdentifier.activeText);
        }
        else if (ObjectType.lifeSafetyPoint.equals(type)) {
            refs.add(oid, PropertyIdentifier.units);
        }
        else if (ObjectType.loop.equals(type)) {
            refs.add(oid, PropertyIdentifier.outputUnits);
        }
        else if (ObjectType.multiStateInput.equals(type) || ObjectType.multiStateOutput.equals(type)
                || ObjectType.multiStateValue.equals(type)) {
            refs.add(oid, PropertyIdentifier.stateText);
        }
        else
            return;

        refs.add(oid, PropertyIdentifier.presentValue);
    }	
	
	private void printObject(ObjectIdentifier oid, PropertyValues pvs) {
		System.out.println(String.format("\t%s", oid));
		for (ObjectPropertyReference opr : pvs) {
			if (oid.equals(opr.getObjectIdentifier())) {
				System.out.println(
						String.format("\t\t%s = %s", opr.getPropertyIdentifier().toString(), pvs.getNoErrorCheck(opr)));
			}
		}
	}
	
	private void subscribeCOV(RemoteDevice d, ObjectIdentifier oid, PropertyIdentifier pid, int lifetime){
        SubscribeCOVRequest req = new SubscribeCOVRequest(new UnsignedInteger(0), oid, new Boolean(true),
                new UnsignedInteger(lifetime));
        localDevice.send(d, req);
	}
	
	private Object readPropertyValueAsString(RemoteDevice d, ObjectIdentifier oid, PropertyIdentifier pid) {
		ServiceFuture sf = localDevice.send(d, new ReadPropertyRequest(oid, pid));

		ReadPropertyAck ack;
		try {
			ack = (ReadPropertyAck) sf.get();
			return ack.getValue().toJsonString();
			
		} catch (BACnetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return e;
		}
		
	}
	
	private Object writePropertyValue(RemoteDevice d, ObjectIdentifier oid, PropertyIdentifier pid, String jsonString) {
		//UnsignedInteger propertyArrayIndex, Encodable propertyValue, UnsignedInteger priority
	
		//ServiceFuture sf = localDevice.send(d, new ReadPropertyRequest(oid, pid));
		//ReadPropertyAck ack;
		try {
			//ack = (ReadPropertyAck) sf.get();
			//Encodable encodable = ack.getValue();
			
			//Encodable incoming = RequestUtils.readProperty(localDevice, d, oid, pid, null);
			//Encodable encodable = incoming.fromJsonString(jsonString);
			Encodable encodable = RequestUtils.getProperty(localDevice, d, oid, pid); new com.serotonin.bacnet4j.type.primitive.Boolean(true);
			encodable.updateFromJson(jsonString);
			RequestUtils.writeProperty(localDevice, d, oid, pid, encodable);
			//sf = localDevice.send(d, new WritePropertyRequest(oid, pid, null, encodable, null));
			//Object wack =  sf.get();
			return null;
			
		} catch (BACnetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return e;
		}
		
	}	

	private void getExtendedDeviceInformation(RemoteDevice d) throws BACnetException {
		ObjectIdentifier oid = d.getObjectIdentifier();

		// Get the device's supported services
		System.out.println("protocolServicesSupported");
		ServiceFuture sf = localDevice.send(d,
				new ReadPropertyRequest(oid, PropertyIdentifier.protocolServicesSupported));

		ReadPropertyAck ack = (ReadPropertyAck) sf.get();
		d.setServicesSupported((ServicesSupported) ack.getValue());

		System.out.println("objectName");
		ack = (ReadPropertyAck) localDevice.send(d, new ReadPropertyRequest(oid, PropertyIdentifier.objectName)).get();
		d.setName(ack.getValue().toString());

		System.out.println("protocolVersion");
		ack = (ReadPropertyAck) localDevice.send(d, new ReadPropertyRequest(oid, PropertyIdentifier.protocolVersion))
				.get();
		d.setProtocolVersion((UnsignedInteger) ack.getValue());

		// System.out.println("protocolRevision");
		// ack = (ReadPropertyAck) localDevice.send(d, new
		// ReadPropertyRequest(oid, PropertyIdentifier.protocolRevision));
		// d.setProtocolRevision((UnsignedInteger) ack.getValue());
	}

}
