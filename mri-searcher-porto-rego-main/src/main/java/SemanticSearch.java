import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.similarities.Similarity;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class SemanticSearch {

    public static void main(String[] args) {

        String usage = "SemanticSearch"
                + " [-search <model> <parameter>] [-index <index_directory>] [-cut <n>]" +
                " [-top <m>] [-queries all | <int1> | <int1-int2>]";

        String model = null;
        String parameterName = null;
        float parameterValue = 0;
        String indexPath = "index";
        int cut = 10;
        int topDocs = 50;
        int firstQuery = -1;
        int lastQuery = -1;
        String queryOption = "all";

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
                    } else {
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
                similarity = new BM25Similarity(parameterValue, 0.75f);
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


            QueryParser queryParser = new QueryParser("text", analyzer);
            Map<Integer, Query> queries = QueryUtils.getQueries(firstQuery, lastQuery, queryParser);
            List<QueryResults> queryResults = QueryUtils.evaluateQueries(searcher, queries, cut, topDocs);

            String csvFile = "semantic.search." + model + "." + cut + ".cut." + parameterName + "." + parameterValue + ".q" + queryOption + ".csv";
            String txtFile = "semantic.search." + model + "." + topDocs + ".hits." + parameterName + "." + parameterValue + ".q" + queryOption + ".txt";

            try (FileWriter csvWriter = new FileWriter(csvFile)) {
                FileWriter txtWriter = new FileWriter(txtFile);

                csvWriter.append("Query,");
                csvWriter.append("P@").append(String.valueOf(cut)).append(",");
                csvWriter.append("Recall@").append(String.valueOf(cut)).append(",");
                csvWriter.append("RR,");
                csvWriter.append("AP@").append(String.valueOf(cut)).append("\n");

                // Escribir resultados para cada consulta
                for (QueryResults results : queryResults) {
                    // Escribir los resultados en el archivo CSV y TXT
                    // Aquí se debería implementar la lógica para la búsqueda semántica y la escritura de resultados
                    // Esto puede implicar modificar la clase QueryUtils para incluir la búsqueda semántica
                    // y calcular las métricas correspondientes.
                    // Luego, los resultados se pueden escribir en los archivos de salida.
                }

                // Cerrar escritores
                txtWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

