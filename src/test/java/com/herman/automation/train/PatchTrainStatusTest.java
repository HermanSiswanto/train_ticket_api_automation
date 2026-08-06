package com.herman.automation.train;

import com.herman.automation.base.BaseTest;
import com.herman.automation.model.TrainData;
import com.herman.automation.utils.AuthUtils;
import com.herman.automation.utils.DatabaseUtils;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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
import io.qameta.allure.Allure;
import io.qameta.allure.Step;

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
@Feature("Train API")
@Story("Patch Train Status")
@DisplayName("Patch Train Status Test")
public class PatchTrainStatusTest extends BaseTest {

        private static final String TRAINS_ENDPOINT = "/api/trains";
        private static final String TRAIN_STATUS_ENDPOINT = "/api/trains/{id}/status";

        private static final String ACTIVE_STATUS = "ACTIVE";
        private static final String INACTIVE_STATUS = "INACTIVE";

        private static final String ACCESS_DENIED_MESSAGE = "Access denied";
        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_TRAIN_ID = Long.MAX_VALUE;

        private String adminToken;
        private String userToken;

        private record TrainRequest(String trainCode, String trainName) {
        }

        private record PatchTrainStatusRequest(
                        String status) {
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

        @Step("Create train for test setup")
        private TrainData createTrainForSetup() {
                TrainRequest request = new TrainRequest(
                                generateUniqueTrainCode(),
                                generateUniqueTrainName());

                Response response = requestAsAdmin()
                                .body(request)
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

                TrainData databaseTrain = DatabaseUtils.getTrainById(trainId);
                assertNotNull(
                                databaseTrain,
                                "Train setup should be persisted in the database");

                return databaseTrain;
        }

        private Response patchTrainStatus(
                        RequestSpecification requestSpec,
                        Long trainId,
                        PatchTrainStatusRequest request) {
                return Allure.step(
                                "PATCH /api/trains/" + trainId + "/status — ",
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .pathParam("id", trainId)
                                                .body(request)
                                                .when()
                                                .patch(TRAIN_STATUS_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertSuccessfulTrainStatusUpdateResponse(
                        Response response,
                        TrainData expectedTrain,
                        String expectedStatus) {
                Allure.step(
                                "Verify successful train status update response",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200)
                                                        .body("id", equalTo(expectedTrain.getId().intValue()))
                                                        .body("trainCode", equalTo(expectedTrain.getTrainCode()))
                                                        .body("trainName", equalTo(expectedTrain.getTrainName()))
                                                        .body("status", equalTo(expectedStatus))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        assertResponseTime(response);
                                });
        }

        private void assertTrainStatusInDatabase(
                        TrainData expectedTrain,
                        String expectedStatus) {

                Allure.step(
                                "Verify train status in database is " + expectedStatus,
                                () -> {

                                        TrainData actualTrain = DatabaseUtils.getTrainById(
                                                        expectedTrain.getId());

                                        assertNotNull(
                                                        actualTrain,
                                                        "Train should exist in the database");

                                        assertAll(
                                                        () -> assertEquals(
                                                                        expectedTrain.getId(),
                                                                        actualTrain.getId()),
                                                        () -> assertEquals(
                                                                        expectedTrain.getTrainCode(),
                                                                        actualTrain.getTrainCode()),
                                                        () -> assertEquals(
                                                                        expectedTrain.getTrainName(),
                                                                        actualTrain.getTrainName()),
                                                        () -> assertEquals(
                                                                        expectedStatus,
                                                                        actualTrain.getStatus()));
                                });
        }

        private void changeTrainStatusAsAdmin(
                        TrainData train,
                        String targetStatus) {
                Allure.step(
                                "Set train status to " + targetStatus + " for test setup",
                                () -> {

                                        PatchTrainStatusRequest request = new PatchTrainStatusRequest(targetStatus);
                                        Response response = patchTrainStatus(
                                                        requestAsAdmin(),
                                                        train.getId(),
                                                        request);

                                        assertSuccessfulTrainStatusUpdateResponse(response, train, targetStatus);

                                        assertTrainStatusInDatabase(
                                                        train,
                                                        targetStatus);
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

        private static Stream<Arguments> validStatusTransitions() {
                return Stream.of(
                                Arguments.of(
                                                "Update ACTIVE train to INACTIVE",
                                                ACTIVE_STATUS,
                                                INACTIVE_STATUS),
                                Arguments.of(
                                                "Update INACTIVE train to ACTIVE",
                                                INACTIVE_STATUS,
                                                ACTIVE_STATUS));
        }

        private static Stream<Arguments> sameStatusTestData() {
                return Stream.of(
                                Arguments.of(ACTIVE_STATUS, "Train is already ACTIVE"),
                                Arguments.of(INACTIVE_STATUS, "Train is already INACTIVE"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("validStatusTransitions")
        @DisplayName("Patch Train Status - Succeeds update train status and is changed in the database")
        void patchTrainStatusShouldSucceed(
                        String scenario,
                        String initialStatus,
                        String targetStatus) {
                TrainData train = createTrainForSetup();

                if (INACTIVE_STATUS.equals(initialStatus)) {
                        changeTrainStatusAsAdmin(train, INACTIVE_STATUS);
                }
                PatchTrainStatusRequest request = new PatchTrainStatusRequest(targetStatus);
                Response response = patchTrainStatus(
                                requestAsAdmin(),
                                train.getId(),
                                request);

                assertSuccessfulTrainStatusUpdateResponse(response, train, targetStatus);
                assertTrainStatusInDatabase(train, targetStatus);
        }

        @ParameterizedTest(name = "Patch train status to same status: {0}")
        @MethodSource("sameStatusTestData")
        void patchTrainStatusShouldReturnBadRequestWhenStatusIsAlreadySet(
                        String currentStatus,
                        String expectedMessage) {

                TrainData train = createTrainForSetup();

                if (INACTIVE_STATUS.equals(currentStatus)) {
                        changeTrainStatusAsAdmin(train, INACTIVE_STATUS);
                }

                PatchTrainStatusRequest request = new PatchTrainStatusRequest(currentStatus);

                Response response = patchTrainStatus(
                                requestAsAdmin(),
                                train.getId(),
                                request);

                assertErrorResponse(response, 400, expectedMessage);
                assertTrainStatusInDatabase(train, currentStatus);
        }

        @Test
        @DisplayName("Patch Train Status - Return 403 (Forbidden) when requested by regular user")
        void patchTrainStatusShouldReturnForbiddenWhenRequestedByUser() {
                TrainData train = createTrainForSetup();
                PatchTrainStatusRequest request = new PatchTrainStatusRequest(INACTIVE_STATUS);
                Response response = patchTrainStatus(
                                requestAsUser(),
                                train.getId(),
                                request);

                assertErrorResponse(response, 403, ACCESS_DENIED_MESSAGE);
                assertTrainStatusInDatabase(train, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Train Status - Return 404 (Not Found) when train does not exist")
        void patchTrainStatusShouldReturnNotFoundWhenTrainDoesNotExist() {
                PatchTrainStatusRequest request = new PatchTrainStatusRequest(INACTIVE_STATUS);

                Response response = patchTrainStatus(
                                requestAsAdmin(),
                                NON_EXISTENT_TRAIN_ID,
                                request);

                assertErrorResponse(
                                response,
                                404,
                                "Train not found with ID: " + NON_EXISTENT_TRAIN_ID);
        }

        @Test
        @DisplayName("Patch Train Status - Return 400 (Bad Request) when patching with invalid status ")
        void patchTrainStatusShouldReturnBadRequestForInvalidStatus() {
                TrainData train = createTrainForSetup();

                PatchTrainStatusRequest request = new PatchTrainStatusRequest("DISABLE");

                Response response = patchTrainStatus(
                                requestAsAdmin(),
                                train.getId(),
                                request);

                assertErrorResponse(
                                response,
                                400,
                                "Status must be either ACTIVE or INACTIVE");

                assertTrainStatusInDatabase(train, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Train Status - Return 401 (Unauthorized) when requested without authentication")
        void patchTrainStatusShouldReturnUnauthorizedWithoutToken() {
                TrainData train = createTrainForSetup();
                PatchTrainStatusRequest request = new PatchTrainStatusRequest(INACTIVE_STATUS);
                Response response = patchTrainStatus(
                                given()
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                train.getId(),
                                request);

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
                assertTrainStatusInDatabase(train, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Train Status - Return 401 (Unauthorized) when requested with invalid token")
        void patchTrainStatusShouldReturnUnauthorizedWithInvalidToken() {
                TrainData train = createTrainForSetup();
                PatchTrainStatusRequest request = new PatchTrainStatusRequest(INACTIVE_STATUS);
                Response response = patchTrainStatus(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                train.getId(),
                                request);

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertTrainStatusInDatabase(train, ACTIVE_STATUS);
        }

        @Test
        @DisplayName("Patch Train Status - Return 401 (Unauthorized) when requested with expired token")
        void patchTrainStatusShouldReturnUnauthorizedWithExpiredAdminToken() {
                TrainData train = createTrainForSetup();
                String expiredToken = AuthUtils.getExpiredAdminToken();
                PatchTrainStatusRequest request = new PatchTrainStatusRequest(INACTIVE_STATUS);

                Response response = patchTrainStatus(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                train.getId(),
                                request);

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertTrainStatusInDatabase(train, ACTIVE_STATUS);
        }
}