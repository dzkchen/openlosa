package app.openlosa.feed;

class FeedIngestException extends Exception {

    FeedIngestException(String message) {
        super(message);
    }

    FeedIngestException(String message, Throwable cause) {
        super(message, cause);
    }
}
