import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class QueryResults {

    private static final Map<Integer, List<String>> relevantDocs;
    private final int id;
    private final String query;
    private final PriorityQueue<Map.Entry<ScoreDoc, Boolean>> topDocs;
    private float precision;
    private float recall;
    private float averagePrecision;
    private float reciprocalRank;

    public int getId() {
        return id;
    }

    static {
        relevantDocs = new HashMap<>();
        loadRelevantDocs();
    }
    
    public QueryResults(int id, String query, ScoreDoc[] hits, int cut, IndexSearcher searcher){
        this.id = id;
        this.query = query;
        Comparator<Map.Entry<ScoreDoc, Boolean>> scoreComparator = (entry1, entry2) -> Float.compare(entry2.getKey().score, entry1.getKey().score);
        this.topDocs = new PriorityQueue<>(scoreComparator);

        createTopDocs(hits, searcher);
        calculateMetrics(cut);
    }

    private static void loadRelevantDocs(){
        try (BufferedReader reader = new BufferedReader(new FileReader("src/test/resources/test.tsv"))) {
            String line;
            reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                int currentQueryId = Integer.parseInt(parts[0]);
                int score = Integer.parseInt(parts[2]);

                if(!relevantDocs.containsKey(currentQueryId)){
                    relevantDocs.put(currentQueryId, new ArrayList<>());
                }
                if (score != 0) {
                    String corpusId = parts[1];
                    relevantDocs.get(currentQueryId).add(corpusId);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void createTopDocs(ScoreDoc[] hits, IndexSearcher searcher){

        Document doc = null;

        for (int i = 0; i < hits.length; i++) {
            ScoreDoc hit = hits[i];
            int docId = hit.doc;
            try {
                doc = searcher.storedFields().document(docId);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
            String corpusId = doc.get("_id");
            Map.Entry<ScoreDoc, Boolean> entry;
            if (relevantDocs.get(id).contains(corpusId)) {
                entry = new AbstractMap.SimpleEntry<>(hit, true);
            }
            else{
                entry = new AbstractMap.SimpleEntry<>(hit, false);
            }
            this.topDocs.add(entry);
        }
    }

    private void calculateMetrics(int cut){

        int numRelevantDocs = relevantDocs.get(id).size();

        int trueHits = 0; // Contador de documentos relevantes recuperados

        float precision = 0.0f; // Precisión
        float recall = 0.0f; // Sensibilidad
        float rr = 0.0f; // Reciprocal Rank
        float ap = 0.0f; // Average Precision

        int i = 0;

        // Calcula RR y AP
        for (Map.Entry<ScoreDoc, Boolean> hitInfo : topDocs) {

            if(i >= cut){
                break;
            }

            boolean isRelevant = hitInfo.getValue();

            if (isRelevant) {
                trueHits++;
                if (rr == 0.0f) {
                    rr = 1.0f / (i + 1);
                }
                ap += (float) trueHits / (i + 1);
            }
            i++;
        }

        // Calcula P@n y AP@n
        if (trueHits > 0) {
            precision = (float) trueHits / cut;
            ap = ap/numRelevantDocs;
        }

        // Calcula Recall@n
        if (numRelevantDocs > 0) {
            recall = (float) trueHits / numRelevantDocs;
        }

        this.averagePrecision = ap;
        this.precision = precision;
        this.recall = recall;
        this.reciprocalRank = rr;
    }


    public String printDocInfo(IndexSearcher searcher, int numDocs){
        StringBuilder docInfo = new StringBuilder("Query: " + query + "\nTop hits:\n");

        int top = 1;

        Iterator<Map.Entry<ScoreDoc, Boolean>> iterator = topDocs.iterator();
        while (iterator.hasNext() && top <= numDocs) {
            Map.Entry<ScoreDoc, Boolean> hit = iterator.next();
            ScoreDoc scoreDoc = hit.getKey();
            boolean isRelevant = hit.getValue();
            float score = scoreDoc.score;
            int docId = scoreDoc.doc;

            try {
                Document document = searcher.storedFields().document(docId);
                String id = document.get("_id");
                String title = document.get("title");
                String text = document.get("text");
                String url = document.get("url");
                String pubmedId = document.get("pubmed_id");
                docInfo.append("Top ").append(top);
                if(isRelevant) docInfo.append("(REL)");
                docInfo.append(":\n");
                docInfo.append("id: ").append(id).append("\ntitle: ").append(title).append("\ntext: ").append(text).append("\nurl: ").append(url).append("\npubmed_id: ").append(pubmedId).append("\nscore: ").append(score).append("\n\n");
            }
            catch (IOException e){
                System.err.println("No se pudo acceder al documento " + docId + "del índice");
                e.printStackTrace();
            }

            top ++;
        }
        return docInfo.toString();
    }


    public String getquery() {
        return query;
    }

    public PriorityQueue<Map.Entry<ScoreDoc, Boolean>> getHits() {
        return topDocs;
    }

    public float getPrecision() {
        return precision;
    }

    public float getRecall() {
        return recall;
    }

    public float getAveragePrecision() {
        return averagePrecision;
    }

    public float getReciprocalRank() {
        return reciprocalRank;
    }

    public float getMetricByName(String name){

        switch(name) {
            case "p":
                return  precision;
            case "r":
                return recall;
            case "mrr":
                return reciprocalRank;
            case "map":
                return averagePrecision;
            default:
                return -1;
        }
    }
}
