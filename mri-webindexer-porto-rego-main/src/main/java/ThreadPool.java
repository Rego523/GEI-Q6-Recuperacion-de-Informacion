import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.jsoup.Jsoup;


import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * For the folder given as argument, the class ThreadPool
 * prints the name of each subfolder using a
 * different thread.
 */
public class ThreadPool {
    /**
     * This Runnable takes a folder and prints its path.
     */
    public static class IndexThread implements Runnable {

        private final File file;
        private final Path docFolder;
        private final IndexWriter writer;
        private final boolean printInfo;
        private final boolean titleTV;
        private final boolean bodyTV;

        public IndexThread(final Path file, final Path docFolder, final IndexWriter writer,
                           final boolean printInfo, final boolean titleTV, final boolean bodyTV) {
            this.file = file.toFile();
            this.docFolder = docFolder;
            this.writer = writer;
            this.printInfo = printInfo;
            this.titleTV = titleTV;
            this.bodyTV = bodyTV;
        }

        /**
         * This is the work that the current thread will do when processed by the pool.
         * In this case, it will only print some information.
         */

        @Override
        public void run() {
            Properties properties = new Properties();
            try(InputStream propReader = this.getClass().getResourceAsStream("config.properties")){
                properties.load(propReader);
            }
            catch (IOException e){
                System.out.println("Error cargando las propiedades");
                e.printStackTrace();
            }
            String allowedDoms = properties.getProperty("onlyDoms");
            String[] doms = allowedDoms.split(" ");
            
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {

                    boolean containsAllowedDom = false;

                    for (String dom : doms) {
                        if (line.contains(dom)) {
                            containsAllowedDom = true;
                            break;
                        }
                    }
                    if (!containsAllowedDom) {
                        System.out.println("La URL " + line + " no pertenece a los dominios aceptados (" + allowedDoms + ")");
                    }
                    else {
                        if (printInfo) {
                            System.out.println("Hilo " + Thread.currentThread().getName() + " inicio url " + line);
                        }

                        fetchHTML(line); // Hace una request a la URL, recibe un html y lo procesa

                        if (printInfo) {
                            System.out.println("Hilo " + Thread.currentThread().getName() + " fin url " + line);
                        }
                    }
                }
            }
            catch(IOException ex){
                ex.printStackTrace();
            }
        }


        private void fetchHTML(String url) {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NORMAL) //Sigue cualquier redirección menos las que llevan de una página https a una http
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                String html = response.body();

                processHTML(url, html);

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void processHTML(String url, String html) {
            try {
                org.jsoup.nodes.Document doc = Jsoup.parse(html);
                String title = doc.title();
                String body = doc.text(); // Obtiene solo el texto del cuerpo sin etiquetas HTML

                String fileName = url.split("://")[1];  // Le quita el"http://" o el "https://" al nombre de la página
                if(fileName.endsWith("/") ||fileName.endsWith("\\")){
                    fileName = fileName.substring(0, fileName.length() - 1);
                }
                //Reemplazamos las "/" por "_" para que el sistema operativo no piense que es un directorio
                fileName = fileName.replace("/", "_");

                String locName = fileName + ".loc";
                String notagsName = fileName + ".loc.notags";

                Path locPath = docFolder.resolve(locName);  // Path de los archivos .loc
                Path notagsPath = docFolder.resolve(notagsName);  //Path de los archivos .loc.notags

                File locFile = locPath.toFile();
                File notagsFile = notagsPath.toFile();

                locFile.createNewFile();
                notagsFile.createNewFile();

                try (FileWriter writer = new FileWriter(locFile)) {
                    // Escribe el contenido HTML completo en el archivo .loc
                    writer.write(html);
                    writer.flush();
                }

                try (FileWriter writer = new FileWriter(notagsFile)) {
                    writer.write(title);
                    writer.write("\n");
                    writer.write(body);
                    writer.flush();
                }

                indexDoc(locPath, notagsPath);

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        private void indexDoc(Path locPath, Path notagsPath){
            // Parsear la ruta para obtener la información necesaria
            String fileName = notagsPath.getFileName().toString();
            String hostname;
            String thread = Thread.currentThread().getName();
            try{
                hostname = InetAddress.getLocalHost().getHostName();
            }
            catch (UnknownHostException e) {
                hostname = "unknown";
            }

            try (InputStream locStrean = Files.newInputStream(locPath)){

                BasicFileAttributes attrs = Files.readAttributes(locPath, BasicFileAttributes.class);

                FileTime lastAccessTime = attrs.lastAccessTime();
                FileTime lastModifiedTime = attrs.lastModifiedTime();
                FileTime creationTime = attrs.creationTime();

                Date lastAccessDate =  Date.from(lastAccessTime.toInstant());
                Date lastModifiedDate = Date.from(lastModifiedTime.toInstant());
                Date creationDate = Date.from(creationTime.toInstant());

                String lastAccessTimeLucene = DateTools.dateToString(lastAccessDate, DateTools.Resolution.SECOND);
                String lastModifiedTimeLucene = DateTools.dateToString(lastModifiedDate, DateTools.Resolution.SECOND);
                String creationTimeLucene = DateTools.dateToString(creationDate, DateTools.Resolution.SECOND);

                long locKb = attrs.size()/1024;
                long notagsKb = Files.size(notagsPath) / 1024;

                // Creación del documento Lucene
                Document doc = new Document();

                Field pathField = new StringField("path", locPath.toString(), Field.Store.YES);
                doc.add(pathField);

                doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(locStrean, StandardCharsets.UTF_8))));

                doc.add(new StringField("hostname", hostname, Field.Store.YES));
                doc.add(new StringField("thread", thread, Field.Store.YES));

                doc.add(new StoredField("locKb", locKb));
                doc.add(new StoredField("notagsKb", notagsKb));

                doc.add(new LongPoint("locKb", locKb));
                doc.add(new LongPoint("notagsKb", notagsKb));
                //Indexamos también de esta forma para que se acepten búsquedas por rangop

                doc.add(new StoredField("lastAccessTime", lastAccessTime.toString()));
                doc.add(new StoredField("lastModifiedTime", lastModifiedTime.toString()));
                doc.add(new StoredField("creationTime", creationTime.toString()));

                doc.add(new StoredField("lastAccessTimeLucene", lastAccessTimeLucene));
                doc.add(new StoredField("lastModifiedTimeLucene", lastModifiedTimeLucene));
                doc.add(new StoredField("creationTimeLucene", creationTimeLucene));


                FieldType titleField = new FieldType();
                FieldType bodyField = new FieldType();
                //Campos propios para poder almacenar term vectors si el usuario lo pide
                //Las index options están por defecto en DOCS_AND_FREQS_AND_POSITIONS.

                titleField.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
                titleField.setTokenized(true);
                titleField.setStored(true);
                if(titleTV){
                    titleField.setStoreTermVectors(true);
                    titleField.setStoreTermVectorPositions(true);
                    titleField.setStoreTermVectorOffsets(true);
                }
                titleField.freeze();

                bodyField.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
                bodyField.setTokenized(true);
                //bodyField.setStored(true);
                if(bodyTV){
                    bodyField.setStoreTermVectors(true);
                    bodyField.setStoreTermVectorPositions(true);
                    bodyField.setStoreTermVectorOffsets(true);
                }
                bodyField.freeze();

                BufferedReader notagsReader = Files.newBufferedReader(notagsPath);

                doc.add(new Field("title",notagsReader.readLine() , titleField));
                doc.add(new Field("body", notagsReader, bodyField));


                // Añadir el documento al índice
                writer.addDocument(doc);

                notagsReader.close();

            }
            catch(Exception e){
                e.printStackTrace();
            }


        }
    }

    public static void createIndexThreads(final Path urlPath, final Path docsPath, final IndexWriter writer,
                                          final boolean threadInfo, final int numThreads,
                                          final boolean titleTermVectors, final boolean bodyTermVectors) {

        /*
         * Create a ExecutorService (ThreadPool is a subclass of ExecutorService) with
         * so many thread as cores in my machine. This can be tuned according to the
         * resources needed by the threads.
         */
        final ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        /*
         * We use Java 7 NIO.2 methods for input/output management. More info in:
         * http://docs.oracle.com/javase/tutorial/essential/io/fileio.html
         *
         * We also use Java 7 try-with-resources syntax. More info in:
         * https://docs.oracle.com/javase/tutorial/essential/exceptions/
         * tryResourceClose.html
         */
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(urlPath)){
            /* We process each subfolder in a new thread. */
            for (final Path file: stream) {
                if (Files.isReadable(file) && Files.isRegularFile(file)) {
                    final Runnable worker = new IndexThread(file, docsPath, writer, threadInfo, titleTermVectors, bodyTermVectors);
                    /*
                     * Send the thread to the ThreadPool. It will be processed eventually.
                     */
                    executor.execute(worker);
                }
            }

        } catch (final IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        /*
         * Close the ThreadPool; no more jobs will be accepted, but all the previously
         * submitted jobs will be processed.
         */
        executor.shutdown();

        /* Wait up to 1 hour to finish all the previously submitted jobs */
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (final InterruptedException e) {
            e.printStackTrace();
            System.exit(-2);
        }
    }
}
