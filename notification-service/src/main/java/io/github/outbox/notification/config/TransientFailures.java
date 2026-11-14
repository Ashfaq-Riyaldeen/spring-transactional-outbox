package io.github.outbox.notification.config;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.List;

/**
 * Tells a failure that goes away by itself - the database being restarted or failing over, a connection that
 * could not be obtained - from one that fails the same way every time.
 */
final class TransientFailures {

    private static final List<Class<? extends Throwable>> TRANSIENT = List.of(
            TransientDataAccessException.class,
            RecoverableDataAccessException.class,
            DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class,
            SQLTransientException.class,
            SQLRecoverableException.class
    );

    private TransientFailures() {
    }

    /**
     * @param failure the failure, or a wrapper of it; its whole cause chain is inspected
     * @return {@code true} if the failure or any of its causes is transient; {@code false} for {@code null}
     */
    static boolean isTransient(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            for (Class<? extends Throwable> type : TRANSIENT) {
                if (type.isInstance(current)) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }
}
