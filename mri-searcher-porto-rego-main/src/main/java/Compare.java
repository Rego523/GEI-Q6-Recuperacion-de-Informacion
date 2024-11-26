import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Compare {

    public static void main(String[] args) {
        String usage = "Compare -test <t|wilcoxon> <alpha> -results <results1.csv> <results2.csv>";

        String testType = "";
        double alpha = 0.05;
        String resultsFile1 = "";
        String resultsFile2 = "";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-test":
                    testType = args[++i];
                    if (!testType.equals("t") && !testType.equals("wilcoxon")) {
                        System.err.println("Tipo de test no válido.");
                        System.out.println(usage);
                        System.exit(1);
                    }
                    try {
                        alpha = Double.parseDouble(args[++i]);
                    } catch (Exception e) {
                        System.out.println(usage);
                        System.exit(-1);
                    }
                    break;
                case "-results":
                    resultsFile1 = args[++i];
                    resultsFile2 = args[++i];
                    break;
                default:
                    System.err.println("Uso incorrecto: " + usage);
                    System.exit(1);
            }
        }

        double pValue = 0.0;
        if (testType.equals("t")) {
            pValue = performTTest(resultsFile1, resultsFile2);
        } else {
            pValue = performWilcoxonTest(resultsFile1, resultsFile2);
        }

        if (pValue < alpha) {
            System.out.println("Resultado del test: Significativo");
        } else {
            System.out.println("Resultado del test: No significativo");
        }
        System.out.println("P-valor: " + pValue);
    }

    private static double performTTest(String resultsFile1, String resultsFile2) {
        TTest tTest = new TTest();

        List<Double> results1 = readResults(resultsFile1);
        List<Double> results2 = readResults(resultsFile2);


        double[] array1 = listToArray(results1);
        double[] array2 = listToArray(results2);

        return tTest.tTest(array1, array2);
    }

    private static double performWilcoxonTest(String resultsFile1, String resultsFile2) {
        List<Double> results1 = readResults(resultsFile1);
        List<Double> results2 = readResults(resultsFile2);

        WilcoxonSignedRankTest wilcoxonTest = new WilcoxonSignedRankTest();

        double[] array1 = listToArray(results1);
        double[] array2 = listToArray(results2);


        return wilcoxonTest.wilcoxonSignedRankTest(array1, array2, false);
    }


    private static List<Double> readResults(String fileName) {
        List<Double> results = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;

            br.readLine();
            while ((line = br.readLine()) != null) {

                String[] parts = line.split(",");

                double metricValue = Double.parseDouble(parts[1]);

                results.add(metricValue);
            }

            results.remove(results.size() - 1);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
    }

    private static double[] listToArray(List<Double> d){
        double[] array = new double[d.size()];

        for(int i = 0; i < d.size(); i++){
            array[i] = d.get(i);
        }
        return array;
    }
}
