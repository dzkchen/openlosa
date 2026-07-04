package app.openlosa.application;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.springframework.util.StringUtils;

import app.openlosa.application.dto.ApplicationCreateRequest;
import app.openlosa.common.api.BadRequestException;

final class ApplicationCsvParser {

    static final List<String> TEMPLATE_HEADERS = List.of(
        "companyName",
        "companyWebsite",
        "companyNotes",
        "roleTitle",
        "postingUrl",
        "location",
        "status",
        "appliedAt",
        "source",
        "salaryText",
        "notes",
        "favorite",
        "tags"
    );

    private static final int COMPANY_NAME_MAX_LENGTH = 255;
    private static final int COMPANY_WEBSITE_MAX_LENGTH = 2048;
    private static final int ROLE_TITLE_MAX_LENGTH = 255;
    private static final int POSTING_URL_MAX_LENGTH = 2048;
    private static final int LOCATION_MAX_LENGTH = 255;
    private static final int SALARY_TEXT_MAX_LENGTH = 255;
    private static final int TAG_NAME_MAX_LENGTH = 80;

    private ApplicationCsvParser() {
    }

    static int parse(Reader reader, Consumer<ImportedApplicationRow> rowConsumer) throws IOException {
        var state = new CsvParseState(rowConsumer);
        parseRecords(reader, state::accept);
        return state.importedRows();
    }

    private static ImportedApplicationRow toApplicationRow(CsvRecord record) {
        if (record.values().size() != TEMPLATE_HEADERS.size()) {
            throw new BadRequestException(
                "Row " + record.rowNumber() + " has " + record.values().size()
                    + " columns; expected " + TEMPLATE_HEADERS.size()
            );
        }

        var values = record.values();
        var request = new ApplicationCreateRequest(
            null,
            cleanRequiredLimited(values.get(0), "companyName", record.rowNumber(), COMPANY_NAME_MAX_LENGTH),
            cleanLimited(values.get(1), "companyWebsite", record.rowNumber(), COMPANY_WEBSITE_MAX_LENGTH),
            clean(values.get(2)),
            cleanRequiredLimited(values.get(3), "roleTitle", record.rowNumber(), ROLE_TITLE_MAX_LENGTH),
            cleanLimited(values.get(4), "postingUrl", record.rowNumber(), POSTING_URL_MAX_LENGTH),
            cleanLimited(values.get(5), "location", record.rowNumber(), LOCATION_MAX_LENGTH),
            parseEnum(values.get(6), ApplicationStatus.class, "status", record.rowNumber()),
            parseDate(values.get(7), "appliedAt", record.rowNumber()),
            parseEnum(values.get(8), ApplicationSource.class, "source", record.rowNumber()),
            cleanLimited(values.get(9), "salaryText", record.rowNumber(), SALARY_TEXT_MAX_LENGTH),
            clean(values.get(10)),
            parseBoolean(values.get(11), "favorite", record.rowNumber())
        );

        return new ImportedApplicationRow(request, parseTags(values.get(12), record.rowNumber()));
    }

    private static void validateHeader(CsvRecord record) {
        var actual = record.values().stream()
            .map(String::trim)
            .toList();

        if (!actual.isEmpty() && actual.getFirst().startsWith("\uFEFF")) {
            var first = actual.getFirst().substring(1);
            var cleaned = new ArrayList<>(actual);
            cleaned.set(0, first);
            actual = cleaned;
        }

        if (!actual.equals(TEMPLATE_HEADERS)) {
            throw new BadRequestException("CSV header must be: " + String.join(",", TEMPLATE_HEADERS));
        }
    }

    private static List<String> parseTags(String value, int rowNumber) {
        var cleaned = clean(value);
        if (cleaned == null) {
            return List.of();
        }

        var tagNames = new ArrayList<String>();
        var seen = new HashSet<String>();
        for (var part : cleaned.split(";")) {
            var tagName = cleanLimited(part, "tags", rowNumber, TAG_NAME_MAX_LENGTH);
            if (tagName == null) {
                continue;
            }

            if (seen.add(tagName.toLowerCase(Locale.ROOT))) {
                tagNames.add(tagName);
            }
        }

        return tagNames;
    }

    private static LocalDate parseDate(String value, String fieldName, int rowNumber) {
        var cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }

        try {
            return LocalDate.parse(cleaned);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("Row " + rowNumber + " has invalid " + fieldName + " '" + cleaned + "'; use YYYY-MM-DD");
        }
    }

    private static Boolean parseBoolean(String value, String fieldName, int rowNumber) {
        var cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }

        return switch (cleaned.toLowerCase(Locale.ROOT)) {
            case "true", "t", "yes", "y", "1" -> true;
            case "false", "f", "no", "n", "0" -> false;
            default -> throw new BadRequestException(
                "Row " + rowNumber + " has invalid " + fieldName + " '" + cleaned + "'; use true or false"
            );
        };
    }

    private static <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String fieldName, int rowNumber) {
        var cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }

        var normalized = cleaned
            .replace('-', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);

        try {
            return Enum.valueOf(enumType, normalized);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                "Row " + rowNumber + " has invalid " + fieldName + " '" + cleaned
                    + "'; use one of: " + enumValues(enumType)
            );
        }
    }

    private static <T extends Enum<T>> String enumValues(Class<T> enumType) {
        var values = new ArrayList<String>();
        for (var value : enumType.getEnumConstants()) {
            values.add(value.name());
        }
        return String.join(", ", values);
    }

    private static String cleanRequiredLimited(String value, String fieldName, int rowNumber, int maxLength) {
        var cleaned = cleanLimited(value, fieldName, rowNumber, maxLength);
        if (!StringUtils.hasText(cleaned)) {
            throw new BadRequestException("Row " + rowNumber + " is missing required " + fieldName);
        }
        return cleaned;
    }

    private static String cleanLimited(String value, String fieldName, int rowNumber, int maxLength) {
        var cleaned = clean(value);
        if (cleaned != null && cleaned.length() > maxLength) {
            throw new BadRequestException("Row " + rowNumber + " " + fieldName + " is longer than " + maxLength + " characters");
        }
        return cleaned;
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static boolean isBlankRecord(List<String> values) {
        return values.stream().noneMatch(StringUtils::hasText);
    }

    private static void parseRecords(Reader reader, Consumer<CsvRecord> recordConsumer) throws IOException {
        var values = new ArrayList<String>();
        var field = new StringBuilder();
        var inQuotes = false;
        var quotedField = false;
        var lastWasComma = false;
        var rowNumber = 1;
        var pushbackReader = new PushbackReader(reader, 1);
        int next;

        while ((next = pushbackReader.read()) != -1) {
            char current = (char) next;

            if (inQuotes) {
                if (current == '"') {
                    var afterQuote = pushbackReader.read();
                    if (afterQuote == '"') {
                        field.append('"');
                    } else {
                        if (afterQuote != -1) {
                            pushbackReader.unread(afterQuote);
                        }
                        inQuotes = false;
                    }
                } else {
                    field.append(current);
                }
                lastWasComma = false;
                continue;
            }

            if (current == '"' && field.length() == 0) {
                inQuotes = true;
                quotedField = true;
                lastWasComma = false;
            } else if (current == ',') {
                values.add(field.toString());
                field.setLength(0);
                quotedField = false;
                lastWasComma = true;
            } else if (current == '\r') {
                addRecord(recordConsumer, values, field, rowNumber);
                quotedField = false;
                lastWasComma = false;
                var afterCarriageReturn = pushbackReader.read();
                if (afterCarriageReturn != '\n' && afterCarriageReturn != -1) {
                    pushbackReader.unread(afterCarriageReturn);
                }
                rowNumber++;
            } else if (current == '\n') {
                addRecord(recordConsumer, values, field, rowNumber);
                quotedField = false;
                lastWasComma = false;
                rowNumber++;
            } else {
                field.append(current);
                lastWasComma = false;
            }
        }

        if (inQuotes) {
            throw new BadRequestException("CSV has an unterminated quoted field");
        }

        if (field.length() > 0 || !values.isEmpty() || lastWasComma || quotedField) {
            addRecord(recordConsumer, values, field, rowNumber);
        }
    }

    private static void addRecord(Consumer<CsvRecord> recordConsumer, List<String> values, StringBuilder field, int rowNumber) {
        values.add(field.toString());
        recordConsumer.accept(new CsvRecord(rowNumber, List.copyOf(values)));
        values.clear();
        field.setLength(0);
    }

    record ImportedApplicationRow(ApplicationCreateRequest request, List<String> tagNames) {
    }

    private record CsvRecord(int rowNumber, List<String> values) {
    }

    private static final class CsvParseState {

        private final Consumer<ImportedApplicationRow> rowConsumer;
        private boolean headerSeen;
        private int importedRows;

        private CsvParseState(Consumer<ImportedApplicationRow> rowConsumer) {
            this.rowConsumer = rowConsumer;
        }

        private void accept(CsvRecord record) {
            if (isBlankRecord(record.values())) {
                return;
            }

            if (!headerSeen) {
                validateHeader(record);
                headerSeen = true;
                return;
            }

            rowConsumer.accept(toApplicationRow(record));
            importedRows++;
        }

        private int importedRows() {
            if (!headerSeen) {
                throw new BadRequestException("CSV file is empty");
            }
            if (importedRows == 0) {
                throw new BadRequestException("CSV has no application rows");
            }
            return importedRows;
        }
    }
}
