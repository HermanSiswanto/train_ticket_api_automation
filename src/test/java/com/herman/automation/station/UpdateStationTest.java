package com.herman.automation.station;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.herman.automation.utils.TestDataGenerator.generateCity;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueLowercaseStationCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationName;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Epic("Train Ticket API")
@Feature("Station API")
@Story("Update Station")
@DisplayName("Update Station Test")
public class UpdateStationTest extends BaseTest {

        private static final String STATIONS_ENDPOINT = "/api/stations";
        private static final String STATION_BY_ID_ENDPOINT = "/api/stations/{id}";

        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
        private static final String ACCESS_DENIED_MESSAGE = "Access denied";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_STATION_ID = Long.MAX_VALUE;

        private static final String ACTIVE_STATUS = "ACTIVE";

        private String adminToken;
        private String userToken;

        @BeforeEach
        void setUp() {
                adminToken = AuthUtils.loginAsAdmin();
                userToken = AuthUtils.loginAsUser();
        }

        private record StationRequest(
                        String stationCode,
                        String stationName,
                        String city) {
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

        @Step("Create station as test setup")
        private StationData createStationForSetup() {
                StationRequest request = new StationRequest(
                                generateUniqueStationCode(),
                                generateUniqueStationName(), 
                                generateCity());

                Response response = requestAsAdmin()
                                .body(request)
                                .when()
                                .post(STATIONS_ENDPOINT)
                                .then()
                                .log().ifValidationFails()
                                .statusCode(201)
                                .body("id", notNullValue())
                                .extract()
                                .response();

                Long stationId = response.jsonPath().getLong("id");
                assertNotNull(stationId, "Extracted ID from API response should not be null");

                StationData databaseStation = DatabaseUtils.getStationById(
                                stationId);

                assertNotNull(
                                databaseStation,
                                "Station setup should be persisted in the database");

                return databaseStation;
        }

        private Response updateStation(
                        RequestSpecification request,
                        Long stationId,
                        StationRequest station) {
                return Allure.step(
                                "PUT /api/stations/{id}",
                                () -> request
                                                .filter(allureFilter)
                                                .pathParam("id", stationId)
                                                .body(station)
                                                .when()
                                                .put(STATION_BY_ID_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertValidationError(
                        Response response,
                        String field,
                        String expectedMessage) {
                Allure.step(
                                "Verify validation error for field: " + field,
                                () -> {

                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(400)
                                                        .body(field, equalTo(expectedMessage));

                                        assertResponseTime(response);
                                });
        }

        private void assertRequiredStationFieldsValidationErrors(Response response) {
                Allure.step(
                                "Verify validation errors for all required fields",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(400)
                                                        .body("stationCode", equalTo("Station code is required"))
                                                        .body("stationName", equalTo("Station name is required"))
                                                        .body("city", equalTo("City is required"));

                                        assertResponseTime(response);
                                });
        }

        private void assertSuccessfulStationUpdateResponse(
                        Response response,
                        StationData originalStation,
                        StationRequest expectedStation) {
                Allure.step(
                                "Verify successful station update response",

                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200)
                                                        .body("id", equalTo(originalStation.getId().intValue()))
                                                        .body("stationCode", equalTo(expectedStation.stationCode()))
                                                        .body("stationName", equalTo(expectedStation.stationName()))
                                                        .body("city", equalTo(expectedStation.city()))
                                                        .body("status", equalTo(ACTIVE_STATUS))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        assertResponseTime(response);
                                });
        }

        private void assertStationUpdatedInDatabase(
                        StationData originalStation,
                        StationRequest expectedStation) {

                Allure.step(
                                "Verify station is updated in database",
                                () -> {

                                        StationData actualStation = DatabaseUtils.getStationById(
                                                        originalStation.getId());

                                        assertNotNull(
                                                        actualStation,
                                                        "Updated station should exist in the database");

                                        assertAll(
                                                        () -> assertEquals(
                                                                        originalStation.getId(),
                                                                        actualStation.getId()),
                                                        () -> assertEquals(
                                                                        expectedStation.stationCode(),
                                                                        actualStation.getStationCode()),
                                                        () -> assertEquals(
                                                                        expectedStation.stationName(),
                                                                        actualStation.getStationName()),
                                                        () -> assertEquals(
                                                                        ACTIVE_STATUS,
                                                                        actualStation.getStatus()));
                                });
        }

        private void assertStationUnchangedInDatabase(StationData expectedStation) {

                Allure.step(
                                "Verify station data remains unchanged in database",
                                () -> {
                                        StationData actualStation = DatabaseUtils.getStationById(
                                                        expectedStation.getId());

                                        assertNotNull(
                                                        actualStation,
                                                        "Existing station should remain in the database");

                                        assertAll(
                                                        () -> assertEquals(
                                                                        expectedStation.getId(),
                                                                        actualStation.getId()),
                                                        () -> assertEquals(
                                                                        expectedStation.getStationCode(),
                                                                        actualStation.getStationCode()),
                                                        () -> assertEquals(
                                                                        expectedStation.getStationName(),
                                                                        actualStation.getStationName()),
                                                        () -> assertEquals(
                                                                        expectedStation.getCity(),
                                                                        actualStation.getCity()),
                                                        () -> assertEquals(
                                                                        expectedStation.getStatus(),
                                                                        actualStation.getStatus()));
                                });
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

        private static Stream<Arguments> invalidStationCodeTestData() {
                String invalidCharacterMessage = "Station code may only contain letters without spaces";

                return Stream.of(
                                Arguments.of("STA 01", invalidCharacterMessage),
                                Arguments.of("ST@S", invalidCharacterMessage),
                                Arguments.of("1234", invalidCharacterMessage)

                );
        }

        @Test
        @DisplayName("Update Station - Succeeds with valid data and is updated in the database")
        void updateStationShouldSucceedAndPersistData() {

                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest request = new StationRequest(
                                "UP" + generateUniqueStationCode(),
                                "UPDATED " + generateUniqueStationName(),
                                "UPDATED " + generateCity());

                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                request);

                assertSuccessfulStationUpdateResponse(
                                response,
                                originalStation,
                                request);

                assertStationUpdatedInDatabase(
                                originalStation,
                                request);
        }

        @Test
        @DisplayName("Update Station - Return 403 (Forbidden) when requested by regular user")
        void updateStationShouldReturnForbiddenWhenRequestedByUser() {
                StationData originalStation = createStationForSetup();
                RequestSpecification userRequest = requestAsUser();

                StationRequest request = new StationRequest(
                                generateUniqueStationCode(),
                                "Updated" + generateUniqueStationName(),
                                generateCity());

                Response response = updateStation(
                                userRequest,
                                originalStation.getId(),
                                request);

                assertErrorResponse(response, 403, ACCESS_DENIED_MESSAGE);
                assertStationUnchangedInDatabase(originalStation);
        }

        @Test
        @DisplayName("Update Station - Return 400 (Bad Request) when station code is empty")
        void updateStationShouldReturnBadRequestWhenStationCodeIsEmpty() {
                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest request = new StationRequest(
                                "",
                                "Updated " + generateUniqueStationName(),
                                generateCity());

                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                request);

                assertValidationError(
                                response,
                                "stationCode",
                                "Station code is required");

                assertStationUnchangedInDatabase(originalStation);
        }

        @Test
        @DisplayName("Update Station - Return 400 (Bad Request) when station name is empty")
        void updateStationShouldReturnBadRequestWhenStationNameIsEmpty() {
                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest request = new StationRequest(
                                generateUniqueStationCode(),
                                "",
                                generateCity());

                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                request);

                assertValidationError(
                                response,
                                "stationName",
                                "Station name is required");

                assertStationUnchangedInDatabase(originalStation);
        }

        @Test
        @DisplayName("Update Station - Return 400 (Bad Request) when station city is empty")
        void updateStationShouldReturnBadRequestWhenStationCityIsEmpty(){
                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest request = new StationRequest(
                                generateUniqueStationCode(),
                                generateUniqueStationName(),
                                "");

                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                request);
                assertValidationError(
                                response,
                                "city",
                                "City is required");

                assertStationUnchangedInDatabase(originalStation);
        }

        @Test
        @DisplayName("Update Station - Return 400 (Bad Request) when All fields are empty")
        void updateStationShouldReturnBadRequestWhenAllFieldsAreEmpty() {
                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest request = new StationRequest("", "", "");

                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                request);
                                
                assertRequiredStationFieldsValidationErrors(response);
                assertStationUnchangedInDatabase(originalStation);
        }

        @ParameterizedTest(name = "Update station should fail with invalid code: {0}")
        @MethodSource("invalidStationCodeTestData")
        @DisplayName("Update Station - Return 400 (Bad Request) when requested with invalid station code")
        void updateStationShouldReturnBadRequestWithInvalidStationCode(
                        String invalidStationCode,
                        String expectedMessage) {
                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();
                StationRequest request = new StationRequest(
                                invalidStationCode,
                                "Updated " + generateUniqueStationName(), 
                                generateCity());
                
                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                request);
                assertValidationError(response, "stationCode", expectedMessage);
                assertStationUnchangedInDatabase(originalStation);
        }

        @Test
        @DisplayName("Update Station - Convert station code to uppercase")
        void updateStationShouldConvertStationCodeToUppercase() {

                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest updatedStation = new StationRequest(
                                generateUniqueLowercaseStationCode(),
                                generateUniqueStationName(),
                                generateCity());

                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                updatedStation);

                StationRequest expectedStation = new StationRequest(
                                updatedStation.stationCode().toUpperCase(),
                                updatedStation.stationName(),
                                updatedStation.city());

                assertSuccessfulStationUpdateResponse(
                                response,
                                originalStation,
                                expectedStation);

                assertStationUpdatedInDatabase(
                                originalStation,
                                expectedStation);
        }

        @Test
        @DisplayName("Update Station - Trim leading and trailing spaces from station name")
        void updateStationShouldTrimStationName() {
                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest updatedStation = new StationRequest(
                                generateUniqueStationCode(),
                                " " + " updated " + generateUniqueStationName() + " ",
                                generateCity());

                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                updatedStation);

                StationRequest expectedStation = new StationRequest(
                                updatedStation.stationCode(),
                                updatedStation.stationName().trim(),
                                updatedStation.city());

                assertSuccessfulStationUpdateResponse(
                                response,
                                originalStation,
                                expectedStation);

                assertStationUpdatedInDatabase(
                                originalStation,
                                expectedStation);
        }

        @Test
        @DisplayName("Update Station - Trim leading and trailing spaces from station city")
        void updateStationShouldTrimStationCity() {
                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest updatedStation = new StationRequest(
                                generateUniqueStationCode(),
                                generateUniqueStationName(),
                                " " + "updated " + generateCity() + " ");

                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                updatedStation);

                StationRequest expectedStation = new StationRequest(
                                updatedStation.stationCode(),
                                updatedStation.stationName(),
                                updatedStation.city().trim());

                assertSuccessfulStationUpdateResponse(
                                response,
                                originalStation,
                                expectedStation);

                assertStationUpdatedInDatabase(
                                originalStation,
                                expectedStation);
        }

        @Test
        @DisplayName("Update Station - Return 400 (Bad Request) when station code exceeds maximum length")
        void updateStationShouldReturnBadRequestWhenStationCodeExceedsMaximumLength() {
                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest request = new StationRequest(
                                "K".repeat(11), 
                                generateUniqueStationName(),
                                generateCity());
                
                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                request);

                assertValidationError(
                                response,
                                "stationCode",
                                "Station code must not exceed 10 characters");

                assertStationUnchangedInDatabase(originalStation);
        }

        @Test
        @DisplayName("Create Station - Return 400 (Bad Request) when station name exceeds maximum length")
        void updateStationShouldReturnBadRequestWhenStationNameExceedsMaximumLength() {
                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest request = new StationRequest(
                                generateUniqueStationCode(),
                                "A".repeat(101), 
                                generateCity());
                
                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                request);

                assertValidationError(
                                response,
                                "stationName",
                                "Station name must not exceed 100 characters");

                assertStationUnchangedInDatabase(originalStation);
        }

        @Test
        @DisplayName("Update Station - Return 400 (Bad Request) when station city exceeds maximum length")
        void updateStationShouldReturnBadRequestWhenStationCityExceedsMaximumLength() {
                StationData originalStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest request = new StationRequest(
                                generateUniqueStationCode(), 
                                generateUniqueStationName(),
                                "C".repeat(101));

                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(), 
                                request);

                assertValidationError(
                                response,
                                "city",
                                "City must not exceed 100 characters");

                assertStationUnchangedInDatabase(originalStation);
        }

        @Test
        @DisplayName("Update Station - Return 409 (Conflict) when station code already exists")
        void updateStationShouldReturnConflictWhenStationCodeAlreadyExists() {
                StationData originalStation = createStationForSetup();
                StationData existingStation = createStationForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                StationRequest updateStation = new StationRequest(
                                existingStation.getStationCode(),
                                "Updated " + generateUniqueStationName(), 
                                generateCity());

                Response response = updateStation(
                                adminRequest,
                                originalStation.getId(),
                                updateStation);

                response.then()
                                .log().ifValidationFails()
                                .statusCode(409)
                                .body(
                                                "message",
                                                equalTo("Station code already exists"));

                assertResponseTime(response);

                assertStationUnchangedInDatabase(originalStation);
                assertStationUnchangedInDatabase(existingStation);
        }

        @Test
        @DisplayName("Update Station - Return 404 (Not Found) when Station doesn't exist")
        void updateStationShouldReturnNotFoundWhenStationDoesNotExist() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationRequest updatedStation = new StationRequest(
                                generateUniqueStationCode(),
                                "Updated " + generateUniqueStationName(), 
                                generateCity());

                Response response = updateStation(
                                adminRequest,
                                NON_EXISTENT_STATION_ID,
                                updatedStation);

                assertErrorResponse(response, 404, "Station not found with ID: " + NON_EXISTENT_STATION_ID);
        }

        @Test
        @DisplayName("Update Station - Return 401 (Unauthorized) when requested without authentication")
        void updateStationShouldReturnUnauthorizedWithoutToken() {
                StationData originalStation = createStationForSetup();

                Response response = updateStation(
                                given()
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                originalStation.getId(),
                                new StationRequest(
                                                generateUniqueStationCode(),
                                                "Updated " + generateUniqueStationName(), generateCity()));

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
                assertStationUnchangedInDatabase(originalStation);
        }

        @Test
        @DisplayName("Update Station - Return 401 (Unauthorized) when requested with invalid token")
        void updateStationShouldReturnUnauthorizedWithInvalidToken() {
                StationData originalStation = createStationForSetup();

                Response response = updateStation(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                originalStation.getId(),
                                new StationRequest(
                                                generateUniqueStationCode(),
                                                "Updated " + generateUniqueStationName(), 
                                                generateCity()));

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertStationUnchangedInDatabase(originalStation);
        }

        @Test
        @DisplayName("Update Station - Return 401 (Unauthorized) when requested with expired token")
        void updateStationShouldReturnUnauthorizedWithExpiredToken() {
                StationData originalStation = createStationForSetup();
                String expiredToken = AuthUtils.getExpiredAdminToken();

                Response response = updateStation(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                originalStation.getId(),
                                new StationRequest(
                                                generateUniqueStationCode(),
                                                "Updated " + generateUniqueStationName(), generateCity()));

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertStationUnchangedInDatabase(originalStation);
        }

}