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
import java.util.List;
import java.util.Map;

public class SearchEvalTrecCovid {

    public static void main(String[] args) {
        String usage = "SearchEvalTrecCovid"
                + " [-search <model> <parameter>] [-index <index_directory>] [-cut <n>]" +
                " [-top <m>] [-queries all | <int1> | <int1-int2>]";

        final float b = 0.75f;
        String model = null;
        String parameterName = null;
        float parameterValue = 0;
        String indexPath = "index";
        int cut = 10;
        int topDocs = 50;
        int firstQuery = -1;
        int lastQuery = -1;
        String queryOption = "all";

        // Procesamiento de argumentos de la línea de comandos
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-search":
                    model = args[++i];
                    try {
                        parameterValue = Float.parseFloat(args[++i]);
                    } catch (Exception e) {
                        System.err.println("Uso incorrecto: " + usage);
                        System.exit(1);
                    }
                    break;
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-cut":
                    try {
                        cut = Integer.parseInt(args[++i]);
                    } catch (Exception e) {
                        System.err.println("Uso incorrecto: " + usage);
                        System.exit(1);
                    }
                    break;
                case "-top":
                    try {
                        topDocs = Integer.parseInt(args[++i]);
                    } catch (Exception e) {
                        System.err.println("Uso incorrecto:\n" + usage);
                        System.exit(1);
                    }
                    break;
                case "-queries":
                    queryOption = args[++i];
                    if (!queryOption.equals("all")) {
                        try {
                            firstQuery = Integer.parseInt(queryOption);
                        } catch (Exception e) {
                            System.out.println("Uso incorrecto:\n" + usage);
                            System.exit(1);
                        }
                        try {
                            lastQuery = Integer.parseInt(args[++i]);
                            queryOption = firstQuery + "-" + lastQuery;
                            if (lastQuery < firstQuery) {
                                System.out.println("La última query a procesar no puede tener un id menor que la primera");
                                System.exit(1);
                            }
                        } catch (Exception e) {
                            lastQuery = firstQuery;
                            i--;
                            queryOption = String.valueOf(firstQuery);
                        }
                    }else{
                        firstQuery = 1;
                        lastQuery = 50;
                    }
                    break;
                default:
                    System.err.println("Uso incorrecto: " + usage);
                    System.exit(1);
            }
        }


        Similarity similarity = null;
        switch (model) {
            case "jm":
                similarity = new LMJelinekMercerSimilarity(parameterValue);
                parameterName = "lambda";
                break;
            case "bm25":
                similarity = new BM25Similarity(parameterValue, b);
                parameterName = "k1";
                break;
            default:
                System.err.println("Modelo de RI no válido");
                System.exit(1);
        }


        try {
            Directory directory = FSDirectory.open(Paths.get(indexPath));
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(similarity);
            Analyzer analyzer = new StandardAnalyzer();


            // Obtención de consultas y evaluación
            QueryParser queryParser = new QueryParser("text", analyzer);
            Map<Integer, Query> queries = QueryUtils.getQueries(firstQuery, lastQuery, queryParser);
            List<QueryResults> queryResults = QueryUtils.evaluateQueries(searcher, queries, cut, topDocs);


            String csvFile = "TREC-COVID." + model + "." + cut + ".cut." + parameterName + "." + parameterValue + ".q" + queryOption + ".csv";
            String txtFile = "TREC-COVID." + model + "." + topDocs + ".hits." + parameterName + "." + parameterValue + ".q" + queryOption + ".txt";

            // Generación de resultados


            try (FileWriter csvWriter = new FileWriter(csvFile)) {
                FileWriter txtWriter = new FileWriter(txtFile);


                // Escribir cabeceras
                csvWriter.append("Query,");
                csvWriter.append("P@").append(String.valueOf(cut)).append(",");
                csvWriter.append("Recall@").append(String.valueOf(cut)).append(",");
                csvWriter.append("RR,");
                csvWriter.append("AP@").append(String.valueOf(cut)).append("\n");

                int numQueries = 0;
                float sumPrecision = 0;
                float sumRecall = 0;
                float sumAP = 0;
                float sumRR = 0;

                // Escribir resultados para cada consulta
                for (QueryResults results : queryResults) {

                    String queryInfo = results.printDocInfo(searcher, topDocs);

                    queryInfo = queryInfo + ("Query Metrics:\n\n");


                    int queryId = results.getId();
                    float precision = results.getPrecision();
                    float recall = results.getRecall();
                    float rr = results.getReciprocalRank();
                    float ap = results.getAveragePrecision();

                    queryInfo = queryInfo + ("Precision@" + cut + ": ") + (precision) + ("\t"); // P@n
                    queryInfo = queryInfo + ("Recall@" + cut + ": ") + (recall) + ("\t"); // Recall@n
                    queryInfo = queryInfo + ("RR@" + cut + ": ") + (rr) + ("\t"); // RR
                    queryInfo = queryInfo + ("AP@" + cut + ": ") + (ap) + ("\n\n"); // AP@n

                    txtWriter.write(queryInfo);
                    System.out.println(queryInfo);


                    csvWriter.append(String.valueOf(queryId)).append(",");
                    csvWriter.append(String.valueOf(precision)).append(","); // P@n
                    csvWriter.append(String.valueOf(recall)).append(","); // Recall@n
                    csvWriter.append(String.valueOf(rr)).append(","); // RR
                    csvWriter.append(String.valueOf(ap)).append("\n"); // AP@n

                    numQueries++;
                    sumPrecision += precision;
                    sumRecall += recall;
                    sumAP += ap;
                    sumRR += rr;
                }

                // Calcular promedios
                float mPrecision = sumPrecision / numQueries;
                float mRecall = sumRecall / numQueries;
                float mRR = sumRR / numQueries;
                float mAP = sumAP / numQueries;

                String meanInfo = "\nTotal Metrics\nMean Precision :" + mPrecision +
                        "\tMean Recall: " + mRecall +
                        "\tMAP" + mAP +
                        "\tmRR: " + mRR + "\n";
                txtWriter.write(meanInfo);
                System.out.println(meanInfo);

                csvWriter.append("\nTotal Metrics\n");
                csvWriter.append("Mean Precision: ").append(String.valueOf(mPrecision)).append("\t");
                csvWriter.append("Mean Recall: ").append(String.valueOf(mRecall)).append("\t");
                csvWriter.append("MRR: ").append(String.valueOf(mRR)).append("\t");
                csvWriter.append("MAP: ").append(String.valueOf(mAP)).append("\n");

                // Escribir promedios
                csvWriter.append("Promedio,");
                csvWriter.append(String.valueOf(mPrecision)).append(",");
                csvWriter.append(String.valueOf(mRecall)).append(",");
                csvWriter.append(String.valueOf(mRR)).append(",");
                csvWriter.append(String.valueOf(mAP)).append("\n");

                txtWriter.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
