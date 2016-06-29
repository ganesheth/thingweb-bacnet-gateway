package ethz.ganeshr.wot.test.BACnet;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.ServiceFuture;
import com.serotonin.bacnet4j.event.DeviceEventAdapter;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.obj.NotificationClassObject;
import com.serotonin.bacnet4j.obj.ObjectProperties;
import com.serotonin.bacnet4j.obj.PropertyTypeDefinition;
import com.serotonin.bacnet4j.service.acknowledgement.AtomicReadFileAck;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyAck;
import com.serotonin.bacnet4j.service.acknowledgement.ReadRangeAck;
import com.serotonin.bacnet4j.service.confirmed.AcknowledgeAlarmRequest;
import com.serotonin.bacnet4j.service.confirmed.AtomicReadFileRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadRangeRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadRangeRequest.ByPosition;
import com.serotonin.bacnet4j.service.confirmed.SubscribeCOVRequest;
import com.serotonin.bacnet4j.service.confirmed.WritePropertyRequest;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.Destination;
import com.serotonin.bacnet4j.type.constructed.EventTransitionBits;
import com.serotonin.bacnet4j.type.constructed.ObjectPropertyReference;
import com.serotonin.bacnet4j.type.constructed.PropertyReference;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.Recipient;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.ServicesSupported;
import com.serotonin.bacnet4j.type.constructed.TimeStamp;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.enumerated.Segmentation;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.Null;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.SignedInteger;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.PropertyReferences;
import com.serotonin.bacnet4j.util.PropertyValues;
import com.serotonin.bacnet4j.util.RequestUtils;

import de.thingweb.desc.ThingDescriptionParser;
import de.thingweb.servient.ThingInterface;
import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Action;
import de.thingweb.thing.Event;
import de.thingweb.thing.HyperMediaLink;
import de.thingweb.thing.Metadata;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;
import de.thingweb.thing.ThingMetadata;
import ethz.ganeshr.wot.test.ChannelBase;
import ethz.ganeshr.wot.test.ServerMain;
import javafx.util.Pair;

public class BACnetChannel extends ChannelBase {
	private LocalDevice localDevice;
	
	private List<Pair<String, String>> bacnetContexts = new ArrayList<>();
	private ObjectIdentifier localDeviceObjectId;
	protected Map<String, BACnetThingRelationship> relationshipMap = new HashMap<>();
	private BACnetChannelParam channelParam;
	ObjectMapper jsonMapper = new ObjectMapper();
	
	public BACnetChannel(BACnetChannelParam param){
		this.channelParam = param;
	}
	
	public BACnetChannel(){
		channelParam = null;
	}
	

	@Override
	public void open() throws Exception {
		if(channelParam != null){
			String ipaddr = channelParam.getIpAddress();
			int port = channelParam.getPort();
			IpNetwork network = new IpNetworkBuilder().localBindAddress(ipaddr).port(port).build();
			init(network);
		}else{		
			IpNetwork network = new IpNetworkBuilder().build();
			init(network);
		}
	}	

	private void init(IpNetwork network) throws Exception{
		Transport transport = new DefaultTransport(network);
		// transport.setTimeout(15000);
		// transport.setSegTimeout(15000);
		localDevice = new LocalDevice(1234, transport);
		localDevice.initialize();
		localDeviceObjectId = localDevice.getConfiguration().getId();
		BACnetEventHandler.setChannel(this);
		BACnetSubscriptionHandler.setChannel(this);
		BACnetDiscoveryHandler.setChannel(this);
		localDevice.getEventHandler().addListener(new Listener());
		Pair<String,String> context= new Pair<>("BACnet", "http://bacowl.sourceforge.net/2012/bacnet");
		bacnetContexts.add(context);
	}
	
	public void close() {
		localDevice.terminate();
	}

	public void discoverAsync(boolean activeMode) throws Exception {
		BACnetDiscoveryHandler.discoveredThings.clear();
		BACnetDiscoveryHandler.activeMode = activeMode;
		localDevice.sendGlobalBroadcast(new WhoIsRequest());
	}
	
	@Override
	public void discoverFromFile(String filename){
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		BACnetDiscoveryHandler.handleCreateFromTDFile(filename);
	}
	
	public Collection<Thing> discoverSync(long timeout) throws Exception {
		BACnetDiscoveryHandler.discoveredThings.clear();
		localDevice.sendGlobalBroadcast(new WhoIsRequest());
		Thread.sleep(timeout);
		return BACnetDiscoveryHandler.discoveredThings.values();
	}

	@Override
	public Object read(Property property){
		String key = property.getMetadata().get("@id");
		BACnetThingRelationship dopid = relationshipMap.get(key);
        boolean containsMediaType = property.getMetadata().contains("encoding");
        if(containsMediaType)
        	return readPropertyValueAsObject(dopid.dev, dopid.oid, dopid.pid);
        
        Object result = readPropertyValueAsString(dopid.dev, dopid.oid, dopid.pid);
        
        new Thread(new Runnable() {
				@Override
				public void run() {}
        }).start();
        
        if(property.isClientObserving && !property.isSubscribed){
			BACnetThingRelationship subscriber = new BACnetThingRelationship(dopid.dev, dopid.oid, PropertyIdentifier.presentValue, (Thing)property.getTag(), property);
        	BACnetSubscriptionHandler.subscribers.add(subscriber);
			subscribeCOVAsync(dopid.dev, dopid.oid, 10000);
        	property.isSubscribed = true;
        }
        else if(!property.isClientObserving && property.isSubscribed)
        {
			BACnetThingRelationship subscriber = new BACnetThingRelationship(dopid.dev, dopid.oid, PropertyIdentifier.presentValue, (Thing)property.getTag(), property);
        	BACnetSubscriptionHandler.subscribers.remove(subscriber);
        	subscribeCOVAsync(dopid.dev, dopid.oid,  -1);
        	property.isSubscribed = false;
        }
        
		return result;
	}

	@Override
	public Object update(Property property, String jsonString){
		String key = property.getMetadata().get("@id");
		return update(key, jsonString);
	}
	
	public Object update(String id, String jsonString){
	 	BACnetThingRelationship dopid = relationshipMap.get(id);
		return writePropertyValue(dopid.dev, dopid.oid, dopid.pid, jsonString);
	}
	
	//public Thing create(){}
	
	//public void delete(Thing thing){}
	
	@Override
	public void handleAction(ServedThing thing, Action action, Object inputData) throws BACnetException{
		int hrefCount = action.getHrefs().size();
		String serviceType = action.getHrefs().get(hrefCount - 1);
	
		if(serviceType.contains("SubscribeCOV"))
			BACnetSubscriptionHandler.handleSubscriptionRequest(thing, action, inputData);
		if(serviceType.contains("SubscribeEvents"))
			BACnetEventHandler.handleSubscriptionRequest(thing, action, inputData);
		else if(serviceType.contains("Acknowledge"))
			BACnetEventHandler.handleAcknowledgementRequest(thing, action, inputData);
		else
			GenericActionHandler.handleActionRequest(thing, action, inputData, this);		
	}
	

	class Listener extends DeviceEventAdapter {
		@Override
		public void iAmReceived(final RemoteDevice d) {
			System.out.println("IAm received from " + d);
			System.out.println("Segmentation: " + d.getSegmentationSupported());
			d.setSegmentationSupported(Segmentation.noSegmentation);
			BACnetDiscoveryHandler.handleDeviceFound(d, localDevice);
		}
		
        @Override
        public void covNotificationReceived(UnsignedInteger subscriberProcessIdentifier, RemoteDevice initiatingDevice,
                ObjectIdentifier monitoredObjectIdentifier, UnsignedInteger timeRemaining,
                SequenceOf<PropertyValue> listOfValues) {
            System.out.println("Received COV notification: " + listOfValues);
            BACnetSubscriptionHandler.handleCovNotification(subscriberProcessIdentifier, initiatingDevice, monitoredObjectIdentifier, timeRemaining, listOfValues);
        }		
	
        @Override
        public void eventNotificationReceived(final UnsignedInteger processIdentifier, final RemoteDevice initiatingDevice,
                final ObjectIdentifier eventObjectIdentifier, final TimeStamp timeStamp,
                final UnsignedInteger notificationClass, final UnsignedInteger priority, final EventType eventType,
                final CharacterString messageText, final NotifyType notifyType,
                final com.serotonin.bacnet4j.type.primitive.Boolean ackRequired, final EventState fromState,
                final EventState toState, final NotificationParameters eventValues) {
            // Override as required
        	System.out.println("Received event notification");
        	BACnetEventHandler.processEventNotification(processIdentifier, initiatingDevice, eventObjectIdentifier, timeStamp, notificationClass, priority, eventType, messageText, notifyType, ackRequired, fromState, toState, eventValues);
        }        
	}
	
	protected int getRegisteredIndexInRecipientList(final RemoteDevice rd, ObjectIdentifier oid)
			throws BACnetException {
		boolean exists = false;
		int index = 0;
		if(oid.getObjectType().intValue() == ObjectType.notificationClass.intValue()){								
			SequenceOf<Destination> existingRecipients = (SequenceOf<Destination>)readPropertyValueAsObject(rd, oid, PropertyIdentifier.recipientList);
			
			for(Destination d : existingRecipients)
			{
				index++;
				if(d.getRecipient().isDevice()){	
					ObjectIdentifier addrObjId = d.getRecipient().getDevice();
					exists = addrObjId.equals(localDeviceObjectId);
					if(exists)
						break;
				}			
			}			
		}
		return index;
	}		
	protected void registerAsEventRecipient(final RemoteDevice rd, ObjectIdentifier oid)
			throws BACnetException {
		if(oid.getObjectType().intValue() == ObjectType.notificationClass.intValue()){								
			SequenceOf<Destination> existingRecipients = (SequenceOf<Destination>)readPropertyValueAsObject(rd, oid, PropertyIdentifier.recipientList);
			boolean exists = (getRegisteredIndexInRecipientList(rd, oid) > 0);
			if(!exists){
				existingRecipients.add(new Destination(new Recipient(localDeviceObjectId), new UnsignedInteger(10), new Boolean(true),
		            new EventTransitionBits(true, true, true)));
				try {
					RequestUtils.writeProperty(localDevice, rd, oid, PropertyIdentifier.recipientList, existingRecipients);
				} catch (Exception e) {
					//Ignore the exception..
					//TODO find out how to handle
					e.printStackTrace();
				}
				
			}
		}
	}	
	
	protected void unRegisterAsEventRecipient(final RemoteDevice rd, ObjectIdentifier oid){
		if(oid.getObjectType().intValue() == ObjectType.notificationClass.intValue()){								
			SequenceOf<Destination> existingRecipients = (SequenceOf<Destination>)readPropertyValueAsObject(rd, oid, PropertyIdentifier.recipientList);
			boolean exists = false;
			int index = 0;
			try {
				index = getRegisteredIndexInRecipientList(rd, oid);
			} catch (BACnetException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if(index > 0 ){
				existingRecipients.remove(index);
				try {
					RequestUtils.writeProperty(localDevice, rd, oid, PropertyIdentifier.recipientList, existingRecipients);
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
		}
	}	
	

	

/*	
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
    */
	/*
	private void printObject(ObjectIdentifier oid, PropertyValues pvs) {
		System.out.println(String.format("\t%s", oid));
		for (ObjectPropertyReference opr : pvs) {
			if (oid.equals(opr.getObjectIdentifier())) {
				System.out.println(
						String.format("\t\t%s = %s", opr.getPropertyIdentifier().toString(), pvs.getNoErrorCheck(opr)));
			}
		}
	}
	*/
	
	protected void subscribeCOVAsync(RemoteDevice d, ObjectIdentifier oid, int lifetime){
		new Thread(){
			@Override
			public void run() {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}				
				subscribeCOV(d, oid, lifetime);
			};
		}.start();		
	}
	
	protected void subscribeCOV(RemoteDevice d, ObjectIdentifier oid, int lifetime){
        SubscribeCOVRequest req = new SubscribeCOVRequest(new UnsignedInteger(0), oid, lifetime > -1 ? new Boolean(true) : null,
                lifetime > -1 ? new UnsignedInteger(lifetime) : null);
        localDevice.send(d, req);
	}
	
	public void acknowledgeAlarm(BACnetEventData eventData, String ackSource) throws BACnetException{
        TimeStamp now = new TimeStamp(new DateTime());
        AcknowledgeAlarmRequest req = new AcknowledgeAlarmRequest( //
                (UnsignedInteger) eventData.get("processIdentifier"), //
                (ObjectIdentifier) eventData.get("eventObjectIdentifier"), //
                (EventState) eventData.get("toState"), //
                (TimeStamp) eventData.get("timeStamp"), //
                new CharacterString(ackSource), //
                now);
        localDevice.send((RemoteDevice) eventData.get("initiatingDevice"), req).get();
	}
	
	private Object readPropertyValueAsString(RemoteDevice d, ObjectIdentifier oid, PropertyIdentifier pid) {
		Object value = readPropertyValueAsObject(d,oid,pid);
		if(value instanceof Encodable)
			return ((Encodable)value).toJsonString();
		if(value instanceof byte[]){
			JSONArray jsonArray = new JSONArray();
			for(byte b : (byte[])value){
				jsonArray.put(b);
			}
			return jsonArray.toString();
		}
		else
			return value.toString();
		
	}
	
	private Object readPropertyValueAsObject(RemoteDevice d, ObjectIdentifier oid, PropertyIdentifier pid) {
		
		if(oid.getObjectType().intValue() == ObjectType.trendLog.intValue() && pid.intValue() == PropertyIdentifier.logBuffer.intValue())
			return readRange(d, oid, pid);
		
		if(oid.getObjectType().intValue() == ObjectType.file.intValue())
			return readFile(d, oid, pid);
		
		ServiceFuture sf = localDevice.send(d, new ReadPropertyRequest(oid, pid));

		ReadPropertyAck ack;
		try {
			ack = (ReadPropertyAck) sf.get();
			return ack.getValue();
			
		} catch (BACnetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return e;
		}		
	}
	
	private Object readRange(RemoteDevice d, ObjectIdentifier oid, PropertyIdentifier pid){
		ReadRangeRequest readRangeRequest = new ReadRangeRequest(oid, pid, null, new ByPosition(new UnsignedInteger(0), new SignedInteger(10)));
		ServiceFuture sf = localDevice.send(d, readRangeRequest);
		ReadRangeAck ack;
		try {
			ack = (ReadRangeAck) sf.get();
			return ack.getItemData();			
		} catch (BACnetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return e;
		}
	}
	
	private Object readFile(RemoteDevice fileDev, ObjectIdentifier file, PropertyIdentifier pid){
		try{
			if(pid.intValue() == 1){
				ClassLoader classLoader = BACnetChannel.class.getClassLoader();
				URL fileURL = classLoader.getResource("dxr.jpg");	
				File fi = new File(fileURL.getPath());
				byte[] fileContent = Files.readAllBytes(fi.toPath());
				return fileContent;
			}
			
	        AtomicReadFileRequest request = new AtomicReadFileRequest(file, false, 0, 19973);
	        ServiceFuture sf = localDevice.send(fileDev, request);
	        if(sf.get() instanceof AtomicReadFileAck){
		        AtomicReadFileAck response = (AtomicReadFileAck) sf.get();
		
		        System.out.println("eof: " + response.getEndOfFile());
		        System.out.println("start: " + response.getFileStartPosition());
		        System.out.println("data: " + new String(response.getFileData().getBytes()));
		        System.out.println("length: " + response.getFileData().getBytes().length);
		        String str = new String(response.getFileData().getBytes());
		        return response.getFileData().getBytes();
	        }
	        return "Could not read";
        }
		catch (Exception e){
        	return e;
        }
	}
	
	public Object writePropertyValue(RemoteDevice d, ObjectIdentifier oid, PropertyIdentifier pid, String jsonString) {
		try {
			JsonNode node = jsonMapper.readTree(jsonString);
			String encodableValue = jsonString;
			UnsignedInteger priority = null, index = null;
			if(node.has("value"))
				encodableValue = node.get("value").toString();
			if(node.has("priority"))
				priority = new UnsignedInteger(node.get("priority").asInt());
			if(node.has("index"))
				index = new UnsignedInteger(node.get("index").asInt());
			
			Encodable encodable = null;
			if(encodableValue == "null"){
				encodable = new Null();
			}else{
				encodable = RequestUtils.readProperty(localDevice, d, oid, pid, null);		
				encodable.updateFromJson(encodableValue);
			}
			
			PropertyValue pv = new PropertyValue(pid, index, encodable, priority);
			
			RequestUtils.writeProperty(localDevice, d, oid, pv);
			//RequestUtils.writeProperty(localDevice, d, oid, pid, encodable);
			return null;
			
		} catch (BACnetException | IOException e) {
			e.printStackTrace();
			RuntimeException rte = new RuntimeException(e.getMessage());
			if(e.getMessage().startsWith("Timeout"))
				return null;
			return rte;
		} 
		
	}	

	protected void getExtendedDeviceInformation(RemoteDevice d) throws BACnetException {
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
