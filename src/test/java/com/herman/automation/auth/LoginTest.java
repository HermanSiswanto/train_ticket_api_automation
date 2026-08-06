package com.herman.automation.auth;

import com.herman.automation.base.BaseTest;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import io.qameta.allure.Story;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.herman.automation.utils.TestDataGenerator.generateUniqueEmail;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

@Epic("Train Ticket API")
@Feature("Authentication API")
@Story("Login")
@DisplayName("Login Test")
public class LoginTest extends BaseTest {

        private static final String REGISTER_ENDPOINT = "/api/auth/register";
        private static final String LOGIN_ENDPOINT = "/api/auth/login";

        private static final String VALID_NAME = "Automation User";
        private static final String VALID_PASSWORD = "Password123!";

        private static final String ADMIN_EMAIL = System.getenv().getOrDefault(
                        "ADMIN_EMAIL",
                        "admin@trainticket.com");

        private static final String ADMIN_PASSWORD = System.getenv().getOrDefault(
                        "ADMIN_PASSWORD",
                        "Admin@123");

        private static final String REGISTER_SUCCESS_MESSAGE = "Register success";
        private static final String LOGIN_SUCCESS_MESSAGE = "Login success";
        private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;

        private record RegisterRequest(
                        String name,
                        String email,
                        String password) {}
                

        private record LoginRequest(
                        String email, 
                        String password) {}


        private RegisterRequest createValidRegisterRequest() {
                return new RegisterRequest(
                                VALID_NAME,
                                generateUniqueEmail(),
                                VALID_PASSWORD);
        }

        private LoginRequest createAdminLoginRequest() {
                return new LoginRequest(
                                ADMIN_EMAIL,
                                ADMIN_PASSWORD);
        }

        @Step("Register user for login test setup")
        private LoginRequest createRegisteredUserLoginRequest() {
                RegisterRequest registerRequest = createValidRegisterRequest();

                Response response = register(registerRequest);

                assertSuccessfulRegistrationResponse(response);

                return new LoginRequest(
                                registerRequest.email(),
                                registerRequest.password());
        }

        private Response register(RegisterRequest request) {
                return Allure.step(
                                "POST " + REGISTER_ENDPOINT,
                                () -> given()
                                                .filter(allureFilter)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON)
                                                .body(request)
                                                .when()
                                                .post(REGISTER_ENDPOINT));
        }

        private Response login(LoginRequest request) {
                return Allure.step(
                                "POST " + LOGIN_ENDPOINT,
                                () -> given()
                                                .filter(allureFilter)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON)
                                                .body(request)
                                                .when()
                                                .post(LOGIN_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
        }

        private void assertSuccessfulRegistrationResponse(Response response) {
                Allure.step(
                                "Verify successful registration response",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(201)
                                                        .body("message", equalTo(REGISTER_SUCCESS_MESSAGE));

                                        assertResponseTime(response);
                                });
        }

        private void assertSuccessfulLoginResponse(Response response) {
                Allure.step(
                                "Verify successful login response",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(200)
                                                        .body("message", equalTo(LOGIN_SUCCESS_MESSAGE))
                                                        .body("token", notNullValue())
                                                        .body("token", startsWith("eyJ"));

                                        assertResponseTime(response);
                                });
        }

        private void assertValidationError(
                        Response response,
                        String field,
                        String expectedMessage) {
                Allure.step(
                                "Verify validation error for field: " + field,
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(400)
                                                        .body(field, equalTo(expectedMessage));

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

        private void assertAllRequiredFieldValidationErrors(Response response) {
                Allure.step(
                                "Verify validation errors for all required fields",
                                () -> {
                                        response.then()
                                                        .log().ifValidationFails()
                                                        .statusCode(400)
                                                        .body("email", equalTo("Email is required"))
                                                        .body("password", equalTo("Password is required"));

                                        assertResponseTime(response);
                                });
        }

        private static Stream<Arguments> whitespaceEmailTestData() {
                return Stream.of(
                                Arguments.of(
                                                "Login with leading whitespace in email",
                                                " ",
                                                ""),
                                Arguments.of(
                                                "Login with trailing whitespace in email",
                                                "",
                                                " "),
                                Arguments.of(
                                                "Login with leading and trailing whitespace in email",
                                                " ",
                                                " "));
        }

        private static Stream<Arguments> sqlInjectionTestData() {
                return Stream.of(
                                Arguments.of(
                                                "SQL injection attempt in email",
                                                "' OR 1=1 --",
                                                VALID_PASSWORD),
                                Arguments.of(
                                                "SQL injection attempt in password",
                                                ADMIN_EMAIL,
                                                "' OR 1=1 --"));
        }

        @Test
        @DisplayName("Login - Successfully logs in using registered user credentials")
        void loginShouldSucceedForRegisteredUser() {
                LoginRequest request = createRegisteredUserLoginRequest();

                Response response = login(request);

                assertSuccessfulLoginResponse(response);
        }

        @Test
        @DisplayName("Login - Successfully logs in using admin credentials")
        void loginShouldSucceedForAdmin() {
                LoginRequest request = createAdminLoginRequest();

                Response response = login(request);

                assertSuccessfulLoginResponse(response);
        }

        @Test
        @DisplayName("Login - Return 401 (Unauthorized) when requested with invalid password")
        void loginShouldReturnUnauthorizedWithInvalidPassword() {
                LoginRequest registeredUser = createRegisteredUserLoginRequest();
                LoginRequest request = new LoginRequest(
                                registeredUser.email(),
                                "WrongPassword123!TEST");
                Response response = login(request);

                assertErrorResponse(response, 401, INVALID_CREDENTIALS_MESSAGE);
        }

        @Test
        @DisplayName("Login - Return 401 (Unauthorized) when requested with unregistered email")
        void loginShouldReturnUnauthorizedForUnregisteredEmail() {
                LoginRequest request = new LoginRequest(
                                generateUniqueEmail(),
                                VALID_PASSWORD);
                Response response = login(request);

                assertErrorResponse(response, 401, INVALID_CREDENTIALS_MESSAGE);
        }

        @Test
        @DisplayName("Login - Return 400 (Bad Request) when email is empty")
        void loginShouldReturnBadRequestWhenEmailIsEmpty() {
                LoginRequest request = new LoginRequest(
                                "",
                                VALID_PASSWORD);
                Response response = login(request);

                assertValidationError(response, "email", "Email is required");
        }

        @Test
        @DisplayName("Login - Return 400 (Bad Request) when password is empty")
        void loginShouldReturnBadRequestWhenPasswordIsEmpty() {

                LoginRequest request = new LoginRequest(
                                generateUniqueEmail(),
                                "");
                Response response = login(request);

                assertValidationError(response, "password", "Password is required");
        }

        @Test
        @DisplayName("Login - Return 400 (Bad Request) when all fields are empty")
        void loginShouldReturnBadRequestWhenAllFieldsAreEmpty() {

                LoginRequest request = new LoginRequest("", "");
                Response response = login(request);
                assertAllRequiredFieldValidationErrors(response);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("whitespaceEmailTestData")
        @DisplayName("Login - Trim leading and trailing spaces from email")
        void loginShouldTrimWhitespaceFromEmail(
                        String scenario,
                        String prefix,
                        String suffix) {

                LoginRequest registeredUser = createRegisteredUserLoginRequest();

                LoginRequest request = new LoginRequest(
                                prefix + registeredUser.email() + suffix,
                                registeredUser.password());

                Response response = login(request);

                assertSuccessfulLoginResponse(response);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("sqlInjectionTestData")
        @DisplayName("Login - Reject SQL injection attempts in login credentials")
        void loginShouldRejectSqlInjectionAttempt(
                        String scenario,
                        String email,
                        String password) {
                LoginRequest request = new LoginRequest(email, password);
                Response response = login(request);

                assertErrorResponse(response, 401, INVALID_CREDENTIALS_MESSAGE);
        }
}