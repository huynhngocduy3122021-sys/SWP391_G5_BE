package Parking.config;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.servlet.HandlerExceptionResolver;
import Parking.Service.TokenService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import Parking.Model.User;
import java.util.List;
import java.io.IOException;
import Parking.exception.exceptions.AuthenticationException;

@Component
public class filter extends OncePerRequestFilter {
    @Autowired
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver resolver;

    @Autowired
    private TokenService tokenService;

    private final List<String> PUBLIC_API_ENDPOINTS = List.of(
               "/",
                "/index.html",
                "/favicon.ico",
                "/style.css",
                "/app.js",
                "/api/auth/register",
                "/api/auth/login",
                "/error",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/swagger-ui/index.html",
                "/v3/api-docs/**",
                "/v3/api-docs",
                "/api/parking-sessions/**",
                "/api/vehicle-types/**",
                "/api/parking-cards/**",
                "/api/parking-zones/**",
                "/api/price-policies/**",
                "/api/parking-session/**",
                "/api/auth/reset-password",
                "/api/parking-branches/**",
                "/api/parking-floors/**"     
    );

            public boolean isPublicAPI(String uri) {
                AntPathMatcher matcher = new AntPathMatcher();

                return PUBLIC_API_ENDPOINTS.stream()
                        .anyMatch(pattern -> matcher.match(pattern, uri));
            }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
         HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getServletPath();
      

        //Bỏ qua WebSocket handshake
        if (uri.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        if(isPublicAPI(uri)){
            //api public
            // tất cả access
            filterChain.doFilter(request , response);

        } else {
            //api theo role
            //check xem co quyen k
            //=> check token
            String token = getToken(request);
            if(token == null) {
                resolver.resolveException(request , response , null , new AuthenticationException("Empty token!"));
            }
            // co token
            // => verify lai cai token do
            User member = null;
            try {
                member = tokenService.extractToken(token);
            } catch (ExpiredJwtException expiredJwtException) {
                //1. token het hang
                resolver.resolveException(request , response , null , new AuthenticationException("Expired token!"));
            } catch (MalformedJwtException malformedJwtException) {
                //2. sai token
                resolver.resolveException(request, response, null, new AuthenticationException("Invalid token!"));
            }
            // luu thong tin vua request
            // luu vao session
            UsernamePasswordAuthenticationToken
                    authenToken =
                    new UsernamePasswordAuthenticationToken(member, token, member.getAuthorities());
            authenToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenToken);

            //token chuan
            //dc phep truy cap vao he thong
            filterChain.doFilter(request , response);

        }


    }


    public String getToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) return null;
        return authHeader.substring(7);
    }
}
