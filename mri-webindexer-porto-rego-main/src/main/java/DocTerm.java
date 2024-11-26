
public class DocTerm implements Comparable<DocTerm>{
    private final String text;
    private final int tf;
    private final int df;
    private final double tfidf;

    public DocTerm(String text, int tf, int df, int totalDocs) {
        this.text = text;
        this.tf = tf;
        this.df = df;
        this.tfidf = tf * Math.log10(totalDocs/df);
    }


    public String getText() {
        return text;
    }

    public int getTf() {
        return tf;
    }

    public int getDf() {
        return df;
    }

    public double getTfidf() {
        return tfidf;
    }


    @Override
    public int compareTo(DocTerm other) {
        if(this.tfidf > other.tfidf) {
            return 1;
        } else if (this.tfidf == other.tfidf) {
            return 0;
        } else {
            return -1;
        }
    }

    public String toString(){
        return "Término: " + text + ", TF: " + tf + ", DF: " + df + ", TF-IDF: " + tfidf + "\n";
    }
}
