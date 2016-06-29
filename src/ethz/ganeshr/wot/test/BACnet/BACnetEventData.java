package ethz.ganeshr.wot.test.BACnet;

import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.TimeStamp;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;

public class BACnetEventData {
	private UnsignedInteger processIdentifier;
	private RemoteDevice initiatingDevice;
	private ObjectIdentifier eventObjectIdentifier;
	private TimeStamp timeStamp;
	private UnsignedInteger notificationClass;
	private UnsignedInteger priority;
	private EventType eventType;
	private CharacterString messageText;
	private NotifyType notifyType;
	private com.serotonin.bacnet4j.type.primitive.Boolean ackRequired;
	private EventState fromState;
	private EventState toState;
	private NotificationParameters eventValues;
	
	public Thing associatedThing;
	
	public void update(final UnsignedInteger processIdentifier, final RemoteDevice initiatingDevice,
            final ObjectIdentifier eventObjectIdentifier, final TimeStamp timeStamp,
            final UnsignedInteger notificationClass, final UnsignedInteger priority, final EventType eventType,
            final CharacterString messageText, final NotifyType notifyType,
            final com.serotonin.bacnet4j.type.primitive.Boolean ackRequired, final EventState fromState,
            final EventState toState, final NotificationParameters eventValues){
		
		this.processIdentifier = processIdentifier;
		this.initiatingDevice = initiatingDevice;
		this.eventObjectIdentifier = eventObjectIdentifier;
		this.timeStamp = timeStamp;
		this.notificationClass = notificationClass;
		this.priority = priority;
		this.eventType = eventType;
		this.messageText = messageText;
		this.notifyType = notifyType;
		this.ackRequired = ackRequired;
		this.fromState = fromState;
		this.toState = toState;
		this.eventValues = eventValues;
		notifyProperties();

	}
	
	public void notifyProperties(){
		if(associatedThing != null){
			for(Property p : associatedThing.getProperties())
			{
				p.isUnderAsyncUpdate = true;
				ServedThing servedThing = (ServedThing)associatedThing.servedThing;
				servedThing.setProperty(p,getDataAsJson(p.getName()));
			}
		}
	}
	
	public String getUniqueName(){
		return String.format("%d_%s", initiatingDevice.getInstanceNumber(), eventObjectIdentifier.getIDSTring());
	}
	
	public Object get(String fieldName){
		if(fieldName.equals("messageText"))
			return messageText;
		else if(fieldName.equals("processIdentifier"))
			return processIdentifier;
		else if(fieldName.equals("initiatingDevice"))
			return initiatingDevice;
		else if(fieldName.equals("eventObjectIdentifier"))
			return eventObjectIdentifier;
		else if(fieldName.equals("timeStamp"))
			return timeStamp;
		else if(fieldName.equals("notificationClass"))
			return notificationClass;
		else if(fieldName.equals("priority"))
			return priority;
		else if(fieldName.equals("eventType"))
			return eventType;
		else if(fieldName.equals("notifyType"))
			return notifyType;
		else if(fieldName.equals("ackRequired"))
			return ackRequired;
		else if(fieldName.equals("fromState"))
			return fromState;
		else if(fieldName.equals("toState"))
			return toState;
		else if(fieldName.equals("eventValues"))
			return eventValues;
		else
			return "unknown field";		
	}
	public String getDataAsJson(String fieldName){
		Object o = get(fieldName);
		if(o instanceof Encodable)
			return ((Encodable)o).toJsonString();
		if(o == null)
			return "null";
		else
			return o.toString();
	}
}

