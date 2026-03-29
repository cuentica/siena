package siena.base.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;

import siena.PersistenceManager;
import siena.jdbc.JdbcPersistenceManager;

/**
 * JDBC tests using H2 in-memory database.
 * Runs without any external database server.
 * Covers: CRUD, queries, filters, ordering, pagination, lifecycle hooks,
 * joins, key-only fetch, iteration, polymorphic models, transactions.
 */
public class H2Test extends BaseTest {
    private static JdbcPersistenceManager pm;

    @Override
    public PersistenceManager createPersistenceManager(List<Class<?>> classes) throws Exception {
        if (pm == null) {
            String url = "jdbc:h2:mem:siena_test;DB_CLOSE_DELAY=-1;MODE=MYSQL";
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

    @Override public boolean supportsAutoincrement() { return true; }
    @Override public boolean supportsMultipleKeys() { return true; }
    @Override public boolean mustFilterToOrder() { return false; }

    // H2 does not support MySQL MATCH...AGAINST full-text search
    @Override public void testSearchSingle() {}
    @Override public void testSearchSingleKeysOnly() {}
    @Override public void testSearchSingleTwice() {}
    @Override public void testSearchSingleCount() {}
}
