package self.chera;

import javax0.geci.annotations.Geci;
import self.chera.grpc.CheraDummyRoaster;

/**
 * Hello world!
 *
 */
@Geci("accessor")
@Geci("builder")
public class App 
{
    private int example;
    
    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );
        CheraDummyRoaster cheraRoaster = new CheraDummyRoaster(4);
        cheraRoaster.setFirstName("Square");
        cheraRoaster.setLastName("Pootaru");
        cheraRoaster.printFullName();
    }
}
