package org.retrosynthesis.restapi

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.retrosynthesis.index.ComplexIndexer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("web")
class SearchControllerTest extends Specification {

    static final String RXN_COUPLING = "Br[c:1]1[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1.OB(O)[c:15]1[cH:16][cH:17][c:18](-[n:19]2[cH:20][cH:21][cH:22][n:23]2)[cH:24][cH:25]1>>[c:1]1(-[c:15]2[cH:16][cH:17][c:18](-[n:19]3[cH:20][cH:21][cH:22][n:23]3)[cH:24][cH:25]2)[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1"
    static final String RXN_HYDROLYSIS = "CC(C)(C)[O:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12>>[OH:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12"
    static final String PROD_COUPLING = "Cn1cnc(-c2ccc(-n3cccn3)cc2)c1-c1ccc(C#N)cn1"

    static final Path INDEX_DIR = buildIndex()

    static Path buildIndex() {
        Path dir = Files.createTempDirectory("search-controller-test")
        def indexer = new ComplexIndexer(dir)
        indexer.addReaction(RXN_COUPLING, "US123;coupling")
        indexer.addReaction(RXN_HYDROLYSIS, "hydrolysis")
        indexer.close()
        return dir
    }

    @DynamicPropertySource
    static void indexdir(DynamicPropertyRegistry registry) {
        registry.add("indexdir", { INDEX_DIR.toString() })
    }

    @Autowired
    MockMvc mvc

    def cleanupSpec() {
        INDEX_DIR.toFile().deleteDir()
    }

    MockHttpServletResponse query(String path, String body) {
        mvc.perform(request(HttpMethod.valueOf("QUERY"), path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andReturn().response
    }

    MockHttpServletResponse query(String path, Map body) {
        query(path, JsonOutput.toJson(body))
    }

    static Object json(MockHttpServletResponse response) {
        new JsonSlurper().parseText(response.contentAsString)
    }

    def "QUERY /reactions/similarity/by-reaction finds the indexed reaction with reaction scores"() {
        when:
        def response = query("/reactions/similarity/by-reaction", [smiles: [RXN_COUPLING], knn: 2])

        then:
        response.status == 200
        def top = json(response)[0][0]
        top.smiles == RXN_COUPLING
        top.xid == "US123;coupling"
        top.sim == 1.0d
        top.similarities.keySet() == ["ecfp4", "path_6"] as Set
    }

    def "weights.product adds the product score as ecfp_prod and blends it into sim"() {
        when:
        def response = query("/reactions/similarity/by-reaction", [smiles: [RXN_HYDROLYSIS], weights: [product: 0.25]])

        then:
        response.status == 200
        def hits = json(response)[0]
        hits.every { it.similarities.keySet() == ["ecfp4", "path_6", "ecfp_prod"] as Set }
        hits.every { hit ->
            def s = hit.similarities
            double expected = 0.75d * (s.ecfp4 + s.path_6) / 2 + 0.25d * s.ecfp_prod
            Math.abs(hit.sim - expected) < 1e-9
        }
    }

    def "blank subgraph on #path means no filter, like omitting it"() {
        when:
        def omitted = query(path, [smiles: [smiles]])
        def blank = query(path, [smiles: [smiles], subgraph: subgraph])

        then:
        blank.status == 200
        !json(omitted)[0].isEmpty()
        json(blank) == json(omitted)

        where:
        path                                | smiles        | subgraph
        "/reactions/similarity/by-reaction" | RXN_COUPLING  | ""
        "/reactions/similarity/by-reaction" | RXN_COUPLING  | "  "
        "/reactions/similarity/by-product"  | PROD_COUPLING | ""
        "/reactions/similarity/by-product"  | PROD_COUPLING | "  "
    }

    def "QUERY /reactions/similarity/by-product reports its product score as ecfp_prod"() {
        when:
        def response = query("/reactions/similarity/by-product", [smiles: [PROD_COUPLING]])

        then:
        response.status == 200
        def top = json(response)[0][0]
        top.smiles == RXN_COUPLING
        top.similarities.keySet() == ["ecfp_prod"] as Set
    }

    def "#method #path is rejected with 405 and Allow: QUERY"() {
        when:
        def response = mvc.perform(builder(path).contentType(MediaType.APPLICATION_JSON).content('{"smiles":["CCO"]}'))
                .andReturn().response

        then:
        response.status == 405
        response.getHeader("Allow") == "QUERY"

        where:
        method | path                                | builder
        "POST" | "/reactions/similarity/by-reaction" | { String p -> post(p) }
        "GET"  | "/reactions/similarity/by-product"  | { String p -> get(p) }
    }

    def "invalid #path request is a 400 problem detail: #expected"() {
        when:
        def response = query(path, body)

        then:
        response.status == 400
        response.contentType == MediaType.APPLICATION_PROBLEM_JSON_VALUE
        (json(response).detail as String).startsWith(expected)

        where:
        path                                | body                                          | expected
        "/reactions/similarity/by-reaction" | [smiles: []]                                  | "smiles must contain at least one SMILES"
        "/reactions/similarity/by-product"  | [:]                                           | "smiles must contain at least one SMILES"
        "/reactions/similarity/by-reaction" | [smiles: ["not-a-smiles"]]                    | "Invalid reaction SMILES"
        "/reactions/similarity/by-reaction" | [smiles: ["CCO"]]                             | "Invalid reaction SMILES"
        "/reactions/similarity/by-product"  | [smiles: [RXN_COUPLING]]                      | "Invalid molecule SMILES"
        "/reactions/similarity/by-reaction" | [smiles: [RXN_COUPLING], subgraph: "xx("]     | "Invalid subgraph SMILES"
        "/reactions/similarity/by-product"  | [smiles: ["CCO"], subgraph: "xx("]            | "Invalid subgraph SMILES"
        "/reactions/similarity/by-reaction" | [smiles: [RXN_COUPLING], rxnSmarts: "[[["]    | "Invalid rxnSmarts"
        "/reactions/similarity/by-reaction" | [smiles: [RXN_COUPLING], knn: 0]              | "knn must be at least 1"
        "/reactions/similarity/by-product"  | [smiles: ["CCO"], knn: -1]                    | "knn must be at least 1"
        "/reactions/similarity/by-reaction" | [smiles: [RXN_COUPLING], knn: 1001]           | "knn must be at most 1000, got 1001"
        "/reactions/similarity/by-product"  | [smiles: ["CCO"], knn: 5000]                  | "knn must be at most 1000, got 5000"
        "/reactions/similarity/by-reaction" | [smiles: ["CCO>>"], weights: [product: 0.5]]   | "a product weight requires a reaction with products"
        "/reactions/similarity/by-reaction" | [smiles: [RXN_COUPLING], weights: [product: 1.5]] | "product weight must be between 0 and 1, got 1.5"
        "/reactions/similarity/by-reaction" | [smiles: [RXN_COUPLING], weights: [product: -0.1]] | "product weight must be between 0 and 1, got -0.1"
        "/reactions/similarity/by-reaction" | [smiles: [RXN_COUPLING], weights: [:]]          | "weights must include product"
    }

    def "malformed JSON is a 400 problem detail"() {
        when:
        def response = query("/reactions/similarity/by-reaction", "{")

        then:
        response.status == 400
        response.contentType == MediaType.APPLICATION_PROBLEM_JSON_VALUE
    }

    def "health is UP and info reports the index size"() {
        expect:
        json(mvc.perform(get("/actuator/health")).andReturn().response).status == "UP"
        json(mvc.perform(get("/actuator/info")).andReturn().response).index.reactions == 2
    }
}
