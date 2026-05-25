package maru.platform.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PropertiesResult {

    private String refrc1; // property key
    private String refrc2; // 실제 적용값
    private String refrc3; // DB 기본값 (null 가능)

    public String resolveValue(String annotationDefault) {
        if (isPresent(refrc2)) return refrc2;
        if (isPresent(refrc3)) return refrc3;
        return annotationDefault;
    }

    private boolean isPresent(String val) {
        return val != null && !val.isBlank();
    }
}
