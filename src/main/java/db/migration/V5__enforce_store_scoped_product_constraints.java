package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V5__enforce_store_scoped_product_constraints
        extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        dropSingleColumnUniqueConstraint(
                connection,
                "products",
                "barcode"
        );

        dropSingleColumnUniqueConstraint(
                connection,
                "products",
                "clover_item_id"
        );

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE products
                    ALTER COLUMN store_id SET NOT NULL
                    """);

            statement.execute("""
                    ALTER TABLE products
                    ADD CONSTRAINT uq_products_store_barcode
                    UNIQUE (store_id, barcode)
                    """);

            statement.execute("""
                    ALTER TABLE products
                    ADD CONSTRAINT uq_products_store_clover_item
                    UNIQUE (store_id, clover_item_id)
                    """);
        }
    }

    private void dropSingleColumnUniqueConstraint(
            Connection connection,
            String tableName,
            String columnName) throws Exception {

        String schemaName = connection.getSchema();

        if (schemaName == null || schemaName.isBlank()) {
            schemaName = "public";
        }

        String query = """
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                WHERE LOWER(tc.table_schema) = LOWER(?)
                  AND LOWER(tc.table_name) = LOWER(?)
                  AND tc.constraint_type = 'UNIQUE'
                  AND (
                      SELECT COUNT(*)
                      FROM information_schema.key_column_usage kcu
                      WHERE kcu.constraint_schema =
                            tc.constraint_schema
                        AND kcu.constraint_name =
                            tc.constraint_name
                        AND LOWER(kcu.table_name) =
                            LOWER(tc.table_name)
                  ) = 1
                  AND EXISTS (
                      SELECT 1
                      FROM information_schema.key_column_usage kcu
                      WHERE kcu.constraint_schema =
                            tc.constraint_schema
                        AND kcu.constraint_name =
                            tc.constraint_name
                        AND LOWER(kcu.table_name) =
                            LOWER(tc.table_name)
                        AND LOWER(kcu.column_name) = LOWER(?)
                  )
                """;

        List<String> constraintNames = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(query)) {

            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            statement.setString(3, columnName);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    constraintNames.add(
                            resultSet.getString("constraint_name")
                    );
                }
            }
        }

        if (constraintNames.isEmpty()) {
            throw new IllegalStateException(
                    "Could not find the single-column unique "
                            + "constraint for "
                            + tableName
                            + "."
                            + columnName
            );
        }

        for (String constraintName : constraintNames) {
            String dropStatement =
                    "ALTER TABLE "
                            + tableName
                            + " DROP CONSTRAINT "
                            + quoteIdentifier(constraintName);

            try (Statement statement =
                         connection.createStatement()) {

                statement.execute(dropStatement);
            }
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\""
                + identifier.replace("\"", "\"\"")
                + "\"";
    }
}