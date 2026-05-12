package com.ai.app.chat.mapper;

import com.ai.app.chat.domain.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    int insert(ChatMessage message);

    List<ChatMessage> findBySessionIdAsc(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    List<ChatMessage> findBySessionIdAscBefore(
            @Param("sessionId") Long sessionId,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit);

    ChatMessage findById(Long id);

    int deleteBySessionId(Long sessionId);

    int deleteByIdAndSessionId(@Param("id") Long id, @Param("sessionId") Long sessionId);
}
