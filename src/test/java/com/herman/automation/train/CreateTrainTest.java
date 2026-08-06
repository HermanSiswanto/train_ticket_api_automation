package com.herman.automation.train;

import com.herman.automation.base.BaseTest;
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

import java.util.Map;
import java.util.stream.Stream;

import static com.herman.automation.utils.TestDataGenerator.generateAlphabeticTrainCode;
import static com.herman.automation.utils.TestDataGenerator.generateNumericTrainCode;
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
@Story("Create Train")
@DisplayName("Create Train Test")
public class CreateTrainTest extends BaseTest {

        private static final String TRAINS_ENDPOINT = "/api/trains";
        private static final long MAX_RESPONSE_TIME_MS = 1_000L;

        private static final String ACTIVE_STATUS = "ACTIVE";
        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
        private static final String ACCESS_DENIED_MESSAGE = "Access denied";

        private String adminToken;
        private String userToken;

        private record TrainRequest(String trainCode, String trainName) {
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

        private Response createTrain(RequestSpecification requestSpec, TrainRequest request) {
                return Allure.step(
                                "POST /api/trains",
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .body(request)
                                                .when()
                                                .post(TRAINS_ENDPOINT));

        }

        @Step("Prepare valid train data")
        private TrainRequest validTrain() {
                return validTrain(generateUniqueTrainCode());
        }

        private TrainRequest validTrain(String trainCode) {
                return new TrainRequest(
                                trainCode,
                                generateUniqueTrainName());
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertCreatedTrain(
                        Response response,
                        TrainRequest expectedTrain) {
                Allure.step(
                                "Verify successful train creation response",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(201)
                                                        .body("id", notNullValue())
                                                        .body("trainCode", equalTo(expectedTrain.trainCode()))
                                                        .body("trainName", equalTo(expectedTrain.trainName()))
                                                        .body("status", equalTo(ACTIVE_STATUS))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        assertResponseTime(response);
                                });
        }

        private void assertTrainPersistedInDatabase(TrainRequest expectedTrain) {

                Allure.step(
                                "Verify train is persisted in database",
                                () -> {
                                        TrainData databaseTrain = DatabaseUtils
                                                        .getTrainByCode(expectedTrain.trainCode());

                                        assertNotNull(
                                                        databaseTrain,
                                                        "Created train should be persisted in the database");

                                        assertAll(
                                                        () -> assertEquals(
                                                                        expectedTrain.trainCode(),
                                                                        databaseTrain.getTrainCode()),
                                                        () -> assertEquals(
                                                                        expectedTrain.trainName(),
                                                                        databaseTrain.getTrainName()),
                                                        () -> assertEquals(
                                                                        ACTIVE_STATUS,
                                                                        databaseTrain.getStatus()));
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

        private static Stream<Arguments> validTrainRequests() {
                return Stream.of(
                                Arguments.of(
                                                "unique alphanumeric code",
                                                new TrainRequest(
                                                                generateUniqueTrainCode(),
                                                                generateUniqueTrainName())),
                                Arguments.of(
                                                "numeric code",
                                                new TrainRequest(
                                                                generateNumericTrainCode(),
                                                                generateUniqueTrainName())),
                                Arguments.of(
                                                "alphabetic code",
                                                new TrainRequest(
                                                                generateAlphabeticTrainCode(),
                                                                generateUniqueTrainName())));
        }

        private static Stream<Arguments> invalidTrainCodeRequests() {
                String invalidMessage = "Train code may only contain alphanumeric characters without spaces";

                return Stream.of(
                                Arguments.of("AP 001", invalidMessage),
                                Arguments.of("AP@001", invalidMessage),
                                Arguments.of(
                                                "ABCDEFGHIJKLMNOPQRSTU",
                                                "Train code must not exceed 20 characters"));
        }

        @ParameterizedTest(name = "Create train successfully with {0}")
        @MethodSource("validTrainRequests")
        @DisplayName("Create Train - Succeeds with valid data and the data is persisted in the database")
        void createTrainShouldSucceedAndPersistData(
                        String scenario,
                        TrainRequest train) {
                RequestSpecification adminRequest = requestAsAdmin();
                Response response = createTrain(adminRequest, train);
                assertCreatedTrain(response, train);
                assertTrainPersistedInDatabase(train);
        }

        @Test
        @DisplayName("Create Train - Return 400 (Bad Request) when train code is empty")
        void createTrainShouldReturnBadRequestWhenTrainCodeIsEmpty() {
                TrainRequest train = new TrainRequest("", generateUniqueTrainName());
                Response response = createTrain(requestAsAdmin(), train);

                assertValidationError(
                                response,
                                "trainCode",
                                "Train code is required");
        }

        @Test
        @DisplayName("Create Train - Return 400 (Bad Request) when train name is empty")
        void createTrainShouldReturnBadRequestWhenTrainNameIsEmpty() {
                RequestSpecification adminRequest = requestAsAdmin();
                TrainRequest train = new TrainRequest(generateUniqueTrainCode(), "");

                Response response = createTrain(adminRequest, train);

                assertValidationError(
                                response,
                                "trainName",
                                "Train name is required");
        }

        @Test
        @DisplayName("Create Train - Return 400 (Bad Request) when All fields are empty")
        void createTrainShouldReturnBadRequestWhenAllFieldsAreEmpty() {
                RequestSpecification adminRequest = requestAsAdmin();
                TrainRequest train = new TrainRequest("", "");

                Response response = createTrain(adminRequest, train);

                assertAllRequiredTrainFieldsValidationErrors(response);
                assertResponseTime(response);
        }

        @Test
        @DisplayName("Create Train - Return 409 (Conflict) when train code already exists")
        void createTrainShouldReturnConflictWhenTrainCodeAlreadyExists() {
                String duplicateTrainCode = generateUniqueTrainCode();
                RequestSpecification adminRequest = requestAsAdmin();

                TrainRequest existingTrain = validTrain(duplicateTrainCode);
                TrainRequest duplicateTrain = validTrain(duplicateTrainCode);

                Response createResponse = createTrain(
                                adminRequest,
                                existingTrain);

                assertCreatedTrain(createResponse, existingTrain);
                assertTrainPersistedInDatabase(existingTrain);

                Response duplicateResponse = createTrain(
                                adminRequest,
                                duplicateTrain);

                assertErrorResponse(
                                duplicateResponse,
                                409,
                                "Train code already exists");
        }

        @ParameterizedTest(name = "Create train should fail with invalid code: {0}")
        @MethodSource("invalidTrainCodeRequests")
        @DisplayName("Create Train - Return 400 (Bad Request) when requested with invalid train code")
        void createTrainShouldReturnBadRequestForInvalidTrainCode(
                        String invalidTrainCode,
                        String expectedMessage) {
                RequestSpecification adminRequest = requestAsAdmin();
                TrainRequest train = new TrainRequest(
                                invalidTrainCode,
                                generateUniqueTrainName());

                Response response = createTrain(adminRequest, train);

                assertValidationError(response, "trainCode", expectedMessage);
        }

        @Test
        @DisplayName("Create Train - Return 400 (Bad Request) when train name exceeds maximum length")
        void createTrainShouldReturnBadRequestWhenTrainNameExceedsMaximumLength() {
                RequestSpecification adminRequest = requestAsAdmin();
                TrainRequest train = new TrainRequest(
                                generateUniqueTrainCode(),
                                "A".repeat(101));

                Response response = createTrain(adminRequest, train);

                assertValidationError(
                                response,
                                "trainName",
                                "Train name must not exceed 100 characters");
        }

        @Test
        @DisplayName("Create Train - Return 403 (Forbidden) when requested by regular user")
        void createTrainShouldReturnForbiddenWhenRequestedByUser() {
                RequestSpecification userRequest = requestAsUser();
                Response response = createTrain(userRequest, validTrain());

                assertErrorResponse(response, 403, ACCESS_DENIED_MESSAGE);
        }

        @Test
        @DisplayName("Create Train - Return 401 (Unauthorized) when requested without authentication")
        void createTrainShouldReturnUnauthorizedWithoutToken() {
                Response response = createTrain(
                                given()
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                validTrain());

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
        }

        @Test
        @DisplayName("Create Train - Return 401 (Unauthorized) when requested with invalid token")
        void createTrainShouldReturnUnauthorizedWithInvalidToken() {
                Response response = createTrain(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                validTrain());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Create Train - Return 401 (Unauthorized) when requested with expired token")
        void createTrainShouldReturnUnauthorizedWithExpiredToken() {
                String expiredToken = AuthUtils.getExpiredAdminToken();

                Response response = createTrain(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                validTrain());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }
}