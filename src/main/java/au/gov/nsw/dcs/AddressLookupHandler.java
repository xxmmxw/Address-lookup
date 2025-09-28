package au.gov.nsw.dcs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.net.http.*;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
/**
 * AWS Lambda handler for NSW address lookup.
 * Runtime: Java 17
 * Handler: au.gov.nsw.dcs.AddressLookupHandler::handleRequest
 */
public class AddressLookupHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final String KEY_ERROR = "error";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper M = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);


    private static final String GEO_ADDR =
            "https://portal.spatial.nsw.gov.au/server/rest/services/NSW_Geocoded_Addressing_Theme/FeatureServer/1/query";
    private static final String SUBURB =
            "https://portal.spatial.nsw.gov.au/server/rest/services/NSW_Administrative_Boundaries_Theme/FeatureServer/2/query";
    private static final String SED =
            "https://portal.spatial.nsw.gov.au/server/rest/services/NSW_Administrative_Boundaries_Theme/FeatureServer/4/query";

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            Map<String, String> q = Optional.ofNullable(event.getQueryStringParameters()).orElse(Map.of());
            String address = q.get("address");

            if (address == null || address.isBlank()) {
                return json(400, Map.of(KEY_ERROR, "Missing required query parameter: address"));
            }

            // 1) Geocode address to lon/lat + IDs
            String normalized = address.toUpperCase().trim().replace("'", "''");
            Map<String, String> p = new LinkedHashMap<>();
            p.put("where", "address = '" + normalized + "'");
            p.put("outFields", "*");
            p.put("f", "geojson");

            JsonNode geo = getJson(GEO_ADDR, p);
            var features = geo.path("features");
            if (!features.isArray() || features.isEmpty()) {
                return json(404, Map.of(KEY_ERROR, "Address not found"));
            }
            JsonNode f0 = features.get(0);
            JsonNode coords = f0.path("geometry").path("coordinates");
            double lon = coords.get(0).asDouble();
            double lat = coords.get(1).asDouble();
            JsonNode props = f0.path("properties");
            Long gurasid = safeLong(props, "gurasid");
            Long pasoid = safeLong(props, "principaladdresssiteoid");

            // 2) Point-in-polygon lookups
            String suburbName = queryPoint(SUBURB, lat, lon).path("suburbname").asText(null);
            String districtName = queryPoint(SED, lat, lon).path("districtname").asText(null);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("address", normalized);
            body.put("location", Map.of("lat", lat, "lon", lon));
            body.put("suburb", suburbName);
            body.put("state_electoral_district", districtName);
            Map<String, Object> ids = new LinkedHashMap<>();
            if (gurasid != null) ids.put("gurasid", gurasid);
            if (pasoid != null) ids.put("principaladdresssiteoid", pasoid);
            body.put("identifiers", ids);
            body.put("source", "NSW Spatial Services");

            return json(200, body);

        } catch (java.net.http.HttpTimeoutException e) {
            return json(504, Map.of(KEY_ERROR, "Upstream timeout"));
        } catch (Exception e) {
            return json(502, Map.of(KEY_ERROR, "Upstream or parse error: " + e.getClass().getSimpleName()));
        }
    }

    private static Long safeLong(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? null : f.asLong();
    }

    private static JsonNode queryPoint(String layerUrl, double lat, double lon) throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("geometry", lon + "," + lat);
        p.put("geometryType", "esriGeometryPoint");
        p.put("inSR", "4326");
        p.put("spatialRel", "esriSpatialRelIntersects");
        p.put("outFields", "*");
        p.put("returnGeometry", "false");
        p.put("f", "geoJSON");
        JsonNode node = getJson(layerUrl, p);
        JsonNode feats = node.path("features");
        return (feats.isArray() && !feats.isEmpty()) ? feats.get(0).path("properties") : M.createObjectNode();
    }

    private static JsonNode getJson(String base, Map<String, String> params)
            throws IOException, InterruptedException {
        String qs = encode(params);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "?" + qs))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "nsw-addr-lookup-java/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            String body = resp.body();
            String snippet = (body == null) ? "" :
                    (body.length() > 256 ? body.substring(0, 256) + "..." : body);
            throw new IOException("HTTP " + resp.statusCode() + " from " + base + " " + snippet);
        }

        return M.readTree(resp.body());
    }






    private static String encode(Map<String,String> p) {
        StringBuilder sb = new StringBuilder();
        for (var e : p.entrySet()) {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(url(e.getKey())).append('=').append(url(e.getValue()));
        }
        return sb.toString();
    }
    private static String url(String s) {
        return URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static APIGatewayV2HTTPResponse json(int status, Map<String,?> body) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(status)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(M.writeValueAsString(body))
                    .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody("{\"error\":\"serialization\"}")
                    .build();
        }
    }
}
