package maru.platform.config;

import maru.platform.annotation.DbValue;
import maru.platform.dto.PropertiesResult;
import maru.platform.mapper.PropertiesMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * DbValueBeanPostProcessor (ValueBeanPostProcessor.java) 단위 테스트
 *
 * [검증 항목]
 *   Positive: 타입별 변환(String/int/long/boolean), fallback 우선순위(refrc2→refrc3→defaultValue),
 *             프로파일 해석('common' fallback), @DbValue 없는 필드 무시, blank → 0 방어
 *   Negative: 숫자 변환 실패(NumberFormatException), DB null + defaultValue 없음 → 빈 문자열,
 *             boolean 비표준 문자열('yes') → false
 *
 * [ObjectProvider 설정]
 *   ObjectProvider<PropertiesMapper> 는 @InjectMocks 로 자동 주입되지 않으므로
 *   @BeforeEach 에서 processor 를 직접 생성한다.
 *   propertiesMapperProvider.getObject() stub 은 lenient() 로 선언한다.
 *   → p07(@DbValue 없는 필드 무시) 테스트에서 provider 가 호출되지 않아
 *     STRICT_STUBS 위반이 발생하는 것을 방지하기 위함.
 */
@ExtendWith(MockitoExtension.class)
class ValueBeanPostProcessorTest {

    @Mock private PropertiesMapper                  propertiesMapper;
    @Mock private ObjectProvider<PropertiesMapper>  propertiesMapperProvider;
    @Mock private Environment                       environment;

    private DbValueBeanPostProcessor processor;

    @BeforeEach
    void setUp() {
        lenient().when(propertiesMapperProvider.getObject()).thenReturn(propertiesMapper);
        processor = new DbValueBeanPostProcessor(propertiesMapperProvider, environment);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** DB refrc2(현재 적용값)가 있는 PropertiesResult */
    private PropertiesResult dbResult(String refrc2) {
        PropertiesResult r = new PropertiesResult();
        ReflectionTestUtils.setField(r, "refrc2", refrc2);
        return r;
    }

    /** DB refrc2 없고 refrc3(DB 기본값)만 있는 PropertiesResult */
    private PropertiesResult dbResultFallback(String refrc3) {
        PropertiesResult r = new PropertiesResult();
        ReflectionTestUtils.setField(r, "refrc3", refrc3);
        return r;
    }

    // ── POSITIVE ────────────────────────────────────────────────────────

    @Test
    @DisplayName("[P] String 필드 — DB refrc2 값으로 주입됨")
    void p01_String_DB_refrc2_주입() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"dev"});
        given(propertiesMapper.findByKey("app.title", "dev")).willReturn(dbResult("Hello DB"));

        StringBean bean = new StringBean();
        processor.postProcessBeforeInitialization(bean, "stringBean");

        assertThat(bean.title).isEqualTo("Hello DB");
    }

    @Test
    @DisplayName("[P] int 필드 — DB 값 정수 변환 성공")
    void p02_int_DB_정수_변환() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.timeout", "common")).willReturn(dbResult("30"));

        IntBean bean = new IntBean();
        processor.postProcessBeforeInitialization(bean, "intBean");

        assertThat(bean.timeout).isEqualTo(30);
    }

    @Test
    @DisplayName("[P] long 필드 — DB 값 Long 변환 성공")
    void p03_long_DB_Long_변환() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.interval", "common")).willReturn(dbResult("9999999999"));

        LongBean bean = new LongBean();
        processor.postProcessBeforeInitialization(bean, "longBean");

        assertThat(bean.interval).isEqualTo(9999999999L);
    }

    @Test
    @DisplayName("[P] boolean 필드 — DB 값 'true' → true 변환")
    void p04_boolean_true_변환() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.enabled", "common")).willReturn(dbResult("true"));

        BooleanBean bean = new BooleanBean();
        processor.postProcessBeforeInitialization(bean, "booleanBean");

        assertThat(bean.enabled).isTrue();
    }

    @Test
    @DisplayName("[P] DB null → annotation defaultValue 사용됨 (코드레벨 fallback)")
    void p05_DB_null_annotation_default_사용() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.title", "common")).willReturn(null);

        StringBean bean = new StringBean();
        processor.postProcessBeforeInitialization(bean, "stringBean");

        assertThat(bean.title).isEqualTo("default-title");
    }

    @Test
    @DisplayName("[P] DB refrc2 blank, refrc3 있음 → refrc3 값으로 주입됨 (fallback 우선순위)")
    void p06_refrc2_blank_refrc3_fallback() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.title", "common")).willReturn(dbResultFallback("DB-default"));

        StringBean bean = new StringBean();
        processor.postProcessBeforeInitialization(bean, "stringBean");

        assertThat(bean.title).isEqualTo("DB-default");
    }

    @Test
    @DisplayName("[P] @DbValue 없는 필드 — Mapper 호출 없이 무시됨")
    void p07_no_annotation_필드_무시() {
        NoAnnotationBean bean = new NoAnnotationBean();
        processor.postProcessBeforeInitialization(bean, "noAnnotationBean");

        assertThat(bean.value).isNull();
        then(propertiesMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("[P] int 필드 — DB blank 값 → 0 반환 (isBlank 방어 처리)")
    void p08_int_blank_DB값_0_반환() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.timeout", "common")).willReturn(dbResult("   "));

        IntBean bean = new IntBean();
        processor.postProcessBeforeInitialization(bean, "intBean");

        assertThat(bean.timeout).isEqualTo(0);
    }

    @Test
    @DisplayName("[P] long 필드 — DB blank 값 → 0L 반환")
    void p09_long_blank_DB값_0L_반환() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.interval", "common")).willReturn(dbResult(""));

        LongBean bean = new LongBean();
        processor.postProcessBeforeInitialization(bean, "longBean");

        assertThat(bean.interval).isEqualTo(0L);
    }

    @Test
    @DisplayName("[P] 활성 프로파일 있을 때 → 해당 프로파일로 DB 조회")
    void p10_active_profile_사용() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"prod"});
        given(propertiesMapper.findByKey("app.title", "prod")).willReturn(dbResult("Prod Title"));

        StringBean bean = new StringBean();
        processor.postProcessBeforeInitialization(bean, "stringBean");

        then(propertiesMapper).should().findByKey("app.title", "prod");
        assertThat(bean.title).isEqualTo("Prod Title");
    }

    @Test
    @DisplayName("[P] 활성 프로파일 없을 때 → 'common' 으로 DB 조회 (기본값)")
    void p11_no_profile_common_fallback() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.title", "common")).willReturn(dbResult("Common Title"));

        StringBean bean = new StringBean();
        processor.postProcessBeforeInitialization(bean, "stringBean");

        then(propertiesMapper).should().findByKey("app.title", "common");
    }

    // ── NEGATIVE ────────────────────────────────────────────────────────

    @Test
    @DisplayName("[N] int 필드 — 숫자 아닌 문자열 → NumberFormatException 발생")
    void n01_int_숫자아닌값_예외() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.timeout", "common")).willReturn(dbResult("not-a-number"));

        IntBean bean = new IntBean();
        assertThatThrownBy(() ->
            processor.postProcessBeforeInitialization(bean, "intBean")
        ).isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("[N] long 필드 — 숫자 아닌 문자열 → NumberFormatException 발생")
    void n02_long_숫자아닌값_예외() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.interval", "common")).willReturn(dbResult("INVALID"));

        LongBean bean = new LongBean();
        assertThatThrownBy(() ->
            processor.postProcessBeforeInitialization(bean, "longBean")
        ).isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("[N] int 필드 — 부동소수점 문자열 → NumberFormatException 발생")
    void n03_int_부동소수점_예외() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.timeout", "common")).willReturn(dbResult("3.14"));

        IntBean bean = new IntBean();
        assertThatThrownBy(() ->
            processor.postProcessBeforeInitialization(bean, "intBean")
        ).isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("[N] DB null + @DbValue defaultValue 없음 → 빈 문자열('') 로 주입됨")
    void n04_DB_null_no_default_빈문자열_주입() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.nodefault", "common")).willReturn(null);

        NoDefaultBean bean = new NoDefaultBean();
        processor.postProcessBeforeInitialization(bean, "noDefaultBean");

        assertThat(bean.value).isEmpty();
    }

    @Test
    @DisplayName("[N] boolean 필드 — 'yes' 문자열 → false (Boolean.parseBoolean 은 'true' 만 true)")
    void n05_boolean_yes_문자열_false() {
        given(environment.getActiveProfiles()).willReturn(new String[]{});
        given(propertiesMapper.findByKey("app.enabled", "common")).willReturn(dbResult("yes"));

        BooleanBean bean = new BooleanBean();
        processor.postProcessBeforeInitialization(bean, "booleanBean");

        assertThat(bean.enabled).isFalse();
    }

    // ── Test Beans ──────────────────────────────────────────────────────

    static class StringBean {
        @DbValue(value = "app.title", defaultValue = "default-title")
        String title;
    }

    static class IntBean {
        @DbValue(value = "app.timeout")
        int timeout;
    }

    static class LongBean {
        @DbValue(value = "app.interval")
        long interval;
    }

    static class BooleanBean {
        @DbValue(value = "app.enabled")
        boolean enabled;
    }

    static class NoAnnotationBean {
        String value;
    }

    static class NoDefaultBean {
        @DbValue(value = "app.nodefault")
        String value;
    }
}
