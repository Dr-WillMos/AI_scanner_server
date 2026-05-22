package org.example.aiscanner_server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.aiscanner_server.model.entity.DetectionRecord;

import java.util.List;

@Mapper
public interface DetectionRecordMapper {

    int insert(DetectionRecord record);

    DetectionRecord selectById(@Param("id") Long id);

    List<DetectionRecord> selectByDeviceId(@Param("deviceId") String deviceId);

    List<DetectionRecord> selectByAuthorId(@Param("authorId") String authorId);

    List<DetectionRecord> selectAll();

    /** Cursor-based: records with id > afterId, newest first, for incremental sync */
    List<DetectionRecord> selectByDeviceIdAfterId(@Param("deviceId") String deviceId,
                                                  @Param("afterId") Long afterId,
                                                  @Param("limit") int limit);

    /** Offset-based: page N of size S, newest first */
    List<DetectionRecord> selectByDeviceIdPaged(@Param("deviceId") String deviceId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    /** Total count for a device */
    long countByDeviceId(@Param("deviceId") String deviceId);
}
