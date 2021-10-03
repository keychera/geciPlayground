package self.chera;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.DecoratingHttpClientFunction;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;

@SuppressWarnings("NullableProblems")
public class DummyDecorator implements DecoratingHttpClientFunction {
    @Override
    public HttpResponse execute(HttpClient delegate, ClientRequestContext ctx, HttpRequest req) throws Exception {
        return delegate.execute(ctx, req);
    }
}
