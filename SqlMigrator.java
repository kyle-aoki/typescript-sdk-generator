
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SqlMigrator {
    
    private String migrationDirectory;

    public SqlMigrator setMigrationDirectory(String migrationDirectory) {
        this.migrationDirectory = migrationDirectory;
        return this;
    }

    public static class SqlMigratorException extends Exception {
        public SqlMigratorException(String message) {
            super(message);
        }
    }

    public void migrate(DataSource dataSource) throws SqlMigratorException {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS _migrations (filename text primary key)");
            List<String> migrationList = jdbcTemplate.query(
                    "select filename from _migrations",
                    (rs, _) -> rs.getString("filename")
            );
            Set<String> migrationSet = new HashSet<>(migrationList);
            List<Path> rawPaths = Files.list(Path.of(this.migrationDirectory)).toList();
            Map<String, Path> pathMap = rawPaths
                    .stream()
                    .collect(Collectors.toMap(path -> path.getFileName().toString(), Function.identity()));
            List<String> files = rawPaths
                    .stream()
                    .filter(path -> !Files.isDirectory(path))
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
            for (String file : files) {
                if (migrationSet.contains(file)) {
                    System.out.println("⬜️ " + file);
                    continue;
                }
                StringBuilder sql = new StringBuilder();
                sql.append("BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;");
                sql.append("INSERT INTO _migrations (filename) VALUES ('?');".replace("?", file));
                sql.append(Files.readString(pathMap.get(file)));
                sql.append("COMMIT;");
                Instant i1 = Instant.now();
                try {
                    jdbcTemplate.execute(sql.toString());
                } catch (DataAccessException e) {
                    System.out.println("🟥 " + file);
                    System.out.println("migration failed: \n\n" + sql);
                    e.printStackTrace();
                    System.exit(1);
                }
                Duration executionTime = Duration.between(i1, Instant.now());
                System.out.println("🟩 " + file + " :: ran in " + executionTime.toMillis() + "ms");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SqlMigratorException(e.getMessage());
        }
    }
}
