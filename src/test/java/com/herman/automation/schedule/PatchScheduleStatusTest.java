package com.herman.automation.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.herman.automation.base.BaseTest;
import com.herman.automation.model.ScheduleData;
import com.herman.automation.model.StationData;
import com.herman.automation.model.TrainData;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Stream;

import static com.herman.automation.utils.TestDataGenerator.generateArrivalTime;
import static com.herman.automation.utils.TestDataGenerator.generateCity;
import static com.herman.automation.utils.TestDataGenerator.generateDepartureTime;
import static com.herman.automation.utils.TestDataGenerator.generatePrice;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationName;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueTrainCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueTrainName;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Epic("Train Ticket API")
@Feature("Schedule API")
@Story("Patch Schedule Status")
@DisplayName("Patch Schedule Status Test")
public class PatchScheduleStatusTest extends BaseTest {

        private static final String TRAINS_ENDPOINT = "/api/trains";
        private static final String STATIONS_ENDPOINT = "/api/stations";
        private static final String SCHEDULES_ENDPOINT = "/api/schedules";
        private static final String SCHEDULE_STATUS_ENDPOINT = "/api/schedules/{id}/status";

        private static final String ACTIVE_STATUS = "ACTIVE";
        private static final String INACTIVE_STATUS = "INACTIVE";

        private static final String ACCESS_DENIED_MESSAGE = "Access denied";
        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_SCHEDULE_ID = Long.MAX_VALUE;

        private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
                        .ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        private String adminToken;
        private String userToken;

        private record PatchScheduleStatusRequest(
                        String status) {
        }

        private record ScheduleRequest(
                        Long trainId,
                        Long originStationId,
                        Long destinationStationId,
                        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime departureTime,
                        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime arrivalTime,
                        BigDecimal price) {
        }

        private record StationRequest(
                        String stationCode,
                        String stationName,
                        String city) {
        }

        private record TrainRequest(
                        String trainCode,
                        String trainName) {
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

        @Step("Create Train for test setup")
        public TrainData createTrainForSetup(RequestSpecification request) {

                TrainRequest body = new TrainRequest(
                                generateUniqueTrainCode(),
                                generateUniqueTrainName());

                Response response = request
                                .filter(allureFilter)
                                .body(body)
                                .when()
                                .post(TRAINS_ENDPOINT);

                response.then()
                                .log().ifValidationFails()
                                .statusCode(201);

                Long trainId = response.jsonPath().getLong("id");
                assertNotNull(trainId, "Extracted ID from API response should not be null");

                TrainData train = DatabaseUtils.getTrainById(trainId);
                assertNotNull(train, "Train setup should be persisted in database");
                return train;
        }

        @Step("Create {stationRole} station for test setup")
        public StationData createStationForSetup(
                        RequestSpecification requestSpec,
                        String stationRole) {

                StationRequest request = new StationRequest(
                                generateUniqueStationCode(),
                                generateUniqueStationName(),
                                generateCity());

                Response response = requestSpec
                                .filter(allureFilter)
                                .body(request)
                                .when()
                                .post(STATIONS_ENDPOINT);

                response.then()
                                .log().ifValidationFails()
                                .statusCode(201);

                Long stationId = response.jsonPath().getLong("id");
                assertNotNull(stationId, "Extracted ID from API response should not be null");

                StationData databaseStation = DatabaseUtils.getStationById(stationId);

                assertNotNull(databaseStation, "Station setup should be persisted in database");

                return databaseStation;
        }

        @Step("Prepare valid schedule request")
        private ScheduleRequest createValidScheduleRequest() {

                RequestSpecification adminRequest = requestAsAdmin();
                TrainData train = createTrainForSetup(adminRequest);
                StationData origin = createStationForSetup(adminRequest, "origin");
                StationData destination = createStationForSetup(adminRequest, "destination");
                LocalDateTime departure = generateDepartureTime();

                return new ScheduleRequest(
                                train.getId(),
                                origin.getId(),
                                destination.getId(),
                                departure,
                                generateArrivalTime(departure),
                                generatePrice());
        }

        private Response createSchedule(
                        RequestSpecification requestSpec,
                        ScheduleRequest request) {

                return Allure.step(
                                "POST /api/schedules",
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .body(request)
                                                .when()
                                                .post(SCHEDULES_ENDPOINT));
        }

        @Step("Create schedule for test setup")
        private ScheduleData createScheduleForSetup() {
                RequestSpecification adminRequest = requestAsAdmin();
                ScheduleRequest request = createValidScheduleRequest();

                Response response = createSchedule(
                                adminRequest,
                                request);

                Long scheduleId = response.jsonPath().getLong("id");
                assertNotNull(scheduleId, "Extracted ID from API response should not be null");

                ScheduleData schedule = DatabaseUtils.getScheduleById(scheduleId);
                assertNotNull(
                                schedule,
                                "Schedule setup should be persisted in database");

                return schedule;
        }

        private Response patchScheduleStatus(
                        RequestSpecification requestSpec,
                        Long scheduleId,
                        PatchScheduleStatusRequest request) {

                return Allure.step(
                                "PATCH /api/schedules/" + scheduleId + "/status",
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .pathParam("id", scheduleId)
                                                .body(request)
                                                .when()
                                                .patch(SCHEDULE_STATUS_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertScheduleStatusUpdatedSuccessfulResponse(
                        Response response,
                        ScheduleData schedule,
                        String expectedStatus) {
                Allure.step(
                                "Verify successful schedule status update response",

                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200)
                                                        .body("id", equalTo(schedule.getId().intValue()))
                                                        .body(
                                                                        "train.id",
                                                                        equalTo(schedule.getTrainId()
                                                                                        .intValue()))
                                                        .body(
                                                                        "originStation.id", equalTo(
                                                                                        schedule.getOriginStationId()
                                                                                                        .intValue()))
                                                        .body(
                                                                        "destinationStation.id", equalTo(
                                                                                        schedule.getDestinationStationId()
                                                                                                        .intValue()))
                                                        .body(
                                                                        "departureTime", equalTo(
                                                                                        schedule.getDepartureTime()
                                                                                                        .format(DATE_TIME_FORMATTER)))
                                                        .body(
                                                                        "arrivalTime", equalTo(
                                                                                        schedule.getArrivalTime()
                                                                                                        .format(DATE_TIME_FORMATTER)))
                                                        .body("status", equalTo(expectedStatus))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        String priceStr = response.jsonPath().getString("price");
                                        assertNotNull(priceStr, "Price property missing from the JSON payload");
                                        BigDecimal actualPrice = new BigDecimal(priceStr);

                                        assertEquals(
                                                        0,
                                                        actualPrice.compareTo(schedule.getPrice()),
                                                        "Response price should remain unchanged");

                                        assertResponseTime(response);
                                });
        }

        private void assertScheduleStatusInDatabase(
                        ScheduleData expectedSchedule,
                        String expectedStatus) {
                assertNotNull(expectedSchedule, "Target schedule record context cannot be null");
                assertNotNull(expectedSchedule.getId(), "Target schedule ID cannot be null");

                Allure.step(
                                "Verify schedule status in database is " + expectedStatus,

                                () -> {
                                        ScheduleData databaseSchedule = DatabaseUtils.getScheduleById(
                                                        expectedSchedule.getId());

                                        assertNotNull(
                                                        databaseSchedule,
                                                        "Schedule should exist in the database");

                                        assertAll(
                                                        () -> assertEquals(
                                                                        expectedSchedule.getTrainId(),
                                                                        databaseSchedule.getTrainId()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getOriginStationId(),
                                                                        databaseSchedule.getOriginStationId()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getDestinationStationId(),
                                                                        databaseSchedule.getDestinationStationId()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getDepartureTime(),
                                                                        databaseSchedule.getDepartureTime()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getArrivalTime(),
                                                                        databaseSchedule.getArrivalTime()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getPrice(),
                                                                        databaseSchedule.getPrice()),
                                                        () -> assertEquals(
                                                                        expectedStatus,
                                                                        databaseSchedule.getStatus()));
                                });
        }

        private void changeScheduleStatusAsAdmin(
                        ScheduleData schedule,
                        String targetStatus) {

                Allure.step(
                                "Set schedule status to " + targetStatus + " for test setup",
                                () -> {

                                        PatchScheduleStatusRequest request = new PatchScheduleStatusRequest(
                                                        targetStatus);

                                        Response response = patchScheduleStatus(
                                                        requestAsAdmin(),
                                                        schedule.getId(),
                                                        request);

                                        assertScheduleStatusUpdatedSuccessfulResponse(
                                                        response,
                                                        schedule,
                                                        targetStatus);

                                        assertScheduleStatusInDatabase(
                                                        schedule,
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
                                                "Update ACTIVE schedule to INACTIVE",
                                                ACTIVE_STATUS,
                                                INACTIVE_STATUS),
                                Arguments.of(
                                                "Update INACTIVE schedule to ACTIVE",
                                                INACTIVE_STATUS,
                                                ACTIVE_STATUS));
        }

        private static Stream<Arguments> sameStatusTestData() {
                return Stream.of(
                                Arguments.of(ACTIVE_STATUS, "Schedule is already ACTIVE"),
                                Arguments.of(INACTIVE_STATUS, "Schedule is already INACTIVE"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("validStatusTransitions")
        @DisplayName("Patch Schedule Status - Succeeds patch schedule status and the status is changed in database")
        void patchScheduleStatusShouldSucceed(
                        String scenario,
                        String initialStatus,
                        String targetStatus) {

                ScheduleData schedule = createScheduleForSetup();

                if (INACTIVE_STATUS.equals(initialStatus)) {
                        changeScheduleStatusAsAdmin(schedule, INACTIVE_STATUS);
                }

                PatchScheduleStatusRequest request = new PatchScheduleStatusRequest(targetStatus);

                Response response = patchScheduleStatus(
                                requestAsAdmin(),
                                schedule.getId(),
                                request);

                assertScheduleStatusUpdatedSuccessfulResponse(
                                response,
                                schedule,
                                targetStatus);

                assertScheduleStatusInDatabase(
                                schedule,
                                targetStatus);
        }

        @ParameterizedTest(name = "Patch schedule to same status: {0}")
        @MethodSource("sameStatusTestData")
        @DisplayName("Patch Schedule Status - Return 400 (Bad Request) when patching to the current status")
        void patchScheduleStatusShouldReturnBadRequestWhenStatusIsAlreadySet(
                        String currentStatus,
                        String expectedMessage) {
                ScheduleData schedule = createScheduleForSetup();

                if (INACTIVE_STATUS.equals(currentStatus)) {
                        changeScheduleStatusAsAdmin(schedule, INACTIVE_STATUS);
                }
                PatchScheduleStatusRequest request = new PatchScheduleStatusRequest(currentStatus);

                Response response = patchScheduleStatus(
                                requestAsAdmin(),
                                schedule.getId(),
                                request);

                assertErrorResponse(response, 400, expectedMessage);

                assertScheduleStatusInDatabase(schedule, currentStatus);
        }

        @Test
        @DisplayName("Patch Schedule Status - Return 403 (Forbidden) when requested by regular user")
        void patchScheduleStatusShouldReturnForbiddenWhenRequestedByUser() {
                ScheduleData schedule = createScheduleForSetup();

                PatchScheduleStatusRequest request = new PatchScheduleStatusRequest(INACTIVE_STATUS);

                Response response = patchScheduleStatus(
                                requestAsUser(),
                                schedule.getId(),
                                request);

                assertErrorResponse(response, 403, ACCESS_DENIED_MESSAGE);

                assertScheduleStatusInDatabase(schedule, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Schedule Status - Return 404 (Not found) when schedule doesn't exist")
        void patchScheduleStatusShouldReturnNotFoundWhenScheduleDoesNotExist() {
                PatchScheduleStatusRequest request = new PatchScheduleStatusRequest(INACTIVE_STATUS);
                Response response = patchScheduleStatus(
                                requestAsAdmin(),
                                NON_EXISTENT_SCHEDULE_ID,
                                request);

                assertErrorResponse(
                                response,
                                404,
                                "Schedule not found with ID: " + NON_EXISTENT_SCHEDULE_ID);
        }

        @Test
        @DisplayName("Patch Schedule Status - Return 400 (Bad Request) when status is empty")
        void patchScheduleStatusShouldReturnBadRequestWhenStatusIsEmpty() {

                ScheduleData schedule = createScheduleForSetup();
                PatchScheduleStatusRequest request = new PatchScheduleStatusRequest("");

                Response response = patchScheduleStatus(
                                requestAsAdmin(),
                                schedule.getId(),
                                request);

                assertValidationError(
                                response,
                                "status",
                                "Status is required");

                assertScheduleStatusInDatabase(
                                schedule,
                                ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Schedule Status - Return 400 (Bad Request) when patching with invalid status")
        void patchScheduleStatusShouldReturnBadRequestForInvalidStatus() {
                ScheduleData schedule = createScheduleForSetup();
                PatchScheduleStatusRequest request = new PatchScheduleStatusRequest("DISABLED");

                Response response = patchScheduleStatus(
                                requestAsAdmin(),
                                schedule.getId(),
                                request);

                assertErrorResponse(
                                response,
                                400,
                                "Status must be either ACTIVE or INACTIVE");

                assertScheduleStatusInDatabase(schedule, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Schedule Status - Return 401 (Unauthorized) when requested without authentication")
        void patchScheduleStatusShouldReturnUnauthorizedWithoutToken() {
                ScheduleData schedule = createScheduleForSetup();
                PatchScheduleStatusRequest request = new PatchScheduleStatusRequest(INACTIVE_STATUS);
                Response response = patchScheduleStatus(
                                given()
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                schedule.getId(),
                                request);

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);

                assertScheduleStatusInDatabase(schedule, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Schedule Status - Return 401 (Unauthorized) when requested with invalid token")
        void patchScheduleStatusShouldReturnUnauthorizedWithInvalidToken() {
                ScheduleData schedule = createScheduleForSetup();
                PatchScheduleStatusRequest request = new PatchScheduleStatusRequest(INACTIVE_STATUS);
                Response response = patchScheduleStatus(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                schedule.getId(),
                                request);

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);

                assertScheduleStatusInDatabase(schedule, ACTIVE_STATUS);
        }
        @Test
        @DisplayName("Patch Schedule Status - Return 401 (Unauthorized) when requested with expired token")
        void patchScheduleStatusShouldReturnUnauthorizedWithExpiredToken() {
                ScheduleData schedule = createScheduleForSetup();
                String expiredToken = AuthUtils.getExpiredAdminToken();
                PatchScheduleStatusRequest request = new PatchScheduleStatusRequest(INACTIVE_STATUS);
                Response response = patchScheduleStatus(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                schedule.getId(),
                                request);

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);

                assertScheduleStatusInDatabase(schedule, ACTIVE_STATUS);
        }
}