package self.chera;

import org.junit.Assert;
import org.junit.Test;
import self.chera.generated.grpc.CheraRPC;

import static org.junit.Assert.fail;

public class CheraTest {
    @Test
    public void testGeneratedCheraUniverse() {
        try {
            CheraRPC.CreateUniverse createUniverse = new CheraRPC.CreateUniverse();

            createUniverse.requestBuilder.setCount(0).setName("square");
            createUniverse.clientBuilder.setHeader("authorization", "waltz");

            createUniverse.exec();
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(true);
        }
    }
}
