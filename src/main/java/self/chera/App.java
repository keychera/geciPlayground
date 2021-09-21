package self.chera;

import self.chera.proto.Takarakane;
import you.waltz.proto.Waltz;

import java.util.List;

/**
 * Hello world!
 *
 */
public class App
{
    private int example;

    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );

        var nums = List.of(5,6);
        nums.forEach(i -> {
            var field = Takarakane.Dragon.getDescriptor()
                    .getFields()
                    .get(i);
            System.out.println(field);
            System.out.println(field.getEnumType().getFullName());
            var container = field.getEnumType().getContainingType();
            System.out.println("proto pkg: " + Takarakane.class.getCanonicalName());
            if (container != null) {
                System.out.println("container: "+ container.getName());
            }
        });
    }
}
