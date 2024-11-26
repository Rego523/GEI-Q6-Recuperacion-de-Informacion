import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

public class IndexTrecCovid {

    public static void main(String[] args) {
        String usage = "IndexTrecCovid"
                + " [-openmode <openmode>] [-index <index_directory>] [-docs <docs_directory>] " +
                "[-indexingmodel <model> <model_params>] ";

        String indexPath = "index";
        String docsPath = "docs";
        String openMode = "create";
        String indexingModel = "jm";
        float modelParams = 0;

        final float b = 0.75f;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-docs":
                    docsPath = args[++i];
                    break;
                case "-openmode":
                    openMode = args[++i];
                    break;
                case "-indexingmodel":
                    indexingModel = args[++i];
                    try{
                        modelParams = Float.parseFloat(args[++i]);
                    }
                    catch(Exception e){
                        System.err.println("Uso incorrecto: " + usage);
                        System.exit(1);
                    }
                    break;
                default:
                    System.err.println("Uso incorrecto: " + usage);
                    System.exit(1);
            }
        }

        // Set up Lucene index writer
        Analyzer analyzer = new StandardAnalyzer();

        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

        switch(openMode) {
            case "create":
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
                break;
            case "append":
                iwc.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
                break;
            case "create_or_append":
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                break;
        }

        // Set similarity based on the indexing model
        switch(indexingModel) {
            case "jm":
                iwc.setSimilarity(new LMJelinekMercerSimilarity(modelParams));
                break;
            case "bm25":
                iwc.setSimilarity(new BM25Similarity(modelParams, b));
                break;
            default:
                System.err.println("Invalid indexing model specified." );
                System.exit(1);
        }


        try {
            Directory directory = FSDirectory.open(Paths.get(indexPath));
            IndexWriter writer = new IndexWriter(directory, iwc);

            // Index documents from TREC-COVID collection
            indexDocuments(writer, docsPath);

            // Close the index writer
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void indexDocuments(IndexWriter writer, String docsPath) {
        // Lógica para leer y parsear documentos JSONL y agregarlos al índice

        try (BufferedReader br = Files.newBufferedReader(Paths.get(docsPath, "corpus.jsonl"))) {
            ObjectMapper mapper = new ObjectMapper();
            String line;

            while ((line = br.readLine()) != null) {
                JsonNode articleNode = mapper.readTree(line);

                String id = articleNode.get("_id").asText();
                String title = articleNode.get("title").asText();
                String text = articleNode.get("text").asText();

                JsonNode metadataNode = articleNode.get("metadata");
                String url = metadataNode.get("url").asText();
                String pubmedId = metadataNode.get("pubmed_id").asText();

                Document doc = new Document();
                doc.add(new StringField("_id", id, Field.Store.YES));
                doc.add(new TextField("title", title, Field.Store.YES));
                doc.add(new TextField("text", text, Field.Store.YES));
                doc.add(new StringField("url", url, Field.Store.YES));
                doc.add(new StringField("pubmed_id", pubmedId, Field.Store.YES));

                writer.addDocument(doc);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
