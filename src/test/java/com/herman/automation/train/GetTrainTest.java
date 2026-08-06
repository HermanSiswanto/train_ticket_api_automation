package com.herman.automation.train;

import com.herman.automation.base.BaseTest;
import com.herman.automation.model.TrainData;
import com.herman.automation.utils.AuthUtils;
import com.herman.automation.utils.DatabaseUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static com.herman.automation.utils.TestDataGenerator.generateUniqueTrainCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueTrainName;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Epic("Train Ticket API")
@Feature("Train API")
@Story("Get Train")
@DisplayName("Get Train Test")
public class GetTrainTest extends BaseTest {

        private static final String TRAINS_ENDPOINT = "/api/trains";
        private static final String TRAIN_BY_ID_ENDPOINT = "/api/trains/{id}";

        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_TRAIN_ID = Long.MAX_VALUE;

        private String adminToken;
        private String userToken;

        private record TrainRequest(String trainCode, String trainName) {
        }

        private enum Role {
                ADMIN,
                USER
        }

        @BeforeEach
        void setUp() {
                adminToken = AuthUtils.loginAsAdmin();
                userToken = AuthUtils.loginAsUser();
        }

        private static Stream<Arguments> authorizedRoles() {
                return Stream.of(
                                Arguments.of(Role.ADMIN),
                                Arguments.of(Role.USER));
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

        @Step("Create train for test setup")
        private TrainData createTrainForSetup() {
                TrainRequest request = new TrainRequest(
                                generateUniqueTrainCode(), 
                                generateUniqueTrainName());

                Response response = requestAs(Role.ADMIN)
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
                assertNotNull(databaseTrain, "Train setup should persisted in the database");

                return databaseTrain;
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertTrainDetail(
                        Response response,
                        TrainData expectedTrain) {

                Allure.step(
                                "Verify train detail response",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200)
                                                        .body("id",
                                                                        equalTo(expectedTrain.getId().intValue()))
                                                        .body("trainCode",
                                                                        equalTo(expectedTrain.getTrainCode()))
                                                        .body("trainName",
                                                                        equalTo(expectedTrain.getTrainName()))
                                                        .body("status",
                                                                        equalTo(expectedTrain.getStatus()))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        assertResponseTime(response);
                                });
        }

        private void assertTrainExistsInList(
                        Response response,
                        TrainData expectedTrain) {

                Allure.step(
                                "Verify train exists in response list",
                                () -> {

                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200);

                                        List<Map<String, Object>> trains = response.jsonPath().getList("$");

                                        boolean trainExists = trains.stream()
                                                        .anyMatch(train -> matchesTrain(train, expectedTrain));

                                        assertThat(
                                                        "Created train should exist in the train list",
                                                        trainExists, is(true));

                                        assertResponseTime(response);
                                });
        }

        private boolean matchesTrain(
                        Map<String, Object> actualTrain,
                        TrainData expectedTrain) {

                return hasExpectedId(
                                actualTrain.get("id"),
                                expectedTrain.getId())
                                && expectedTrain.getTrainCode()
                                                .equals(actualTrain.get("trainCode"))
                                && expectedTrain.getTrainName()
                                                .equals(actualTrain.get("trainName"))
                                && expectedTrain.getStatus()
                                                .equals(actualTrain.get("status"));
        }

        private boolean hasExpectedId(
                        Object actualId,
                        Long expectedId) {

                return actualId instanceof Number id
                                && id.longValue() == expectedId;
        }

        private Response getAllTrains(RequestSpecification requestSpec) {
                return Allure.step(
                                "GET /api/trains",
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .when()
                                                .get(TRAINS_ENDPOINT));
        }

        private Response getTrainById(
                        RequestSpecification requestSpec,
                        Long trainId) {
                return Allure.step(
                                "GET /api/trains/" + trainId,
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .pathParam("id", trainId)
                                                .when()
                                                .get(TRAIN_BY_ID_ENDPOINT));
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

        @ParameterizedTest(name = "Get all trains as {0}")
        @MethodSource("authorizedRoles")
        @DisplayName("Get Train - Successfully retrieves all trains for admin and regular user")
        void getAllTrainsShouldReturnCreatedTrain(Role role) {
                TrainData expectedTrain = createTrainForSetup();

                Response response = getAllTrains(requestAs(role));

                assertTrainExistsInList(response, expectedTrain);
        }

        @ParameterizedTest(name = "Get train by ID as {0}")
        @MethodSource("authorizedRoles")
        @DisplayName("Get Train - Successfully retrieves existing train by ID for admin and regular user")
        void getTrainByIdShouldReturnExpectedTrain(Role role) {

                TrainData expectedTrain = createTrainForSetup();

                Response response = getTrainById(
                                requestAs(role),
                                expectedTrain.getId());

                assertTrainDetail(
                                response,
                                expectedTrain);
        }

        @ParameterizedTest(name = "Get missing train as {0}")
        @MethodSource("authorizedRoles")
        @DisplayName("Get Train - Return 404 when train ID does not exist")
        void getTrainByIdShouldReturn404WhenTrainDoesNotExist(Role role) {
                Response response = getTrainById(
                                requestAs(role),
                                NON_EXISTENT_TRAIN_ID);

                assertErrorResponse(
                                response, 
                                404, 
                                "Train not found with ID: " + NON_EXISTENT_TRAIN_ID);
        }

        @Test
        @DisplayName("Get Train - Return 401 (Unauthorized) when getting all trains without authentication")
        void getAllTrainsShouldReturn401WithoutToken() {
                Response response = getAllTrains(
                                given()
                                                .accept(ContentType.JSON));

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
        }

        @Test
        @DisplayName("Get Train - Return 401 (Unauthorized) when getting train by ID without authentication")
        void getTrainByIdShouldReturn401WithoutToken() {
                TrainData expectedTrain = createTrainForSetup();

                Response response = getTrainById(
                                given()
                                                .accept(ContentType.JSON),
                                expectedTrain.getId());

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
        }

        @Test
        @DisplayName("Get Train - Return 401 (Unauthorized) when getting all trains with invalid token")
        void getAllTrainsShouldReturn401WithInvalidToken() {
                Response response = getAllTrains(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .accept(ContentType.JSON));

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Get Train - Return 401 (Unauthorized) when getting train by ID with invalid token")
        void getTrainByIdShouldReturn401WithInvalidToken() {
                TrainData expectedTrain = createTrainForSetup();

                Response response = getTrainById(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .accept(ContentType.JSON),
                                expectedTrain.getId());
                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Get Train - Return 401 (Unauthorized) when getting all trains with expired user token")
        void getAllTrainsShouldReturn401WithExpiredUserToken() {
                String expiredUserToken = AuthUtils.getExpiredUserToken();

                Response response = getAllTrains(
                                given()
                                                .auth()
                                                .oauth2(expiredUserToken)
                                                .accept(ContentType.JSON));

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Get Train - Return 401 (Unauthorized) when getting train by ID with expired admin token")
        void getTrainByIdShouldReturn401WithExpiredAdminToken() {
                TrainData expectedTrain = createTrainForSetup();
                String expiredAdminToken = AuthUtils.getExpiredAdminToken();

                Response response = getTrainById(
                                given()
                                                .auth()
                                                .oauth2(expiredAdminToken)
                                                .accept(ContentType.JSON),
                                expectedTrain.getId());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }
}