import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TopTermsInDoc {

    public static void main(String [] args){

        String usage =
                "TopTermsInDoc"
                        + " [-index INDEX_PATH] [-field FIELD] [-url URL] [-top n] [-outfile OUTFILE]\n\n"
                        + "[\"Busca en el índice ubicado en INDEX_PATH los n documentos más relevantes en el campo FIELD" +
                        " del documento que corresponda a la url URL y los escribe en pantalla y en OUTFILE\"]\n";

        String indexPath = null;
        String field = null;
        String url = null;
        int numTerms = 10;    //Dato por defecto
        String outfile = "termsDoc.txt";

        IndexReader reader = null;
        Directory dir = null;
        IndexSearcher searcher = null;
        StoredFields storedFields = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-field":
                    field = args[++i];
                    break;
                case "-url":
                    url = args[++i];
                    break;
                case "-top":
                    numTerms = Integer.parseInt(args[++i]);
                    break;
                case "-outfile":
                    outfile = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("Parámetro desconocido: " + args[i]);
            }
        }

        if (indexPath == null || field == null || url == null) {
            System.err.println(usage);
            return;
        }

        if(!url.contains("://")){
            System.out.println("La url debe comenzar por \"http://\" o \"https://\"");
        }


        String file = url.split("://")[1];  // Le quita el"http://" o el "https://" al nombre de la url de entrada.
        if (file.endsWith("/") || file.endsWith("\\")) {
            file = file.substring(0, file.length() - 1);
        }

        //Reemplazamos las "/" por "_" para que el sistema operativo no piense que es un directorio
        file = file.replace("/", "_");

        file = file + ".loc";   // Añade la extensio ".loc" a la url sin "https://".

        try{
            dir = FSDirectory.open(Paths.get(indexPath));   // Abrimos el directorio IndexFile
            reader = DirectoryReader.open(dir);             // Obtenemos el reader
            storedFields = reader.storedFields();
        }
        catch (CorruptIndexException e1) {
            System.out.println("No se pudo abrir el índice: excepción " + e1);
            e1.printStackTrace();
            System.exit(1);
        } catch (IOException e1) {
            System.out.println("No se pudo abrir el índice: excepción " + e1);
            e1.printStackTrace();
            System.exit(1);
        }

        searcher = new IndexSearcher(reader);

        try {
            // Toma un documento arbitrario para sacar su campo path
            Document arbitraryDoc = storedFields.document(0);
            Path arbitraryPath = Path.of(arbitraryDoc.get("path"));

            // Con el campo path podemos obtener la ruta a DocFiles si quitamos el último /<algo>
            String docFolder = arbitraryPath.getParent().toString();
            file = Paths.get(docFolder, file).toString();

            TermQuery query = new TermQuery(new Term("path", file));
            ScoreDoc[] docs = searcher.search(query, 1).scoreDocs;

            if (docs.length == 0) {
                System.err.println("No se encontró un documento para la url " + url);
                return;
            }

            int docId = docs[0].doc;    // Conseguimos el docId del documento correspondiente a la url dada de entrada

            // Devuelve un map de todos los terminos de un campo y las veces que salen dentro del documento con docID
            Map<String, Integer> frequencies = TopTermsInDoc.getTermFrequencies(reader, docId, field);

            PriorityQueue<DocTerm> orderedTerms = new PriorityQueue<>(Collections.reverseOrder());

            int numDocs = reader.numDocs(); // Numero de documentos.

            for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
                String term = entry.getKey();       // Nombre del termino.
                int termFreq = entry.getValue();    // Frecuencia del termino en cuestion en este campo.
                int docFreq = reader.docFreq(new Term(field, term));    // Número de coumentos en los cuales aparece el temrino.

                orderedTerms.add(new DocTerm(term, termFreq, docFreq, numDocs));
            }


            // Write the top terms to the output file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outfile))) {
                writer.write("Top " + numTerms + " términos para el documento con URL: " + url + "(docID " + docId +")\n");
                int count = 0;
                while (!orderedTerms.isEmpty() && count < numTerms) {
                    DocTerm dt = orderedTerms.poll();

                    /*String term = dt.getText();
                    int tf = dt.getTf();
                    int df = dt.getDf();
                    double tfIdf= dt.getTfidf();*/


                    writer.write(dt.toString());
                    System.out.print(dt.toString());
                    count++;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, Integer> getTermFrequencies(IndexReader reader, int docId, String field) throws IOException {
        TermVectors termVectors = reader.termVectors();
           Terms vector = termVectors.get(docId, field);
        if(vector == null){
            System.err.println("No se han creado term vectors para este campo del índice");
            System.exit(1);
        }

        TermsEnum termsEnum = null;
        termsEnum = vector.iterator();
        Map<String, Integer> frequencies = new HashMap<>();
        BytesRef text = null;
        while ((text = termsEnum.next()) != null) {
            String term = text.utf8ToString();
            int freq = (int) termsEnum.totalTermFreq();
            frequencies.put(term, freq);
        }
        return frequencies;
    }
}
