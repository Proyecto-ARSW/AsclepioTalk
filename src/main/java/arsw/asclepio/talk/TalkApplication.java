package arsw.asclepio.talk;

import arsw.asclepio.talk.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// Daniel Useche
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class TalkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalkApplication.class, args);
    }
}
