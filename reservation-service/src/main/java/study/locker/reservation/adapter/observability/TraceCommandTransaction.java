package study.locker.reservation.adapter.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;

import org.springframework.jdbc.core.simple.JdbcClient;

import study.locker.reservation.application.CommandHandler;

import java.util.HashMap;
import java.util.function.Supplier;

/** PostgreSQL resets this setting at transaction end, including on rollback. */
public record TraceCommandTransaction(JdbcClient db, CommandHandler delegate)
        implements CommandHandler {
    @Override
    public Execution execute(Command command, Supplier<Result> work) {
        if (Span.current().getSpanContext().isSampled()) {
            var headers = new HashMap<String, String>();
            W3CTraceContextPropagator.getInstance()
                    .inject(Context.current(), headers, HashMap::put);
            db.sql("select set_config('locker.trace_parent', ?, true)")
                    .param(headers.get("traceparent"))
                    .query(String.class)
                    .single();
        }
        return delegate.execute(command, work);
    }
}
