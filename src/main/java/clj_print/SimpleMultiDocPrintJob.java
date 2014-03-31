package clj_print;

import javax.print.Doc;
import javax.print.MultiDoc;
import javax.print.MultiDocPrintJob;
import javax.print.MultiDocPrintService;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.attribute.PrintJobAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.event.PrintJobAttributeListener;
import javax.print.event.PrintJobListener;

/**
 * Created by racevedo on 3/31/2014.
 */
public class SimpleMultiDocPrintJob implements MultiDocPrintJob {

	private MultiDoc doc;
	private MultiDocPrintService service;

	public SimpleMultiDocPrintJob(MultiDocPrintService service) {
		this.service = service;
	}

	public void print(MultiDoc multiDoc, PrintRequestAttributeSet attributes)
	        throws PrintException {
		while (multiDoc.next() != null) {
			// Print them
		}
	}

	public PrintService getPrintService() {
		return service;
	}

	public PrintJobAttributeSet getAttributes() {
		return null;
	}

	public void addPrintJobListener(PrintJobListener listener) {

	}

	public void removePrintJobListener(PrintJobListener listener) {

	}

	public void addPrintJobAttributeListener(
	    PrintJobAttributeListener listener, PrintJobAttributeSet attributes) {

	}

	public void removePrintJobAttributeListener(
	    PrintJobAttributeListener listener) {

	}

	public void print(Doc doc, PrintRequestAttributeSet attributes)
	        throws PrintException {

	}
}
