package org.example.aiscanner_server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.aiscanner_server.model.entity.ApiKey;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ApiKeyMapper {

    int insert(ApiKey apiKey);

    ApiKey selectById(@Param("id") Long id);

    ApiKey selectByKeyValue(@Param("keyValue") String keyValue);

    ApiKey selectByDeviceId(@Param("deviceId") String deviceId);

    List<ApiKey> selectAll();

    int update(ApiKey apiKey);

    int updateLastUsedAt(@Param("id") Long id, @Param("lastUsedAt") LocalDateTime lastUsedAt);

    int revoke(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt);

    int deleteById(@Param("id") Long id);
}
