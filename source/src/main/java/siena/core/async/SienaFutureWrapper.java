package siena.core.async;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A Future wrapper that transforms the result type from K to V.
 * Originally based on GAE's FutureWrapper, now implemented with plain java.util.concurrent.
 *
 * @param <K> the source type
 * @param <V> the target type
 */
abstract public class SienaFutureWrapper<K, V> implements Future<V> {

    private final Future<K> base;

    public SienaFutureWrapper(Future<K> base) {
        this.base = base;
    }

    protected abstract V wrap(K result) throws Exception;

    protected Throwable convertException(Throwable cause) {
        return cause;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return base.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return base.isCancelled();
    }

    @Override
    public boolean isDone() {
        return base.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        try {
            return wrap(base.get());
        } catch (ExecutionException e) {
            throw new ExecutionException(convertException(e.getCause()));
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new ExecutionException(e);
        }
    }

    @Override
    public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return wrap(base.get(timeout, unit));
        } catch (ExecutionException e) {
            throw new ExecutionException(convertException(e.getCause()));
        } catch (InterruptedException | TimeoutException e) {
            throw e;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new ExecutionException(e);
        }
    }
}
