package ethz.ganeshr.wot.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.ObjectPropertyReference;
import com.serotonin.bacnet4j.type.constructed.PropertyReference;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.ServicesSupported;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.enumerated.Segmentation;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.PropertyReferences;
import com.serotonin.bacnet4j.util.PropertyValues;
import com.serotonin.bacnet4j.util.RequestUtils;

import de.thingweb.desc.pojo.InteractionDescription;
import de.thingweb.desc.pojo.Metadata;
import de.thingweb.desc.pojo.PropertyDescription;
import de.thingweb.desc.pojo.Protocol;
import de.thingweb.desc.pojo.ThingDescription;
import de.thingweb.thing.Property;

public class BACnetChannel {
	private LocalDevice localDevice;
	ThingDescription rootThing = null;
	List<ThingDescription> discoveredThings = new ArrayList<ThingDescription>();

	public void open() throws Exception {
		IpNetwork network = new IpNetworkBuilder().build();
		Transport transport = new DefaultTransport(network);
		// transport.setTimeout(15000);
		// transport.setSegTimeout(15000);
		localDevice = new LocalDevice(1234, transport);
		localDevice.initialize();
		localDevice.getEventHandler().addListener(new Listener());
	}

	public void close() {

	}

	public List<ThingDescription> discover(long timeout, ThingDescription root) throws Exception {
		rootThing = root;
		discoveredThings.clear();
		localDevice.sendGlobalBroadcast(new WhoIsRequest());
		Thread.sleep(timeout);
		return discoveredThings;
	}

	public void create(ThingDescription thing) {

	}

	public String read(PropertyDescription property) {
		DeviceObjectPropertyIdentifier dopid = bacnetReferenceMap.get(property);
		return readPropertyValueAsString(dopid.dev, dopid.oid, dopid.pid);
	}

	public void update(ThingDescription thing) {

	}

	public void delete(ThingDescription thing) {

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

						//PropertyReferences refs = new PropertyReferences();
						//refs.add(d.getObjectIdentifier(), PropertyIdentifier.all);
						//ObjectIdentifier ai = null;
						

						for (ObjectIdentifier oid : oids) {
							UnsignedInteger propertyArrayIndex = new UnsignedInteger(0);
							CharacterString objectName = (CharacterString)RequestUtils.readProperty(localDevice, d, oid, PropertyIdentifier.objectName, null);
							addThing(d, objectName.toString(), oid);
							//refs.add(oid, PropertyIdentifier.all);
							//if (oid.getObjectType().equals(ObjectType.analogInput))
							//	ai = oid;
						}

						//System.out.println("Start read properties");
						//final long start = System.currentTimeMillis();

						//Encodable pvs = RequestUtils.readProperty(localDevice, d, ai, PropertyIdentifier.presentValue,
						//		null);
						//System.out.println(
						//		String.format("Properties read done in %d ms", System.currentTimeMillis() - start));

					} catch (BACnetException e) {
						e.printStackTrace();
					}
				}
			}).start();
		}
	}


	private class DeviceObjectPropertyIdentifier{
		public DeviceObjectPropertyIdentifier(RemoteDevice dev, ObjectIdentifier oid, PropertyIdentifier pid){
			this.oid = oid;
			this.pid =pid;
			this.dev = dev;
		}
		public ObjectIdentifier oid;
		public PropertyIdentifier pid;
		public RemoteDevice dev;
	}
	
	private Map<PropertyDescription, DeviceObjectPropertyIdentifier> bacnetReferenceMap = new HashMap<>();
	
	private void addThing(RemoteDevice device, String objectName, ObjectIdentifier oid){
		String deviceName = device.getName();
		ArrayList<Protocol> protocols = new ArrayList<Protocol>();
		Metadata rootMetadata =  rootThing.getMetadata();
		Map<String, Protocol> rootProtocols = rootMetadata.getProtocols();
		Map<String, Protocol> childProtocols = new HashMap<String, Protocol>();
		for(String key :rootProtocols.keySet()){
			Protocol root = rootProtocols.get(key);
			String uri = root.uri + deviceName + "/" + objectName;
			Protocol protocol = new Protocol(uri, root.priority);
			childProtocols.put(key, protocol);
		}
		Metadata meta = new Metadata(objectName, childProtocols, rootMetadata.getEncodings());
		
		List<PropertyTypeDefinition> properties =  ObjectProperties.getRequiredPropertyTypeDefinitions(oid.getObjectType());
		
		List<InteractionDescription> interactions = new ArrayList<InteractionDescription>();

		for(PropertyTypeDefinition prop : properties){			
			PropertyIdentifier pid = prop.getPropertyIdentifier();
			String propertyName = pid.toString();
			boolean isWriteable = ObjectProperties.isCommandable(oid.getObjectType(), pid);
			String typeName = prop.getClazz().getSimpleName();
			PropertyDescription pd = new PropertyDescription(propertyName, isWriteable, typeName);
			interactions.add(pd);
			bacnetReferenceMap.put(pd, new DeviceObjectPropertyIdentifier(device, oid, pid));
		}		
		
		ThingDescription thing = new ThingDescription(meta, interactions);
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
	
	private String readPropertyValueAsString(RemoteDevice d, ObjectIdentifier oid, PropertyIdentifier pid){
		ServiceFuture sf = localDevice.send(d, new ReadPropertyRequest(oid, pid));

		ReadPropertyAck ack;
		try {
			ack = (ReadPropertyAck) sf.get();
			return ack.getValue().toString();
		} catch (BACnetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "Error";
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
