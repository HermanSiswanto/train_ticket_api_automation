package com.herman.automation.schedule;

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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Epic("Train Ticket API")
@Feature("Schedule API")
@Story("Delete Schedule")
@DisplayName("Delete Schedule Test")
public class DeleteScheduleTest extends BaseTest {
        private static final String TRAINS_ENDPOINT = "/api/trains";
        private static final String STATIONS_ENDPOINT = "/api/stations";
        private static final String SCHEDULES_ENDPOINT = "/api/schedules";
        private static final String SCHEDULE_BY_ID_ENDPOINT = "/api/schedules/{id}";

        private static final String ACCESS_DENIED_MESSAGE = "Access denied";
        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_SCHEDULE_ID = Long.MAX_VALUE;

        private String adminToken;
        private String userToken;

        private record StationRequest(
                        String stationCode,
                        String stationName,
                        String city){
        }

        private record TrainRequest(
                        String trainCode,
                        String trainName){
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
                assertNotNull(trainId,"Extracted ID from API response should not be null");

                TrainData databaseTrain = DatabaseUtils.getTrainById(trainId);
                assertNotNull(
                                databaseTrain,
                                "Train setup should be persisted in the database");

                return databaseTrain;
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

        @Step("Create schedule for test setup")
        private ScheduleData createScheduleForSetup() {

                RequestSpecification adminRequest = requestAsAdmin();
                TrainData train = createTrainForSetup(adminRequest);
                StationData originStation = createStationForSetup(adminRequest, "origin");
                StationData destinationStation = createStationForSetup(adminRequest, "destination");
                LocalDateTime departureTime = generateDepartureTime();

                Map<String, Object> body = new HashMap<>();

                body.put("trainId", train.getId());
                body.put("originStationId", originStation.getId());
                body.put("destinationStationId", destinationStation.getId());
                body.put("departureTime", departureTime.toString());
                body.put(
                                "arrivalTime",
                                generateArrivalTime(departureTime).toString());
                body.put("price", generatePrice());

                Response response = adminRequest
                                .body(body)
                                .when()
                                .post(SCHEDULES_ENDPOINT)
                                .then()
                                .log().ifValidationFails()
                                .statusCode(201)
                                .extract()
                                .response();

                Long scheduleId = response.jsonPath().getLong("id");
                assertNotNull(scheduleId, "Extracted ID from API response should not be null");

                ScheduleData expectedSchedule = DatabaseUtils.getScheduleById(scheduleId);

                assertNotNull(expectedSchedule, "Schedule setup should persisted in the database");

                return expectedSchedule;
        }

        private Response deleteSchedule(
                        RequestSpecification requestSpec,
                        Long scheduleId) {
                return Allure.step(
                                "DELETE "+ SCHEDULES_ENDPOINT + "/" + scheduleId,
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .pathParam("id", scheduleId)
                                                .when()
                                                .delete(SCHEDULE_BY_ID_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertDeletedSuccessfulResponse(Response response) {
                Allure.step(
                                "Verify successful schedule deletion",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(204);

                                        assertResponseTime(response);
                                });
        }

        private void assertScheduleDeletedFromDatabase(Long scheduleId) {
                Allure.step(
                                "Verify schedule is deleted from database",
                                () -> {
                                        ScheduleData expectedSchedule = DatabaseUtils.getScheduleById(scheduleId);
                                        assertNull(
                                                        expectedSchedule,
                                                        "Deleted schedule should no longer exist in the database");
                                });
        }

        private void assertScheduleStillExistsInDatabase(Long scheduleId) {
                Allure.step(
                                "Verify schedule remains in database",
                                () -> {
                                        ScheduleData expectedSchedule = DatabaseUtils.getScheduleById(scheduleId);
                                        assertNotNull(
                                                        expectedSchedule,
                                                        "Schedule should remain in the database after a failed delete");
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

        @Test
        @DisplayName("Delete Schedule - Succeeds with valid data and is deleted from the database")
        void deleteScheduleShouldSucceed() {
                ScheduleData expectedSchedule = createScheduleForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                Response response = deleteSchedule(
                                adminRequest,
                                expectedSchedule.getId());

                assertDeletedSuccessfulResponse(response);
                assertScheduleDeletedFromDatabase(expectedSchedule.getId());
        }

        @Test
        @DisplayName("Delete Schedule - Return 403 (Forbidden) when requested by a regular user and it remains in the database")
        void deleteScheduleShouldReturnForbiddenWhenRequestedByUser() {
                ScheduleData expectedSchedule = createScheduleForSetup();
                RequestSpecification userRequest = requestAsUser();
                Response response = deleteSchedule(
                                userRequest,
                                expectedSchedule.getId());

                assertErrorResponse(response, 403, ACCESS_DENIED_MESSAGE);
                assertScheduleStillExistsInDatabase(expectedSchedule.getId());
        }

        @Test
        @DisplayName("Delete Schedule - Return 404 (Not Found) when schedule doesn't exist")
        void deleteScheduleShouldReturnNotFoundWhenScheduleDoesNotExist() {
                RequestSpecification adminRequest = requestAsAdmin();
                Response response = deleteSchedule(
                                adminRequest,
                                NON_EXISTENT_SCHEDULE_ID);

                assertErrorResponse(
                                response,
                                404,
                                "Schedule not found with ID: " + NON_EXISTENT_SCHEDULE_ID);
        }

        @Test
        @DisplayName("Delete Schedule - Return 401 (Unauthorized) when requested without authentication")
        void deleteScheduleShouldReturn401WithoutToken() {
                ScheduleData expectedSchedule = createScheduleForSetup();

                Response response = deleteSchedule(
                                given()
                                        .contentType(ContentType.JSON)
                                        .accept(ContentType.JSON),
                                expectedSchedule.getId());

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
                assertScheduleStillExistsInDatabase(expectedSchedule.getId());
        }

        @Test
        @DisplayName("Delete Schedule - Return 401 (Unauthorized) when requested with invalid token")
        void deleteScheduleShouldReturnUnauthorizedWithInvalidToken() {
                ScheduleData expectedSchedule = createScheduleForSetup();

                Response response = deleteSchedule(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                expectedSchedule.getId());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertScheduleStillExistsInDatabase(expectedSchedule.getId());
        }

        @Test
        @DisplayName("Delete Schedule - Return 401 (Unauthorized) when requested with expired token")
        void deleteScheduleShouldReturnUnauthorizedWithExpiredToken() {
                ScheduleData expectedSchedule = createScheduleForSetup();
                String expiredToken = AuthUtils.getExpiredAdminToken();

                Response response = deleteSchedule(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                expectedSchedule.getId());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertScheduleStillExistsInDatabase(expectedSchedule.getId());
        }
}