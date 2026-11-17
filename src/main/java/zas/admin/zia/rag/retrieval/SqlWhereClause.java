package zas.admin.zia.rag.retrieval;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL {@code WHERE} clause shared by the JDBC retrievers: visible documents (public, plus the private
 * documents of {@link SearchFilter#userId()} when set), metadata filters and content filter. Every client value (metadata keys included) is bound as a parameter.
 */
final class SqlWhereClause {

    private interface Binder {
        void bind(Connection connection, PreparedStatement ps, int index) throws SQLException;
    }

    private final String sql;
    private final List<Binder> binders;

    private SqlWhereClause(String sql, List<Binder> binders) {
        this.sql = sql;
        this.binders = binders;
    }

    static SqlWhereClause of(SearchFilter filter) {
        StringBuilder sql = new StringBuilder();
        List<Binder> binders = new ArrayList<>();

        // Public documents ('user_uuid' metadata empty or absent), plus the private documents of the
        // requesting user when one is given.
        if (filter.hasUserId()) {
            String userId = filter.userId();
            sql.append("WHERE (metadata->>'user_uuid' IS NULL OR metadata->>'user_uuid' = '' "
                    + "OR metadata->>'user_uuid' = ?) ");
            binders.add((c, ps, i) -> ps.setString(i, userId));
        } else {
            sql.append("WHERE (metadata->>'user_uuid' IS NULL OR metadata->>'user_uuid' = '') ");
        }

        for (Map.Entry<String, List<String>> entry : filter.metadata().entrySet()) {
            String key = entry.getKey();
            Object[] values = entry.getValue().toArray();
            sql.append("AND metadata->>? = ANY(?) ");
            binders.add((c, ps, i) -> ps.setString(i, key));
            binders.add((c, ps, i) -> ps.setArray(i, c.createArrayOf("text", values)));
        }

        if (filter.hasContentFilter()) {
            String pattern = "%" + escapeLike(filter.contentContains()) + "%";
            sql.append("AND content ILIKE ? ESCAPE '\\' ");
            binders.add((c, ps, i) -> ps.setString(i, pattern));
        }

        return new SqlWhereClause(sql.toString(), List.copyOf(binders));
    }

    String sql() {
        return sql;
    }

    /**
     * Binds the clause parameters starting at {@code startIndex}.
     *
     * @return the next free parameter index
     */
    int bind(Connection connection, PreparedStatement ps, int startIndex) throws SQLException {
        int index = startIndex;
        for (Binder binder : binders) {
            binder.bind(connection, ps, index++);
        }
        return index;
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
