package com.herman.automation.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

public class TestDataGenerator {
    private static final Random RANDOM = new Random();
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private TestDataGenerator() {
        // Utility class
    }

    public static String generateUniqueEmail() {
        return "automation"
                + UUID.randomUUID().toString().substring(0, 8)
                + "@auto.com";
    }

    // ===== Train =====

    public static String generateUniqueTrainCode() {
        return "AP" + System.currentTimeMillis();
    }

    public static String generateUniqueTrainName() {
        return "Automation Train " + System.currentTimeMillis();
    }

    public static String generateNumericTrainCode() {
        String timestamp = String.valueOf(System.currentTimeMillis());

        return timestamp.substring(timestamp.length() - 6);
    }

    public static String generateAlphabeticTrainCode() {

        StringBuilder trainCode = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            int index = RANDOM.nextInt(LETTERS.length());
            trainCode.append(LETTERS.charAt(index));
        }

        return trainCode.toString();
    }

    // ===== Station =====
    public static String generateUniqueStationCode() {

        StringBuilder stationCode = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            stationCode.append(
                    LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }

        return stationCode.toString();
    }

    public static String generateUniqueStationName() {
        return "Automation Station " + System.currentTimeMillis();
    }

    public static String generateAlphabeticStationCode() {

        StringBuilder stationCode = new StringBuilder();

        for (int i = 0; i < 9; i++) {
            int index = RANDOM.nextInt(LETTERS.length());
            stationCode.append(LETTERS.charAt(index));
        }

        return stationCode.toString();
    }

    private static final String[] CITIES = {
            "Jakarta",
            "Bandung",
            "Surabaya",
            "Yogyakarta",
            "Semarang",
            "Medan",
            "Makassar",
            "Palembang",
            "Depok",
            "Tangerang"
    };

    public static String generateCity() {
        return CITIES[RANDOM.nextInt(CITIES.length)];
    }

    public static String generateUniqueLowercaseStationCode() {
        return generateUniqueStationCode().toLowerCase();
    }

    // ====== Schedule ======

    public static LocalDateTime generateDepartureTime() {
        return LocalDateTime.now()
                .plusDays(7)
                .withHour(8)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }

    public static LocalDateTime generateArrivalTime(LocalDateTime departureTime) {
        return departureTime.plusHours(2).plusMinutes(30);
    }

    public static BigDecimal generatePrice() {
        int price = RANDOM.nextInt(400_001) + 100_000; // 100.000 - 500.000
        // Set scale to 2 to match NUMERIC(12,2) -> outputs e.g., 357521.00
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
    }
}