
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class WebIndexer {

    private static Analyzer analyzerFromName(String name){
        Analyzer analyzer = null;
        switch (name){
            case "standard":
                analyzer = new StandardAnalyzer();
                break;
            case "spanish":
                analyzer = new SpanishAnalyzer();
                break;
            case "english":
                analyzer = new EnglishAnalyzer();
        }
        return analyzer;
    }

    public static void main(String [] args) throws Exception {

        // java -jar target/mri-webindexer-1.0-SNAPSHOT.jar WebIndexer -index C:\Users\adrir\OneDrive\Desktop\RI\IndexFile -docs C:\Users\
        // adrir\OneDrive\Desktop\RI\DocFile

        String usage =
                "WebIndexer"
                        + " [-index INDEX_PATH] [-docs DOCS_PATH] [-create] [-numThreads n] [-h] [-p] [-titleTermVectors] [-bodyTermVectors] [-analyzer Analyzer]\n"
                        + "[\"Indexa los documentos de DOCS_PATH en un índice ubicado en INDEX_PATH con el analyzer Analyzer.\n " +
                        "Para esto, usará una pool de n threads. Con la opción -h, cada thread informará de las urls analizadas. " +
                        "Con la opción -p, la aplicación informará de la creación del índice\n" +
                        "Con bodyTermVectors y titleTermVectors, se almacenarán TermVectors para su respectivo campo";

        String supportedAnalyzers = "Los analyzers permitidos son: Standard, Spanish y English\n";

        final Path urlPath = Paths.get("src\\test\\resources\\urls");

        String indexPath = "index";
        String docsPath = null;
        boolean create = false;
        int nThreads = Runtime.getRuntime().availableProcessors(); //valor por defecto
        boolean threadInfo = false;
        boolean runtimeInfo = false;
        boolean titleTermVectors = false;
        boolean bodyTermVectors = false;
        String analyzerName = "standard";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-docs":
                    docsPath = args[++i];
                    break;
                case "-create":
                    create = true;
                    break;
                case "-numThreads":
                    nThreads = Integer.parseInt(args[++i]);
                    break;
                case "-h":
                    threadInfo = true;
                    break;
                case "-p":
                    runtimeInfo = true;
                    break;
                case "-titleTermVectors":
                    titleTermVectors = true;
                    break;
                case "-bodyTermVectors":
                    bodyTermVectors = true;
                    break;
                case "-analyzer":
                    analyzerName = args[++i].toLowerCase();
                    break;
                default:
                    throw new IllegalArgumentException("Parámetro no reconocido: " + args[i]);
            }
        }

        if (docsPath == null) {
            System.err.println("Usage: " + usage + supportedAnalyzers);
            System.exit(1);
        }

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println(
                    "El directorio de documentos '"
                            + docDir.toAbsolutePath()
                            + "' no existe o no es legible");
            System.exit(1);
        }

        Directory indexDir = FSDirectory.open(Paths.get(indexPath));

        try {

            Analyzer analyzer = WebIndexer.analyzerFromName(analyzerName);
            if (analyzer == null) {
                System.out.println("Analyzer " + analyzerName + " no permitido\n" + supportedAnalyzers);
                System.exit(1);
            }
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
// Create a new index in the directory, removing any
// previously indexed documents:
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            } else {
// Add new documents to an existing index:
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            }

            long startTime = System.currentTimeMillis();
            try(IndexWriter writer = new IndexWriter(indexDir, iwc)){
                ThreadPool.createIndexThreads(urlPath, docDir, writer, threadInfo, nThreads, titleTermVectors, bodyTermVectors);
            }
            long endTime = System.currentTimeMillis();
            if(runtimeInfo){
                System.out.println("Creado índice " + indexPath + " en " + (endTime - startTime) + "msecs");
            }

        }
        catch (IOException e) {
            System.out.println("Ha saltado una excepción " + e.getClass() + "\n con mensaje: " + e.getMessage());
        }
    }
}

