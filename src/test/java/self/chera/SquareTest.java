package self.chera;

import org.junit.Test;
import self.chera.generated.grpc.SquareGrpc;

import java.util.List;

public class SquareTest {
    @Test
    public void doClientStream() {
        var redStream = new SquareGrpc.RedStream();

        redStream.prepareStream();

        List.of("square", "white", "trouble", "black", "pesimus", "diane", "chera")
                .forEach(name -> {
                    redStream.requestBuilder.setCount(0).setName(name);
                    redStream.supplyRequestToStream();
                });

        var response = redStream.completeStream();
        assert response != null;
    }
}
