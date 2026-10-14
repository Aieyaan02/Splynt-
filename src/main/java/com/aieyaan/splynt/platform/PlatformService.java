package com.aieyaan.splynt.platform;

import java.util.*;
import java.time.OffsetDateTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class PlatformService {
    private final JdbcTemplate db;
    public PlatformService(JdbcTemplate db) { this.db=db; }
    public record Overview(long organizations,long activeOrganizations,long stores,long activeStores,long users,
        long newOrganizations7Days,long newUsers7Days,long storesNeedingAttention,OffsetDateTime checkedAt,
        String applicationHealth,String databaseHealth,long uptimeSeconds) {}
    public record StoreRow(long id,String name,String organization,boolean active,String connectionStatus,
        OffsetDateTime lastSuccessfulSync,OffsetDateTime lastFailedSync,String safeErrorSummary,
        Integer lastReceivedProducts,Integer lastImportedProducts,long activeImportedProducts,int importIssues,List<String> attention) {}
    public record UserRow(long id,String email,String name,boolean enabled,OffsetDateTime registeredAt,OffsetDateTime lastLoginAt,OffsetDateTime lastActivityAt) {}
    public record OrganizationRow(long id,String name,boolean active,OffsetDateTime registeredAt,long stores,long members) {}
    public record Page<T>(List<T> items,int page,int size,long total) {}
    private static final String NEEDS_ATTENTION="""
        s.active=true and (c.id is null or c.disconnected=true or c.last_synced_at is null or c.last_synced_at < ?
        or c.last_sync_error is not null or (c.last_sync_failed_at is not null and (c.last_synced_at is null or c.last_sync_failed_at > c.last_synced_at)))
        """;
    public Overview overview() {
        OffsetDateTime now=OffsetDateTime.now();
        // Only successful database reads result in a healthy snapshot. DB errors propagate as unavailable.
        return new Overview(count("select count(*) from organizations"), count("select count(*) from organizations where active=true"),
            count("select count(*) from stores"),count("select count(*) from stores where active=true"),count("select count(*) from app_users"),
            count("select count(*) from organizations where created_at >= ?",now.minusDays(7)),
            count("select count(*) from app_users where created_at >= ?",now.minusDays(7)),
            count("select count(*) from stores s left join clover_oauth_credentials c on c.store_id=s.id where "+NEEDS_ATTENTION,now.minusHours(24)),
            now,"UP","UP",java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime()/1000);
    }
    public Page<StoreRow> stores(int page,boolean attentionOnly) {
        validate(page); OffsetDateTime cutoff=OffsetDateTime.now().minusHours(24);
        String from=" from stores s join organizations o on o.id=s.organization_id left join clover_oauth_credentials c on c.store_id=s.id";
        String where=attentionOnly ? " where "+NEEDS_ATTENTION : "";
        List<Object> parameters=new ArrayList<>(); if (attentionOnly) parameters.add(cutoff);
        long total=count("select count(*)"+from+where,parameters.toArray());
        parameters.add(25);parameters.add(page*25);
        List<StoreRow> rows=db.query("""
            select s.id,s.name,o.name as organization,s.active,c.id as connection_id,c.disconnected,c.last_synced_at,c.last_sync_failed_at,
            c.last_sync_received_count,c.last_sync_imported_count,c.inventory_issue_count,
            case when c.last_sync_error is null then false else true end as has_warning,
            (select count(*) from products p where p.store_id=s.id and p.active=true and p.source='CLOVER') as product_count
            """+from+where+" order by s.id limit ? offset ?",(rs,n) -> {
                boolean connected=rs.getObject("connection_id") != null && !rs.getBoolean("disconnected");
                var success=time(rs,"last_synced_at"); var failure=time(rs,"last_sync_failed_at");
                int issues=rs.getInt("inventory_issue_count"); boolean active=rs.getBoolean("active");
                List<String> reasons=new ArrayList<>();
                if (active) {
                    if (!connected) reasons.add("POS not connected");
                    else {
                        if (success==null) reasons.add("No successful inventory sync");
                        else if(success.isBefore(cutoff)) reasons.add("Inventory older than 24 hours");
                        if(failure!=null && (success==null || failure.isAfter(success))) reasons.add("Latest inventory sync failed");
                        else if(rs.getBoolean("has_warning")) reasons.add("Inventory import needs review");
                    }
                }
                String status=!connected ? "NOT_CONNECTED" : failure!=null && (success==null || failure.isAfter(success)) ? "SYNC_FAILED" : success==null ? "AWAITING_SYNC" : rs.getBoolean("has_warning") ? "NEEDS_REVIEW" : "CONNECTED";
                return new StoreRow(rs.getLong("id"),rs.getString("name"),rs.getString("organization"),active,status,success,failure,
                    failure==null ? null : "Inventory sync failed. Check provider access and retry; no raw provider error is exposed.",
                    (Integer)rs.getObject("last_sync_received_count"),(Integer)rs.getObject("last_sync_imported_count"),rs.getLong("product_count"),issues,List.copyOf(reasons));
            },parameters.toArray());
        return new Page<>(rows,page,25,total);
    }
    public Page<UserRow> users(int page) {
        validate(page);
        var rows=db.query("select id,email,first_name,last_name,enabled,created_at,last_login_at,last_activity_at from app_users order by created_at desc,id desc limit 25 offset ?",(rs,n) -> new UserRow(rs.getLong("id"),rs.getString("email"),rs.getString("first_name")+" "+rs.getString("last_name"),rs.getBoolean("enabled"),time(rs,"created_at"),time(rs,"last_login_at"),time(rs,"last_activity_at")),page*25);
        return new Page<>(rows,page,25,count("select count(*) from app_users"));
    }
    public Page<OrganizationRow> organizations(int page) {
        validate(page);
        var rows=db.query("""
            select o.id,o.name,o.active,o.created_at,
            (select count(*) from stores s where s.organization_id=o.id) as stores,
            (select count(*) from organization_memberships m where m.organization_id=o.id and m.active=true) as members
            from organizations o order by o.created_at desc,o.id desc limit 25 offset ?
            """,(rs,n) -> new OrganizationRow(rs.getLong("id"),rs.getString("name"),rs.getBoolean("active"),time(rs,"created_at"),rs.getLong("stores"),rs.getLong("members")),page*25);
        return new Page<>(rows,page,25,count("select count(*) from organizations"));
    }
    public record AuditRow(long id,OffsetDateTime occurredAt,Long actorId,String action,int status) {}
    public Page<AuditRow> audit(int page) {
        validate(page);
        var rows=db.query("select id,occurred_at,actor_id,action,status from security_audit order by id desc limit 25 offset ?",(rs,n)->new AuditRow(rs.getLong("id"),time(rs,"occurred_at"),(Long)rs.getObject("actor_id"),rs.getString("action"),rs.getInt("status")),page*25);
        return new Page<>(rows,page,25,count("select count(*) from security_audit"));
    }
    private long count(String sql,Object...args) { return Objects.requireNonNull(db.queryForObject(sql,Long.class,args)); }
    private static OffsetDateTime time(ResultSet rs,String column) throws SQLException { return rs.getObject(column,OffsetDateTime.class); }
    private void validate(int page) { if(page<0 || page>100000) throw new IllegalArgumentException("Invalid page."); }
}
