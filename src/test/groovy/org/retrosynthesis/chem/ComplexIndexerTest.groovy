package org.retrosynthesis.chem

import org.openscience.cdk.fingerprint.ICountFingerprint
import org.openscience.cdk.similarity.Tanimoto
import org.retrosynthesis.index.ComplexIndexer
import org.retrosynthesis.index.Result
import org.retrosynthesis.index.Searcher
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class ComplexIndexerTest extends Specification {

    static Path tempDir

    def setupSpec() {
        tempDir = Files.createTempDirectory("mytest")
    }

    def cleanupSpec() {
        tempDir.toFile().deleteDir()
    }

    def "add fingerprints to index"() {

        when:

        String rxn1 = "O=[CH:1][c:2]1[cH:3][cH:4][c:5](-[c:6]2[n:7][c:8]([CH3:9])[c:10]([CH2:11][O:12][c:13]3[cH:14][cH:15][c:16]([C@H:17]([CH2:18][C:19](=[O:20])[N:21]4[C:22](=[O:23])[O:24][CH2:25][C@@H:26]4[CH2:27][c:28]4[cH:29][cH:30][cH:31][cH:32][cH:33]4)[c:34]4[cH:35][cH:36][o:37][n:38]4)[cH:39][cH:40]3)[s:41]2)[cH:42][cH:43]1.[CH3:44][CH2:45][NH:46][CH2:47][CH3:48]>>[CH2:1]([c:2]1[cH:3][cH:4][c:5](-[c:6]2[n:7][c:8]([CH3:9])[c:10]([CH2:11][O:12][c:13]3[cH:14][cH:15][c:16]([C@H:17]([CH2:18][C:19](=[O:20])[N:21]4[C:22](=[O:23])[O:24][CH2:25][C@@H:26]4[CH2:27][c:28]4[cH:29][cH:30][cH:31][cH:32][cH:33]4)[c:34]4[cH:35][cH:36][o:37][n:38]4)[cH:39][cH:40]3)[s:41]2)[cH:42][cH:43]1)[N:46]([CH2:45][CH3:44])[CH2:47][CH3:48]"
        String rxn1a = "[CH3:1][CH2:2][S:3](=[O:4])(=[O:5])[N:6]1[CH2:7][CH2:8][CH:9]([CH2:10][CH2:11]1)[c:12]1[cH:13][nH:14][c:15]2[c:16]1[cH:17][c:18]([cH:19][c:20]2[C:21]([NH2:22])=[O:23])-[c:24]1[cH:25][cH:26][c:27]([cH:28][cH:29]1)[CH:30]=O.[CH3:31][CH2:32][NH:33][CH2:34][CH3:35]>>[CH3:1][CH2:2][S:3](=[O:4])(=[O:5])[N:6]1[CH2:7][CH2:8][CH:9]([CH2:10][CH2:11]1)[c:12]1[cH:13][nH:14][c:15]2[c:16]1[cH:17][c:18]([cH:19][c:20]2[C:21]([NH2:22])=[O:23])-[c:24]1[cH:25][cH:26][c:27]([cH:28][cH:29]1)[CH2:30][N:33]([CH2:34][CH3:35])[CH2:32][CH3:31]"
        String rxn2 = "Cl[C:1](=[O:2])[O:3][CH:4]1[CH2:5][CH2:6][CH2:7][CH2:8]1.[CH3:9][CH2:10][O:11][c:12]1[cH:13][cH:14][c:15]([O:16][c:17]2[n:18][cH:19][n:20][c:21]3[c:22]2[cH:23][n:24][n:25]3[CH:26]2[CH2:27][CH2:28][NH:29][CH2:30][CH2:31]2)[c:32]([F:33])[cH:34]1>>[C:1](=[O:2])([O:3][CH:4]1[CH2:5][CH2:6][CH2:7][CH2:8]1)[N:29]1[CH2:28][CH2:27][CH:26]([n:25]2[c:21]3[n:20][cH:19][n:18][c:17]([O:16][c:15]4[cH:14][cH:13][c:12]([O:11][CH2:10][CH3:9])[cH:34][c:32]4[F:33])[c:22]3[cH:23][n:24]2)[CH2:31][CH2:30]1"
        String rxn3 = "Br[CH2:1][c:2]1[n:3][c:4]2[c:5]([NH2:6])[n:7][cH:8][n:9][c:10]2[n:11]1[CH2:12][CH2:13][c:14]1[cH:15][cH:16][cH:17][cH:18][cH:19]1.[CH3:20][CH2:21][O:22][P:23](=[O:24])([CH2:25][OH:26])[O:27][CH2:28][CH3:29]>>[CH2:1]([c:2]1[n:3][c:4]2[c:5]([NH2:6])[n:7][cH:8][n:9][c:10]2[n:11]1[CH2:12][CH2:13][c:14]1[cH:15][cH:16][cH:17][cH:18][cH:19]1)[O:26][CH2:25][P:23]([O:22][CH2:21][CH3:20])(=[O:24])[O:27][CH2:28][CH3:29]"
        String rxn4 = "O[C:1](=[O:2])[c:3]1[cH:4][c:5]([N+:6](=[O:7])[O-:8])[c:9]([S:10][c:11]2[c:12]([Cl:13])[cH:14][n:15][cH:16][c:17]2[Cl:18])[s:19]1.[NH2:20][c:21]1[cH:22][cH:23][cH:24][c:25]2[cH:26][n:27][cH:28][cH:29][c:30]12>>[C:1](=[O:2])([c:3]1[cH:4][c:5]([N+:6](=[O:7])[O-:8])[c:9]([S:10][c:11]2[c:12]([Cl:13])[cH:14][n:15][cH:16][c:17]2[Cl:18])[s:19]1)[NH:20][c:21]1[cH:22][cH:23][cH:24][c:25]2[cH:26][n:27][cH:28][cH:29][c:30]12"
        String rxn5 = "O=[C:1]([CH2:2][F:3])[CH2:4][F:5].[CH3:6][C:7]1([CH3:8])[CH2:9][CH2:10][C:11]([CH2:12][N:13]2[CH2:14][CH2:15][N:16]([c:17]3[cH:18][cH:19][c:20]([C:21](=[O:22])[NH:23][S:24](=[O:25])(=[O:26])[c:27]4[cH:28][cH:29][c:30]([NH:31][CH2:32][CH:33]5[CH2:34][NH:35][CH2:36]5)[c:37]([N+:38](=[O:39])[O-:40])[cH:41]4)[c:42]([O:43][c:44]4[cH:45][n:46][c:47]5[nH:48][cH:49][cH:50][c:51]5[cH:52]4)[cH:53]3)[CH2:54][CH2:55]2)=[C:56]([c:57]2[cH:58][cH:59][c:60]([Cl:61])[cH:62][cH:63]2)[CH2:64]1>>[CH:1]([CH2:2][F:3])([CH2:4][F:5])[N:35]1[CH2:34][CH:33]([CH2:32][NH:31][c:30]2[cH:29][cH:28][c:27]([S:24]([NH:23][C:21]([c:20]3[cH:19][cH:18][c:17]([N:16]4[CH2:15][CH2:14][N:13]([CH2:12][C:11]5=[C:56]([c:57]6[cH:58][cH:59][c:60]([Cl:61])[cH:62][cH:63]6)[CH2:64][C:7]([CH3:6])([CH3:8])[CH2:9][CH2:10]5)[CH2:55][CH2:54]4)[cH:53][c:42]3[O:43][c:44]3[cH:45][n:46][c:47]4[nH:48][cH:49][cH:50][c:51]4[cH:52]3)=[O:22])(=[O:25])=[O:26])[cH:41][c:37]2[N+:38](=[O:39])[O-:40])[CH2:36]1"
        String rxn5a = "[CH:1]([CH2:2][F:3])([CH2:4][F:5])[N:35]1[CH2:34][CH:33]([CH2:32][NH:31][c:30]2[cH:29][cH:28][c:27]([S:24]([NH:23][C:21]([c:20]3[cH:19][cH:18][c:17]([N:16]4[CH2:15][CH2:14][N:13]([CH2:12][C:11]5=[C:56]([c:57]6[cH:58][cH:59][c:60]([Cl:61])[cH:62][cH:63]6)[CH2:64][C:7]([CH3:6])([CH3:8])[CH2:9][CH2:10]5)[CH2:55][CH2:54]4)[cH:53][c:42]3[O:43][c:44]3[cH:45][n:46][c:47]4[nH:48][cH:49][cH:50][c:51]4[cH:52]3)=[O:22])(=[O:25])=[O:26])[cH:41][c:37]2[N+:38](=[O:39])[O-:40])[CH2:36]1>>O=[C:1]([CH2:2][F:3])[CH2:4][F:5].[CH3:6][C:7]1([CH3:8])[CH2:9][CH2:10][C:11]([CH2:12][N:13]2[CH2:14][CH2:15][N:16]([c:17]3[cH:18][cH:19][c:20]([C:21](=[O:22])[NH:23][S:24](=[O:25])(=[O:26])[c:27]4[cH:28][cH:29][c:30]([NH:31][CH2:32][CH:33]5[CH2:34][NH:35][CH2:36]5)[c:37]([N+:38](=[O:39])[O-:40])[cH:41]4)[c:42]([O:43][c:44]4[cH:45][n:46][c:47]5[nH:48][cH:49][cH:50][c:51]5[cH:52]4)[cH:53]3)[CH2:54][CH2:55]2)=[C:56]([c:57]2[cH:58][cH:59][c:60]([Cl:61])[cH:62][cH:63]2)[CH2:64]1"
        String rxn6 = "CC(C)(C)[O:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12>>[OH:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12"
        String rxn7 = "Br[c:1]1[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1.OB(O)[c:15]1[cH:16][cH:17][c:18](-[n:19]2[cH:20][cH:21][cH:22][n:23]2)[cH:24][cH:25]1>>[c:1]1(-[c:15]2[cH:16][cH:17][c:18](-[n:19]3[cH:20][cH:21][cH:22][n:23]3)[cH:24][cH:25]2)[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1"
        String rxn8 = "Br[c:1]1[c:2]([O:3][CH:4]2[CH2:5][CH2:6][CH2:7][CH2:8][O:9]2)[cH:10][cH:11][c:12]([C:13]#[N:14])[cH:15]1.CC1(C)OB([c:16]2[cH:17][cH:18][c:19]([O:20][CH2:21][c:22]3[cH:23][cH:24][c:25]4[cH:26][cH:27][cH:28][cH:29][c:30]4[n:31]3)[cH:32][cH:33]2)OC1(C)C>>[c:1]1(-[c:16]2[cH:17][cH:18][c:19]([O:20][CH2:21][c:22]3[cH:23][cH:24][c:25]4[cH:26][cH:27][cH:28][cH:29][c:30]4[n:31]3)[cH:32][cH:33]2)[c:2]([O:3][CH:4]2[CH2:5][CH2:6][CH2:7][CH2:8][O:9]2)[cH:10][cH:11][c:12]([C:13]#[N:14])[cH:15]1"
        String rxn9 = "[CH3:9][C:8]1=[CH:10][S:41][C:6](=[N:7]1)[C:5]1=[C:4](F)[CH:3]=[C:2]([CH:1]=O)[CH:43]=[CH:42]1.[CH2:11]1[CH2:44][CH2:45][NH:46][CH2:47][CH2:48]1>>[CH3:9][C:8]1=[CH:10][S:41][C:6](=[N:7]1)[C:5]1=[C:4](F)[CH:3]=[C:2]([CH2:1][N:46]2[CH2:45][CH2:44][CH2:11][CH2:48][CH2:47]2)[CH:43]=[CH:42]1";
// [CH3:1][O:2][c:3]1[cH:4][cH:5][c:6]([C:7](=[O:8])[CH2:9][c:10]2[c:11]([Cl:12])[cH:13][n+:14]([O-:15])[cH:16][c:17]2[Cl:18])[c:19]2[c:20]1[O:21][C:22]1([CH2:23][CH2:24][CH2:25][CH2:26]1)[O:27]2>>[CH3:1][O:2][c:3]1[cH:4][cH:5][c:6]([CH:7]([OH:8])[CH2:9][c:10]2[c:11]([Cl:12])[cH:13][n+:14]([O-:15])[cH:16][c:17]2[Cl:18])[c:19]2[c:20]1[O:21][C:22]1([CH2:23][CH2:24][CH2:25][CH2:26]1)[O:27]2
// O[C:1](=[O:2])[c:3]1[cH:4][cH:5][c:6]([Br:7])[cH:8][c:9]1[Cl:10].[CH3:11][C:12]([CH3:13])([CH3:14])[O:15][C:16](=[O:17])[NH:18][NH2:19]>>[C:1](=[O:2])([c:3]1[cH:4][cH:5][c:6]([Br:7])[cH:8][c:9]1[Cl:10])[NH:19][NH:18][C:16]([O:15][C:12]([CH3:11])([CH3:13])[CH3:14])=[O:17]// 
// COc1ccc(C[n:1]2[n:2][n:3][c:4]([C:5]([O:6][CH2:7][CH3:8])=[O:9])[c:10]2[C:11](=[O:12])[c:13]2[cH:14][cH:15][c:16]([O:17][CH3:18])[c:19]([O:20][CH3:21])[cH:22]2)cc1>>[nH:1]1[n:2][n:3][c:4]([C:5]([O:6][CH2:7][CH3:8])=[O:9])[c:10]1[C:11](=[O:12])[c:13]1[cH:14][cH:15][c:16]([O:17][CH3:18])[c:19]([O:20][CH3:21])[cH:22]1
// O[C:1]([C@H:2]([CH:3]([CH3:4])[CH3:5])[NH:6][C:7](=[O:8])[CH2:9][NH:10][C:11](=[O:12])[C@@H:13]1[CH2:14][CH2:15][CH2:16][N:17]1[C:18](=[O:19])[C@@H:20]([NH:21][C:22](=[O:23])[O:24][CH2:25][c:26]1[cH:27][cH:28][cH:29][cH:30][cH:31]1)[CH:32]([CH3:33])[CH3:34])=[O:35].[CH3:36][O:37][C:38](=[O:39])[CH2:40][NH2:41]>>[C:1]([C@H:2]([CH:3]([CH3:4])[CH3:5])[NH:6][C:7](=[O:8])[CH2:9][NH:10][C:11](=[O:12])[C@@H:13]1[CH2:14][CH2:15][CH2:16][N:17]1[C:18](=[O:19])[C@@H:20]([NH:21][C:22](=[O:23])[O:24][CH2:25][c:26]1[cH:27][cH:28][cH:29][cH:30][cH:31]1)[CH:32]([CH3:33])[CH3:34])(=[O:35])[NH:41][CH2:40][C:38]([O:37][CH3:36])=[O:39]

        // initialize indexer

        println(tempDir)
        ComplexIndexer indexer = new ComplexIndexer(tempDir);

        // add reactions to index
        indexer.addReaction(rxn1, "1");
        indexer.addReaction(rxn2, "2");
        indexer.addReaction(rxn3, "3");
        indexer.addReaction(rxn4, "4");
        indexer.addReaction(rxn5, "5");
        indexer.addReaction(rxn6, "6");
        indexer.addReaction(rxn7, "7");
        indexer.addReaction(rxn8, "8");
        indexer.addReaction(rxn5a, "9");
        indexer.addReaction(rxn9, "10");
        indexer.addReaction(rxn1a, "11");

        indexer.close();

        then:

        Searcher searcher = new Searcher(tempDir);
        ReactionFingerprinter fp = new ReactionFingerprinter();
        int k = 1;
        for (String rxn : [rxn1, rxn2, rxn3, rxn4, rxn5, rxn6, rxn7, rxn8, rxn5a, rxn9, rxn1a]) {
//            for (String rxnf : [rxn1, rxn2, rxn3, rxn4, rxn5, rxn6, rxn7, rxn8, rxn5a]) {
                println(rxn)

              List<Result> results = searcher.searchBySmiles(rxn, 5);

                ICountFingerprint ref = fp.getCountFingerprint(SmilesXMol.reaction(rxn));
//                def fp2 = fp.getCountFingerprint(SmilesXMol.reaction(rxnf));
            results.forEach {
                def fp2 = fp.getCountFingerprint(SmilesXMol.reaction(it.smiles));
                println it.smiles + " " + it.xid + " " + k++ + " " + Tanimoto.method1(ref, fp2)
            };
            println()

        }
        searcher.close();
    }

    def "search against index"() {

        when:

        String rxn1 = "O=[CH:1][c:2]1[cH:3][cH:4][c:5](-[c:6]2[n:7][c:8]([CH3:9])[c:10]([CH2:11][O:12][c:13]3[cH:14][cH:15][c:16]([C@H:17]([CH2:18][C:19](=[O:20])[N:21]4[C:22](=[O:23])[O:24][CH2:25][C@@H:26]4[CH2:27][c:28]4[cH:29][cH:30][cH:31][cH:32][cH:33]4)[c:34]4[cH:35][cH:36][o:37][n:38]4)[cH:39][cH:40]3)[s:41]2)[cH:42][cH:43]1.[CH3:44][CH2:45][NH:46][CH2:47][CH3:48]>>[CH2:1]([c:2]1[cH:3][cH:4][c:5](-[c:6]2[n:7][c:8]([CH3:9])[c:10]([CH2:11][O:12][c:13]3[cH:14][cH:15][c:16]([C@H:17]([CH2:18][C:19](=[O:20])[N:21]4[C:22](=[O:23])[O:24][CH2:25][C@@H:26]4[CH2:27][c:28]4[cH:29][cH:30][cH:31][cH:32][cH:33]4)[c:34]4[cH:35][cH:36][o:37][n:38]4)[cH:39][cH:40]3)[s:41]2)[cH:42][cH:43]1)[N:46]([CH2:45][CH3:44])[CH2:47][CH3:48]"
        String rxn1a = "[CH3:1][CH2:2][S:3](=[O:4])(=[O:5])[N:6]1[CH2:7][CH2:8][CH:9]([CH2:10][CH2:11]1)[c:12]1[cH:13][nH:14][c:15]2[c:16]1[cH:17][c:18]([cH:19][c:20]2[C:21]([NH2:22])=[O:23])-[c:24]1[cH:25][cH:26][c:27]([cH:28][cH:29]1)[CH:30]=O.[CH3:31][CH2:32][NH:33][CH2:34][CH3:35]>>[CH3:1][CH2:2][S:3](=[O:4])(=[O:5])[N:6]1[CH2:7][CH2:8][CH:9]([CH2:10][CH2:11]1)[c:12]1[cH:13][nH:14][c:15]2[c:16]1[cH:17][c:18]([cH:19][c:20]2[C:21]([NH2:22])=[O:23])-[c:24]1[cH:25][cH:26][c:27]([cH:28][cH:29]1)[CH2:30][N:33]([CH2:34][CH3:35])[CH2:32][CH3:31]"
        String rxn2 = "Cl[C:1](=[O:2])[O:3][CH:4]1[CH2:5][CH2:6][CH2:7][CH2:8]1.[CH3:9][CH2:10][O:11][c:12]1[cH:13][cH:14][c:15]([O:16][c:17]2[n:18][cH:19][n:20][c:21]3[c:22]2[cH:23][n:24][n:25]3[CH:26]2[CH2:27][CH2:28][NH:29][CH2:30][CH2:31]2)[c:32]([F:33])[cH:34]1>>[C:1](=[O:2])([O:3][CH:4]1[CH2:5][CH2:6][CH2:7][CH2:8]1)[N:29]1[CH2:28][CH2:27][CH:26]([n:25]2[c:21]3[n:20][cH:19][n:18][c:17]([O:16][c:15]4[cH:14][cH:13][c:12]([O:11][CH2:10][CH3:9])[cH:34][c:32]4[F:33])[c:22]3[cH:23][n:24]2)[CH2:31][CH2:30]1"
        String rxn3 = "Br[CH2:1][c:2]1[n:3][c:4]2[c:5]([NH2:6])[n:7][cH:8][n:9][c:10]2[n:11]1[CH2:12][CH2:13][c:14]1[cH:15][cH:16][cH:17][cH:18][cH:19]1.[CH3:20][CH2:21][O:22][P:23](=[O:24])([CH2:25][OH:26])[O:27][CH2:28][CH3:29]>>[CH2:1]([c:2]1[n:3][c:4]2[c:5]([NH2:6])[n:7][cH:8][n:9][c:10]2[n:11]1[CH2:12][CH2:13][c:14]1[cH:15][cH:16][cH:17][cH:18][cH:19]1)[O:26][CH2:25][P:23]([O:22][CH2:21][CH3:20])(=[O:24])[O:27][CH2:28][CH3:29]"
        String rxn4 = "O[C:1](=[O:2])[c:3]1[cH:4][c:5]([N+:6](=[O:7])[O-:8])[c:9]([S:10][c:11]2[c:12]([Cl:13])[cH:14][n:15][cH:16][c:17]2[Cl:18])[s:19]1.[NH2:20][c:21]1[cH:22][cH:23][cH:24][c:25]2[cH:26][n:27][cH:28][cH:29][c:30]12>>[C:1](=[O:2])([c:3]1[cH:4][c:5]([N+:6](=[O:7])[O-:8])[c:9]([S:10][c:11]2[c:12]([Cl:13])[cH:14][n:15][cH:16][c:17]2[Cl:18])[s:19]1)[NH:20][c:21]1[cH:22][cH:23][cH:24][c:25]2[cH:26][n:27][cH:28][cH:29][c:30]12"
        String rxn5 = "O=[C:1]([CH2:2][F:3])[CH2:4][F:5].[CH3:6][C:7]1([CH3:8])[CH2:9][CH2:10][C:11]([CH2:12][N:13]2[CH2:14][CH2:15][N:16]([c:17]3[cH:18][cH:19][c:20]([C:21](=[O:22])[NH:23][S:24](=[O:25])(=[O:26])[c:27]4[cH:28][cH:29][c:30]([NH:31][CH2:32][CH:33]5[CH2:34][NH:35][CH2:36]5)[c:37]([N+:38](=[O:39])[O-:40])[cH:41]4)[c:42]([O:43][c:44]4[cH:45][n:46][c:47]5[nH:48][cH:49][cH:50][c:51]5[cH:52]4)[cH:53]3)[CH2:54][CH2:55]2)=[C:56]([c:57]2[cH:58][cH:59][c:60]([Cl:61])[cH:62][cH:63]2)[CH2:64]1>>[CH:1]([CH2:2][F:3])([CH2:4][F:5])[N:35]1[CH2:34][CH:33]([CH2:32][NH:31][c:30]2[cH:29][cH:28][c:27]([S:24]([NH:23][C:21]([c:20]3[cH:19][cH:18][c:17]([N:16]4[CH2:15][CH2:14][N:13]([CH2:12][C:11]5=[C:56]([c:57]6[cH:58][cH:59][c:60]([Cl:61])[cH:62][cH:63]6)[CH2:64][C:7]([CH3:6])([CH3:8])[CH2:9][CH2:10]5)[CH2:55][CH2:54]4)[cH:53][c:42]3[O:43][c:44]3[cH:45][n:46][c:47]4[nH:48][cH:49][cH:50][c:51]4[cH:52]3)=[O:22])(=[O:25])=[O:26])[cH:41][c:37]2[N+:38](=[O:39])[O-:40])[CH2:36]1"
        String rxn5a = "[CH:1]([CH2:2][F:3])([CH2:4][F:5])[N:35]1[CH2:34][CH:33]([CH2:32][NH:31][c:30]2[cH:29][cH:28][c:27]([S:24]([NH:23][C:21]([c:20]3[cH:19][cH:18][c:17]([N:16]4[CH2:15][CH2:14][N:13]([CH2:12][C:11]5=[C:56]([c:57]6[cH:58][cH:59][c:60]([Cl:61])[cH:62][cH:63]6)[CH2:64][C:7]([CH3:6])([CH3:8])[CH2:9][CH2:10]5)[CH2:55][CH2:54]4)[cH:53][c:42]3[O:43][c:44]3[cH:45][n:46][c:47]4[nH:48][cH:49][cH:50][c:51]4[cH:52]3)=[O:22])(=[O:25])=[O:26])[cH:41][c:37]2[N+:38](=[O:39])[O-:40])[CH2:36]1>>O=[C:1]([CH2:2][F:3])[CH2:4][F:5].[CH3:6][C:7]1([CH3:8])[CH2:9][CH2:10][C:11]([CH2:12][N:13]2[CH2:14][CH2:15][N:16]([c:17]3[cH:18][cH:19][c:20]([C:21](=[O:22])[NH:23][S:24](=[O:25])(=[O:26])[c:27]4[cH:28][cH:29][c:30]([NH:31][CH2:32][CH:33]5[CH2:34][NH:35][CH2:36]5)[c:37]([N+:38](=[O:39])[O-:40])[cH:41]4)[c:42]([O:43][c:44]4[cH:45][n:46][c:47]5[nH:48][cH:49][cH:50][c:51]5[cH:52]4)[cH:53]3)[CH2:54][CH2:55]2)=[C:56]([c:57]2[cH:58][cH:59][c:60]([Cl:61])[cH:62][cH:63]2)[CH2:64]1"
        String rxn6 = "CC(C)(C)[O:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12>>[OH:1][C:2](=[O:3])[CH2:4][O:5][NH:6][C:7](=[O:8])[NH:9][CH2:10][c:11]1[cH:12][cH:13][cH:14][c:15]2[cH:16][cH:17][cH:18][cH:19][c:20]12"
        String rxn7 = "Br[c:1]1[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1.OB(O)[c:15]1[cH:16][cH:17][c:18](-[n:19]2[cH:20][cH:21][cH:22][n:23]2)[cH:24][cH:25]1>>[c:1]1(-[c:15]2[cH:16][cH:17][c:18](-[n:19]3[cH:20][cH:21][cH:22][n:23]3)[cH:24][cH:25]2)[n:2]([CH3:3])[cH:4][n:5][c:6]1-[c:7]1[cH:8][c:9]([C:10]#[N:11])[cH:12][cH:13][n:14]1"
        String rxn8 = "Br[c:1]1[c:2]([O:3][CH:4]2[CH2:5][CH2:6][CH2:7][CH2:8][O:9]2)[cH:10][cH:11][c:12]([C:13]#[N:14])[cH:15]1.CC1(C)OB([c:16]2[cH:17][cH:18][c:19]([O:20][CH2:21][c:22]3[cH:23][cH:24][c:25]4[cH:26][cH:27][cH:28][cH:29][c:30]4[n:31]3)[cH:32][cH:33]2)OC1(C)C>>[c:1]1(-[c:16]2[cH:17][cH:18][c:19]([O:20][CH2:21][c:22]3[cH:23][cH:24][c:25]4[cH:26][cH:27][cH:28][cH:29][c:30]4[n:31]3)[cH:32][cH:33]2)[c:2]([O:3][CH:4]2[CH2:5][CH2:6][CH2:7][CH2:8][O:9]2)[cH:10][cH:11][c:12]([C:13]#[N:14])[cH:15]1"
        String rxn9 = "[CH3:9][C:8]1=[CH:10][S:41][C:6](=[N:7]1)[C:5]1=[C:4](F)[CH:3]=[C:2]([CH:1]=O)[CH:43]=[CH:42]1.[CH2:11]1[CH2:44][CH2:45][NH:46][CH2:47][CH2:48]1>>[CH3:9][C:8]1=[CH:10][S:41][C:6](=[N:7]1)[C:5]1=[C:4](F)[CH:3]=[C:2]([CH2:1][N:46]2[CH2:45][CH2:44][CH2:11][CH2:48][CH2:47]2)[CH:43]=[CH:42]1";

        then:

        println("search against index test starting")

        Searcher searcher = new Searcher(tempDir);

        ReactionFingerprinter fp = new ReactionFingerprinter();
        
        int k = 1;
        
        for (String rxn : [rxn1, rxn2, rxn3, rxn4, rxn5, rxn6, rxn7, rxn8, rxn5a, rxn9, rxn1a]) {

              println(rxn)

            // String product = rxn.split(">")[2];
             // (String queryRxnSmiles, String subgraphSmiles, String rxnSmarts, int topK, boolean reactionSim, Double productWeight, boolean rerank)
              List<Result> results = searcher.searchBySmiles(rxn, null, null, 5, true, 1.0d, false);

                ICountFingerprint ref = fp.getCountFingerprint(SmilesXMol.reaction(rxn));
//                def fp2 = fp.getCountFingerprint(SmilesXMol.reaction(rxnf));
            results.forEach {
                def fp2 = fp.getCountFingerprint(SmilesXMol.reaction(it.smiles));
                println it.smiles + " " + it.xid + " " + k++ + " " + Tanimoto.method1(ref, fp2)
            };
            println()

        }
        searcher.close();
    }


}
