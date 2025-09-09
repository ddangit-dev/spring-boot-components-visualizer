package service1;

import io.github.m4gshm.components.visualizer.ComponentsExtractor;
import io.github.m4gshm.components.visualizer.PlantUmlTextFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

import static io.github.m4gshm.components.visualizer.eval.bytecode.StringifyResolver.Level.full;
import static java.util.Objects.requireNonNull;
import static service1.SchemaGeneratorTest.*;

@EnableAutoConfiguration
@SpringBootTest(classes = {YourSprintBootApplication.class, SchemaGeneratorTest2.TestConfiguration.class})
public class SchemaGeneratorTest2 {

    @Autowired
    ConfigurableApplicationContext context;
    @Autowired
    PlantUmlTextFactory schemaFactory;

    @Test
    public void generatePlantUml() {
        var extractor = new ComponentsExtractor(context, ComponentsExtractor.Options.builder()
                .failFast(true)
                .stringifyLevel(full)
                .build());

        var schema = schemaFactory.create(extractor.getComponents(), schemaFactory.getOptions().toBuilder()
                .concatenateInterfaces(PlantUmlTextFactory.Options.ConcatenateInterfacesOptions.builder()
                        .moreThan(1)
                        .build())
                .concatenateComponents(PlantUmlTextFactory.Options.ConcatenateComponentsOptions.builder()
                        .moreThan(1)
                        .build())
                .build());
        var envName = "PLANTUML_OUT";
        var plantUmlOutFile = new File(requireNonNull(System.getenv(envName), envName), "components-compress.puml");
        writeTextFile(plantUmlOutFile, schema);
        writeSwgFile(getSvgFile(plantUmlOutFile), schema);
    }

    @Configuration
    public static class TestConfiguration {
        @Bean
        ComponentsExtractor.Options options() {
            return ComponentsExtractor.Options.builder()
                    .failFast(false)
                    .build();
        }
    }
}
