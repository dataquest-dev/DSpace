package org.dspace.authenticate;

import org.dspace.AbstractUnitTest;
import org.dspace.authenticate.clarin.ShibHeaders;
import org.junit.Test;

import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class ShibHeadersTest extends AbstractUnitTest {

    @Test
    public void testParsingStringHeaders() {
        String shibHeadersString = "shib-netid=123456\nshib-identity-provider=Test Idp\n" +
        "x-csrf-token=f06905b1-3458-4c3c-bd91-78e97fe7b2e1";

        ShibHeaders shibHeaders = new ShibHeaders(shibHeadersString);
        assertEquals(Objects.nonNull(shibHeaders), true);
    }

}
