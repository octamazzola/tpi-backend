package utnfc.isi.back.spring.geoapi.exception;

public class OsrmUnavailableException extends RuntimeException {
    public OsrmUnavailableException(String message) {
        super(message);
    }

    public OsrmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
