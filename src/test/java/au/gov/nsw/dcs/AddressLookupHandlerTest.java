package au.gov.nsw.dcs;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class AddressLookupHandlerTest {

    @Test
    public void testMissingParam() {
        AddressLookupHandler h = new AddressLookupHandler();
        APIGatewayV2HTTPEvent req = new APIGatewayV2HTTPEvent(); // no params
        APIGatewayV2HTTPResponse resp = h.handleRequest(req, null);
        assertEquals(400, resp.getStatusCode());
        assertTrue(resp.getBody().contains("address"));
    }

    @Test
    public void testLowercaseAddressAccepted() {
        AddressLookupHandler h = new AddressLookupHandler();
        APIGatewayV2HTTPEvent req = new APIGatewayV2HTTPEvent();
        req.setQueryStringParameters(Map.of("address"," 346 panorama avenue bathurst "));
        APIGatewayV2HTTPResponse resp = h.handleRequest(req, null);
        // Can't assert 200 without live HTTP; just ensure not 400.
        assertNotEquals(400, resp.getStatusCode());
    }
}
