package siena.base.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;

import siena.PersistenceManager;
import siena.jdbc.JdbcPersistenceManager;

/**
 * Aggregated relationship tests using H2 in-memory database.
 * Tests @Aggregated, One&lt;T&gt;, Many&lt;T&gt; relationships.
 */
public class H2AggregatedTest extends BaseAggregatedTest {
    private static JdbcPersistenceManager pm;

    @Override
    public PersistenceManager createPersistenceManager(List<Class<?>> classes) throws Exception {
        if (pm == null) {
            String url = "jdbc:h2:mem:siena_aggregated_test;DB_CLOSE_DELAY=-1;MODE=MYSQL";
            String username = "sa";
            String password = "";

            Properties p = new Properties();
            p.setProperty("driver", "org.h2.Driver");
            p.setProperty("user", username);
            p.setProperty("password", password);
            p.setProperty("url", url);

            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                H2SchemaHelper.createTablesForClasses(conn, classes);
            }

            pm = new JdbcPersistenceManager();
            pm.init(p);
        }
        return pm;
    }
}
