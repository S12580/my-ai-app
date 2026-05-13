package com.ai.app.rag.controller;

import com.ai.app.chat.dto.PageResponse;
import com.ai.app.rag.dto.RagAskRequest;
import com.ai.app.rag.dto.RagAskResponse;
import com.ai.app.rag.dto.RagCitation;
import com.ai.app.rag.dto.RagDocumentResponse;
import com.ai.app.rag.service.RagRetrievalResult;
import com.ai.app.rag.service.RagService;
import com.ai.app.service.BailianService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final int MAX_REFERENCE_SOURCES = 5;

    private final RagService ragService;
    private final BailianService bailianService;

    @Value("${bailian.api.model}")
    private String defaultModel;

    @Value("${bailian.api.max-tokens}")
    private int defaultMaxTokens;

    public RagController(RagService ragService, BailianService bailianService) {
        this.ragService = ragService;
        this.bailianService = bailianService;
    }

    @PostMapping("/documents")
    public RagDocumentResponse upload(@RequestParam("file") MultipartFile file) {
        return ragService.uploadAndIngest(file);
    }

    @GetMapping("/documents")
    public PageResponse<RagDocumentResponse> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ragService.listDocuments(page, size);
    }

    /** Lightweight id list for chat RAG (full scope without loading every row). */
    @GetMapping("/document-ids")
    public List<Long> listDocumentIds() {
        return ragService.listDocumentIds();
    }

    @DeleteMapping("/documents/{id}")
    public void deleteDocument(@PathVariable("id") Long id) {
        ragService.deleteKbDocument(id);
    }

    @PostMapping("/ask")
    public RagAskResponse ask(@RequestBody @Valid RagAskRequest request) {
        RagRetrievalResult retrieval = ragService.retrieve(
                request.getQuestion().trim(),
                request.getDocumentIds(),
                request.getTopK());

        List<Map<String, Object>> messages = new ArrayList<>();
        if (!retrieval.getContextText().isEmpty()) {
            Map<String, Object> system = new HashMap<>();
            system.put("role", "system");
            system.put("content", "你是知识库问答助手。优先依据给定参考资料回答，若资料不足请明确说不知道。\n\n参考资料:\n"
                    + retrieval.getContextText());
            messages.add(system);
        }
        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", request.getQuestion().trim());
        messages.add(user);

        String answer = bailianService.chat(messages, defaultModel, defaultMaxTokens);
        List<RagCitation> cites = retrieval.getCitations() == null ? List.of() : retrieval.getCitations();
        String answerWithCitations = appendCitations(answer, cites);

        RagAskResponse response = new RagAskResponse();
        response.setAnswer(answerWithCitations);
        response.setCitations(new ArrayList<>(cites));
        return response;
    }

    private String appendCitations(String answer, List<RagCitation> citations) {
        String part = RagCitation.formatReferenceAppendix(citations, MAX_REFERENCE_SOURCES);
        if (part.isEmpty()) {
            return answer;
        }
        return (answer == null ? "" : answer.trim()) + part;
    }
}
