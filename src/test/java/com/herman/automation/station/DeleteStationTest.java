package com.herman.automation.station;

import static com.herman.automation.utils.TestDataGenerator.generateCity;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationName;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.herman.automation.base.BaseTest;
import com.herman.automation.model.StationData;
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
@Feature("Station API")
@Story("Delete Station")
@DisplayName("Delete Station Test")
public class DeleteStationTest extends BaseTest {
        private static final String STATIONS_ENDPOINT = "/api/stations";
        private static final String STATION_BY_ID_ENDPOINT = "/api/stations/{id}";

        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
        private static final String ACCESS_DENIED_MESSAGE = "Access denied";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;
        private static final long NON_EXISTENT_STATION_ID = Long.MAX_VALUE;

        private String adminToken;
        private String userToken;

        private record StationRequest(
                        String stationCode,
                        String stationName,
                        String city) {
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

        @Step("Create Station for test setup")
        private StationData createStationForSetup(RequestSpecification requestSpec) {
                StationRequest request = new StationRequest(
                                generateUniqueStationCode(), 
                                generateUniqueStationName(), 
                                generateCity());

                Response response = requestSpec
                                .body(request)
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

        private Response deleteStation(
                        RequestSpecification requestSpec,
                        Long stationId) {
                return Allure.step(
                                "DELETE /api/schedules/" + stationId,
                                () -> requestSpec
                                                .filter(allureFilter)
                                                .pathParam("id", stationId)
                                                .when()
                                                .delete(STATION_BY_ID_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertDeletedSuccessfulResponse(Response response) {
                Allure.step(
                                "Verify successful station deletion",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(204);

                                        assertResponseTime(response);
                                });
        }

        private void assertStationDeletedFromDatabase(Long stationId) {
                Allure.step(
                                "Verify schedule is deleted from database",
                                () -> {
                                        StationData station = DatabaseUtils.getStationById(stationId);

                                        assertNull(
                                                        station,
                                                        "Deleted station should no longer exist in the database");
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

        private void assertStationStillExistsInDatabase(Long stationId) {
                Allure.step(
                                "Verify station remains in database",
                                () -> {
                                        StationData databaseStation = DatabaseUtils.getStationById(stationId);
                                        assertNotNull(
                                                        databaseStation,
                                                        "Station should remain in the database after a failed delete");
                                });
        }

        @Test
        @DisplayName("Delete Station - Succeeds with valid data and is deleted from the database")
        void deleteStationShouldSucceed() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationData databaseStation = createStationForSetup(adminRequest);

                Response response = deleteStation(
                                requestAsAdmin(),
                                databaseStation.getId());

                assertDeletedSuccessfulResponse(response);
                assertStationDeletedFromDatabase(databaseStation.getId());
        }

        @Test
        @DisplayName("Delete Station - Return 403 (Forbidden) when requested by a regular user")
        void deleteStationShouldReturnForbiddenWhenRequestedByUser() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationData databaseStation = createStationForSetup(adminRequest);

                Response response = deleteStation(
                                requestAsUser(),
                                databaseStation.getId());

                assertErrorResponse(
                                response,
                                403,
                                ACCESS_DENIED_MESSAGE);

                assertStationStillExistsInDatabase(databaseStation.getId());
        }

        @Test
        @DisplayName("Delete Station - Return 404 (Not Found) when schedule doesn't exist")
        void deleteStationShouldReturnNotFoundWhenStationDoesNotExist() {
                Response response = deleteStation(
                                requestAsAdmin(),
                                NON_EXISTENT_STATION_ID);

                assertErrorResponse(
                                response,
                                404,
                                "Station not found with ID: " + NON_EXISTENT_STATION_ID);
        }

        @Test
        @DisplayName("Delete Station - Return 401 (Unauthorized) when requested without authentication")
        void deleteStationShouldReturnUnauthorizedWithoutToken() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationData databaseStation = createStationForSetup(adminRequest);

                Response response = deleteStation(
                                given()
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                databaseStation.getId());

                assertErrorResponse(
                                response,
                                401,
                                AUTH_REQUIRED_MESSAGE);

                assertStationStillExistsInDatabase(databaseStation.getId());
        }

        @Test
        @DisplayName("Delete Station - Return 401 (Unauthorized) when requested with invalid token")
        void deleteStationShouldReturnUnauthorizedWithInvalidToken() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationData databaseStation = createStationForSetup(adminRequest);

                Response response = deleteStation(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .contentType("application/json")
                                                .accept(ContentType.JSON),
                                databaseStation.getId());

                assertErrorResponse(
                                response,
                                401,
                                INVALID_TOKEN_MESSAGE);

                assertStationStillExistsInDatabase(databaseStation.getId());
        }

        @Test
        @DisplayName("Delete Station - Return 401 (Unauthorized) when requested with expired token")
        void deleteStationShouldReturnUnauthorizedWithExpiredAdminToken() {
                RequestSpecification adminRequest = requestAsAdmin();
                StationData databaseStation = createStationForSetup(adminRequest);
                String expiredToken = AuthUtils.getExpiredAdminToken();

                Response response = deleteStation(
                                given()
                                                .auth()
                                                .oauth2(expiredToken)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON),
                                databaseStation.getId());

                assertErrorResponse(
                                response,
                                401,
                                INVALID_TOKEN_MESSAGE);

                assertStationStillExistsInDatabase(databaseStation.getId());
        }

}
