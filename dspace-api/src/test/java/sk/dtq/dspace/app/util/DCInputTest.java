package sk.dtq.dspace.app.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.dspace.AbstractUnitTest;
import org.dspace.app.util.DCInput;
import org.junit.Before;
import org.junit.Test;

public class DCInputTest extends AbstractUnitTest {

    private DCInput dcInput;

    @Before
    @Override
    public void init() {
        // Field map
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("dc-qualifier", "person");
        fieldMap.put("dc-element", "contact");
        fieldMap.put("dc-schema", "local");
        fieldMap.put("repeatable", "true");
        fieldMap.put("hint", "Hint");
        fieldMap.put("complex-definition-ref", "contact_person");
        fieldMap.put("label", "Contact person");
        fieldMap.put("input-type", "complex");
        fieldMap.put("required", "null");

        // Complex definition
        DCInput.ComplexDefinition complexDefinition = new DCInput.ComplexDefinition("contact_person");

        // Complex Definition inputs
        Map<String, String> complexDefinitionInputGivenname = new HashMap<>();
        Map<String, String> complexDefinitionInputSurname = new HashMap<>();

        complexDefinitionInputGivenname.put("name","givenname");
        complexDefinitionInputGivenname.put("input-type","text");
        complexDefinitionInputGivenname.put("label","Given name");
        complexDefinitionInputGivenname.put("required","true");

        complexDefinitionInputSurname.put("name","surname");
        complexDefinitionInputSurname.put("input-type","text");
        complexDefinitionInputSurname.put("label","Surname");
        complexDefinitionInputSurname.put("required","true");

        try {
            complexDefinition.addInput(complexDefinitionInputGivenname);
            complexDefinition.addInput(complexDefinitionInputSurname);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Complex definitions
        DCInput.ComplexDefinitions complexDefinitions = new DCInput.ComplexDefinitions(null);
        complexDefinitions.addDefinition(complexDefinition);

        this.dcInput = new DCInput(fieldMap, null, complexDefinitions);
    }

    @Test
    public void shouldCreateDCInput() {
        assertThat("shouldCreateDCInput 0", this.dcInput, notNullValue());
        assertThat("shouldCreateDCInput 1", this.dcInput.getComplexDefinition(), notNullValue());
    }

    @Test
    public void DCInputShouldReturnComplexDefinitionAsJSONString() {
        String complexDefinitionJSONString = "{\"givenname\":{\"name\":\"givenname\",\"input-type\":" +
                "\"text\",\"label\":\"Given name\",\"required\":\"true\"},\"surname\":{" +
                "\"name\":\"surname\",\"input-type\":\"text\",\"label\":\"Surname\"," +
                "\"required\":\"true\"}}";

        assertThat("DCInputShouldReturnComplexDefinitionAsJSONString 0", this.dcInput.getComplexDefinitionJSONString(),
                equalTo(complexDefinitionJSONString));
    }

}
