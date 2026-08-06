package com.herman.automation.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

@Epic("Train Ticket API")
@Feature("Schedule API")
@Story("Create Schedule")
@DisplayName("Create Schedule Test")
public class CreateScheduleTest extends BaseTest {
        private static final String STATIONS_ENDPOINT = "/api/stations";
        private static final String TRAINS_ENDPOINT = "/api/trains";
        private static final String SCHEDULES_ENDPOINT = "/api/schedules";
        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final String ACTIVE_STATUS = "ACTIVE";
        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
        private static final String ACCESS_DENIED_MESSAGE = "Access denied";
        private static final long NON_EXISTENT_TRAIN_ID = Long.MAX_VALUE;
        private static final long NON_EXISTENT_ORIGIN_STATION_ID = Long.MAX_VALUE;
        private static final Long NON_EXISTENT_DESTINATION_STATION_ID = Long.MAX_VALUE;
        private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
                        .ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        private String adminToken;
        private String userToken;

        private record TrainRequest(
                        String trainCode,
                        String trainName) {
        }

        private record StationRequest(
                        String stationCode,
                        String stationName,
                        String city) {
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

        @Step("Create Train for test setup")
        private TrainData createTrainForSetup(RequestSpecification requestSpec) {
                TrainRequest body = new TrainRequest(
                                generateUniqueTrainCode(),
                                generateUniqueTrainName());

                Response response = requestSpec
                                .body(body)
                                .when()
                                .post(TRAINS_ENDPOINT)
                                .then()
                                .log().ifValidationFails()
                                .statusCode(201)
                                .extract()
                                .response();

                Long trainId = response.jsonPath().getLong("id");
                assertNotNull(trainId, "Extracted ID from API response should not be null");
                TrainData databaseTrain = DatabaseUtils.getTrainById(trainId);
                assertNotNull(
                                databaseTrain,
                                "Train setup should be persisted in the database");
                return databaseTrain;
        }

        @Step("Create Station for test setup")
        private StationData createStationForSetup(RequestSpecification requestSpec) {
                StationRequest body = new StationRequest(
                                generateUniqueStationCode(),
                                generateUniqueStationName(),
                                generateCity());

                Response response = requestSpec
                                .body(body)
                                .when()
                                .post(STATIONS_ENDPOINT)
                                .then()
                                .log().ifValidationFails()
                                .statusCode(201)
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

        @Step("Prepare valid schedule data")
        private ScheduleRequest validSchedule() {

                RequestSpecification adminRequest = requestAsAdmin();
                TrainData train = createTrainForSetup(adminRequest);
                StationData origin = createStationForSetup(adminRequest);
                StationData destination = createStationForSetup(adminRequest);
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
                        RequestSpecification requestSpec, ScheduleRequest request) {
                return Allure.step(
                                "POST " + SCHEDULES_ENDPOINT,
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .body(request)
                                                .when()
                                                .post(SCHEDULES_ENDPOINT));
        }

        private void assertCreatedScheduleSuccessfulResponse(
                        Response response, ScheduleRequest expected) {
                Allure.step("Verify schedule creation response",
                                () -> {
                                        response.then()
                                                        .statusCode(201)
                                                        .body("id", notNullValue())
                                                        .body("train.id", equalTo(expected.trainId().intValue()))
                                                        .body("originStation.id",
                                                                        equalTo(expected.originStationId().intValue()))
                                                        .body("destinationStation.id",
                                                                        equalTo(expected.destinationStationId()
                                                                                        .intValue()))
                                                        .body("departureTime",
                                                                        equalTo(expected.departureTime()
                                                                                        .format(DATE_TIME_FORMATTER)))
                                                        .body("arrivalTime",
                                                                        equalTo(expected.arrivalTime()
                                                                                        .format(DATE_TIME_FORMATTER)))
                                                        .body("status", equalTo(ACTIVE_STATUS))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        String priceStr = response.jsonPath().getString("price");
                                        assertNotNull(priceStr, "Price property missing from the JSON payload");
                                        BigDecimal actualPrice = new BigDecimal(priceStr);

                                        assertEquals(
                                                        0,
                                                        actualPrice.compareTo(expected.price()),
                                                        "Price should match expected value");

                                        assertResponseTime(response);
                                });
        }

        private void assertSchedulePersistedInDatabase(
                        ScheduleRequest expectedSchedule,
                        Long scheduleId) {
                Allure.step("Verify schedule is persisted in the database",
                                () -> {

                                        ScheduleData databaseSchedule = DatabaseUtils.getScheduleById(scheduleId);
                                        assertNotNull(
                                                        databaseSchedule,
                                                        "Created schedule should be persisted in the database");

                                        assertAll(
                                                        () -> assertEquals(
                                                                        expectedSchedule.trainId(),
                                                                        databaseSchedule.getTrainId()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.originStationId(),
                                                                        databaseSchedule.getOriginStationId()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.destinationStationId(),
                                                                        databaseSchedule.getDestinationStationId()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.departureTime(),
                                                                        databaseSchedule.getDepartureTime()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.arrivalTime(),
                                                                        databaseSchedule.getArrivalTime()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.price(),
                                                                        databaseSchedule.getPrice()),
                                                        () -> assertEquals(
                                                                        ACTIVE_STATUS,
                                                                        databaseSchedule.getStatus()));
                                });
        }

        private Response createSchedule(
                        RequestSpecification requestSpec,
                        String requestBody) {
                return Allure.step(
                                "POST /api/schedules with invalid request format",
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .contentType(ContentType.JSON)
                                                .body(requestBody)
                                                .when()
                                                .post(SCHEDULES_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
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

        private void assertAllRequiredScheduleFieldsValidationErrors(Response response) {
                Allure.step(
                                "Verify validation errors for all required fields",
                                () -> verifyValidationErrors(
                                                response,
                                                Map.of(
                                                                "trainId", "Train ID is required",
                                                                "originStationId", "Origin station ID is required",
                                                                "destinationStationId",
                                                                "Destination station ID is required",
                                                                "departureTime", "Departure time is required",
                                                                "arrivalTime", "Arrival time is required",
                                                                "price", "Price is required")

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

        private String requestBodyWithInvalidField(
                        ScheduleRequest valid,
                        String field,
                        String invalidValue) {

                String departureTime = "\"" + valid.departureTime().format(DATE_TIME_FORMATTER) + "\"";
                String arrivalTime = "\"" + valid.arrivalTime().format(DATE_TIME_FORMATTER) + "\"";
                String price = valid.price().toPlainString();

                switch (field) {
                        case "departureTime" -> departureTime = invalidValue;
                        case "arrivalTime" -> arrivalTime = invalidValue;
                        case "price" -> price = invalidValue;
                }

                return """
                                {
                                  "trainId": %d,
                                  "originStationId": %d,
                                  "destinationStationId": %d,
                                  "departureTime": %s,
                                  "arrivalTime": %s,
                                  "price": %s
                                }
                                """.formatted(
                                valid.trainId(),
                                valid.originStationId(),
                                valid.destinationStationId(),
                                departureTime,
                                arrivalTime,
                                price);
        }

        private void assertInvalidRequestFormat(
                        Response response,
                        String field) {
                Allure.step(
                                "Verify invalid request format for field: " + field,
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(400)
                                                        .body("message", equalTo("Invalid request format"))
                                                        .body("field", equalTo(field));

                                        assertResponseTime(response);
                                });
        }

        private static Stream<Arguments> invalidRequestFormats() {
                return Stream.of(
                                Arguments.of("price", "\"abc\""),
                                Arguments.of("price", "\"@#$%\""),
                                Arguments.of("price", "\"100 000\""),
                                Arguments.of("price", "\"100,000\""),
                                Arguments.of("departureTime", "\"2027-11-09 09:00:00\""),
                                Arguments.of("arrivalTime", "\"2027-11-09 11:00:00\""));
        }

        private static Stream<Arguments> invalidPrices() {
                return Stream.of(
                                Arguments.of(BigDecimal.ZERO, "Price must be greater than 0"),
                                Arguments.of(BigDecimal.valueOf(-1), "Price must be greater than 0"));
        }

        @Test
        @DisplayName("Create Schedule - Succeeds with valid data and is persisted in the database")
        void createScheduleShouldSucceedAndPersistData() {
                ScheduleRequest request = validSchedule();

                Response response = createSchedule(requestAsAdmin(), request);

                assertCreatedScheduleSuccessfulResponse(response, request);

                Long scheduleId = response.jsonPath().getLong("id");

                assertSchedulePersistedInDatabase(request, scheduleId);
        }

        @Test
        @DisplayName("Create Schedule - Return 403 (Forbidden) when requested by Regular User")
        void createScheduleShouldReturnForbiddenWhenRequestedByUser() {
                Response response = createSchedule(requestAsUser(), validSchedule());

                assertErrorResponse(response, 403, ACCESS_DENIED_MESSAGE);
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when trainId is empty")
        void createScheduleShouldReturnBadRequestWhenTrainIdIsEmpty() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                null,
                                valid.originStationId(),
                                valid.destinationStationId(),
                                valid.departureTime(),
                                valid.arrivalTime(),
                                valid.price());

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertValidationError(
                                response,
                                "trainId",
                                "Train ID is required");
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when stationId is empty")
        void createScheduleShouldReturnBadRequestWhenOriginStationIdIsEmpty() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                null,
                                valid.destinationStationId(),
                                valid.departureTime(),
                                valid.arrivalTime(),
                                valid.price());

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertValidationError(
                                response,
                                "originStationId",
                                "Origin station ID is required");
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when DestinationStationId is empty")
        void createScheduleShouldReturnBadRequestWhenDestinationStationIdIsEmpty() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                valid.originStationId(),
                                null,
                                valid.departureTime(),
                                valid.arrivalTime(),
                                valid.price());

                Response response = createSchedule(requestAsAdmin(), request);

                assertValidationError(
                                response,
                                "destinationStationId",
                                "Destination station ID is required");
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when departureTime is empty")
        void createScheduleShouldReturnBadRequestWhenDepartureTimeIsEmpty() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                valid.originStationId(),
                                valid.destinationStationId(),
                                null,
                                valid.arrivalTime(),
                                valid.price());

                Response response = createSchedule(requestAsAdmin(), request);

                assertValidationError(
                                response,
                                "departureTime",
                                "Departure time is required");
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when arrivalTime is empty")
        void createScheduleShouldReturnBadRequestWhenArrivalTimeIsEmpty() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                valid.originStationId(),
                                valid.destinationStationId(),
                                valid.departureTime(),
                                null,
                                valid.price());

                Response response = createSchedule(requestAsAdmin(), request);

                assertValidationError(
                                response,
                                "arrivalTime",
                                "Arrival time is required");
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when price is empty")
        void createScheduleShouldReturnBadRequestWhenPriceIsEmpty() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                valid.originStationId(),
                                valid.destinationStationId(),
                                valid.departureTime(),
                                valid.arrivalTime(),
                                null);

                Response response = createSchedule(requestAsAdmin(), request);

                assertValidationError(
                                response,
                                "price",
                                "Price is required");
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when all required fields are empty")
        void createScheduleShouldReturnBadRequestWhenAllRequiredFieldsAreEmpty() {
                ScheduleRequest request = new ScheduleRequest(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null);

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertAllRequiredScheduleFieldsValidationErrors(response);
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when originStationId equals destinationStationId")
        void createScheduleShouldReturnBadRequestWhenOriginEqualsDestination() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                valid.originStationId(),
                                valid.originStationId(),
                                valid.departureTime(),
                                valid.arrivalTime(),
                                valid.price());

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                response.then()
                                .log().ifValidationFails()
                                .statusCode(400);

                assertErrorResponse(response, 400, "Origin and destination stations cannot be the same");
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when departureTime is after arrivalTime")
        void createScheduleShouldReturnBadRequestWhenDepartureAfterArrival() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                valid.originStationId(),
                                valid.destinationStationId(),
                                valid.arrivalTime(),
                                valid.departureTime(),
                                valid.price());

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertErrorResponse(response, 400, "Arrival time must be after departure time");
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when departureTime is in the past")
        void createScheduleShouldReturnBadRequestWhenDepartureTimeIsInThePast() {
                ScheduleRequest valid = validSchedule();

                LocalDateTime departure = LocalDateTime.now().minusHours(1);
                LocalDateTime arrival = LocalDateTime.now().plusHours(2);

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                valid.originStationId(),
                                valid.destinationStationId(),
                                departure,
                                arrival,
                                valid.price());

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertValidationError(
                                response,
                                "departureTime",
                                "Departure time must be in the future");
        }

        @Test
        @DisplayName("Create Schedule - Return 400 (Bad Request) when arrivalTime equals departureTime")
        void createScheduleShouldReturnBadRequestWhenArrivalEqualsDepartureTime() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                valid.originStationId(),
                                valid.destinationStationId(),
                                valid.departureTime(),
                                valid.departureTime(),
                                valid.price());

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertErrorResponse(response, 400, "Arrival time must be after departure time");
        }

        @Test
        @DisplayName("Create Schedule - Return 409 (Conflict) when schedule already exists")
        void createScheduleShouldReturnConflictWhenScheduleAlreadyExists() {
                ScheduleRequest request = validSchedule();

                Response firstResponse = createSchedule(
                                requestAsAdmin(),
                                request);

                assertCreatedScheduleSuccessfulResponse(firstResponse, request);

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertErrorResponse(response, 409, "Schedule already exists.");
        }

        @Test
        @DisplayName("Create Schedule - Return 404 (Not Found) when Train does not exist")
        void createScheduleShouldReturnNotFoundWhenTrainDoesNotExist() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                NON_EXISTENT_TRAIN_ID,
                                valid.originStationId(),
                                valid.destinationStationId(),
                                valid.departureTime(),
                                valid.arrivalTime(),
                                valid.price());

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertErrorResponse(response, 404, "Train not found with ID: " + NON_EXISTENT_TRAIN_ID);
        }

        @Test
        @DisplayName("Create Schedule - Return 404 (Not Found) when station does not exist")
        void createScheduleShouldReturnNotFoundWhenOriginStationDoesNotExist() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                NON_EXISTENT_ORIGIN_STATION_ID,
                                valid.destinationStationId(),
                                valid.departureTime(),
                                valid.arrivalTime(),
                                valid.price());

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertErrorResponse(response, 404,
                                "Origin station not found with ID: " + NON_EXISTENT_ORIGIN_STATION_ID);
        }

        @Test
        @DisplayName("Create Schedule - Return 404 (Not Found) when destination station does not exist")
        void createScheduleShouldReturnNotFoundWhenDestinationStationDoesNotExist() {
                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                valid.originStationId(),
                                NON_EXISTENT_DESTINATION_STATION_ID,
                                valid.departureTime(),
                                valid.arrivalTime(),
                                valid.price());

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertErrorResponse(response, 404,
                                "Destination station not found with ID: " + NON_EXISTENT_DESTINATION_STATION_ID);
        }

        @ParameterizedTest(name = "Price = {0}")
        @MethodSource("invalidPrices")
        @DisplayName("Create Schedule - Return 400 (Bad Request) when price is not greater than 0")
        void createScheduleShouldReturnBadRequestWhenPriceIsNotGreaterThanZero(
                        BigDecimal price,
                        String expectedMessage) {

                ScheduleRequest valid = validSchedule();

                ScheduleRequest request = new ScheduleRequest(
                                valid.trainId(),
                                valid.originStationId(),
                                valid.destinationStationId(),
                                valid.departureTime(),
                                valid.arrivalTime(),
                                price);

                Response response = createSchedule(
                                requestAsAdmin(),
                                request);

                assertValidationError(
                                response,
                                "price",
                                expectedMessage);
        }

        @ParameterizedTest(name = "Invalid {0}: {1}")
        @MethodSource("invalidRequestFormats")
        @DisplayName("Create Schedule - Return 400 (Bad Request) when request format is invalid")
        void createScheduleShouldReturnBadRequestWhenRequestFormatIsInvalid(
                        String field,
                        String invalidValue) {

                ScheduleRequest valid = validSchedule();

                String body = requestBodyWithInvalidField(
                                valid,
                                field,
                                invalidValue);

                Response response = createSchedule(
                                requestAsAdmin(),
                                body);

                assertInvalidRequestFormat(
                                response,
                                field);
        }

        @Test
        @DisplayName("Create Schedule - Return 401 (Unauthorized) when requested without token")
        void createScheduleShouldReturnUnauthorizedWithoutToken() {
                Response response = createSchedule(
                                given()
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                validSchedule());

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
        }

        @Test
        @DisplayName("Create Schedule - Return 401 (Unauthorized) when requested with invalid token")
        void createScheduleShouldReturnUnauthorizedWithInvalidToken() {
                Response response = createSchedule(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                validSchedule());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Create Schedule - Return 401 (Unauthorized) when requested with expired token")
        void createScheduleShouldReturnUnauthorized401WithExpiredToken() {
                ScheduleRequest request = validSchedule();

                String expiredToken = AuthUtils.getExpiredAdminToken();

                Response response = createSchedule(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                request);

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

}
