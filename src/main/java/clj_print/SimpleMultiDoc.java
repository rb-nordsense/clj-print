package clj_print;

import java.io.IOException;

import javax.print.Doc;
import javax.print.MultiDoc;
import javax.print.PrintException;

/**
 * Created by racevedo on 3/31/2014.
 */
public class SimpleMultiDoc implements MultiDoc {

    private Doc doc;
	private MultiDoc next;

	public Doc getDoc() throws IOException {
		return this.doc;
	}

	// I feel loitering here, curious to see how other people implement this,
	// wondering if I should just use a Stack to manage the documents and
	// forego conforming to the javax.print API.
	public MultiDoc next() throws IOException {
        if (next == null) throw new PrintException("No more documents.")
        MultiDoc curr = next;
        next = curr.next;
        return curr;
    }
}
