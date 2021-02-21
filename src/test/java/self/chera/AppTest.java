package self.chera;

import static javax0.geci.api.Source.maven;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax0.geci.accessor.Accessor;
import javax0.geci.builder.Builder;
import javax0.geci.engine.Geci;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue()
    {
        assertTrue( true );
    }

    @Test
    public void testAccessor() throws Exception {
        Geci geci;
        Assertions.assertFalse(
                (geci = new Geci())
                        .register(Accessor.builder().build())
                        .register(Builder.builder().build())
                        .generate(),
                geci.failed());
    }
}
