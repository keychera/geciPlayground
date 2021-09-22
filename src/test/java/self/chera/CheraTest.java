package self.chera;

import org.junit.Assert;
import org.junit.Test;
import self.chera.generated.grpc.CheraGrpc;
import self.chera.generated.grpc.TakaraKaneGrpc;

import static org.junit.Assert.fail;
import static self.chera.generated.grpc.CheraGrpc.cheraGrpc;
import static self.chera.generated.grpc.TakaraKaneGrpc.takaraKaneGrpc;

public class CheraTest {
    @Test
    public void testGeneratedCheraUniverse() {
        try {
            var createUniverse = new CheraGrpc.CreateUniverse();
            createUniverse.requestBuilder.setCount(0).setName("square");
            createUniverse.clientBuilder.setHeader("authorization", "waltz");
            createUniverse.exec();

            cheraGrpc().createUniverse(0L, null);

            var submergeShip = new TakaraKaneGrpc.SubmergeShip();
            submergeShip.clientBuilder.setHeader("authorization", "waltz");
            submergeShip.exec();

            takaraKaneGrpc()
                    .withHeader("deviceId", "")
                    .submergeShip(null, null, null, null, null, null, null);
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testGeneratedStaticMethod() {
        try {
            takaraKaneGrpc().sailThe(null, null, null, null, null, null, null, null, null, null);
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(true);
        }
    }
}
