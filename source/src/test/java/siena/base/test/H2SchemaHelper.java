package siena.base.test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

import siena.*;
import siena.core.DecimalPrecision;
import siena.core.Polymorphic;
import siena.embed.Embedded;

/**
 * Helper to generate H2-compatible CREATE TABLE statements from Siena model classes.
 * Replaces DDLUtils which doesn't support H2.
 */
public class H2SchemaHelper {

    public static void createTablesForClasses(Connection conn, List<Class<?>> classes) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            for (Class<?> clazz : classes) {
                if (!Modifier.isAbstract(clazz.getModifiers())) {
                    String sql = generateCreateTable(clazz);
                    stmt.execute(sql);
                }
            }
        }
    }

    static void createTables(Statement stmt) throws Exception {
        // This method is kept for backward compatibility but prefer createTablesForClasses
    }

    static String generateCreateTable(Class<?> clazz) {
        ClassInfo info = ClassInfo.getClassInfo(clazz);
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(info.tableName).append(" (");

        boolean first = true;
        String primaryKey = null;
        boolean autoIncrement = false;

        for (Field field : info.allFields) {
            if (Modifier.isTransient(field.getModifiers())) continue;
            if (field.getAnnotation(siena.Ignore.class) != null) continue;

            String[] columns = ClassInfo.getColumnNames(field);
            Class<?> type = field.getType();

            // Skip Many/One relationship fields (they don't map to columns directly)
            if (siena.core.Many.class.isAssignableFrom(type)) continue;
            if (siena.core.One.class.isAssignableFrom(type)) continue;

            for (String colName : columns) {
                if (!first) sb.append(", ");
                first = false;

                sb.append(colName).append(" ");
                sb.append(sqlType(field, type));

                if (field.getAnnotation(NotNull.class) != null || type.isPrimitive()) {
                    sb.append(" NOT NULL");
                }

                Id id = field.getAnnotation(Id.class);
                if (id != null) {
                    primaryKey = colName;
                    // H2 only supports AUTO_INCREMENT on numeric columns
                    if (id.value() == Generator.AUTO_INCREMENT &&
                            (type == Long.class || type == long.class ||
                             type == Integer.class || type == int.class)) {
                        sb.append(" AUTO_INCREMENT");
                        autoIncrement = true;
                    }
                }

                if (field.getAnnotation(Unique.class) != null) {
                    sb.append(" UNIQUE");
                }
            }
        }

        if (primaryKey != null) {
            sb.append(", PRIMARY KEY (").append(primaryKey).append(")");
        }

        sb.append(")");
        return sb.toString();
    }

    private static String sqlType(Field field, Class<?> type) {
        if (field.getAnnotation(Polymorphic.class) != null) return "BLOB";
        if (field.getAnnotation(Embedded.class) != null) return "TEXT";

        if (type == String.class) {
            if (field.getAnnotation(Text.class) != null) return "TEXT";
            Max max = field.getAnnotation(Max.class);
            int len = (max != null) ? max.value() : 255;
            return "VARCHAR(" + len + ")";
        }
        if (type == Long.class || type == long.class) return "BIGINT";
        if (type == Integer.class || type == int.class) return "INT";
        if (type == Short.class || type == short.class) return "SMALLINT";
        if (type == Byte.class || type == byte.class) return "TINYINT";
        if (type == Float.class || type == float.class) return "FLOAT";
        if (type == Double.class || type == double.class) return "DOUBLE";
        if (type == Boolean.class || type == boolean.class) return "BOOLEAN";
        if (type == Date.class) {
            if (field.getAnnotation(SimpleDate.class) != null) return "DATE";
            if (field.getAnnotation(Time.class) != null) return "TIME";
            return "TIMESTAMP";
        }
        if (type == java.time.LocalDateTime.class) return "TIMESTAMP";
        if (type == java.time.LocalDate.class) return "DATE";
        if (type == byte[].class) return "BLOB";
        if (type == Json.class) return "TEXT";
        if (type == BigDecimal.class) {
            DecimalPrecision dp = field.getAnnotation(DecimalPrecision.class);
            if (dp != null) {
                if (dp.storageType() == DecimalPrecision.StorageType.STRING) return "VARCHAR(50)";
                if (dp.storageType() == DecimalPrecision.StorageType.DOUBLE) return "DOUBLE";
                return "DECIMAL(" + dp.size() + "," + dp.scale() + ")";
            }
            return "DECIMAL(19,2)";
        }
        if (type.isEnum()) return "VARCHAR(100)";

        // Model references (foreign keys) - store as the type of the referenced model's ID
        if (siena.Model.class.isAssignableFrom(type)) {
            return "BIGINT"; // Most common ID type
        }

        return "VARCHAR(255)";
    }
}
