package ch.nacht.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
public class PingController {

    private final DataSource dataSource;

    public PingController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");

        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                response.put("database", "available");
            } else {
                response.put("database", "unavailable");
            }
        } catch (Exception e) {
            response.put("database", "unavailable");
            response.put("error", e.getMessage());
        }

        return response;
    }
}
