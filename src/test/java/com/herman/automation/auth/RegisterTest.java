package com.herman.automation.auth;

import com.herman.automation.base.BaseTest;
import com.herman.automation.model.UserData;
import com.herman.automation.utils.DatabaseUtils;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.herman.automation.utils.TestDataGenerator.generateUniqueEmail;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Epic("Train Ticket API")
@Feature("Authentication API")
@Story("Register")
@DisplayName("Register Test")
public class RegisterTest extends BaseTest {

        private static final String REGISTER_ENDPOINT = "/api/auth/register";

        private static final String VALID_NAME = "Automation User";
        private static final String VALID_PASSWORD = "Password123!";
        private static final String USER_ROLE = "USER";

        private static final String REGISTER_SUCCESS_MESSAGE = "Register success";
        private static final String INVALID_NAME_MESSAGE = "Name may only contain letters and single spaces";
        private static final String INVALID_PASSWORD_MESSAGE = "Password must be at least 8 characters, include uppercase, "
                        + "lowercase, number, and contain no whitespace";

        private static final long MAX_RESPONSE_TIME_MS = 1_000L;

        private record RegisterRequest(
                        String name,
                        String email,
                        String password) {
        }

        private RegisterRequest createValidRegisterRequest() {
                return new RegisterRequest(
                                VALID_NAME,
                                generateUniqueEmail(),
                                VALID_PASSWORD);
        }

        private Response register(Object requestBody) {
                return Allure.step(
                                "POST " + REGISTER_ENDPOINT,
                                () -> given()
                                                .filter(allureFilter)
                                                .contentType(ContentType.JSON)
                                                .accept(ContentType.JSON)
                                                .body(requestBody)
                                                .when()
                                                .post(REGISTER_ENDPOINT));
        }

        private void assertResponseTime(Response response) {
                response.then()
                                .time(lessThan(MAX_RESPONSE_TIME_MS));
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
                                                        .body("name", equalTo("Name is required"))
                                                        .body("email", equalTo("Email is required"))
                                                        .body("password", equalTo("Password is required"));

                                        assertResponseTime(response);
                                });
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

        private void assertRegisteredUserPersistedInDatabase(
                        String expectedName,
                        String expectedEmail) {
                Allure.step(
                                "Verify registered user is persisted in database",
                                () -> {
                                        UserData actualUser = DatabaseUtils.getUserByEmail(expectedEmail);

                                        assertNotNull(
                                                        actualUser,
                                                        "Registered user should exist in the database");

                                        assertEquals(
                                                        expectedName,
                                                        actualUser.getName(),
                                                        "Database should store the expected name");

                                        assertEquals(
                                                        expectedEmail,
                                                        actualUser.getEmail(),
                                                        "Database should store the expected email");

                                        assertEquals(
                                                        USER_ROLE,
                                                        actualUser.getRole(),
                                                        "Newly registered user should have USER role");
                                });
        }

        private void assertUserDoesNotExistInDatabase(String email) {

                Allure.step(
                                "Verify user is not persisted in database",
                                () -> {

                                        UserData user = DatabaseUtils.getUserByEmail(email);

                                        assertNull(
                                                        user,
                                                        "User should not exist in database");
                                });
        }

        private static Stream<Arguments> invalidNameTestData() {
                return Stream.of(
                                Arguments.of(
                                                "Name exceeds maximum length",
                                                "A".repeat(101),
                                                "Name must not exceed 100 characters"),
                                Arguments.of(
                                                "Name contains numbers",
                                                "Herman123",
                                                INVALID_NAME_MESSAGE),
                                Arguments.of(
                                                "Name contains special character",
                                                "Herman@",
                                                INVALID_NAME_MESSAGE),
                                Arguments.of(
                                                "Name contains double spaces",
                                                "Herman  Siswanto",
                                                INVALID_NAME_MESSAGE));
        }

        private static Stream<Arguments> invalidEmailTestData() {
                return Stream.of(
                                Arguments.of(
                                                "Email without @",
                                                "hackername.com",
                                                "Invalid email format"),
                                Arguments.of(
                                                "Email ends with @",
                                                "hackername@",
                                                "Invalid email format"),
                                Arguments.of(
                                                "Email without domain extension",
                                                "herman@gmail",
                                                "Invalid email format"),
                                Arguments.of(
                                                "Email with invalid top-level domain",
                                                "hackername@gmail.c",
                                                "Invalid email format"),
                                Arguments.of(
                                                "Email exceeds maximum length",
                                                "A".repeat(91) + "@gmail.com",
                                                "Email must not exceed 100 characters"),
                                Arguments.of(
                                                "Email contains internal whitespace",
                                                "Giyu @gmail.com",
                                                "Invalid email format"));
        }

        private static Stream<Arguments> invalidPasswordTestData() {
                return Stream.of(
                                Arguments.of("Password below eight characters", "Her12ma"),
                                Arguments.of("Password contains letters only", "HERmanaja"),
                                Arguments.of("Password contains numbers only", "123456789"),
                                Arguments.of(
                                                "Password has no lowercase character",
                                                "HERMAN123"),
                                Arguments.of(
                                                "Password contains leading whitespace",
                                                " Herman123"),
                                Arguments.of(
                                                "Password contains middle whitespace",
                                                "Herman 123"),
                                Arguments.of(
                                                "Password contains trailing whitespace",
                                                "Herman123 "));
        }

        private static Stream<Arguments> whitespaceTestDataForName() {
                return Stream.of(
                                Arguments.of("Name with leading whitespace", " Automation User"),
                                Arguments.of("Name with trailing whitespace", "Automation User "),
                                Arguments.of("Name with leading and trailing whitespace", " Automation User "));
        }

        private static Stream<Arguments> whitespaceTestDataForEmail() {
                return Stream.of(
                                Arguments.of(
                                                "Email with leading whitespace",
                                                " " + generateUniqueEmail()),
                                Arguments.of(
                                                "Email with trailing whitespace",
                                                generateUniqueEmail() + " "),
                                Arguments.of(
                                                "Email with leading and trailing whitespace",
                                                " " + generateUniqueEmail() + " "));
        }

        private static Stream<Arguments> privilegeEscalationRequests() {
                return Stream.of(
                                Arguments.of("Client-supplied role ADMIN", "role", "ADMIN"),
                                Arguments.of("Client-supplied roleId", "roleId", 1));
        }

        @Test
        @DisplayName("Register - Successfully registers a new user with valid data and is persisted in database")
        void registerShouldSucceedWithValidData() {
                RegisterRequest request = createValidRegisterRequest();

                Response response = register(request);

                assertSuccessfulRegistrationResponse(response);

                assertRegisteredUserPersistedInDatabase(
                                request.name(),
                                request.email());
        }

        @Test
        @DisplayName("Register - Return 409 (Conflict) when email already exists")
        void registerShouldReturnConflictWhenEmailAlreadyExists() {
                RegisterRequest request = createValidRegisterRequest();

                Response firstResponse = register(request);
                assertSuccessfulRegistrationResponse(firstResponse);

                Response duplicateResponse = register(request);
                assertErrorResponse(duplicateResponse, 409, "Email already registered");
                assertRegisteredUserPersistedInDatabase(
                                request.name(),
                                request.email());
        }

        @Test
        @DisplayName("Register - Return 409 (Conflict) when email already exists (Case Insensitive)")
        void registerShouldReturnConflictWhenEmailAlreadyExistsCaseInsensitive() {
                String baseEmail = generateUniqueEmail();

                RegisterRequest originalRequest = new RegisterRequest(
                                VALID_NAME,
                                baseEmail.toUpperCase(),
                                VALID_PASSWORD);

                Response firstResponse = register(originalRequest);

                assertSuccessfulRegistrationResponse(firstResponse);

                assertRegisteredUserPersistedInDatabase(
                                originalRequest.name(),
                                originalRequest.email().toLowerCase());

                RegisterRequest duplicateRequest = new RegisterRequest(
                                VALID_NAME,
                                baseEmail.toLowerCase(),
                                VALID_PASSWORD);

                Response duplicateResponse = register(duplicateRequest);

                assertErrorResponse(
                                duplicateResponse,
                                409,
                                "Email already registered");
        }

        @Test
        @DisplayName("Register - Return 400 (Bad Request) when name is empty")
        void registerShouldReturnBadRequestWhenNameIsEmpty() {
                String email = generateUniqueEmail();

                RegisterRequest request = new RegisterRequest(
                                "",
                                email,
                                VALID_PASSWORD);

                Response response = register(request);

                assertValidationError(response, "name", "Name is required");
                assertUserDoesNotExistInDatabase(request.email());
        }

        @Test
        @DisplayName("Register - Return 400 (Bad Request) when email is empty")
        void registerShouldReturnBadRequestWhenEmailIsEmpty() {
                RegisterRequest request = new RegisterRequest(
                                VALID_NAME,
                                "",
                                VALID_PASSWORD);

                Response response = register(request);

                assertValidationError(response, "email", "Email is required");
                assertUserDoesNotExistInDatabase(request.email());
        }

        @Test
        @DisplayName("Register - Return 400 (Bad Request) when password is empty")
        void registerShouldReturnBadRequestWhenPasswordIsEmpty() {

                RegisterRequest request = new RegisterRequest(
                                VALID_NAME,
                                generateUniqueEmail(),
                                "");

                Response response = register(request);

                assertValidationError(response, "password", "Password is required");
                assertUserDoesNotExistInDatabase(request.email());
        }

        @Test
        @DisplayName("Register - Return 400 (Bad Request) when all fields are empty")
        void registerShouldReturnBadRequestWhenAllFieldsAreEmpty() {
                RegisterRequest request = new RegisterRequest("", "", "");
                Response response = register(request);

                assertAllRequiredFieldValidationErrors(response);

        }

        @ParameterizedTest(name = "Register with invalid name: {0}")
        @MethodSource("invalidNameTestData")
        @DisplayName("Register - Return 400 (Bad Request) when requested with invalid name format")
        void registerShouldReturnBadRequestWithInvalidName(
                        String scenario,
                        String invalidName,
                        String expectedMessage) {

                RegisterRequest request = new RegisterRequest(
                                invalidName,
                                generateUniqueEmail(),
                                VALID_PASSWORD);

                Response response = register(request);

                assertValidationError(response, "name", expectedMessage);
                assertUserDoesNotExistInDatabase(request.email());
        }

        @ParameterizedTest(name = "Register with invalid email: {0}")
        @MethodSource("invalidEmailTestData")
        @DisplayName("Register - Return 400 (Bad Request) when requested with invalid email format")
        void registerShouldReturnBadRequestWithInvalidEmail(
                        String scenario,
                        String invalidEmail,
                        String expectedMessage) {

                RegisterRequest request = new RegisterRequest(
                                VALID_NAME,
                                invalidEmail,
                                VALID_PASSWORD);

                Response response = register(request);

                assertValidationError(response, "email", expectedMessage);
                assertUserDoesNotExistInDatabase(request.email());
        }

        @ParameterizedTest(name = "Register with invalid password: {0}")
        @MethodSource("invalidPasswordTestData")
        @DisplayName("Register - Return 400 (Bad Request) when requested with invalid password format")
        void registerShouldReturnBadRequestWithInvalidPassword(
                        String scenario,
                        String invalidPassword) {

                RegisterRequest request = new RegisterRequest(
                                VALID_NAME,
                                generateUniqueEmail(),
                                invalidPassword);

                Response response = register(request);
                assertValidationError(
                                response,
                                "password",
                                INVALID_PASSWORD_MESSAGE);

                assertUserDoesNotExistInDatabase(request.email());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("whitespaceTestDataForName")
        @DisplayName("Register - Trim leading and trailing spaces from name")
        void registerShouldTrimWhitespaceFromName(
                        String scenario,
                        String name) {

                RegisterRequest request = new RegisterRequest(
                                name,
                                generateUniqueEmail(),
                                VALID_PASSWORD);

                Response response = register(request);

                assertSuccessfulRegistrationResponse(response);

                assertRegisteredUserPersistedInDatabase(
                                request.name().trim(),
                                request.email());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("whitespaceTestDataForEmail")
        @DisplayName("Register - Trim leading and trailing spaces from email")
        void registerShouldTrimWhitespaceFromEmail(
                        String scenario,
                        String email) {

                RegisterRequest request = new RegisterRequest(
                                VALID_NAME,
                                email,
                                VALID_PASSWORD);

                Response response = register(request);

                assertSuccessfulRegistrationResponse(response);

                assertRegisteredUserPersistedInDatabase(
                                request.name(),
                                request.email().trim());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("privilegeEscalationRequests")
        @DisplayName("Register - Prevent privilege escalation through client-supplied role fields")
        void registerShouldPreventPrivilegeEscalation(String scenario, String additionalField, Object additionalValue) {

                String email = generateUniqueEmail();

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("name", VALID_NAME);
                requestBody.put("email", email);
                requestBody.put("password", VALID_PASSWORD);
                requestBody.put(additionalField, additionalValue);

                Response response = register(requestBody);

                assertSuccessfulRegistrationResponse(response);
                assertRegisteredUserPersistedInDatabase(
                                VALID_NAME,
                                email);
        }
}