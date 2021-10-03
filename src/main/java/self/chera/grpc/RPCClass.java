package self.chera.grpc;

import com.linecorp.armeria.client.DecoratingHttpClientFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@SuppressWarnings("unchecked")
public class RPCClass<T> {
    protected static List<Consumer<RPCClientBuilder>> clientMod = new ArrayList<>();

    public T with(Consumer<RPCClientBuilder> headerMod) {
        clientMod.add(headerMod);
        return (T) this;
    }

    public T decorator(DecoratingHttpClientFunction decorator) {
        clientMod.add(c -> c.decorator(decorator));
        return (T) this;
    }

    public T setHeader(CharSequence name, Object value) {
        clientMod.add(c -> c.setHeader(name, value));
        return (T) this;
    }

    public T addHeader(CharSequence name, Object value) {
        clientMod.add(c -> c.addHeader(name, value));
        return (T) this;
    }
}
