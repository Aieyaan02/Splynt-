package com.aieyaan.splynt.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
public class SecurityAuditFilter extends OncePerRequestFilter {
 private final JdbcTemplate db;
 public SecurityAuditFilter(JdbcTemplate db){this.db=db;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String path=request.getRequestURI();boolean login=path.equals("/api/auth/login");boolean admin=path.startsWith("/api/platform/");
  if(!login&&!admin){chain.doFilter(request,response);return;}
  Long actor=null;var authentication=SecurityContextHolder.getContext().getAuthentication();
  if(authentication!=null)try{actor=Long.parseLong(authentication.getName());}catch(NumberFormatException ignored){}
  // Fixed action categories only: never record submitted credentials, URLs, query strings or codes.
  String action=login?"PASSWORD_LOGIN":path.startsWith("/api/platform/mfa")?"ADMIN_MFA":"ADMIN_ACCESS";
  try{chain.doFilter(request,response);}finally{
   db.update("insert into security_audit(occurred_at,actor_id,action,status) values (?,?,?,?)",OffsetDateTime.now(),actor,action,response.getStatus());
  }
 }
}
