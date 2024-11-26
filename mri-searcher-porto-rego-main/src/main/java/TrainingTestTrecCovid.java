import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class TrainingTestTrecCovid {

    public static void main(String[] args) {
        String usage = "TrainingTestTrecCovid"
                + "[-evaljm <int1-int2> <int3-int4>]|[-evalbm25 <int1-int2> <int3-int4>]" +
                "[-cut <n>] [-metrica P|R|MRR|MAP] -index <ruta>";

        String indexPath = "";
        int cut = 0;
        String metric = "";
        int trainingStart = 0;
        int trainingEnd = 0;
        int testStart = 0;
        int testEnd = 0;
        String indexingModel = null;
        Similarity similarity;

        List<Float> trainingParams = new ArrayList<>();
        float b = 0.75f;


        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-cut":
                    try {
                        cut = Integer.parseInt(args[++i]);
                    } catch (Exception e) {
                        System.out.println(usage);
                        System.exit(-1);
                    }
                    break;
                case "-metrica":
                    metric = args[++i].toLowerCase();
                    break;
                case "-evaljm":
                    if (indexingModel != null) {
                        System.err.println("Las opciones \"-evaljm\" y \"-evalbm\" son mutuamente exclusivas");
                        System.exit(1);
                    }
                    try {
                        String[] trainingQueries = args[++i].split("-");
                        String[] testQueries = args[++i].split("-");

                        trainingStart = Integer.parseInt(trainingQueries[0]);
                        trainingEnd = Integer.parseInt(trainingQueries[1]);

                        testStart = Integer.parseInt(testQueries[0]);
                        testEnd = Integer.parseInt(testQueries[1]);
                    } catch (Exception e) {
                        System.out.println(usage);
                        System.exit(-1);
                    }

                    indexingModel = "jm";
                    trainingParams.add(0.01f);
                    for (float val = 0.1f; val < 1.01f; val += 0.1f) {
                        trainingParams.add((float)Math.round(100 *val)/100);
                    }
                    break;
                case "-evalbm25":
                    if (indexingModel != null) {
                        System.err.println("Las opciones \"-evaljm\" y \"-evalbm\" son mutuamente exclusivas");
                        System.exit(1);
                    }
                    try {
                        String[] trainingQueries = args[++i].split("-");
                        String[] testQueries = args[++i].split("-");

                        trainingStart = Integer.parseInt(trainingQueries[0]);
                        trainingEnd = Integer.parseInt(trainingQueries[1]);

                        testStart = Integer.parseInt(testQueries[0]);
                        testEnd = Integer.parseInt(testQueries[1]);
                    } catch (Exception e) {
                        System.out.println(usage);
                        System.exit(-1);
                    }
                    indexingModel = "bm25";

                    for (float val = 0.4f; val < 2.01f; val += 0.2f) {
                        trainingParams.add((float)Math.round(100 *val)/100);
                    }
                    break;
                default:
                    System.err.println("Uso incorrecto: " + usage);
                    System.exit(1);
            }
        }

        if (indexingModel == null) {
            System.err.println("Es necesario elegir un modelo para el entrenamiento (\"-evaljm\" o \"-evalbm\")");
            System.exit(-1);
        }
        Directory directory;
        IndexReader reader;
        Analyzer analyzer = new StandardAnalyzer();
        IndexSearcher searcher = null;
        QueryParser queryParser = new QueryParser("text", analyzer);

        Map<Integer, Query> trainingQueries = QueryUtils.getQueries(trainingStart, trainingEnd, queryParser);
        Map<Integer, Query> testQueries = QueryUtils.getQueries(testStart, testEnd, queryParser);


        try {
            directory = FSDirectory.open(Paths.get(indexPath));
            reader = DirectoryReader.open(directory);
            searcher = new IndexSearcher(reader);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        float bestParamValue = 0.0f, bestMetricValue = 0.0f;
        float metricAcum, metricAverage, currentMetric;
        int numQueries = trainingQueries.size();

        Float[][] queryMetrics = new Float[numQueries + 1][trainingParams.size()];


        for (int i = 0; i < trainingParams.size(); i++) {
            float paramValue = trainingParams.get(i);
            if (indexingModel.equals("jm")) {
                similarity = new LMJelinekMercerSimilarity(paramValue);
            } else {
                similarity = new BM25Similarity(paramValue, b);
            }

            searcher.setSimilarity(similarity);
            List<QueryResults> trainingQResults = QueryUtils.evaluateQueries(searcher, trainingQueries, cut, cut);
            metricAcum = 0.0f;

            for (QueryResults qr : trainingQResults) {
                currentMetric = qr.getMetricByName(metric);
                metricAcum += currentMetric;
                queryMetrics[qr.getId() - trainingStart][i] = currentMetric;
            }
            metricAverage = metricAcum / trainingQResults.size();
            queryMetrics[numQueries][i] = metricAverage;

            if (metricAverage >= bestMetricValue) {
                bestMetricValue = metricAverage;
                bestParamValue = paramValue;
            }
        }

        String trainingFile = String.format("TREC-COVID.%s.training.%d-%d.test.%d-%d.%s%d.training.csv",
                indexingModel, trainingStart, trainingEnd, testStart, testEnd, metric, cut);


        if (indexingModel.equals("jm")) {
            similarity = new LMJelinekMercerSimilarity(bestParamValue);
        } else {
            similarity = new BM25Similarity(bestParamValue, b);
        }
        searcher.setSimilarity(similarity);
        List<QueryResults> testQResults = QueryUtils.evaluateQueries(searcher, testQueries, cut, cut);

        StringBuilder trainingResults = new StringBuilder(metric + "@" + cut);
        for (float param : trainingParams) {
            trainingResults.append(",").append(param);
        }
        for (int i = 0; i < queryMetrics.length; i++) {
            if(i == queryMetrics.length - 1){
                trainingResults.append("\navg");
            }else {
                trainingResults.append("\n").append(i + trainingStart);
            }
            for (int j = 0; j < queryMetrics[0].length; j++) {
                trainingResults.append(",").append( queryMetrics[i][j]);
            }
        }
        System.out.println("Training results:\n");
        System.out.println(trainingResults);
        try (FileWriter trainingWriter = new FileWriter(trainingFile)) {
            trainingWriter.write(trainingResults.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }

        StringBuilder testResults = new StringBuilder(bestParamValue + "," + metric);
        metricAcum = 0;
        for (QueryResults qr : testQResults){
            currentMetric = qr.getMetricByName(metric);
            testResults.append("\n").append(qr.getId()).append(",").append(currentMetric);
            metricAcum += currentMetric;
        }

        testResults.append("\navg,").append(metricAcum / testQResults.size());

        String testFile = String.format("TREC-COVID.%s.training.%d-%d.test.%d-%d.%s%d.test.csv",
                indexingModel, trainingStart, trainingEnd, testStart, testEnd, metric, cut);
        try (FileWriter testWriter = new FileWriter(testFile)) {
            testWriter.write(testResults.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("\nTest results:\n");
        System.out.println(testResults);


    }
}
