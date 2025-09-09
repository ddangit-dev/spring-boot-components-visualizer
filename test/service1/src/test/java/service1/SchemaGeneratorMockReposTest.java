package service1;

import io.github.m4gshm.components.visualizer.ComponentsExtractor;
import io.github.m4gshm.components.visualizer.PlantUmlTextFactory;
import io.github.m4gshm.spring.data.mock.ReplaceRepositoriesByMocks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

import static java.util.Objects.requireNonNull;
import static service1.SchemaGeneratorTest.writeSwgFile;
import static service1.SchemaGeneratorTest.writeTextFile;

@ReplaceRepositoriesByMocks
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@SpringBootTest(classes = {YourSprintBootApplication.class, SchemaGeneratorMockReposTest.TestConfiguration.class})
public class SchemaGeneratorMockReposTest {

    @Autowired
    ComponentsExtractor extractor;
    @Autowired
    PlantUmlTextFactory schemaFactory;

    static File getSvgFile(File plantUmlFile) {
        var plantUmlOutFileName = plantUmlFile.getName();
        var extensionDelim = plantUmlOutFileName.lastIndexOf(".");
        return new File(
                plantUmlFile.getParentFile(),
                (extensionDelim != -1 ? plantUmlOutFileName.substring(0, extensionDelim) : plantUmlOutFileName) + ".svg"
        );
    }

    @Test
    public void generatePlantUml() {
        var schema = schemaFactory.create(extractor.getComponents());
        var envName = "PLANTUML_OUT";
        var plantUmlOutFile = new File(requireNonNull(System.getenv(envName), envName), "components-mock-db-repos.puml");
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
