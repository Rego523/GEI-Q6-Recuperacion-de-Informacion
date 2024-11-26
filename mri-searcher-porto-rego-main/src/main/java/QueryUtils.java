import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class QueryUtils {


    public static Map<Integer, Query> getQueries(int firstQuery, int lastQuery, QueryParser queryParser) {
        Map<Integer, Query> queries = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader("src/test/resources/queries.jsonl"))) {
            String line;
            ObjectMapper mapper = new ObjectMapper();
            while ((line = reader.readLine()) != null) {
                JsonNode queryNode = mapper.readTree(line);
                int id = queryNode.get("_id").asInt();
                // Verificar si es necesario procesar la query
                if (id >= firstQuery && id <= lastQuery) {
                    String queryText = queryNode.get("metadata").get("query").asText();
                    Query query = parseQuery(queryText, queryParser); // Parsear la consulta
                    if (query != null) {
                        queries.put(id, query);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return queries;
    }

    private static Query parseQuery(String line, QueryParser queryParser) {
        try {
            // Parsear la línea JSONL para obtener la consulta
            // Supongamos que el formato es simplemente una cadena de texto
            return queryParser.parse(line.trim());
        } catch (ParseException e) {
            System.err.println("Error al parsear la consulta: " + e.getMessage());
            return null;
        }
    }



    public static List<QueryResults> evaluateQueries(IndexSearcher searcher, Map<Integer, Query> queries, int cut, int topDocs) {
        List<QueryResults> totalResults = new ArrayList<>();

        List<Map.Entry<Integer, Query>> sortedEntries = new ArrayList<>(queries.entrySet());
        sortedEntries.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, Query> entry : sortedEntries) {
            int queryId = entry.getKey();
            Query query = entry.getValue();

            try {

                // Realizar la búsqueda y obtener los hits
                TopDocs topDocsResult = searcher.search(query, Math.max(topDocs, cut));
                ScoreDoc[] hits = topDocsResult.scoreDocs;

                QueryResults queryResults = new QueryResults(queryId, query.toString(), hits, cut, searcher);

                totalResults.add(queryResults);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return totalResults; // Retornar los resultados de las queries
    }
}
