package clj_print;

import javax.print.Doc;
import javax.print.MultiDoc;
import java.io.IOException;

/**
 * Created by racevedo on 3/31/2014.
 */
public class SimpleMultiDoc implements MultiDoc {

    private Doc doc;
    public SimpleMultiDoc next;

    public Doc getDoc() throws IOException {
        return doc;
    }

    public SimpleMultiDoc next() throws IOException {
        if (next == null)
            throw new RuntimeException("No more documents.");
        SimpleMultiDoc curr = next;
        next = curr.next;
        return curr;
    }
}
