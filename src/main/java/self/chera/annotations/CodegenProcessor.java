package self.chera.annotations;

import com.google.auto.service.AutoService;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("self.chera.annotations.CodegenTest")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class CodegenProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation: annotations) {
            var annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            if (annotatedElements.isEmpty()) {
                continue;
            }
            try {
                writeClass();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private void writeClass() throws IOException {
        var name = "HeyQA";
        final var someClass = Roaster.create(JavaClassSource.class);
        someClass.setName(name).setPackage("self.chera.generated")
                .addMethod().setStatic(true).setPublic().setName("Hmm").setReturnType(String.class).setBody("return \"Hello Annotation!\";");

        var builderFile = processingEnv.getFiler()
                .createSourceFile(name);
        try (var out = new PrintWriter(builderFile.openWriter())) {
            out.println(someClass);
        }
    }
}
