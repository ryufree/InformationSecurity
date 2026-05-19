package maru.platform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import maru.platform.annotation.DbValue;
import maru.platform.dto.PropertiesResult;
import maru.platform.mapper.PropertiesMapper;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class DbValueBeanPostProcessor implements BeanPostProcessor {

    private final PropertiesMapper propertiesMapper;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        ReflectionUtils.doWithFields(
            bean.getClass(),
            field -> {
                DbValue annotation = field.getAnnotation(DbValue.class);
                if (annotation == null) return;

                String key = annotation.value();
                PropertiesResult result = propertiesMapper.findByKey(key);

                // 우선순위: refrc2(현재값) > refrc3(DB기본값) > annotation.defaultValue()
                String rawValue = (result != null)
                    ? result.resolveValue(annotation.defaultValue())
                    : annotation.defaultValue();

                field.setAccessible(true);
                field.set(bean, convert(rawValue, field.getType(), key));
            },
            field -> field.isAnnotationPresent(DbValue.class)
        );

        return bean;
    }

    private Object convert(String rawValue, Class<?> type, String key) {
        try {
            if (type == int.class || type == Integer.class) {
                return rawValue.isBlank() ? 0 : Integer.parseInt(rawValue.trim());
            }
            if (type == long.class || type == Long.class) {
                return rawValue.isBlank() ? 0L : Long.parseLong(rawValue.trim());
            }
            if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(rawValue.trim());
            }
            return rawValue;
        } catch (NumberFormatException e) {
            log.warn("@DbValue 변환 실패 — key: {}, value: '{}', type: {}", key, rawValue, type.getSimpleName());
            throw e;
        }
    }
}
