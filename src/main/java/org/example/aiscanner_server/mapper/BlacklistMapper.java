package org.example.aiscanner_server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.aiscanner_server.model.entity.BlacklistEntry;

import java.util.List;

@Mapper
public interface BlacklistMapper {

    int insert(BlacklistEntry entry);

    BlacklistEntry selectByAuthorAndType(@Param("authorId") String authorId,
                                          @Param("listType") String listType);

    List<BlacklistEntry> selectByType(@Param("listType") String listType);

    int deleteByAuthorAndType(@Param("authorId") String authorId,
                               @Param("listType") String listType);

    List<BlacklistEntry> selectAll();
}
