package com.aa.airchennai.airport;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Powers the From / To autocomplete on the home page.
 * GET /api/airports?q=mum  ->  [{"code":"BOM","name":"...","city":"Mumbai","country":"India"}]
 */
@RestController
@RequestMapping("/api/airports")
public class AirportController
{
    private static final int MAX_RESULTS = 8;

    public record AirportDto(String code, String name, String city, String country) {}

    private final JdbcClient jdbc;

    public AirportController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<AirportDto> search(@RequestParam(name = "q", defaultValue = "") String q) {
        String term = q.trim();
        if (term.isEmpty()) {
            return List.of();
        }

        // Escape LIKE wildcards so "100%" or "a_b" are matched literally.
        String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");

        // Ranking: exact code, then code prefix, then city prefix, then name/city contains.
        return jdbc.sql("""
                        SELECT code, name, city, country
                        FROM airport
                        WHERE active
                          AND (code ILIKE :prefix
                               OR city ILIKE :prefix
                               OR name ILIKE :contains)
                        ORDER BY CASE
                                     WHEN upper(code) = upper(:exact) THEN 0
                                     WHEN code ILIKE :prefix THEN 1
                                     WHEN city ILIKE :prefix THEN 2
                                     ELSE 3
                                 END,
                                 city, name
                        LIMIT :limit
                        """)
                .param("exact", term)
                .param("prefix", escaped + "%")
                .param("contains", "%" + escaped + "%")
                .param("limit", MAX_RESULTS)
                .query((rs, n) -> new AirportDto(
                        rs.getString("code").trim(),
                        rs.getString("name"),
                        rs.getString("city"),
                        rs.getString("country")))
                .list();
    }
}
