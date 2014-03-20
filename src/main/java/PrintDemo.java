import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.DocAttribute;
import javax.print.attribute.HashDocAttributeSet;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.MediaTray;
import javax.print.attribute.standard.PrinterName;

/**
 * Created by roberto on 3/20/2014.
 */
public class PrintDemo {

	public static void main(String[] args) {

		// Set up our document attributes
		AttributeSet docAttributeSet =
		    new HashDocAttributeSet(new DocAttribute[] { MediaTray.MAIN,
		        Chromaticity.COLOR });

		AttributeSet printServiceAttributeSet =
		    new HashPrintServiceAttributeSet(
		        new PrintServiceAttribute[] { new PrinterName(
		            "\\\\SRQPRINT\\2WColor-PRT2_SA", Locale.US) });

		PrintService[] printServices =
		    PrintServiceLookup.lookupPrintServices(
		        DocFlavor.INPUT_STREAM.AUTOSENSE, printServiceAttributeSet);

		PrintService printService = printServices[0];

		try {
			FileInputStream fileInputStream =
			    new FileInputStream("C:/Users/racevedo/Desktop/test.pdf");

			SimpleDoc doc =
			    new SimpleDoc(fileInputStream, DocFlavor.INPUT_STREAM.PDF,
			        new HashDocAttributeSet());

			DocPrintJob printJob = printService.createPrintJob();

			printJob.print(doc, null);

		} catch (IOException e) {

		} catch (PrintException e) {

		}
	}
}
