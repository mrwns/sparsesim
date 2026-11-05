package org.retrosynthesis.restapi;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps HTTP {@code QUERY} (RFC 10008: safe, idempotent, request body allowed) requests onto a handler,
 * like {@code @PostMapping} does for POST. Spring MVC has no built-in QUERY support yet, so matching is
 * done by {@link QueryMethodConfig}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequestMapping
public @interface HttpQueryMapping {

    @AliasFor(annotation = RequestMapping.class)
    String[] value() default {};
}
