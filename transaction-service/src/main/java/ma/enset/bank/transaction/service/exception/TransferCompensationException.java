package ma.enset.bank.transaction.service.exception;

import ma.enset.bank.transaction.domain.TransferStatus;

public class TransferCompensationException extends RuntimeException {

    private final TransferStatus status;

    public TransferCompensationException(TransferStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public TransferStatus getStatus() {
        return status;
    }
}
