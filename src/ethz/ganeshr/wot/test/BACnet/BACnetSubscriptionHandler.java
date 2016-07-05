package ethz.ganeshr.wot.test.BACnet;

import java.util.ArrayList;
import java.util.UUID;

import org.json.JSONObject;

import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Action;
import de.thingweb.thing.HyperMediaLink;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;
import de.thingweb.thing.ThingMetadata;

public class BACnetSubscriptionHandler {
	
	private static BACnetChannel channel;
	public static void setChannel(BACnetChannel chnl){
		channel = chnl;
	}
	
	public static ArrayList<BACnetThingRelationship> subscribers = new ArrayList<>();

	public static String handleSubscriptionRequest(ServedThing thing, Action action, Object inputData) throws BACnetException{
		int deviceId, objectType, objectIntstance;
		
		String subscriptionParameter = (String)inputData;
		JSONObject jsonObjInputData = new JSONObject(subscriptionParameter);
		
		if(action.getDefaultParameters() != null){
			JSONObject jsonObjDefaults = new JSONObject(action.getDefaultParameters());
			JSONObject objectRef = jsonObjDefaults.getJSONObject("objectReference");
			deviceId = objectRef.getInt("deviceIdentifier");
			JSONObject objectId = objectRef.getJSONObject("objectIdentifier");
			objectType = objectId.getInt("objectType");
			objectIntstance = objectId.getInt("instance");			
		}else{
			JSONObject objectRef = jsonObjInputData.getJSONObject("objectReference");
			deviceId = objectRef.getInt("deviceIdentifier");
			JSONObject objectId = objectRef.getJSONObject("objectIdentifier");
			objectType = objectId.getInt("objectType");
			objectIntstance = objectId.getInt("instance");			
		}		
		int lifetime = jsonObjInputData.getInt("lifetime");
		
		RemoteDevice rd = BACnetDiscoveryHandler.discoveredDevices.get(deviceId);
		ObjectIdentifier oid = new ObjectIdentifier(ObjectType.ALL[objectType], objectIntstance);
		
		//BACnetThingRelationship dopid = channel.relationshipMap.get(propertyId);
		
		//String actionName = action.getName();
		String monitoredId = action.getMetadata().get("@id");
		Thing subThing = new Thing("_monitor_" + monitoredId + "_" + UUID.randomUUID().toString());
		
		String uri = "SubResources/COVMonitors/" + monitoredId + "/" + subThing.getName();
		
		subThing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_URIS, uri, uri);
		subThing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_CONTEXT, "BACnet:http://n.ethz.ch/student/ganeshr/bacnet/bacnettypes.json");
		subThing.getMetadata().getAssociations().add(new HyperMediaLink("parent", thing.getURIs().get(0)));
		subThing.getMetadata().add(ThingMetadata.METADATA_ELEMENT_ENCODINGS, "JSON");
		subThing.getMetadata().add("@type", "BACnet:COVNotification");
		//(String name, String xsdType, boolean isReadable, boolean isWritable, String propertyType, List<String> hrefs)
		//ArrayList<String> hrefs = new ArrayList<>();
		Property valueProperty = new Property("value", "BACnet:Real", true, false, true, "BACnet:Monitor", new ArrayList<>());
		valueProperty.isUnderAsyncUpdate = true;
		subThing.addProperty(valueProperty);
		//hrefs = new ArrayList<>();
		Property timestampProperty = new Property("timeStamp", "BACnet:DateTime", true, false, true, null, new ArrayList<>());
		timestampProperty.isUnderAsyncUpdate = true;
		subThing.addProperty(timestampProperty);
		//hrefs = new ArrayList<>();
		Property timeRemainingProperty = new Property("timeRemaining", "BACnet:UnsignedInteger", true, false, true, null, new ArrayList<>());
		timeRemainingProperty.isUnderAsyncUpdate = true;
		subThing.addProperty(timeRemainingProperty);
		
		BACnetThingRelationship subscriber = new BACnetThingRelationship(rd, oid, PropertyIdentifier.presentValue, subThing, valueProperty);
		subscribers.add(subscriber);
		
		final HyperMediaLink childLink = new HyperMediaLink("child", uri, "GET", "application/.td+jsonld");	
		if(action != null){				
			action.getMetadata().getAssociations().add(childLink);
		}
		
		subThing.setDeleteCallback((dp)->{
				if(action != null){
					action.getMetadata().getAssociations().remove(childLink);
				}
				channel.subscribeCOV(rd, oid,  -1);
				channel.reportDeletion(subThing);
				subscribers.remove(subscriber);
			});
		
		//channel.relationshipMap.put(subThing.getName(), subThingRelation);
		channel.reportDiscovery(subThing);
		action.getMetadata().getAssociations().add(new HyperMediaLink("child", uri));
		channel.subscribeCOV(rd, oid,  lifetime);
		
		return uri;
	}
	
	 public static void handleCovNotification(UnsignedInteger subscriberProcessIdentifier, RemoteDevice initiatingDevice,
             ObjectIdentifier monitoredObjectIdentifier, UnsignedInteger timeRemaining,
             SequenceOf<PropertyValue> listOfValues) {
         System.out.println("Received COV notification: " + listOfValues);
         for(BACnetThingRelationship subscriber : subscribers){
         	if(subscriber.thing != null && subscriber.thing.servedThing != null && monitoredObjectIdentifier.equals(subscriber.oid)){
         		for(PropertyValue pv : listOfValues){
         			if(pv.getPropertyIdentifier().equals(subscriber.pid) && pv.getValue() != null){
	         			new Thread(new Runnable() {
	         				@Override
	         				public void run() {
	         					try {	                     			
	                     				ServedThing servedThing = (ServedThing)subscriber.thing.servedThing;
	                     				if(subscriber.thing.getMetadata().contains("@type") && subscriber.thing.getMetadata().get("@type").equals("BACnet:COVNotification")){
	                     					servedThing.setProperty("value", pv.getValue().toJsonString());
	                     					servedThing.setProperty("timeStamp", (new DateTime()).toJsonString());
	                     					servedThing.setProperty("timeRemaining", timeRemaining.toJsonString());	                     				
	                     				}else{
		                     				subscriber.property.isUnderAsyncUpdate = true;
		                     				servedThing.setProperty(subscriber.property, pv.getValue().toJsonString());
		                     				subscriber.property.isUnderAsyncUpdate = false;
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
}
