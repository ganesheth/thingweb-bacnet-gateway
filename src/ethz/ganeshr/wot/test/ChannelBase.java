package ethz.ganeshr.wot.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import de.thingweb.servient.impl.ServedThing;
import de.thingweb.thing.Action;
import de.thingweb.thing.Content;
import de.thingweb.thing.Property;
import de.thingweb.thing.Thing;


public abstract class ChannelBase {

	private Consumer<Object> m_thingFoundCallback;
	private Consumer<Object> m_thingDeletedCallback;
	
	public void open() throws Exception
	{}
	
	public void close() throws Exception
	{}
	
	public  void open(Object parameter) throws Exception
	{}
	
	public abstract void discoverAsync(boolean activeMode) throws Exception;
	
	public void discoverFromFile(String filename){
		
	}
	
	public abstract Object read(Property property);
	
	public abstract Object update(Property property, String value);
	
	public abstract Content handleAction(ServedThing thing, Action action, Object inputData) throws Exception;
	
	public void addThingFoundCallback(Consumer<Object> thingFoundCallback){
		m_thingFoundCallback = thingFoundCallback;
	}
	
	public void addThingDeletedCallback(Consumer<Object> thingDeletedCallback){
		m_thingDeletedCallback = thingDeletedCallback;
	}
	
	public void reportDiscovery(List<Thing> thingsFound){
		if(m_thingFoundCallback != null)
			m_thingFoundCallback.accept(thingsFound);
	}
	
	public void reportDiscovery(Thing thingFound){
		ArrayList<Thing> things = new ArrayList<>();
		things.add(thingFound);
		reportDiscovery(things);
	}
	
	public void reportDeletion(Thing thingDeleted){
		if(m_thingDeletedCallback != null)
			m_thingDeletedCallback.accept(thingDeleted);
	}
}
