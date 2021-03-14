package self.chera;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;

public class RoasterTest {
    private static final String PACKAGE_LOC = "src/main/java/self/chera/grpc";

    @Test
    public void testRpcActionRoaster() {
        String className = "CheraDummyRoaster";

        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
        javaClass.setPackage("self.chera.grpc").setName(className);

        javaClass.addInterface(Serializable.class);
        javaClass.addField().setName("serialVersionUID").setType("long").setLiteralInitializer("1L").setPrivate()
                .setStatic(true).setFinal(true);

        javaClass.addProperty(Integer.class, "id").setMutable(false);
        javaClass.addProperty(String.class, "firstName");
        javaClass.addProperty("String", "lastName");

        javaClass.addMethod().setConstructor(true).setPublic().setBody("this.id = id;")
                .addParameter(Integer.class, "id");
        String filename = String.format("%s/%s.java", PACKAGE_LOC, className);
        try {
            File myObj = new File(filename);
            if (myObj.createNewFile()) {
                System.out.println("File created: " + myObj.getName());
            } else {
                System.out.println("File already exists.");
            }
            FileWriter myWriter = new FileWriter(filename);
            myWriter.write(javaClass.toString());
            myWriter.close();
            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}
