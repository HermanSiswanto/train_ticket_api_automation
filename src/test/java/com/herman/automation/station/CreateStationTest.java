package com.herman.automation.station;

import static com.herman.automation.utils.TestDataGenerator.generateCity;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueLowercaseStationCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationName;
import static io.restassured.RestAssured.given;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import com.herman.automation.base.BaseTest;
import com.herman.automation.model.StationData;
import com.herman.automation.utils.AuthUtils;
import com.herman.automation.utils.DatabaseUtils;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Epic("Train Ticket API")
@Feature("Station API")
@Story("Create Station")
@DisplayName("Create Station Test")
public class CreateStationTest extends BaseTest {
        private static final String STATIONS_ENDPOINT = "/api/stations";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;

        private static final String ACTIVE_STATUS = "ACTIVE";
        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
        private static final String ACCESS_DENIED_MESSAGE = "Access denied";

        private String adminToken;
        private String userToken;

        private record StationRequest(
                        String stationCode,
                        String stationName,
                        String city) {
        }

        @BeforeEach
        void setup() {
                adminToken = AuthUtils.loginAsAdmin();
                userToken = AuthUtils.loginAsUser();
        }

        private RequestSpecification requestAsAdmin() {
                return given()
                                .auth()
                                .oauth2(adminToken)
                                .contentType(ContentType.JSON)
                                .accept(ContentType.JSON);
        }

        private RequestSpecification requestAsUser() {
                return given()
                                .auth()
                                .oauth2(userToken)
                                .contentType(ContentType.JSON)
                                .accept(ContentType.JSON);
        }

        private Response createStation(RequestSpecification requestSpec, StationRequest request) {
                return Allure.step(
                                "POST " + STATIONS_ENDPOINT,
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .body(request)
                                                .when()
                                                .post(STATIONS_ENDPOINT)
                                                .then()
                                                .extract()
                                                .response());
        }

        @Step("Prepare valid station data")
        private StationRequest validStation() {
                return new StationRequest(
                                generateUniqueStationCode(),
                                generateUniqueStationName(),
                                generateCity());
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertCreatedStationSuccessfulResponse(
                        Response response,
                        StationRequest expected) {
                Allure.step(
                                "Verify station creation response",
                                () -> {

                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(201)
                                                        .body("id", notNullValue())
                                                        .body("stationCode", equalTo(expected.stationCode()))
                                                        .body("stationName", equalTo(expected.stationName()))
                                                        .body("city", equalTo(expected.city()))
                                                        .body("status", equalTo(ACTIVE_STATUS))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        assertResponseTime(response);
                                });
        }

        private void assertStationPersistedInDatabase(
                        StationRequest expected) {

                Allure.step(
                                "Verify station is persisted in the database",
                                () -> {

                                        StationData databaseStation = DatabaseUtils
                                                        .getStationByCode(expected.stationCode());

                                        assertNotNull(
                                                        databaseStation,
                                                        "Created station should be persisted in the database");

                                        assertAll(
                                                        () -> assertEquals(
                                                                        expected.stationCode(),
                                                                        databaseStation.getStationCode()),
                                                        () -> assertEquals(
                                                                        expected.stationName(),
                                                                        databaseStation.getStationName()),
                                                        () -> assertEquals(
                                                                        expected.city(),
                                                                        databaseStation.getCity()),
                                                        () -> assertEquals(
                                                                        ACTIVE_STATUS,
                                                                        databaseStation.getStatus()));
                                });
        }

        private void verifyValidationErrors(
                        Response response,
                        Map<String, String> expectedErrors) {

                response.then()
                                .log().ifValidationFails()
                                .statusCode(400);

                expectedErrors.forEach((field, message) -> assertEquals(
                                message,
                                response.path(field),
                                "Validation message mismatch for field: " + field));

                assertResponseTime(response);
        }

        private void assertValidationError(
                        Response response,
                        String field,
                        String expectedMessage) {

                Allure.step(
                                "Verify validation error for field: " + field,
                                () -> verifyValidationErrors(
                                                response,
                                                Map.of(field, expectedMessage)));
        }

        private void assertAllRequiredStationFieldsValidationErrors(Response response) {
                Allure.step(
                                "Verify validation errors for all required fields",
                                () -> verifyValidationErrors(
                                                response,
                                                Map.of(
                                                                "stationCode", "Station code is required",
                                                                "stationName", "Station name is required",
                                                                "city", "City is required")

                                ));
        }

        private void assertErrorResponse(
                        Response response,
                        int expectedStatusCode,
                        String expectedMessage) {
                Allure.step(
                                "Verify error response: HTTP %d - %s"
                                                .formatted(expectedStatusCode, expectedMessage),
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(expectedStatusCode)
                                                        .body("message", equalTo(expectedMessage));

                                        assertResponseTime(response);
                                });
        }

        private static Stream<Arguments> invalidStationCodeRequests() {
                String invalidMessage = "Station code may only contain letters without spaces";

                return Stream.of(

                                Arguments.of("ABC123", invalidMessage),
                                Arguments.of("ABC 123", invalidMessage),
                                Arguments.of("ABC@DEF", invalidMessage)
                );
        }

        @Test
        @DisplayName("Create Station - Succeeds with valid data and is persisted in the database")
        void createStationShouldSucceedAndPersistData() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationRequest request = validStation();

                Response response = createStation(adminRequest, request);
        
                assertCreatedStationSuccessfulResponse(response, request);
                assertStationPersistedInDatabase(request);
        }

        @Test
        @DisplayName("Create Station - Return 403 (Forbidden) when requested by Regular User")
        void createStationShouldReturnForbiddenWhenRequestedByUser() {
                RequestSpecification userRequest = requestAsUser();
                Response response = createStation(userRequest, validStation());

                assertErrorResponse(response, 403, ACCESS_DENIED_MESSAGE);
        }

        @Test
        @DisplayName("Create Station - Convert station code to uppercase")
        void createStationShouldConvertStationCodeToUppercase() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationRequest request = new StationRequest(
                                generateUniqueLowercaseStationCode(),
                                generateUniqueStationName(),
                                generateCity());

                Response response = createStation(adminRequest, request);

                StationRequest expectedStation = new StationRequest(
                                request.stationCode().toUpperCase(),
                                request.stationName(),
                                request.city());

                assertCreatedStationSuccessfulResponse(response, expectedStation);
                assertStationPersistedInDatabase(expectedStation);
        }

        @Test
        @DisplayName("Create Station - Trim leading and trailing spaces from station name")
        void createStationShouldTrimStationName() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationRequest request = new StationRequest(generateUniqueStationCode(),
                                " " + generateUniqueStationName() +
                                                " ",
                                generateCity());

                Response response = createStation(adminRequest, request);

                StationRequest expected = new StationRequest(
                                request.stationCode(),
                                request.stationName().trim(),
                                request.city());

                assertCreatedStationSuccessfulResponse(response, expected);
                assertStationPersistedInDatabase(expected);

        }

        @Test
        @DisplayName("Create Station - Trim leading and trailing spaces from station city")
        void createStationShouldTrimCity() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationRequest request = new StationRequest(
                                generateUniqueStationCode(),
                                generateUniqueStationName(),
                                "  " + generateCity() + "  ");

                Response response = createStation(adminRequest, request);

                StationRequest expected = new StationRequest(
                                request.stationCode(),
                                request.stationName(),
                                request.city().trim());

                assertCreatedStationSuccessfulResponse(response, expected);
                assertStationPersistedInDatabase(expected);
        }

        @Test
        @DisplayName("Create Station - Return 400 (Bad Request) when station code is empty")
        void createStationShouldReturnBadRequestWhenStationCodeIsEmpty() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationRequest request = new StationRequest(
                                "", 
                                generateUniqueStationName(), 
                                generateCity());

                Response response = createStation(adminRequest, request);

                assertValidationError(
                                response,
                                "stationCode",
                                "Station code is required");
        }

        @Test
        @DisplayName("Create Station - Return 400 (Bad Request) when station name is empty")
        void createStationShouldReturnBadRequestWhenStationNameIsEmpty() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationRequest request = new StationRequest(
                                generateUniqueStationCode(), 
                                "", 
                                generateCity());

                Response response = createStation(adminRequest, request);

                assertValidationError(
                                response,
                                "stationName",
                                "Station name is required");
        }

        @Test
        @DisplayName("Create Station - Return 400 (Bad Request) when station city is empty")
        void createStationShouldReturnBadRequestWhenCityIsEmpty() {
                StationRequest request = new StationRequest(generateUniqueStationCode(), generateUniqueStationName(),
                                "");

                Response response = createStation(requestAsAdmin(), request);

                assertValidationError(
                                response,
                                "city",
                                "City is required");
        }

        @Test
        @DisplayName("Create Station - Return 400 (Bad Request) when all required fields are empty")
        void createStationShouldReturnBadRequestWhenAllFieldsAreEmpty() {
                StationRequest request = new StationRequest("", "", "");

                Response response = createStation(requestAsAdmin(), request);

                assertAllRequiredStationFieldsValidationErrors(response);
        }

        @ParameterizedTest(name = "Create station should fail with invalid code: {0}")
        @MethodSource("invalidStationCodeRequests")
        @DisplayName("Create Station - Return 400 (Bad Request) when requested with invalid station code")
        void createStationShouldReturnBadRequestForInvalidStationCode(
                        String invalidStationCode,
                        String expectedMessage) {

                StationRequest request = new StationRequest(
                                invalidStationCode,
                                generateUniqueStationName(),
                                generateCity());

                Response response = createStation(requestAsAdmin(), request);

                assertValidationError(response, "stationCode", expectedMessage);
        }

        @Test
        @DisplayName("Create Station - Return 400 (Bad Request) when station code exceeds maximum length")
        void createStationShouldReturnBadRequestWhenStationCodeExceedsMaximumLength() {
                StationRequest request = new StationRequest(
                                "A".repeat(11),
                                generateUniqueStationName(),
                                generateCity());

                Response response = createStation(requestAsAdmin(), request);

                assertValidationError(
                                response,
                                "stationCode",
                                "Station code must not exceed 10 characters");
        }

        @Test
        @DisplayName("Create Station - Return 400 (Bad Request) when station name exceeds maximum length")
        void createStationShouldReturnBadRequestWhenStationNameExceedsMaximumLength() {
                StationRequest request = new StationRequest(
                                generateUniqueStationCode(),
                                "B".repeat(101),
                                generateCity());

                Response response = createStation(requestAsAdmin(), request);

                assertValidationError(
                                response,
                                "stationName",
                                "Station name must not exceed 100 characters");
        }

        @Test
        @DisplayName("Create Station - Return 400 (Bad Request) when station city exceeds maximum length")
        void createStationShouldReturnBadRequestWhenStationCityExceedsMaximumLength() {
                StationRequest request = new StationRequest(
                                generateUniqueStationCode(),
                                generateUniqueStationName(),
                                "B".repeat(101));

                Response response = createStation(requestAsAdmin(), request);

                assertValidationError(
                                response,
                                "city",
                                "City must not exceed 100 characters");
        }

        @Test
        @DisplayName("Create Station - Return 409 (Conflict) when station code already exists")
        void createStationShouldReturnBadRequestWhenStationCodeAlreadyExists() {
                String duplicateStationCode = generateUniqueStationCode();

                StationRequest existingStation = new StationRequest(
                                duplicateStationCode,
                                generateUniqueStationName(),
                                generateCity());

                StationRequest duplicateStation = new StationRequest(
                                duplicateStationCode,
                                generateUniqueStationCode(),
                                generateCity());

                Response createResponse = createStation(requestAsAdmin(), existingStation);
                assertCreatedStationSuccessfulResponse(createResponse, existingStation);

                Response duplicateResponse = createStation(requestAsAdmin(), duplicateStation);

                duplicateResponse.then()
                                .log().ifValidationFails()
                                .statusCode(409)
                                .body("message", equalTo("Station code already exists"));

                assertResponseTime(duplicateResponse);
        }

        @Test
        @DisplayName("Create Station - Return 401 (Unauthorized) when requested without authentication")
        void createStationShouldReturnUnauthorizedWithoutToken() {
                Response response = createStation(
                                given()
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                validStation());

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
        }

        @Test
        @DisplayName("Create Station - Return 401 (Unauthorized) when requested with invalid token")
        void createStationShouldReturnUnauthorizedWithInvalidToken() {
                Response response = createStation(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                validStation());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Create Station - Return 401 (Unauthorized) when requested with expired token")
        void createStationShouldReturnUnauthorizedWithExpiredToken() {
                String expiredToken = AuthUtils.getExpiredAdminToken();

                Response response = createStation(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                validStation());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }
}
