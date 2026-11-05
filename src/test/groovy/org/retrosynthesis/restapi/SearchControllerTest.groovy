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
        indexer.addReaction(RXN_COUPLING, 1)
        indexer.addReaction(RXN_HYDROLYSIS, 2)
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

    def "QUERY /similarity finds the indexed reaction with reaction scores"() {
        when:
        def response = query("/similarity", [smiles: [RXN_COUPLING], knn: 2])

        then:
        response.status == 200
        def top = json(response)[0][0]
        top.smiles == RXN_COUPLING
        top.sim == 1.0d
        top.similarities.keySet() == ["ecfp4", "path_6"] as Set
    }

    def "productsim adds the product score as ecfp_prod"() {
        when:
        def response = query("/similarity", [smiles: [RXN_COUPLING], productsim: true])

        then:
        response.status == 200
        json(response)[0][0].similarities.keySet() == ["ecfp4", "path_6", "ecfp_prod"] as Set
    }

    def "QUERY /molsimilarity reports its product score as ecfp_prod"() {
        when:
        def response = query("/molsimilarity", [smiles: [PROD_COUPLING]])

        then:
        response.status == 200
        def top = json(response)[0][0]
        top.smiles == RXN_COUPLING
    }

    def "#method #path is rejected with 405 and Allow: QUERY"() {
        when:
        def response = mvc.perform(builder(path).contentType(MediaType.APPLICATION_JSON).content('{"smiles":["CCO"]}'))
                .andReturn().response

        then:
        response.status == 405
        response.getHeader("Allow") == "QUERY"

        where:
        method | path             | builder
        "POST" | "/similarity"    | { String p -> post(p) }
        "GET"  | "/molsimilarity" | { String p -> get(p) }
    }
}
