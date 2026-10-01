package com.raghu.pilliongo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Patches column constraints on startup that spring.jpa.hibernate.ddl-auto=update
 * can never fix on its own. That setting only ADDS new tables/columns — it
 * never loosens a constraint on a column that already exists — so two real
 * bugs came from it:
 *
 *   1. rider_id / driver_id on `rides` were created NOT NULL back when
 *      every ride had both a rider and (once accepted) a driver. Once
 *      drivers/riders could post an open offer for the other side to
 *      book (offerPlannedRide, offerInstantRide), rows started needing
 *      rider_id or driver_id to be NULL, and MySQL rejected the insert
 *      with "Column 'rider_id' cannot be null".
 *
 *   2. status was created as a native MySQL ENUM with a fixed value
 *      list. When Ride.RideStatus later gained EXPIRED, the Java side
 *      knew about it but the DB column's allowed values didn't change,
 *      so MySQL threw "Data truncated for column 'status'" the first
 *      time anything tried to save that status.
 *
 *   3. route_locations.name was originally UNIQUE platform-wide — one
 *      fixed fare per place name, period. That meant a place could only
 *      ever be fixed-priced from ONE origin (e.g. only "LPU Main Gate ->
 *      Butani Colony", never ALSO a separate fixed fare for "Meheru ->
 *      Butani Colony"), and every "Add Route" for an already-named place
 *      failed with a bare "Destination already exists" no matter what
 *      "from" was typed. The real unique identity of a fixed-fare row is
 *      the (name, fromLocation) PAIR, now enforced in AdminService instead
 *      — but ddl-auto=update never drops an old constraint, so the legacy
 *      single-column unique index has to be found and dropped by name
 *      here (dropUniqueNameIndex below), since Hibernate auto-generates
 *      that name and it isn't known in advance.
 *
 * Each statement here is idempotent (MODIFY COLUMN re-applying the same
 * definition is a no-op), so this safely runs on every boot instead of
 * requiring a one-off manual ALTER TABLE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaPatchRunner implements ApplicationRunner {

    private final DataSource dataSource;

    private static final String[] PATCHES = {
            "ALTER TABLE rides MODIFY COLUMN rider_id BIGINT NULL",
            "ALTER TABLE rides MODIFY COLUMN driver_id BIGINT NULL",
            "ALTER TABLE rides MODIFY COLUMN status VARCHAR(20) NOT NULL",

            // route_locations.from_location is a brand-new nullable column
            // (added by ddl-auto=update). Any row that predates it (e.g. the
            // original "Green Valley Main Gate" entry) comes back with NULL
            // until this backfills it — running this again once nothing is
            // NULL is just a no-op, so it's safe on every boot.
            "UPDATE route_locations SET from_location = 'LPU University Main Gate' WHERE from_location IS NULL",

            // vehicles.primary_vehicle is a brand-new nullable column — normalize
            // any pre-existing rows to false first (the Java side reads this as
            // a primitive boolean, so a NULL would NPE on unboxing), then give
            // every driver who has vehicles but no primary one an actual primary
            // (their earliest-added vehicle), so the dashboard/profile sync has
            // something to point at instead of nothing.
            "UPDATE vehicles SET primary_vehicle = 0 WHERE primary_vehicle IS NULL",
            "UPDATE vehicles v " +
                    "JOIN (SELECT driver_id, MIN(id) AS min_id FROM vehicles GROUP BY driver_id) f ON v.id = f.min_id " +
                    "LEFT JOIN (SELECT DISTINCT driver_id FROM vehicles WHERE primary_vehicle = 1) p ON p.driver_id = v.driver_id " +
                    "SET v.primary_vehicle = 1 " +
                    "WHERE p.driver_id IS NULL",

            // rides.paid is a brand-new nullable column on an existing,
            // populated table — same NULL-into-primitive-boolean NPE risk
            // as primary_vehicle above, so every pre-existing ride gets
            // backfilled to "not yet paid" rather than left NULL.
            "UPDATE rides SET paid = 0 WHERE paid IS NULL",
    };

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection()) {
            dropUniqueNameIndex(conn);

            try (Statement stmt = conn.createStatement()) {
                for (String sql : PATCHES) {
                    try {
                        stmt.execute(sql);
                        log.info("[SchemaPatch] applied: {}", sql);
                    } catch (Exception e) {
                        log.warn("[SchemaPatch] skipped ({}): {}", sql, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[SchemaPatch] could not connect to apply schema patches: {}", e.getMessage());
        }
    }

    // Finds and drops whatever Hibernate auto-named the old single-column
    // unique index on route_locations.name (its generated name isn't
    // predictable, so it can't just be hard-coded like the PATCHES above).
    // Only touches an index that covers name AND NOTHING ELSE — a future
    // composite index that happens to include name is left alone. A no-op
    // once the index is gone, so this is safe to run on every boot.
    private void dropUniqueNameIndex(Connection conn) {
        String findSql =
                "SELECT s.INDEX_NAME FROM information_schema.STATISTICS s " +
                "WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'route_locations' " +
                "  AND s.COLUMN_NAME = 'name' AND s.NON_UNIQUE = 0 AND s.INDEX_NAME <> 'PRIMARY' " +
                "  AND (SELECT COUNT(*) FROM information_schema.STATISTICS s2 " +
                "       WHERE s2.TABLE_SCHEMA = s.TABLE_SCHEMA AND s2.TABLE_NAME = s.TABLE_NAME " +
                "         AND s2.INDEX_NAME = s.INDEX_NAME) = 1";
        try (Statement findStmt = conn.createStatement();
             ResultSet rs = findStmt.executeQuery(findSql)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                try (Statement dropStmt = conn.createStatement()) {
                    dropStmt.execute("ALTER TABLE route_locations DROP INDEX `" + indexName + "`");
                    log.info("[SchemaPatch] dropped legacy unique index on route_locations.name: {}", indexName);
                } catch (Exception e) {
                    log.warn("[SchemaPatch] could not drop index {}: {}", indexName, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[SchemaPatch] could not inspect route_locations indexes: {}", e.getMessage());
        }
    }
}
