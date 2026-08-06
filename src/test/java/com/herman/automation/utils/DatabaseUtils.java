package com.herman.automation.utils;

import com.herman.automation.model.ScheduleData;
import com.herman.automation.model.StationData;
import com.herman.automation.model.TrainData;
import com.herman.automation.model.UserData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public final class DatabaseUtils {

    private static final String DB_URL = System.getenv().getOrDefault(
            "DB_URL",
            "jdbc:postgresql://localhost:5432/train_ticket_db");

    private static final String DB_USERNAME = System.getenv().getOrDefault(
            "DB_USERNAME",
            "postgres");

    private static final String DB_PASSWORD = System.getenv().getOrDefault(
    "DB_PASSWORD",
    "");

    private static final String FIND_USER_BY_EMAIL_SQL = """
            SELECT
                u.name,
                u.email,
                r.role_name
            FROM users u
            JOIN roles r ON u.role_id = r.id
            WHERE u.email = ?
            """;

    private static final String FIND_TRAIN_BY_CODE_SQL = """
            SELECT
                id,
                train_code,
                train_name,
                status,
                created_at,
                updated_at
            FROM trains
            WHERE train_code = ?
            """;

    private static final String FIND_TRAIN_BY_ID_SQL = """
            SELECT
                id,
                train_code,
                train_name,
                status,
                created_at,
                updated_at
            FROM trains
            WHERE id = ?
            """;

    private static final String FIND_STATION_BY_CODE_SQL = """
            SELECT
                id,
                station_code,
                station_name,
                city,
                created_at,
                updated_at,
                status
            FROM stations
            WHERE station_code = ?
            """;

    private static final String FIND_STATION_BY_ID_SQL = """
            SELECT
                id,
                station_code,
                station_name,
                city,
                created_at,
                updated_at,
                status
            FROM stations
            WHERE id = ?
            """;

    private static final String FIND_SCHEDULE_BY_ID_SQL = """
            SELECT
                id,
                train_id,
                origin_station_id,
                destination_station_id,
                departure_time,
                arrival_time,
                price,
                status,
                created_at,
                updated_at
            FROM schedules
            WHERE id = ?
            """;

    private DatabaseUtils() {
        // Utility class
    }

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to connect to database", exception);
        }
    }

    public static UserData getUserByEmail(String email) {
        try (
                Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_USER_BY_EMAIL_SQL)) {
            statement.setString(1, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return new UserData(
                        resultSet.getString("name"),
                        resultSet.getString("email"),
                        resultSet.getString("role_name"));
            }
        } catch (SQLException exception) {
            throw new RuntimeException(
                    "Failed to retrieve user by email from database",
                    exception);
        }
    }

    public static TrainData getTrainByCode(String trainCode) {
        return findTrain(
                FIND_TRAIN_BY_CODE_SQL,
                statement -> statement.setString(1, trainCode),
                "Failed to retrieve train by code from database");
    }

    public static TrainData getTrainById(Long id) {
        Objects.requireNonNull(id, "Train ID must not be null");

        return findTrain(
                FIND_TRAIN_BY_ID_SQL,
                statement -> statement.setLong(1, id),
                "Failed to retrieve train by ID from database");
    }

    private static TrainData findTrain(
            String sql,
            StatementParameterSetter parameterSetter,
            String errorMessage) {
        try (
                Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            parameterSetter.setParameters(statement);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return mapTrain(resultSet);
            }
        } catch (SQLException exception) {
            throw new RuntimeException(errorMessage, exception);
        }
    }

    private static TrainData mapTrain(ResultSet resultSet) throws SQLException {
        return new TrainData(
                resultSet.getLong("id"),
                resultSet.getString("train_code"),
                resultSet.getString("train_name"),
                resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime());
    }

    public static StationData getStationByCode(String stationCode) {
        return findStation(
                FIND_STATION_BY_CODE_SQL,
                statement -> statement.setString(1, stationCode),
                "Failed to retrieve station by code from database");
    }

    public static StationData getStationById(Long id) {
        Objects.requireNonNull(id, "Station ID must not be null");

        return findStation(
                FIND_STATION_BY_ID_SQL,
                statement -> statement.setLong(1, id),
                "Failed to retrieve station by ID from database");
    }

    private static StationData findStation(
            String sql,
            StatementParameterSetter parameterSetter,
            String errorMessage) {
        try (
                Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            parameterSetter.setParameters(statement);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return mapStation(resultSet);
            }
        } catch (SQLException exception) {
            throw new RuntimeException(errorMessage, exception);
        }
    }

    private static StationData mapStation(ResultSet resultSet) throws SQLException {
        return new StationData(
                resultSet.getLong("id"),
                resultSet.getString("station_code"),
                resultSet.getString("station_name"),
                resultSet.getString("city"),
                resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime());
    }

    public static ScheduleData getScheduleById(Long id) {
        Objects.requireNonNull(id, "Schedule ID must not be null");

        return findSchedule(
                FIND_SCHEDULE_BY_ID_SQL,
                statement -> statement.setLong(1, id),
                "Failed to retrieve schedule by ID from database");
    }

    private static ScheduleData findSchedule(
            String sql,
            StatementParameterSetter parameterSetter,
            String errorMessage) {
        try (
                Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            parameterSetter.setParameters(statement);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return mapSchedule(resultSet);
            }
        } catch (SQLException exception) {
            throw new RuntimeException(errorMessage, exception);
        }
    }

    private static ScheduleData mapSchedule(ResultSet resultSet)
            throws SQLException {

        return new ScheduleData(
                resultSet.getLong("id"),
                resultSet.getLong("train_id"),
                resultSet.getLong("origin_station_id"),
                resultSet.getLong("destination_station_id"),
                resultSet.getTimestamp("departure_time").toLocalDateTime(),
                resultSet.getTimestamp("arrival_time").toLocalDateTime(),
                resultSet.getBigDecimal("price"),
                resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime());
    }

    @FunctionalInterface
    private interface StatementParameterSetter {
        void setParameters(PreparedStatement statement) throws SQLException;
    }
}