package self.chera.grpc;

import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.DecoratingHttpClientFunction;

import static java.util.Objects.requireNonNull;

/**
 * reroute ClientBuilder's method with out own
 * so that we can slip processes in-between
 */
public class RPCClientBuilder {
    public final ClientBuilder origin = Clients.builder(RPCGlobals.defaultUrl);

    public <T> T build(Class<T> clientType) {
        requireNonNull(clientType, "RPC clientType");
        return origin.build(clientType);
    }

    public RPCClientBuilder decorator(DecoratingHttpClientFunction decorator) {
        origin.decorator(decorator);
        return this;
    }

    public RPCClientBuilder responseTimeoutMillis(long responseTimeoutMillis) {
        origin.responseTimeoutMillis(responseTimeoutMillis);
        return this;
    }

    public RPCClientBuilder addHeader(CharSequence name, Object value) {
        RPCGlobals.rpcActionListeners.forEach(listener -> listener.addHeaderListener(name, value));
        origin.addHeader(name, value);
        return this;
    }

    public RPCClientBuilder setHeader(CharSequence name, Object value) {
        RPCGlobals.rpcActionListeners.forEach(listener -> listener.setHeaderListener(name, value));
        origin.setHeader(name, value);
        return this;
    }
}
