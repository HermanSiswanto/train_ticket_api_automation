package com.herman.automation.schedule;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.herman.automation.base.BaseTest;
import com.herman.automation.model.ScheduleData;
import com.herman.automation.model.StationData;
import com.herman.automation.model.TrainData;
import com.herman.automation.utils.AuthUtils;
import com.herman.automation.utils.DatabaseUtils;
import static com.herman.automation.utils.TestDataGenerator.generateArrivalTime;
import static com.herman.automation.utils.TestDataGenerator.generateCity;
import static com.herman.automation.utils.TestDataGenerator.generateDepartureTime;
import static com.herman.automation.utils.TestDataGenerator.generatePrice;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationName;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueTrainCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueTrainName;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

@Epic("Train Ticket API")
@Feature("Schedule API")
@Story("Get Schedule")
@DisplayName("Get Schedule Test")
public class GetScheduleTest extends BaseTest {

        private static final String TRAINS_ENDPOINT = "/api/trains";
        private static final String STATIONS_ENDPOINT = "/api/stations";
        private static final String SCHEDULES_ENDPOINT = "/api/schedules";
        private static final String SCHEDULE_BY_ID_ENDPOINT = "/api/schedules/{id}";

        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_SCHEDULE_ID = Long.MAX_VALUE;

        private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
                        .ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        private String adminToken;
        private String userToken;

        private enum Role {
                ADMIN,
                USER
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

        private record ScheduleRequest(
                        Long trainId,
                        Long originStationId,
                        Long destinationStationId,
                        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime departureTime,
                        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime arrivalTime,
                        BigDecimal price) {
        }

        @BeforeEach
        void setUp() {
                adminToken = AuthUtils.loginAsAdmin();
                userToken = AuthUtils.loginAsUser();
        }

        private RequestSpecification requestAs(Role role) {
                return given()
                                .auth()
                                .oauth2(tokenFor(role))
                                .contentType(ContentType.JSON)
                                .accept(ContentType.JSON);
        }

        private String tokenFor(Role role) {
                return role == Role.ADMIN ? adminToken : userToken;
        }

        @Step("Create Train for test setup")
        private TrainData createTrainForSetup(RequestSpecification requestSpec) {
                TrainRequest request = new TrainRequest(
                                generateUniqueTrainCode(),
                                generateUniqueTrainName());

                Response response = requestSpec
                                .filter(allureFilter)
                                .body(request)
                                .when()
                                .post(TRAINS_ENDPOINT);
                response.then()
                                .log().ifValidationFails()
                                .statusCode(201);

                Long trainId = response.jsonPath().getLong("id");
                assertNotNull(trainId, "Extracted ID from API response should not be null");

                TrainData databaseTrain = DatabaseUtils.getTrainById(trainId);
                assertNotNull(databaseTrain, "Train setup should persisted in the database");

                return databaseTrain;
        }

        @Step("Create Station for test setup")
        private StationData createStationForSetup(RequestSpecification requestSpec, String stationRole) {
                return Allure.step(
                                "Create " + stationRole + " station for test setup",
                                () -> {
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
                                        assertNotNull(stationRole, "Extracted ID from API response should not be null");

                                        StationData station = DatabaseUtils.getStationById(stationId);
                                        assertNotNull(station, "Station setup should persisted in the database");

                                        return station;
                                });
        }

        @Step("Prepare valid schedule request")
        private ScheduleRequest createValidScheduleRequest() {
                RequestSpecification adminRequest = requestAs(Role.ADMIN);

                TrainData train = createTrainForSetup(adminRequest);
                StationData origin = createStationForSetup(adminRequest,"origin");
                StationData destination = createStationForSetup(adminRequest,"destination");

                LocalDateTime departure = generateDepartureTime();

                return new ScheduleRequest(
                                train.getId(),
                                origin.getId(),
                                destination.getId(),
                                departure,
                                generateArrivalTime(departure),
                                generatePrice());
        }

        @Step("Create schedule for test setup")
        private ScheduleData createScheduleForSetup() {

                ScheduleRequest request = createValidScheduleRequest();
                RequestSpecification adminRequest = requestAs(Role.ADMIN);
                Response response = createSchedule(adminRequest, request);

                response.then()
                                .log().ifValidationFails()
                                .statusCode(201)
                                .body("id", notNullValue());

                Long scheduleId = response.jsonPath().getLong("id");
                assertNotNull(scheduleId, "Extracted ID from API response should not be null");

                ScheduleData schedule = DatabaseUtils.getScheduleById(scheduleId);
                assertNotNull(schedule, "Schedule setup should be persisted in the database");

                return schedule;
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

        private Response getSchedule(
                        RequestSpecification requestSpec,
                        Long scheduleId) {
                return Allure.step(
                                "GET /api/schedules/" + scheduleId,
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .pathParam("id", scheduleId)
                                                .when()
                                                .get(SCHEDULE_BY_ID_ENDPOINT));
        }

        private Response getAllSchedules(RequestSpecification requestSpec) {
                return Allure.step(
                                "GET /api/schedules",
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .when()
                                                .get(SCHEDULES_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
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

        private void assertScheduleDetail(
                        Response response,
                        ScheduleData expectedSchedule) {
                Allure.step(
                                "Verify schedule detail response",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200)
                                                        .body("id", equalTo(expectedSchedule.getId().intValue()))
                                                        .body("train.id",
                                                                        equalTo(expectedSchedule.getTrainId()
                                                                                        .intValue()))
                                                        .body("originStation.id",
                                                                        equalTo(expectedSchedule.getOriginStationId()
                                                                                        .intValue()))
                                                        .body("destinationStation.id",
                                                                        equalTo(expectedSchedule
                                                                                        .getDestinationStationId()
                                                                                        .intValue()))
                                                        .body("departureTime",
                                                                        equalTo(expectedSchedule.getDepartureTime()
                                                                                        .format(DATE_TIME_FORMATTER)))
                                                        .body("arrivalTime",
                                                                        equalTo(expectedSchedule.getArrivalTime()
                                                                                        .format(DATE_TIME_FORMATTER)))
                                                        .body("status", equalTo(expectedSchedule.getStatus()))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        BigDecimal actualPrice = new BigDecimal(
                                                        response.jsonPath().get("price").toString());

                                        assertEquals(
                                                        0,
                                                        actualPrice.compareTo(expectedSchedule.getPrice()),
                                                        "Response price should match database data");

                                        assertResponseTime(response);
                                });
        }

        private void assertScheduleExistsInList(
                        Response response,
                        ScheduleData expectedSchedule) {
                Allure.step(
                                "Verify schedule exists in response list",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200);

                                        List<Map<String, Object>> schedules = response.jsonPath().getList("$");

                                        boolean scheduleExists = schedules.stream()
                                                        .anyMatch(schedule -> matchesSchedule(
                                                                        schedule,
                                                                        expectedSchedule));

                                        assertThat(
                                                        "Created schedule should exist in the schedule list",
                                                        scheduleExists,
                                                        is(true));

                                        assertResponseTime(response);
                                });
        }

        @SuppressWarnings("unchecked")
        private boolean matchesSchedule(
                        Map<String, Object> actualSchedule,
                        ScheduleData expectedSchedule) {
                Map<String, Object> actualTrain = (Map<String, Object>) actualSchedule.get("train");

                Map<String, Object> actualOriginStation = (Map<String, Object>) actualSchedule.get("originStation");

                Map<String, Object> actualDestinationStation = (Map<String, Object>) actualSchedule.get(
                                "destinationStation");

                Object actualPrice = actualSchedule.get("price");

                return hasExpectedId(
                                actualSchedule.get("id"),
                                expectedSchedule.getId())
                                && hasExpectedId(
                                                actualTrain.get("id"),
                                                expectedSchedule.getTrainId())
                                && hasExpectedId(
                                                actualOriginStation.get("id"),
                                                expectedSchedule.getOriginStationId())
                                && hasExpectedId(
                                                actualDestinationStation.get("id"),
                                                expectedSchedule.getDestinationStationId())
                                && expectedSchedule.getDepartureTime()
                                                .format(DATE_TIME_FORMATTER)
                                                .equals(actualSchedule.get("departureTime"))
                                && expectedSchedule.getArrivalTime()
                                                .format(DATE_TIME_FORMATTER)
                                                .equals(actualSchedule.get("arrivalTime"))
                                && expectedSchedule.getStatus()
                                                .equals(actualSchedule.get("status"))
                                && expectedSchedule.getPrice()
                                                .compareTo(
                                                                new BigDecimal(actualPrice.toString())) == 0;
        }

        private boolean hasExpectedId(
                        Object actualId,
                        Long expectedId) {

                return actualId instanceof Number id
                                && id.longValue() == expectedId;
        }

        private static Stream<Arguments> authorizedRoles() {
                return Stream.of(
                                Arguments.of(Role.ADMIN),
                                Arguments.of(Role.USER));
        }

        @ParameterizedTest(name = "Get all schedules as {0}")
        @MethodSource("authorizedRoles")
        @DisplayName("Get Schedule - Successfully retrieves All schedules for admin and regular user")
        void getAllSchedulesShouldContainCreatedSchedule(Role role) {
                ScheduleData expectedSchedule = createScheduleForSetup();

                Response response = getAllSchedules(requestAs(role));

                assertScheduleExistsInList(response, expectedSchedule);
        }

        @ParameterizedTest(name = "Get schedule by ID as {0}")
        @MethodSource("authorizedRoles")
        @DisplayName("Get Schedule - Successfully retrieves schedule by ID for admin and regular user")
        void getScheduleByIdShouldReturnScheduleDetail(Role role) {
                ScheduleData expectedSchedule = createScheduleForSetup();

                Response response = getSchedule(
                                requestAs(role),
                                expectedSchedule.getId());

                assertScheduleDetail(response, expectedSchedule);
        }

        @ParameterizedTest(name = "Get missing schedule as {0}")
        @MethodSource("authorizedRoles")
        @DisplayName("Get Schedule - Return 404 (Not Found) when schedule by ID does not exist")
        void getScheduleByIdShouldReturnNotFoundWhenScheduleDoesNotExist(Role role) {
                Response response = getSchedule(
                                requestAs(role),
                                NON_EXISTENT_SCHEDULE_ID);

                assertErrorResponse(response, 404, "Schedule not found with ID: " + NON_EXISTENT_SCHEDULE_ID);
        }

        @Test
        @DisplayName("Get Schedule - Return 401 (Unauthorized) when getting schedule by ID without authentication")
        void getScheduleByIdShouldReturnUnauthorizedWithoutToken() {
                ScheduleData expectedSchedule = createScheduleForSetup();

                Response response = getSchedule(
                                given()
                                                .accept(ContentType.JSON),
                                expectedSchedule.getId());

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
        }

        @Test
        @DisplayName("Get Schedule - Return 401 (Unauthorized) when getting schedule by ID with invalid token")
        void getScheduleByIdShouldReturnUnauthorizedWithInvalidToken() {
                ScheduleData expectedSchedule = createScheduleForSetup();

                Response response = getSchedule(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .accept(ContentType.JSON),
                                expectedSchedule.getId());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Get Schedule - Return 401 (Unauthorized) when getting schedule by ID with expired admin token")
        void getScheduleByIdShouldReturnUnauthorizedWithExpiredAdminToken() {
                ScheduleData expectedSchedule = createScheduleForSetup();
                String expiredAdminToken = AuthUtils.getExpiredAdminToken();

                Response response = getSchedule(
                                given()
                                                .auth()
                                                .oauth2(expiredAdminToken)
                                                .accept(ContentType.JSON),
                                expectedSchedule.getId());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Get Schedule - Return 401 (Unauthorized) when getting schedule by ID with expired user token")
        void getScheduleByIdShouldReturnUnauthorizedWithExpiredUserToken() {
                ScheduleData expectedSchedule = createScheduleForSetup();
                String expiredUserToken = AuthUtils.getExpiredUserToken();

                Response response = getSchedule(
                                given()
                                                .auth()
                                                .oauth2(expiredUserToken)
                                                .accept(ContentType.JSON),
                                expectedSchedule.getId());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }
}