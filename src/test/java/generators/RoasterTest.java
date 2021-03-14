package generators;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

public class RoasterTest {
    private static final String GEN_LOC = "target/generated-sources/rpc-actions";

    public static void main(String[] args) {
        prepareGeneratedFolder();
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

        javaClass.addMethod().setName("printFullName").setPublic().
                setBody("System.out.println(firstName + \" \" + lastName);");

        String filename = String.format("%s/%s.java", GEN_LOC, className);
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

    private static void prepareGeneratedFolder() {
        try {
            Files.createDirectories(Path.of(GEN_LOC));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
