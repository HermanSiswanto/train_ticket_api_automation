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

import static com.herman.automation.utils.TestDataGenerator.generateUniqueTrainCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueTrainName;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Epic("Train Ticket API")
@Feature("Train API")
@Story("Delete Train")
@DisplayName("Delete Train Test")
public class DeleteTrainTest extends BaseTest {

        private static final String TRAINS_ENDPOINT = "/api/trains";
        private static final String TRAIN_BY_ID_ENDPOINT = "/api/trains/{id}";

        private static final String ACCESS_DENIED_MESSAGE = "Access denied";
        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
        private static final String EXPIRED_TOKEN_MESSAGE = "Invalid or expired token";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_TRAIN_ID = Long.MAX_VALUE;

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

        @Step("Create Train for test setup")
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
                assertNotNull(databaseTrain, "Train setup should persisted in the database");

                return databaseTrain;
        }

        private Response deleteTrain(
                        RequestSpecification requestSpec,
                        Long trainId) {
                return Allure.step(
                                "DELETE /api/stations/" + trainId,
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .pathParam("id", trainId)
                                                .when()
                                                .delete(TRAIN_BY_ID_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertSuccessfulDeleteTrain(Response response) {

                Allure.step(
                                "Verify successful station deletion",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(204);

                                        assertResponseTime(response);
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

        private void assertTrainStillExistsInDatabase(Long stationId) {
                Allure.step(
                                "Verify train remains in database",
                                () -> {
                                        TrainData databaseTrain = DatabaseUtils.getTrainById(stationId);

                                        assertNotNull(
                                                        databaseTrain,
                                                        "Train should remain in the database after a failed delete");
                                });
        }

        private void assertTrainDeletedFromDatabase(Long trainId) {
                Allure.step(
                                "Verify train is deleted from database",
                                () -> {
                                        TrainData databaseTrain = DatabaseUtils.getTrainById(trainId);

                                        assertNull(
                                                        databaseTrain,
                                                        "Deleted train should no longer exist in the database");
                                });
        }

        @Test
        @DisplayName("Delete Train - Succeeds delete train and is deleted from database")
        void deleteTrainShouldSucceed() {
                TrainData train = createTrainForSetup();
                RequestSpecification adminRequest = requestAsAdmin();

                Response response = deleteTrain(
                                adminRequest,
                                train.getId());

                assertSuccessfulDeleteTrain(response);
                assertTrainDeletedFromDatabase(train.getId());
        }

        @Test
        @DisplayName("Delete Train - Return 403 (Forbidden) when requested by regular user")
        void deleteTrainShouldReturnForbiddenWhenRequestedByUser() {
                TrainData train = createTrainForSetup();
                RequestSpecification userRequest = requestAsUser();

                Response response = deleteTrain(
                                userRequest,
                                train.getId());

                assertErrorResponse(
                                response,
                                403,
                                ACCESS_DENIED_MESSAGE);

                assertTrainStillExistsInDatabase(train.getId());
        }

        @Test
        @DisplayName("Delete Train - Return 404 (Not Found) when train doesn't exist")
        void deleteTrainShouldReturnNotFoundWhenTrainDoesNotExist() {
                RequestSpecification adminRequest = requestAsAdmin();
                Response response = deleteTrain(
                                adminRequest,
                                NON_EXISTENT_TRAIN_ID);

                assertErrorResponse(
                                response,
                                404,
                                "Train not found with ID: " + NON_EXISTENT_TRAIN_ID);
        }

        @Test
        @DisplayName("Delete Train - Return 401 (Unauthorized) when requested without authentication")
        void deleteTrainShouldReturnUnauthorizedWithoutToken() {
                TrainData train = createTrainForSetup();

                Response response = deleteTrain(
                                given()
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                train.getId());

                assertErrorResponse(
                                response,
                                401,
                                AUTH_REQUIRED_MESSAGE);

                assertTrainStillExistsInDatabase(train.getId());
        }

        @Test
        @DisplayName("Delete Train - Return 401 (Unauthorized) when requested with invalid token")
        void deleteTrainShouldReturnUnauthorizedWithInvalidToken() {
                TrainData train = createTrainForSetup();

                Response response = deleteTrain(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                train.getId());

                assertErrorResponse(
                                response,
                                401,
                                INVALID_TOKEN_MESSAGE);

                assertTrainStillExistsInDatabase(train.getId());
        }

        @Test
        @DisplayName("Delete Train - Return 401 (Unauthorized) when requested with expired token")
        void deleteTrainShouldReturnUnauthroizedWithExpiredAdminToken() {
                TrainData train = createTrainForSetup();
                String expiredToken = AuthUtils.getExpiredAdminToken();

                Response response = deleteTrain(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                train.getId());

                assertErrorResponse(
                                response,
                                401,
                                EXPIRED_TOKEN_MESSAGE);

                assertTrainStillExistsInDatabase(train.getId());
        }
}
