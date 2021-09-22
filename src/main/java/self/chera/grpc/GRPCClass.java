package self.chera.grpc;

import com.linecorp.armeria.client.ClientBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public class GRPCClass<T> {
    protected static List<Consumer<ClientBuilder>> clientMod = new ArrayList<>();

    public T header(Consumer<ClientBuilder> headerMod) {
        clientMod.add(headerMod);
        return (T) this;
    }

    public T withHeader(CharSequence name, Object value) {
        clientMod.add(c -> c.addHeader(name, value));
        return (T) this;
    }
}
