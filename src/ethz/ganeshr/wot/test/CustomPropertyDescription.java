package ethz.ganeshr.wot.test;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.thingweb.desc.pojo.PropertyDescription;

public class CustomPropertyDescription extends PropertyDescription {

    @JsonProperty("location")
    private String location;
    
	public CustomPropertyDescription(String name, String id, Boolean writable, String outputType, List<String> hrefs,
			String propertyType) {
		super(name, id, writable, outputType, hrefs, propertyType, null);
		// TODO Auto-generated constructor stub
		location = "Somewhere";
	}

}
