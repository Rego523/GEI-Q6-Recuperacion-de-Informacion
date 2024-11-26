import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class TopTermsInField {

    public static void main(String [] args) throws IOException{

        String usage =
                "TopTermsInField"
                        + " [-index INDEX_PATH] [-field FIELD] [-top n] [-outfile OUTFILE]\n\n"
                        + "[\"Busca en el índice ubicado en INDEX_PATH los n términos del campo FIELD que aparecen en un mayor número de documentos" +
                        " y los escribe en pantalla y en OUTFILE\"]\n";

        String indexPath = null;
        String field = null;
        int numTerms = 10;
        String outfile = "termsField.txt";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-field":
                    field = args[++i];
                    break;
                case "-top":
                    numTerms = Integer.parseInt(args[++i]);
                    break;
                case "-outfile":
                    outfile = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("Parámetro no reconocido " + args[i]);
            }
        }

        if (indexPath == null || field == null) {
            System.err.println(usage);
            return;
        }

        Directory dir = null;
        DirectoryReader indexReader = null;

        try {
            dir = FSDirectory.open(Paths.get(indexPath));
            indexReader = DirectoryReader.open(dir);
        } catch (CorruptIndexException e1) {
            System.out.println("No se pudo abrir el índice: excepción " + e1);
            e1.printStackTrace();
            System.exit(1);
        } catch (IOException e1) {
            System.out.println("No se pudo abrir el índice: excepción " + e1);
            e1.printStackTrace();
            System.exit(1);
        }

        Map<String, Integer> foundTerms = new HashMap<>();

        for(final LeafReaderContext leaf : indexReader.leaves()) {
            try (LeafReader leafReader = leaf.reader()) {

                final Terms terms = leafReader.terms(field);

                if(terms == null){
                    System.out.println("El campo " + field + " no es un campo del índice");
                    System.exit(0);
                }
                if(terms.size() == 0){
                    System.out.println("No existen términos almacenados para el campo " + field);
                    System.exit(0);
                }

                final TermsEnum termsEnum = terms.iterator();

                while (termsEnum.next() != null) {

                    final String tt = termsEnum.term().utf8ToString();
                    int freq = termsEnum.docFreq();

                    /*Cada término puede aparecer en distintos leafContext, por lo que se hace lo siguiente:
                    -Si el término no está en el mapa, se añade a él
                    -Si el término está en el mapa, se añade la frecuencia encontrada a la que hay en el mapa
                    */
                    foundTerms.compute(tt, (key, value) -> (value == null) ? freq: value + freq);
                }
            }
        }

        //Para que se pongan en orden descendente, hacemos un comparador que ordene de mayor a menor freq
        //compareTo por defecto ordena de menor a mayor
        Comparator<Map.Entry<String, Integer>> comparator = (entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue());
        PriorityQueue<Map.Entry<String, Integer>> orderedTerms = new PriorityQueue<>(comparator);


        orderedTerms.addAll(foundTerms.entrySet());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outfile))) {
            writer.write("Top " + numTerms + " términos en el campo: " + field + "\n");
            int count = 0;
            while (!orderedTerms.isEmpty() && count < numTerms) {
                Map.Entry<String, Integer> term = orderedTerms.poll();

                String text = term.getKey();
                int df = term.getValue();

                String textToWrite = String.format("Término: \t%-10s df:\t %-10d\n", text, df);

                writer.write(textToWrite);
                System.out.print(textToWrite);
                count++;
            }
        }

    }
}