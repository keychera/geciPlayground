package self.chera;

import org.junit.Assert;
import org.junit.Test;
import self.chera.grpc.CheraManualRPC;

import static org.junit.Assert.fail;

public class CheraTest {
    @Test
    public void testCheraUniverse() {
        try {
            CheraManualRPC.CreateUniverse createUniverse = new CheraManualRPC.CreateUniverse();

            createUniverse.requestBuilder.setCount(0).setName("square");
            createUniverse.clientBuilder.setHeader("authorization", "waltz");

            createUniverse.exec();
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testGeneratedCheraUniverse() {
        try {
            generated.grpc.CheraRPC.CreateUniverse createUniverse = new generated.grpc.CheraRPC.CreateUniverse();

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
