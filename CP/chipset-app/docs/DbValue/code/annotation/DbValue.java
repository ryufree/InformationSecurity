package maru.platform.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DbValue {
    String value();                  // refrc1: property key
    String defaultValue() default ""; // 코드 레벨 fallback (refrc3도 없을 때)
}
