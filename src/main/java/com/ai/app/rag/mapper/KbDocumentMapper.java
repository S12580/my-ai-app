package com.ai.app.rag.mapper;

import com.ai.app.rag.domain.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentMapper {

    int insert(KbDocument document);

    int updateStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage);

    int updateSourcePath(@Param("id") Long id, @Param("sourcePath") String sourcePath);

    int deleteById(@Param("id") Long id);

    List<Long> findIdsByName(@Param("name") String name);

    KbDocument findById(Long id);

    List<KbDocument> findAll();

    long countAll();

    List<KbDocument> findPage(@Param("offset") int offset, @Param("limit") int limit);

    List<Long> findAllIds();
}
