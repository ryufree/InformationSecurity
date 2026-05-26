package maru.platform.mapper;

import maru.platform.dto.PropertiesResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PropertiesMapper {

    PropertiesResult findByKey(@Param("key") String key, @Param("profile") String profile);
}
