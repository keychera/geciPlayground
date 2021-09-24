package self.chera.annotations;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("self.chera.annotations.CodegenTest")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class CodegenProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            var annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);

            var annotatedMethods = annotatedElements.stream()
                    .collect(Collectors.partitioningBy(element -> ((ExecutableType) element.asType()).getParameterTypes().size() == 1 && element.getSimpleName().toString().startsWith("set")));

        }
        return false;
    }
}
