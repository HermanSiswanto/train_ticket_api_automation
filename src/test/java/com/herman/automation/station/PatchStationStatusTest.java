package com.herman.automation.station;

import static com.herman.automation.utils.TestDataGenerator.generateCity;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationName;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

@Epic("Train Ticket API")
@Feature("Station API")
@Story("Patch Station Status")
@DisplayName("Patch Station Status Test")
public class PatchStationStatusTest extends BaseTest {
        private static final String STATIONS_ENDPOINT = "/api/stations";
        private static final String STATIONS_STATUS_ENDPOINT = "/api/stations/{id}/status";

        private static final String ACTIVE_STATUS = "ACTIVE";
        private static final String INACTIVE_STATUS = "INACTIVE";

        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
        private static final String ACCESS_DENIED_MESSAGE = "Access denied";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_STATION_ID = Long.MAX_VALUE;

        private String adminToken;
        private String userToken;

        private record PatchStationStatusRequest(
                        String status) {
        }

        private record StationRequest(
                        String stationCode,
                        String stationName,
                        String city) {
        }

        @BeforeEach
        void setUp() {
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

        @Step("Create Station for test setup")
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

                StationData databaseStation = DatabaseUtils.getStationById(stationId);

                assertNotNull(
                                databaseStation,
                                "Station setup should be persisted in the database");

                return databaseStation;
        }

        private Response patchStationStatus(
                        RequestSpecification requestSpec,
                        Long stationId,
                        PatchStationStatusRequest request) {

                return Allure.step(
                                "PATCH /api/stations/" + stationId + "/status",
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .pathParam("id", stationId)
                                                .body(request)
                                                .when()
                                                .patch(STATIONS_STATUS_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertStationStatusUpdatedSuccessfulResponse(
                        Response response,
                        StationData expectedStation,
                        String expectedStatus) {
                Allure.step(
                                "Verify successful station status update response",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200)
                                                        .body("id", equalTo(expectedStation.getId().intValue()))
                                                        .body("stationCode", equalTo(expectedStation.getStationCode()))
                                                        .body("stationName", equalTo(expectedStation.getStationName()))
                                                        .body("city", equalTo(expectedStation.getCity()))
                                                        .body("status", equalTo(expectedStatus))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        assertResponseTime(response);
                                });
        }

        private void assertStationStatusInDatabase(
                        StationData expectedStation,
                        String expectedStatus) {
                Allure.step(
                                "Verify station status in database is " + expectedStatus,
                                () -> {
                                        StationData databaseStation = DatabaseUtils.getStationById(
                                                                expectedStation.getId());

                                        assertNotNull(
                                                        databaseStation,
                                                        "Station should exist in the database");

                                        assertAll(
                                                        () -> assertEquals(
                                                                        expectedStation.getId(),
                                                                        databaseStation.getId()),
                                                        () -> assertEquals(
                                                                        expectedStation.getStationCode(),
                                                                        databaseStation.getStationCode()),
                                                        () -> assertEquals(
                                                                        expectedStation.getStationName(),
                                                                        databaseStation.getStationName()),
                                                        () -> assertEquals(
                                                                        expectedStation.getCity(),
                                                                        databaseStation.getCity()),
                                                        () -> assertEquals(
                                                                        expectedStatus,
                                                                        databaseStation.getStatus()));
                                });
        }

        private void changeStationStatusAsAdmin(
                        StationData station,
                        String targetStatus) {
                Allure.step(
                                "Set station status to " + targetStatus + " for test setup",
                                () -> {
                                        PatchStationStatusRequest request = new PatchStationStatusRequest(targetStatus);
                                        Response response = patchStationStatus(requestAsAdmin(), station.getId(),
                                                        request);

                                        assertStationStatusUpdatedSuccessfulResponse(
                                                        response,
                                                        station,
                                                        targetStatus);
                                        assertStationStatusInDatabase(
                                                        station,
                                                        targetStatus);
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

        private static Stream<Arguments> validStatusTransitions() {
                return Stream.of(
                                Arguments.of(
                                                "Update ACTIVE station to INACTIVE",
                                                ACTIVE_STATUS,
                                                INACTIVE_STATUS),
                                Arguments.of(
                                                "Update INACTIVE station to ACTIVE",
                                                INACTIVE_STATUS,
                                                ACTIVE_STATUS));
        }

        private static Stream<Arguments> sameStatusTestData() {
                return Stream.of(
                                Arguments.of(ACTIVE_STATUS, "Station is already ACTIVE"),
                                Arguments.of(INACTIVE_STATUS, "Station is already INACTIVE"));
        }

        // ===TESTS===

        @ParameterizedTest(name = "{0}")
        @MethodSource("validStatusTransitions")
        @DisplayName("Patch Station Status - Succeeds patch station status and the status is changed in database")
        void patchStationStatusShouldSucceed(
                        String scenario,
                        String initialStatus,
                        String targetStatus) {
                StationData station = createStationForSetup();

                if (INACTIVE_STATUS.equals(initialStatus)) {
                        changeStationStatusAsAdmin(station, INACTIVE_STATUS);
                }
                PatchStationStatusRequest request = new PatchStationStatusRequest(targetStatus);
                Response response = patchStationStatus(requestAsAdmin(), station.getId(), request);

                assertStationStatusUpdatedSuccessfulResponse(response, station, targetStatus);
                assertStationStatusInDatabase(station, targetStatus);
        }

        @ParameterizedTest(name = "Patch train status to same status: {0}")
        @MethodSource("sameStatusTestData")
        @DisplayName("Patch Station Status - Return 400 (Bad Request) when patching to current status")
        void patchStationStatusShouldReturnBadRequestWhenStatusIsAlreadySet(
                        String currentStatus,
                        String expectedMessage) {
                StationData station = createStationForSetup();

                if (INACTIVE_STATUS.equals(currentStatus)) {
                        changeStationStatusAsAdmin(station, INACTIVE_STATUS);
                }
                PatchStationStatusRequest request = new PatchStationStatusRequest(currentStatus);
                Response response = patchStationStatus(
                                requestAsAdmin(),
                                station.getId(),
                                request);

                assertErrorResponse(response, 400, expectedMessage);
                assertStationStatusInDatabase(station, currentStatus);
        }

        @Test
        @DisplayName("Patch Station Status - Return 403 (Forbidden) when requested by regular user")
        void patchStationStatusShouldReturnForbiddenWhenRequestedByUser() {
                StationData station = createStationForSetup();

                PatchStationStatusRequest request = new PatchStationStatusRequest(INACTIVE_STATUS);
                Response response = patchStationStatus(
                                requestAsUser(),
                                station.getId(),
                                request);

                assertErrorResponse(response, 403, ACCESS_DENIED_MESSAGE);
                assertStationStatusInDatabase(station, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Station Status - Return 400 (Bad Request) when status is empty")
        void patchStationStatusShouldReturnBadRequestWhenStatusIsEmpty() {
                StationData station = createStationForSetup();
                PatchStationStatusRequest request = new PatchStationStatusRequest("");
                Response response = patchStationStatus(
                                requestAsAdmin(),
                                station.getId(),
                                request);

                assertValidationError(response, "status", "Status is required");
                assertStationStatusInDatabase(station, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Station Status - Return 404 (Not Found) when station doesn't exist")
        void patchStationStatusShouldReturnNotFoundWhenStationDoesNotExist() {
                PatchStationStatusRequest request = new PatchStationStatusRequest(INACTIVE_STATUS);
                Response response = patchStationStatus(
                                requestAsAdmin(),
                                NON_EXISTENT_STATION_ID,
                                request);

                assertErrorResponse(
                                response,
                                404,
                                "Station not found with ID: " + NON_EXISTENT_STATION_ID);
        }

        @Test
        @DisplayName("Patch Station Status - Return 400 (Bad Request) when requested with invalid station status")
        void patchStationStatusShouldReturnBadRequestForInvalidStatus() {
                StationData station = createStationForSetup();
                PatchStationStatusRequest request = new PatchStationStatusRequest("DISABLED");
                Response response = patchStationStatus(
                                requestAsAdmin(),
                                station.getId(),
                                request);

                assertErrorResponse(
                                response,
                                400,
                                "Status must be either ACTIVE or INACTIVE");

                assertStationStatusInDatabase(station, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Station Status - Return 401 (Unauthorized) when requested without authentication")
        void patchStationStatusShouldReturnUnauthorizedWithoutToken() {
                StationData station = createStationForSetup();
                PatchStationStatusRequest request = new PatchStationStatusRequest(INACTIVE_STATUS);
                Response response = patchStationStatus(
                                given()
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                station.getId(),
                                request);

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
                assertStationStatusInDatabase(station, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Station Status - Return 401 (Unauthorized) when requested with invalid token")
        void patchStationStatusShouldReturnUnauthorizedWithInvalidToken() {
                StationData station = createStationForSetup();
                PatchStationStatusRequest request = new PatchStationStatusRequest(INACTIVE_STATUS);
                Response response = patchStationStatus(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                station.getId(),
                                request);

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertStationStatusInDatabase(station, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Station Status - Return 401 (Unauthorized) when requested with expired token")
        void patchStationtatusShouldReturnUnauthorizedWithExpiredAdminToken() {
                StationData station = createStationForSetup();
                String expiredToken = AuthUtils.getExpiredAdminToken();
                PatchStationStatusRequest request = new PatchStationStatusRequest(INACTIVE_STATUS);
                Response response = patchStationStatus(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                station.getId(),
                                request);

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertStationStatusInDatabase(station, ACTIVE_STATUS);
        }

}
