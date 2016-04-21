package ethz.ganeshr.wot.test;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.thingweb.thing.Property;

public class CustomPropertyDescription extends Property {

    @JsonProperty("location")
    private String location;
    
	public CustomPropertyDescription(String name, String id, Boolean writable, String outputType, List<String> hrefs,
			String propertyType) {
		//String name, String xsdType, boolean isReadable, boolean isWriteable, String propertyType, List<String> hrefs
		super(name, outputType, true, writable, propertyType, hrefs);
		// TODO Auto-generated constructor stub
		location = "Somewhere";
	}

}
