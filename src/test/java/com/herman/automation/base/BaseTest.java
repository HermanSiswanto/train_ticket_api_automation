package com.herman.automation.base;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.BeforeAll;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;

public class BaseTest {

    protected static final AllureRestAssured allureFilter = new AllureRestAssured()
            .setRequestAttachmentName("API Request")
            .setResponseAttachmentName("API Response");

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost:8080";

        try {
            Path source = Path.of("src/test/resources/environment.properties");
            Path target = Path.of("allure-results/environment.properties");

            Files.createDirectories(target.getParent());

            Files.copy(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println(
                    "Unable to copy Allure environment.properties: "
                            + e.getMessage());
        }
    }
}