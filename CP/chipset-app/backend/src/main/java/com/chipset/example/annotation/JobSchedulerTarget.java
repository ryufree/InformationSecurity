package com.chipset.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobSchedulerTarget {
    String enabled() default "true";  // DB key 또는 "${...}" 형식
    String cron()    default "";      // DB key 또는 "${...}" 형식
}
