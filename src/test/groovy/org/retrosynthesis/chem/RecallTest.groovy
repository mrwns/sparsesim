package org.retrosynthesis.chem

import org.openscience.cdk.fingerprint.Fingerprinter
import org.openscience.cdk.fingerprint.ICountFingerprint
import org.openscience.cdk.similarity.Tanimoto
import org.retrosynthesis.index.ComplexIndexer
import org.retrosynthesis.index.Result
import org.retrosynthesis.index.Searcher
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

class RecallTest extends Specification {

    /** Max train reactions to use (subsample for CI; use full set when overridden). */
    static final int TRAIN_LIMIT = Integer.getInteger("recall.train.limit", 2000)
    /** Max test queries (subsample for CI). Queries are drawn from the index (train) so we measure recall for specific indexed items. */
    static final int TEST_LIMIT = Integer.getInteger("recall.test.limit", 200)
    /** K values for recall. */
    static final int[] K_VALUES = [10, 50, 100]
    static final int TOP_K = K_VALUES.max()

    static Path tempDir
    static List<TrainEntry> trainCache
    /** Test queries are a sample of train (indexed) entries so we measure recall for specific items in the index. */
    static List<TrainEntry> testQueryCache
    static List<String> trainSmilesForIndex

    def setupSpec() {
        tempDir = Files.createTempDirectory("recall-test")
        def trainPath = Paths.get(getClass().getResource("/u50k_trainrxn.smi").toURI())

        def ecfpFpr = new ReactionFingerprinter()
        def pathFpr = new ReactionFingerprinter(new Fingerprinter(768763, 6))

        trainCache = []
        trainSmilesForIndex = []
        def trainLines = Files.lines(trainPath)
                .map { it.trim() }
                .filter { !it.isEmpty() && !it.startsWith("#") }
                .collect(Collectors.toList())
        for (line in trainLines) {
            if (trainCache.size() >= TRAIN_LIMIT) break
            try {
                def rxn = SmilesXMol.reaction(line)
                def ecfp = ecfpFpr.getCountFingerprint(rxn)
                def pathFp = pathFpr.getCountFingerprint(rxn)
                int xid = trainCache.size()
                trainCache << new TrainEntry(xid: xid, smiles: line, ecfp: ecfp, pathFp: pathFp)
                trainSmilesForIndex << line
            } catch (Exception e) {
                // skip unparseable
            }
        }

        // Use a sample of indexed (train) entries as test queries so we measure recall for specific items in the index
        int step = Math.max(1, (int) (trainCache.size() / TEST_LIMIT))
        testQueryCache = []
        for (int i = 0; i < trainCache.size() && testQueryCache.size() < TEST_LIMIT; i += step) {
            testQueryCache << trainCache[i]
        }

        // Index with IDs matching trainCache positions, so retrieved xids map back to train entries.
        try (def indexer = new ComplexIndexer(tempDir)) {
            for (entry in trainCache) {
                indexer.addReaction(entry.smiles, String.valueOf(entry.xid))
            }
        }
    }

    def cleanupSpec() {
        tempDir.toFile().deleteDir()
    }

    def "recall vs brute-force with and without rerank"() {
        expect:
        trainCache.size() > 0
        testQueryCache.size() > 0

        when:
        def searcher = new Searcher(tempDir)

        def retrievedNoRerank = [] as List<List<Integer>>
        def retrievedRerank = [] as List<List<Integer>>
        for (test in testQueryCache) {
            def resultsNoRerank = searcher.searchBySmiles(test.smiles, null, null, TOP_K, true, null, false)
            def resultsRerank = searcher.searchBySmiles(test.smiles, null, null, TOP_K, true, null, true)
            retrievedNoRerank << resultsNoRerank.collect { Integer.parseInt(it.xid()) }
            retrievedRerank << resultsRerank.collect { Integer.parseInt(it.xid()) }
        }
        searcher.close()

        def groundTruthPerQuery = bruteForceTopK(testQueryCache, trainCache, TOP_K)

        def recallNoRerank = [:] as Map<Integer, Double>
        def recallRerank = [:] as Map<Integer, Double>
        for (int k : K_VALUES) {
            recallNoRerank[k] = meanRecall(retrievedNoRerank, groundTruthPerQuery, k)
            recallRerank[k] = meanRecall(retrievedRerank, groundTruthPerQuery, k)
        }

        def meanTanimotoNoRerank = [:] as Map<Integer, Double>
        def meanTanimotoRerank = [:] as Map<Integer, Double>
        for (int k : K_VALUES) {
            meanTanimotoNoRerank[k] = meanOfMeanTanimoto(testQueryCache, trainCache, retrievedNoRerank, k)
            meanTanimotoRerank[k] = meanOfMeanTanimoto(testQueryCache, trainCache, retrievedRerank, k)
        }

        then:
        reportRecall(recallNoRerank, recallRerank)
        reportMeanTanimoto(meanTanimotoNoRerank, meanTanimotoRerank)

        recallRerank[10] >= 0.0
        recallNoRerank[10] >= 0.0
    }

    static List<List<Integer>> bruteForceTopK(List<TrainEntry> testQueries, List<TrainEntry> trainCache, int topK) {
        def result = [] as List<List<Integer>>
        for (test in testQueries) {
            def scored = trainCache.collect { train ->
                def ec = Tanimoto.method1(test.ecfp, train.ecfp)
                def path = Tanimoto.method1(test.pathFp, train.pathFp)
                [train.xid, (ec + path) * 0.5]
            }
            def top = scored.sort { -it[1] }.take(topK).collect { it[0] }
            result << top
        }
        return result
    }

    static double meanRecall(List<List<Integer>> retrieved, List<List<Integer>> groundTruth, int k) {
        int n = Math.min(retrieved.size(), groundTruth.size())
        if (n == 0) return 0.0
        double sum = 0.0
        for (int q = 0; q < n; q++) {
            def retSet = retrieved[q].take(k) as Set
            def gtSet = groundTruth[q].take(k) as Set
            sum += (retSet.intersect(gtSet).size() / (double) k)
        }
        return sum / n
    }

    /** For each query: mean Tanimoto (0.5*ecfp+0.5*path) over top-k retrieved. Return mean of those per-query means. */
    static double meanOfMeanTanimoto(List<TrainEntry> testQueries, List<TrainEntry> trainCache,
                                     List<List<Integer>> retrieved, int k) {
        if (testQueries.isEmpty() || retrieved.isEmpty()) return 0.0
        double sumOfMeans = 0.0
        int queryCount = 0
        for (int q = 0; q < testQueries.size() && q < retrieved.size(); q++) {
            def query = testQueries[q]
            def retIds = retrieved[q].take(k)
            if (retIds.isEmpty()) continue
            double simSum = 0.0
            for (xid in retIds) {
                if (xid >= 0 && xid < trainCache.size()) {
                    def train = trainCache[xid]
                    def ec = Tanimoto.method1(query.ecfp, train.ecfp)
                    def path = Tanimoto.method1(query.pathFp, train.pathFp)
                    simSum += (ec + path) * 0.5
                }
            }
            sumOfMeans += simSum / retIds.size()
            queryCount++
        }
        return queryCount == 0 ? 0.0 : sumOfMeans / queryCount
    }

    static void reportRecall(Map<Integer, Double> noRerank, Map<Integer, Double> rerank) {
        println("Recall (train=${trainCache.size()}, test queries from index=${testQueryCache.size()}):")
        for (int k : K_VALUES) {
            printf("  Recall@%d (no rerank): %.2f%%  (rerank): %.2f%%%n", k, noRerank[k] * 100, rerank[k] * 100)
        }
    }

    static void reportMeanTanimoto(Map<Integer, Double> noRerank, Map<Integer, Double> rerank) {
        println("Mean-of-mean Tanimoto (query vs retrieved top-K):")
        for (int k : K_VALUES) {
            printf("  top-%d (no rerank): %.4f  (rerank): %.4f%n", k, noRerank[k], rerank[k])
        }
    }

    static class TrainEntry {
        int xid
        String smiles
        ICountFingerprint ecfp
        ICountFingerprint pathFp
    }
}
