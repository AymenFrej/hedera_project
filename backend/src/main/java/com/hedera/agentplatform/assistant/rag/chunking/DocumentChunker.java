package com.hedera.agentplatform.assistant.rag.chunking;

import com.hedera.agentplatform.assistant.rag.model.DocumentChunk;
import com.hedera.agentplatform.assistant.rag.model.DocumentationDocument;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");

    private final int chunkWords;
    private final int overlapWords;

    public DocumentChunker(
            @Value("${assistant.rag.chunk-size:700}") int chunkWords,
            @Value("${assistant.rag.chunk-overlap:100}") int overlapWords) {
        if (chunkWords < 1 || overlapWords < 0 || overlapWords >= chunkWords) {
            throw new IllegalArgumentException("RAG chunk size must be positive and overlap must be smaller than chunk size");
        }
        this.chunkWords = chunkWords;
        this.overlapWords = overlapWords;
    }

    public List<DocumentChunk> chunk(DocumentationDocument document) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int index = 0;
        for (var section : document.sections()) {
            List<String> sentences = new ArrayList<>();
            for (String sentence : SENTENCE_BOUNDARY.split(section.text().trim())) {
                if (!sentence.isBlank()) {
                    sentences.add(sentence.trim());
                }
            }
            List<String> current = new ArrayList<>();
            int currentWords = 0;
            for (String sentence : sentences) {
                List<String> sentenceWords = words(sentence);
                if (sentenceWords.size() > chunkWords) {
                    if (!current.isEmpty()) {
                        chunks.add(toChunk(document, section.title(), current, index++));
                        current = overlap(current);
                        currentWords = current.size();
                    }
                    int start = 0;
                    while (start < sentenceWords.size()) {
                        int end = Math.min(start + chunkWords - currentWords, sentenceWords.size());
                        current.addAll(sentenceWords.subList(start, end));
                        currentWords += end - start;
                        if (currentWords >= chunkWords) {
                            chunks.add(toChunk(document, section.title(), current, index++));
                            current = overlap(current);
                            currentWords = current.size();
                        }
                        start = end;
                    }
                } else {
                    if (currentWords + sentenceWords.size() > chunkWords && !current.isEmpty()) {
                        chunks.add(toChunk(document, section.title(), current, index++));
                        current = overlap(current);
                        currentWords = current.size();
                    }
                    current.addAll(sentenceWords);
                    currentWords += sentenceWords.size();
                }
            }
            if (!current.isEmpty()) {
                chunks.add(toChunk(document, section.title(), current, index++));
            }
        }
        return List.copyOf(chunks);
    }

    private DocumentChunk toChunk(DocumentationDocument document, String section, List<String> words, int index) {
        String text = String.join(" ", words).trim();
        String seed = document.source() + "\n" + section + "\n" + index + "\n" + text;
        String chunkId = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        return new DocumentChunk(chunkId, text, document.source(), document.title(), section);
    }

    private List<String> overlap(List<String> words) {
        int from = Math.max(0, words.size() - overlapWords);
        return new ArrayList<>(words.subList(from, words.size()));
    }

    private List<String> words(String text) {
        return List.of(text.trim().split("\\s+"));
    }
}
