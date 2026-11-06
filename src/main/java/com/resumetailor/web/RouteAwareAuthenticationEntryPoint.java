package com.resumetailor.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides what an anonymous request to a protected address gets:
 * <ul>
 *   <li>an address no controller handles: 404, so unknown paths are not mistaken
 *       for pages behind the login (they used to redirect to the login form);</li>
 *   <li>an API endpoint: 401, rendered as JSON by the error page;</li>
 *   <li>any other page: the usual redirect to the login form.</li>
 * </ul>
 * Every path still needs a signed-in user unless SecurityConfig permits it; this only
 * changes the response to a request that was already refused.
 */
public class RouteAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ListableBeanFactory beanFactory;
    private final AuthenticationEntryPoint loginEntryPoint;
    private volatile Collection<RequestMappingInfoHandlerMapping> handlerMappings;

    public RouteAwareAuthenticationEntryPoint(ListableBeanFactory beanFactory, String loginPage) {
        this.beanFactory = beanFactory;
        this.loginEntryPoint = new LoginUrlAuthenticationEntryPoint(loginPage);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        if (!routeExists(request)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else if (RequestPaths.isApi(request)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            loginEntryPoint.commence(request, response, authException);
        }
    }

    /** True when a controller (or actuator endpoint) maps this path, whatever the method. */
    boolean routeExists(HttpServletRequest request) {
        // The lookup stores the parsed path and the match in request attributes; drop them
        // afterwards so the error dispatch or the login redirect starts clean.
        Set<String> attributesBefore = new HashSet<>(Collections.list(request.getAttributeNames()));
        Object previousPath = request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
        try {
            ServletRequestPathUtils.parseAndCache(request);
            for (RequestMappingInfoHandlerMapping mapping : mappings()) {
                try {
                    if (mapping.getHandler(request) != null) return true;
                } catch (Exception e) {
                    // The path matched but the method, content type or parameters did not.
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            // When in doubt, keep the old behaviour: send the visitor to the login page.
            return true;
        } finally {
            for (String name : Collections.list(request.getAttributeNames())) {
                if (!attributesBefore.contains(name)) request.removeAttribute(name);
            }
            if (previousPath != null) {
                request.setAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE, previousPath);
            } else {
                request.removeAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
            }
        }
    }

    private Collection<RequestMappingInfoHandlerMapping> mappings() {
        Collection<RequestMappingInfoHandlerMapping> current = handlerMappings;
        if (current == null) {
            current = beanFactory.getBeansOfType(RequestMappingInfoHandlerMapping.class).values();
            handlerMappings = current;
        }
        return current;
    }
}
