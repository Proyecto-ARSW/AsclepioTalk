package arsw.asclepio.talk;

import arsw.asclepio.talk.config.JwtProperties;
import arsw.asclepio.talk.config.S3Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// Daniel Useche
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, S3Properties.class})
public class TalkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalkApplication.class, args);
    }
}
