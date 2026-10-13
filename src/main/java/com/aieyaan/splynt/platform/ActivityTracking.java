package com.aieyaan.splynt.platform;

import java.time.OffsetDateTime;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class ActivityTracking implements WebMvcConfigurer {
    private final JdbcTemplate db;
    public ActivityTracking(JdbcTemplate db) { this.db=db; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception ex) {
                if (ex != null || response.getStatus() >= 400) return;
                var authentication=SecurityContextHolder.getContext().getAuthentication();
                if (!(authentication instanceof JwtAuthenticationToken jwt)) return;
                try {
                    OffsetDateTime now=OffsetDateTime.now();
                    db.update("update app_users set last_activity_at=? where id=? and enabled=true and (last_activity_at is null or last_activity_at < ?)",now,Long.parseLong(jwt.getName()),now.minusMinutes(5));
                } catch (NumberFormatException | org.springframework.dao.DataAccessException ignored) {
                    // Monitoring failure must not turn a successful business request into a failure.
                }
            }
        }).addPathPatterns("/api/**");
    }
}
