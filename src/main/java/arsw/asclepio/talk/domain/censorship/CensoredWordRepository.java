package arsw.asclepio.talk.domain.censorship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
// Daniel Useche
public interface CensoredWordRepository extends JpaRepository<CensoredWord, UUID> {

    @Query("SELECT c.word FROM CensoredWord c WHERE c.active = true")
    List<String> findAllActiveWords();

    List<CensoredWord> findAllByOrderByCreatedAtDesc();

    Optional<CensoredWord> findByWordIgnoreCase(String word);
}
