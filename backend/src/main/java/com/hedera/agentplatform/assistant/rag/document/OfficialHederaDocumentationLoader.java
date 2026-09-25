package com.hedera.agentplatform.assistant.rag.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.agentplatform.assistant.rag.model.DocumentationDocument;
import com.hedera.agentplatform.assistant.rag.model.DocumentationSection;
import com.hedera.agentplatform.assistant.rag.model.DocumentationSource;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OfficialHederaDocumentationLoader implements DocumentationLoader {
    private static final Logger logger = LoggerFactory.getLogger(OfficialHederaDocumentationLoader.class);
    private final int timeoutMillis;
    private final ObjectMapper objectMapper;
    private final Path cacheDirectory;

    public OfficialHederaDocumentationLoader(
            ObjectMapper objectMapper,
            @Value("${assistant.rag.document-timeout-ms:15000}") int timeoutMillis,
            @Value("${assistant.rag.document-cache-path:data/hedera-documents}") String cachePath) {
        this.objectMapper = objectMapper;
        this.timeoutMillis = timeoutMillis;
        this.cacheDirectory = Path.of(cachePath).toAbsolutePath().normalize();
    }

    @Override
    public List<DocumentationDocument> load(List<DocumentationSource> sources) {
        List<DocumentationDocument> documents = new ArrayList<>();
        for (DocumentationSource source : sources) {
            try {
                DocumentationDocument cached = readCache(source);
                if (cached != null) {
                    documents.add(cached);
                    continue;
                }
                var response = Jsoup.connect(source.url())
                        .userAgent("HederaAssistantDocumentationIndexer/1.0")
                        .timeout(timeoutMillis)
                        .maxBodySize(2_000_000)
                        .execute();
                DocumentationDocument extracted = response.contentType() != null
                        && response.contentType().toLowerCase().contains("markdown")
                        ? extractMarkdown(source, response.body())
                        : extract(source, response.parse());
                if (extracted.sections().stream().anyMatch(section -> !section.text().isBlank())) {
                    writeCache(source, extracted);
                    documents.add(extracted);
                } else {
                    logger.warn("Skipping empty Hedera documentation page (source={})", source.url());
                }
            } catch (IOException | RuntimeException exception) {
                logger.warn(
                        "Could not load Hedera documentation page (source={}, exceptionType={})",
                        source.url(),
                        exception.getClass().getSimpleName());
            }
        }
        return List.copyOf(documents);
    }

    private DocumentationDocument readCache(DocumentationSource source) {
        Path cacheFile = cacheFile(source);
        if (!Files.isRegularFile(cacheFile)) return null;
        try {
            DocumentationDocument cached = objectMapper.readValue(cacheFile.toFile(), DocumentationDocument.class);
            if (cached != null && source.url().equals(cached.source())
                    && cached.sections().stream().anyMatch(section -> !section.text().isBlank())) {
                logger.debug("Loaded cached Hedera documentation (source={})", source.url());
                return cached;
            }
        } catch (IOException | RuntimeException exception) {
            logger.warn("Ignoring invalid cached Hedera documentation (source={}, exceptionType={})",
                    source.url(), exception.getClass().getSimpleName());
        }
        return null;
    }

    private void writeCache(DocumentationSource source, DocumentationDocument document) throws IOException {
        Files.createDirectories(cacheDirectory);
        Path cacheFile = cacheFile(source);
        Path temporaryFile = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        objectMapper.writeValue(temporaryFile.toFile(), document);
        try {
            Files.move(temporaryFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path cacheFile(DocumentationSource source) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.url().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return cacheDirectory.resolve(HexFormat.of().formatHex(hash) + ".json");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private DocumentationDocument extractMarkdown(DocumentationSource source, String markdown) {
        List<DocumentationSection> sections = new ArrayList<>();
        String title = source.title();
        String sectionTitle = "Overview";
        StringBuilder text = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                addSection(sections, sectionTitle, text);
                int markerCount = 0;
                while (markerCount < trimmed.length() && trimmed.charAt(markerCount) == '#') markerCount++;
                sectionTitle = trimmed.substring(markerCount).trim();
                if (markerCount == 1 && !sectionTitle.isBlank()) title = sectionTitle;
                text.setLength(0);
            } else if (!trimmed.isBlank()) {
                String clean = trimmed.replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")
                        .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                        .replaceAll("[`*_~]", "")
                        .replaceAll("^[-*+]\\s+", "")
                        .replaceAll("^\\d+[.)]\\s+", "");
                text.append(clean).append('\n');
            }
        }
        addSection(sections, sectionTitle, text);
        return new DocumentationDocument(title, source.url(), sections);
    }

    private DocumentationDocument extract(DocumentationSource source, Document html) {
        Element content = html.selectFirst("main");
        if (content == null) {
            content = html.body();
        }
        if (content == null) {
            return new DocumentationDocument(source.title(), source.url(), List.of());
        }
        content.select("nav, header, footer, aside, script, style, button, form").remove();
        String title = content.select("h1").first() == null
                ? source.title()
                : content.select("h1").first().text();
        List<DocumentationSection> sections = new ArrayList<>();
        String sectionTitle = "Overview";
        StringBuilder text = new StringBuilder();
        Elements blocks = content.select("h1, h2, h3, p, li, blockquote, pre");
        for (Element block : blocks) {
            if (block.tagName().matches("h[1-3]")) {
                addSection(sections, sectionTitle, text);
                sectionTitle = block.text();
                text.setLength(0);
            } else if (!block.text().isBlank()) {
                text.append(block.text()).append('\n');
            }
        }
        addSection(sections, sectionTitle, text);
        if (sections.isEmpty() && !content.text().isBlank()) {
            sections.add(new DocumentationSection("Overview", content.text()));
        }
        return new DocumentationDocument(title, source.url(), sections);
    }

    private void addSection(List<DocumentationSection> sections, String title, StringBuilder text) {
        String content = text.toString().trim();
        if (!content.isBlank()) {
            sections.add(new DocumentationSection(title, content));
        }
    }
}
