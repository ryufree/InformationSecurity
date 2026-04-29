package com.chipset.example.model;

/**
 * SYS_CONFIG 테이블 매핑 모델
 * MyBatis map-underscore-to-camel-case=true 적용
 */
public class SysConfig {

    private Long   configId;
    private String profile;
    private String propKey;
    private String propValue;
    private String dataType;   // STRING / BOOLEAN / INTEGER / CRON
    private String editableYn; // Y=런타임 변경 가능
    private String restartYn;  // Y=서버 재시작 시에만 반영(이전 불가)
    private String description;
    private String useYn;

    // ── 편의 변환 메서드 ────────────────────────────────────────────

    /** 런타임 변경 가능 여부 */
    public boolean isEditable() { return "Y".equalsIgnoreCase(editableYn); }

    /** 서버 재시작이 필요한 인프라 설정 여부 (DB 이전 불가) */
    public boolean requiresRestart() { return "Y".equalsIgnoreCase(restartYn); }

    /** propValue → boolean 변환 (true/Y/1 → true) */
    public boolean asBoolean() {
        if (propValue == null) return false;
        return "true".equalsIgnoreCase(propValue)
            || "Y".equalsIgnoreCase(propValue)
            || "1".equals(propValue.trim());
    }

    /** propValue → int 변환 */
    public int asInt(int defaultVal) {
        try { return Integer.parseInt(propValue); }
        catch (Exception e) { return defaultVal; }
    }

    /** propValue → String (null 방어) */
    public String asString(String defaultVal) {
        return (propValue != null && !propValue.isBlank()) ? propValue : defaultVal;
    }

    // ── Getters / Setters ───────────────────────────────────────────

    public Long   getConfigId()   { return configId; }
    public String getProfile()    { return profile; }
    public String getPropKey()    { return propKey; }
    public String getPropValue()  { return propValue; }
    public String getDataType()   { return dataType; }
    public String getEditableYn() { return editableYn; }
    public String getRestartYn()  { return restartYn; }
    public String getDescription(){ return description; }
    public String getUseYn()      { return useYn; }

    public void setConfigId(Long configId)      { this.configId = configId; }
    public void setProfile(String profile)      { this.profile = profile; }
    public void setPropKey(String propKey)      { this.propKey = propKey; }
    public void setPropValue(String propValue)  { this.propValue = propValue; }
    public void setDataType(String dataType)    { this.dataType = dataType; }
    public void setEditableYn(String editableYn){ this.editableYn = editableYn; }
    public void setRestartYn(String restartYn)  { this.restartYn = restartYn; }
    public void setDescription(String description){ this.description = description; }
    public void setUseYn(String useYn)          { this.useYn = useYn; }
}
