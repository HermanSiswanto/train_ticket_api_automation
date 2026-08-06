package com.herman.automation.train;

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

import com.herman.automation.base.BaseTest;
import com.herman.automation.model.TrainData;
import com.herman.automation.utils.AuthUtils;
import com.herman.automation.utils.DatabaseUtils;
import static com.herman.automation.utils.TestDataGenerator.generateAlphabeticTrainCode;
import static com.herman.automation.utils.TestDataGenerator.generateNumericTrainCode;
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
@Feature("Train API")
@Story("Update Train")
@DisplayName("Update Train Test")
public class UpdateTrainTest extends BaseTest {

        private static final String TRAINS_ENDPOINT = "/api/trains";
        private static final String TRAIN_BY_ID_ENDPOINT = "/api/trains/{id}";

        private static final String ACTIVE_STATUS = "ACTIVE";
        private static final String ACCESS_DENIED_MESSAGE = "Access denied";
        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_TRAIN_ID = Long.MAX_VALUE;

        private String adminToken;
        private String userToken;

        private record TrainRequest(String trainCode, String trainName ){}

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

        private Response updateTrain(
                        RequestSpecification requestSpec,
                        Long trainId,
                        TrainRequest request) {
                return Allure.step(
                                "PUT /api/trains/{id}",
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .pathParam("id", trainId)
                                                .body(request)
                                                .when()
                                                .put(TRAIN_BY_ID_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertSuccessfulTrainUpdateResponse(
                        Response response,
                        TrainData originalTrain,
                        TrainRequest expectedTrain) {
                Allure.step(
                                "Verify successful train update response",

                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200)
                                                        .body("id", equalTo(originalTrain.getId().intValue()))
                                                        .body("trainCode", equalTo(expectedTrain.trainCode()))
                                                        .body("trainName", equalTo(expectedTrain.trainName()))
                                                        .body("status", equalTo(ACTIVE_STATUS))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        assertResponseTime(response);
                                });
        }


        private TrainRequest validUpdateRequest(String codeType) {
                return new TrainRequest(
                                generateTrainCode(codeType),
                                "Updated " + generateUniqueTrainName());
        }

        private TrainRequest validUpdateRequest() {
                return validUpdateRequest("UNIQUE");
        }

        private void assertTrainUpdatedInDatabase(
                        TrainData originalTrain,
                        TrainRequest expectedTrain) {
                Allure.step(
                                "Verify train is updated in database",
                                () -> {

                                        TrainData actualTrain = DatabaseUtils.getTrainById(
                                                        originalTrain.getId());

                                        assertNotNull(
                                                        actualTrain,
                                                        "Updated train should exist in the database");

                                        assertAll(
                                                        () -> assertEquals(
                                                                        originalTrain.getId(),
                                                                        actualTrain.getId()),
                                                        () -> assertEquals(
                                                                        expectedTrain.trainCode(),
                                                                        actualTrain.getTrainCode()),
                                                        () -> assertEquals(
                                                                        expectedTrain.trainName(),
                                                                        actualTrain.getTrainName()),
                                                        () -> assertEquals(
                                                                        ACTIVE_STATUS,
                                                                        actualTrain.getStatus()));
                                });
        }

        private void assertTrainUnchangedInDatabase(TrainData expectedTrain) {
                Allure.step(
                                "Verify train data remains unchanged in database",
                                () -> {
                                        TrainData actualTrain = DatabaseUtils.getTrainById(
                                                        expectedTrain.getId());

                                        assertNotNull(
                                                        actualTrain,
                                                        "Existing train should remain in the database");

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
                                                                        expectedTrain.getStatus(),
                                                                        actualTrain.getStatus()));
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

        private void assertAllRequiredTrainFieldsValidationErrors(Response response) {
                Allure.step(
                                "Verify validation errors for all required fields",
                                () -> verifyValidationErrors(
                                                response,
                                                Map.of(
                                                                "trainCode", "Train code is required",
                                                                "trainName", "Train name is required"
                                                )

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

        private static Stream<Arguments> validTrainCodeTypes() {
                return Stream.of(
                                Arguments.of("unique alphanumeric code", "UNIQUE"),
                                Arguments.of("numeric code", "NUMERIC"),
                                Arguments.of("alphabetic code", "ALPHABETIC"));
        }

        private static Stream<Arguments> invalidTrainCodeTestData() {
                String invalidCharacterMessage = "Train code may only contain alphanumeric characters without spaces";

                return Stream.of(
                                Arguments.of("TRAIN 01", invalidCharacterMessage),
                                Arguments.of("TR@123", invalidCharacterMessage));
        }

        private String generateTrainCode(String codeType) {
                return switch (codeType) {
                        case "NUMERIC" -> generateNumericTrainCode();
                        case "ALPHABETIC" -> generateAlphabeticTrainCode();
                        default -> generateUniqueTrainCode();
                };
        }

        //===TESTS===

        @ParameterizedTest(name = "Update train successfully with {0}")
        @MethodSource("validTrainCodeTypes")
        @DisplayName("Update Train - Succeeds with valid data and is updated in the database")
        void updateTrainShouldSucceedWithValidTrainCode(
                        String scenario,
                        String codeType) {
                TrainData originalTrain = createTrainForSetup();

                RequestSpecification adminRequest = requestAsAdmin();
                TrainRequest updatedTrain = new TrainRequest(
                                generateTrainCode(codeType),
                                "Updated " + generateUniqueTrainName());

                Response response = updateTrain(
                                adminRequest,
                                originalTrain.getId(),
                                updatedTrain);

                assertSuccessfulTrainUpdateResponse(response, originalTrain, updatedTrain);
                assertTrainUpdatedInDatabase(originalTrain, updatedTrain);
        }

        @Test
        @DisplayName("Update Train - Return 403 (Forbidden) when requested by regular user")
        void updateTrainShouldReturnForbiddenWhenRequestedByUser() {
                TrainData originalTrain = createTrainForSetup();
                RequestSpecification userRequest = requestAsUser();

                TrainRequest updatedTrain = new TrainRequest(
                                generateUniqueTrainCode(),
                                "Updated " + generateUniqueTrainName());

                Response response = updateTrain(
                                userRequest,
                                originalTrain.getId(),
                                updatedTrain);

                assertErrorResponse(response, 403, ACCESS_DENIED_MESSAGE);
                assertTrainUnchangedInDatabase(originalTrain);
        }

        @Test
        @DisplayName("Update Train - Return 400 (Bad Request) when train code is empty")
        void updateTrainShouldReturnBadRequestWhenTrainCodeIsEmpty() {
                TrainData originalTrain = createTrainForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                Response response = updateTrain(
                                adminRequest,
                                originalTrain.getId(),
                                new TrainRequest("", "Updated " + generateUniqueTrainName()));

                assertValidationError(
                                response,
                                "trainCode",
                                "Train code is required");

                assertTrainUnchangedInDatabase(originalTrain);
        }

        @Test
        @DisplayName("Update Train - Return 400 (Bad Request) when train name is empty")
        void updateTrainShouldReturnBadRequestWhenTrainNameIsEmpty() {
                TrainData originalTrain = createTrainForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                Response response = updateTrain(
                                adminRequest,
                                originalTrain.getId(),
                                new TrainRequest(generateUniqueTrainCode(), ""));

                assertValidationError(
                                response,
                                "trainName",
                                "Train name is required");

                assertTrainUnchangedInDatabase(originalTrain);
        }

        @Test
        @DisplayName("Update Train - Return 400 (Bad Request) when all fields are empty")
        void updateTrainShouldReturnBadRequestWhenAllFieldsAreEmpty() {
                TrainData originalTrain = createTrainForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                Response response = updateTrain(
                                adminRequest,
                                originalTrain.getId(),
                                new TrainRequest("", ""));

                assertAllRequiredTrainFieldsValidationErrors(response);
                assertTrainUnchangedInDatabase(originalTrain);
        }

        @ParameterizedTest(name = "Update train should fail with invalid code: {0}")
        @MethodSource("invalidTrainCodeTestData")
        @DisplayName("Update Train - Return 400 (Bad Request) when requested with invalid train code")
        void updateTrainShouldReturnBadRequestWithInvalidTrainCode(
                        String invalidTrainCode,
                        String expectedMessage) {
                TrainData originalTrain = createTrainForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                Response response = updateTrain(
                                adminRequest,
                                originalTrain.getId(),
                                new TrainRequest(
                                                invalidTrainCode,
                                                "Updated " + generateUniqueTrainName()));

                assertValidationError(response, "trainCode", expectedMessage);
                assertTrainUnchangedInDatabase(originalTrain);
        }

        @Test
        @DisplayName("Update Train - Return 400 (Bad Request) when train code exceeds maximum length")
        void updateTrainShouldReturnBadRequestWhenTrainCodeExceedsMaximumLength() {
                TrainData originalTrain = createTrainForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                Response response = updateTrain(
                                adminRequest,
                                originalTrain.getId(),
                                new TrainRequest(
                                                "K".repeat(21),
                                                generateUniqueTrainName()

                                ));

                assertValidationError(
                                response,
                                "trainCode",
                                "Train code must not exceed 20 characters");

                assertTrainUnchangedInDatabase(originalTrain);
        }

        @Test
        @DisplayName("Update Train - Return 400 (Bad Request) when train name exceeds maximum length")
        void updateTrainShouldReturnBadRequestWhenTrainNameExceedsMaximumLength() {
                TrainData originalTrain = createTrainForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                Response response = updateTrain(
                                adminRequest,
                                originalTrain.getId(),
                                new TrainRequest(
                                                generateUniqueTrainCode(),
                                                "A".repeat(101)));

                assertValidationError(
                                response,
                                "trainName",
                                "Train name must not exceed 100 characters");

                assertTrainUnchangedInDatabase(originalTrain);
        }

        @Test
        @DisplayName("Update Train - Return 404 (Not Found) when train id doesn't exist")
        void updateTrainShouldReturnNotFoundWhenTrainDoesNotExist() {
                TrainRequest updatedTrain = validUpdateRequest();
                RequestSpecification adminRequest = requestAsAdmin();

                Response response = updateTrain(
                                adminRequest,
                                NON_EXISTENT_TRAIN_ID,
                                updatedTrain);

                assertErrorResponse(response, 404, "Train not found with ID: " + NON_EXISTENT_TRAIN_ID);

        }

        @Test
        @DisplayName("Update Train - Return 409 (Conflict) when train code already exists")
        void updateTrainShouldReturnConflictWhenTrainCodeAlreadyExists() {
                TrainData originalTrain = createTrainForSetup();
                TrainData existingTrain = createTrainForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                TrainRequest updatedTrain = new TrainRequest(
                                existingTrain.getTrainCode(),
                                "Update " + generateUniqueTrainName());

                Response response = updateTrain(
                                adminRequest,
                                originalTrain.getId(),
                                updatedTrain);

                assertErrorResponse(
                                response,
                                409,
                                "Train code already exists");

                assertTrainUnchangedInDatabase(originalTrain);
                assertTrainUnchangedInDatabase(existingTrain);
        }

        @Test
        @DisplayName("Update Train - Return 401 (Unauthorized) when requested without authentication")
        void updateTrainShouldReturnUnauthorizedWithoutToken() {
                TrainData originalTrain = createTrainForSetup();

                Response response = updateTrain(
                                given()
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                originalTrain.getId(),
                                validUpdateRequest());

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
                assertTrainUnchangedInDatabase(originalTrain);
        }

        @Test
        @DisplayName("Update Train - Return 401 (Unauthorized) when requested with invalid token")
        void updateTrainShouldReturnUnauthorizedWithInvalidToken() {
                TrainData originalTrain = createTrainForSetup();

                Response response = updateTrain(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                originalTrain.getId(),
                                validUpdateRequest());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertTrainUnchangedInDatabase(originalTrain);
        }

        @Test
        @DisplayName("Update Train - Return 401 (Unauthorized) when requested with expired token")
        void updateTrainShouldReturnUnauthorizedWithExpiredToken() {
                TrainData originalTrain = createTrainForSetup();
                String expiredToken = AuthUtils.getExpiredAdminToken();

                Response response = updateTrain(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                originalTrain.getId(),
                                validUpdateRequest());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
                assertTrainUnchangedInDatabase(originalTrain);
        }
}