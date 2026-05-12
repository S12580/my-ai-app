package com.ai.app.chat.mapper;

import com.ai.app.chat.domain.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    int insert(ChatSession session);

    int updateTitle(@Param("id") Long id, @Param("title") String title);

    int touchUpdatedAt(Long id);

    int deleteById(Long id);

    ChatSession findById(Long id);

    long countAll();

    List<ChatSession> findPage(@Param("offset") int offset, @Param("limit") int limit);
}
