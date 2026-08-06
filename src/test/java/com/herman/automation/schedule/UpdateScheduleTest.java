package com.herman.automation.schedule;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
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
@Story("Update Schedule")
@DisplayName("Update Schedule Test")
public class UpdateScheduleTest extends BaseTest {

        private static final String STATIONS_ENDPOINT = "/api/stations";
        private static final String TRAINS_ENDPOINT = "/api/trains";
        private static final String SCHEDULES_ENDPOINT = "/api/schedules";
        private static final String SCHEDULE_BY_ID_ENDPOINT = "/api/schedules/{id}";

        private static final String ACTIVE_STATUS = "ACTIVE";
        private static final String ACCESS_DENIED_MESSAGE = "Access denied";
        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_TRAIN_ID = Long.MAX_VALUE;
        private static final long NON_EXISTENT_ORIGIN_STATION_ID = Long.MAX_VALUE;
        private static final long NON_EXISTENT_DESTINATION_STATION_ID = Long.MAX_VALUE;
        private static final long NON_EXISTENT_SCHEDULE_ID = Long.MAX_VALUE;

        private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
                        .ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        private String adminToken;
        private String userToken;

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
        private TrainData createTrainForSetup(RequestSpecification request) {
                TrainRequest body = new TrainRequest(
                                generateUniqueTrainCode(),
                                generateUniqueTrainName());

                Response response = request
                                .body(body)
                                .when()
                                .post(TRAINS_ENDPOINT)
                                .then()
                                .log().ifValidationFails()
                                .statusCode(201)
                                .body("id", notNullValue())
                                .extract()
                                .response();

                Long trainId = response.jsonPath().getLong("id");
                assertNotNull(trainId, "Extracted ID from API response should not be null");

                TrainData train = DatabaseUtils.getTrainById(trainId);
                assertNotNull(train, "Train setup should be persisted in the database");

                return train;
        }

        @Step("Create {stationRole} station for test setup")
        private StationData createStationForSetup(RequestSpecification request, String stationRole) {

                StationRequest body = new StationRequest(
                                generateUniqueStationCode(),
                                generateUniqueStationName(),
                                generateCity());

                Response response = request
                                .body(body)
                                .when()
                                .post(STATIONS_ENDPOINT);

                response.then()
                                .log().ifValidationFails()
                                .statusCode(201);

                Long stationId = response.jsonPath().getLong("id");
                assertNotNull(stationRole, "Extracted ID from API response should not be null");

                StationData station = DatabaseUtils.getStationById(stationId);
                assertNotNull(
                                station,
                                "Station setup should be persisted in the database");

                return station;
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        @Step("Prepare valid update schedule request")
        private ScheduleRequest createValidUpdateRequest() {
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

        @Step("Create Schedule for test setup")
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
                                "Schedule setup should be persisted in the database");

                return schedule;
        }

        private Response updateSchedule(
                        RequestSpecification request,
                        Long scheduleId,
                        ScheduleRequest schedule) {

                return Allure.step(
                                "PUT /api/schedules/{id}",
                                () -> request
                                                .filter(allureFilter)
                                                .pathParam("id", scheduleId)
                                                .body(schedule))
                                .when()
                                .put(SCHEDULE_BY_ID_ENDPOINT);
        }

        private void assertSuccessfulUpdateScheduleResponse(
                        Response response,
                        ScheduleData originalSchedule,
                        ScheduleRequest expectedSchedule) {
                Allure.step(
                                "Verify successful schedule update response",

                                () -> {
                                        response.then()
                                                        .statusCode(200)
                                                        .body("id",
                                                                        equalTo(originalSchedule.getId().intValue()))
                                                        .body("train.id",
                                                                        equalTo(expectedSchedule.trainId().intValue()))
                                                        .body("originStation.id",
                                                                        equalTo(expectedSchedule.originStationId()
                                                                                        .intValue()))
                                                        .body("destinationStation.id",
                                                                        equalTo(expectedSchedule.destinationStationId()
                                                                                        .intValue()))
                                                        .body("departureTime",
                                                                        equalTo(expectedSchedule.departureTime()
                                                                                        .format(DATE_TIME_FORMATTER)))
                                                        .body("arrivalTime",
                                                                        equalTo(expectedSchedule.arrivalTime()
                                                                                        .format(DATE_TIME_FORMATTER)))
                                                        .body("status", equalTo(ACTIVE_STATUS))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        String priceStr = response.jsonPath().getString("price");
                                        assertNotNull(priceStr, "Price property missing from the JSON payload");
                                        BigDecimal actualPrice = new BigDecimal(priceStr);

                                        assertEquals(
                                                        0,
                                                        actualPrice.compareTo(expectedSchedule.price()),
                                                        "Price should match expected value");

                                        assertResponseTime(response);
                                });
        }

        private Response createSchedule(
                        RequestSpecification request, ScheduleRequest schedule) {
                return Allure.step(
                                "POST /api/schedules",
                                () -> request
                                                .filter(allureFilter)
                                                .body(schedule)
                                                .when()
                                                .post(SCHEDULES_ENDPOINT));
        }

        @Step("Prepare valid schedule data")
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

        private void assertAllRequiredFieldValidationErrors(Response response) {
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

        private void assertScheduleUpdatedInDatabase(
                        ScheduleRequest expectedSchedule,
                        Long scheduleId) {
                Allure.step(
                                "Verify schedule is updated in the database",
                                () -> {

                                        ScheduleData databaseSchedule = DatabaseUtils.getScheduleById(scheduleId);

                                        assertNotNull(
                                                        databaseSchedule,
                                                        "Updated schedule should be persisted in the database");

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

        private void assertScheduleUnchangedInDatabase(
                        ScheduleData expectedSchedule) {

                Allure.step(
                                "Verify schedule data remains unchanged in database",
                                () -> {

                                        ScheduleData actualSchedule = DatabaseUtils.getScheduleById(
                                                        expectedSchedule.getId());

                                        assertNotNull(
                                                        actualSchedule,
                                                        "Existing schedule should remain in the database");

                                        assertAll(
                                                        () -> assertEquals(
                                                                        expectedSchedule.getTrainId(),
                                                                        actualSchedule.getTrainId()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getOriginStationId(),
                                                                        actualSchedule.getOriginStationId()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getDestinationStationId(),
                                                                        actualSchedule.getDestinationStationId()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getDepartureTime(),
                                                                        actualSchedule.getDepartureTime()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getArrivalTime(),
                                                                        actualSchedule.getArrivalTime()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getPrice(),
                                                                        actualSchedule.getPrice()),
                                                        () -> assertEquals(
                                                                        expectedSchedule.getStatus(),
                                                                        actualSchedule.getStatus()));
                                });
        }

        private Response updateSchedule(
                        RequestSpecification request,
                        Long scheduleId,
                        String requestBody) {

                return Allure.step(
                                "PUT /api/schedules/{id} with invalid request format",
                                () -> request
                                                .filter(allureFilter)
                                                .pathParam("id", scheduleId)
                                                .body(requestBody)
                                                .when()
                                                .put(SCHEDULE_BY_ID_ENDPOINT));
        }

        private String requestBodyWithInvalidField(
                        ScheduleRequest valid,
                        String field,
                        String invalidValue) {

                String departureTime = "\"" + valid.departureTime() + "\"";
                String arrivalTime = "\"" + valid.arrivalTime() + "\"";
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

        private static Stream<Arguments> invalidPrices() {
                return Stream.of(
                                Arguments.of(BigDecimal.ZERO, "Price must be greater than 0"),
                                Arguments.of(BigDecimal.valueOf(-1), "Price must be greater than 0"));
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

        @Test
        @DisplayName("Update Schedule - Succeeds with valid data and it is updated in the database")
        void updateScheduleShouldSucceedWithValidData() {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest updatedSchedule = createValidUpdateRequest();

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                updatedSchedule);

                assertSuccessfulUpdateScheduleResponse(response, originalSchedule, updatedSchedule);

                assertScheduleUpdatedInDatabase(
                                updatedSchedule,
                                originalSchedule.getId());
        }

        @Test
        @DisplayName("Update Schedule - Return 403 (Forbidden) when requested by regular user")
        void updateScheduleShouldReturnForbiddenForUserRole() {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest updatedSchedule = createValidUpdateRequest();

                Response response = updateSchedule(
                                requestAsUser(),
                                originalSchedule.getId(),
                                updatedSchedule);

                assertErrorResponse(response, 403, ACCESS_DENIED_MESSAGE);
                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when trainId is empty")
        void updateScheduleShouldReturnBadRequestWhenTrainIdIsEmpty() {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                null,
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertValidationError(
                                response,
                                "trainId",
                                "Train ID is required");

                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when StationId field is empty")
        void updateScheduleShouldReturnBadRequestWhenOriginStationIdIsEmpty() {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                null,
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertValidationError(
                                response,
                                "originStationId",
                                "Origin station ID is required");

                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when destinationStationId field is empty")
        void updateScheduleShouldReturnBadRequestWhenDestinationStationIdIsEmpty() {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                null,
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertValidationError(
                                response,
                                "destinationStationId",
                                "Destination station ID is required");
                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when departureTime field is empty")
        void updateScheduleShouldReturnBadRequestWhenDepartureTimeIsEmpty() {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                null,
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertValidationError(
                                response,
                                "departureTime",
                                "Departure time is required");
                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when arrivalTime field is empty")
        void updateScheduleShouldReturnBadRequestWhenArrivalTimeIsEmpty() {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getDepartureTime(),
                                null,
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertValidationError(
                                response,
                                "arrivalTime",
                                "Arrival time is required");
                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when price field is empty")
        void updateScheduleShouldReturnBadRequestWhenPriceIsEmpty() {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                null);

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertValidationError(
                                response,
                                "price",
                                "Price is required");
                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when all fields are empty")
        void updateScheduleShouldReturnBadRequestWhenAllRequiredFieldsAreEmpty() {
                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null);

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertAllRequiredFieldValidationErrors(response);
                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when originStationId equals destinationStationId")
        void updateScheduleShouldReturnBadRequestWhenOriginEqualsDestination() {
                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertErrorResponse(response, 400, "Origin and destination stations cannot be the same");

                assertScheduleUnchangedInDatabase(originalSchedule);

        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when departureTime is after arrivalTime")
        void updateScheduleShouldReturnBadRequestWhenDepartureAfterArrival() {
                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertErrorResponse(response, 400, "Arrival time must be after departure time");

                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when departureTime is in the past")
        void updateScheduleShouldReturnBadRequestWhenDepartureTimeIsInThePast() {
                ScheduleData originalSchedule = createScheduleForSetup();

                LocalDateTime departure = LocalDateTime.now().minusHours(1);
                LocalDateTime arrival = LocalDateTime.now().plusHours(2);

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                departure,
                                arrival,
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertValidationError(
                                response,
                                "departureTime",
                                "Departure time must be in the future");

                assertScheduleUnchangedInDatabase(originalSchedule);

        }

        @Test
        @DisplayName("Update Schedule - Return 400 (Bad Request) when arrivalTime equals departureTime")
        void updateScheduleShouldReturnBadRequestWhenArrivalEqualsDepartureTime() {
                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertErrorResponse(response, 400, "Arrival time must be after departure time");

                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 409 (Conflict) when updated schedule already exists")
        void updateScheduleShouldReturnConflictWhenUpdatedScheduleAlreadyExists() {

                ScheduleData originalSchedule = createScheduleForSetup();
                ScheduleData ScheduleToUpdate = createScheduleForSetup();

                ScheduleRequest duplicateScheduleRequest = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                ScheduleToUpdate.getId(),
                                duplicateScheduleRequest);

                assertErrorResponse(
                                response,
                                409,
                                "Schedule already exists.");

                assertScheduleUnchangedInDatabase(ScheduleToUpdate);
        }

        @Test
        @DisplayName("Update Schedule - Return 404 (Not Found) when schedule doesn't exist")
        void updateScheduleShouldReturnNotFoundWhenScheduleDoesNotExist() {

                ScheduleRequest updateSchedule = createValidUpdateRequest();

                Response response = updateSchedule(
                                requestAsAdmin(),
                                NON_EXISTENT_SCHEDULE_ID,
                                updateSchedule);

                assertErrorResponse(
                                response,
                                404,
                                "Schedule not found with ID: " + NON_EXISTENT_SCHEDULE_ID);
        }

        @Test
        @DisplayName("Update Schedule - Return 404 (Not Found) when train doesn't exist")
        void updateScheduleShouldReturnNotFoundWhenTrainDoesNotExist() {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                NON_EXISTENT_TRAIN_ID,
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertErrorResponse(
                                response,
                                404,
                                "Train not found with ID: " + NON_EXISTENT_TRAIN_ID);

                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 404 (Not Found) when origin station doesn't exist")
        void updateScheduleShouldReturnNotFoundWhenOriginStationDoesNotExist() {
                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                NON_EXISTENT_ORIGIN_STATION_ID,
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertErrorResponse(response, 404,
                                "Origin station not found with ID: " + NON_EXISTENT_ORIGIN_STATION_ID);

                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 404 (Not Found) when destination station doesn't exist")
        void updateScheduleShouldReturnNotFoundWhenDestinationStationDoesNotExist() {
                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                NON_EXISTENT_DESTINATION_STATION_ID,
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getPrice());

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertErrorResponse(response, 404,
                                "Destination station not found with ID: " + NON_EXISTENT_DESTINATION_STATION_ID);
                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @ParameterizedTest(name = "Price = {0}")
        @MethodSource("invalidPrices")
        @DisplayName("Update Schedule - Return 400 (Bad Request) when request price is not greater than 0")
        void updateScheduleShouldReturnBadRequestWhenPriceIsNotGreaterThanZero(
                        BigDecimal price,
                        String expectedMessage) {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest invalidSchedule = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                price);

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidSchedule);

                assertValidationError(
                                response,
                                "price",
                                expectedMessage);

                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @ParameterizedTest(name = "Invalid {0}: {1}")
        @MethodSource("invalidRequestFormats")
        @DisplayName("Update Schedule - Return 400 (Bad Request) when request format is invalid")
        void updateScheduleShouldReturnBadRequestWhenRequestFormatIsInvalid(
                        String field,
                        String invalidValue) {

                ScheduleData originalSchedule = createScheduleForSetup();

                ScheduleRequest baseRequest = new ScheduleRequest(
                                originalSchedule.getTrainId(),
                                originalSchedule.getOriginStationId(),
                                originalSchedule.getDestinationStationId(),
                                originalSchedule.getDepartureTime(),
                                originalSchedule.getArrivalTime(),
                                originalSchedule.getPrice());

                String invalidJsonBody = requestBodyWithInvalidField(
                                baseRequest,
                                field,
                                invalidValue);

                Response response = updateSchedule(
                                requestAsAdmin(),
                                originalSchedule.getId(),
                                invalidJsonBody);

                assertInvalidRequestFormat(
                                response,
                                field);

                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 401 (Unauthorized) when requested without authentication")
        void updateScheduleShouldReturnUnauthorizedWithoutToken() {

                ScheduleData originalSchedule = createScheduleForSetup();

                Response response = given()
                                .contentType(ContentType.JSON)
                                .accept(ContentType.JSON)
                                .pathParam("id", originalSchedule.getId())
                                .when()
                                .put(SCHEDULE_BY_ID_ENDPOINT);

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 401 (Unauthorized) when requested with invalid token")
        void updateScheduleShouldReturnUnauthorizedWithInvalidToken() {

                ScheduleData originalSchedule = createScheduleForSetup();

                Response response = given()
                                .auth()
                                .oauth2("invalid-token")
                                .contentType(ContentType.JSON)
                                .accept(ContentType.JSON)
                                .pathParam("id", originalSchedule.getId())
                                .when()
                                .put(SCHEDULE_BY_ID_ENDPOINT);

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertScheduleUnchangedInDatabase(originalSchedule);
        }

        @Test
        @DisplayName("Update Schedule - Return 401 (Unauthorized) when requested with expired token")
        void updateScheduleShouldReturnUnauthorizedWithExpiredToken() {

                ScheduleData originalSchedule = createScheduleForSetup();
                String expiredToken = AuthUtils.getExpiredAdminToken();

                Response response = given()
                                .auth()
                                .oauth2(expiredToken)
                                .contentType(ContentType.JSON)
                                .accept(ContentType.JSON)
                                .pathParam("id", originalSchedule.getId())
                                .when()
                                .put(SCHEDULE_BY_ID_ENDPOINT);

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertScheduleUnchangedInDatabase(originalSchedule);
        }
}
