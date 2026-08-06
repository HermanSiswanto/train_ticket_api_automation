package com.herman.automation.station;

import com.herman.automation.base.BaseTest;
import com.herman.automation.model.StationData;
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

import static com.herman.automation.utils.TestDataGenerator.generateCity;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationCode;
import static com.herman.automation.utils.TestDataGenerator.generateUniqueStationName;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Epic("Train Ticket API")
@Feature("Station API")
@Story("Get Station")
@DisplayName("Get Station Test")
public class GetStationTest extends BaseTest {

        private static final String STATIONS_ENDPOINT = "/api/stations";
        private static final String STATION_BY_ID_ENDPOINT = "/api/stations/{id}";
        private static final long MAX_RESPONSE_TIME_MS = 1_000L;

        private static final String AUTH_REQUIRED_MESSAGE = "Authentication required";
        private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
        private static final long NON_EXISTENT_STATION_ID = Long.MAX_VALUE;

        private String adminToken;
        private String userToken;


        private record StationRequest(
                        String stationCode,
                        String stationName,
                        String city) {
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

        @Step("Create Station for test setup")
        private StationData createStationForSetup() {
                StationRequest request = new StationRequest(
                                generateUniqueStationCode(), 
                                generateUniqueStationName(), 
                                generateCity());

                Response response = requestAs(Role.ADMIN)
                                .body(request)
                                .when()
                                .post(STATIONS_ENDPOINT)
                                .then()
                                .statusCode(201)
                                .body("id", notNullValue())
                                .extract()
                                .response();

                Long stationId = response.jsonPath().getLong("id");
                assertNotNull(stationId, "Extracted ID from API response should not be null");

                StationData expectedStation = DatabaseUtils.getStationById(stationId);
                assertNotNull(
                                expectedStation,
                                "Station setup should be persisted in the database");

                return expectedStation;
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
                                "Verify error response: HTTP %d — %s"
                                                .formatted(expectedStatusCode, expectedMessage),
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(expectedStatusCode)
                                                        .body("message", equalTo(expectedMessage));

                                        assertResponseTime(response);
                                });
        }

        private void assertStationDetail(Response response, StationData expectedStation) {
                Allure.step(
                                "Verify station detail response",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200)
                                                        .body("id", equalTo(expectedStation.getId().intValue()))
                                                        .body("stationCode", equalTo(expectedStation.getStationCode()))
                                                        .body("stationName", equalTo(expectedStation.getStationName()))
                                                        .body("city", equalTo(expectedStation.getCity()))
                                                        .body("status", equalTo(expectedStation.getStatus()))
                                                        .body("createdAt", notNullValue())
                                                        .body("updatedAt", notNullValue());

                                        assertResponseTime(response);

                                });
        }

        private void assertStationExistsInList(
                        Response response,
                        StationData expectedStation) {

                Allure.step(
                                "Verify station exists in response list",
                                () -> {

                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200);

                                        List<Map<String, Object>> stations = response.jsonPath().getList("$");

                                        boolean stationExists = stations.stream()
                                                        .anyMatch(station -> matchesStation(
                                                                        station,
                                                                        expectedStation));

                                        assertThat(
                                                        "Created station should exist in the station list",
                                                        stationExists,
                                                        is(true));

                                        assertResponseTime(response);
                                });
        }

        private boolean matchesStation(
                        Map<String, Object> actualStation,
                        StationData expectedStation) {

                return hasExpectedId(
                                actualStation.get("id"),
                                expectedStation.getId())
                                && expectedStation.getStationCode()
                                                .equals(actualStation.get("stationCode"))
                                && expectedStation.getStationName()
                                                .equals(actualStation.get("stationName"))
                                && expectedStation.getCity()
                                                .equals(actualStation.get("city"))
                                && expectedStation.getStatus()
                                                .equals(actualStation.get("status"));
        }

        private boolean hasExpectedId(
                        Object actualId,
                        Long expectedId) {

                return actualId instanceof Number id
                                && id.longValue() == expectedId;
        }

        private Response getAllStations(RequestSpecification request) {
                return Allure.step(
                                "GET /api/stations",
                                () -> request
                                                .filter(allureFilter)
                                                .when()
                                                .get(STATIONS_ENDPOINT));
        }

        private Response getStationById(
                        RequestSpecification request,
                        Long stationId) {
                return Allure.step(
                                "GET /api/stations/" + stationId,
                                () -> request
                                                .filter(allureFilter)
                                                .pathParam("id", stationId)
                                                .when()
                                                .get(STATION_BY_ID_ENDPOINT));
        }

        @ParameterizedTest(name = "Get all stations as {0}")
        @MethodSource("authorizedRoles")
        @DisplayName("Get Station - Successfully retrieves all trains for admin and regular user")
        void getAllStationsShouldReturnCreatedStation(Role role) {
                StationData expectedStation = createStationForSetup();

                Response response = getAllStations(requestAs(role));

                assertStationExistsInList(response, expectedStation);
        }

        @ParameterizedTest(name = "Get station by ID as {0}")
        @MethodSource("authorizedRoles")
        @DisplayName("Get Station - Successfully retrieves existing station by ID for admin and regular user")
        void getStationByIdShouldReturnExpectedStation(Role role) {
                StationData expectedStation = createStationForSetup();

                Response response = getStationById(
                                requestAs(role),
                                expectedStation.getId());

                assertStationDetail(response, expectedStation);
        }

        @ParameterizedTest(name = "Get missing station as {0}")
        @MethodSource("authorizedRoles")
        @DisplayName("Get Station - Return 404 (Forbidden) when station id does not exist")
        void getStationByIdShouldReturnNotFoundWhenStationDoesNotExist(Role role) {
                Response response = getStationById(
                                requestAs(role),
                                NON_EXISTENT_STATION_ID);

                assertErrorResponse(
                                response,
                                404,
                                "Station not found with ID: " + NON_EXISTENT_STATION_ID);
        }

        @Test
        @DisplayName("Get Station - Return 401 (Unauthorized) when getting all station without authentication")
        void getAllStationsShouldReturnUnauthorizedWithoutToken() {
                Response response = getAllStations(
                                given()
                                                .accept(ContentType.JSON));

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
        }

        @Test
        @DisplayName("Get Station - Return 401 (Unauthorized) when getting station by ID without authentication")
        void getStationByIdShouldReturnUnauthorizedWithoutToken() {
                StationData expectedStation = createStationForSetup();

                Response response = getStationById(
                                given()
                                                .accept(ContentType.JSON),
                                expectedStation.getId());

                assertErrorResponse(response, 401, AUTH_REQUIRED_MESSAGE);
        }

        @Test
        @DisplayName("Get Station - Return 401 (Unauthorized) when getting all stations with invalid token")
        void getAllStationsShouldReturnUnauthorizedWithInvalidToken() {
                Response response = getAllStations(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .accept(ContentType.JSON));

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Get Station - Return 401 (Unauthorized) when getting station by ID with invalid token")
        void getStationByIdShouldReturnUnauthorizedWithInvalidToken() {
                StationData expectedStation = createStationForSetup();

                Response response = getStationById(
                                given()
                                                .auth()
                                                .oauth2("invalid-token")
                                                .accept(ContentType.JSON),
                                expectedStation.getId());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Get Station - Return 401 (Unauthorized) when getting station by ID with expired user token")
        void getAllStationShouldReturnUnauthorizedWithExpiredUserToken() {
                String expiredUserToken = AuthUtils.getExpiredUserToken();

                Response response = getAllStations(
                                given()
                                                .auth()
                                                .oauth2(expiredUserToken)
                                                .accept(ContentType.JSON));

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

        @Test
        @DisplayName("Get Station - Return 401 (Unauthorized) when getting station by ID with expired admin token")
        void getStationByIdShouldReturnUnauthorizedWithExpiredAdminToken() {
                StationData expectedStation = createStationForSetup();
                String expiredAdminToken = AuthUtils.getExpiredAdminToken();

                Response response = getStationById(
                                given()
                                                .auth()
                                                .oauth2(expiredAdminToken)
                                                .accept(ContentType.JSON),
                                expectedStation.getId());

                assertErrorResponse(response, 401, INVALID_TOKEN_MESSAGE);
        }

}