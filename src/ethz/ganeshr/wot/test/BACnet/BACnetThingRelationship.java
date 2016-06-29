package ethz.ganeshr.wot.test.BACnet;

import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;

import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;

public class BACnetThingRelationship{
	public BACnetThingRelationship(RemoteDevice dev, ObjectIdentifier oid, PropertyIdentifier pid, Thing thing, Property thingProperty){
		this.oid = oid;
		this.pid =pid;
		this.dev = dev;
		this.property = thingProperty;
		this.thing = thing;
	}
	public ObjectIdentifier oid;
	public PropertyIdentifier pid;
	public RemoteDevice dev;
	//public ServedThing parentThing;
	public Thing thing;
	public Property property;
	//public ServedThing subThing;
	//public Property propertyOnSubThing;
}