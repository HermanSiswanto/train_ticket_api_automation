package com.herman.automation.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.qameta.allure.Allure;
import io.restassured.response.Response;

public final class AllureApiReporter {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                        .enable(SerializationFeature.INDENT_OUTPUT);

        private AllureApiReporter() {
        }

        public static void attachApiExchange(
                        String method,
                        String endpoint,
                        Object requestBody,
                        Response response) {

                attachRequest(method, endpoint, requestBody);
                attachStatus(response);
                attachResponse(response);
        }

        private static void attachRequest(
                        String method,
                        String endpoint,
                        Object requestBody) {

                String content = """
                                %s %s

                                Body:
                                %s
                                """.formatted(
                                method,
                                endpoint,
                                formatRequestBody(requestBody));

                Allure.addAttachment(
                                "Request",
                                "application/json",
                                content);
        }

        private static void attachStatus(Response response) {

                Allure.addAttachment(
                                "HTTP Status",
                                "text/plain",
                                response.getStatusLine());
        }

        private static void attachResponse(Response response) {

                String responseBody = response.getBody().asString();

                Allure.addAttachment(
                                "Response Body",
                                isJsonResponse(response)
                                                ? "application/json"
                                                : "text/plain",
                                responseBody.isBlank()
                                                ? "(empty response body)"
                                                : response.getBody().asPrettyString());
        }

        private static String formatRequestBody(Object requestBody) {

                if (requestBody == null) {
                        return "(no request body)";
                }

                try {
                        return OBJECT_MAPPER.writeValueAsString(requestBody);
                } catch (JsonProcessingException e) {
                        return requestBody.toString();
                }
        }

        private static boolean isJsonResponse(Response response) {

                String contentType = response.getContentType();

                return contentType != null
                                && contentType.contains("json");
        }
}