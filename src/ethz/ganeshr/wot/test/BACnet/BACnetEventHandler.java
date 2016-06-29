package ethz.ganeshr.wot.test.BACnet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonObject;

import org.json.JSONObject;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.service.confirmed.AcknowledgeAlarmRequest;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.TimeStamp;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Action;
import de.thingweb.thing.HyperMediaLink;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;
import de.thingweb.thing.ThingMetadata;

public class BACnetEventHandler {
	
	private static BACnetChannel channel;
	public static void setChannel(BACnetChannel chnl){
		channel = chnl;
	}
	
	public static void handleSubscriptionRequest(ServedThing thing, Action action, Object inputData) throws BACnetException{
		String subscriptionParameter = (String)inputData;
		JSONObject jsonObj = new JSONObject(subscriptionParameter);
		int instance = jsonObj.getInt("notificationClass");
		int deviceId = jsonObj.getInt("deviceId");
		
		ObjectIdentifier notificationClass = null;
		for(ObjectIdentifier oid : BACnetDiscoveryHandler.notificationClasses){
			if(oid.getInstanceNumber() == instance){
				notificationClass = oid;
				break;
			}
		}
		
		final ObjectIdentifier nc = notificationClass;

		RemoteDevice rd = BACnetDiscoveryHandler.discoveredDevices.get(deviceId);
		
		if(notificationClass == null)
			return;
		
		channel.registerAsEventRecipient(rd, notificationClass);
		

		createEventSubscriptionMonitorThing(rd, nc);
	}

	public static void createEventSubscriptionMonitorThing(RemoteDevice rd,final ObjectIdentifier nc) {
		Integer instance = nc.getInstanceNumber();
		
		Thing subThing = new Thing("_Recipient_NC_" + instance );
		
		String uri = "SubResources/EventMonitors/" + subThing.getName();
		
		subThing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_URIS, uri, uri);
		subThing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_CONTEXT, "BACnet:http://n.ethz.ch/student/ganeshr/bacnet/bacnettypes.json");
		subThing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_ENCODINGS, "JSON");
		subThing.getMetadata().add("@type", "BACnet:NotificationRecipient");
		
		ArrayList<String> hrefs = new ArrayList<>();
		hrefs.add("destination");
		hrefs.add("destination");
		String bacnetHref = String.format("/%d/15/%d/102", rd.getInstanceNumber(), nc.getInstanceNumber()); //Href for Notification class recipient list.
		hrefs.add("/1/15/8/102");
		Property p = new Property("destination", "BACnet:RecipientList", true, false,  null, hrefs);
		String id = String.format("%d_15_%d_102", rd.getInstanceNumber(), nc.getInstanceNumber());
		p.getMetadata().add("@id", id);
		subThing.addProperty(p);
		
		BACnetThingRelationship dopid = new BACnetThingRelationship(rd, nc, PropertyIdentifier.recipientList, subThing, p);
		channel.relationshipMap.put(p.getMetadata().get("@id"), dopid);
		
		subThing.setDeleteCallback((dp)->{
				channel.unRegisterAsEventRecipient(rd, nc);
				channel.reportDeletion(subThing);
			});
		
		channel.reportDiscovery(subThing);
	}	
	
	private static  Map<String, BACnetEventData> eventNotifications = new HashMap<String, BACnetEventData>();
	
	public static void handleAcknowledgementRequest(ServedThing thing, Action action, Object inputData) throws BACnetException{
		/*
		String actionName = action.getName();
		Thing subThing = new Thing("_monitor_" + actionName + "_" + UUID.randomUUID().toString());		
		String uri = "SubResources/ActionMonitors/" + thing.getName()  + "/" + actionName + "/" + subThing.getName();
		
		subThing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_URIS, uri, uri);
		subThing.getMetadata().addContext("BACnet", "http://n.ethz.ch/student/ganeshr/bacnet/bacnettypes.json");
		subThing.getMetadata().getAssociations().add(new HyperMediaLink("parent", thing.getURIs().get(0)));
		subThing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_ENCODINGS, "JSON");
		//(String name, String xsdType, boolean isReadable, boolean isWritable, String propertyType, List<String> hrefs)
		ArrayList<String> hrefs = new ArrayList<>();
		Property statusProperty = new Property("status", "BACnet:AckRequestStatus", true, false, null, hrefs);
		subThing.addProperty(statusProperty);	
		subThing.setDeleteCallback((dp)->{
			channel.reportDeletion(subThing);
			});
		channel.reportDiscovery(subThing);
		action.getMetadata().getAssociations().add(new HyperMediaLink("child", uri));
		*/

		if (action.getInputType().equals("BACnet:EventAckParameters")){
			for(BACnetEventData ed : eventNotifications.values()){
				if(ed.associatedThing.getName().equals(thing.getName())){
					JSONObject jsonObj = new JSONObject(inputData.toString());
					String ackSource = jsonObj.getString("ackSource");
					channel.acknowledgeAlarm(ed, ackSource);
					break;
				}
			}				
		}	
	}
	
	public static void processEventNotification(final UnsignedInteger processIdentifier, final RemoteDevice initiatingDevice,
            final ObjectIdentifier eventObjectIdentifier, final TimeStamp timeStamp,
            final UnsignedInteger notificationClass, final UnsignedInteger priority, final EventType eventType,
            final CharacterString messageText, final NotifyType notifyType,
            final com.serotonin.bacnet4j.type.primitive.Boolean ackRequired, final EventState fromState,
            final EventState toState, final NotificationParameters eventValues) {
        // Override as required
    	System.out.println("Received event notification");
    	System.out.println(String.format("From %s To %s NotifyType %s EventType %s AckReq %s", fromState, toState, notifyType, eventType, ackRequired));
    	if(eventNotifications.containsKey(eventObjectIdentifier.toString())){
    		BACnetEventData existingEventData = eventNotifications.get(eventObjectIdentifier.toString());
    		existingEventData.update(processIdentifier, initiatingDevice, eventObjectIdentifier, timeStamp, notificationClass, priority, eventType, messageText, notifyType, ackRequired, fromState, toState, eventValues);
    		if(toState.intValue() == 0 && (ackRequired == null || !ackRequired.booleanValue())){ //To Normal and no ack required - remove event resource.
    			channel.reportDeletion(existingEventData.associatedThing);
    			eventNotifications.remove(eventObjectIdentifier.toString());
    		}
    	}
    	else if(toState.intValue() > 0 ){        	
        	BACnetEventData data = new BACnetEventData();
        	data.update(processIdentifier, initiatingDevice, eventObjectIdentifier, timeStamp, notificationClass, priority, eventType, messageText, notifyType, ackRequired, fromState, toState, eventValues);
        	
        	Thing thing = createEventThing(data);
        	Thing sourceObjectThing = BACnetDiscoveryHandler.discoveredThings.get(eventObjectIdentifier.toString());
        	thing.getMetadata().getAssociations().add(new HyperMediaLink("source_object", sourceObjectThing.getName()));
        	data.associatedThing = thing;
        	eventNotifications.put(eventObjectIdentifier.toString(), data);
        	channel.reportDiscovery(thing);
        	data.notifyProperties();
        	sourceObjectThing.getMetadata().getAssociations().add(new HyperMediaLink("event_info", thing.getName()));
        	
    	}
    } 

	public static Thing createEventThing(BACnetEventData eventData){
		
		String thingName = eventData.getUniqueName();
		String uri = "SubResources/AlarmEvents/" + thingName;		
		Thing thing = new Thing(thingName);
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_URIS, uri, uri);
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_CONTEXT, "BACnet:http://n.ethz.ch/student/ganeshr/bacnet/bacnettypes.json");
		thing.getMetadata().add("@id", thingName);
		//thing.getMetadata().add("associations", "{\"href\":\"device\",\"rt\":\"parent\"}", "{\"href\":\"device\",\"rt\":\"parent\"}");
		thing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_ENCODINGS, "JSON");
		//Property(String name, String xsdType, boolean isReadable, boolean isWritable, String propertyType, List<String> hrefs)
		
		Property p = new Property("processIdentifier", "BACnet:UnsignedInteger", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("initiatingDevice", "BACnet:CharacterArray", true, false,  "BACnet:Device", new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("eventObjectIdentifier", "BACnet:ObjectIdentifier", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("timeStamp", "BACnet:TimeStamp", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("notificationClass", "BACnet:UnsignedInteger", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("priority", "BACnet:UnsignedInteger", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("eventType", "BACnet:EventType", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("messageText", "BACnet:CharacterString", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("notifyType", "BACnet:NotifyType", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("ackRequired", "BACnet:Boolean", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("fromState", "BACnet:EventState", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("toState", "BACnet:EventState", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		p = new Property("eventValues", "BACnet:NotificationParametersOutOfRange", true, false,  null, new ArrayList<String>());
		thing.addProperty(p);
		
		Action action1 = Action.getBuilder("AcknowledgeAlarm").setInputType("BACnet:EventAckParameters").build();
		//action1.getMetadata().add("@type", "BACnet:AcknowledgeAlarm");
		thing.addAction(action1);
		
		thing.setTag(eventData);
		return thing;
	}	
}
