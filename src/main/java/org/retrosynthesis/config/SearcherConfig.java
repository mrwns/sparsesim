package org.retrosynthesis.config;

import org.retrosynthesis.index.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@Profile("web")
public class SearcherConfig {

    private static final Logger logger = LoggerFactory.getLogger(SearcherConfig.class);

    @Bean(destroyMethod = "close")
    public Searcher searcher(@Value("${indexdir}") String indexDir, @Value("${preload}") boolean preload) throws Exception {
        if (indexDir == null || indexDir.isEmpty()) {
            throw new IllegalArgumentException("Property 'indexdir' must be set");
        }
        Path path = Path.of(indexDir);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Index directory does not exist: " + indexDir);
        }
        Searcher searcher = new Searcher(path, preload);
        logger.info("Opened index {} (preload={})", path, preload);
        long t0 = System.currentTimeMillis();
        searcher.warmup();
        logger.info("Index warmup completed in {} ms", System.currentTimeMillis() - t0);
        return searcher;
    }
}
