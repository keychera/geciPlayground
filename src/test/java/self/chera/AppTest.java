package self.chera;

import static org.junit.Assert.*;

import io.grpc.MethodDescriptor;
import org.junit.Test;
import self.chera.proto.CheraHandlerGrpc;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unit test for simple App.
 */
public class AppTest {
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue() {
        assertTrue(true);
    }

    @Test
    public void testRpcActionGenerator() {
        Class<CheraHandlerGrpc> handler = CheraHandlerGrpc.class;
        List<Field> allMethodDescriptor = Arrays.stream(handler.getDeclaredFields())
                .filter(t -> t.getType().isAssignableFrom(MethodDescriptor.class)).collect(Collectors.toList());
        for (Field declaredField : allMethodDescriptor) {
            ParameterizedType types = null;
            String serviceName = null;
            try {
                MethodDescriptor<?, ?> test = (MethodDescriptor<?, ?>) declaredField.get(handler);
                serviceName = test.getBareMethodName();
                types = (ParameterizedType) declaredField.getGenericType();
            } catch (Exception e) {
                e.printStackTrace();
                fail();
            }
            String requestName = types.getActualTypeArguments()[0].getTypeName().split("\\$")[1];
            String responseName = types.getActualTypeArguments()[1].getTypeName().split("\\$")[1];

            System.out.printf("%s %s %s%n", serviceName, requestName, responseName);
        }
    }
}
