package app.openlosa.feed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

@Component
class FeedJobsFileParser {

    private final ObjectReader objectReader;

    FeedJobsFileParser(ObjectMapper objectMapper) {
        // Without strict detection Jackson keeps the last duplicate key,
        // silently dropping the earlier entry.
        this.objectReader = objectMapper.reader()
            .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    ParsedFeedSnapshot parse(Path path) throws FeedIngestException {
        try {
            BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class);
            byte[] content = Files.readAllBytes(path);
            BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class);
            if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
                throw new FeedIngestException("jobs.json changed while it was being read");
            }

            JsonNode root = objectReader.readTree(content);
            if (root == null || !root.isObject()) {
                throw new FeedIngestException("jobs.json must contain an object keyed by engine id");
            }

            List<FeedJobSnapshot> jobs = new ArrayList<>();
            Set<String> closedEngineIds = new HashSet<>();
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (!entry.getValue().isObject()) {
                    throw new FeedIngestException("Job '" + entry.getKey() + "' must be an object");
                }
                JsonNode job = entry.getValue();
                JsonNode openNode = job.get("is_open");
                if (openNode == null || !openNode.isBoolean()) {
                    throw new FeedIngestException("Job '" + entry.getKey() + "' has no boolean is_open");
                }
                if (!openNode.booleanValue()) {
                    // Closed entries may be sparse, so only the key is trusted.
                    closedEngineIds.add(entry.getKey());
                    continue;
                }

                String engineId = requiredText(job, "id", 255, entry.getKey());
                if (!engineId.equals(entry.getKey())) {
                    throw new FeedIngestException(
                        "Job key '" + entry.getKey() + "' does not match id '" + engineId + "'"
                    );
                }

                jobs.add(new FeedJobSnapshot(
                    engineId,
                    requiredText(job, "company", 255, engineId),
                    requiredText(job, "title", 500, engineId),
                    requiredText(job, "url", 2048, engineId),
                    optionalText(job, "location", 1000, engineId),
                    requiredText(job, "source", 80, engineId),
                    optionalText(job, "sponsorship", 40, engineId),
                    optionalDate(job, "posted_at", engineId)
                ));
            }

            return new ParsedFeedSnapshot(
                sha256(content),
                LocalDateTime.ofInstant(after.lastModifiedTime().toInstant(), ZoneOffset.UTC),
                List.copyOf(jobs),
                Set.copyOf(closedEngineIds)
            );
        } catch (FeedIngestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FeedIngestException("Could not read jobs.json: " + exception.getMessage(), exception);
        }
    }

    private String requiredText(JsonNode job, String field, int maxLength, String engineId)
        throws FeedIngestException {
        String value = optionalText(job, field, maxLength, engineId);
        if (value == null) {
            throw new FeedIngestException("Open job '" + engineId + "' has no " + field);
        }
        return value;
    }

    private String optionalText(JsonNode job, String field, int maxLength, String engineId)
        throws FeedIngestException {
        JsonNode node = job.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new FeedIngestException("Job '" + engineId + "' field " + field + " must be text");
        }
        String value = node.textValue().trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new FeedIngestException(
                "Job '" + engineId + "' field " + field + " exceeds " + maxLength + " characters"
            );
        }
        return value;
    }

    private LocalDate optionalDate(JsonNode job, String field, String engineId)
        throws FeedIngestException {
        String value = optionalText(job, field, 40, engineId);
        if (value == null) {
            return null;
        }
        if (value.length() < 10) {
            throw new FeedIngestException("Job '" + engineId + "' has invalid " + field);
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException exception) {
            throw new FeedIngestException("Job '" + engineId + "' has invalid " + field, exception);
        }
    }

    private String sha256(byte[] content) throws FeedIngestException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new FeedIngestException("SHA-256 is unavailable", exception);
        }
    }
}
