package com.chipset.example.mapper;

import com.chipset.example.model.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysConfigMapper {

    /** 프로파일 + 키로 단건 조회 */
    SysConfig selectByProfileAndKey(@Param("profile") String profile,
                                    @Param("propKey") String propKey);

    /** 특정 프로파일의 전체 설정 조회 (common 포함) */
    List<SysConfig> selectByProfile(@Param("profile") String profile);

    /** EDITABLE_YN='Y' 인 설정만 조회 (런타임 변경 대상) */
    List<SysConfig> selectEditableByProfile(@Param("profile") String profile);

    /** 전체 조회 (관리 화면용) */
    List<SysConfig> selectAll();

    /** 런타임 값 업데이트 */
    int updateValue(@Param("profile") String profile,
                    @Param("propKey") String propKey,
                    @Param("propValue") String propValue);
}
