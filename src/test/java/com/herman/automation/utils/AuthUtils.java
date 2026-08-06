package com.herman.automation.utils;

import io.restassured.http.ContentType;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

public final class AuthUtils {

    private static final String LOGIN_ENDPOINT = "/api/auth/login";

    private static final Credentials ADMIN_CREDENTIALS = new Credentials(
            getEnvironmentValue("ADMIN_EMAIL", "admin@trainticket.com"),
            getEnvironmentValue("ADMIN_PASSWORD", "Admin@123"));

    private static final Credentials USER_CREDENTIALS = new Credentials(
            getEnvironmentValue("USER_EMAIL", "user@trainticket.com"),
            getEnvironmentValue("USER_PASSWORD", "User@123"));

    private static final long EXPIRED_TOKEN_WAIT_TIME_MS = 60_000L;

    private AuthUtils() {
        // Utility class
    }

    public static String loginAsAdmin() {
        return login(ADMIN_CREDENTIALS);
    }

    public static String loginAsUser() {
        return login(USER_CREDENTIALS);
    }

    public static String getExpiredAdminToken() {
        return getExpiredToken(ADMIN_CREDENTIALS);
    }

    public static String getExpiredUserToken() {
        return getExpiredToken(USER_CREDENTIALS);
    }

    private static String login(Credentials credentials) {
        Map<String, String> requestBody = Map.of(
                "email", credentials.email(),
                "password", credentials.password());

        return given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(requestBody)
                .when()
                .post(LOGIN_ENDPOINT)
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .body("token", notNullValue())
                .extract()
                .path("token");
    }

    private static String getExpiredToken(Credentials credentials) {
        String token = login(credentials);

        waitUntilTokenExpires();

        return token;
    }

    private static void waitUntilTokenExpires() {
        try {
            Thread.sleep(EXPIRED_TOKEN_WAIT_TIME_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "Interrupted while waiting for token expiration",
                    exception);
        }
    }

    private static String getEnvironmentValue(
            String environmentVariable,
            String defaultValue) {
        return System.getenv().getOrDefault(
                environmentVariable,
                defaultValue);
    }

    private record Credentials(String email, String password) {
    }
}