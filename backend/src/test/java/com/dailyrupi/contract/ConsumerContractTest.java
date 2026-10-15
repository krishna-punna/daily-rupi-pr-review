package com.dailyrupi.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Checks that the backend still gives each consumer the fields it reads (REQ-API-001).
 *
 * <p>Every file in {@code contracts/consumers/} lists, per endpoint, the fields one consumer reads and
 * their JSON types. Extra fields are fine; a missing, renamed or retyped field fails. Tagged
 * {@code contract}, so plain {@code mvn test} skips it; run it with {@code mvn test -Pcontract-tests}.
 *
 * <p>The fixture uses August 2012, a month no other test touches, so lists and budgets for it always
 * hold this test's own rows.
 */
@Tag("contract")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsumerContractTest {

    private static final String CONTRACTS = "classpath:contracts/consumers/*.json";
    private static final String MONTH = "2012-08";
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @TestFactory
    Stream<DynamicTest> everyConsumerGetsTheFieldsItReads() throws Exception {
        Map<String, String> fixture = createFixture();
        List<DynamicTest> tests = new ArrayList<>();
        for (Resource file : new PathMatchingResourcePatternResolver().getResources(CONTRACTS)) {
            JsonNode contract;
            try (InputStream in = file.getInputStream()) {
                contract = json.readTree(in);
            }
            String consumer = contract.path("consumer").asText(file.getFilename());
            for (JsonNode endpoint : contract.path("endpoints")) {
                String name = consumer + ": " + endpoint.path("name").asText();
                tests.add(DynamicTest.dynamicTest(name, () -> check(endpoint, fixture)));
            }
        }
        assertThat(tests).as("endpoints in " + CONTRACTS).isNotEmpty();
        return tests.stream();
    }

    private void check(JsonNode endpoint, Map<String, String> fixture) throws Exception {
        JsonNode request = endpoint.path("request");
        String method = request.path("method").asText("GET");
        String path = fill(request.path("path").asText(), fixture);

        MockHttpServletRequestBuilder call = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .with(user("contract-check").roles("USER"));
        if (!"GET".equals(method)) {
            call.with(csrf());
        }
        if (request.has("body")) {
            call.contentType(MediaType.APPLICATION_JSON).content(fillBody(request.get("body"), fixture));
        }

        MvcResult result = mvc.perform(call).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as(method + " " + path + " answered " + body)
                .isEqualTo(request.path("status").asInt(200));

        JsonNode response = json.readTree(body);
        Set<String> problems = new LinkedHashSet<>();
        endpoint.path("fields").fields()
                .forEachRemaining(field -> problems.addAll(FieldCheck.check(response, field.getKey(), field.getValue().asText())));
        assertThat(problems).as("contract problems for " + method + " " + path).isEmpty();
    }

    /** Replaces {name} placeholders in a path. */
    private static String fill(String text, Map<String, String> fixture) {
        for (Map.Entry<String, String> entry : fixture.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return text;
    }

    /** In a body, a whole "{name}" string becomes the value itself: a JSON number if it is one. */
    private String fillBody(JsonNode body, Map<String, String> fixture) throws Exception {
        String text = json.writeValueAsString(body);
        for (Map.Entry<String, String> entry : fixture.entrySet()) {
            String value = entry.getValue();
            String literal = NUMBER.matcher(value).matches() ? value : json.writeValueAsString(value);
            text = text.replace("\"{" + entry.getKey() + "}\"", literal);
        }
        return text;
    }

    /** Creates, through the API, every kind of row the contracts need at least one of. */
    private Map<String, String> createFixture() throws Exception {
        String suffix = Long.toString(System.nanoTime() % 1_000_000);
        long category = create("/api/master-data/categories", "Contract Category " + suffix);
        long subCategory = create("/api/master-data/categories/" + category + "/sub-categories", "Contract Sub " + suffix);
        long item = create("/api/master-data/sub-categories/" + subCategory + "/items", "Contract Item " + suffix);
        long paymentMethod = json.readTree(mvc.perform(get("/api/payment-methods").with(user("contract-check").roles("USER")))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();

        // One expense with a note, one without, and one deleted so the sync sees a deletion.
        expense(item, paymentMethod, "250.50", MONTH + "-10T10:00", "\"Contract note\"", null);
        expense(item, paymentMethod, "99", MONTH + "-11T18:30", "null", null);
        long deleted = expense(item, paymentMethod, "10", MONTH + "-12T09:00", "null", UUID.randomUUID().toString());
        expectStatus(delete("/api/expenses/" + deleted), 204);
        expectStatus(json(put("/api/budgets/" + MONTH + "/categories/" + category), "{\"amount\":600}"), 200);

        Map<String, String> fixture = new LinkedHashMap<>();
        fixture.put("month", MONTH);
        fixture.put("nextMonth", "2012-09");
        fixture.put("from", MONTH + "-01");
        fixture.put("since", "2000-01-01T00:00:00");
        fixture.put("categoryId", Long.toString(category));
        fixture.put("itemId", Long.toString(item));
        fixture.put("paymentMethodId", Long.toString(paymentMethod));
        fixture.put("clientId", UUID.randomUUID().toString());
        return fixture;
    }

    private long create(String url, String name) throws Exception {
        return idOf(expectStatus(json(post(url), "{\"name\":\"" + name + "\"}"), 201));
    }

    private long expense(long item, long paymentMethod, String amount, String spentAt, String note, String clientId)
            throws Exception {
        String body = "{\"itemId\":" + item + ",\"paymentMethodId\":" + paymentMethod + ",\"amount\":" + amount
                + ",\"spentAt\":\"" + spentAt + "\",\"note\":" + note
                + (clientId == null ? "" : ",\"clientId\":\"" + clientId + "\"") + "}";
        return idOf(expectStatus(json(post("/api/expenses"), body), 201));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String expectStatus(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request.with(user("contract-check").roles("USER")).with(csrf())).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as("fixture call answered " + body).isEqualTo(status);
        return body;
    }

    private long idOf(String body) throws Exception {
        return json.readTree(body).get("id").asLong();
    }

    /** Walks a field path such as {@code content[].note} and checks every value it reaches. */
    static final class FieldCheck {

        private FieldCheck() {
        }

        static List<String> check(JsonNode root, String path, String type) {
            List<String> problems = new ArrayList<>();
            List<JsonNode> nodes = List.of(root);
            String walked = "";
            for (String token : path.split("\\.")) {
                boolean each = token.endsWith("[]");
                String name = each ? token.substring(0, token.length() - 2) : token;
                walked = walked.isEmpty() ? token : walked + "." + token;
                List<JsonNode> next = new ArrayList<>();
                for (JsonNode node : nodes) {
                    JsonNode value = node;
                    if (!name.isEmpty()) {
                        if (!node.isObject() || !node.has(name)) {
                            problems.add(walked + " is missing");
                            continue;
                        }
                        value = node.get(name);
                    }
                    if (each) {
                        if (!value.isArray()) {
                            problems.add(walked + " should be an array but is " + value.getNodeType());
                            continue;
                        }
                        value.forEach(next::add);
                    } else {
                        next.add(value);
                    }
                }
                if (!problems.isEmpty()) {
                    return problems;
                }
                if (each && next.isEmpty()) {
                    // An empty array would let any field through unchecked.
                    problems.add(walked + " has no elements to check; the fixture must create one");
                    return problems;
                }
                nodes = next;
            }
            for (JsonNode node : nodes) {
                if (!matches(node, type)) {
                    problems.add(path + " should be " + type + " but is " + node.getNodeType() + ": " + node);
                }
            }
            return problems;
        }

        private static boolean matches(JsonNode node, String type) {
            for (String option : type.split("\\|")) {
                boolean ok = switch (option.trim()) {
                    case "null" -> node.isNull();
                    case "string" -> node.isTextual();
                    case "number" -> node.isNumber();
                    case "boolean" -> node.isBoolean();
                    case "array" -> node.isArray();
                    case "object" -> node.isObject();
                    default -> throw new IllegalArgumentException("Unknown contract type: " + option);
                };
                if (ok) {
                    return true;
                }
            }
            return false;
        }
    }
}
