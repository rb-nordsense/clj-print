package clj_print;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.MultiDocPrintJob;
import javax.print.MultiDocPrintService;
import javax.print.PrintService;
import javax.print.ServiceUIFactory;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.event.PrintServiceAttributeListener;

/**
 * Created by racevedo on 3/31/2014.
 */
public class SimpleMultiDocPrintService implements MultiDocPrintService {

	// The actual PrintService to be used that we will manage from here
	private PrintService service;

	public MultiDocPrintJob createMultiDocPrintJob() {
		return new SimpleMultiDocPrintJob(this);
	}

	public String getName() {
		return "[MULTI] - " + service.getName();
	}

	public DocPrintJob createPrintJob() {
		return service.createPrintJob();
	}

	public void addPrintServiceAttributeListener(
	    PrintServiceAttributeListener listener) {
		service.addPrintServiceAttributeListener(listener);
	}

	public void removePrintServiceAttributeListener(
	    PrintServiceAttributeListener listener) {
		service.removePrintServiceAttributeListener(listener);
	}

	public PrintServiceAttributeSet getAttributes() {
		return service.getAttributes();
	}

	public <T extends PrintServiceAttribute> T getAttribute(Class<T> category) {
		return service.getAttribute(category);
	}

	public DocFlavor[] getSupportedDocFlavors() {
		return service.getSupportedDocFlavors();
	}

	public boolean isDocFlavorSupported(DocFlavor flavor) {
		return service.isDocFlavorSupported(flavor);
	}

	public Class<?>[] getSupportedAttributeCategories() {
		return service.getSupportedAttributeCategories();
	}

	public boolean isAttributeCategorySupported(
	    Class<? extends Attribute> category) {
		return service.isAttributeCategorySupported(category);
	}

	public Object getDefaultAttributeValue(Class<? extends Attribute> category) {
		return service.getDefaultAttributeValue(category);
	}

	public Object getSupportedAttributeValues(
	    Class<? extends Attribute> category, DocFlavor flavor,
	    AttributeSet attributes) {
		return service.getSupportedAttributeValues(category, flavor,
		    attributes);
	}

	public boolean isAttributeValueSupported(Attribute attrval,
	    DocFlavor flavor, AttributeSet attributes) {
		return service.isAttributeValueSupported(attrval, flavor, attributes);
	}

	public AttributeSet getUnsupportedAttributes(DocFlavor flavor,
	    AttributeSet attributes) {
		return service.getUnsupportedAttributes(flavor, attributes);
	}

	public ServiceUIFactory getServiceUIFactory() {
		return service.getServiceUIFactory();
	}
}
