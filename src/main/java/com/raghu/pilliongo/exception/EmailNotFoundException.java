package com.raghu.pilliongo.exception;

// Thrown specifically by the forgot-password flow when no account exists
// for the given email. Kept distinct from the generic RuntimeException
// (which GlobalExceptionHandler maps to 400 everywhere else in this app)
// so only this one case returns 404, without changing any other
// endpoint's existing error behavior.
public class EmailNotFoundException extends RuntimeException {
    public EmailNotFoundException(String message) {
        super(message);
    }
}
