package org.retrosynthesis.config;

import org.retrosynthesis.index.Searcher;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Reports the served index on {@code GET /actuator/info}. */
@Component
@Profile("web")
public class IndexInfoContributor implements InfoContributor {

    private final Searcher searcher;

    public IndexInfoContributor(Searcher searcher) {
        this.searcher = searcher;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("index", Map.of("reactions", searcher.numReactions()));
    }
}
