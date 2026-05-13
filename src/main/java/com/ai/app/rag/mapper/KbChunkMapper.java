package com.ai.app.rag.mapper;

import com.ai.app.rag.domain.KbChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbChunkMapper {

    int insert(KbChunk chunk);

    int deleteByDocumentId(@Param("documentId") Long documentId);

    List<KbChunk> findByDocumentIds(@Param("documentIds") List<Long> documentIds);
}
