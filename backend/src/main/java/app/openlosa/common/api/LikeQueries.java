package app.openlosa.common.api;

public final class LikeQueries {

    public static final char ESCAPE = '\\';

    private LikeQueries() {
    }

    public static String contains(String value) {
        var escaped = value.trim().toLowerCase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
