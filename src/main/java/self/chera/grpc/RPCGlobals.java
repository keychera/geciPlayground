package self.chera.grpc;

import com.linecorp.armeria.client.ClientBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RPCGlobals {
    public static Supplier<String> defaultUrl = () -> "unset";
    public static List<Consumer<ClientBuilder>> defaultClientMod = new ArrayList<>();
}
