package self.chera;

import org.junit.Assert;
import org.junit.Test;
import self.chera.generated.grpc.CheraGrpc;
import self.chera.generated.grpc.TakaraKaneGrpc;

import static org.junit.Assert.fail;

public class CheraTest {
    @Test
    public void testGeneratedCheraUniverse() {
        try {
            CheraGrpc.CreateUniverse createUniverse = new CheraGrpc.CreateUniverse();

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
    public void testGeneratedStaticMethod() {
        try {
            TakaraKaneGrpc.sailThe(null, null, null, null, null, null, null, null, null, null);
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(true);
        }
    }
}
