package org.retrosynthesis.restapi;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/** Restricts {@link HttpQueryMapping} handlers to the HTTP QUERY method. */
@Configuration
public class QueryMethodConfig {

    static final String QUERY = "QUERY";

    @Bean
    public WebMvcRegistrations queryMethodRegistrations() {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new RequestMappingHandlerMapping() {
                    @Override
                    protected RequestCondition<?> getCustomMethodCondition(Method method) {
                        return method.isAnnotationPresent(HttpQueryMapping.class) ? QueryMethodCondition.INSTANCE : null;
                    }
                };
            }
        };
    }

    enum QueryMethodCondition implements RequestCondition<QueryMethodCondition> {
        INSTANCE;

        @Override
        public QueryMethodCondition combine(QueryMethodCondition other) {
            return this;
        }

        @Override
        public QueryMethodCondition getMatchingCondition(HttpServletRequest request) {
            return QUERY.equals(request.getMethod()) ? this : null;
        }

        @Override
        public int compareTo(QueryMethodCondition other, HttpServletRequest request) {
            return 0;
        }
    }
}
